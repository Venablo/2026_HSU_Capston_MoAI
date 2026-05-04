# MoAI Platform — API Specification

- **총 엔드포인트**: 41개 (REST 38 + SSE 2 + WebSocket 1)
- **인증 방식**: JWT Bearer Token — `Authorization: Bearer {accessToken}`
- **Base URL (REST)**: `https://api.moai.app/v1`
- **Base URL (WS)**: `wss://api.moai.app`
- **Content-Type**: `application/json` (SSE·WebSocket 제외)
- **백엔드 스택**: Spring Boot + JPA + JWT + Spring Security + Redis + RDS + S3 + EC2

## 공통 응답 형식

```json
{
  "success": true,
  "data": { ... },
  "message": "OK",
  "timestamp": "2025-03-01T09:00:00Z"
}
```

## 공통 에러 코드

| HTTP | 에러 코드 | 설명 |
|------|----------|------|
| 400 | BAD_REQUEST | 요청 파라미터가 유효하지 않습니다 |
| 401 | UNAUTHORIZED | 인증 토큰이 없거나 만료되었습니다 |
| 403 | FORBIDDEN | 해당 리소스에 대한 권한이 없습니다 |
| 404 | NOT_FOUND | 요청한 리소스를 찾을 수 없습니다 |
| 409 | CONFLICT | 이미 존재하는 리소스입니다 |
| 500 | INTERNAL_ERROR | 서버 내부 오류입니다 |

---

## 전체 API 목록

| # | 메서드 | 엔드포인트 | 설명 | 인증 |
|---|--------|-----------|------|------|
| **1. 인증 (Auth)** |
| 1 | POST | /api/auth/register | 회원가입 | 공개 |
| 2 | POST | /api/auth/login | 로그인 및 토큰 발급 | 공개 |
| 3 | POST | /api/auth/logout | 로그아웃 (토큰 무효화) | JWT |
| 4 | POST | /api/auth/refresh | Access Token 갱신 | 공개 |
| **2. 홈 (Home)** |
| 5 | GET | /api/users/me | 내 프로필 조회 (전체 필드 반환) | JWT |
| **3. 온보딩 (Onboarding)** |
| 6 | GET | /api/onboarding/keywords | 추천 키워드 목록 조회 | JWT |
| 7 | POST | /api/learning-rooms | 학습실 생성 + AI 커리큘럼 자동 생성 | JWT |
| **4. 학습실 (Learning Room)** |
| 8 | GET | /api/learning-rooms | 내 학습실 목록 조회 | JWT |
| 9 | GET | /api/learning-rooms/{roomId}/curriculum | 전체 주차 커리큘럼 목록 | JWT |
| 10 | GET | /api/learning-rooms/{roomId}/curriculum/{weekId} | 주차 상세 조회 | JWT |
| 11 | PATCH | /api/learning-rooms/{roomId}/curriculum/{weekId}/progress | 진척도 업데이트 | JWT |
| 12 | GET | /api/learning-rooms/{roomId}/curriculum/{weekId}/recommended-videos | AI 추천 영상 목록 | JWT |
| 13 | GET | /api/learning-rooms/{roomId}/materials | AI 핵심 요약 자료 목록 | JWT |
| 14 | GET | /api/learning-rooms/{roomId}/materials/{materialId} | 요약 자료 상세 | JWT |
| 15 | GET | /api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-attempts | 퀴즈 응시 이력 목록 | JWT |
| 16 | GET | /api/quiz-attempts/{attemptId} | 퀴즈 상세 조회 (AI 해설 포함) | JWT |
| **5. 행동 로그 (Learning Event Logs)** |
| 17 | POST | /api/learning-rooms/{roomId}/events | 행동 이벤트 전송 (Redis 패턴 판단) | JWT |
| 18 | GET | /api/learning-rooms/{roomId}/materials/{materialId} | 패턴1 요약본 조회 | JWT |
| 19 | GET | /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/instant | 패턴3 돌발 퀴즈 조회 | JWT |
| 20 | POST | /api/quiz-attempts | 퀴즈 정답 제출 | JWT |
| **6. 거꾸로 학습 (Flipped Learning)** |
| 21 | POST | /api/learning-rooms/{roomId}/flipped/start | 세션 시작 | JWT |
| 22 | SSE | /api/learning-rooms/{roomId}/flipped/stream | AI 역질문 SSE 스트리밍 | JWT |
| 23 | POST | /api/learning-rooms/{roomId}/flipped/end | 세션 종료 + 최종 평가 | JWT |
| 24 | GET | /api/learning-rooms/{roomId}/flipped/result/{sessionId} | 평가 결과 + 대화 기록 조회 | JWT |
| **7. 키워드 (User Keywords)** |
| 25 | GET | /api/learning-rooms/{roomId}/keywords | 강점·약점 키워드 목록 | JWT |
| **8. 파이널 퀴즈 (Final Quiz)** |
| 26 | GET | /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final | 파이널 퀴즈 문제 목록 | JWT |
| 27 | POST | /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final/submit | 파이널 퀴즈 답안 제출 (비동기) | JWT |
| 28 | GET | /api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-report | AI 종합 분석 리포트 | JWT |
| **9. 스터디 매칭 (Study Matching)** |
| 29 | POST | /api/learning-rooms/{roomId}/match | 사용자 트리거 매칭 요청 (202 Accepted) | JWT |
| 30 | GET | /api/study-groups/suggestions | 매칭 제안 목록 조회 | JWT |
| 31 | POST | /api/study-groups/suggestions/{id}/accept | 스터디 제안 수락 | JWT |
| 32 | POST | /api/study-groups/suggestions/{id}/reject | 스터디 제안 거절 | JWT |
| 33 | GET | /api/study-groups/{groupId} | 스터디 그룹 상세 | JWT |
| 34 | GET | /api/study-groups/{groupId}/messages | 채팅 이력 조회 | JWT |
| 35 | WS | /ws/study-groups/{groupId} | 실시간 채팅 WebSocket | JWT |
| **10. 알림 (Notifications)** |
| 36 | SSE | /api/notifications/stream | 실시간 알림 SSE 연결 | JWT |
| 37 | GET | /api/notifications | 읽지 않은 알림 목록 조회 | JWT |
| 38 | PATCH | /api/notifications/{id}/read | 알림 읽음 처리 | JWT |
| **11. 마이페이지 (My Page)** |
| 39 | GET | /api/users/me | 프로필 전체 조회 | JWT |
| 40 | PATCH | /api/users/me | 프로필 수정 | JWT |
| 41 | PATCH | /api/users/me/keywords | 관심 키워드 수정 | JWT |
| 42 | DELETE | /api/users/me | 회원 탈퇴 | JWT |
| 43 | GET | /api/users/me/learning-history | 학습실 이력 목록 | JWT |

