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

- [ ] GET /api/learning-rooms — 내 학습실 목록, LearningRoomListResponseDto
- [ ] GET /api/learning-rooms/{roomId}/curriculum — 전체 주차 목록, CurriculumListResponseDto
- [ ] GET /api/learning-rooms/{roomId}/curriculum/{weekId} — 주차 상세, CurriculumDetailResponseDto (mainVideoId 포함)
- [ ] PATCH /api/learning-rooms/{roomId}/curriculum/{weekId}/progress — 진척도 업데이트, 주차 평균으로 학습실 completionRate 자동 갱신
- [ ] GET /api/learning-rooms/{roomId}/curriculum/{weekId}/recommended-videos — resources에서 youtube 타입 추출
- [ ] domain/material/entity/CustomMaterial.java — db_schema 17번 참조
- [ ] CustomMaterialRepository — findByRoomIdOrderByCreatedAtDesc
- [ ] GET /api/learning-rooms/{roomId}/materials — 요약 자료 목록
- [ ] GET /api/learning-rooms/{roomId}/materials/{materialId} — 요약 자료 상세 (summaryItems 포함)
- [ ] domain/quiz/entity/Quiz.java, QuizQuestion.java, QuizAttempt.java, QuizReport.java — db_schema 07~10번
- [ ] QuizRepository, QuizQuestionRepository, QuizAttemptRepository, QuizReportRepository
- [ ] GET /api/learning-rooms/{roomId}/quiz-attempts — 퀴즈 응시 이력 목록
- [ ] GET /api/quiz-attempts/{attemptId} — 퀴즈 상세 (AI 해설 포함)