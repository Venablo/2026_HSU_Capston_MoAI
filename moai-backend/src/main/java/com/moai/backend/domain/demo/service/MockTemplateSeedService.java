package com.moai.backend.domain.demo.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moai.backend.domain.curriculum.entity.CurriculumResource;
import com.moai.backend.domain.demo.dto.MockTemplateSeedRequestDto;
import com.moai.backend.domain.demo.dto.MockTemplateSeedResponseDto;
import com.moai.backend.domain.demo.entity.MockCurriculumTemplate;
import com.moai.backend.domain.demo.entity.MockTranscriptTemplate;
import com.moai.backend.domain.demo.repository.MockCurriculumTemplateRepository;
import com.moai.backend.domain.demo.repository.MockTranscriptTemplateRepository;
import com.moai.backend.global.llm.LlmRequestDto;
import com.moai.backend.global.llm.LlmService;
import com.moai.backend.global.subtitle.SubtitleScraper;
import com.moai.backend.global.subtitle.dto.SubtitleChunk;
import com.moai.backend.global.subtitle.dto.SubtitleScrapeResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 시연용 목업 템플릿 시드 서비스.
 *
 * videoId + topic 만 받아서:
 * 1. SubtitleScraper 로 자막 추출 — 실패 시 예외 그대로 전파 (자막이 시드의 핵심)
 * 2. LLM 2회 호출 (실제 학습실 생성 흐름과 동일 구조):
 *    a. topic 기반 description 생성 — 실제 학습실의 "최초 커리큘럼 생성" 단계 모방
 *    b. 자막 기반 keywords 추출 — 실제 학습실의 "자막 스크래핑 후 키워드 재추출" 단계 모방
 *    각 호출은 독립 실패 — 한쪽 실패해도 다른 쪽 결과는 살린다.
 * 3. weekNumber 자동(MAX+1), resources=YouTube 단일 항목
 * 4. mock_curriculum_templates + mock_transcript_templates 에 INSERT
 *
 * keywords 추출 프롬프트는 {@code CurriculumEnrichmentService.extractKeywords()} 의 원문을
 * 그대로 사용해 실제 학습실 생성과 키워드 품질을 정확히 일치시킨다.
 *
 * 권한 체크(시연 계정 화이트리스트)는 호출부(DemoController)가 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockTemplateSeedService {

    private final SubtitleScraper subtitleScraper;
    private final LlmService llmService;
    private final MockCurriculumTemplateRepository mockCurriculumTemplateRepository;
    private final MockTranscriptTemplateRepository mockTranscriptTemplateRepository;

    @Transactional
    public MockTemplateSeedResponseDto seed(MockTemplateSeedRequestDto request) {
        String videoId = request.getVideoId().trim();
        String topic = request.getTopic().trim();
        log.info("목업 템플릿 시드 시작 — videoId={}, topic={}", videoId, topic);

        // 1) 자막 추출 — 실패 시 SubtitleScrapeException 그대로 전파
        SubtitleScrapeResult scrapeResult = subtitleScraper.scrape(videoId);
        List<SubtitleChunk> chunks = scrapeResult.chunks();

        // 2) LLM 2회: description (topic 기반) + keywords (자막 기반). 각 호출 독립 실패.
        String description = generateDescription(topic);
        List<String> keywords = extractKeywords(topic, chunks);

        // 3) weekNumber 자동 결정 (MAX+1)
        short nextWeek = resolveNextWeekNumber();

        // 4) resources — YouTube 단일 항목
        List<CurriculumResource> resources = List.of(
                new CurriculumResource("youtube", videoId, topic, null, null, null, null, null)
        );

        // 5) MockCurriculumTemplate INSERT
        MockCurriculumTemplate template = MockCurriculumTemplate.builder()
                .weekNumber(nextWeek)
                .topic(topic)
                .description(description)
                .keywords(keywords)
                .resources(resources)
                .build();
        MockCurriculumTemplate savedTemplate = mockCurriculumTemplateRepository.save(template);

        // 6) MockTranscriptTemplate 청크별 INSERT
        List<MockTranscriptTemplate> rows = chunks.stream()
                .map(chunk -> MockTranscriptTemplate.builder()
                        .templateId(savedTemplate.getId())
                        .videoId(videoId)
                        .startSec(chunk.getStartSec())
                        .endSec(chunk.getEndSec())
                        .textContent(chunk.getText())
                        .chunkIndex(chunk.getChunkIndex())
                        .build())
                .collect(Collectors.toList());
        mockTranscriptTemplateRepository.saveAll(rows);

        log.info("목업 템플릿 시드 완료 — templateId={}, weekNumber={}, keywords={}, chunks={}",
                savedTemplate.getId(), nextWeek, keywords.size(), rows.size());

        return new MockTemplateSeedResponseDto(
                savedTemplate.getId(),
                nextWeek,
                keywords.size(),
                rows.size()
        );
    }

    /**
     * 기존 mock_curriculum_templates 의 max(week_number)+1 을 반환한다.
     * 비어있으면 1 부터 시작.
     */
    private short resolveNextWeekNumber() {
        List<MockCurriculumTemplate> existing = mockCurriculumTemplateRepository.findAllByOrderByWeekNumberAsc();
        if (existing.isEmpty()) return 1;
        short max = existing.get(existing.size() - 1).getWeekNumber();
        return (short) (max + 1);
    }

    /**
     * description LLM 호출.
     *
     * 실제 학습실 생성의 "최초 커리큘럼 생성" 단계에서 topic + description 이 같이 만들어지는 흐름을
     * 시드용으로 모방. 자막을 보기 전(=실제 흐름과 동일하게) topic 만으로 1~2문장 요약을 작성한다.
     *
     * 실패 시 폴백: topic 을 그대로 반환.
     */
    private String generateDescription(String topic) {
        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 주차 커리큘럼 설계 AI입니다.

                역할: 학습 주차의 topic 만 보고 학습자에게 보여줄 1~2문장 요약 설명(description)을 작성합니다.

                ■ 출력 형식: 순수 JSON (코드블록/마크다운 금지)
                {"description": "한두 줄 요약"}

                ■ description 규칙
                1. 1~2 문장(최대 120자) 이내의 한국어 요약.
                2. topic 을 그대로 복사하지 말고, 해당 주제에서 학습자가 무엇을 얻을지 함축.
                3. 모호한 일반론 대신 구체적인 학습 목표·범위를 드러낼 것.
                """;

        String userMessage = String.format("학습 주제(topic): %s", topic);

        try {
            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();
            LlmDescriptionResponse response = llmService.callJson(request, LlmDescriptionResponse.class);
            String description = response.getDescription();
            return (description != null && !description.isBlank()) ? description : topic;
        } catch (Exception e) {
            log.warn("LLM description 생성 실패 — 폴백 적용 (topic 복사): {}", e.getMessage());
            return topic;
        }
    }

    /**
     * keywords LLM 호출.
     *
     * 프롬프트 본문은 {@code CurriculumEnrichmentService.extractKeywords()} 의 원문 그대로 —
     * 실제 학습실의 키워드 추출 품질과 정확히 일치시킨다.
     *
     * 실패 시 폴백: 빈 리스트.
     */
    private List<String> extractKeywords(String topic, List<SubtitleChunk> chunks) {
        String fullText = chunks.stream()
                .map(SubtitleChunk::getText)
                .collect(Collectors.joining(" "));

        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 강의 자막 분석 AI입니다.

                역할: 한 주차의 전체 강의 자막에서 **시험 출제·복습·매칭에 활용 가능한 핵심 학습 키워드**를 정제해 추출합니다.
                이 키워드 리스트는 이후 (a) 거꾸로 학습 평가의 허용 키워드 필터, (b) 파이널 퀴즈 생성의 소재, (c) 멘토-멘티 매칭의 기준으로 사용됩니다. 따라서 학술적 정확성과 범위 일관성이 최우선입니다.

                ■ 출력 형식: 순수 JSON (코드블록/마크다운 금지)
                {"keywords": ["키워드1", "키워드2", ...]}

                ■ 필수 규칙
                1. 개수는 5~10개. 주차 주제 전체를 균형 있게 대표하도록 선정.
                2. 각 키워드는 명사/명사구 단위. 영문 원어가 일반적으로 통용되면 "한글(영문)" 형태 권장 (예: "정규화(Normalization)"). 괄호가 어색하면 한글 단독 허용.
                3. 학습 단위가 되는 **개념/원리/알고리즘/모델 이름**을 우선. 예시·실습 코드의 라이브러리명·변수명·인명·브랜드는 제외.
                4. 지나치게 포괄적인 단어(예: "프로그래밍", "데이터")는 제외. 구체적·차별화된 용어로.
                5. 중복·동의어·표기 변형은 1개로 통합.
                6. 키워드가 전혀 파악되지 않으면 빈 배열 반환: {"keywords": []}

                ■ 금지 사항
                - 강의 속 진행 멘트·인사·광고 문구를 키워드화하지 말 것.
                - 문장/구절을 그대로 옮겨 넣지 말 것 (명사구 단위로 정제).
                """;

        String userMessage = String.format(
                "학습 주제: %s\n\n강의 자막 전문:\n%s",
                topic, fullText
        );

        try {
            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();
            LlmKeywordResponse response = llmService.callJson(request, LlmKeywordResponse.class);
            return response.getKeywords() != null ? response.getKeywords() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("LLM keywords 추출 실패 — 폴백 적용 (빈 리스트): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** description LLM 응답 매핑용 내부 DTO. */
    @Getter
    @NoArgsConstructor
    static class LlmDescriptionResponse {
        @JsonProperty("description")
        private String description;
    }

    /** keywords LLM 응답 매핑용 내부 DTO. */
    @Getter
    @NoArgsConstructor
    static class LlmKeywordResponse {
        private List<String> keywords;
    }
}
