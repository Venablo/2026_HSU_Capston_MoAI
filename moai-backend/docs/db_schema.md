# MoAI Platform — DB Schema Document v7.0

- **총 테이블 수**: 18개
- **DB 엔진**: MySQL 8.0 (AWS RDS)
- **백엔드 스택**: Spring Boot + JPA + JWT + Spring Security + Redis + RDS + S3 + EC2

## 도메인 구성

| 도메인 | 테이블 |
|--------|--------|
| 사용자 | USERS |
| 학습실 | LEARNING_ROOMS, WEEKLY_CURRICULUMS |
| AI 기능 | USER_KEYWORDS, VIDEO_TRANSCRIPTS, LEARNING_EVENT_LOGS, AI_INTERACTIONS, FLIPPED_SESSIONS, CUSTOM_MATERIALS |
| 퀴즈 | QUIZZES, QUIZ_QUESTIONS, QUIZ_ATTEMPTS, QUIZ_REPORTS |
| 스터디 | STUDY_GROUPS, STUDY_MEMBERS, STUDY_SUGGESTIONS, STUDY_MESSAGES |
| 알림 | NOTIFICATIONS |

---

## 01. USERS (사용자 도메인)

플랫폼을 사용하는 모든 학생 정보를 저장하는 최상위 테이블.

용도: 인증, 개인화 추천, 스터디 매칭 기준 테이블

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 사용자 고유 식별자 (UUID) |
| login_id | VARCHAR(50) | UNIQUE | 사용자가 직접 입력하는 로그인 아이디 |
| password_hash | TEXT | | bcrypt 등으로 해싱된 비밀번호 |
| name | VARCHAR(50) | | 사용자 실명 |
| nickname | VARCHAR(20) | | 화면에 표시되는 닉네임. 최대 20자 |
| email | VARCHAR(255) | UNIQUE | 이메일 주소 |
| birth_date | DATE | | 생년월일 |
| status | VARCHAR(10) | | CHECK: "active" \| "inactive". 온라인 상태는 Redis로 관리 |
| profile_image_url | TEXT | | S3 Public URL. NULL 허용 |
| interest_keywords | JSON | | 관심 키워드 배열. 예: ["정보처리기사", "웹개발"] |
| study_suggestion_enabled | TINYINT(1) | | FALSE이면 AI 매칭 엔진 후보 제외. 기본값 TRUE |
| theme_preference | VARCHAR(10) | | CHECK: "light" \| "dark". 기본값 "light" |
| created_at | DATETIME | | 가입 일시 |
| updated_at | DATETIME | | 최근 수정 일시. 트리거 자동 갱신 |

---

## 02. LEARNING_ROOMS (학습실 도메인)

사용자가 개설한 학습실. 생성 시 AI가 WEEKLY_CURRICULUMS를 주차 수만큼 자동 INSERT.

용도: 모든 학습 활동의 루트 컨텍스트

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 학습실 고유 ID (UUID) |
| user_id | CHAR(36) | FK | users.id 참조. ON DELETE CASCADE |
| subject | VARCHAR(100) | | 학습 주제. 예: "정보처리기사" |
| level | VARCHAR(20) | | CHECK: "beginner" \| "intermediate" \| "advanced" |
| duration_weeks | SMALLINT | | 총 학습 기간(주). AI가 커리큘럼 행 수 결정 기준 |
| hours_per_day | DECIMAL(4,1) | | 하루 학습 시간. 범위: 0.5~12 |
| current_week | SMALLINT | | 현재 진행 주차. 기본값 1 |
| completion_rate | DECIMAL(5,2) | | 전체 학습실 달성률 0.00~100.00. 기본값 0 |
| status | VARCHAR(20) | | CHECK: "active" \| "completed" \| "paused" |
| created_at | DATETIME | | 개설 일시 |
| updated_at | DATETIME | | 수정 일시. 트리거 자동 갱신 |

