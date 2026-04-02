package com.moai.backend.domain.curriculum.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moai.backend.domain.curriculum.entity.CurriculumResource;
import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.transcript.entity.VideoTranscript;
import com.moai.backend.domain.transcript.repository.VideoTranscriptRepository;
import com.moai.backend.global.llm.LlmRequestDto;
import com.moai.backend.global.llm.LlmService;
import com.moai.backend.global.material.MaterialContent;
import com.moai.backend.global.material.MaterialGeneratorService;
import com.moai.backend.global.s3.S3Service;
import com.moai.backend.global.subtitle.SubtitleChunkDto;
import com.moai.backend.global.subtitle.SubtitleScraperService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurriculumEnrichmentService {

    private final WeeklyCurriculumRepository weeklyCurriculumRepository;
    private final VideoTranscriptRepository videoTranscriptRepository;
    private final LlmService llmService;
    private final SubtitleScraperService subtitleScraperService;
    private final MaterialGeneratorService materialGeneratorService;
    private final S3Service s3Service;

    /**
     * 모든 주차에 대해 비동기 enrichment를 트리거한다.
     * 각 주차는 독립적인 @Async 스레드에서 병렬 실행된다.
     */
    public void enrichAllWeeks(List<WeeklyCurriculum> curriculums) {
        for (WeeklyCurriculum curriculum : curriculums) {
            enrichWeek(curriculum.getId());
        }
    }

    /**
     * 주차별 비동기 enrichment 파이프라인.
     * Step A → Step B → Step C -> Step D 순차 실행. 각 단계 실패 시 해당 주차를 스킵한다.
     *
     * 각 @Async 메서드는 별도 스레드에서 실행되므로 독립적인 트랜잭션 경계를 갖는다.
     * 단계별로 개별 저장하여, 이전 단계 결과는 다음 단계가 실패해도 유지된다.
     */
    @Async("curriculumTaskExecutor")
    @Transactional
    public void enrichWeek(String curriculumId) {
        WeeklyCurriculum curriculum = weeklyCurriculumRepository.findById(curriculumId)
                .orElse(null);
        if (curriculum == null) {
            log.warn("enrichment 대상 커리큘럼을 찾을 수 없음: curriculumId={}", curriculumId);
            return;
        }

        log.info("[{}주차] enrichment 시작 — topic: {}", curriculum.getWeekNumber(), curriculum.getTopic());

        // Step A: LLM을 통해 YouTube video_id 추천받기
        String videoId = recommendVideo(curriculum);
        if (videoId == null) {
            // 영상 추천 실패 → resources 빈 배열 저장, Step B/C 스킵
            curriculum.updateResources(List.of());
            weeklyCurriculumRepository.save(curriculum);
            log.warn("[{}주차] 영상 추천 실패 — enrichment 스킵", curriculum.getWeekNumber());
            return;
        }

        // video_id를 resources JSON에 저장
        CurriculumResource resource = new CurriculumResource(
                "youtube", videoId, curriculum.getTopic(), null, null
        );
        curriculum.updateResources(List.of(resource));
        weeklyCurriculumRepository.save(curriculum);

        // Step B: YouTube 자막 스크래핑
        // 실패해도 파이프라인을 중단하지 않고 Step C/D로 계속 진행한다.
        List<SubtitleChunkDto> chunks = List.of();
        int transcriptCount = 0;
        try {
            chunks = subtitleScraperService.scrape(videoId);
            if (chunks.isEmpty()) {
                log.warn("[{}주차] 자막 스크래핑 결과 비어있음 (videoId={}) — Step C 스킵, Step D 계속 진행",
                        curriculum.getWeekNumber(), videoId);
            } else {
                // 자막 청크를 VideoTranscript 엔티티로 변환하여 일괄 저장
                List<VideoTranscript> transcripts = chunks.stream()
                        .map(chunk -> VideoTranscript.builder()
                                .curriculum(curriculum)
                                .videoId(videoId)
                                .startSec(chunk.getStartSec())
                                .endSec(chunk.getEndSec())
                                .textContent(chunk.getText())
                                .chunkIndex(chunk.getChunkIndex())
                                .build())
                        .toList();
                videoTranscriptRepository.saveAll(transcripts);
                transcriptCount = transcripts.size();
            }
        } catch (Exception e) {
            log.warn("[{}주차] 자막 스크래핑 중 예외 발생 (videoId={}) — Step C 스킵, Step D 계속 진행: {}",
                    curriculum.getWeekNumber(), videoId, e.getMessage());
        }

        // Step C: 자막 텍스트 기반 LLM 키워드 추출
        // 자막이 있을 때만 실행. 실패해도 Step D로 계속 진행한다.
        List<String> keywords = null;
        if (!chunks.isEmpty()) {
            keywords = extractKeywords(curriculum, chunks);
            if (keywords != null && !keywords.isEmpty()) {
                curriculum.updateKeywords(keywords);
                weeklyCurriculumRepository.save(curriculum);
            }
        }

        // Step D: LLM 학습 자료 생성 → PDF 변환 → S3 업로드 → resources에 추가
        // Step B/C가 실패해도 topic/description만으로 자료 생성 가능
        generateAndUploadMaterials(curriculum);

        log.info("[{}주차] enrichment 완료 — videoId: {}, transcripts: {}개, keywords: {}",
                curriculum.getWeekNumber(), videoId, transcriptCount,
                keywords != null ? keywords.size() + "개" : "추출 실패");
    }

    // --- Step A: LLM YouTube 영상 추천 ---

    private String recommendVideo(WeeklyCurriculum curriculum) {
        try {
            String systemPrompt = "당신은 교육 영상 추천 전문가입니다. "
                    + "주어진 학습 주제에 가장 적합한 YouTube 영상의 video_id를 추천해주세요. "
                    + "실제로 존재하는 한국어 교육 영상을 추천해야 합니다. "
                    + "반드시 아래 JSON 형식만 출력하세요:\n"
                    + "{\"video_id\": \"영상ID\", \"title\": \"영상 제목\"}";

            String userMessage = String.format(
                    "학습 주제: %s\n상세 설명: %s",
                    curriculum.getTopic(), curriculum.getDescription()
            );

            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();

            LlmVideoResponse response = llmService.callJson(request, LlmVideoResponse.class);
            return response.getVideoId();
        } catch (Exception e) {
            log.warn("[{}주차] LLM 영상 추천 실패: {}", curriculum.getWeekNumber(), e.getMessage());
            return null;
        }
    }

    // --- Step C: LLM 키워드 추출 ---

    private List<String> extractKeywords(WeeklyCurriculum curriculum, List<SubtitleChunkDto> chunks) {
        try {
            // 전체 자막 텍스트를 하나로 합침
            String fullText = chunks.stream()
                    .map(SubtitleChunkDto::getText)
                    .collect(Collectors.joining(" "));

            String systemPrompt = "당신은 교육 콘텐츠 분석 전문가입니다. "
                    + "주어진 강의 자막에서 핵심 학습 키워드를 5~10개 추출해주세요. "
                    + "반드시 아래 JSON 형식만 출력하세요:\n"
                    + "{\"keywords\": [\"키워드1\", \"키워드2\", ...]}";

            String userMessage = String.format(
                    "학습 주제: %s\n\n강의 자막:\n%s",
                    curriculum.getTopic(), fullText
            );

            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();

            LlmKeywordResponse response = llmService.callJson(request, LlmKeywordResponse.class);
            return response.getKeywords();
        } catch (Exception e) {
            log.warn("[{}주차] LLM 키워드 추출 실패: {}", curriculum.getWeekNumber(), e.getMessage());
            return null;
        }
    }

    // --- Step D: LLM 학습 자료 생성 + PDF 변환 + S3 업로드 ---

    /**
     * LLM으로 학습 자료 콘텐츠를 생성한 뒤, PDF로 변환하여 S3에 업로드한다.
     * LLM 호출 실패 시 파일 생성을 스킵하지만, PDF URL이 존재하면 항상 DB에 저장한다.
     */
    private void generateAndUploadMaterials(WeeklyCurriculum curriculum) {
        // 1. LLM으로 구조화된 학습 자료 콘텐츠 생성
        MaterialContent content = generateMaterialContent(curriculum);
        if (content == null) {
            log.warn("[{}주차] 학습 자료 콘텐츠 생성 실패 — PDF 생성 스킵", curriculum.getWeekNumber());
            return;
        }

        // 기존 resources(YouTube 영상 등)를 유지하면서 PDF를 추가
        List<CurriculumResource> resources = new ArrayList<>(
                curriculum.getResources() != null ? curriculum.getResources() : List.of()
        );

        String materialTitle = curriculum.getWeekNumber() + "주차 학습 자료";
        String s3Directory = "materials/" + curriculum.getRoom().getId();
        String fileBaseName = curriculum.getId();

        // 2. PDF 생성 → S3 업로드
        try {
            byte[] pdfBytes = materialGeneratorService.generatePdf(content);
            String pdfUrl = s3Service.upload(
                    s3Directory, fileBaseName + ".pdf", pdfBytes, "application/pdf"
            );
            String pdfSize = formatFileSize(pdfBytes.length);
            resources.add(new CurriculumResource("pdf", null, materialTitle, pdfUrl, pdfSize));
            log.info("[{}주차] PDF 업로드 완료 — size: {}", curriculum.getWeekNumber(), pdfSize);
        } catch (Exception e) {
            log.warn("[{}주차] PDF 생성/업로드 실패: {}", curriculum.getWeekNumber(), e.getMessage());
        }

        // 3. resources JSON 업데이트 — PDF 추가 여부와 관계없이 항상 DB에 저장
        curriculum.updateResources(resources);
        weeklyCurriculumRepository.save(curriculum);
    }

    /**
     * LLM을 호출하여 학습 자료의 구조화된 콘텐츠를 생성한다.
     * keywords가 있으면 프롬프트에 포함하여 더 정확한 자료를 생성한다.
     */
    private MaterialContent generateMaterialContent(WeeklyCurriculum curriculum) {
        try {
            String systemPrompt = "당신은 학습 자료 제작 전문가입니다. "
                    + "주어진 학습 주제와 키워드를 바탕으로 체계적인 학습 자료를 생성해주세요. "
                    + "반드시 아래 JSON 형식만 출력하세요:\n"
                    + "{\"title\": \"자료 제목\", \"sections\": ["
                    + "{\"heading\": \"핵심 개념 요약\", \"content\": \"...\"},"
                    + "{\"heading\": \"주요 키워드 정리\", \"content\": \"- 키워드1: 설명\\n- 키워드2: 설명\"},"
                    + "{\"heading\": \"핵심 포인트\", \"content\": \"1. ...\\n2. ...\"},"
                    + "{\"heading\": \"연습 문제\", \"content\": \"1. ...\\n2. ...\"}"
                    + "]}";

            // 키워드가 있으면 프롬프트에 포함 (Step C 성공 시)
            String keywordsLine = "";
            if (curriculum.getKeywords() != null && !curriculum.getKeywords().isEmpty()) {
                keywordsLine = "\n핵심 키워드: " + String.join(", ", curriculum.getKeywords());
            }

            String userMessage = String.format(
                    "학습 주제: %s\n상세 설명: %s%s",
                    curriculum.getTopic(), curriculum.getDescription(), keywordsLine
            );

            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();

            return llmService.callJson(request, MaterialContent.class);
        } catch (Exception e) {
            log.warn("[{}주차] LLM 학습 자료 생성 실패: {}", curriculum.getWeekNumber(), e.getMessage());
            return null;
        }
    }

    /**
     * 바이트 크기를 사람이 읽기 쉬운 형태로 변환한다. (예: "245KB", "1.2MB")
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + "KB";
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    // --- LLM 응답 DTO (내부 클래스) ---

    @Getter
    @NoArgsConstructor
    static class LlmVideoResponse {
        @JsonProperty("video_id")
        private String videoId;
        private String title;
    }

    @Getter
    @NoArgsConstructor
    static class LlmKeywordResponse {
        private List<String> keywords;
    }
}
