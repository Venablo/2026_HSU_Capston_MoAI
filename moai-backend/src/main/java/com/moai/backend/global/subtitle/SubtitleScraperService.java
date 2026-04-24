package com.moai.backend.global.subtitle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubtitleScraperService {

    private final ObjectMapper objectMapper;

    @Value("${subtitle.script-path}")
    private String scriptPath;

    private static final long TIMEOUT_SECONDS = 30;
    private static final List<String> PYTHON_CANDIDATES = List.of("python3", "python", "py");

    /**
     * YouTube 자막을 스크래핑하여 청크 리스트로 반환한다.
     * 실패 시 빈 리스트를 반환한다.
     */
    public List<SubtitleChunkDto> scrape(String videoId) {
        Process process = null;
        try {
            process = startPythonProcess(videoId);
            if (process == null) {
                log.warn("Python 실행 파일을 찾을 수 없음 — python3/python/py 모두 실패");
                return Collections.emptyList();
            }

            // stdout/stderr을 병합해서 한 스트림으로 드레인 (버퍼 가득 차서 hang 방지)
            String combinedOutput;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                combinedOutput = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("자막 스크래핑 타임아웃: videoId={}", videoId);
                return Collections.emptyList();
            }

            if (process.exitValue() != 0) {
                log.warn("자막 스크래핑 실패 (exit={}): videoId={}, output={}",
                        process.exitValue(), videoId, combinedOutput);
                return Collections.emptyList();
            }

            // stdout만 JSON으로 파싱 — 혹시 stderr이 섞였다면 마지막 JSON 배열만 추출 시도
            String jsonText = combinedOutput.trim();
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

        } catch (Exception e) {
            log.warn("자막 스크래핑 중 예외 발생: videoId={}", videoId, e);
            return Collections.emptyList();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private Process startPythonProcess(String videoId) {
        for (String candidate : PYTHON_CANDIDATES) {
            try {
                ProcessBuilder pb = new ProcessBuilder(candidate, scriptPath, videoId);
                pb.redirectErrorStream(true);
                return pb.start();
            } catch (IOException ignored) {
                // 다음 후보 시도
            }
        }
        return null;
    }
}