---

## 03. WEEKLY_CURRICULUMS (학습실 도메인)

학습실 생성 시 LLM이 자동으로 채워주는 주차별 학습 계획. duration_weeks 수만큼 행이 생성. 자막 스크래핑 후 LLM이 핵심 키워드를 추출하여 keywords에 저장.

용도: 커리큘럼 로드맵 표시, 하위 테이블 연결 허브, 거꾸로 학습 키워드 제공

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 주차 커리큘럼 고유 ID (UUID). 하위 테이블 대부분이 참조 |
| room_id | CHAR(36) | FK | learning_rooms.id 참조. ON DELETE CASCADE |
| week_number | SMALLINT | | 주차 번호. UNIQUE(room_id, week_number) |
| topic | VARCHAR(200) | | 주차 학습 주제. 예: "데이터베이스 아키텍처 및 트랜잭션 이해" |
| description | TEXT | | 주차 학습 내용 요약. 예: "이번 주차에는 DB의 기본 구조와 ACID 원리를 마스터합니다." |
| keywords | JSON | | 자막 기반 LLM 추출 핵심 키워드. 예: ["원자성","일관성","고립성","지속성"] |
| resources | JSON | | AI 추천 학습 자료 목록. 예: [{"type":"youtube","video_id":"abc","title":"DML 강의"}] |
| completion_rate | DECIMAL(5,2) | | 0.00~100.00. 주차별 진척도. 기본값 0 |
| created_at | DATETIME | | 생성 일시 |

---

## 04. USER_KEYWORDS (AI 기능 도메인)

사용자별 강점·약점 키워드를 관리. 패턴 감지, 거꾸로 학습, 퀴즈 결과를 종합해 누적.

용도: AI 멘토-멘티 매칭 엔진 핵심 입력 — 강점 1개↑ vs 약점 2개↑ + created_at 7일 이내 + Redis 토큰 있는 사용자

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 키워드 레코드 고유 ID (UUID) |
| user_id | CHAR(36) | FK | users.id 참조 |
| room_id | CHAR(36) | FK | learning_rooms.id 참조. 발생 학습실 추적용 |
| curriculum_id | CHAR(36) | FK | weekly_curriculums.id 참조. 몇 주차에서 발생했는지 추적 |
| keyword | VARCHAR(100) | | 키워드명. 예: "DML", "WHERE 절" |
| keyword_type | VARCHAR(10) | | CHECK: "strength" \| "weakness" |
| weakness_count | SMALLINT | | 약점 누적 횟수. 매칭 조건: 2 이상. 기본값 1 |
| is_resolved | TINYINT(1) | | 마무리 퀴즈 정답으로 약점 해소 시 TRUE. 기본값 FALSE |
| resolved_at | DATETIME | | 약점 해소 일시 |
| created_at | DATETIME | | 생성 일시. 매칭 조건: 7일 이내 여부 판별 기준 |
| updated_at | DATETIME | | 수정 일시. 트리거 자동 갱신 |

---

## 05. VIDEO_TRANSCRIPTS (AI 기능 도메인)

YouTube 자막을 시간 청크(start_sec~end_sec) 단위로 사전 저장. 커리큘럼 영상 등록 시 백엔드가 미리 스크래핑해 INSERT.

용도: 패턴1 감지 시 해당 구간 자막 빠르게 쿼리 → LLM에 전달해 키워드 추출

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 자막 청크 고유 ID (UUID) |
| curriculum_id | CHAR(36) | FK | weekly_curriculums.id 참조 |
| video_id | VARCHAR(20) | | YouTube video_id. 예: "dQw4w9WgXcQ" |
| start_sec | DECIMAL(10,3) | | 청크 시작 시간(초) |
| end_sec | DECIMAL(10,3) | | 청크 종료 시간(초). CONSTRAINT: end_sec > start_sec |
| text_content | TEXT | | 자막 텍스트 원문 |
| chunk_index | INT | | 스크래핑 당시 원본 순서. 안정적인 정렬 보장 |
| created_at | DATETIME | | 생성 일시 |

