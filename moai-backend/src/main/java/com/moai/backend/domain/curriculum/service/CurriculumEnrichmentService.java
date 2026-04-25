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
import com.moai.backend.global.youtube.YoutubeApiService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
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
    private final YoutubeApiService youtubeApiService;

    public record WeekEnrichmentContext(
            String curriculumId,
            String topic,
            String weeklySummary,
            List<String> learningObjectives,
            List<String> keyConcepts,
            List<String> focusQuestions,
            List<String> practiceKeywords,
            String youtubeSearchQuery,
            String subject,
            String level
    ) {}

    /**
     * 주차별 비동기 enrichment 파이프라인.
     * Step A → Step B → Step C -> Step D 순차 실행. 각 단계 실패 시 해당 주차를 스킵한다.
     *
     * 각 @Async 메서드는 별도 스레드에서 실행되므로 독립적인 트랜잭션 경계를 갖는다.
     * 단계별로 개별 저장하여, 이전 단계 결과는 다음 단계가 실패해도 유지된다.
     */
    @Async("curriculumTaskExecutor")
    @Transactional
    public void enrichWeek(WeekEnrichmentContext context) {
        WeeklyCurriculum curriculum = weeklyCurriculumRepository.findById(context.curriculumId())
                .orElse(null);
        if (curriculum == null) {
            log.warn("enrichment 대상 커리큘럼을 찾을 수 없음: curriculumId={}", context.curriculumId());
            return;
        }

        log.info("[{}주차] enrichment 시작 — topic: {}", curriculum.getWeekNumber(), curriculum.getTopic());

        // Step A: LLM을 통해 YouTube video_id 추천받기
        YoutubeApiService.VideoMeta videoMeta = recommendVideo(curriculum, context);
        if (videoMeta == null) {
            // 영상 추천 실패 → resources 빈 배열 저장, Step B/C 스킵
            curriculum.updateResources(List.of());
            weeklyCurriculumRepository.save(curriculum);
            log.warn("[{}주차] 영상 추천 실패 — enrichment 스킵", curriculum.getWeekNumber());
            return;
        }

        String videoId = videoMeta.videoId();
        String videoTitle = (videoMeta.title() != null && !videoMeta.title().isBlank())
                ? videoMeta.title() : curriculum.getTopic();

        // video_id를 resources JSON에 저장 (duration/viewCount 포함)
        CurriculumResource resource = new CurriculumResource(
                "youtube", videoId, videoTitle, null, null,
                videoMeta.durationSec(), videoMeta.viewCount()
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
        generateAndUploadMaterials(curriculum, context);

        log.info("[{}주차] enrichment 완료 — videoId: {}, transcripts: {}개, keywords: {}",
                curriculum.getWeekNumber(), videoId, transcriptCount,
                keywords != null ? keywords.size() + "개" : "추출 실패");
    }

    // --- Step A: LLM YouTube 영상 추천 ---

    private YoutubeApiService.VideoMeta recommendVideo(WeeklyCurriculum curriculum, WeekEnrichmentContext context) {
        try {
            String systemPrompt = """
                    당신은 MoAI 학습 플랫폼의 한국어 YouTube 교육 영상 큐레이션 전문가입니다.

                    주어진 학습 주제와 주차 맥락에 가장 잘 부합하는 실제 한국어 교육 영상 video_id 1개를 추천하세요.

                    ■ 출력 형식: 순수 JSON (코드블록/마크다운 절대 금지)
                    {"video_id": "영상ID", "title": "영상 제목"}

                    ■ 필수 규칙:
                    1. 실제 존재하는 한국어 교육 영상만 추천 (광고/예능 제외)
                    2. 11자 YouTube video_id만 반환 (URL 전체 금지)
                    3. 주차 학습 목표/핵심 키워드/추천 검색어를 종합적으로 고려
                    """;

            String objectives = joinLines(context.learningObjectives());
            String keyConcepts = joinCsv(context.keyConcepts());
            String searchQuery = nullSafe(context.youtubeSearchQuery());
            String summary = nullSafe(context.weeklySummary());

            String userMessage = String.format(
                    "과목: %s\n난이도: %s\n학습 주제: %s\n주차 요약: %s\n핵심 키워드: %s\n학습 목표:\n%s\n추천 검색어: %s",
                    nullSafe(context.subject()),
                    nullSafe(context.level()),
                    curriculum.getTopic(),
                    summary,
                    keyConcepts,
                    objectives,
                    searchQuery
            );

            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();

            LlmVideoResponse response = llmService.callJson(request, LlmVideoResponse.class);
            return verifyOrSearchVideoId(curriculum, context, response.getVideoId(), response.getTitle());
        } catch (Exception e) {
            log.warn("[{}주차] LLM 영상 추천 실패: {}", curriculum.getWeekNumber(), e.getMessage());
            return null;
        }
    }

    private YoutubeApiService.VideoMeta verifyOrSearchVideoId(
            WeeklyCurriculum curriculum, WeekEnrichmentContext context,
            String llmVideoId, String llmTitle) {

        if (!youtubeApiService.isEnabled()) {
            if (llmVideoId == null || llmVideoId.isBlank()) return null;
            String title = (llmTitle != null && !llmTitle.isBlank()) ? llmTitle : curriculum.getTopic();
            return new YoutubeApiService.VideoMeta(llmVideoId, title, null, true, null, null);
        }

        if (llmVideoId != null && !llmVideoId.isBlank()) {
            var verified = youtubeApiService.verifyVideo(llmVideoId);
            if (verified.isPresent()) {
                return verified.get();
            }
            log.info("[{}주차] LLM 추천 videoId 검증 실패 ({}), 검색 폴백 시도",
                    curriculum.getWeekNumber(), llmVideoId);
        }

        String query = nullSafe(context.youtubeSearchQuery());
        if (query.isBlank()) query = curriculum.getTopic();
        var candidates = youtubeApiService.searchVideos(query, 5);
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }

        // 모든 API 시도 실패 → LLM 결과를 메타 없이 그대로 사용
        if (llmVideoId == null || llmVideoId.isBlank()) return null;
        String title = (llmTitle != null && !llmTitle.isBlank()) ? llmTitle : curriculum.getTopic();
        return new YoutubeApiService.VideoMeta(llmVideoId, title, null, true, null, null);
    }

    // --- Step C: LLM 키워드 추출 ---

    private List<String> extractKeywords(WeeklyCurriculum curriculum, List<SubtitleChunkDto> chunks) {
        try {
            // 전체 자막 텍스트를 하나로 합침
            String fullText = chunks.stream()
                    .map(SubtitleChunkDto::getText)
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
    private void generateAndUploadMaterials(WeeklyCurriculum curriculum, WeekEnrichmentContext context) {
        // 1. LLM으로 구조화된 학습 자료 콘텐츠 생성
        MaterialContent content = generateMaterialContent(curriculum, context);
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
            resources.add(new CurriculumResource("pdf", null, materialTitle, pdfUrl, pdfSize, null, null));
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
    private MaterialContent generateMaterialContent(WeeklyCurriculum curriculum, WeekEnrichmentContext context) {
        try {
            String systemPrompt = """
                    당신은 MoAI 학습 플랫폼의 학습 콘텐츠 생성 전문가 AI입니다.

                    아래 주차 정보를 바탕으로 상세 학습 콘텐츠를 생성하세요.

                    ■ 출력 형식: 순수 JSON (코드블록/마크다운 절대 금지)
                    {
                      "week_number": N,
                      "study_material": "## 마크다운 학습 자료 — 아래 구조 필수\\n\\n### 📌 이번 주차 전체 지도\\n(앞주차와의 연결, 이번 주 범위, 학습 순서를 한 문단)\\n\\n### 1️⃣~6️⃣ 핵심 개념 6개 상세 (정의 → 원리 → 예시 → 주의사항 → 시험 포인트 구조)\\n\\n### 📊 비교 정리표 2개 (마크다운 표)\\n\\n### 🧪 실전 시나리오 3개\\n\\n### 🚨 자주 하는 실수 TOP 6\\n\\n### 💡 시험 포인트 & 암기 장치\\n\\n### ✅ 이번 주차 체크리스트 6개",
                      "quiz": {
                        "questions": [
                          {"question_type":"multiple","question":"객관식 문제 (4지선다)","options":[{"label":"A","text":"보기1"},{"label":"B","text":"보기2"},{"label":"C","text":"보기3"},{"label":"D","text":"보기4"}],"answer":"정답라벨","related_keyword":"키워드","explanation":"정답 근거 + 오답 분석"}
                        ]
                      },
                      "assignment": {
                        "title": "과제 제목 (미니 실습형)",
                        "description": "10~15분 안에 끝낼 수 있는 미니 과제 안내문",
                        "submission_checklist": ["요소1", "요소2", "요소3", "요소4"],
                        "required_keywords": ["필수 키워드1", "키워드2", "키워드3", "키워드4"],
                        "example_outline": ["수행 단계 1", "수행 단계 2", "수행 단계 3"],
                        "scoring_rubric": "핵심 개념 반영 + 정확성 + 간결한 정리",
                        "max_score": 100
                      }
                    }

                    ■ 필수 규칙:
                    1. study_material: 최소 2500자 이상. 마크다운 서식은 풍부하되 JSON 안정성 우선.
                    2. study_material에는 핵심 개념 섹션, 비교 표, 실전 시나리오, 자주 하는 실수, 체크리스트를 균형 있게 포함.
                    3. quiz: 5문항 이상, 모두 4지선다 객관식. explanation은 정답 근거를 상세 서술.
                    4. assignment: 리포트형 금지, 바로 수행 가능한 미니 실습형. submission_checklist 4개 이상, required_keywords 4개 이상, example_outline 3개 이상.
                    5. assignment: scoring_rubric은 간단명료하게. 장문 보고서나 1000자 이상 제출 요구 금지.
                    6. 모든 내용은 한국어. 전문 용어는 영어 병기 (예: 원자성(Atomicity)).
                    """;

            String keywordsLine = "";
            List<String> finalKeywords = curriculum.getKeywords() != null && !curriculum.getKeywords().isEmpty()
                    ? curriculum.getKeywords()
                    : context.keyConcepts();
            if (finalKeywords != null && !finalKeywords.isEmpty()) {
                keywordsLine = "\n핵심 키워드: " + String.join(", ", finalKeywords);
            }

            String userMessage = String.format(
                    "과목: %s\n난이도: %s\n주차: %d주차\n학습 주제: %s\n주차 요약: %s\n학습 목표:\n%s\n집중 질문:\n%s\n복습 키워드: %s%s",
                    nullSafe(context.subject()),
                    nullSafe(context.level()),
                    (int) curriculum.getWeekNumber(),
                    curriculum.getTopic(),
                    nullSafe(context.weeklySummary()),
                    joinLines(context.learningObjectives()),
                    joinLines(context.focusQuestions()),
                    joinCsv(context.practiceKeywords()),
                    keywordsLine
            );

            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();

            LlmWeekDetailResponse detail = llmService.callJson(request, LlmWeekDetailResponse.class);
            return mapToMaterialContent(curriculum, detail);
        } catch (Exception e) {
            log.warn("[{}주차] LLM 학습 자료 생성 실패: {}", curriculum.getWeekNumber(), e.getMessage());
            return null;
        }
    }

    private MaterialContent mapToMaterialContent(WeeklyCurriculum curriculum, LlmWeekDetailResponse detail) {
        List<MaterialContent.Section> sections = new ArrayList<>();

        if (detail.getStudyMaterial() != null && !detail.getStudyMaterial().isBlank()) {
            sections.add(new MaterialContent.Section("학습 자료", detail.getStudyMaterial()));
        }

        if (detail.getQuiz() != null && detail.getQuiz().getQuestions() != null
                && !detail.getQuiz().getQuestions().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int idx = 1;
            for (LlmWeekDetailResponse.QuizQuestion q : detail.getQuiz().getQuestions()) {
                sb.append(idx++).append(". ").append(nullSafe(q.getQuestion())).append("\n");
                if (q.getOptions() != null) {
                    for (LlmWeekDetailResponse.QuizOption opt : q.getOptions()) {
                        sb.append("   ").append(nullSafe(opt.getLabel())).append(". ")
                                .append(nullSafe(opt.getText())).append("\n");
                    }
                }
                sb.append("   정답: ").append(nullSafe(q.getAnswer())).append("\n");
                if (q.getRelatedKeyword() != null && !q.getRelatedKeyword().isBlank()) {
                    sb.append("   관련 키워드: ").append(q.getRelatedKeyword()).append("\n");
                }
                if (q.getExplanation() != null && !q.getExplanation().isBlank()) {
                    sb.append("   해설: ").append(q.getExplanation()).append("\n");
                }
                sb.append("\n");
            }
            sections.add(new MaterialContent.Section("연습 퀴즈", sb.toString()));
        }

        if (detail.getAssignment() != null) {
            LlmWeekDetailResponse.Assignment a = detail.getAssignment();
            StringBuilder sb = new StringBuilder();
            if (a.getTitle() != null) sb.append("제목: ").append(a.getTitle()).append("\n");
            if (a.getDescription() != null) sb.append("\n").append(a.getDescription()).append("\n");
            if (a.getSubmissionChecklist() != null && !a.getSubmissionChecklist().isEmpty()) {
                sb.append("\n[제출 체크리스트]\n");
                for (String item : a.getSubmissionChecklist()) sb.append("- ").append(item).append("\n");
            }
            if (a.getRequiredKeywords() != null && !a.getRequiredKeywords().isEmpty()) {
                sb.append("\n[필수 키워드] ").append(String.join(", ", a.getRequiredKeywords())).append("\n");
            }
            if (a.getExampleOutline() != null && !a.getExampleOutline().isEmpty()) {
                sb.append("\n[수행 단계]\n");
                int i = 1;
                for (String item : a.getExampleOutline()) sb.append(i++).append(". ").append(item).append("\n");
            }
            if (a.getScoringRubric() != null) sb.append("\n[채점 기준] ").append(a.getScoringRubric()).append("\n");
            if (a.getMaxScore() != null) sb.append("[배점] ").append(a.getMaxScore()).append("점\n");
            sections.add(new MaterialContent.Section("주차 과제", sb.toString()));
        }

        if (sections.isEmpty()) {
            return null;
        }

        String title = curriculum.getWeekNumber() + "주차 학습 자료 — " + curriculum.getTopic();
        return new MaterialContent(title, sections);
    }

    /**
     * 바이트 크기를 사람이 읽기 쉬운 형태로 변환한다. (예: "245KB", "1.2MB")
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + "KB";
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private String joinLines(List<String> items) {
        if (items == null || items.isEmpty()) return "";
        return items.stream().map(i -> "- " + i).collect(Collectors.joining("\n"));
    }

    private String joinCsv(List<String> items) {
        return items == null || items.isEmpty() ? "" : String.join(", ", items);
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

    @Getter
    @NoArgsConstructor
    static class LlmWeekDetailResponse {
        @JsonProperty("week_number")
        private Integer weekNumber;

        @JsonProperty("study_material")
        private String studyMaterial;

        private Quiz quiz;

        private Assignment assignment;

        @Getter
        @NoArgsConstructor
        static class Quiz {
            private List<QuizQuestion> questions;
        }

        @Getter
        @NoArgsConstructor
        static class QuizQuestion {
            @JsonProperty("question_type")
            private String questionType;
            private String question;
            private List<QuizOption> options;
            private String answer;

            @JsonProperty("related_keyword")
            private String relatedKeyword;

            private String explanation;
        }

        @Getter
        @NoArgsConstructor
        static class QuizOption {
            private String label;
            private String text;
        }

        @Getter
        @NoArgsConstructor
        static class Assignment {
            private String title;
            private String description;

            @JsonProperty("submission_checklist")
            private List<String> submissionChecklist;

            @JsonProperty("required_keywords")
            private List<String> requiredKeywords;

            @JsonProperty("example_outline")
            private List<String> exampleOutline;

            @JsonProperty("scoring_rubric")
            private String scoringRubric;

            @JsonProperty("max_score")
            private Integer maxScore;
        }
    }
}
