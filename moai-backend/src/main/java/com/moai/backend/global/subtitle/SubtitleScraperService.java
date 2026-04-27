package com.moai.backend.global.subtitle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubtitleScraperService {

    private final ObjectMapper objectMapper;

    @Value("${subtitle.script-path}")
    private String scriptPath;

    private String resolvedScriptPath;

    /**
     * 앱 시작 시 subtitle_scraper.py를 classpath에서 읽어 임시 파일로 추출한다.
     * 이렇게 하면 앱의 working directory에 관계없이 스크립트를 항상 찾을 수 있다.
     */
    @PostConstruct
    public void resolveScript() {
        // 1. 설정된 경로에 파일이 직접 존재하면 그대로 사용
        File configured = new File(scriptPath);
        if (configured.exists()) {
            resolvedScriptPath = configured.getAbsolutePath();
            log.info("Subtitle scraper script found at configured path: {}", resolvedScriptPath);
            return;
        }

        // 2. classpath에서 로드 (working directory 무관)
        ClassPathResource resource = new ClassPathResource("scripts/subtitle_scraper.py");
        if (resource.exists()) {
            try {
                File tempScript = File.createTempFile("subtitle_scraper", ".py");
                tempScript.deleteOnExit();
                Files.copy(resource.getInputStream(), tempScript.toPath(), StandardCopyOption.REPLACE_EXISTING);
                resolvedScriptPath = tempScript.getAbsolutePath();
                log.info("Subtitle scraper script extracted from classpath to: {}", resolvedScriptPath);
            } catch (IOException e) {
                log.warn("Failed to extract subtitle scraper from classpath, falling back to configured path: {}", scriptPath);
                resolvedScriptPath = scriptPath;
            }
        } else {
            log.warn("Subtitle scraper script not found at '{}' or on classpath — scraping will be skipped", scriptPath);
            resolvedScriptPath = scriptPath;
        }
    }

    private static final long TIMEOUT_SECONDS = 30;
    private static final long CHECK_TIMEOUT_SECONDS = 10;
    private static final List<List<String>> PYTHON_CANDIDATES = List.of(
            List.of("python3"),
            List.of("python"),
            List.of("py", "-3"),
            List.of("py")
    );

    /**
     * 수동/자동 자막 모두 포함하여 자막 존재 여부를 확인한다 (--check 모드).
     * 전체 자막을 다운로드하지 않으므로 scrape()보다 훨씬 빠르다.
     */
    public boolean hasSubtitle(String videoId) {
        for (List<String> commandPrefix : PYTHON_CANDIDATES) {
            try {
                java.util.ArrayList<String> command = new java.util.ArrayList<>(commandPrefix);
                command.add(resolvedScriptPath);
                command.add(videoId);
                command.add("--check");

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                boolean finished = process.waitFor(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    continue;
                }
                return process.exitValue() == 0;
            } catch (IOException e) {
                // 해당 Python 실행 파일 없음 — 다음 후보 시도
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public List<SubtitleChunkDto> scrape(String videoId) {
        ScraperRunResult lastFailure = null;

        for (List<String> commandPrefix : PYTHON_CANDIDATES) {
            try {
                ScraperRunResult runResult = runScraper(commandPrefix, videoId);
                if (runResult.exitCode() == 0) {
                    return parseScraperOutput(runResult.output());
                }

                lastFailure = runResult;
                log.warn("Subtitle scraper candidate failed (command={}, exit={}): videoId={}, output={}",
                        String.join(" ", commandPrefix), runResult.exitCode(), videoId, truncate(runResult.output()));
            } catch (IOException e) {
                log.debug("Subtitle scraper candidate could not start (command={}): {}",
                        String.join(" ", commandPrefix), e.getMessage());
            } catch (TimeoutException e) {
                log.warn("Subtitle scraper timed out (command={}): videoId={}",
                        String.join(" ", commandPrefix), videoId);
                return Collections.emptyList();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Subtitle scraper interrupted: videoId={}", videoId, e);
                return Collections.emptyList();
            } catch (Exception e) {
                log.warn("Subtitle scraper failed while parsing output: videoId={}", videoId, e);
                return Collections.emptyList();
            }
        }

        if (lastFailure == null) {
            log.warn("No Python executable found for subtitle scraper. Tried python3/python/py.");
        } else {
            log.warn("Subtitle scraping failed for every Python candidate: videoId={}, lastOutput={}",
                    videoId, truncate(lastFailure.output()));
        }
        return Collections.emptyList();
    }

    private ScraperRunResult runScraper(List<String> commandPrefix, String videoId)
            throws IOException, InterruptedException, TimeoutException, ExecutionException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(buildCommand(commandPrefix, videoId));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readOutput(process));
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException("subtitle scraper timed out");
        }

        String output;
        try {
            output = outputFuture.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            outputFuture.cancel(true);
            output = "";
        }
        return new ScraperRunResult(process.exitValue(), output);
    }

    private List<String> buildCommand(List<String> commandPrefix, String videoId) {
        java.util.ArrayList<String> command = new java.util.ArrayList<>(commandPrefix);
        command.add(resolvedScriptPath);
        command.add(videoId);
        return command;
    }

    private String readOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        } catch (IOException e) {
            return "";
        }
    }

    private List<SubtitleChunkDto> parseScraperOutput(String output) throws IOException {
        String jsonText = extractJsonArray(output);
        List<Map<String, Object>> rawEntries = objectMapper.readValue(
                jsonText, new TypeReference<>() {}
        );

        return IntStream.range(0, rawEntries.size())
                .mapToObj(i -> {
                    Map<String, Object> entry = rawEntries.get(i);
                    String text = (String) entry.get("text");
                    double start = ((Number) entry.get("start")).doubleValue();
                    double duration = ((Number) entry.get("duration")).doubleValue();
                    return SubtitleChunkDto.of(i, text, start, duration);
                })
                .toList();
    }

    private String extractJsonArray(String output) throws IOException {
        String trimmed = output == null ? "" : output.trim();
        int start = findJsonArrayStart(trimmed);
        int end = trimmed.lastIndexOf(']');
        if (start < 0 || end < start) {
            throw new IOException("subtitle scraper did not return a JSON array: " + truncate(trimmed));
        }
        return trimmed.substring(start, end + 1);
    }

    private int findJsonArrayStart(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) != '[') {
                continue;
            }

            int next = i + 1;
            while (next < value.length() && Character.isWhitespace(value.charAt(next))) {
                next++;
            }
            if (next < value.length() && (value.charAt(next) == '{' || value.charAt(next) == ']')) {
                return i;
            }
        }
        return -1;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }

    private record ScraperRunResult(int exitCode, String output) {
    }
}
