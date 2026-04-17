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
- [x] 버그 수정: [COUNTER_QUESTION]과 [NEXT_KEYWORD]가 동일 응답에서 동시 감지될 때 역질문 직후 session_complete가 전송되는 문제 — processTokenBuffer()에서 counterQuestionMode=true일 때 NEXT_KEYWORD 무시 + 시스템 프롬프트에 동시 사용 금지 규칙 추가
- [x] 시스템 프롬프트 강화: [COUNTER_QUESTION] 태그를 모든 종류의 질문(역질문, 후속 질문, 마무리 질문)에 필수 사용하도록 변경 + 마지막 키워드에서 [NEXT_KEYWORD] 사용 시 전체 키워드 요약 및 격려 마무리 메시지와 함께 출력하도록 규칙 추가 (예시 포함)
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
- [x] 버그 수정: LLM이 커리큘럼 키워드 목록 외의 키워드를 반환하는 문제 — evaluateSession() 프롬프트에 커리큘럼 키워드 목록 제약 추가 + endSession()에서 gainedKeywords/weakKeywords를 WeeklyCurriculum.keywords와 교집합 필터링 (PHASE 4 EventProcessingService와 동일 패턴)

### 5-5. 평가 결과 조회 API
- [x] GET /api/learning-rooms/{roomId}/flipped/result/{sessionId}
- [x] FlippedSession + AiInteraction 조인 조회
- [x] FlippedResultResponseDto (score, gainedKeywords, weakKeywords, feedback, conversations 배열)
## PHASE6

### 6-1. 키워드 API
- [x] domain/keyword/controller/KeywordController.java (UserKeyword 엔티티/레포지토리는 PHASE 4에서 구현 완료)
- [x] GET /api/learning-rooms/{roomId}/keywords — 강점/약점 키워드 목록
- [x] KeywordListResponseDto (strengths 배열, weaknesses 배열)

### 6-2. 파이널 퀴즈 조회 API
- [x] GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final
- [x] completionRate >= 70 검증: 거꾸로 학습 미완료 시 예외
- [x] 로직: Quiz(quiz_type="weekly") + QuizQuestion(question_type="essay") 조회
- [x] 퀴즈가 없으면 LlmService로 5문제 자동 생성 → Quiz + QuizQuestion INSERT 후 반환
- [x] FinalQuizResponseDto (quizId, title, questions 배열)

### 6-3. 파이널 퀴즈 제출 API (비동기)
- [x] POST /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final/submit
- [x] FinalQuizSubmitRequestDto (quizId, answers 배열)
- [x] QuizAttempt.selected 컬럼 VARCHAR(5) → TEXT 변경 (서술형 답변 저장용)
- [x] 로직:
  1. QuizReport INSERT (status="analyzing", estimated_sec=15)
  2. 즉시 202 Accepted 응답 반환
  3. @Async 비동기 처리:
     a. 문항별 LlmService 호출 → 채점 (weaknessKeywords 네이밍 통일, 커리큘럼 키워드 목록 제약 적용)
     b. QuizAttempt INSERT (각 문항)
     c. 전체 점수 합산 → finalScore 계산
     d. radarData JSON 생성 (LLM이 전체 채점 결과 종합하여 역량 카테고리별 점수 생성)
     e. QuizReport UPDATE (status="completed")
     f. UserKeyword UPSERT — 정답: 기존 weakness resolve + strength INSERT, 오답: weakness count 증가 또는 INSERT
     g. WeeklyCurriculum.completionRate += 30% (100% 초과 방지)
     h. LearningRoom.completionRate 재계산

### 6-4. AI 분석 리포트 조회 API
- [x] GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-report
- [x] QuizReportResponseDto (finalScore, radarData, questions 배열)
- [x] status="analyzing"이면 그대로 반환 (프론트가 폴링으로 재요청)
- [x] QuizReport questions JSON 키 missingKeywords → weakKeywords 네이밍 통일

### 6-5. 중복 제출 방어
- [x] submitFinalQuiz() — QuizReport 존재 시 FINAL_QUIZ_ALREADY_SUBMITTED(409) 예외
- [x] flipped/start — FlippedSession 존재 시 FLIPPED_SESSION_ALREADY_COMPLETED(409) 예외
- [x] FlippedSessionRepository.findByUserIdAndCurriculumId() 추가
##  PHASE 7

