# CLAUDE.md
> **구현 범위: Backend (Spring Boot) + Infrastructure만 구현 대상입니다. Frontend 코드는 작성하지 않습니다.**

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MoAI (AI-based Study Platform) backend — Spring Boot 3.5 application on Java 17. Stack: MySQL + Redis + AWS S3, JWT auth, STOMP/WebSocket for chat, SSE for real-time notifications and flipped-learning events, WebFlux `WebClient` for LLM calls, Apache PDFBox for material generation, and a pluggable subtitle scraper (`SubtitleScraper` 인터페이스 — `subtitle.provider` 프로퍼티로 ytdlp/supadata 토글).

## Reference docs

- `docs/api_spec.md` — REST/SSE/WebSocket endpoint contracts
- `docs/architecture.md` — detailed flows and sequence diagrams
- `docs/db_schema.md` — table definitions and relationships

## Build & Run Commands

```bash
# Build
./gradlew build

# Run application
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.moai.backend.SomeTest"

# Start infrastructure (MySQL 8.0 + Redis)
docker-compose up -d
```

## Required Environment Variables

- `DB_URL` — JDBC connection string (e.g. `jdbc:mysql://localhost:3306/moai_db`)
- `DB_USERNAME` / `DB_PASSWORD` — MySQL credentials
- `JWT_SECRET_KEY` — HMAC-SHA signing key for JWT tokens
- `LLM_API_KEY` — LLM API key (OpenAI or Gemini)
- `LLM_API_URL` — LLM API endpoint URL
- `LLM_MODEL` — Model name to use (e.g. gpt-4o)
- `SUBTITLE_PROVIDER` — `ytdlp`(로컬 기본, 미설정 시 동일) 또는 `supadata`(EC2 등 클라우드 IP)
- `SUPADATA_API_KEY` — Supadata Transcript API 키 (`SUBTITLE_PROVIDER=supadata` 일 때만 필수)

## Architecture

### Package layout: `com.moai.backend`

- **`domain/{feature}/`** — feature-based modules, each with `controller/`, `service/`, `dto/`, `entity/`, `repository/` sub-packages. Current domains:
  - `auth` — login/logout/token reissue
  - `users` — signup, user entity, mypage
  - `onboarding` — initial user keyword/interest setup
  - `learningroom` — learning room CRUD, file URLs via S3
  - `curriculum` — LLM-driven weekly curriculum + YouTube video_id recommendation
  - `transcript` — subtitle scraping (Python subprocess) + storage
  - `material` — week-level study materials (PDF via PDFBox)
  - `flipped` — 거꾸로 학습 (flipped-learning) flow, SSE-driven
  - `quiz` — final quiz generation, async grading, AI analysis report
  - `keyword` — strength/weakness keyword aggregation
  - `eventlog` — YouTube-IFrame behavior events → Redis pattern detection
  - `study` — matching engine + study proposals (uses Redis online state)
  - `chat` — STOMP/WebSocket real-time chat + offline-notification bridge
  - `notification` — SSE push + mypage history
- **`global/`** — cross-cutting concerns:
  - `auth/` — `JwtTokenProvider` (token creation/validation/parsing, access/refresh `token_type` claim), `JwtAuthenticationFilter` (Spring Security filter)
  - `config/` — `SecurityConfig` (stateless JWT), `RedisConfig`, WebSocket/STOMP config
  - `common/` — `ApiResponse<T>` (standard success wrapper), `BaseTimeEntity`
  - `exception/` — `CustomException(status, code, message)`, `GlobalExceptionHandler`
  - `llm/` — shared `LlmService` (WebClient-based), `LlmConfig`, request/response DTOs. All LLM calls go through this module
  - `s3/` — `S3Service` / `S3Config` (AWS SDK v2) for learning-room file URLs
  - `subtitle/` — `SubtitleScraper` 인터페이스 + 두 구현체. `subtitle.provider` 프로퍼티로 토글:
    - `YtdlpSubtitleScraper` (기본/local) — `scripts/scrape_subtitle.py` 를 `ProcessBuilder` 로 실행 (yt-dlp 기본 + youtube-transcript-api 폴백)
    - `SupadataSubtitleScraper` (EC2/prod) — Supadata Transcript API (`GET /v1/transcript`, `mode=native` + `text=false` 고정, `x-api-key` 헤더 인증). EC2 IP 대역의 YouTube 봇 탐지(IP 밴) 우회 목적
    - 호출부(`CurriculumEnrichmentService`)는 인터페이스에만 의존. 에러는 `SubtitleScrapeException(SubtitleErrorCode)` 로 분기 (Supadata 신규 코드 — `SUPADATA_AUTH_FAILED` 401/403, `SUPADATA_QUOTA_EXCEEDED` 402)
  - `material/` — `MaterialGeneratorService` + `MaterialContent` (PDF output)

### Key patterns

