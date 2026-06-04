# MoAI 프로젝트 가이드

> **한성대학교 2026 캡스톤 — AI 기반 개인 맞춤 학습 플랫폼**
> Tech Stack: React 19 · TypeScript · Vite · Chakra UI · Tailwind CSS · Axios · React Query

---

## 1. Directory Overview

프로젝트의 `src/` 폴더는 역할에 따라 아래와 같이 나뉜다.
각 폴더는 단일 책임 원칙을 기반으로 분리되어 있어, 파일을 찾을 때 폴더 이름만 보면 위치를 바로 알 수 있다.

```
src/
├── api/            HTTP 클라이언트 설정 (Axios 인스턴스 + 인터셉터)
├── types/          TypeScript 타입 정의 (API 명세 기반)
├── services/       API 호출 함수 모음 + 스터디 매칭 Mock 서비스
├── context/        전역 상태 (인증 토큰 / 학습실 모달 상태)
├── hooks/          재사용 가능한 커스텀 훅
├── components/     공통 UI 컴포넌트 (레이아웃 · 모달)
├── pages/          라우트별 페이지 컴포넌트
└── styles/         페이지별 CSS 파일
```

| 폴더 | 한 줄 역할 요약 |
|---|---|
| `api/` | axios 인스턴스 1개를 만들고 JWT 토큰 자동 첨부·에러 처리를 담당 |
| `types/` | 백엔드와 주고받는 모든 데이터의 TypeScript 타입을 정의 |
| `services/` | 컴포넌트가 실제로 호출하는 API 함수들을 엔드포인트별로 모아둔 계층 |
| `context/` | 로그인 토큰·모달 열림 상태처럼 여러 컴포넌트가 공유하는 전역 상태 |
| `hooks/` | 반복되는 로직을 훅으로 추출해 컴포넌트를 단순하게 유지 |
| `components/` | 페이지 여러 곳에서 재사용되는 UI 조각 (레이아웃·모달 등) |
| `pages/` | React Router 가 렌더링하는 최상위 화면 컴포넌트 |
| `styles/` | 페이지·컴포넌트별 CSS 파일 (CSS 변수는 `index.css` 에 전역 정의) |

---

## 2. Key File Descriptions

### `src/api/`

| 파일 | 설명 |
|---|---|
| `axios.ts` | 모든 HTTP 요청의 출발점이야. Axios 인스턴스를 만들고, 요청마다 `Authorization: Bearer 토큰` 헤더를 자동으로 붙여주며, 401 오류가 오면 refreshToken 으로 토큰을 자동 재발급하고, 403·404·500 오류는 `MoaiApiError` 로 변환해서 컴포넌트에 전달해. 동시에 여러 요청이 401을 받았을 때 `isRefreshing` 플래그와 `pendingQueue`로 토큰 갱신을 한 번만 시도하는 경쟁 조건 처리도 포함되어 있어. |

---

### `src/types/`

| 파일 | 설명 |
|---|---|
| `api.ts` | 백엔드 API 명세서에 정의된 모든 엔드포인트의 요청·응답 데이터 규격을 TypeScript 인터페이스로 정의해. 인증·온보딩·학습실·행동 로그·거꾸로 학습·파이널 퀴즈·스터디 매칭·알림·마이페이지까지 전체 범위를 커버해. |
| `aiEvents.ts` | 학습실 화면에서만 쓰는 내부 전용 타입 파일이야. `MetaEvaluationResponse`(이해도 점수·강점/약점 키워드)와 `StudyMatchResponse`(파트너 매칭 정보)는 API 응답을 모달용으로 변환한 어댑터 타입이고, `ModalData`는 열린 모달마다 타입-안전하게 데이터를 전달하는 판별 유니온이야. |

---

### `src/services/`

| 파일 | 설명 |
|---|---|
| `apiService.ts` | 백엔드 API와 통신하는 모든 함수를 한 파일에 모아둔 창구야. 컴포넌트는 URL·HTTP 메서드를 몰라도 되고, 이 파일의 함수 이름만 호출하면 토큰 인증과 공통 응답 파싱(`unwrap`)이 자동으로 처리돼. |
| `aiSummaryService.ts` | 스터디 파트너 매칭 정보를 흉내내는 Mock 서비스야. `fetchStudyMatch()`만 남아 있으며, TODO 주석에 실제 API(`getStudySuggestions()`) 연결 방법이 적혀 있어. 그 외 개념 요약·패턴 분석 mock 함수는 실제 API 흐름으로 대체되어 제거됐어. |

