package com.moai.backend.domain.curriculum.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moai.backend.domain.curriculum.entity.CurriculumResource;
import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.quiz.entity.Quiz;
import com.moai.backend.domain.quiz.entity.QuizOption;
import com.moai.backend.domain.quiz.entity.QuizQuestion;
import com.moai.backend.domain.quiz.repository.QuizQuestionRepository;
import com.moai.backend.domain.quiz.repository.QuizRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

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
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;

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
                videoMeta.durationSec(), videoMeta.viewCount(), null
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

    // --- Step A: Algorithmic YouTube 영상 추천 ---

    private YoutubeApiService.VideoMeta recommendVideo(WeeklyCurriculum curriculum, WeekEnrichmentContext context) {
        if (!youtubeApiService.isEnabled()) return null;

        String subject = nullSafe(context.subject()).trim();
        String topic = curriculum.getTopic() != null ? curriculum.getTopic().replaceAll("(?i)^week\\s*\\d+\\s*[:\\-]\\s*", "").trim() : "";
        String fullBase = (subject + " " + topic).replaceAll("\\s+", " ").trim();
        String searchQuery = nullSafe(context.youtubeSearchQuery());

        List<String> queries = new ArrayList<>();
        if (!searchQuery.isBlank()) queries.add(searchQuery);
        if (!fullBase.isBlank()) {
            queries.add(fullBase + " 개념 강의");
            queries.add(fullBase);
        }

        Pattern problemPattern = Pattern.compile("기출|문제|문제풀이|풀이|해설|모의고사|예상문제|암기법|벼락치기|합격후기|공부법|shorts|쇼츠", Pattern.CASE_INSENSITIVE);
        Pattern metaPattern = Pattern.compile("시험 정보|응시자격|공부법|합격 전략|빠르게 요약|초단기|한방 정리|오리엔테이션", Pattern.CASE_INSENSITIVE);
        Pattern conceptPattern = Pattern.compile("강의|개념|이론|원리|기초|입문|정리|소프트웨어 설계|객체지향|데이터베이스|정규화|프로그래밍 언어|운영체제|네트워크|보안", Pattern.CASE_INSENSITIVE);

        List<YoutubeApiService.VideoMeta> allCandidates = new ArrayList<>();
        for (String query : queries) {
            if (query.isBlank()) continue;
            var results = youtubeApiService.searchVideos(query, 6);
            allCandidates.addAll(results);
            if (!results.isEmpty() && allCandidates.size() >= 10) break;
        }

        YoutubeApiService.VideoMeta bestVideo = null;
        int bestScore = -9999;

        for (YoutubeApiService.VideoMeta video : allCandidates) {
            String title = video.title() != null ? video.title() : "";
            if (problemPattern.matcher(title).find()) continue;
            if (metaPattern.matcher(title).find()) continue;

            int score = 0;
            if (video.hasCaptions()) score += 10; // 수동 자막 보너스

            long duration = video.durationSec() != null ? video.durationSec() : 0;
            if (duration >= 1200 && duration <= 7200) {
                score += 15;
            } else if (duration >= 600) {
                score += 5;
            } else if (duration < 180) {
                score -= 15;
            }

            if (conceptPattern.matcher(title).find()) score += 10;

            String titleLower = title.toLowerCase();
            if (!subject.isBlank() && titleLower.contains(subject.toLowerCase())) score += 5;

            for (String part : topic.split("[\\s,]+")) {
                if (part.length() >= 2 && titleLower.contains(part.toLowerCase())) score += 5;
            }

            if (score > bestScore) {
                bestScore = score;
                bestVideo = video;
            }
        }

        if (bestVideo != null && bestScore >= 0) {
            log.info("[{}주차] 유튜브 매칭 성공: {} (score: {})", curriculum.getWeekNumber(), bestVideo.title(), bestScore);
            return bestVideo;
        }

        // 조건 매칭 실패 시 폴백 — 검색 단계에서 이미 자막 있는 영상만 후보로 존재
        log.info("[{}주차] 엄격한 매칭 실패, 폴백으로 첫번째 검색 결과 사용", curriculum.getWeekNumber());
        return allCandidates.isEmpty() ? null : allCandidates.get(0);
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
        String subject = nullSafe(context.subject()).replaceAll("[\\s/]", "_");
        String levelKo = switch (nullSafe(context.level())) {
            case "beginner" -> "기초";
            case "intermediate" -> "중급";
            case "advanced" -> "고급";
            default -> nullSafe(context.level());
        };
        int totalWeeks = curriculum.getRoom().getDurationWeeks();
        String s3Directory = "materials/" + subject + "_" + levelKo + "_" + totalWeeks + "주";
        String fileBaseName = curriculum.getWeekNumber() + "주차_" + curriculum.getTopic().replaceAll("[\\s/\\[\\]]", "_");

        // 2. Markdown 생성 → S3 업로드
        try {
            byte[] mdBytes = materialGeneratorService.generateMarkdown(content);
            String mdUrl = s3Service.upload(
                    s3Directory, fileBaseName + ".md", mdBytes, "text/markdown"
            );
            String mdSize = formatFileSize(mdBytes.length);
            if (mdUrl != null) {
                resources.add(new CurriculumResource("md", null, materialTitle, mdUrl, mdSize, null, null, null));
                log.info("[{}주차] Markdown 업로드 완료 — size: {}", curriculum.getWeekNumber(), mdSize);
            } else {
                log.info("[{}주차] Markdown 생성 완료 ({}) — S3 비활성화로 저장 스킵", curriculum.getWeekNumber(), mdSize);
            }
        } catch (Exception e) {
            log.warn("[{}주차] Markdown 생성/업로드 실패: {}", curriculum.getWeekNumber(), e.getMessage());
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

    // --- 약점 키워드 보충 enrichment ---

    /**
     * 파이널 퀴즈 완료 후 미해소 약점 키워드가 있을 때 다음 주차에 보충 콘텐츠를 추가한다.
     * 영상(최소 수로 커버), 통합 학습 자료 1개, 약점 퀴즈 1세트를 생성한다.
     */
    @Async("curriculumTaskExecutor")
    @Transactional
    public void enrichWithWeaknessKeywords(String curriculumId, List<String> weaknessKeywords,
                                            String subject, String level) {
        if (weaknessKeywords == null || weaknessKeywords.isEmpty()) return;

        WeeklyCurriculum curriculum = weeklyCurriculumRepository.findById(curriculumId).orElse(null);
        if (curriculum == null) {
            log.warn("weakness enrichment 대상 커리큘럼을 찾을 수 없음: curriculumId={}", curriculumId);
            return;
        }

        // 이미 약점 보충 콘텐츠가 있으면 스킵 (멱등성)
        boolean alreadyEnriched = curriculum.getResources() != null &&
                curriculum.getResources().stream().anyMatch(r -> "weakness".equals(r.getTag()));
        if (alreadyEnriched) {
            log.info("[{}주차] 약점 보충 enrichment 이미 완료됨 — 스킵", curriculum.getWeekNumber());
            return;
        }

        log.info("[{}주차] 약점 키워드 보충 enrichment 시작 — keywords: {}", curriculum.getWeekNumber(), weaknessKeywords);

        // Step W-A: 약점 키워드별 YouTube 영상 검색 (videoId 기준 중복 제거)
        List<CurriculumResource> weaknessVideos = findVideosForWeaknessKeywords(weaknessKeywords, subject);
        if (!weaknessVideos.isEmpty()) {
            List<CurriculumResource> resources = new ArrayList<>(
                    curriculum.getResources() != null ? curriculum.getResources() : List.of());
            resources.addAll(weaknessVideos);
            curriculum.updateResources(resources);
            weeklyCurriculumRepository.save(curriculum);
        }

        // Step W-B: 약점 키워드 통합 학습 자료 1개 생성
        generateAndUploadWeaknessMaterial(curriculum, weaknessKeywords, subject, level);

        // Step W-C: 약점 퀴즈 생성
        generateWeaknessQuiz(curriculum, weaknessKeywords);

        log.info("[{}주차] 약점 키워드 보충 enrichment 완료 — {}개 키워드, {}개 영상",
                curriculum.getWeekNumber(), weaknessKeywords.size(), weaknessVideos.size());
    }

    private List<CurriculumResource> findVideosForWeaknessKeywords(List<String> keywords, String subject) {
        if (!youtubeApiService.isEnabled()) return List.of();

        // videoId 기준으로 중복 제거하며 탐색 — 한 영상이 여러 키워드를 커버하면 한 번만 추가
        Map<String, YoutubeApiService.VideoMeta> byVideoId = new LinkedHashMap<>();

        for (String keyword : keywords) {
            String query = nullSafe(subject).isBlank()
                    ? keyword + " 개념 강의"
                    : subject + " " + keyword + " 개념 강의";
            List<YoutubeApiService.VideoMeta> results = youtubeApiService.searchVideos(query, 5);
            if (results.isEmpty()) {
                results = youtubeApiService.searchVideos(keyword + " 강의", 3);
            }

            YoutubeApiService.VideoMeta best = results.stream()
                    .filter(YoutubeApiService.VideoMeta::hasCaptions)
                    .findFirst()
                    .orElse(results.isEmpty() ? null : results.get(0));

            if (best != null) {
                byVideoId.putIfAbsent(best.videoId(), best);
            }
        }

        return byVideoId.values().stream()
                .map(v -> new CurriculumResource(
                        "youtube", v.videoId(), v.title(), null, null,
                        v.durationSec(), v.viewCount(), "weakness"))
                .toList();
    }

    private void generateAndUploadWeaknessMaterial(WeeklyCurriculum curriculum,
                                                    List<String> weaknessKeywords,
                                                    String subject, String level) {
        try {
            String systemPrompt = """
                    당신은 MoAI 학습 플랫폼의 학습 보충 자료 생성 전문가 AI입니다.

                    학습자가 파이널 퀴즈에서 부족했던 약점 키워드들을 집중 보완하는 학습 자료를 생성하세요.

                    ■ 출력 형식: 순수 JSON (코드블록/마크다운 절대 금지)
                    {"study_material": "마크다운 학습 자료 전문"}

                    ■ 학습 자료 필수 구성:
                    ### 🔁 약점 보충 학습 목표
                    (왜 이 키워드들이 부족했는지, 무엇을 보완해야 하는지 1문단)

                    ### 키워드별 심화 정리
                    (각 약점 키워드마다 정의→원리→예시→자주 하는 실수 구조로 최소 300자 이상 상세 서술)

                    ### 비교 정리표
                    (키워드들 간 차이점·공통점 마크다운 표)

                    ### 핵심 암기 포인트 & 체크리스트

                    ■ 필수 규칙:
                    1. 전체 2500자 이상
                    2. 학습자가 틀렸던 개념에 특히 집중
                    3. 모든 내용 한국어, 전문 용어는 영어 병기 (예: 정규화(Normalization))
                    """;

            String userMessage = String.format(
                    "과목: %s\n난이도: %s\n%d주차 약점 키워드: %s",
                    nullSafe(subject), nullSafe(level),
                    (int) curriculum.getWeekNumber(),
                    String.join(", ", weaknessKeywords)
            );

            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();

            WeaknessMaterialResponse response = llmService.callJson(request, WeaknessMaterialResponse.class);
            if (response == null || response.getStudyMaterial() == null || response.getStudyMaterial().isBlank()) {
                log.warn("[{}주차] 약점 학습 자료 LLM 응답 없음", curriculum.getWeekNumber());
                return;
            }

            MaterialContent content = new MaterialContent(
                    curriculum.getWeekNumber() + "주차 약점 보충 자료",
                    List.of(new MaterialContent.Section("약점 보충 학습", response.getStudyMaterial()))
            );

            String subjectKey = nullSafe(subject).replaceAll("[\\s/]", "_");
            String levelKo = switch (nullSafe(level)) {
                case "beginner" -> "기초";
                case "intermediate" -> "중급";
                case "advanced" -> "고급";
                default -> nullSafe(level);
            };
            int totalWeeks = curriculum.getRoom().getDurationWeeks();
            String s3Directory = "materials/" + subjectKey + "_" + levelKo + "_" + totalWeeks + "주";
            String keywordsSlug = weaknessKeywords.stream()
                    .limit(3)
                    .map(k -> k.replaceAll("[\\s/\\[\\]()]", "_"))
                    .collect(Collectors.joining("_"));
            String fileBaseName = curriculum.getWeekNumber() + "주차_약점보충_" + keywordsSlug;
            String materialTitle = curriculum.getWeekNumber() + "주차 약점 보충 자료";

            List<CurriculumResource> resources = new ArrayList<>(
                    curriculum.getResources() != null ? curriculum.getResources() : List.of());

            try {
                byte[] mdBytes = materialGeneratorService.generateMarkdown(content);
                String mdUrl = s3Service.upload(s3Directory, fileBaseName + ".md", mdBytes, "text/markdown");
                String mdSize = formatFileSize(mdBytes.length);
                if (mdUrl != null) {
                    resources.add(new CurriculumResource("md", null, materialTitle, mdUrl, mdSize, null, null, "weakness"));
                    log.info("[{}주차] 약점 Markdown 업로드 완료 — size: {}", curriculum.getWeekNumber(), mdSize);
                } else {
                    resources.add(new CurriculumResource("md", null, materialTitle, null, mdSize, null, null, "weakness"));
                    log.info("[{}주차] 약점 Markdown 생성 완료 ({}) — S3 비활성화로 저장 스킵", curriculum.getWeekNumber(), mdSize);
                }
            } catch (Exception e) {
                log.warn("[{}주차] 약점 Markdown 생성/업로드 실패: {}", curriculum.getWeekNumber(), e.getMessage());
            }

            curriculum.updateResources(resources);
            weeklyCurriculumRepository.save(curriculum);

        } catch (Exception e) {
            log.warn("[{}주차] 약점 학습 자료 생성 실패: {}", curriculum.getWeekNumber(), e.getMessage());
        }
    }

    private void generateWeaknessQuiz(WeeklyCurriculum curriculum, List<String> weaknessKeywords) {
        try {
            if (quizRepository.findByCurriculumIdAndQuizType(curriculum.getId(), "weakness_remediation").isPresent()) {
                return;
            }

            List<String> capped = weaknessKeywords.stream().limit(5).toList();

            String systemPrompt = """
                    당신은 MoAI 학습 플랫폼의 약점 보충 퀴즈 출제 AI입니다.

                    학습자가 파이널 퀴즈에서 부족했던 약점 키워드를 타겟으로 객관식 퀴즈를 생성하세요.

                    ■ 출력: 순수 JSON (코드블록 없이)
                    {
                      "questions": [
                        {
                          "question": "객관식 문제 (개념 이해를 직접 검증하는 질문)",
                          "options": [
                            {"label": "A", "text": "보기1"},
                            {"label": "B", "text": "보기2"},
                            {"label": "C", "text": "보기3"},
                            {"label": "D", "text": "보기4"}
                          ],
                          "answer": "정답라벨",
                          "related_keyword": "관련 약점 키워드",
                          "explanation": "정답 근거 (2~3문장, 오답 분석 포함)"
                        }
                      ]
                    }

                    ■ 필수 규칙:
                    1. 각 약점 키워드당 최소 1문제, 최대 5문제 총합
                    2. 4지선다 객관식, 단순 암기가 아닌 이해도 검증 질문
                    3. related_keyword는 반드시 입력된 약점 키워드 중 하나
                    4. explanation에 왜 다른 선택지가 틀렸는지도 간략히 포함
                    """;

            String userMessage = String.format(
                    "주차 주제: %s\n약점 키워드: %s",
                    curriculum.getTopic(), String.join(", ", capped)
            );

            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();

            WeaknessQuizResponse response = llmService.callJson(request, WeaknessQuizResponse.class);
            if (response == null || response.getQuestions() == null || response.getQuestions().isEmpty()) {
                log.warn("[{}주차] 약점 퀴즈 LLM 응답 없음", curriculum.getWeekNumber());
                return;
            }

            Quiz quiz = Quiz.builder()
                    .curriculum(curriculum)
                    .quizType("weakness_remediation")
                    .title(curriculum.getWeekNumber() + "주차 약점 보충 퀴즈")
                    .build();
            quizRepository.save(quiz);

            short order = 1;
            for (WeaknessQuizResponse.QuestionItem qi : response.getQuestions()) {
                List<QuizOption> options = qi.getOptions() == null ? List.of() :
                        qi.getOptions().stream()
                                .map(o -> new QuizOption(o.getLabel(), o.getText()))
                                .toList();

                QuizQuestion question = QuizQuestion.builder()
                        .quiz(quiz)
                        .questionType("multiple")
                        .question(qi.getQuestion())
                        .options(options)
                        .answer(qi.getAnswer())
                        .questionOrder(order++)
                        .relatedKeyword(qi.getRelatedKeyword())
                        .build();
                quizQuestionRepository.save(question);
            }

            log.info("[{}주차] 약점 퀴즈 생성 완료 — {}문제", curriculum.getWeekNumber(), response.getQuestions().size());
        } catch (Exception e) {
            log.warn("[{}주차] 약점 퀴즈 생성 실패: {}", curriculum.getWeekNumber(), e.getMessage());
        }
    }

    // --- LLM 응답 DTO (내부 클래스) ---

    @Getter
    @NoArgsConstructor
    static class WeaknessMaterialResponse {
        @JsonProperty("study_material")
        private String studyMaterial;
    }

    @Getter
    @NoArgsConstructor
    static class WeaknessQuizResponse {
        private List<QuestionItem> questions;

        @Getter
        @NoArgsConstructor
        static class QuestionItem {
            private String question;
            private List<OptionItem> options;
            private String answer;

            @JsonProperty("related_keyword")
            private String relatedKeyword;

            private String explanation;
        }

        @Getter
        @NoArgsConstructor
        static class OptionItem {
            private String label;
            private String text;
        }
    }

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