- **Stateless JWT auth**: Access token (30min) + Refresh token (14 days) with a `token_type` claim to distinguish them (refresh-only endpoints reject access tokens and vice versa). Refresh tokens stored in Redis (`RT:{email}`). Logout blacklists access tokens in Redis with remaining TTL.
- **API response format**: All success responses use `ApiResponse.success(status, message, data)`. Errors use `ErrorResponse` with `status`, `code`, `message`.
- **Exception handling**: Throw `CustomException(httpStatus, errorCode, message)` — caught globally by `GlobalExceptionHandler`.
- **Security permit paths**: Only /api/auth/register, /api/auth/login, and /api/auth/refresh are public; all other endpoints require a valid JWT.
- **Entities**: Use `@Builder` with protected no-arg constructor. Passwords stored BCrypt-encoded.
- **DTOs**: Use Lombok `@Getter` and Jakarta Validation annotations (`@NotBlank`, `@Email`, etc.).
- **Transactions**: Service classes default to `@Transactional(readOnly = true)` at class level; write operations override with `@Transactional`.
- **Strict Layered Architecture**: 비즈니스 로직은 절대 Controller에 작성하지 않고 오직 Service 계층에만 구현합니다. Controller는 프론트엔드와의 데이터 교환(DTO 변환 및 응답)만 담당합니다.
- **Real-time channels**:
  - SSE emitters: flipped-learning event stream + notification push (requires JWT via query param or header — see `SecurityConfig`)
  - STOMP/WebSocket: chat. Offline recipients fall back to notification-table + SSE
- **LLM calls**: always via `global/llm/LlmService` (reactive `WebClient`). Domains never instantiate their own HTTP client for LLM.

### Infrastructure

- MySQL 8.0 on port 3306 (database: `moai_db`) — production/local runtime
- Redis on port 6379 (token storage, blacklist, event counters, online-presence)
- Tests use H2 in-memory (`testRuntimeOnly 'com.h2database:h2'`) — running `./gradlew test` does NOT require a live MySQL/Redis.
- JPA `ddl-auto: update` — schema managed by Hibernate
- Config file is `src/main/resources/application.yaml` (not `application.yml`). `subtitle.script-path` points at the Python scraper.
- ytdlp 사용 환경(로컬, `SUBTITLE_PROVIDER=ytdlp`)은 Python 3, `yt-dlp`, `youtube-transcript-api` 설치 필요 (`pip install -r src/main/resources/scripts/requirements.txt`). Supadata 사용 환경(EC2, `SUBTITLE_PROVIDER=supadata`)은 Python 의존성 불필요.

### Key dependencies (non-obvious)

- `io.jsonwebtoken:jjwt 0.12.3` — JWT signing/parsing
- `software.amazon.awssdk:s3` (AWS SDK v2, BOM 2.42.26) — S3 file URLs
- `org.apache.pdfbox:pdfbox 3.0.4` — PDF material generation
- `spring-boot-starter-websocket` — STOMP chat
- `spring-boot-starter-webflux` — `WebClient` for LLM calls (no reactive endpoints; MVC stack remains the default)

### System Architecture
```mermaid
graph TB
    subgraph Client["🖥️ Frontend (React)"]
        UI[UI Components]
        YT[YouTube IFrame API]
        ES[EventSource - SSE]
        WS_C[WebSocket Client - STOMP]
    end

    subgraph Server["⚙️ Backend (Spring Boot)"]
        SC[Spring Security + JWT Filter]
        REST[REST Controllers - 38 endpoints]
        SSE_S[SSE Emitters - 2 endpoints]
        WS_S[WebSocket/STOMP - 1 endpoint]
        
        subgraph Services["Service Layer"]
            AUTH[AuthService]
            ROOM[LearningRoomService]
            CURRI[CurriculumService]
            EVENT[EventDetectionService]
            FLIP[FlippedLearningService]
            QUIZ[QuizService]
            MATCH[MatchingEngineService]
            CHAT[ChatService]
            NOTI[NotificationService]
            LLM[LLMService - 공통 모듈]
        end
    end

    subgraph External["☁️ External Services"]
        LLM_API[LLM API - OpenAI/Gemini]
        PY[Python Script - yt-dlp 기본 + youtube-transcript-api 폴백]
    end

    subgraph Infra["🗄️ Infrastructure (AWS)"]
        RDS[(MySQL 8.0 - RDS)]
        REDIS[(Redis)]
        S3[(S3 - 파일 저장)]
        EC2[EC2 - 서버]
    end

    UI -->|HTTP/JSON| SC
    YT -->|행동 이벤트| REST
    ES -->|SSE Stream| SSE_S
    WS_C -->|STOMP| WS_S

    SC --> REST
    SC --> SSE_S
    SC --> WS_S

    REST --> Services
    SSE_S --> FLIP
    SSE_S --> NOTI
    WS_S --> CHAT

    LLM -->|HTTP| LLM_API
    CURRI -->|ProcessBuilder| PY

    Services -->|JPA| RDS
    EVENT -->|카운팅/쿨다운| REDIS
    AUTH -->|토큰 저장| REDIS
    MATCH -->|온라인 상태| REDIS
    ROOM -->|파일 URL| S3
```
### 핵심 비즈니스 로직

