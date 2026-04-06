## PHASE 1

- [x] global/llm/LlmService.java — Gemini REST API WebClient 호출 공통 모듈, JSON 응답 시 마크다운 코드블록 자동 제거 후 파싱
- [x] global/llm/LlmRequestDto.java — 시스템 프롬프트 + 유저 메시지 구조
- [x] global/llm/LlmResponseDto.java — Gemini 응답 파싱 (content 텍스트 추출)
- [x] global/llm/LlmConfig.java — WebClient Bean 설정 (llm.api-url 기반)
- [x] src/main/resources/scripts/subtitle_scraper.py — youtube-transcript-api로 자막 JSON 출력 (인자: video_id)
- [x] global/subtitle/SubtitleScraperService.java — ProcessBuilder로 Python 스크립트 호출, end_sec = start + duration 계산, 타임아웃 30초, 실패 시 빈 리스트 반환
- [x] global/s3/S3Service.java — 프로필 이미지 업로드, Presigned URL 생성
- [x] application.yml — cloud.aws.s3.bucket, cloud.aws.region 프로퍼티 추가
## PHASE 2

- [x] domain/learningroom/entity/LearningRoom.java — db_schema 02번 참조
- [x] domain/curriculum/entity/WeeklyCurriculum.java — db_schema 03번 참조, keywords/resources JSON 타입
- [x] domain/transcript/entity/VideoTranscript.java — db_schema 05번 참조
- [x] LearningRoomRepository — findByUserId, findByIdAndUserId
- [x] WeeklyCurriculumRepository — findByRoomIdOrderByWeekNumber, findByIdAndRoomId
- [x] VideoTranscriptRepository — findByCurriculumIdAndStartSecLessThanEqualAndEndSecGreaterThanEqual
- [x] GET /api/onboarding/keywords — OnboardingController + OnboardingService + OnboardingKeywordResponseDto
- [x] POST /api/learning-rooms — LearningRoomController + LearningRoomCreateRequestDto/ResponseDto
- [x] LearningRoomService 파이프라인: LearningRoom INSERT → LLM 커리큘럼 생성 → WeeklyCurriculum INSERT x N주
- [x] AsyncConfig.java — @EnableAsync + TaskExecutor 설정
- [x] @Async 파이프라인 (주차별 병렬): LLM video_id 추천 → 자막 스크래핑 → LLM 키워드 추출 순차 실행, 실패 시 해당 주차 스킵
- [x] @Async Step D: LLM 학습 자료 생성 → PDF 변환 (PDFBox) → S3 업로드 → resources JSON 추가, 독립 실패 처리
## PHASE 3

- [x] GET /api/learning-rooms — 내 학습실 목록, LearningRoomListResponseDto
- [x] GET /api/learning-rooms/{roomId}/curriculum — 전체 주차 목록, CurriculumListResponseDto
- [x] GET /api/learning-rooms/{roomId}/curriculum/{weekId} — 주차 상세, CurriculumDetailResponseDto (mainVideoId 포함)
- [x] PATCH /api/learning-rooms/{roomId}/curriculum/{weekId}/progress — 진척도 업데이트, 주차 평균으로 학습실 completionRate 자동 갱신
- [x] GET /api/learning-rooms/{roomId}/curriculum/{weekId}/recommended-videos — resources에서 youtube 타입 추출
- [x] domain/material/entity/CustomMaterial.java — db_schema 17번 참조
- [x] CustomMaterialRepository — findByRoomIdOrderByCreatedAtDesc
- [x] GET /api/learning-rooms/{roomId}/materials — 요약 자료 목록
- [x] GET /api/learning-rooms/{roomId}/materials/{materialId} — 요약 자료 상세 (summaryItems 포함)
- [x] domain/quiz/entity/Quiz.java, QuizQuestion.java, QuizAttempt.java, QuizReport.java — db_schema 07~10번
- [x] QuizRepository, QuizQuestionRepository, QuizAttemptRepository, QuizReportRepository
- [x] GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-attempts — 주차별 퀴즈 응시 이력
- [x] GET /api/quiz-attempts/{attemptId} — 퀴즈 상세 (AI 해설 포함)
## PHASE 4

### 4-1. Entity + Repository
- [x] domain/eventlog/entity/LearningEventLog.java — db_schema 06번, payload는 JSON 타입,
  video_id (VARCHAR(20), NOT NULL) 컬럼 별도 추가 (커리큘럼당 영상 여러 개이므로 조회/필터링용)
- [x] LearningEventLogRepository