---

## 1. 인증 (Auth)

### POST /api/auth/register (공개)

회원가입 — 이메일 직접 가입 방식

**REQUEST BODY**
```json
{
  "login_id": "kimcoding",
  "password": "Moai1234!",
  "name": "김코딩",
  "nickname": "코딩왕",
  "email": "kim@moai.app",
  "birth_date": "2000-03-15",
  "interest_keywords": ["정보처리기사", "프로그래밍"]
}
```

**RESPONSE 201**
```json
{
  "success": true,
  "data": {
    "userId": "a0000000-0000-0000-0000-000000000001",
    "nickname": "코딩왕",
    "message": "환영합니다, 김코딩 님!"
  }
}
```

### POST /api/auth/login (공개)

로그인 — accessToken/refreshToken 발급, Redis에 토큰 저장

**REQUEST BODY**
```json
{
  "login_id": "kimcoding",
  "password": "Moai1234!"
}
```

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 3600,
    "userId": "a0000000-0000-0000-0000-000000000001",
    "nickname": "코딩왕"
  }
}
```

### POST /api/auth/logout (JWT 필요)

로그아웃 — Redis의 RefreshToken 무효화

**REQUEST BODY**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**RESPONSE 200**
```json
{
  "success": true,
  "message": "로그아웃되었습니다."
}
```

### POST /api/auth/refresh (공개)

Access Token 갱신

**REQUEST BODY**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...새토큰",
    "expiresIn": 3600
  }
}
```

---

## 2. 홈 (Home)

### GET /api/users/me (JWT 필요)

홈 화면 및 마이페이지 공통 — 전체 프로필 반환. 프론트가 화면별로 필요한 필드만 사용.

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "userId": "a0000000-0000-0000-0000-000000000001",
    "loginId": "kimcoding",
    "name": "김코딩",
    "nickname": "코딩왕",
    "email": "kim@moai.app",
    "birthDate": "2000-03-15",
    "profileImageUrl": "https://s3.amazonaws.com/moai/profiles/abc.jpg",
    "interestKeywords": ["정보처리기사", "프로그래밍"],
    "studySuggestionEnabled": true,
    "themePreference": "light",
    "status": "active"
  }
}
```

---

## 3. 온보딩 (Onboarding)

### GET /api/onboarding/keywords (JWT 필요)

Step 1 — 추천 키워드 태그 목록 조회

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "keywords": ["정보처리기사", "컴공", "웹개발", "CS면접", "토익", "프로그래밍"]
  }
}
```