### 7-1. Entity 생성
- [x] domain/study/entity/StudyGroup.java — db_schema 11번, activate() 편의 메서드
- [x] domain/study/entity/StudyMember.java — 12번. UNIQUE(group_id, user_id)
- [x] domain/study/entity/StudySuggestion.java — 13번. UNIQUE(group_id, suggested_to). accept()/reject() 편의 메서드 추가
- [x] domain/notification/entity/Notification.java — db_schema 18번 (PHASE 8 선행 생성). markAsRead() 편의 메서드
- [x] StudyGroupRepository, StudyMemberRepository, StudySuggestionRepository
- [x] domain/notification/repository/NotificationRepository.java — findByUserIdAndIsReadFalseOrderByCreatedAtDesc

### 7-2. 매칭 엔진 서비스
- [x] domain/study/service/MatchingEngineService.java
- [x] UserKeywordRepository에 매칭용 메서드 추가:
  - 약점 조회: findByUserIdAndCurriculumIdAndKeywordTypeAndIsResolvedFalseAndWeaknessCountGreaterThanEqualOrderByWeaknessCountDesc (curriculumId 필터, 7일 필터 제거 — curriculumId로 이미 주차 스코프 한정)
  - 강점 보유자 조회: findByKeywordAndKeywordTypeAndUserIdNot
  - 후보자 강점 전체 조회: findByUserIdAndKeywordTypeAndCreatedAtAfter (최근 7일 강점 키워드 목록)
- [x] StudySuggestionRepository에 중복 매칭 방지 쿼리 추가:
  - existsActiveOrPendingBetween(userId, partnerId) — 두 사용자 간 active/pending_acceptance 그룹 존재 여부 확인
- [x] LlmMatchingResult DTO 생성 (selectedIndex, matchScore, matchReason)
- [x] tryMatch()를 @Async로 변경 — LLM 호출이 포함되므로 flipped/end 응답을 차단하지 않도록 비동기 실행
- [x] tryMatch(User user, LearningRoom room, WeeklyCurriculum curriculum) 메서드:
  1. UserKeyword에서 현재 사용자의 약점 키워드 조회 (curriculumId 필터, weakness_count >= 3, is_resolved=false, weakness_count DESC 정렬)
  2. 동일 키워드를 strength로 가진 다른 사용자 조회 — 후보자 최대 5명 수집 (중복 제거)
  3. 후보자별 필터: studySuggestionEnabled == true, Redis 온라인 확인, active/pending 그룹 중복 제외
  4. 후보자별 최근 7일 전체 강점 키워드 조회
  5. LLM 호출: 학생의 약점 + 후보 멘토들의 강점 목록 전달 → 최적 멘토 1명 선택, matchScore/matchReason 생성
  6. LLM 실패 시 폴백: 첫 번째 후보 선택, 수식 기반 matchScore (min(0.6 + weaknessCount * 0.05, 0.990)), 하드코딩 matchReason
  7. 선택된 멘토로 매칭 결과 생성:
     a. StudyGroup INSERT (type="mentor_mentee", status="pending_acceptance", match_keyword, match_reason, match_score)
     b. StudySuggestion INSERT x 2 (양측에 각각, suggested_role="mentee"/"mentor")
     c. Notification INSERT x 2 (type="study_match", reference_id=suggestion.id)
     d. SSE 알림 푸시는 TODO 주석 처리 — PHASE 8에서 NotificationService.pushSse() 연동
  8. 매칭 대상 미발견 시 아무 동작 없음
- [x] 전체 tryMatch()를 try-catch로 감싸 매칭 실패가 호출자를 방해하지 않도록 처리. 실패 시 log.error

### 7-3. 기존 FlippedLearningService 매칭 엔진 연동
- [x] PHASE 5의 POST /flipped/end에서 TODO로 남겨둔 매칭 엔진 호출 활성화
- [x] MatchingEngineService 주입 → endSession()에서 user.studySuggestionEnabled 확인 후 matchingEngineService.tryMatch(user, room, curriculum) 호출