---

## 06. LEARNING_EVENT_LOGS (AI 기능 도메인)

YouTube IFrame API에서 감지한 행동 이벤트를 패턴 발동 시에만 저장.

용도: 패턴1·2·3 발동 이력 저장 — AI 맞춤 자료 생성 트리거 기록

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 이벤트 고유 ID (UUID) |
| user_id | CHAR(36) | FK | users.id 참조 |
| curriculum_id | CHAR(36) | FK | weekly_curriculums.id 참조. 학습 컨텍스트 파악 |
| event_type | VARCHAR(20) | | CHECK: "video_rewind"(패턴1) \| "video_skip"(패턴3) \| "video_pause"(패턴2.1) \| "tab_departure"(패턴2.2) |
| payload | JSON | | 이벤트별 부가 정보 (아래 예시 참조) |
| ai_triggered | TINYINT(1) | | AI 호출 완료 시 TRUE. 기본값 TRUE (발동 시에만 저장) |
| logged_at | DATETIME | | 이벤트 발생 일시 |

**payload 예시:**
- 패턴1: `{"video_id":"dml_week1","rewind_target_sec":268}`
- 패턴2.1: `{"video_id":"dml_week1","pause_start_sec":275,"pause_duration_sec":185,"trigger_type":"long_pause"}`
- 패턴2.2: `{"video_id":"dml_week1","departure_sec":300,"return_sec":480,"departure_count":3,"current_keyword":"ACID"}`
- 패턴3: `{"video_id":"dml_week1","skip_from_sec":120,"skip_to_sec":300}`

---

## 07. QUIZZES (퀴즈 도메인)

세 가지 종류의 퀴즈 세트를 관리. 각 세트는 QUIZ_QUESTIONS에 세부 문항을 가짐.

용도: 주차 마무리 / 패턴3 돌발 팝업 퀴즈 세트 관리

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 퀴즈 세트 고유 ID (UUID) |
| curriculum_id | CHAR(36) | FK | weekly_curriculums.id 참조 |
| quiz_type | VARCHAR(15) | | CHECK: "weekly"(주차 마무리) \| "ox_popup"(패턴3 O/X) \| "multiple_popup"(패턴3 객관식) |
| title | VARCHAR(200) | | 퀴즈 세트 제목. 예: "Week 1 파이널 퀴즈" |
| created_at | DATETIME | | 생성 일시 |

---

## 08. QUIZ_QUESTIONS (퀴즈 도메인)

퀴즈 세트(QUIZZES)에 속한 세부 문항을 저장.

용도: 퀴즈 문항 단위 저장, 정오 판별, 키워드 연계

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 문항 고유 ID (UUID) |
| quiz_id | CHAR(36) | FK | quizzes.id 참조. ON DELETE CASCADE |
| question_type | VARCHAR(10) | | CHECK: "multiple"(객관식) \| "essay"(서술형) \| "ox"(O/X) |
| question | TEXT | | 문제 본문 |
| options | JSON | | 선택지 배열. essay 타입은 NULL. 예: [{"label":"A","text":"..."}] |
| answer | VARCHAR(5) | | 정답 라벨. essay 타입은 NULL (AI가 채점). 예: "A", "O" |
| question_order | SMALLINT | | 세트 내 문항 순서 |
| related_keyword | VARCHAR(100) | | 이 문항의 키워드. 강점/약점 추출 기준 |
| time_limit_sec | SMALLINT | | 문항별 제한 시간(초). NULL이면 제한 없음. 예: 60 |
| max_length | SMALLINT | | 서술형 답변 최대 글자 수. essay 타입에만 사용. 예: 500 |
| tip | TEXT | | 서술형 문항 힌트. essay 타입에만 사용 |
| created_at | DATETIME | | 생성 일시 |