### 4-2. Redis 패턴 감지 서비스
- [x] domain/eventlog/service/PatternDetectionService.java 생성
- [x] Redis 키 설계:
    - 되감기 리스트: moai:rewind:{userId}:{videoId} (TTL 10분, RPUSH)
    - 스킵 카운트: moai:skip:{userId}:{videoId} (TTL 10분, INCR)
    - 2배속 누적: moai:speedup:{userId}:{videoId} (TTL 10분, INCRBY duration_sec)
    - 쿨다운: moai:cooldown:{userId}:{videoId}:{pattern} (TTL 5분, EXISTS) — pattern = rewind | skip | speedup
- [x] 패턴1 (되감기) — RPUSH → LLEN≥3 → 최근3개 MAX-MIN≤10초 → 같은구간 반복 발동, 쿨다운 확인
- [x] 패턴3 (스킵) — INCR 후 카운트≥3이면 발동, 쿨다운 확인
- [x] 패턴4 (2배속) — 프론트에서 2배속 시청 중 10초마다 event_type="video_speed_up" 전송 (2배속 해제/영상 종료 시 잔여 초도 마지막 전송). INCRBY duration_sec 후 누적 시간≥180초(3분)이면 발동, 쿨다운 확인. payload: { video_id, speed_start_sec, playback_rate, duration_sec }

### 4-3. 패턴 발동 시 AI 처리 서비스
- [x] domain/eventlog/service/EventProcessingService.java 생성
- [x] 패턴1 발동 처리:
    1. LearningEventLog INSERT
    2. VideoTranscriptRepository 구간 자막 조회 (start_sec <= toSec AND end_sec >= fromSec, overlapping range query)
    3. 자막 텍스트 합치기
    4. LlmService 키워드추출 → WeeklyCurriculum.keywords에 포함된 키워드만 필터링 (구간 자막에서 추출한 키워드 ∩ 커리큘럼 기존 키워드)
    5. LlmService summaryItems 생성 (필터링된 키워드 기반)
    6. CustomMaterial INSERT (trigger_keywords, video_segment, title, summary_items)
    7. UserKeyword INSERT (keyword_type="weakness") — 필터링된 키워드만 대상
    8. 응답: aiTriggered=true, eventType, extractedKeywords, materialId
- [x] 패턴3/4 발동 처리:
    1. LearningEventLog INSERT
    2. 스킵/2배속 구간 자막 조회
    3. LlmService 키워드추출 → WeeklyCurriculum.keywords와 교집합 필터링
    4. LlmService 필터링된 키워드 + 자막 기반 4지선다 퀴즈 1문제 생성
    5. Quiz INSERT (quiz_type="multiple_popup", rewind_to_sec=skip_from_sec 또는 speed_start_sec) + QuizQuestion INSERT
    6. 응답: aiTriggered=true, eventType="video_skip" 또는 "video_speed_up"

### 4-4. 이벤트 API 엔드포인트
- [x] domain/eventlog/controller/EventLogController.java
- [x] POST /api/learning-rooms/{roomId}/events
- [x] EventRequestDto (event_type, curriculum_id, payload) — event_type: video_rewind, video_skip, video_speed_up
- [x] EventResponseDto (aiTriggered, eventType, extractedKeywords, materialId)

### 4-5. 돌발 퀴즈 + 정답 제출 API
- [x] GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/instant — 돌발 퀴즈 1문제 조회
- [x] InstantQuizResponseDto (questionId, quizId, questionType, question, options, timeLimitSec, relatedKeyword)
- [x] POST /api/quiz-attempts — 퀴즈 정답 제출
- [x] QuizAttemptRequestDto (questionId, quizId, selected)
- [x] 제출 로직:
    1. QuizQuestion.answer 비교 → is_correct 판별
    2. LlmService → ai_explanation 생성
    3. QuizAttempt INSERT
    4. 오답 시 UserKeyword 약점 누적 (weakness_count 증가 또는 INSERT)
    5. 오답 시 Quiz.rewind_to_sec 조회하여 응답에 포함 — 프론트는 isCorrect=false일 때 rewindToSec 지점으로 영상 되감기 + 1배속 복원
- [x] QuizAttemptResponseDto (attemptId, isCorrect, correctAnswer, aiExplanation, relatedVideoId, relatedTimestamp, rewindToSec)

### 4-6. DB 스키마 변경
- [x] QUIZZES 테이블에 rewind_to_sec 컬럼 추가 (INTEGER, nullable) — 퀴즈 생성 시 되감기 대상 초 저장
- [x] LEARNING_EVENT_LOGS 테이블에 video_id 컬럼 추가 (VARCHAR(20), NOT NULL) — 조회/필터링용
- [x] LEARNING_EVENT_LOGS event_type CHECK에 "video_speed_up"(패턴4) 추가

### 4-7. 후순위 (추후 구현)
- [ ] 패턴2.1 (일시정지) — pause_duration_sec≥180이면 즉시 발동
- [ ] 패턴2.2 (탭이탈) — INCR 후 카운트≥3이면 발동