---

### `src/context/`

| 파일 | 설명 |
|---|---|
| `AuthContext.tsx` | 로그인 후 발급받은 accessToken·refreshToken·userId·nickname을 앱 전체에서 공유하는 인증 상태 저장소야. `useAuth()` 훅으로 어디서든 로그인 여부를 확인하거나 토큰을 꺼낼 수 있어. |
| `ClassroomModalContext.tsx` | 학습실 화면에서 열리는 11가지 모달의 현재 상태(어떤 모달이 열려있는지, 어떤 데이터를 가지는지)를 전역으로 관리하는 컨텍스트야. `open(key, data)` 한 번으로 어느 컴포넌트에서든 특정 모달을 열 수 있어. |

---

### `src/hooks/`

| 파일 | 설명 |
|---|---|
| `useYouTubePlayer.ts` | YouTube IFrame API를 동적으로 로드하고, 1초 폴링으로 영상 재생 위치를 추적해서 되감기·건너뛰기·장시간 정지·탭 이탈 4가지 학습 패턴을 감지해. 패턴이 감지되면 `onPatternDetected` 콜백을 호출해 StudyClassroom 에 이벤트를 전달해. |
| `useDebugToast.ts` | 개발 모드 전용 Debug Toast 상태 관리 훅이야. `addToast(eventType)` → 파란색 "Sending" 토스트를 즉시 표시하고 id를 반환해. 이후 API 응답을 받으면 `resolveToast(id, aiTriggered)` → `aiTriggered: true`면 보라색 "AI Triggered", `false`면 흐린 파란색 "No Trigger"로 업데이트돼. 프로덕션에서는 완전히 비활성화돼. |

---

### `src/components/`

| 파일 | 설명 |
|---|---|
| `AppLayout.tsx` | 로그인 후 모든 페이지를 감싸는 레이아웃 래퍼야. 좌측 사이드바를 포함한 공통 틀을 제공하고, 안쪽의 `<Outlet />`에 각 페이지 내용이 들어가. |
| `Sidebar.tsx` | 좌측 네비게이션 바야. 홈·내 스터디·마이페이지 메뉴를 제공하고, 현재 경로에 따라 활성 메뉴를 강조 표시해. 접기/펼치기 토글 기능도 있어. |
| `OnboardingWizard.tsx` | 신규 사용자가 처음 로그인했을 때 나타나는 4단계 온보딩 마법사야. 목표·현재 수준·학습 계획을 입력하면 `createLearningRoom()` 을 호출해 학습실을 생성하고 AI 커리큘럼 생성 로딩 화면 후 학습실로 이동해. |

---

### `src/components/modals/`

학습실에서 AI가 특정 행동을 감지하거나 사용자가 버튼을 클릭할 때 화면 위에 뜨는 팝업 컴포넌트들이야.
모달은 역할에 따라 4개 하위 폴더로 구분되며, 모든 렌더링은 `common/ClassroomModals.tsx` 가 단일 진입점으로 통합 관리한다.

```
modals/
├── common/       진입점 라우터 · 베이스 컴포넌트 · 개발용 디버그 도구
├── monitoring/   AI 패턴 감지 후 뜨는 개입 모달 (되감기·건너뛰기 반응)
├── flipped/      거꾸로 학습(Flipped Learning) 세션 전체 흐름
└── quiz/         퀴즈 응시·결과·주간 파이널 퀴즈
```

#### `common/`

