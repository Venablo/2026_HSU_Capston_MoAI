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
## PHASE 5

### 5-1. Entity 생성
- [x] domain/flipped/entity/AiInteraction.java — db_schema 15번 참조
- [x] domain/flipped/entity/FlippedSession.java — 16번 참조. gained_keywords, weak_keywords는 JSON 타입
- [x] AiInteractionRepository — findBySessionIdOrderByCreatedAtAsc
- [x] FlippedSessionRepository — findBySessionId, findByUserIdAndRoomId

### 5-2. 거꾸로 학습 세션 시작 API
- [x] domain/flipped/controller/FlippedLearningController.java
- [x] POST /api/learning-rooms/{roomId}/flipped/start
- [x] FlippedStartRequestDto (curriculum_id)
- [x] FlippedStartResponseDto: keywords, currentKeywordIndex(0), totalKeywords 필드 추가
- [x] firstMessage는 첫 번째 키워드만 대상으로 생성 (예: "첫 번째 키워드는 '원자성'입니다. 원자성에 대해 설명해주세요!")
- [x] Redis 키 초기화: moai:flipped:{sessionId}:keywordIndex=0, exchangeCount=0 (TTL 1시간)
- [x] completionRate >= 40 검증: 영상 시청 미완료 시 FLIPPED_VIDEO_NOT_COMPLETED(400) 예외
- [x] 사용자 조회 수정: userDetails.getUsername()은 email 반환 → findById를 findByEmail로 변경 (startSession, streamChat, endSession, getResult 전체 적용)
- [x] 로직:
  1. sessionId(UUID) 발급
  2. WeeklyCurriculum.keywords 조회
  3. LlmService 호출 → 첫 번째 키워드 기반 firstMessage 생성
  4. AiInteraction INSERT (role="assistant", session_id, content=firstMessage)
  5. Redis 세션 상태 초기화
  6. 응답: sessionId, firstMessage, keywords, currentKeywordIndex, totalKeywords

### 5-3. SSE 스트리밍 API (키워드 순차 진행 방식)
- [x] SSE /api/learning-rooms/{roomId}/flipped/stream
- [x] FlippedStreamRequestDto (sessionId, message)
- [x] LlmService에 callStream() 스트리밍 메서드 추가 (Gemini streamGenerateContent)
- [x] SseEmitter 기반 구현:
  1. 사용자 메시지를 AiInteraction INSERT (role="user")
  2. Redis에서 keywordIndex, exchangeCount 조회 후 exchangeCount INCR
  3. sessionId로 전체 대화 이력 SELECT → LLM messages 배열 구성
  4. 시스템 프롬프트: 전체 키워드 목록 + 현재 키워드 인덱스 포함
  5. LLM 스트리밍 호출 → 토큰 단위로 SseEmitter.send()
  6. 태그 감지: [COUNTER_QUESTION], [NEXT_KEYWORD]
  7. 키워드 전환 규칙 (하이브리드):
     - 최소 2회 교환: 2회 미만이면 [NEXT_KEYWORD] 무시
     - 2회 이상이면 LLM 판단에 맡김 ([NEXT_KEYWORD] 태그로 전환)
     - 최대 5회 교환: 5회 도달 시 강제 키워드 전환
  8. SSE 이벤트 타입: token, counter_question, next_keyword, session_complete, done
  9. 키워드 전환 시: {type:"next_keyword", keyword:"고립성", keywordIndex:2}
  10. 마지막 키워드 완료 시: {type:"session_complete", content:"모든 키워드를 다뤘습니다!"}
  11. 완료 시 AI 전체 응답을 AiInteraction INSERT (role="assistant")
- [x] Redis 키: moai:flipped:{sessionId}:keywordIndex, moai:flipped:{sessionId}:exchangeCount
- [x] LazyInitializationException 수정: TX1에서 curriculum lazy 프록시의 keywords/topic을 즉시 로드하여 SavedContext에 전달, TX2에서는 saved.keywords()/saved.topic() 사용 — 세션 경계를 넘는 lazy 프록시 접근 제거
- [x] LLM 태그: [COUNTER_QUESTION] 역질문, [NEXT_KEYWORD] 키워드 전환
- [x] DB 커넥션 풀 점유 방지:
  - streamChat()에서 @Transactional 제거 → TransactionTemplate으로 개별 짧은 트랜잭션 분리
  - 트랜잭션 1: 사용자 메시지 저장 (즉시 커밋 — SSE 실패와 무관하게 메시지 보존)
  - 트랜잭션 2: 대화 이력 로드 + LLM 컨텍스트 구성 (읽기 전용) → 즉시 커넥션 반환
  - LLM 스트리밍 + Redis 구간: DB 커넥션 미점유
  - 트랜잭션 3: 스트리밍 완료 후 AI 응답 저장
  - HikariCP maximum-pool-size=20 (application.yaml)