- **패턴 감지**: Redis 기반 카운팅. 키 `moai:events:{userId}:{videoId}:{eventType}` (TTL 10분), 쿨다운 `moai:cooldown:{userId}:{videoId}:{pattern}` (TTL 5분)
- **진척도**: 영상 시청 40% + 거꾸로 학습 30% + 파이널 퀴즈 30%. 학습실 전체 달성률 = 주차별 평균
- **매칭 엔진**: 약점 키워드 weakness_count >= 2 + 동일 키워드 strength 보유자 + created_at 7일 이내 + Redis 토큰 존재(온라인)
- **자막 스크래핑**: `SubtitleScraper` 인터페이스 추상화. `subtitle.provider` 프로퍼티로 두 구현체 토글:
  - **ytdlp** (기본/로컬) — `src/main/resources/scripts/scrape_subtitle.py` 를 ProcessBuilder 로 호출 (yt-dlp 기본 + youtube-transcript-api 폴백). `subtitle.script-path` / `python-bin` / `timeout-sec` / `preferred-langs` / `enable-fallback` / `cookies-path` / `concurrency-limit` 프로퍼티로 관리.
  - **supadata** (EC2/prod) — Supadata Transcript API (`GET https://api.supadata.ai/v1/transcript`, `x-api-key` 헤더, `mode=native` + `text=false` 고정). 202 비동기 응답은 jobId 폴링(최대 30초 — 15회 × 2초, 기존 yt-dlp 의 30초 타임아웃 정책과 일치). 폴링 한도는 @Async 스레드 풀 보호 목적. HTTP status 매핑: 200→정상, 202→폴링, 206→NO_SUBTITLES_AVAILABLE, 401/403→SUPADATA_AUTH_FAILED, 402→SUPADATA_QUOTA_EXCEEDED, 404→VIDEO_NOT_FOUND, 429→RATE_LIMITED, 5xx/네트워크→NETWORK_ERROR, 폴링 초과→SCRIPT_TIMEOUT.

  공통: 실패 시 `SubtitleScrapeException(SubtitleErrorCode)` 발생, 호출부 분기 정책은 구현체 무관하게 동일:
    - `NO_SUBTITLES_AVAILABLE` → 후보 풀 차순위 영상으로 재선정 1회 시도
    - `RATE_LIMITED` → `SubtitleRetryQueue` 에 등록 (60초 뒤 같은 영상 재시도)
    - 기타 (PRIVATE/AGE/REGION/NOT_FOUND/NETWORK/TIMEOUT/SUPADATA_*) → 자막 없이 다음 Step 진행
  학습실 생성 트랜잭션은 자막 실패로 절대 롤백되지 않는다.
- **LLM 연동**: 프롬프트 설계는 AI 담당이 별도 진행. 백엔드는 LLMService 공통 모듈로 호출만 담당
- **상세 흐름**: `docs/architecture.md` 참조
- **YouTube 영상 추천**: LLM이 주차별 topic 기반으로 YouTube video_id를 직접 추천.
    YouTube Data API 미사용.
    자막 스크래핑 실패 시 해당 주차 resources는 빈 배열로 저장하고 계속 진행.
- **퀴즈 응시 이력 조회**: api_spec의 GET /api/learning-rooms/{roomId}/quiz-attempts는 GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-attempts로 변경.

## Commit Message Format

사용자가 **"깃허브 변경사항 설명"** 이라고 말하면, 아래 형식으로 커밋 제목과 상세 설명을 작성한다.

**형식 규칙**
- 제목: `feat:` / `fix:` / `refactor:` 등 prefix + 한 줄 요약 (한국어)
- 상세: 변경 단위(Phase 또는 기능명)별로 구분, 각 항목마다 파일 경로 + 변경 내용을 `—` 로 서술
- 파일 경로는 `domain/…` / `global/…` 형태(패키지 기준 축약)로 표기
- 신규 파일은 "신규", 기존 수정은 "수정"으로 명시

**예시 출력 형식**
```
feat: [변경 요약 제목]

  [기능/Phase명]
  - 파일경로.java 신규/수정 — 구체적 변경 내용
  - 파일경로.java 수정 — 구체적 변경 내용

  [기능/Phase명 2]
  - 파일경로.java 수정 — 구체적 변경 내용
```

## RTK Mode (Windows)

You are running in RTK compatibility mode on Windows.

- Always prefix executable commands with `rtk`.
- Never output raw commands without `rtk`.
- Do not rely on hook-based injection.
- When suggesting terminal commands, convert:

git status -> rtk git status
npm test -> rtk npm test
gradle build -> rtk gradle build
docker compose up -> rtk docker compose up