| 파일 | 설명 |
|---|---|
| `Modal.tsx` | 모든 모달의 기반이 되는 베이스 컴포넌트야. 배경 어두움 처리, 닫기 버튼, wide 옵션 등 공통 UI를 제공해. |
| `ClassroomModals.tsx` | 현재 열린 모달 키를 보고 알맞은 모달 컴포넌트를 렌더링하는 라우터 역할이야. 모달 간 전환 흐름(모니터링→요약 상세, 거꾸로 학습→메타인지→매칭)도 여기서 조율해. `StudyClassroom.tsx`가 직접 import하는 유일한 모달 진입점이야. |
| `DebugEventController.tsx` | 개발·테스트 환경에서만 보이는 디버그 도구야. 되감기·건너뛰기 등 AI 트리거 이벤트를 버튼 하나로 강제 발동시켜 모달 흐름을 테스트할 수 있어. |
| `DebugToast.tsx` | 개발 모드 전용 이벤트 로그 오버레이야. 학습 패턴이 감지될 때마다 화면 우측 상단에 반투명 토스트를 표시해. 파란색은 API 전송 중, 보라색은 AI 모달 트리거 성공, 흐린 파란색은 임계값 미달을 나타내. |

#### `monitoring/`

AI가 학습 패턴을 감지했을 때 사용자에게 개입하는 모달들이야.

| 파일 | 설명 |
|---|---|
| `MonitoringModal.tsx` | 영상 되감기·장시간 정지·탭 이탈이 감지됐을 때 "이 개념이 어려우신가요? 요약본을 볼까요?" 라고 제안하는 팝업이야. |
| `SummaryDetailModal.tsx` | "네, 요약본 볼래요" 클릭 시 나타나는 모달로, AI가 생성한 개념 요약 카드를 보여줘. 별도 API 호출 없이 `ModalData.monitoring.summaryItems`에 미리 저장된 데이터를 사용해. `SummaryItem` 타입도 이 파일에서 export해. |
| `FastTrackModal.tsx` | 평균보다 빠른 속도로 섹션을 완료했을 때 심화 학습을 권유하는 팝업이야. |

#### `flipped/`

거꾸로 학습(Flipped Learning) 세션의 전체 흐름을 담당하는 모달들이야.

| 파일 | 설명 |
|---|---|
| `FlippedModal.tsx` | 거꾸로 학습 세션을 시작하도록 유도하는 진입 모달이야. |
| `ReverseLearningModal.tsx` | 사용자가 배운 내용을 AI에게 설명하는 채팅 인터페이스야. AI의 역질문이 SSE 스트리밍으로 실시간 표시돼. 세션 종료 시 `onSessionEnd(EndFlippedResponse)` 콜백으로 평가 결과를 ClassroomModals에 전달해. |
| `MetaEvaluationModal.tsx` | 거꾸로 학습 세션이 끝난 후 AI가 분석한 이해도 점수, 강점·약점 키워드를 보여주는 결과 모달이야. |
| `StudyMatchingModal.tsx` | 메타인지 평가 후 AI가 찾아준 1:1 스터디 파트너(멘토/멘티)를 소개하고 매칭 수락 여부를 묻는 모달이야. |

#### `quiz/`

퀴즈 응시부터 결과 리포트까지 퀴즈 관련 모달 전체를 담당해.

| 파일 | 설명 |
|---|---|
| `QuizPassModal.tsx` | 돌발 4지선다 퀴즈 문제를 보여주고 답을 선택하게 하는 퀴즈 응시 모달이야. |
| `QuizCorrectModal.tsx` | 퀴즈 정답 시 나타나는 축하 모달이야. |
| `QuizIncorrectModal.tsx` | 퀴즈 오답 시 올바른 정답과 AI 해설을 보여주며, 관련 영상 구간으로 이동을 유도하는 모달이야. |
| `FinalQuizModal.tsx` | 주차 학습 완료 후 치르는 최종 퀴즈야. 5문제 순차 응시 → AI 분석 → Recharts 레이더 차트 결과 리포트까지 5단계 흐름을 담고 있어. |

---

### `src/pages/`

| 파일 | 설명 |
|---|---|
| `LoginPage.tsx` | 앱의 첫 화면이야. 아이디·비밀번호를 입력해 로그인하면 `AuthContext.saveAuth()`가 토큰을 저장하고 메인 화면으로 이동해. |
| `main/MainPage.tsx` | 로그인 후 진입하는 홈 대시보드야. 추천 학습실 목록을 보여주고, 온보딩 마법사를 통해 새 학습실을 만들 수 있어. |
| `my-studies/MyStudiesPage.tsx` | 내가 참여 중인 모든 학습실 카드를 그리드 형태로 보여주는 페이지야. ⚠️ 현재 하드코딩 목 데이터를 사용 중 — `getLearningRooms()` API 연결 시 `LearningRoomListItem` 타입으로 교체 필요. |
| `study/classroom/StudyClassroom.tsx` | 핵심 학습 화면이야. YouTube 영상 플레이어·문서·요약·퀴즈 탭과 우측 메타인지 패널로 구성되며, 모든 AI 이벤트 감지와 모달 흐름이 이 페이지에서 시작돼. |