---

## 09. QUIZ_ATTEMPTS (퀴즈 도메인)

학생이 퀴즈 문항을 제출할 때마다 한 행이 쌓임. 제출 시 LLM이 ai_explanation을 생성하여 함께 저장.

용도: 취약점 분석, USER_KEYWORDS 갱신 트리거, AI 해설 재열람

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 응시 고유 ID (UUID) |
| question_id | CHAR(36) | FK | quiz_questions.id 참조 |
| quiz_id | CHAR(36) | FK | quizzes.id 참조. 세트 단위 집계용 중복 저장 |
| user_id | CHAR(36) | FK | users.id 참조 |
| selected | VARCHAR(5) | | 학생이 선택한 라벨. 예: "B", "X" |
| is_correct | TINYINT(1) | | 정오 여부. 백엔드에서 answer와 비교 후 저장 |
| ai_explanation | TEXT | | 퀴즈 제출 시 LLM이 생성한 해설. 퀴즈 내역 상세에서 재사용 |
| attempted_at | DATETIME | | 응시 일시 |

---

## 10. QUIZ_REPORTS (퀴즈 도메인)

파이널 퀴즈 제출 후 AI가 비동기로 생성한 종합 분석 리포트를 저장.

용도: AI 종합 분석 리포트 저장 — 점수, 레이더차트, 문항별 해설

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 리포트 고유 ID (UUID) |
| quiz_id | CHAR(36) | FK | quizzes.id 참조 |
| user_id | CHAR(36) | FK | users.id 참조 |
| curriculum_id | CHAR(36) | FK | weekly_curriculums.id 참조 |
| final_score | DECIMAL(5,2) | | 최종 점수 0.00~100.00 |
| radar_data | JSON | | 레이더차트 데이터. 예: {"개념이해도":90,"응용력":85,"논리력":95,"키워드적중률":88} |
| questions | JSON | | 문항별 AI 해설 배열. 예: [{"order":1,"score":20,"isCorrect":true,"gainedKeywords":[...],"aiComment":"..."}] |
| status | VARCHAR(15) | | CHECK: "analyzing" \| "completed" |
| estimated_sec | SMALLINT | | 분석 예상 소요 시간(초). 예: 15 |
| created_at | DATETIME | | 리포트 생성 요청 일시 |
| completed_at | DATETIME | | AI 분석 완료 일시 |

---

## 11. STUDY_GROUPS (스터디 도메인)

AI 매칭 엔진이 생성하는 멘토-멘티 스터디 그룹. 양측 모두 수락해야 active로 전환.

용도: 멘토-멘티 그룹 메타 정보 및 AI 매칭 근거 보존

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 그룹 고유 ID (UUID) |
| type | VARCHAR(20) | | CHECK: "mentor_mentee" |
| subject | VARCHAR(100) | | 스터디 주제. 예: "정보처리기사 DML" |
| match_keyword | VARCHAR(100) | | 매칭 근거 키워드. 예: "DML" |
| match_reason | TEXT | | AI 매칭 이유 요약 |
| match_score | DECIMAL(4,3) | | AI 산출 매칭 점수. 0.000~1.000 |
| status | VARCHAR(20) | | CHECK: "pending_acceptance" \| "active" \| "completed" \| "disbanded" |
| expires_at | DATETIME | | 스터디 만료 일시. active 전환 시 현재 시각 + 7일로 설정. NULL 허용 |
| created_at | DATETIME | | 생성 일시 |

---

## 12. STUDY_MEMBERS (스터디 도메인)

스터디 그룹에 수락한 멤버 목록. 양측 모두 accepted가 되면 행이 추가.

