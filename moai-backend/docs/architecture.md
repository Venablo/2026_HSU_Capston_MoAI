## 2. 핵심 사용자 흐름 (Main User Flow)

```mermaid
flowchart TD
    A([회원가입/로그인]) --> B[홈 화면]
    B --> C{학습실 있음?}
    C -->|없음| D[온보딩 4단계]
    C -->|있음| F[학습실 목록]
    
    D --> D1[Step1: 키워드 선택]
    D1 --> D2[Step2: 난이도 선택]
    D2 --> D3[Step3: 기간/시간 설정]
    D3 --> D4[Step4: AI 커리큘럼 생성]
    D4 -->|"LLM 커리큘럼 생성\n+ YouTube 영상 추천\n+ 자막 스크래핑\n+ 키워드 추출"| F

    F --> G[학습실 상세 - 주차별]
    
    G --> H[📺 영상 시청]
    H --> I{행동 패턴 감지}
    
    I -->|패턴1: 되감기 3회| J[요약 자료 팝업]
    I -->|패턴2: 일시정지/탭이탈| K[요약 노트 팝업]
    I -->|패턴3: 빠른 스킵 3회| L[돌발 퀴즈 팝업]
    I -->|정상 시청| H
    
    J --> J1[네, 요약본 볼래요]
    J --> H
    K --> H
    L --> L1{퀴즈 정답?}
    L1 -->|정답| H
    L1 -->|오답| L2[약점 키워드 누적]
    L2 --> H

    H -->|시청 완료\n진척도 +40%| M[거꾸로 학습]
    
    M --> M1[AI 튜터와 대화 - SSE]
    M1 --> M2[최종 평가]
    M2 -->|"강점/약점 키워드 추출\n진척도 +30%"| N{매칭 조건 충족?}
    
    N -->|약점 2개↑| O[AI 스터디 매칭]
    N -->|미충족| P[파이널 퀴즈]
    O --> O1[매칭 알림 SSE 전송]
    O1 --> O2{양측 수락?}
    O2 -->|수락| O3[채팅방 개설 - WebSocket]
    O2 -->|거절| P
    
    P --> P1[서술형 5문제]
    P1 --> P2[AI 비동기 채점]
    P2 --> P3["종합 분석 리포트\n(레이더차트)\n진척도 +30%"]
    P3 --> Q([주차 완료 - 100%])
```

---

## 3. 패턴 감지 데이터 흐름 (Pattern Detection Flow)

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant BE as Spring Boot
    participant RD as Redis
    participant DB as PostgreSQL
    participant AI as LLM API

    Note over FE: 영상 시청 중 되감기 발생
    FE->>BE: POST /events {event_type: "video_rewind", rewind_target_sec: 268}
    
    BE->>RD: RPUSH moai:rewind:{userId}:{videoId} 268
    BE->>RD: LLEN → 현재 1개
    RD-->>BE: 임계값(3) 미달
    BE-->>FE: {aiTriggered: false}

    Note over FE: 2번째 되감기
    FE->>BE: POST /events {rewind_target_sec: 270}
    BE->>RD: RPUSH → 현재 2개
    RD-->>BE: 임계값 미달
    BE-->>FE: {aiTriggered: false}

    Note over FE: 3번째 되감기 (같은 구간)
    FE->>BE: POST /events {rewind_target_sec: 265}
    BE->>RD: RPUSH → 현재 3개
    BE->>RD: 최댓값(270) - 최솟값(265) = 5초 < 10초 허용범위
    RD-->>BE: ✅ 패턴1 발동!
    
    BE->>RD: SET moai:cooldown:{userId}:{videoId}:pattern1 (TTL 5분)
    BE->>DB: INSERT LEARNING_EVENT_LOGS
    BE->>DB: SELECT VIDEO_TRANSCRIPTS WHERE start_sec <= 268 AND end_sec >= 268
    DB-->>BE: 해당 구간 자막 텍스트
    BE->>AI: 자막 텍스트 + 키워드 추출/요약 프롬프트
    AI-->>BE: {summaryItems: [{label: "A", title: "...", desc: "..."}]}
    BE->>DB: INSERT CUSTOM_MATERIALS
    BE->>DB: INSERT USER_KEYWORDS (weakness)
    BE-->>FE: {aiTriggered: true, eventType: "video_rewind", materialId: "m1...", extractedKeywords: ["ACID"]}

    Note over FE: 요약 자료 팝업 표시
    FE->>BE: GET /materials/{materialId}
    BE-->>FE: summaryItems 카드 데이터