---

### 루트 설정 파일

| 파일 | 설명 |
|---|---|
| `src/main.tsx` | React 앱의 진입점이야. `<AuthProvider>`로 앱 전체를 감싸서 어디서든 인증 상태에 접근할 수 있게 해. |
| `src/App.tsx` | React Router 라우팅 테이블이야. URL 경로와 페이지 컴포넌트를 매핑하고 공통 레이아웃을 적용해. |
| `src/index.css` | 전역 CSS 변수(보라색 팔레트·폰트)와 공통 애니메이션(`fadeIn`, `slideUp`, `pulse-slow`)을 정의해. |
| `.env` | `VITE_API_BASE_URL` 환경 변수를 설정하는 파일이야. `dev`와 `prod` 환경에서 서로 다른 백엔드 URL을 가리키도록 구성해. |

---

## 3. Learning Flow Summary

### 핵심 학습 흐름: User Action → Event Log → AI Trigger → Modal

```
사용자가 영상을 시청하면서 특정 행동을 함
          │
          ▼
┌─────────────────────────────────────────────────────────┐
│                 StudyClassroom.tsx                       │
│  useYouTubePlayer 훅이 다음 행동을 포착:                 │
│    - 같은 구간 되감기 (threshold 5초)  → video_rewind   │
│    - 3분 이상 일시정지                 → video_pause    │
│    - 탭 이탈                          → tab_departure  │
│    - 30초 이상 건너뛰기               → video_skip     │
│                                                          │
│  → addToast(eventType)  ← 파란색 "Sending" 토스트 표시  │
└─────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────┐
│   sendEventLog(roomId, { event_type, curriculum_id,     │
│                           payload })   (apiService.ts)  │
│                                                          │
│   POST /api/learning-rooms/{roomId}/events              │
│   백엔드 Redis에서 패턴 판단 후 응답 반환               │
└─────────────────────────────────────────────────────────┘
          │
          ├── aiTriggered: false
          │     → resolveToast(id, false)  흐린 파란색 "No Trigger"
          │     → 아무것도 하지 않음 (임계값 미달)
          │
          └── aiTriggered: true
                → resolveToast(id, true)  보라색 "AI Triggered"
                    │
                    ├── video_rewind / video_pause / tab_departure
                    │         │
                    │         ▼
                    │   getMaterialDetail(roomId, materialId)
                    │   → MaterialDetail { title, summaryItems: [{label,title,desc}] }
                    │         │
                    │         ▼
                    │   summaryItems 필드명 변환
                    │   { label→letter, desc→description }
                    │         │
                    │         ▼
                    │   open('monitoring', { conceptName, reason, summaryItems })
                    │   → MonitoringModal 팝업
                    │         │
                    │         └── "네, 요약본 볼래요" 클릭
                    │                   │
                    │                   ▼ (추가 API 호출 없음)
                    │             SummaryDetailModal
                    │             (저장된 summaryItems 즉시 표시)
                    │
                    └── video_skip
                              │
                              ▼
                        getInstantQuiz(roomId, weekId)
                        → 돌발 4지선다 퀴즈 1문제 수신
                              │
                              ▼
                        open('quiz-pass')
                        → QuizPassModal (60초 제한 타이머)
```

---

### 거꾸로 학습(Flipped Learning) 흐름

