package com.moai.backend.global.subtitle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moai.backend.global.subtitle.dto.SubtitleChunk;
import com.moai.backend.global.subtitle.dto.SubtitleScrapeResult;
import com.moai.backend.global.subtitle.exception.SubtitleErrorCode;
import com.moai.backend.global.subtitle.exception.SubtitleScrapeException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Python 스크립트(scrape_subtitle.py)를 호출해 자막을 추출하는 서비스.
 *
 * 동작:
 * - 1차 yt-dlp, 2차 youtube-transcript-api (스크립트 내부 폴백)
 * - stdout: 성공 JSON 한 줄 — {"chunks","lang","source"}
 * - stderr: 실패 시 구조화 JSON — {"errorCode","message","detail"}
 * - 종료 코드: SubtitleErrorCode 와 1:1 매핑
 *
 * 데드락 방지: redirectErrorStream(false) + stdout/stderr 별도 스레드로 동시에 읽는다.
 * 두 버퍼가 가득 차면 자식 프로세스가 멈출 수 있으므로, 두 스트림 모두를 동시에 비우는 게 필수다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubtitleScraperService {

    private final ObjectMapper objectMapper;

    /**
     * 콤마 구분 Python 실행 명령 후보 리스트.
     * 예: "python3,python,py" — Linux/macOS 는 보통 python3, Windows 는 py 또는 python.
     * 앞에서부터 시도하여 실행 가능한 첫 명령을 사용한다.
     */
    @Value("${subtitle.python-bin:python3,python,py}")
    private String pythonBin;

    @Value("${subtitle.script-path}")
    private String scriptPath;

    @Value("${subtitle.timeout-sec:30}")
    private long timeoutSec;

    @Value("${subtitle.preferred-langs:ko,en}")
    private String preferredLangs;

    @Value("${subtitle.enable-fallback:true}")
    private boolean enableFallback;

    /**
     * YouTube 봇 탐지(IP 밴) 우회용 Netscape 포맷 cookies.txt 절대 경로.
     * 비어있으면 쿠키 미사용. 파일 누락은 Python 스크립트에서 soft-skip 처리.
     */
    @Value("${subtitle.cookies-path:}")
    private String cookiesPath;

    /**
     * 동시 호출 한도. YouTube 봇 탐지 회피를 위해 동시에 N 개의 자막 스크래핑만 허용한다.
     * 권장 1~3. 4 이상은 동일 IP 에서 자동화 시그널이 강해질 수 있음.
     */
    @Value("${subtitle.concurrency-limit:2}")
    private int concurrencyLimit;

    private String resolvedScriptPath;
    private Semaphore concurrencyLimiter;

    @PostConstruct
    public void resolveScript() {
        // 1) 설정된 경로 그대로 사용 가능하면 그대로 사용
        File configured = new File(scriptPath);
        if (configured.exists()) {
            resolvedScriptPath = configured.getAbsolutePath();
            log.info("자막 스크립트 발견 (설정 경로): {}", resolvedScriptPath);
        } else {
            // 2) classpath 의 scripts/scrape_subtitle.py 를 임시 디렉토리에 추출
            ClassPathResource resource = new ClassPathResource("scripts/scrape_subtitle.py");
            if (resource.exists()) {
                try {
                    File tempDir = Files.createTempDirectory("moai_subtitle").toFile();
                    tempDir.deleteOnExit();

                    File tempScript = new File(tempDir, "scrape_subtitle.py");
                    tempScript.deleteOnExit();
                    Files.copy(resource.getInputStream(), tempScript.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    resolvedScriptPath = tempScript.getAbsolutePath();
                    log.info("자막 스크립트를 classpath 에서 추출: {}", resolvedScriptPath);
                } catch (IOException e) {
                    log.warn("자막 스크립트 추출 실패, 설정 경로로 폴백: {}", scriptPath);
                    resolvedScriptPath = scriptPath;
                }
            } else {
                log.warn("자막 스크립트를 찾을 수 없음 — 경로='{}'. 자막 추출은 항상 실패한다.", scriptPath);
                resolvedScriptPath = scriptPath;
            }
        }

        // 동시성 제한 세마포어 초기화 — 잘못된 설정(0/음수)도 1 로 보정해 데드락 방지
        int permits = Math.max(1, concurrencyLimit);
        this.concurrencyLimiter = new Semaphore(permits, true);
        log.info("자막 스크래핑 동시성 한도: {}", permits);
    }

    /**
     * 주어진 YouTube videoId 에 대해 자막을 추출한다.
     *
     * 봇 탐지 회피를 위해 {@link #concurrencyLimiter} 로 동시 실행 수를 제한한다.
     * 한도를 초과한 호출자는 슬롯이 빌 때까지 블로킹된다 (FIFO 공정성 보장).
     *
     * @throws SubtitleScrapeException 실패 시. errorCode 로 분기 처리 가능.
     */
    public SubtitleScrapeResult scrape(String videoId) {
        try {
            concurrencyLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SubtitleScrapeException(SubtitleErrorCode.SCRIPT_EXECUTION_FAILED,
                    "동시성 세마포어 대기 중 인터럽트", e);
        }
        try {
            return doScrape(videoId);
        } finally {
            concurrencyLimiter.release();
        }
    }

    /**
     * 실제 Python 호출 + stdout/stderr 처리. 동시성 제어는 {@link #scrape(String)} 가 담당한다.
     *
     * Python 실행 명령 후보를 순서대로 시도하고, "명령을 찾을 수 없음" 류의 실패가 아닌
     * 정상 시작 후의 실패는 그대로 SubtitleScrapeException 으로 전파한다.
     */
    private SubtitleScrapeResult doScrape(String videoId) {
        List<String> pythonCandidates = parsePythonCandidates();

        Process process = null;
        IOException lastStartFailure = null;
        for (String candidate : pythonCandidates) {
            List<String> command = buildCommand(candidate, videoId);
            log.debug("자막 스크립트 호출: {}", String.join(" ", command));
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(false); // stdout/stderr 분리 — 에러 JSON 보존을 위해 필수
                process = pb.start();
                break;
            } catch (IOException e) {
                // 해당 Python 명령이 PATH 에 없는 등 시작 자체에 실패 — 다음 후보 시도
                lastStartFailure = e;
                log.debug("Python 실행 명령 '{}' 시작 실패, 다음 후보 시도: {}", candidate, e.getMessage());
            }
        }

        if (process == null) {
            log.error("Python 프로세스를 시작할 수 없음 — 후보={}", pythonCandidates, lastStartFailure);
            throw new SubtitleScrapeException(SubtitleErrorCode.SCRIPT_EXECUTION_FAILED,
                    "Python 실행 명령 없음: " + pythonCandidates,
                    lastStartFailure);
        }

        // stdout/stderr 를 동시에 읽어 버퍼 가득참으로 인한 데드락을 방지
        CompletableFuture<String> stdoutFuture = readStreamAsync(process.getInputStream(), "stdout");
        CompletableFuture<String> stderrFuture = readStreamAsync(process.getErrorStream(), "stderr");

        boolean finished;
        try {
            finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new SubtitleScrapeException(SubtitleErrorCode.SCRIPT_EXECUTION_FAILED, "프로세스 대기 중 인터럽트", e);
        }

        if (!finished) {
            process.destroyForcibly();
            stdoutFuture.cancel(true);
            stderrFuture.cancel(true);
            log.warn("자막 스크립트 타임아웃 ({}s): videoId={}", timeoutSec, videoId);
            throw new SubtitleScrapeException(SubtitleErrorCode.SCRIPT_TIMEOUT, "videoId=" + videoId);
        }

        int exitCode = process.exitValue();
        String stdout = awaitStream(stdoutFuture);
        String stderr = awaitStream(stderrFuture);

        if (exitCode == 0) {
            return parseSuccessOutput(videoId, stdout);
        }

        throw buildFailureException(videoId, exitCode, stderr);
    }

    private List<String> parsePythonCandidates() {
        if (pythonBin == null || pythonBin.isBlank()) {
            return List.of("python3");
        }
        List<String> candidates = Arrays.stream(pythonBin.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        return candidates.isEmpty() ? List.of("python3") : candidates;
    }

    private List<String> buildCommand(String pythonExec, String videoId) {
        List<String> command = new ArrayList<>();
        command.add(pythonExec);
        command.add(resolvedScriptPath);
        command.add(videoId);
        command.add("--langs");
        command.add(preferredLangs);
        if (!enableFallback) {
            command.add("--no-fallback");
        }
        // 쿠키 경로가 설정된 경우에만 --cookies 인자 추가. Fail-Fast/soft-skip 분기는 Python 측에서 수행.
        if (cookiesPath != null && !cookiesPath.isBlank()) {
            command.add("--cookies");
            command.add(cookiesPath);
        }
        return command;
    }

    private CompletableFuture<String> readStreamAsync(InputStream stream, String label) {
        return CompletableFuture.supplyAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException e) {
                log.debug("자막 스크립트 {} 스트림 읽기 실패: {}", label, e.getMessage());
                return "";
            }
        });
    }

    private String awaitStream(CompletableFuture<String> future) {
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException e) {
            future.cancel(true);
            return "";
        }
    }

    private SubtitleScrapeResult parseSuccessOutput(String videoId, String stdout) {
        if (stdout == null || stdout.isBlank()) {
            throw new SubtitleScrapeException(SubtitleErrorCode.INVALID_SCRIPT_OUTPUT, "stdout 비어있음");
        }

        // stdout 은 단일 JSON 한 줄이지만, 디버그 로그 등이 섞일 가능성에 대비해 첫 '{' 부터 마지막 '}' 까지 추출
        String jsonText = extractJsonObject(stdout);
        if (jsonText == null) {
            throw new SubtitleScrapeException(SubtitleErrorCode.INVALID_SCRIPT_OUTPUT,
                    "stdout 에서 JSON 객체를 찾을 수 없음");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(jsonText);
        } catch (JsonProcessingException e) {
            throw new SubtitleScrapeException(SubtitleErrorCode.INVALID_SCRIPT_OUTPUT,
                    "stdout JSON 파싱 실패: " + e.getOriginalMessage(), e);
        }

        JsonNode chunksNode = root.get("chunks");
        if (chunksNode == null || !chunksNode.isArray()) {
            throw new SubtitleScrapeException(SubtitleErrorCode.INVALID_SCRIPT_OUTPUT,
                    "chunks 필드 누락 또는 배열 아님");
        }

        List<SubtitleChunk> chunks = new ArrayList<>(chunksNode.size());
        int index = 0;
        for (JsonNode entry : chunksNode) {
            String text = entry.path("text").asText("");
            double start = entry.path("start").asDouble(0.0);
            double duration = entry.path("duration").asDouble(0.0);
            chunks.add(SubtitleChunk.of(index++, text, start, duration));
        }

        String lang = root.path("lang").asText(null);
        String source = root.path("source").asText(null);

        log.info("자막 추출 성공: videoId={}, lang={}, source={}, chunks={}", videoId, lang, source, chunks.size());
        return new SubtitleScrapeResult(chunks, lang, source);
    }

    private SubtitleScrapeException buildFailureException(String videoId, int exitCode, String stderr) {
        // 1) stderr JSON 파싱 시도
        SubtitleErrorCode codeFromStderr = null;
        String detail = "";
        String stderrJson = extractJsonObject(stderr);
        if (stderrJson != null) {
            try {
                JsonNode node = objectMapper.readTree(stderrJson);
                String errorCodeStr = node.path("errorCode").asText(null);
                if (errorCodeStr != null) {
                    codeFromStderr = SubtitleErrorCode.fromName(errorCodeStr);
                }
                detail = node.path("detail").asText("");
            } catch (JsonProcessingException e) {
                log.debug("stderr JSON 파싱 실패 — exitCode 기반 폴백: {}", e.getOriginalMessage());
            }
        }

        // 2) stderr 파싱 실패 시 exitCode 로 역매핑
        SubtitleErrorCode resolved = codeFromStderr != null
                ? codeFromStderr
                : SubtitleErrorCode.fromExitCode(exitCode);

        log.warn("자막 추출 실패: videoId={}, exit={}, errorCode={}, detail={}",
                videoId, exitCode, resolved, truncate(detail.isEmpty() ? stderr : detail));

        return new SubtitleScrapeException(resolved, detail.isEmpty() ? "exit=" + exitCode : detail);
    }

    /**
     * 문자열에서 가장 바깥쪽 JSON 객체 ({ ... }) 를 추출한다.
     * 단일 라인 페이로드를 기대하지만, 의도치 않은 로그 줄이 섞여도 견디도록 한다.
     */
    private String extractJsonObject(String value) {
        if (value == null) return null;
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return value.substring(start, end + 1);
    }

    private String truncate(String value) {
        if (value == null) return "";
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }
}