```

---

## 4. 거꾸로 학습 흐름 (Flipped Learning Flow)

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant BE as Spring Boot
    participant DB as PostgreSQL
    participant AI as LLM API
    participant RD as Redis

    FE->>BE: POST /flipped/start {curriculum_id}
    BE->>DB: SELECT WEEKLY_CURRICULUMS.keywords
    DB-->>BE: ["원자성", "일관성", "고립성", "지속성"]
    BE->>AI: 키워드 기반 첫 안내 문구 생성
    AI-->>BE: "이번 주차에서 배운 '원자성, 일관성...'에 대해 설명해주세요!"
    BE->>DB: INSERT AI_INTERACTIONS (role: assistant, session_id 발급)
    BE-->>FE: {sessionId, firstMessage}

    Note over FE: 모달 오픈, 대화 시작

    FE->>BE: SSE /flipped/stream {sessionId, message: "원자성이란..."}
    BE->>DB: INSERT AI_INTERACTIONS (role: user)
    BE->>DB: SELECT AI_INTERACTIONS WHERE session_id (전체 이력)
    BE->>AI: [전체 대화 이력 + 새 메시지] → SSE 스트리밍 요청
    
    loop 토큰 단위 스트리밍
        AI-->>BE: token chunk
        BE-->>FE: data: {type: "token", content: "아하! 계좌이체 비유를..."}
    end
    BE-->>FE: data: {type: "counter_question", content: "그렇다면 ROLLBACK은?"}
    BE-->>FE: data: {type: "done", interactionId: "uuid"}
    BE->>DB: INSERT AI_INTERACTIONS (role: assistant, 전체 응답)

    Note over FE: "설명 완료하고 최종 평가받기" 클릭

    FE->>BE: POST /flipped/end {sessionId}
    BE->>DB: SELECT 전체 대화 이력
    BE->>AI: 전체 대화 → 최종 평가 프롬프트
    AI-->>BE: {score: 95, gainedKeywords, weakKeywords, feedback}
    BE->>DB: INSERT FLIPPED_SESSIONS
    BE->>DB: UPSERT USER_KEYWORDS (강점/약점)
    
    Note over BE: 매칭 엔진 실행
    BE->>DB: SELECT USER_KEYWORDS WHERE weakness_count >= 2
    BE->>DB: SELECT 동일 키워드 strength 보유자
    BE->>RD: 상대방 Redis 토큰 존재 여부 확인
    
    alt 매칭 대상 발견
        BE->>DB: INSERT STUDY_GROUPS + STUDY_SUGGESTIONS
        BE->>DB: INSERT NOTIFICATIONS
        BE-->>FE: SSE 알림 → "완벽한 상호 보완 파트너를 찾았습니다!"
    end
    
    BE-->>FE: {flippedResult: "pass", score: 95, gainedKeywords, weakKeywords, feedback}
```

---

## 5. 학습실 생성 파이프라인 (Learning Room Creation Pipeline)

```mermaid
flowchart LR
    A[POST /learning-rooms] --> B[LEARNING_ROOMS INSERT]
    B --> C[LLM API 호출]
    C -->|"subject + level +\nduration_weeks 프롬프트"| D[주차별 topic/description 생성]
    D --> E[WEEKLY_CURRICULUMS INSERT x N주]
    E --> F[LLM API 호출]
    F -->|"주차별 topic 프롬프트\n→ YouTube 영상 추천"| G[resources JSONB 저장]
    G --> H["ProcessBuilder\nPython 스크립트 실행"]
    H -->|youtube-transcript-api| I[자막 JSON 수신]
    I --> J[VIDEO_TRANSCRIPTS INSERT - 청크 단위]
    J --> K[LLM API 호출]
    K -->|자막 텍스트 → 키워드 추출| L[WEEKLY_CURRICULUMS.keywords 업데이트]
    L --> M[201 Created 응답]

    style A fill:#7c3aed,color:#fff
    style M fill:#22c55e,color:#fff
    style C fill:#f59e0b,color:#fff
    style F fill:#f59e0b,color:#fff
    style H fill:#3b82f6,color:#fff
    style K fill:#f59e0b,color:#fff
```

---

## 6. 스터디 매칭 + 채팅 흐름 (Study Matching & Chat Flow)