용도: 그룹 채팅 접근 권한 확인, 멘토/멘티 역할 구분

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 멤버십 고유 ID (UUID) |
| group_id | CHAR(36) | FK | study_groups.id 참조 |
| user_id | CHAR(36) | FK | users.id 참조. UNIQUE(group_id, user_id) |
| role | VARCHAR(10) | | CHECK: "mentor" \| "mentee" |
| joined_at | DATETIME | | 입장 일시 |

---

## 13. STUDY_SUGGESTIONS (스터디 도메인)

수락·거절 전 단계의 스터디 제안. 멘토·멘티 양측 모두에게 발송.

용도: "양측 수락 후 그룹 활성화" 플로우 관리

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 제안 고유 ID (UUID) |
| group_id | CHAR(36) | FK | study_groups.id 참조 |
| suggested_to | CHAR(36) | FK | 제안받는 사용자 ID. UNIQUE(group_id, suggested_to) |
| room_id | CHAR(36) | FK, NOT NULL | 수신자 본인 컨텍스트 학습실. 멘티는 매칭 트리거 학습실, 멘토는 매칭에 사용된 strength 키워드가 발생한 학습실 |
| curriculum_id | CHAR(36) | FK, NOT NULL | 수신자 본인 컨텍스트 주차. room_id와 동일 출처 |
| suggested_role | VARCHAR(10) | | CHECK: "mentor" \| "mentee" |
| status | VARCHAR(10) | | CHECK: "pending" \| "accepted" \| "rejected" |
| responded_at | DATETIME | | 응답 일시. NULL이면 미응답 |
| created_at | DATETIME | | 발송 일시 |

---

## 14. STUDY_MESSAGES (스터디 도메인)

스터디 그룹 채팅 메시지. WebSocket으로 실시간 전송, REST로 이전 이력 조회.

용도: 그룹 채팅 이력 저장, AI 중재 발언 구분

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 메시지 고유 ID (UUID) |
| group_id | CHAR(36) | FK | study_groups.id 참조 |
| sender_id | CHAR(36) | FK | users.id. AI 발언 시 NULL |
| sender_type | VARCHAR(5) | | CHECK: "user" \| "ai" |
| content | TEXT | | 메시지 본문 |
| is_ai_correction | TINYINT(1) | | AI 오류 정정 발언 여부. 기본값 FALSE |
| sent_at | DATETIME | | 전송 일시 |

---

## 15. AI_INTERACTIONS (AI 기능 도메인)

거꾸로 학습 세션의 대화 이력. SSE 스트리밍 시 전체 이력을 LLM messages 배열에 주입.

용도: 거꾸로 학습 SSE 컨텍스트 관리

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 대화 메시지 고유 ID (UUID) |
| session_id | CHAR(36) | | 거꾸로 학습 세션 구분자. 세션 시작 시 UUID 발급 |
| user_id | CHAR(36) | FK | users.id 참조 |
| room_id | CHAR(36) | FK | learning_rooms.id 참조 |
| curriculum_id | CHAR(36) | FK | weekly_curriculums.id. 주차 추적 |
| role | VARCHAR(10) | | CHECK: "user" \| "assistant" |
| content | TEXT | | 메시지 본문 |
| is_counter_question | TINYINT(1) | | AI 역질문 여부. 기본값 FALSE |
| created_at | DATETIME | | 생성 일시 |

---

## 16. FLIPPED_SESSIONS (AI 기능 도메인)

거꾸로 학습 세션의 최종 평가 결과를 저장. AI_INTERACTIONS에서 결과 컬럼을 분리하여 세션 단위 관리.