### POST /api/learning-rooms (JWT 필요)

Step 4 — 학습실 생성 + AI 커리큘럼 + 자막 스크래핑 + 키워드 추출 자동 실행

**REQUEST BODY**
```json
{
  "subject": "정보처리기사",
  "level": "beginner",
  "duration_weeks": 10,
  "hours_per_day": 3
}
```

**RESPONSE 201**
```json
{
  "success": true,
  "data": {
    "roomId": "r1000000-0000-0000-0000-000000000001",
    "subject": "정보처리기사",
    "level": "beginner",
    "durationWeeks": 10,
    "curriculum": [
      { "weekNumber": 1, "topic": "DB 기초 및 SQL 개요" },
      { "weekNumber": 2, "topic": "DML — SELECT / INSERT / UPDATE / DELETE" }
    ]
  }
}
```

> 파이프라인: LLM 커리큘럼 생성 → YouTube 영상 추천 → 자막 스크래핑(VIDEO_TRANSCRIPTS) → LLM 핵심 키워드 추출 → WEEKLY_CURRICULUMS.keywords 저장

---

## 4. 학습실 (Learning Room)

### GET /api/learning-rooms (JWT 필요)

내 학습실 목록 조회

**RESPONSE 200**
```json
{
  "success": true,
  "data": [
    {
      "roomId": "r1000000-0000-0000-0000-000000000001",
      "subject": "정보처리기사",
      "level": "beginner",
      "currentWeek": 1,
      "durationWeeks": 10,
      "completionRate": 30.0,
      "status": "active"
    }
  ]
}
```

### GET /api/learning-rooms/{roomId}/curriculum (JWT 필요)

전체 주차 커리큘럼 목록 (Week 드롭다운)

**RESPONSE 200**
```json
{
  "success": true,
  "data": [
    { "weekId": "w2000000-...-000001", "weekNumber": 1, "topic": "DB 기초 및 SQL 개요", "completionRate": 100.0 },
    { "weekId": "w3000000-...-000002", "weekNumber": 2, "topic": "DML — SELECT / INSERT / UPDATE / DELETE", "completionRate": 30.0 }
  ]
}
```

### GET /api/learning-rooms/{roomId}/curriculum/{weekId} (JWT 필요)

주차 상세 조회 — 학습실 화면 진입 시 핵심 API

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "weekId": "w3000000-...-000002",
    "weekNumber": 1,
    "topic": "데이터베이스 아키텍처 및 트랜잭션 이해",
    "description": "이번 주차에는 DB의 기본 구조와 ACID 원리를 마스터합니다.",
    "completionRate": 30.0,
    "keywords": ["원자성", "일관성", "고립성", "지속성"],
    "resources": [
      { "type": "pdf", "title": "Week 1 데이터베이스 기초 완벽 정리.pdf", "url": "https://s3.amazonaws.com/moai/...", "size": "2.4MB" },
      { "type": "docx", "title": "기출문제 풀이집 및 해설.docx", "url": "https://s3.amazonaws.com/moai/...", "size": "1.1MB" }
    ],
    "mainVideoId": "dml_week1"
  }
}
```

### PATCH /api/learning-rooms/{roomId}/curriculum/{weekId}/progress (JWT 필요)

진척도 업데이트

**REQUEST BODY**
```json
{ "completionRate": 60.0 }
```

**RESPONSE 200**
```json
{ "success": true, "data": { "completionRate": 60.0 } }
```

### GET /api/learning-rooms/{roomId}/curriculum/{weekId}/recommended-videos (JWT 필요)

AI 추천 영상 탭

**RESPONSE 200**
```json
{
  "success": true,
  "data": [
    {
      "videoId": "dml_week1",
      "title": "데이터베이스 트랜잭션 완전 정복",
      "channelName": "코딩TV",
      "durationSec": 2530,
      "thumbnailUrl": "https://img.youtube.com/vi/dml_week1/0.jpg",
      "youtubeUrl": "https://youtu.be/dml_week1"
    }
  ]
}
```

### GET /api/learning-rooms/{roomId}/materials (JWT 필요)

AI 핵심 요약 탭 — 패턴1 감지 시 자동 생성된 자료 목록

**RESPONSE 200**
```json
{
  "success": true,
  "data": [
    {
      "materialId": "m1000000-...-000001",
      "title": "트랜잭션 ACID 4가지 원리 핵심 정리",
      "videoSegment": "영상 구간 12:45",
      "triggerKeywords": ["ACID", "트랜잭션"],
      "createdAt": "2025-03-01T10:30:00Z"
    }
  ]
}
```

### GET /api/learning-rooms/{roomId}/materials/{materialId} (JWT 필요)

요약 자료 상세 — summaryItems 카드 형태 반환

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "materialId": "m1000000-...-000001",
    "title": "트랜잭션 ACID 4가지 원리 핵심 정리",
    "videoSegment": "영상 구간 12:45",
    "summaryItems": [
      { "label": "A", "title": "Atomicity (원자성)", "desc": "트랜잭션은 완전히 수행되거나 전혀 수행되지 않아야 합니다." },
      { "label": "C", "title": "Consistency (일관성)", "desc": "시스템의 고정 요소는 트랜잭션 수행 전후에 같아야 합니다." },
      { "label": "I", "title": "Isolation (고립성)", "desc": "트랜잭션 실행 중에는 다른 트랜잭션이 끼어들 수 없습니다." },
      { "label": "D", "title": "Durability (지속성)", "desc": "성공적으로 완료된 트랜잭션의 결과는 영구적으로 반영됩니다." }
    ]
  }
}
```

### GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-attempts (JWT 필요)

퀴즈 내역 탭 — 응시 이력 목록

**RESPONSE 200**
```json
{
  "success": true,
  "data": [
    {
      "attemptId": "qa1000000-...-000001",
      "questionTitle": "트랜잭션 ACID 4가지 원리",
      "isCorrect": true,
      "videoSegment": "영상 구간 12:45",
      "attemptedAt": "2025-03-01T10:30:00Z"
    }
  ]
}
```

### GET /api/quiz-attempts/{attemptId} (JWT 필요)

퀴즈 상세 — AI 해설 포함

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "question": "다음 중 데이터베이스 트랜잭션의 'ACID' 원리에 대한 설명으로 올바르지 않은 것은?",
    "options": [
      { "label": "A", "text": "원자성(Atomicity): 트랜잭션의 연산은 모두 반영되거나 전혀 반영되지 않아야 한다." },
      { "label": "B", "text": "일관성(Consistency): 트랜잭션 중에는 다른 트랜잭션이 접근할 수 있다." },
      { "label": "I", "text": "고립성(Isolation): 둘 이상의 트랜잭션이 동시에 실행될 때 서로 영향을 주지 않아야 한다." },
      { "label": "D", "text": "지속성(Durability): 성공적으로 완료된 트랜잭션의 결과는 영구적으로 반영되어야 한다." }
    ],
    "myAnswer": "B",
    "correctAnswer": "B",
    "isCorrect": true,
    "aiExplanation": "완벽합니다. 선택하신 내용은 일관성이 아닌 '고립성(Isolation)'에 대한 설명입니다."
  }
}
```

---

## 5. 행동 로그 (Learning Event Logs)

### POST /api/learning-rooms/{roomId}/events (JWT 필요)

모든 패턴 이벤트 발생 시 호출. 백엔드가 Redis에서 패턴 판단 후 aiTriggered 반환.

**REQUEST BODY — 패턴1 (VIDEO_REWIND)**
```json
{
  "event_type": "video_rewind",
  "curriculum_id": "w3000000-...-000002",
  "payload": { "video_id": "dml_week1", "rewind_target_sec": 268 }
}
```

**REQUEST BODY — 패턴2.1 (VIDEO_PAUSE)**
```json
{
  "event_type": "video_pause",
  "curriculum_id": "w3000000-...-000002",
  "payload": { "video_id": "dml_week1", "pause_start_sec": 275, "pause_duration_sec": 185, "trigger_type": "long_pause" }
}
```

**REQUEST BODY — 패턴2.2 (TAB_DEPARTURE)**
```json
{
  "event_type": "tab_departure",
  "curriculum_id": "w3000000-...-000002",
  "payload": { "video_id": "dml_week1", "departure_sec": 300, "return_sec": 480, "departure_count": 3, "current_keyword": "ACID" }
}
```

**REQUEST BODY — 패턴3 (VIDEO_SKIP)**
```json
{
  "event_type": "video_skip",
  "curriculum_id": "w3000000-...-000002",
  "payload": { "video_id": "dml_week1", "skip_from_sec": 120, "skip_to_sec": 300 }
}
```

**RESPONSE 200 — aiTriggered: false (패턴 미발동)**
```json
{ "success": true, "data": { "aiTriggered": false } }
```

**RESPONSE 200 — aiTriggered: true (패턴1 발동)**
```json
{
  "success": true,
  "data": {
    "aiTriggered": true,
    "eventType": "video_rewind",
    "extractedKeywords": ["ACID", "트랜잭션"],
    "materialId": "m1000000-...-000001"
  }
}
```

**RESPONSE 200 — aiTriggered: true (패턴2.1 / 패턴2.2 발동)**
```json
{
  "success": true,
  "data": {
    "aiTriggered": true,
    "eventType": "video_pause",
    "extractedKeywords": ["ACID", "트랜잭션"],
    "materialId": "m1000000-...-000001"
  }
}
```

**RESPONSE 200 — aiTriggered: true (패턴3 발동)**
```json
{ "success": true, "data": { "aiTriggered": true, "eventType": "video_skip" } }
```

> 프론트 분기: video_rewind → 보충 자료 팝업 / video_pause·tab_departure → 요약 노트 팝업 / video_skip → 돌발 퀴즈 팝업

### GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/instant (JWT 필요)

패턴3 발동 후 돌발 4지선다 퀴즈 1문제 조회

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "questionId": "qq1000000-...-000001",
    "quizId": "q1000000-...-000001",
    "questionType": "multiple",
    "question": "다음 중 데이터베이스 트랜잭션의 'ACID' 원리에 대한 설명으로 올바르지 않은 것은?",
    "options": [
      { "label": "A", "text": "원자성: 트랜잭션의 연산은 모두 반영되거나 전혀 반영되지 않아야 한다." },
      { "label": "B", "text": "일관성: 트랜잭션 중에는 다른 트랜잭션이 접근할 수 있다." },
      { "label": "C", "text": "고립성: 둘 이상의 트랜잭션이 동시에 실행될 때 서로 영향을 주지 않아야 한다." },
      { "label": "D", "text": "지속성: 성공적으로 완료된 트랜잭션의 결과는 영구적으로 반영되어야 한다." }
    ],
    "timeLimitSec": 60,
    "relatedKeyword": "ACID"
  }
}
```