```
"AI에게 거꾸로 설명하기" 버튼 클릭
          │
          ▼
① startFlippedSession(roomId, { curriculum_id: weekId })
   POST /api/learning-rooms/{roomId}/flipped/start
   → { sessionId, firstMessage } 수신

② ReverseLearningModal 오픈
   → firstMessage를 채팅 UI 첫 메시지로 표시

③ 사용자 설명 입력 후 전송
   → streamFlipped(roomId, sessionId, message)  [SSE 스트리밍]
   → GET /api/learning-rooms/{roomId}/flipped/stream?...
   → AI 응답이 토큰 단위로 실시간 출력
   → type: 'counter_question' → 역질문으로 강조 표시
   → type: 'done' → EventSource 닫기

④ "설명 완료하고 최종 평가받기" 클릭
   → endFlippedSession(roomId, { sessionId })
   POST /api/learning-rooms/{roomId}/flipped/end
   → { score, gainedKeywords, weakKeywords, feedback } 수신

⑤ handleSessionEnd(EndFlippedResponse)
   → 필드 매핑: score→comprehensionScore, gainedKeywords→strongKeywords
   → open('meta-evaluation', { evaluation: MetaEvaluationResponse })
   → MetaEvaluationModal (이해도 점수, 강점/약점 키워드)

⑥ "맞춤형 파트너 찾기" 클릭
   → fetchStudyMatch(evaluation)  [현재 Mock → TODO: getStudySuggestions()]
   → open('study-matching', { match: StudyMatchResponse })
   → StudyMatchingModal (파트너 프로필, 매칭률 표시)

⑦ "파트너 연결" 클릭
   → acceptStudySuggestion(suggestionId)
   POST /api/study-groups/suggestions/{id}/accept
   → setMetacogComplete(true), setPartnerConnected(true)
```

---

### 파이널 퀴즈 흐름

```
"퀴즈 도전하기" 버튼 클릭 (메타인지 완료 후 잠금 해제)
          │
          ▼
① getFinalQuiz(roomId, weekId)
   GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final
   → { quizId, title, questions: FinalQuizQuestion[] } 수신 (5문제)

② FinalQuizModal 내부: 단계별 응시 (1/5 → 2/5 → ... → 5/5)
   각 문항은 서술형(essay), maxLength 및 tip 포함

③ submitFinalQuiz(roomId, weekId, { quizId, answers })
   POST /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final/submit
   → 202 Accepted: { reportId, status: "analyzing", estimatedSec } 수신

④ 로딩 화면 표시 + 폴링 시작
   getQuizReport(roomId, weekId, reportId)
   GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-reports/{reportId}
   estimatedSec 간격으로 반복 호출

⑤ status: "completed" 수신
   → 결과 화면: finalScore, Recharts RadarChart(radarData), 문항별 AI 해설
```

---

### 모달 전환 맵

```
MonitoringModal ──(summaryItems 즉시 전달)──→ SummaryDetailModal

FlippedModal ──→ ReverseLearningModal ──→ MetaEvaluationModal ──→ StudyMatchingModal

FastTrackModal ──→ QuizPassModal ──→ QuizCorrectModal
                                 └──→ QuizIncorrectModal

FinalQuizModal (독립 진입점 — "퀴즈 도전하기" 버튼)
  loading-questions → answering (1~5) → submitting → analyzing → report
```

---

## 5. API 연결 체크리스트

백엔드 개발 완료 후 실제 API로 전환할 때 확인할 항목들이야.

- [ ] `.env` 파일에 `VITE_API_BASE_URL=https://api.moai.app/v1` 설정
- [ ] `LoginPage.tsx` 에서 `login()` 호출 후 `saveAuth(result)` 연결 확인
- [ ] `StudyClassroom.tsx` 에서 영상 이벤트 → `sendEventLog()` 연결 (`evxentType` 오타 수정 완료)
- [ ] `ReverseLearningModal.tsx` 에서 `streamFlipped()` EventSource 연결 + `.close()` 정리
- [ ] `App.tsx` 또는 최상위 컴포넌트에서 `connectNotificationStream()` 연결
- [ ] `aiSummaryService.ts`의 `fetchStudyMatch` → `getStudySuggestions()` + `acceptStudySuggestion()` 교체
- [ ] `MyStudiesPage.tsx` 로컬 `Study` 인터페이스 → `getLearningRooms()` + `LearningRoomListItem` 타입으로 교체
- [ ] SSE 엔드포인트 2개(`/flipped/stream`, `/notifications/stream`)에서 백엔드가 `?token=` 쿼리 파라미터 인증 지원 확인
- [ ] WebSocket(`/ws/study-groups/{groupId}`)에서 백엔드가 `?token=` 쿼리 파라미터 인증 지원 확인
- [ ] `FinalQuizModal.tsx` — 주관식(essay) 답변 제출 후 폴링 간격이 `estimatedSec`과 맞는지 확인