용도: 거꾸로 학습 평가 결과 저장 — 강점/약점 키워드 추출 트리거, 매칭 엔진 입력

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 세션 결과 고유 ID (UUID) |
| session_id | CHAR(36) | UNIQUE | AI_INTERACTIONS.session_id와 연결 |
| user_id | CHAR(36) | FK | users.id 참조 |
| room_id | CHAR(36) | FK | learning_rooms.id 참조 |
| curriculum_id | CHAR(36) | FK | weekly_curriculums.id 참조 |
| flipped_result | VARCHAR(10) | | CHECK: "pass" \| "fail". pass→강점 / fail→약점 키워드 추출 |
| score | DECIMAL(5,2) | | 이해도 점수 0.00~100.00 |
| feedback | TEXT | | AI 최종 피드백 문구 |
| gained_keywords | JSON | | 거꾸로 학습에서 획득한 강점 키워드. 예: ["원자성","비유적_설명","TCL_기초"] |
| weak_keywords | JSON | | 거꾸로 학습에서 발견된 약점 키워드. 예: ["고립성(Isolation)_개념"] |
| created_at | DATETIME | | 세션 평가 완료 일시 |

---

## 17. CUSTOM_MATERIALS (AI 기능 도메인)

패턴1 감지 시 LLM이 자동 생성하는 맞춤형 학습 자료.

용도: AI 핵심 요약 탭 및 마이페이지 취약점 히스토리에서 재열람

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 맞춤 자료 고유 ID (UUID) |
| user_id | CHAR(36) | FK | users.id 참조 |
| room_id | CHAR(36) | FK | learning_rooms.id 참조 |
| curriculum_id | CHAR(36) | FK | weekly_curriculums.id 참조 |
| trigger_keywords | JSON | | 생성 원인 키워드 목록. 예: ["ACID", "트랜잭션"] |
| video_segment | VARCHAR(50) | | 자료 생성 원인 영상 구간. 예: "영상 구간 12:45" |
| title | VARCHAR(200) | | AI 생성 자료 제목 |
| summary_items | JSON | | AI 생성 요약 항목 배열. 예: [{"label":"A","title":"Atomicity (원자성)","desc":"..."}] |
| created_at | DATETIME | | 생성 일시 |

---

## 18. NOTIFICATIONS (알림 도메인)

매칭 알림, 스터디 수락/거절 알림, 채팅 알림 등을 저장.

용도: 실시간 알림 이력 저장 — 읽지 않은 알림 조회, 알림 읽음 처리

| 컬럼명 | 타입 | 키 | 설명 |
|--------|------|-----|------|
| id | CHAR(36) | PK | 알림 고유 ID (UUID) |
| user_id | CHAR(36) | FK | users.id 참조. 알림 수신 대상 |
| type | VARCHAR(20) | | CHECK: "study_match" \| "study_accepted" \| "study_rejected" \| "chat_message" |
| reference_id | CHAR(36) | | 알림 관련 리소스 ID. type에 따라 group_id 또는 suggestion_id |
| is_read | TINYINT(1) | | 읽음 여부. 기본값 FALSE |
| created_at | DATETIME | | 알림 생성 일시 |

---

## 인덱스 요약

| 테이블 | 인덱스 컬럼 | 용도 |
|--------|-------------|------|
| video_transcripts | (curriculum_id, start_sec, end_sec) | 패턴1 구간 자막 빠른 조회 |
| learning_event_logs | (user_id, event_type, ai_triggered) | 미처리 이벤트 필터링 |
| user_keywords | (keyword, keyword_type, weakness_count) WHERE is_resolved=FALSE | AI 매칭 엔진 후보 탐색 |
| user_keywords | (user_id, room_id) | 사용자별 키워드 조회 |
| ai_interactions | (session_id) | 거꾸로 학습 세션별 대화 이력 조회 |
| ai_interactions | (room_id, created_at) | SSE 대화 이력 시간순 조회 |
| flipped_sessions | (user_id, room_id) | 학습실별 거꾸로 학습 결과 조회 |
| quiz_attempts | (user_id) | 내 오답 이력 조회 |
| quiz_reports | (user_id, curriculum_id) | 파이널 퀴즈 리포트 조회 |
| study_messages | (group_id, sent_at) | 채팅 이력 시간순 조회 |
| notifications | (user_id, is_read, created_at) | 읽지 않은 알림 조회 |