### POST /api/quiz-attempts (JWT 필요)

퀴즈 정답 제출 — 돌발 및 파이널 퀴즈 공통

**REQUEST BODY**
```json
{
  "questionId": "qq1000000-...-000001",
  "quizId": "q1000000-...-000001",
  "selected": "B"
}
```

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "attemptId": "qa3000000-...-000003",
    "isCorrect": false,
    "correctAnswer": "A",
    "aiExplanation": "아쉽네요! 선택하신 [B]는 일관성에 대한 설명입니다. 질문에서 묻는 원리는 '고립성(Isolation)'입니다.",
    "relatedVideoId": "dml_week1",
    "relatedTimestamp": 275
  }
}
```

---

## 6. 거꾸로 학습 (Flipped Learning)

> 실행 순서: ① GET /curriculum/{weekId} → keywords ② POST /flipped/start → sessionId ③ 모달 오픈 ④ SSE /flipped/stream 반복 ⑤ POST /flipped/end → 결과

### POST /api/learning-rooms/{roomId}/flipped/start (JWT 필요)

세션 시작 — session_id 발급 + 첫 안내 문구 생성

**REQUEST BODY**
```json
{ "curriculum_id": "w3000000-...-000002" }
```

**RESPONSE 201**
```json
{
  "success": true,
  "data": {
    "sessionId": "sess-AAA-0000-0000-0000-000000000001",
    "firstMessage": "이번 주차에서 배운 '원자성, 일관성, 고립성, 지속성'에 대해 설명해주세요!"
  }
}
```

> firstMessage는 WEEKLY_CURRICULUMS.keywords 기반 생성, AI_INTERACTIONS에 role:"assistant"로 저장

### SSE /api/learning-rooms/{roomId}/flipped/stream (JWT 필요)

AI 역질문 SSE 스트리밍 (Content-Type: text/event-stream)

**REQUEST BODY**
```json
{
  "sessionId": "sess-AAA-0000-0000-0000-000000000001",
  "message": "트랜잭션의 원자성이란 계좌 이체를 생각하면 쉬워요..."
}
```

**SSE 스트리밍 응답**
```
data: {"type": "token", "content": "아하! 계좌이체 비유를 들으니 완벽하게 이해가 가요! "}
data: {"type": "token", "content": "선생님 최고 👍 그렇다면 만약 "}
data: {"type": "token", "content": "송금 과정에서 오류가 났을 때, "}
data: {"type": "counter_question", "content": "원자성을 지키기 위해 아예 처음으로 되돌리려면 어떤 명령어를 써야 할까요?"}
data: {"type": "done", "interactionId": "uuid-xxx"}
```

### POST /api/learning-rooms/{roomId}/flipped/end (JWT 필요)

세션 종료 + AI 최종 평가

**REQUEST BODY**
```json
{ "sessionId": "sess-AAA-0000-0000-0000-000000000001" }
```

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "sessionId": "sess-AAA-0000-0000-0000-000000000001",
    "flippedResult": "pass",
    "score": 95,
    "gainedKeywords": ["원자성", "비유적_설명", "TCL_기초"],
    "weakKeywords": ["고립성(Isolation)_개념"],
    "feedback": "이해도 95% - 완벽한 비유로 설명하는 1타 강사!"
  }
}
```

