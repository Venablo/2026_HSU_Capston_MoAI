package com.moai.backend.global.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private final WebClient llmWebClient;
    private final ObjectMapper objectMapper;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(?:json)?\\s*\\n?(.*)\\n?\\s*```", Pattern.DOTALL
    );

    /**
     * Gemini API를 호출하고 응답 텍스트를 반환한다.
     */
    public LlmResponseDto call(LlmRequestDto request) {
        String text = callGemini(request);
        return new LlmResponseDto(text);
    }

    /**
     * Gemini API를 호출하고 응답을 JSON으로 파싱하여 반환한다.
     * 마크다운 코드블록(```json ... ```)이 감싸져 있으면 자동으로 제거한 뒤 파싱한다.
     */
    public <T> T callJson(LlmRequestDto request, Class<T> responseType) {
        String text = callGemini(request);
        String json = stripCodeBlock(text);
        try {
            return objectMapper.readValue(json, responseType);
        } catch (JsonProcessingException e) {
            log.error("LLM 응답 JSON 파싱 실패: {}", json, e);
            throw new CustomException(ErrorCode.LLM_RESPONSE_PARSE_ERROR);
        }
    }

    /**
     * Gemini REST API 호출 — 공통 내부 메서드
     */
    private String callGemini(LlmRequestDto request) {
        Map<String, Object> body = buildRequestBody(request);

        try {
            String response = llmWebClient.post()
                    .uri("/{model}:generateContent?key={key}", model, apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return extractContent(response);
        } catch (WebClientResponseException e) {
            log.error("Gemini API 호출 실패 [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.LLM_API_CALL_FAILED);
        }
    }

    /**
     * Gemini API 요청 바디 구성
     */
    private Map<String, Object> buildRequestBody(LlmRequestDto request) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", request.getSystemPrompt()))
            ));
        }

        body.put("contents", List.of(
                Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", request.getUserMessage()))
                )
        ));

        return body;
    }

    /**
     * Gemini 응답 JSON에서 텍스트 콘텐츠를 추출한다.
     * 경로: candidates[0].content.parts[0].text
     */
    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode textNode = root
                    .path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text");

            if (textNode.isMissingNode()) {
                log.error("Gemini 응답에서 텍스트를 찾을 수 없음: {}", responseBody);
                throw new CustomException(ErrorCode.LLM_RESPONSE_PARSE_ERROR);
            }

            return textNode.asText();
        } catch (JsonProcessingException e) {
            log.error("Gemini 응답 파싱 실패: {}", responseBody, e);
            throw new CustomException(ErrorCode.LLM_RESPONSE_PARSE_ERROR);
        }
    }

    /**
     * Gemini streamGenerateContent API를 호출하여 토큰 단위 Flux를 반환한다.
     * 거꾸로 학습 SSE 스트리밍에서 사용 — 대화 이력(contents)을 직접 전달받는다.
     *
     * @param systemPrompt 시스템 프롬프트
     * @param contents     Gemini contents 배열 (role + parts 형태의 대화 이력)
     * @return 토큰 텍스트를 하나씩 방출하는 Flux
     */
    public Flux<String> callStream(String systemPrompt, List<Map<String, Object>> contents) {
        Map<String, Object> body = new LinkedHashMap<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemPrompt))
            ));
        }
        body.put("contents", contents);

        // Gemini streamGenerateContent는 JSON 배열을 SSE 스트림으로 반환
        // 각 청크에서 candidates[0].content.parts[0].text를 추출
        return llmWebClient.post()
                .uri("/{model}:streamGenerateContent?alt=sse&key={key}", model, apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .mapNotNull(chunk -> {
                    try {
                        JsonNode root = objectMapper.readTree(chunk);
                        JsonNode textNode = root
                                .path("candidates").path(0)
                                .path("content").path("parts").path(0)
                                .path("text");
                        return textNode.isMissingNode() ? null : textNode.asText();
                    } catch (Exception e) {
                        log.debug("스트리밍 청크 파싱 스킵: {}", chunk);
                        return null;
                    }
                });
    }

    /**
     * 마크다운 코드블록(```json ... ``` 또는 ``` ... ```)을 제거하고 내부 JSON만 반환한다.
     * 코드블록이 없어도 선행/후행 텍스트를 제거하여 JSON 객체/배열만 추출한다.
     */
    private String stripCodeBlock(String text) {
        String trimmed = text.trim();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            trimmed = matcher.group(1).trim();
        }
        // Preamble/postamble 텍스트를 제거하고 최외곽 JSON 객체/배열만 추출한다.
        int objStart = trimmed.indexOf('{');
        int arrStart = trimmed.indexOf('[');
        if (objStart == -1 && arrStart == -1) return trimmed;
        boolean useObj = arrStart == -1 || (objStart != -1 && objStart < arrStart);
        int start = useObj ? objStart : arrStart;
        char closeChar = useObj ? '}' : ']';
        int end = trimmed.lastIndexOf(closeChar);
        if (end > start) return trimmed.substring(start, end + 1);
        return trimmed;
    }
}