- [x] 사용자 메시지 유실 방지: 메시지 저장을 별도 트랜잭션으로 분리하여 LLM 스트리밍/SSE 연결 실패 시에도 메시지 보존
- [x] Spring Security 비동기 디스패치 수정: SecurityConfig에 dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll() 추가 — SSE 완료 시 Access Denied 방지
- [x] REQUIRES_NEW 트랜잭션 전파 수정: 클래스 레벨 @Transactional(readOnly=true)가 streamChat()의 TransactionTemplate에 합류하여 쓰기가 무시되는 버그 수정. TransactionTemplate → PlatformTransactionManager 주입으로 변경, PROPAGATION_REQUIRES_NEW로 독립 트랜잭션 생성하여 사용자 메시지 저장 및 AI 응답 저장이 정상 커밋되도록 수정

### 5-4. 세션 종료 + 최종 평가 API
- [x] POST /api/learning-rooms/{roomId}/flipped/end
- [x] FlippedEndRequestDto (sessionId)
- [x] 로직:
  1. sessionId로 전체 대화 이력 조회
  2. LlmService 호출 → 최종 평가 프롬프트 (score, gainedKeywords, weakKeywords, feedback, flippedResult를 JSON으로 반환 요청)
  3. FlippedSession INSERT
  4. UserKeyword 처리 — gainedKeywords: 기존 weakness resolve + strength INSERT (미존재 시), weakKeywords: weakness count 증가 또는 INSERT
  5. 매칭 엔진 실행 (PHASE 7에서 구현할 MatchingEngineService.tryMatch() 호출) — TODO 주석
  6. WeeklyCurriculum.completionRate += 30% (거꾸로 학습 완료 반영, 100% 초과 방지)
  7. LearningRoom.completionRate 재계산 (전체 주차 평균)
  8. Redis 세션 상태 정리 (keywordIndex, exchangeCount 삭제)
- [x] FlippedEndResponseDto (sessionId, flippedResult, score, gainedKeywords, weakKeywords, feedback)

### 5-5. 평가 결과 조회 API
- [x] GET /api/learning-rooms/{roomId}/flipped/result/{sessionId}
- [x] FlippedSession + AiInteraction 조인 조회
- [x] FlippedResultResponseDto (score, gainedKeywords, weakKeywords, feedback, conversations 배열)
## PHASE6

### 6-1. 키워드 API
- [ ] domain/keyword/controller/KeywordController.java (UserKeyword 엔티티/레포지토리는 PHASE 4에서 구현 완료)
- [ ] GET /api/learning-rooms/{roomId}/keywords — 강점/약점 키워드 목록
- [ ] KeywordListResponseDto (strengths 배열, weaknesses 배열)

### 6-2. 파이널 퀴즈 조회 API
- [ ] GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final
- [ ] completionRate >= 70 검증: 거꾸로 학습 미완료 시 예외
- [ ] 로직: Quiz(quiz_type="weekly") + QuizQuestion(question_type="essay") 조회
- [ ] 퀴즈가 없으면 LlmService로 5문제 자동 생성 → Quiz + QuizQuestion INSERT 후 반환
- [ ] FinalQuizResponseDto (quizId, title, questions 배열)

### 6-3. 파이널 퀴즈 제출 API (비동기)
- [ ] POST /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final/submit
- [ ] FinalQuizSubmitRequestDto (quizId, answers 배열)
- [ ] 로직:
  1. QuizReport INSERT (status="analyzing", estimated_sec=15)
  2. 즉시 202 Accepted 응답 반환
  3. @Async 비동기 처리:
     a. 문항별 LlmService 호출 → 채점
     b. QuizAttempt INSERT (각 문항)
     c. 전체 점수 합산 → finalScore 계산
     d. radarData JSON 생성
     e. QuizReport UPDATE (status="completed")
     f. UserKeyword UPSERT — 정답: 기존 weakness resolve + strength INSERT, 오답: weakness count 증가 또는 INSERT
     g. WeeklyCurriculum.completionRate += 30% (100% 초과 방지)
     h. LearningRoom.completionRate 재계산

### 6-4. AI 분석 리포트 조회 API
- [ ] GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-report
- [ ] QuizReportResponseDto (finalScore, radarData, questions 배열)
- [ ] status="analyzing"이면 그대로 반환 (프론트가 폴링으로 재요청)