### 7-4. 매칭 제안 API
- [x] domain/study/service/StudyGroupService.java
- [x] domain/study/controller/StudyGroupController.java
- [x] GET /api/study-groups/suggestions — pending 상태 제안 목록. SuggestionListResponseDto
  - 같은 그룹의 상대방 suggestion 조회 → 상대방 User 정보 (nickname, profileImageUrl)
  - strengthKeyword 필드 제거 — top-level matchKeyword와 중복이며 멘토 시점에서 부정확. 프론트는 matchKeyword 사용
- [x] POST /api/study-groups/suggestions/{id}/accept — 수락 로직:
  1. 본인 제안인지 검증 (suggested_to == userId), pending 상태 검증
  2. suggestion.accept() — status="accepted", responded_at 갱신
  3. 양측 모두 accepted인지 확인
  4. 양측 수락 시: StudyGroup.status = "active" UPDATE + StudyMember INSERT x 2
  5. Notification INSERT — 상대방에게 type="study_accepted", reference_id=group.id
  6. SSE 알림은 TODO 주석 처리
  7. 버그 수정: SuggestionAcceptResponseDto에 groupId 필드 추가 — active 전환 시 클라이언트가 스터디 그룹 상세/채팅방으로 이동할 수 있도록
- [x] POST /api/study-groups/suggestions/{id}/reject — 거절 로직:
  1. 본인 제안인지 검증, pending 상태 검증
  2. suggestion.reject() — status="rejected", responded_at 갱신
  3. StudyGroup.disband() — status="disbanded"로 변경하여 그룹이 pending_acceptance 상태로 남지 않도록 처리
  4. Notification INSERT — 상대방에게 type="study_rejected", reference_id=suggestion.id
  5. SSE 알림은 TODO 주석 처리

### 7-5. 스터디 그룹 상세 API
- [x] GET /api/study-groups/{groupId} — 그룹 상세 + 파트너 정보
- [x] StudyGroupDetailResponseDto (groupId, matchKeyword, status, partner 객체 [userId, nickname, profileImageUrl, role, isOnline]) — matchKeyword는 top-level에만 배치, PartnerDetail에서 제거
- [x] 현재 사용자가 멤버인지 검증 (active→StudyMember, pending→StudySuggestion, 둘 다 아니면 403)
- [x] isOnline은 Redis에서 RT:{email} 토큰 존재 여부로 판단

### 7-6. ErrorCode 추가
- [x] SUGGESTION_NOT_FOUND(404, "스터디 제안을 찾을 수 없습니다.")
- [x] SUGGESTION_ALREADY_RESPONDED(409, "이미 응답한 스터디 제안입니다.")
- [x] STUDY_GROUP_NOT_FOUND(404, "스터디 그룹을 찾을 수 없습니다.")
- [x] STUDY_GROUP_ACCESS_DENIED(403, "스터디 그룹에 접근 권한이 없습니다.")
## PHASE 8

### 8-1. Entity / Repository
- [x] Notification 엔티티, NotificationRepository는 PHASE 7에서 선행 생성 완료 — 이미 존재하면 스킵
- [x]  Notification 엔티티에 message (TEXT) 컬럼 없으면 추가 — SSE 이벤트와 알림 목록에서 표시할 메시지 필요. api_spec 참조: "완벽한 상호 보완 파트너를 찾았습니다!" 등

### 8-2. SSE 알림 인프라
- [x] domain/notification/service/NotificationService.java
- [x] SseEmitter 관리: ConcurrentHashMap<String, SseEmitter> — userId별 emitter 저장
- [x] subscribe(String userId) — SseEmitter 생성 (타임아웃 30분), Map에 저장, 완료/타임아웃/에러 시 Map에서 제거하는 콜백 등록
- [x] pushSse(String userId, Object event) — 해당 userId의 emitter로 이벤트 전송. emitter 없으면(오프라인) 무시 (Notification은 호출자가 이미 DB에 저장)
- [x] SecurityConfig에 /api/notifications/stream SSE 경로 비동기 디스패치 허용 — PHASE 5에서 추가한 dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll() 존재 확인