> 백엔드 처리 순서: ① AI 최종 평가 ② USER_KEYWORDS 업데이트 ③ 주차 진척도 +30% 반영 ④ 학습실 전체 진척도 재계산

### GET /api/learning-rooms/{roomId}/flipped/result/{sessionId} (JWT 필요)

평가 결과 + 전체 대화 기록

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "score": 95,
    "gainedKeywords": ["원자성", "비유적_설명", "TCL_기초"],
    "weakKeywords": ["고립성(Isolation)_개념"],
    "feedback": "이해도 95% - 완벽한 비유로 설명하는 1타 강사!",
    "conversations": [
      { "role": "assistant", "content": "이번 주차에서 배운 '원자성, 일관성, 고립성, 지속성'에 대해 설명해주세요!" },
      { "role": "user", "content": "트랜잭션의 원자성이란 계좌 이체를 생각하면 쉬워요..." },
      { "role": "assistant", "content": "아하! 계좌이체 비유를 들으니 완벽하게 이해가 가요! 그렇다면..." }
    ]
  }
}
```

---

## 7. 키워드 (User Keywords)

### GET /api/learning-rooms/{roomId}/keywords (JWT 필요)

강점·약점 키워드 목록 조회

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "strengths": [
      { "keyword": "원자성", "isResolved": false },
      { "keyword": "TCL_기초", "isResolved": false }
    ],
    "weaknesses": [
      { "keyword": "DML", "weaknessCount": 2, "isResolved": false },
      { "keyword": "고립성(Isolation)_개념", "weaknessCount": 1, "isResolved": false }
    ]
  }
}
```

---

## 8. 파이널 퀴즈 (Final Quiz)

### GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final (JWT 필요)

파이널 퀴즈 문제 목록 — 5문제 서술형(essay)

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "quizId": "qf1000000-...-000001",
    "title": "Week 1 파이널 퀴즈",
    "questions": [
      {
        "questionId": "qq2000000-...-000001",
        "questionType": "essay",
        "order": 1,
        "question": "트랜잭션의 ACID 속성 중 고립성(Isolation)의 개념을 쇼핑몰 결제 상황에 빗대어 설명해 보세요.",
        "maxLength": 500,
        "tip": "결제 도중 다른 사용자가 재고를 수정할 때 발생할 수 있는 혼선을 생각해보세요."
      }
    ]
  }
}
```

### POST /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final/submit (JWT 필요)

파이널 퀴즈 전체 답안 제출 → AI 분석 비동기 시작

**REQUEST BODY**
```json
{
  "quizId": "qf1000000-...-000001",
  "answers": [
    {
      "questionId": "qq2000000-...-000001",
      "answer": "트랜잭션의 고립성은 여러 트랜잭션이 동시에 실행될 때 서로 간섭하지 못하게 하는 성질입니다..."
    }
  ]
}
```

**RESPONSE 202 (Accepted)**
```json
{
  "success": true,
  "data": {
    "reportId": "rpt1000000-...-000001",
    "status": "analyzing",
    "estimatedSec": 15
  }
}
```

### GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-report (JWT 필요)

AI 종합 분석 리포트 — 폴링으로 status 확인 후 completed 시 표시

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "finalScore": 92,
    "radarData": { "개념이해도": 90, "응용력": 85, "논리력": 95, "키워드적중률": 88 },
    "questions": [
      {
        "order": 1,
        "question": "트랜잭션 고립성(Isolation) 실생활 적용 설명",
        "score": 20,
        "maxScore": 20,
        "isCorrect": true,
        "myAnswer": "트랜잭션의 고립성은...",
        "gainedKeywords": ["고립성", "데이터_무결성"],
        "missingKeywords": ["Dirty_Read"],
        "aiComment": "실생활 결제 상황에 빗댄 설명이 훌륭합니다. 다만 'Dirty Read' 현상 방지 내용을 추가하면 완벽합니다."
      }
    ]
  }
}
```

