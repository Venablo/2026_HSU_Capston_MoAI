package com.moai.backend.global.subtitle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
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

    /**
     * YouTube 자막을 스크래핑하여 청크 리스트로 반환한다.
     * 실패 시 빈 리스트를 반환한다.
     */
    public List<SubtitleChunkDto> scrape(String videoId) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", scriptPath, videoId);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String stdout;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                stdout = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("자막 스크래핑 타임아웃: videoId={}", videoId);
                return Collections.emptyList();
            }

            if (process.exitValue() != 0) {
                try (BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String stderr = errReader.lines().collect(Collectors.joining("\n"));
                    log.warn("자막 스크래핑 실패 (exit={}): videoId={}, stderr={}", process.exitValue(), videoId, stderr);
                }
                return Collections.emptyList();
            }

            List<Map<String, Object>> rawEntries = objectMapper.readValue(
                    stdout, new TypeReference<>() {}
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
        }
    }
}