### 8-3. SSE 알림 스트림 API
- [x] domain/notification/controller/NotificationController.java
- [x] GET /api/notifications/stream — SseEmitter 반환 (Content-Type: text/event-stream, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
- [x] JWT에서 userId 추출 → NotificationService.subscribe(userId) 호출

### 8-4. 알림 목록/읽음 API
- [x] GET /api/notifications — 읽지 않은 알림 목록
- [x] NotificationListResponseDto (notificationId, type, message, referenceId, isRead, createdAt)
- [x] PATCH /api/notifications/{id}/read — notification.markAsRead()
- [x] NotificationReadResponseDto (notificationId, isRead)
- [x] 본인 알림인지 검증 (notification.user.id == userId, 아니면 403)

### 8-5. PHASE 7 TODO 주석 활성화
- [x] MatchingEngineService.createMatchGroup()에서 TODO 주석으로 남겨둔 pushSse() 호출 활성화
  - NotificationService 주입
  - 매칭 생성 후: notificationService.pushSse(mentee.getId(), matchEvent), notificationService.pushSse(mentor.getId(), matchEvent)
- [x] StudyGroupService.acceptSuggestion()에서 TODO 주석 활성화
  - 수락 시: 상대방에게 pushSse(partnerId, acceptedEvent)
- [x] StudyGroupService.rejectSuggestion()에서 TODO 주석 활성화
  - 거절 시: 상대방에게 pushSse(partnerId, rejectedEvent)

### 8-6. SSE 이벤트 데이터 형식
- [x] study_match: {type, message, suggestionId, partner: {nickname, role}, matchScore, matchKeyword}
- [x] study_accepted: {type, message, groupId}
- [x] study_rejected: {type, message, suggestionId}
- [x] chat_message: {type, message, groupId, sender: {nickname, profileImageUrl}, preview} — PHASE 9에서 구현

### 8-7. ErrorCode 추가
- [x] NOTIFICATION_NOT_FOUND(404, "알림을 찾을 수 없습니다.")
- [x] NOTIFICATION_ACCESS_DENIED(403, "알림에 접근 권한이 없습니다.")
## PHASE 9

### 9-1. Entity 생성
- [x] domain/chat/entity/StudyMessage.java — db_schema 14번 참조
  - id (CHAR(36) PK), group_id (FK→study_groups.id), sender_id (FK→users.id, nullable — AI 발언 시 NULL), sender_type (VARCHAR(5) CHECK "user"|"ai"), content (TEXT), is_ai_correction (TINYINT(1) 기본값 FALSE), sent_at (DATETIME)
- [x] domain/chat/repository/StudyMessageRepository.java — findByGroupIdOrderBySentAtDesc (페이지네이션: Pageable 파라미터)

### 9-2. WebSocket/STOMP 설정
- [x] global/config/WebSocketConfig.java — WebSocketMessageBrokerConfigurer 구현
  - registerStompEndpoints: /ws/study-groups (SockJS fallback 허용)
  - configureMessageBroker: application prefix /pub, broker prefix /sub
- [x] global/config/StompChannelInterceptor.java — ChannelInterceptor 구현
  - CONNECT 프레임에서 Authorization 헤더 추출 → JWT 토큰 검증
  - 검증 실패 시 연결 거부
  - 검증 성공 시 Principal에 email 설정 (StompHeaderAccessor.setUser)
  - 버그 수정: getUserId()는 Access Token 전용 "userId" 클레임을 읽어 Refresh Token에서 null 반환 → getEmail()로 변경 (subject는 모든 토큰에 존재)

### 9-3. 채팅 이력 조회 API (REST)
- [x] domain/chat/service/ChatService.java
- [x] domain/chat/controller/ChatController.java
- [x]  GET /api/study-groups/{groupId}/messages?page=0&size=50 — 이전 채팅 이력
- [x] 현재 사용자가 해당 그룹의 StudyMember인지 검증 (아니면 403)
- [x] ChatMessageResponseDto (messageId, senderType, senderId, senderNickname, content, isAiCorrection, sentAt)

### 9-4. WebSocket 메시지 핸들러
- [x] domain/chat/controller/ChatWebSocketController.java
- [x] @MessageMapping("/chat/{groupId}") — 메시지 수신 핸들러
- [x] ChatSendRequestDto (content)
- [x] 처리 로직:
  1. Principal에서 email 추출 (Principal.getName()은 JWT 기반으로 email 반환)
  2. 해당 그룹의 StudyMember인지 검증
  3. StudyMessage INSERT (sender_type="user", sender_id=userId, content, is_ai_correction=false)
  4. ChatMessageResponseDto 구성 (senderNickname은 User에서 조회)
  5. /sub/chat/{groupId}로 메시지 브로드캐스트 (SimpMessagingTemplate.convertAndSend)
  6. 상대방이 WebSocket 미연결 상태면: Notification INSERT (type="chat_message", reference_id=group.id) + NotificationService.pushSse() 호출
- [x] 버그 수정: ChatService.sendMessage()에서 Principal.getName()이 email을 반환하는데 findById()로 조회하던 버그 → findByEmail()로 변경, validateMembership()에 sender.getId() 전달

### 9-5. WebSocket 연결 상태 추적 (상대방 오프라인 판단용)
- [x] WebSocket 세션 관리: ConcurrentHashMap<String, Set<String>> — groupId별 연결된 userId 추적
- [x] STOMP SUBSCRIBE 시 Map에 추가, DISCONNECT 시 제거
- [x] 메시지 전송 시 상대방이 Map에 없으면 → 오프라인으로 판단 → 알림 발송

### 9-6. SecurityConfig 경로 추가
- [x] /ws/study-groups/** 경로 permitAll (WebSocket 핸드셰이크 허용, 실제 인증은 StompChannelInterceptor에서 처리)

### 9-7. ErrorCode 추가
- [x] CHAT_GROUP_ACCESS_DENIED(403, "채팅방에 접근 권한이 없습니다.")
## PHASE 10
  
### 10-1. 프로필 전체 조회 API
- [] GET /api/users/me — 기존 홈 화면용 API가 이미 구현되어 있으면 스킵. 없으면 추가
- [] UserProfileResponseDto (userId, loginId, name, nickname, email, birthDate, profileImageUrl, interestKeywords, studySuggestionEnabled, themePreference, status)

### 10-2. 프로필 수정 API
- [] domain/users/controller/UserController.java (기존 컨트롤러에 추가)
- [] domain/users/service/UserService.java (기존 서비스에 추가)
- [] PATCH /api/users/me — 닉네임, 프로필 사진, 테마 수정
- [] UserUpdateRequestDto (nickname, profileImageUrl, themePreference) — 모든 필드 nullable, 전달된 필드만 업데이트 (부분 수정)
- [] UserUpdateResponseDto (nickname, profileImageUrl, themePreference)
- [] User 엔티티에 updateProfile() 편의 메서드 추가 (null이 아닌 필드만 업데이트)

### 10-3. 관심 키워드 수정 API
- [] PATCH /api/users/me/keywords — 관심 키워드 수정
- [] KeywordUpdateRequestDto (interestKeywords 배열)
- [] KeywordUpdateResponseDto (interestKeywords 배열)
- [] User.interestKeywords UPDATE (JSON)

### 10-4. 회원 탈퇴 API
- [] DELETE /api/users/me — 비밀번호 확인 후 탈퇴
- [] UserDeleteRequestDto (password)
- [] 로직:
  1. BCrypt 비밀번호 검증 — 불일치 시 400 에러
  2. User.status = "inactive" UPDATE (논리 삭제)
  3. Redis에서 RefreshToken 삭제 (RT:{email} 키)
  4. 응답: "회원 탈퇴가 완료되었습니다."

### 10-5. 학습실 이력 API
- [] GET /api/users/me/learning-history — 학습실 이력 목록
- [] LearningRoomRepository에 findByUserIdOrderByCreatedAtDesc 메서드 추가 (없으면)
- [] LearningHistoryResponseDto (roomId, subject, level, completionRate, status, createdAt)

### 10-6. ErrorCode 추가
- [] PASSWORD_MISMATCH(400, "비밀번호가 일치하지 않습니다.")
- [] USER_ALREADY_INACTIVE(409, "이미 탈퇴한 사용자입니다.")

## PHASE 11
- [ ] JWT 토큰 타입 구분 — 현재 Access/Refresh Token 구분 없이 모든 JWT가 인증 통과됨. 토큰 생성 시 type 클레임 추가 ("access"/"refresh") + validateToken()에서 type 검증 추가 필요