---

## 9. 스터디 매칭 (Study Matching)

> 매칭 트리거: 사용자가 POST /api/learning-rooms/{roomId}/match 호출 시 비동기 실행
> 매칭 조건: 동일 키워드 + created_at 7일 이내 + Redis 토큰 존재(온라인)

### POST /api/learning-rooms/{roomId}/match (JWT 필요)

사용자 트리거 매칭 요청 — 비동기 실행, 즉시 202 Accepted 반환

**REQUEST BODY**
```json
{ "curriculumId": "wk1000000-...-000001" }
```

**RESPONSE 202 Accepted**
```json
{
  "success": true,
  "data": { "status": "searching" }
}
```

> 결과는 SSE /api/notifications/stream으로 전달 (`study_match` 성공, `study_no_candidate` 후보 없음)
> 에러: 403 STUDY_006(studySuggestionEnabled=false), 404 USER_001 / ROOM_001 / CURRICULUM_001

### GET /api/study-groups/suggestions (JWT 필요)

매칭 제안 목록 조회 (pending 상태만)

**RESPONSE 200**
```json
{
  "success": true,
  "data": [
    {
      "suggestionId": "sug1000000-...-000001",
      "suggestedRole": "mentee",
      "partner": {
        "nickname": "B학생(멘토)",
        "profileImageUrl": "https://s3.amazonaws.com/moai/profiles/b.jpg",
        "strengthKeyword": "고립성_완벽이해"
      },
      "matchScore": 0.98,
      "matchKeyword": "고립성(Isolation)_개념",
      "matchReason": "AI가 대화 데이터를 분석하여, 회원님의 취약점을 완벽히 해결해 줄 1:1 멘토를 매칭했습니다.",
      "status": "pending"
    }
  ]
}
```

### POST /api/study-groups/suggestions/{id}/accept (JWT 필요)

스터디 제안 수락

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "suggestionId": "sug1000000-...-000001",
    "status": "accepted",
    "groupStatus": "pending_acceptance"
  }
}
```

### POST /api/study-groups/suggestions/{id}/reject (JWT 필요)

스터디 제안 거절

**RESPONSE 200**
```json
{ "success": true, "data": { "status": "rejected" } }
```

### GET /api/study-groups/{groupId} (JWT 필요)

스터디 그룹 상세

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "groupId": "g1000000-...-000001",
    "matchKeyword": "고립성(Isolation)_개념",
    "status": "active",
    "partner": {
      "userId": "c0000000-...-000003",
      "nickname": "B학생(멘토)",
      "profileImageUrl": "https://s3.amazonaws.com/moai/profiles/b.jpg",
      "role": "mentor",
      "isOnline": true,
      "strengthKeyword": "고립성_완벽이해"
    }
  }
}
```

> isOnline은 Redis 토큰 존재 여부로 판단

### GET /api/study-groups/{groupId}/messages (JWT 필요)

채팅 이력 조회 (페이지네이션: ?page=0&size=50)

**RESPONSE 200**
```json
{
  "success": true,
  "data": [
    {
      "messageId": "msg1000000-...-000001",
      "senderType": "user",
      "senderId": "c0000000-...-000003",
      "senderNickname": "B학생(멘토)",
      "content": "안녕하세요! 영상 보시다가 헷갈리는 부분 있으신가요?",
      "isAiCorrection": false,
      "sentAt": "2025-03-01T10:30:00Z"
    }
  ]
}
```

### WS wss://api.moai.app/ws/study-groups/{groupId} (JWT 필요)

실시간 채팅 WebSocket — STOMP 프로토콜