```mermaid
sequenceDiagram
    participant A as 학생A (멘티)
    participant BE as Spring Boot
    participant DB as PostgreSQL
    participant RD as Redis
    participant B as 학생B (멘토)

    Note over BE: 거꾸로 학습 종료 후 매칭 엔진 자동 실행
    BE->>DB: 학생A 약점: "DML" (weakness_count >= 2)
    BE->>DB: 학생B 강점: "DML" (strength)
    BE->>RD: 학생B 온라인? → YES
    BE->>DB: INSERT STUDY_GROUPS (pending_acceptance)
    BE->>DB: INSERT STUDY_SUGGESTIONS x 2 (양측)
    BE->>DB: INSERT NOTIFICATIONS x 2

    BE-->>A: SSE 알림: "완벽한 파트너를 찾았습니다!"
    BE-->>B: SSE 알림: "완벽한 파트너를 찾았습니다!"

    A->>BE: POST /suggestions/{id}/accept
    BE->>DB: UPDATE suggestion status = accepted
    BE-->>B: SSE 알림: "상대방이 수락했습니다"

    B->>BE: POST /suggestions/{id}/accept
    BE->>DB: UPDATE STUDY_GROUPS status = active
    BE->>DB: INSERT STUDY_MEMBERS x 2
    BE-->>A: SSE 알림: "스터디가 시작되었습니다!"
    BE-->>B: SSE 알림: "스터디가 시작되었습니다!"

    Note over A,B: 채팅 시작

    A->>BE: GET /study-groups/{groupId}/messages (이전 이력)
    A->>BE: WebSocket CONNECT /ws/study-groups/{groupId}
    B->>BE: WebSocket CONNECT /ws/study-groups/{groupId}

    B->>BE: WS SEND {content: "안녕하세요! 헷갈리는 부분 있나요?"}
    BE->>DB: INSERT STUDY_MESSAGES
    BE-->>A: WS PUSH {senderNickname: "B학생(멘토)", content: "..."}

    A->>BE: WS SEND {content: "Read Committed에서 왜 팬텀 리드가..."}
    BE->>DB: INSERT STUDY_MESSAGES
    BE-->>B: WS PUSH {senderNickname: "김코딩", content: "..."}
```

---

## 7. 파이널 퀴즈 + AI 리포트 흐름 (Final Quiz Flow)

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant BE as Spring Boot
    participant DB as PostgreSQL
    participant AI as LLM API

    FE->>BE: GET /quizzes/final
    BE->>DB: SELECT QUIZZES + QUIZ_QUESTIONS (essay x 5)
    BE-->>FE: {quizId, questions: [{questionId, question, maxLength, tip}]}

    Note over FE: 5문제 서술형 답변 작성

    FE->>BE: POST /quizzes/final/submit {quizId, answers: [...]}
    BE->>DB: INSERT QUIZ_REPORTS (status: "analyzing")
    BE-->>FE: 202 Accepted {reportId, status: "analyzing", estimatedSec: 15}

    Note over FE: 로딩 화면 표시 + 폴링 시작

    rect rgb(255, 243, 224)
        Note over BE: @Async 비동기 처리
        loop 문항별 AI 채점
            BE->>AI: 학생 답변 + 채점 프롬프트
            AI-->>BE: {score, gainedKeywords, missingKeywords, aiComment}
            BE->>DB: INSERT QUIZ_ATTEMPTS
        end
        BE->>DB: UPDATE QUIZ_REPORTS (status: "completed", finalScore, radarData, questions)
        BE->>DB: UPSERT USER_KEYWORDS (정답→강점, 오답→약점)
    end

    FE->>BE: GET /quiz-report (폴링)
    BE-->>FE: {status: "analyzing"}
    FE->>BE: GET /quiz-report (재폴링)
    BE-->>FE: {status: "completed", finalScore: 92, radarData: {...}, questions: [...]}

    Note over FE: 리포트 화면 + 레이더차트 렌더링
```

---

## 8. 진척도 계산 흐름 (Completion Rate Flow)

```mermaid
flowchart TD
    A[주차 학습 시작 - 0%] --> B[📺 영상 시청 완료]
    B -->|"PATCH /progress\n→ completion_rate = 40%"| C[진척도 40%]
    
    C --> D[🎙️ 거꾸로 학습 완료]
    D -->|"POST /flipped/end\n→ completion_rate = 70%"| E[진척도 70%]
    
    E --> F[📝 파이널 퀴즈 완료]
    F -->|"QUIZ_REPORTS status=completed\n→ completion_rate = 100%"| G[진척도 100%]

    G --> H[LEARNING_ROOMS.completion_rate 갱신]
    H -->|"전체 주차 평균\n예: 1주차 100% + 2주차 0% = 50%"| I[학습실 전체 달성률]

    style A fill:#f3f4f6,color:#000
    style C fill:#fbbf24,color:#000
    style E fill:#fb923c,color:#000
    style G fill:#22c55e,color:#fff
```