**클라이언트 → 서버**
```json
{ "content": "Read Committed에서 왜 팬텀 리드 현상이 발생하나요 ㅜㅜ" }
```

**서버 → 클라이언트**
```json
{
  "messageId": "msg3000000-...-000003",
  "senderType": "user",
  "senderId": "a0000000-...-000001",
  "senderNickname": "김코딩",
  "content": "Read Committed에서 왜 팬텀 리드 현상이 발생하나요 ㅜㅜ",
  "isAiCorrection": false,
  "sentAt": "2025-03-01T10:32:00Z"
}
```

---

## 10. 알림 (Notifications)

### SSE /api/notifications/stream (JWT 필요)

로그인 시 즉시 연결 — 실시간 알림 대기 (Content-Type: text/event-stream)

**SSE 이벤트 — 매칭 제안**
```
data: {"type":"study_match","message":"완벽한 상호 보완 파트너를 찾았습니다!","suggestionId":"sug1...","partner":{"nickname":"B학생(멘토)","role":"mentor","strengthKeyword":"고립성_완벽이해"},"matchScore":0.98,"matchKeyword":"고립성(Isolation)_개념"}
```

**SSE 이벤트 — 매칭 후보 없음**
```
data: {"type":"study_no_candidate","message":"현재 매칭 가능한 파트너가 없습니다"}
```

**SSE 이벤트 — 상대방 수락**
```
data: {"type":"study_accepted","message":"스터디가 시작되었습니다!","groupId":"g1..."}
```

**SSE 이벤트 — 상대방 거절**
```
data: {"type":"study_rejected","message":"상대방이 스터디 요청을 거절했습니다.","suggestionId":"sug1..."}
```

**SSE 이벤트 — 채팅 메시지 알림**
```
data: {"type":"chat_message","message":"B학생(멘토)님이 메시지를 보냈습니다.","groupId":"g1...","sender":{"nickname":"B학생(멘토)","profileImageUrl":"https://s3.amazonaws.com/moai/profiles/b.jpg"},"preview":"Read Committed에서 왜 팬텀 리드 현상이..."}
```

### GET /api/notifications (JWT 필요)

읽지 않은 알림 목록 조회

**RESPONSE 200**
```json
{
  "success": true,
  "data": [
    {
      "notificationId": "n1000000-...-000001",
      "type": "study_match",
      "message": "완벽한 상호 보완 파트너를 찾았습니다!",
      "referenceId": "sug1000000-...-000001",
      "isRead": false,
      "createdAt": "2025-03-01T10:30:00Z"
    }
  ]
}
```

### PATCH /api/notifications/{id}/read (JWT 필요)

알림 읽음 처리

**RESPONSE 200**
```json
{
  "success": true,
  "data": { "notificationId": "n1000000-...-000001", "isRead": true }
}
```

---

## 11. 마이페이지 (My Page)

### PATCH /api/users/me (JWT 필요)

프로필 수정

**REQUEST BODY**
```json
{
  "nickname": "새닉네임",
  "profileImageUrl": "https://s3.amazonaws.com/moai/profiles/new.jpg",
  "themePreference": "dark"
}
```

**RESPONSE 200**
```json
{
  "success": true,
  "data": {
    "nickname": "새닉네임",
    "profileImageUrl": "https://s3.amazonaws.com/moai/profiles/new.jpg",
    "themePreference": "dark"
  }
}
```

### PATCH /api/users/me/keywords (JWT 필요)

관심 키워드 수정 → AI 추천 실시간 갱신

**REQUEST BODY**
```json
{ "interestKeywords": ["CS면접", "웹개발"] }
```

**RESPONSE 200**
```json
{ "success": true, "data": { "interestKeywords": ["CS면접", "웹개발"] } }
```

### DELETE /api/users/me (JWT 필요)

회원 탈퇴 — 모든 데이터 파기, USERS.status = "inactive"

**REQUEST BODY**
```json
{ "password": "Moai1234!" }
```

**RESPONSE 200**
```json
{ "success": true, "message": "회원 탈퇴가 완료되었습니다. 모든 데이터가 파기되었습니다." }
```

### GET /api/users/me/learning-history (JWT 필요)

학습실 이력 목록

**RESPONSE 200**
```json
{
  "success": true,
  "data": [
    {
      "roomId": "r1000000-...-000001",
      "subject": "정보처리기사",
      "level": "beginner",
      "completionRate": 30.0,
      "status": "active",
      "createdAt": "2025-03-01T09:00:00Z"
    }
  ]
}
```
