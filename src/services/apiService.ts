/**
 * ============================================================================
 * src/services/apiService.ts  —  MoAI API 서비스 레이어
 * ============================================================================
 *
 * 역할:
 *   이 파일은 컴포넌트와 axios HTTP 클라이언트 사이의 중간 계층이다.
 *   컴포넌트는 이 파일의 함수만 호출하면 되며, HTTP 메서드·URL·응답 파싱은
 *   이 파일이 모두 처리한다.
 *
 * 공통 응답 형식 처리 (스펙 §공통 응답 형식):
 *   백엔드는 모든 응답을 다음 envelope 로 감싸서 내려준다:
 *   {
 *     "success":   true | false,
 *     "data":      { ... },       ← 실제 데이터
 *     "message":   "OK",
 *     "timestamp": "2025-03-01T09:00:00Z"
 *   }
 *   아래 unwrap() 헬퍼가 이 envelope 를 벗겨내고 data 만 반환한다.
 *   success === false 이면 message 를 포함한 에러를 throw 한다.
 *
 * 에러 처리:
 *   HTTP 4xx/5xx 는 axios.ts 의 응답 인터셉터가 MoaiApiError 로 변환한다.
 *   컴포넌트에서 try/catch 로 MoaiApiError 를 잡아 status 코드별 UI 처리 가능.
 * ============================================================================
 */

import api from '../api/axios'
import type { ApiResponse } from '../types/api'
import type {
  // ── 인증 (Auth) ──
  RegisterRequest, RegisterResponse,
  LoginRequest, LoginResponse,
  LogoutRequest,
  RefreshTokenRequest, RefreshTokenResponse,
  // ── 홈 / 사용자 (Home / Users) ──
  UserProfileBasic,
  UserProfileFull,
  UpdateProfileRequest, UpdateProfileResponse,
  UpdateKeywordsRequest, UpdateKeywordsResponse,
  DeleteAccountRequest,
  LearningHistoryItem,
  // ── 온보딩 (Onboarding) ──
  OnboardingKeywordsResponse,
  CreateLearningRoomRequest, CreateLearningRoomResponse,
  // ── 학습실 (Learning Room) ──
  LearningRoomListItem,
  CurriculumWeekSummary,
  CurriculumWeekDetail,
  UpdateProgressRequest, UpdateProgressResponse,
  RecommendedVideo,
  RecommendedVideosData,
  MaterialListItem,
  MaterialDetail,
  QuizAttemptListItem,
  QuizAttemptDetail,
  // ── 행동 로그 (Learning Event Logs) ──
  LearningEventRequest, LearningEventResponse,
  InstantQuizResponse,
  SubmitQuizRequest, SubmitQuizResponse,
  // ── 거꾸로 학습 (Flipped Learning) ──
  StartFlippedRequest, StartFlippedResponse,
  EndFlippedRequest, EndFlippedResponse,
  FlippedResultResponse,
  // ── 키워드 (Keywords) ──
  UserKeywordsResponse,
  // ── 파이널 퀴즈 (Final Quiz) ──
  FinalQuizResponse,
  SubmitFinalQuizRequest, SubmitFinalQuizResponse,
  QuizReportResponse,
  // ── 스터디 매칭 (Study Matching) ──
  StudySuggestion,
  AcceptSuggestionResponse, RejectSuggestionResponse,
  StudyGroupDetail,
  ChatMessage,
  // ── 알림 (Notifications) ──
  NotificationItem, MarkReadResponse,
} from '../types/api'

// ─────────────────────────────────────────────────────────────────────────────
// 공통 응답 Envelope 언래퍼 (unwrap helper)
// ─────────────────────────────────────────────────────────────────────────────
/**
 * unwrap<T>()
 *
 * 백엔드의 모든 응답은 { success, data, message, timestamp } 형태의 envelope 로
 * 감싸져 있다. 이 헬퍼는 envelope 를 벗겨내고 실제 data 만 반환한다.
 *
 * 처리 흐름:
 *   axios 호출 Promise
 *     → HTTP 200 수신 (axios 응답 인터셉터 통과)
 *     → envelope.success 확인
 *         true  → envelope.data 반환 (T 타입)
 *         false → envelope.message 를 포함한 Error throw
 *                 (백엔드가 HTTP 200 으로 내려왔지만 비즈니스 로직 실패인 경우)
 *
 * 타입 파라미터 T 는 각 API 호출 지점에서 자동 추론되므로 직접 지정할 필요 없음.
 *
 * @param promise - api.get / api.post 등이 반환하는 Promise
 * @returns       - envelope.data (T 타입)
 */
async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  // axios 는 응답을 { data: <실제 JSON 바디> } 구조로 감싸서 반환한다.
  // 여기서 data 가 바로 백엔드의 envelope ({ success, data, message, timestamp }) 이다.
  const { data: envelope } = await promise

  // success === false 는 HTTP 상태는 200 이지만 비즈니스 로직이 실패한 경우
  // (현재 스펙에서는 드문 케이스지만, 향후 확장을 위해 처리)
  if (!envelope.success) {
    throw new Error(envelope.message ?? 'API 요청이 실패했습니다.')
  }

  // envelope.data 가 바로 컴포넌트에서 사용하는 실제 데이터
  return envelope.data
}

// =============================================================================
// 1. 인증 (Auth)  — POST /api/auth/*
// =============================================================================
/**
 * ── 인증 토큰 생명주기 개요 ──
 *
 * 로그인 성공 시 백엔드는 두 종류의 토큰을 발급한다:
 *
 *  ┌─────────────────────────────────────────────────────────────────────────┐
 *  │ accessToken  │ 실제 API 요청에 사용. 짧은 유효기간 (3600초 = 1시간).    │
 *  │              │ Authorization: Bearer {accessToken} 헤더로 전달.          │
 *  │              │ 만료 시 axios 인터셉터가 자동으로 refreshToken 을 사용해  │
 *  │              │ 재발급 요청을 보내고, 원본 요청을 재시도한다.             │
 *  ├─────────────────────────────────────────────────────────────────────────┤
 *  │ refreshToken │ accessToken 재발급 전용. 상대적으로 긴 유효기간.          │
 *  │              │ POST /api/auth/refresh 에서만 사용.                        │
 *  │              │ 만료되면 재로그인이 필요하다.                             │
 *  │              │ 백엔드 Redis 에 저장됨 (서버 측에서도 무효화 가능).        │
 *  └─────────────────────────────────────────────────────────────────────────┘
 *
 * 저장 위치: localStorage (AuthContext.tsx 의 saveAuth 가 관리)
 *   - accessToken  → 'accessToken'  키
 *   - refreshToken → 'refreshToken' 키
 *   - userId       → 'userId'       키
 *   - nickname     → 'nickname'     키
 *
 * 토큰 흐름 다이어그램:
 *
 *   로그인 → saveAuth() → localStorage 저장
 *                              ↓
 *   API 요청 → [요청 인터셉터] → Authorization: Bearer {accessToken} 헤더 첨부
 *                              ↓
 *   응답 수신 → HTTP 200 → 정상 처리
 *           → HTTP 401 → [응답 인터셉터] → refreshToken 으로 재발급 시도
 *                                              ↓ 성공
 *                                         새 accessToken 저장 → 원본 요청 재시도
 *                                              ↓ 실패
 *                                         clearAuth() → 로그인 이동
 */

/**
 * register  —  POST /api/auth/register  (공개)
 *
 * 회원가입. 이메일 직접 가입 방식.
 * 성공 시 userId 와 환영 메시지를 반환 (토큰은 발급되지 않음 → 별도 로그인 필요).
 */
export async function register(body: RegisterRequest): Promise<RegisterResponse> {
  // 백엔드 Spring Security 공개 엔드포인트. 회원가입 후 토큰 미발급 → 별도 로그인 필요.
  // 공개 엔드포인트이므로 Authorization 헤더 없이 호출됨
  // (localStorage 에 accessToken 이 없으면 인터셉터가 헤더를 붙이지 않음)
  return unwrap(api.post<ApiResponse<RegisterResponse>>('/api/auth/register', body))
}

/**
 * login  —  POST /api/auth/login  (공개)
 *
 * 로그인 후 반드시 AuthContext.saveAuth(result) 를 호출해야 한다.
 * saveAuth 가 localStorage 에 토큰을 저장해야 이후 요청에서 인터셉터가 동작한다.
 *
 * 사용 예시 (LoginPage.tsx):
 *   const { saveAuth } = useAuth()
 *   const result = await login({ login_id, password })
 *   saveAuth(result)          // ← 이 호출로 토큰이 저장됨
 *   navigate('/main')
 */
export async function login(body: LoginRequest): Promise<LoginResponse> {
  // 백엔드 Spring Security + JWT 필터 연동. 응답 토큰을 AuthContext.saveAuth()로 저장.
  return unwrap(api.post<ApiResponse<LoginResponse>>('/api/auth/login', body))
}

/**
 * logout  —  POST /api/auth/logout  (JWT 필요)
 *
 * 서버 측에서 Redis 의 refreshToken 을 무효화한다.
 * 호출 후 반드시 AuthContext.clearAuth() 를 호출해 클라이언트 측도 정리해야 한다.
 *
 * 사용 예시:
 *   const { clearAuth, refreshToken } = useAuth()
 *   await logout({ refreshToken })   // 서버 측 토큰 무효화
 *   clearAuth()                       // 클라이언트 측 토큰 삭제
 *   navigate('/')
 */
export async function logout(body: LogoutRequest): Promise<void> {
  // 서버 Redis에서 refreshToken 무효화. 호출 후 clearAuth()로 클라이언트 토큰도 삭제.
  // 반환값 없음. 서버가 { success: true, message: "로그아웃되었습니다." } 를 반환하지만
  // 프론트엔드에서는 성공 여부만 확인하면 된다.
  await api.post('/api/auth/logout', body)
}

/**
 * refreshToken  —  POST /api/auth/refresh  (공개)
 *
 * accessToken 을 수동으로 갱신하는 함수.
 * 일반적으로 이 함수를 직접 호출할 필요는 없다.
 * axios 응답 인터셉터 (axios.ts) 가 401 수신 시 자동으로 처리하기 때문이다.
 *
 * 직접 사용하는 경우:
 *   - 앱 시작 시 accessToken 이 곧 만료될 것을 미리 알고 선제적으로 갱신하는 경우
 *   - 백그라운드 토큰 갱신 스케줄러를 구현하는 경우
 *
 * 응답: { accessToken: 새 토큰, expiresIn: 3600 }
 */
export async function refreshAccessToken(body: RefreshTokenRequest): Promise<RefreshTokenResponse> {
  // axios 인터셉터가 401 수신 시 자동 호출. 직접 사용 거의 없음.
  return unwrap(api.post<ApiResponse<RefreshTokenResponse>>('/api/auth/refresh', body))
}

// =============================================================================
// 2. 홈 (Home)  — GET /api/users/me
// =============================================================================

/**
 * getHomeProfile  —  GET /api/users/me  (JWT 필요)
 *
 * 홈 화면 진입 시 호출. 가벼운 프로필 정보만 반환.
 * (userId, nickname, profileImageUrl)
 *
 * ⚠️  마이페이지의 getMyProfile 과는 다른 함수다.
 *   - getHomeProfile: 홈 화면용, 3개 필드만 반환
 *   - getMyProfile:   마이페이지용, email·birthDate 등 전체 프로필 반환
 *   두 함수 모두 GET /api/users/me 를 호출하지만 백엔드가 컨텍스트에 따라
 *   다른 필드를 반환한다.
 */
export async function getHomeProfile(): Promise<UserProfileBasic> {
  // 홈 화면 진입 시 호출. userId·nickname·profileImageUrl 3개 필드만 반환.
  return unwrap(api.get<ApiResponse<UserProfileBasic>>('/api/users/me'))
}

// =============================================================================
// 3. 온보딩 (Onboarding)  — GET /api/onboarding/keywords, POST /api/learning-rooms
// =============================================================================

/**
 * getOnboardingKeywords  —  GET /api/onboarding/keywords  (JWT 필요)
 *
 * 온보딩 Step 1 에서 추천 키워드 태그 목록을 가져온다.
 * 사용자가 키워드를 선택하면 학습실 생성 요청 body 의 subject 에 사용된다.
 *
 * 응답 예시: { keywords: ["정보처리기사", "컴공", "웹개발", "CS면접", ...] }
 */
export async function getOnboardingKeywords(): Promise<OnboardingKeywordsResponse> {
  // 온보딩 Step 1 추천 키워드 태그 목록 조회. 선택된 키워드가 학습실 생성 요청 body에 사용됨.
  return unwrap(api.get<ApiResponse<OnboardingKeywordsResponse>>('/api/onboarding/keywords'))
}

/**
 * createLearningRoom  —  POST /api/learning-rooms  (JWT 필요)
 *
 * 온보딩 Step 4 "이대로 학습실 개설하기" 클릭 시 호출.
 * 백엔드 AI 파이프라인이 자동 실행된다:
 *   LLM 커리큘럼 생성 → YouTube 영상 추천 → 자막 스크래핑 → 키워드 추출
 *
 * 응답에는 생성된 roomId 와 주차별 커리큘럼 요약이 포함된다.
 * roomId 를 저장해 두어야 이후 모든 학습실 API 에서 사용할 수 있다.
 */
export async function createLearningRoom(body: CreateLearningRoomRequest): Promise<CreateLearningRoomResponse> {
  // AI 커리큘럼 생성 파이프라인 트리거. 응답 roomId를 이후 모든 학습실 API에서 사용.
  return unwrap(api.post<ApiResponse<CreateLearningRoomResponse>>('/api/learning-rooms', body))
}

// =============================================================================
// 4. 학습실 (Learning Room)  — /api/learning-rooms/*
// =============================================================================

/**
 * getLearningRooms  —  GET /api/learning-rooms  (JWT 필요)
 *
 * "내 스터디" 메뉴(MyStudiesPage) 에서 내 학습실 목록을 가져온다.
 * completionRate, status 를 기반으로 카드 UI 를 렌더링한다.
 */
export async function getLearningRooms(): Promise<LearningRoomListItem[]> {
  const raw = await unwrap(api.get<ApiResponse<unknown>>('/api/learning-rooms'))
  console.log('getLearningRooms :', JSON.stringify(raw, null, 2))
  // Handle flat array or wrapped object (e.g. { rooms:[...] } / { items:[...] })
  if (Array.isArray(raw)) return raw as LearningRoomListItem[]
  if (raw && typeof raw === 'object') {
    const obj = raw as Record<string, unknown>
    for (const key of ['rooms', 'items', 'learningRooms', 'content', 'data']) {
      if (Array.isArray(obj[key])) return obj[key] as LearningRoomListItem[]
    }
  }
  return []
}

/**
 * getCurriculum  —  GET /api/learning-rooms/{roomId}/curriculum  (JWT 필요)
 *
 * 학습실 화면 상단의 "Week 드롭다운" 메뉴를 채울 때 사용.
 * 전체 주차 목록과 각 주차의 completionRate 를 반환한다.
 *
 * @param roomId - 학습실 고유 ID (예: "r1000000-0000-0000-0000-000000000001")
 */
export async function getCurriculum(roomId: string): Promise<CurriculumWeekSummary[]> {
  const raw = await unwrap(api.get<ApiResponse<unknown>>(`/api/learning-rooms/${roomId}/curriculum`))
  console.log('[getCurriculum] raw response:', JSON.stringify(raw, null, 2))
  if (Array.isArray(raw)) return raw as CurriculumWeekSummary[]
  if (raw && typeof raw === 'object') {
    const obj = raw as Record<string, unknown>
    for (const key of ['weeklyCurriculums', 'curriculums', 'weeks', 'curriculum', 'content', 'items']) {
      if (Array.isArray(obj[key])) return obj[key] as CurriculumWeekSummary[]
    }
  }
  return []
}

/**
 * getCurriculumWeek  —  GET /api/learning-rooms/{roomId}/curriculum/{weekId}  (JWT 필요)
 *
 * 학습실 화면 진입 시 가장 먼저 호출하는 핵심 API.
 * topic, description, completionRate, keywords, resources, mainVideoId 를 반환한다.
 *
 * keywords 는 거꾸로 학습(Flipped Learning) 세션 시작 메시지 생성에 사용된다.
 * (예: "이번 주차에서 배운 '원자성, 일관성, 고립성, 지속성'에 대해 설명해주세요!")
 *
 * @param roomId - 학습실 ID
 * @param weekId - 주차 ID (예: "w3000000-0000-0000-0000-000000000002")
 */
export async function getCurriculumWeek(roomId: string, weekId: string): Promise<CurriculumWeekDetail> {
  const raw = await unwrap(
    api.get<ApiResponse<unknown>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}`),
  )
  console.log('[getCurriculumWeek] raw response:', JSON.stringify(raw, null, 2))
  if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
    const obj = raw as Record<string, unknown>
    // If the object itself looks like CurriculumWeekDetail, return it directly
    if ('weekId' in obj || 'weekNumber' in obj) return obj as unknown as CurriculumWeekDetail
    // Otherwise try common server-side wrapper keys
    for (const key of ['week', 'weekDetail', 'curriculum', 'data']) {
      if (obj[key] && typeof obj[key] === 'object' && !Array.isArray(obj[key])) {
        return obj[key] as CurriculumWeekDetail
      }
    }
  }
  return raw as CurriculumWeekDetail
}

/**
 * updateProgress  —  PATCH /api/learning-rooms/{roomId}/curriculum/{weekId}/progress  (JWT 필요)
 *
 * 영상 재생 진행도 또는 학습 완료 시 호출하여 completionRate 를 업데이트한다.
 * 학습실 상단의 COURSE PROGRESS % 바와 연동된다.
 *
 * 호출 시점 예시:
 *   - 영상 재생 25%, 50%, 75%, 100% 도달 시
 *   - 퀴즈 완료 시
 *
 * @param roomId               - 학습실 고유 ID (예: "r1000000-0000-0000-0000-000000000001")
 * @param weekId               - 주차 ID (예: "w3000000-0000-0000-0000-000000000002")
 * @param body                 - PATCH 요청 바디 (UpdateProgressRequest)
 * @param body.completionRate  - 새 진척도 (0.0 ~ 100.0)
 */
export async function updateProgress(
  roomId: string,
  weekId: string,
  body: UpdateProgressRequest,
): Promise<UpdateProgressResponse> {
  // 영상 재생 진행도 업데이트. 학습실 상단 COURSE PROGRESS 바와 연동됨.
  return unwrap(
    api.patch<ApiResponse<UpdateProgressResponse>>(
      `/api/learning-rooms/${roomId}/curriculum/${weekId}/progress`,
      body,
    ),
  )
}

/**
 * getRecommendedVideos  —  GET /api/learning-rooms/{roomId}/curriculum/{weekId}/recommended-videos  (JWT 필요)
 *
 * 학습실 "영상" 탭을 클릭할 때 호출.
 * AI 가 커리큘럼 생성 시 추천한 YouTube 영상 목록을 반환한다.
 * 응답 data 는 { videos: RecommendedVideo[] } 형태이며, videos 배열만 반환한다.
 * durationSec 을 분:초 형식으로 변환해 UI 에 표시한다.
 */
export async function getRecommendedVideos(roomId: string, weekId: string): Promise<RecommendedVideo[]> {
  const raw = await unwrap(
    api.get<ApiResponse<unknown>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}/recommended-videos`),
  )
  console.log('[getRecommendedVideos] raw response:', JSON.stringify(raw, null, 2))
  if (Array.isArray(raw)) return raw as RecommendedVideo[]
  if (raw && typeof raw === 'object') {
    const obj = raw as Record<string, unknown>
    for (const key of ['videos', 'recommendedVideos', 'items', 'content']) {
      if (Array.isArray(obj[key])) return obj[key] as RecommendedVideo[]
    }
  }
  return []
}

/**
 * getMaterials  —  GET /api/learning-rooms/{roomId}/materials  (JWT 필요)
 *
 * 학습실 "요약" 탭을 클릭할 때 호출.
 * 패턴1(되감기 3회) 또는 패턴2(장시간 일시정지) 감지 시 AI 가 자동 생성한 요약 자료 목록.
 * triggerKeywords 를 참고해 어떤 개념에서 생성된 자료인지 파악할 수 있다.
 */
export async function getMaterials(roomId: string): Promise<MaterialListItem[]> {
  // AI 자동 생성 요약 자료 목록 조회. 학습실 "요약" 탭 클릭 시 호출.
  return unwrap(api.get<ApiResponse<MaterialListItem[]>>(`/api/learning-rooms/${roomId}/materials`))
}

/**
 * getMaterialDetail  —  GET /api/learning-rooms/{roomId}/materials/{materialId}  (JWT 필요)
 *
 * 패턴1 감지 후 "네, 요약본 볼래요" 버튼 클릭 시 호출.
 * summaryItems 배열을 받아 SummaryDetailModal 에 전달한다.
 *
 * ⚠️  필드명 매핑 주의:
 *   API 응답: { label: "A", title: "...", desc: "..." }
 *   모달 타입: { letter: "A", title: "...", description: "..." }
 *   → label → letter, desc → description 으로 변환 후 모달에 전달해야 함
 *
 * @param roomId     - 학습실 고유 ID
 * @param materialId - 자료 ID (sendEventLog 응답의 aiTriggered: true 시 반환됨)
 */
export async function getMaterialDetail(roomId: string, materialId: string): Promise<MaterialDetail> {
  // 패턴 감지 후 요약 모달에 표시할 개념 카드 조회. 응답 label→letter, desc→description 필드 변환 필요.
  return unwrap(
    api.get<ApiResponse<MaterialDetail>>(`/api/learning-rooms/${roomId}/materials/${materialId}`),
  )
}

/**
 * getQuizAttempts  —  GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-attempts  (JWT 필요)
 *
 * 학습실 "퀴즈 내역" 탭을 클릭할 때 호출.
 * weekId 를 필수로 받아 해당 주차의 퀴즈 내역만 조회한다.
 * attemptedAt 은 ISO 8601 형식이므로 프론트에서 "1분 전", "1시간 전" 등으로 변환한다.
 */
export async function getQuizAttempts(roomId: string, weekId: string): Promise<QuizAttemptListItem[]> {
  const raw = await unwrap(
    api.get<ApiResponse<unknown>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}/quiz-attempts`),
  )
  console.log('[getQuizAttempts] raw response:', JSON.stringify(raw, null, 2))
  if (Array.isArray(raw)) return raw as QuizAttemptListItem[]
  if (raw && typeof raw === 'object') {
    const obj = raw as Record<string, unknown>
    for (const key of ['quizAttempts', 'attempts', 'items', 'content']) {
      if (Array.isArray(obj[key])) return obj[key] as QuizAttemptListItem[]
    }
  }
  return []
}

/**
 * getQuizAttemptDetail  —  GET /api/quiz-attempts/{attemptId}  (JWT 필요)
 *
 * 퀴즈 내역에서 "상세 보기" 클릭 시 호출.
 * AI 해설(aiExplanation)이 포함되며, 오답인 경우 relatedVideoId + relatedTimestamp 로
 * 해당 영상 구간으로 이동 유도 기능을 구현할 수 있다.
 */
export async function getQuizAttemptDetail(attemptId: string): Promise<QuizAttemptDetail> {
  // 퀴즈 내역 "상세 보기" 클릭 시 호출. AI 해설(aiExplanation) 및 오답 시 영상 구간 정보 포함.
  return unwrap(api.get<ApiResponse<QuizAttemptDetail>>(`/api/quiz-attempts/${attemptId}`))
}

// =============================================================================
// 5. 행동 로그 (Learning Event Logs)  — POST /api/learning-rooms/{roomId}/events
// =============================================================================
/**
 * ── 행동 이벤트 → AI 트리거 처리 흐름 개요 ──
 *
 * 사용자가 영상을 시청하는 동안 특정 행동(되감기, 일시정지, 건너뛰기)을 감지하면
 * sendEventLog 를 호출한다. 백엔드는 Redis 에서 패턴을 판단하고 aiTriggered 를 반환한다.
 *
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │ 이벤트 타입     │ 트리거 조건              │ aiTriggered: true 시 동작       │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ video_rewind    │ 같은 구간 되감기 3회↑    │ materialId 포함 →               │
 * │                 │                          │ getMaterialDetail → 요약 모달   │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ video_pause     │ 3분↑ 장시간 일시정지     │ materialId 포함 →               │
 * │ tab_departure   │ 탭 이탈 3회↑             │ getMaterialDetail → 요약 모달   │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ video_skip      │ 구간 건너뛰기 감지       │ materialId 없음 →               │
 * │                 │                          │ getInstantQuiz → 돌발 퀴즈 모달 │
 * └─────────────────────────────────────────────────────────────────────────────┘
 */

/**
 * sendEventLog  —  POST /api/learning-rooms/{roomId}/events  (JWT 필요)
 *
 * 모든 패턴 이벤트를 백엔드에 전송하고, AI 트리거 여부를 확인한다.
 *
 * 반환 타입은 discriminated union 이다:
 *   { aiTriggered: false }
 *   → 패턴 미발동. 아무 UI 변화 없음.
 *
 *   { aiTriggered: true, eventType, extractedKeywords?, materialId? }
 *   → 패턴 발동. eventType 에 따라 아래 분기 처리를 수행한다.
 *
 * 사용 예시 (StudyClassroom.tsx 에서):
 *
 *   // 영상 되감기 발생 시
 *   const result = await sendEventLog(roomId, {
 *     event_type:    'video_rewind',
 *     curriculum_id: weekId,
 *     payload: { video_id: 'dml_week1', rewind_target_sec: 268 },
 *   })
 *
 *   if (result.aiTriggered) {
 *     if (result.eventType === 'video_rewind' || result.eventType === 'video_pause'
 *         || result.eventType === 'tab_departure') {
 *       // materialId 가 함께 반환됨 → 요약 자료 상세 조회
 *       const material = await getMaterialDetail(roomId, result.materialId!)
 *       open('monitoring', { conceptName: material.title, summaryItems: ... })
 *     }
 *     if (result.eventType === 'video_skip') {
 *       // materialId 없음 → 돌발 퀴즈 조회
 *       const quiz = await getInstantQuiz(roomId, weekId)
 *       open('quiz-pass', { quiz })
 *     }
 *   }
 */
export async function sendEventLog(
  roomId: string,
  body: LearningEventRequest,
): Promise<LearningEventResponse> {
  // 사용자 행동 감지 시 호출. aiTriggered 응답에 따라 요약 모달 또는 돌발 퀴즈 모달 팝업 제어.
  return unwrap(
    api.post<ApiResponse<LearningEventResponse>>(`/api/learning-rooms/${roomId}/events`, body),
  )
}

/**
 * getInstantQuiz  —  GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/instant  (JWT 필요)
 *
 * 패턴3(video_skip) 발동 후 호출. 해당 주차 키워드 기반의 돌발 4지선다 퀴즈 1문제를 반환.
 * timeLimitSec (기본 60초) 을 UI 카운트다운 타이머와 연동한다.
 */
export async function getInstantQuiz(roomId: string, weekId: string): Promise<InstantQuizResponse> {
  // 건너뛰기(video_skip) 패턴 발동 후 호출. 해당 주차 키워드 기반 돌발 4지선다 퀴즈 1문제 반환.
  return unwrap(
    api.get<ApiResponse<InstantQuizResponse>>(
      `/api/learning-rooms/${roomId}/curriculum/${weekId}/quizzes/instant`,
    ),
  )
}

/**
 * submitQuiz  —  POST /api/quiz-attempts  (JWT 필요)
 *
 * 돌발 퀴즈와 파이널 퀴즈 모두 이 엔드포인트를 공유한다.
 * 백엔드가 정오답을 판별하고 AI 해설을 생성하여 반환한다.
 *
 * 응답의 isCorrect 에 따라:
 *   true  → quiz-correct 모달 표시
 *   false → quiz-incorrect 모달 표시 (relatedVideoId + relatedTimestamp 로 영상 구간 이동 유도)
 */
export async function submitQuiz(body: SubmitQuizRequest): Promise<SubmitQuizResponse> {
  // 돌발·파이널 퀴즈 공용 답안 제출. isCorrect 값에 따라 정오답 모달 표시.
  return unwrap(api.post<ApiResponse<SubmitQuizResponse>>('/api/quiz-attempts', body))
}

// =============================================================================
// 6. 거꾸로 학습 (Flipped Learning)  — /api/learning-rooms/{roomId}/flipped/*
// =============================================================================
/**
 * ── 거꾸로 학습 전체 실행 순서 ──
 *
 * ① getCurriculumWeek(roomId, weekId)
 *     → keywords 를 받아둔다 (예: ["원자성", "일관성", "고립성", "지속성"])
 *     → keywords 는 세션 시작 메시지 생성에 백엔드가 내부적으로 활용
 *
 * ② startFlippedSession(roomId, { curriculum_id: weekId })
 *     → sessionId 발급 (이후 모든 flipped API 에서 세션 식별자로 사용)
 *     → firstMessage 반환 (모달의 첫 번째 AI 메시지로 표시)
 *
 * ③ 모달(ReverseLearningModal) 오픈
 *     → firstMessage 를 채팅 UI 첫 메시지로 렌더링
 *
 * ④ 사용자가 설명을 입력하고 전송 버튼 클릭
 *     → streamFlipped(roomId, sessionId, message) 로 SSE 연결 수립
 *     → 스트리밍 토큰을 받아 실시간으로 채팅 UI 에 append
 *     → type: 'counter_question' 이면 AI 역질문으로 강조 표시
 *     → type: 'done' 이면 EventSource 닫기
 *
 * ⑤ "설명 완료하고 최종 평가받기" 버튼 클릭
 *     → endFlippedSession(roomId, { sessionId })
 *     → score, gainedKeywords, weakKeywords, feedback 반환
 *     → MetaEvaluationModal 에 결과 표시
 *     → (백엔드 내부: USER_KEYWORDS 업데이트 + 매칭 엔진 자동 실행)
 *
 * ⑥ (선택) "전체 답변 기록 보기" 클릭
 *     → getFlippedResult(roomId, sessionId)
 *     → 평가 결과 + 전체 대화 기록 표시
 */

/**
 * startFlippedSession  —  POST /api/learning-rooms/{roomId}/flipped/start  (JWT 필요)
 *
 * "AI에게 거꾸로 설명하기" 버튼 클릭 시 호출.
 * sessionId 를 발급받고 모달을 오픈한다.
 *
 * 반환값:
 *   sessionId    - 세션 고유 ID (이후 stream / end / result 에서 모두 사용)
 *   firstMessage - AI 의 첫 번째 안내 메시지
 *                  예: "이번 주차에서 배운 '원자성, 일관성, 고립성, 지속성'에 대해 설명해주세요!"
 */
export async function startFlippedSession(
  roomId: string,
  body: StartFlippedRequest,
): Promise<StartFlippedResponse> {
  // 거꾸로 학습 세션 시작. sessionId 발급 및 AI 첫 안내 메시지(firstMessage) 수신.
  return unwrap(
    api.post<ApiResponse<StartFlippedResponse>>(`/api/learning-rooms/${roomId}/flipped/start`, body),
  )
}

/**
 * streamFlipped  —  SSE /api/learning-rooms/{roomId}/flipped/stream  (JWT 필요)
 *
 * AI 역질문을 토큰 단위로 스트리밍한다. axios 가 아닌 브라우저 EventSource API 를 사용한다.
 *
 * ⚠️  SSE 인증 제약:
 *   브라우저의 EventSource 는 커스텀 헤더(Authorization)를 지원하지 않는다.
 *   따라서 accessToken 을 URL 쿼리 파라미터(?token=...)로 전달한다.
 *   백엔드는 이 엔드포인트에서 헤더 대신 쿼리 파라미터로 토큰을 검증해야 한다.
 *
 * SSE 이벤트 타입:
 *   { type: 'token',            content: '...' }  → 채팅 UI 에 텍스트 append
 *   { type: 'counter_question', content: '...' }  → AI 역질문으로 강조 표시
 *   { type: 'done',             interactionId }   → 스트리밍 완료, EventSource 닫기
 *
 * 사용 예시 (ReverseLearningModal.tsx):
 *
 *   const sse = streamFlipped(roomId, sessionId, userMessage)
 *   let buffer = ''
 *
 *   sse.onmessage = (e) => {
 *     const event = JSON.parse(e.data)
 *     if (event.type === 'token' || event.type === 'counter_question') {
 *       buffer += event.content
 *       setAiMessage(buffer)      // 실시간 업데이트
 *     }
 *     if (event.type === 'done') {
 *       sse.close()               // ← 반드시 닫아야 메모리 누수 방지
 *     }
 *   }
 *
 *   sse.onerror = () => sse.close()
 *
 * @param roomId    - 학습실 고유 ID
 * @param sessionId - startFlippedSession 에서 발급받은 세션 ID
 * @param message   - 사용자가 입력한 설명 텍스트
 * @returns EventSource 인스턴스 — 컴포넌트 unmount 시 반드시 .close() 호출
 */
export function streamFlipped(roomId: string, sessionId: string, message: string): EventSource {
  // SSE 스트리밍 방식으로 AI 역질문을 실시간 수신. Authorization 헤더 미지원으로 ?token= 쿼리 파라미터 사용.
  // SSE 는 Authorization 헤더를 쓸 수 없으므로 토큰을 쿼리 파라미터로 전달
  const token = localStorage.getItem('accessToken') ?? ''
  const params = new URLSearchParams({ sessionId, message, token })

  return new EventSource(
    `${import.meta.env.VITE_API_BASE_URL}/api/learning-rooms/${roomId}/flipped/stream?${params}`,
  )
}

/**
 * endFlippedSession  —  POST /api/learning-rooms/{roomId}/flipped/end  (JWT 필요)
 *
 * "설명 완료하고 최종 평가받기" 버튼 클릭 시 호출.
 * 백엔드 처리 순서:
 *   1. AI 전체 대화를 분석하여 gainedKeywords, weakKeywords 확정
 *   2. USER_KEYWORDS 테이블 업데이트
 *   3. 매칭 엔진 자동 실행 (약점 키워드 보유자 검색)
 *   4. 매칭 대상 발견 시 SSE(/api/notifications/stream) 으로 상대방에게 알림 푸시
 *
 * 반환값:
 *   flippedResult - 'pass' | 'fail'
 *   score         - 이해도 점수 (0~100, 예: 95)
 *   gainedKeywords - 잘 이해한 키워드 목록 → strongKeywords 로 UI 표시
 *   weakKeywords   - 보완이 필요한 키워드 목록 → weakKeywords 로 UI 표시
 *   feedback      - AI 피드백 문구 (예: "이해도 95% - 완벽한 비유로 설명하는 1타 강사!")
 */
export async function endFlippedSession(
  roomId: string,
  body: EndFlippedRequest,
): Promise<EndFlippedResponse> {
  // 거꾸로 학습 세션 종료. AI 이해도 평가 + USER_KEYWORDS 업데이트 + 매칭 엔진 자동 실행.
  return unwrap(
    api.post<ApiResponse<EndFlippedResponse>>(`/api/learning-rooms/${roomId}/flipped/end`, body),
  )
}

/**
 * getFlippedResult  —  GET /api/learning-rooms/{roomId}/flipped/result/{sessionId}  (JWT 필요)
 *
 * "전체 답변 기록 보기" 버튼 클릭 시 호출.
 * endFlippedSession 의 평가 결과 + 세션 전체 대화 기록을 함께 반환한다.
 * conversations 배열을 순서대로 렌더링하면 채팅 기록을 재현할 수 있다.
 */
export async function getFlippedResult(roomId: string, sessionId: string): Promise<FlippedResultResponse> {
  // 거꾸로 학습 전체 대화 기록 및 평가 결과 조회. "전체 답변 기록 보기" 버튼에서 사용.
  return unwrap(
    api.get<ApiResponse<FlippedResultResponse>>(
      `/api/learning-rooms/${roomId}/flipped/result/${sessionId}`,
    ),
  )
}

// =============================================================================
// 7. 키워드 (User Keywords)  — GET /api/learning-rooms/{roomId}/keywords
// =============================================================================

/**
 * getUserKeywords  —  GET /api/learning-rooms/{roomId}/keywords  (JWT 필요)
 *
 * 거꾸로 학습 결과 모달 및 스터디 매칭 버튼에서 활용.
 * strengths (강점 키워드) 와 weaknesses (약점 키워드) 목록을 반환한다.
 * weaknessCount 가 높을수록 매칭 우선순위가 높아진다.
 */
export async function getUserKeywords(roomId: string): Promise<UserKeywordsResponse> {
  // 사용자 강점·약점 키워드 목록 조회. 스터디 매칭 우선순위 및 거꾸로 학습 결과 모달에서 사용.
  return unwrap(api.get<ApiResponse<UserKeywordsResponse>>(`/api/learning-rooms/${roomId}/keywords`))
}

// =============================================================================
// 8. 파이널 퀴즈 (Final Quiz)  — /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/*
// =============================================================================

/**
 * getFinalQuiz  —  GET .../quizzes/final  (JWT 필요)
 *
 * "퀴즈 도전하기" 버튼 클릭 시 호출. 5문제 서술형(essay) 퀴즈를 반환한다.
 * 메타인지(거꾸로 학습) 완료 후에만 버튼이 활성화된다.
 * maxLength 로 답안 textarea 의 글자 수 제한, tip 으로 힌트를 표시한다.
 */
export async function getFinalQuiz(roomId: string, weekId: string): Promise<FinalQuizResponse> {
  // 메타인지(거꾸로 학습) 완료 후 활성화. 5문제 서술형 파이널 퀴즈 조회.
  return unwrap(
    api.get<ApiResponse<FinalQuizResponse>>(
      `/api/learning-rooms/${roomId}/curriculum/${weekId}/quizzes/final`,
    ),
  )
}

/**
 * submitFinalQuiz  —  POST .../quizzes/final/submit  (JWT 필요)
 *
 * 파이널 퀴즈 전체 답안 제출. 백엔드 AI 분석이 비동기로 시작된다.
 * 응답 HTTP 상태 202 (Accepted) — 처리 중 상태를 의미.
 *
 * 반환값:
 *   reportId    - 완료 후 조회할 리포트 ID
 *   status      - 'analyzing' (처리 중) 또는 'completed' (완료)
 *   estimatedSec - 예상 완료 시간 (초, 예: 15)
 *
 * 폴링 패턴:
 *   const { reportId } = await submitFinalQuiz(roomId, weekId, body)
 *   const poll = setInterval(async () => {
 *     const report = await getQuizReport(roomId, weekId)
 *     if (report) {                  // 완료 시 데이터 반환
 *       clearInterval(poll)
 *       navigate('/quiz-report')
 *     }
 *   }, 3000)                         // 3초마다 폴링
 */
export async function submitFinalQuiz(
  roomId: string,
  weekId: string,
  body: SubmitFinalQuizRequest,
): Promise<SubmitFinalQuizResponse> {
  // 파이널 퀴즈 답안 제출. 202 Accepted 반환 → AI 분석 비동기 시작 → Polling으로 완료 확인.
  return unwrap(
    api.post<ApiResponse<SubmitFinalQuizResponse>>(
      `/api/learning-rooms/${roomId}/curriculum/${weekId}/quizzes/final/submit`,
      body,
    ),
  )
}

/**
 * getQuizReport  —  GET .../quiz-report  (JWT 필요)
 *
 * AI 종합 분석 리포트 조회. submitFinalQuiz 후 status 가 'completed' 가 될 때까지 폴링.
 *
 * 반환값:
 *   finalScore  - 최종 점수 (예: 92)
 *   radarData   - 레이더 차트 데이터 { 개념이해도: 90, 응용력: 85, 논리력: 95, 키워드적중률: 88 }
 *   questions   - 문항별 AI 해설 (gainedKeywords, weakKeywords, aiComment 포함)
 */
export async function getQuizReport(roomId: string, weekId: string): Promise<QuizReportResponse> {
  // 퀴즈 제출 후 AI 분석 리포트가 완료될 때까지 주기적으로 상태 확인(Polling).
  return unwrap(
    api.get<ApiResponse<QuizReportResponse>>(
      `/api/learning-rooms/${roomId}/curriculum/${weekId}/quiz-report`,
    ),
  )
}

// =============================================================================
// 9. 스터디 매칭 (Study Matching)  — /api/study-groups/*
// =============================================================================
/**
 * ── 스터디 매칭 흐름 ──
 *
 * 매칭은 endFlippedSession 완료 후 백엔드가 자동으로 실행한다.
 * 매칭 조건:
 *   - 동일 키워드를 강점으로 가진 상대방 (약점 키워드 ↔ 강점 키워드)
 *   - USER_KEYWORDS.created_at 7일 이내
 *   - Redis 에 토큰이 있는 현재 로그인 중인 사용자
 *
 * 매칭 성사 시 상대방에게 SSE(/api/notifications/stream) 로 실시간 알림 푸시.
 * 나에게는 getStudySuggestions 호출 시 pending 상태의 제안이 반환됨.
 */

/**
 * getStudySuggestions  —  GET /api/study-groups/suggestions  (JWT 필요)
 *
 * 매칭 제안 목록 조회 (pending 상태만).
 * SSE 알림을 받은 후 이 API 를 호출해 제안 상세 정보를 가져온다.
 */
export async function getStudySuggestions(): Promise<StudySuggestion[]> {
  // SSE 매칭 알림 수신 후 호출. pending 상태의 스터디 제안 목록 조회.
  return unwrap(api.get<ApiResponse<StudySuggestion[]>>('/api/study-groups/suggestions'))
}

/**
 * acceptStudySuggestion  —  POST /api/study-groups/suggestions/{id}/accept  (JWT 필요)
 *
 * "멘토에게 스터디 요청하기" 버튼 클릭 시 호출.
 * 양쪽 모두 수락하면 groupStatus 가 'active' 로 전환되고 STUDY_GROUPS 가 생성된다.
 * 상대방이 아직 수락하지 않은 경우 groupStatus 는 'pending_acceptance'.
 */
export async function acceptStudySuggestion(suggestionId: string): Promise<AcceptSuggestionResponse> {
  // 스터디 제안 수락. 양쪽 모두 수락 시 groupStatus 'active' 전환, STUDY_GROUPS 생성.
  return unwrap(
    api.post<ApiResponse<AcceptSuggestionResponse>>(
      `/api/study-groups/suggestions/${suggestionId}/accept`,
    ),
  )
}

/**
 * rejectStudySuggestion  —  POST /api/study-groups/suggestions/{id}/reject  (JWT 필요)
 *
 * "나중에 결정하기" 버튼 클릭 시 호출.
 * 거절 시 상대방에게 SSE 로 "상대방이 거절했습니다" 알림이 푸시된다.
 */
export async function rejectStudySuggestion(suggestionId: string): Promise<RejectSuggestionResponse> {
  // 스터디 제안 거절. 상대방에게 SSE로 거절 알림 자동 푸시.
  return unwrap(
    api.post<ApiResponse<RejectSuggestionResponse>>(
      `/api/study-groups/suggestions/${suggestionId}/reject`,
    ),
  )
}

/**
 * getStudyGroup  —  GET /api/study-groups/{groupId}  (JWT 필요)
 *
 * 스터디 그룹 상세 정보 조회.
 * partner.isOnline 은 Redis 에서 상대방의 토큰 존재 여부로 판단 (실시간 접속 상태).
 */
export async function getStudyGroup(groupId: string): Promise<StudyGroupDetail> {
  // 스터디 그룹 상세 조회. partner.isOnline은 Redis 기반 실시간 접속 여부.
  return unwrap(api.get<ApiResponse<StudyGroupDetail>>(`/api/study-groups/${groupId}`))
}

/**
 * getGroupMessages  —  GET /api/study-groups/{groupId}/messages  (JWT 필요)
 *
 * 채팅방 입장 시 이전 대화 이력을 가져온다 (페이지네이션 지원).
 * 이력 로드 후 connectGroupChat 으로 WebSocket 연결을 수립한다.
 *
 * @param groupId - 스터디 그룹 고유 ID
 * @param page    - 0부터 시작하는 페이지 번호 (기본값: 0)
 * @param size    - 페이지당 메시지 수 (기본값: 50)
 */
export async function getGroupMessages(
  groupId: string,
  page = 0,
  size = 50,
): Promise<ChatMessage[]> {
  // 채팅방 입장 시 이전 대화 이력 조회. connectGroupChat() WebSocket 연결 전 호출.
  return unwrap(
    api.get<ApiResponse<ChatMessage[]>>(
      `/api/study-groups/${groupId}/messages?page=${page}&size=${size}`,
    ),
  )
}

/**
 * connectGroupChat  —  WS wss://api.moai.app/ws/study-groups/{groupId}  (JWT 필요)
 *
 * 실시간 채팅 WebSocket 연결.
 * getGroupMessages 로 이전 이력을 먼저 로드한 후 이 함수를 호출한다.
 *
 * ⚠️  WebSocket 인증 제약:
 *   브라우저 WebSocket API 는 커스텀 헤더를 지원하지 않는다.
 *   accessToken 을 URL 쿼리 파라미터(?token=...)로 전달한다.
 *
 * 메시지 전송 형식 (클라이언트 → 서버):
 *   ws.send(JSON.stringify({ content: "메시지 내용" }))
 *
 * 메시지 수신 형식 (서버 → 클라이언트):
 *   { messageId, senderType, senderId, senderNickname, content, isAiCorrection, sentAt }
 *
 * 사용 예시:
 *   const history = await getGroupMessages(groupId)
 *   setMessages(history)
 *
 *   const ws = connectGroupChat(groupId)
 *   ws.onmessage = (e) => setMessages(prev => [...prev, JSON.parse(e.data)])
 *   ws.onerror   = () => console.error('WebSocket 오류')
 *   // 컴포넌트 unmount 시: ws.close()
 *
 * @param groupId - 스터디 그룹 고유 ID (acceptStudySuggestion 응답 또는 getStudyGroup 에서 획득)
 * @returns WebSocket 인스턴스 — 컴포넌트 unmount 시 반드시 .close() 호출
 */
export function connectGroupChat(groupId: string): WebSocket {
  // WebSocket 실시간 채팅 연결. Authorization 헤더 미지원으로 ?token= 쿼리 파라미터 사용.
  const token = localStorage.getItem('accessToken') ?? ''
  // http(s):// → ws(s):// 로 변환
  const wsBase = (import.meta.env.VITE_API_BASE_URL as string).replace(/^http/, 'ws')
  return new WebSocket(`${wsBase}/ws/study-groups/${groupId}?token=${token}`)
}

// =============================================================================
// 10. 알림 (Notifications)  — /api/notifications/*
// =============================================================================

/**
 * connectNotificationStream  —  SSE /api/notifications/stream  (JWT 필요)
 *
 * 로그인 성공 직후 즉시 연결하여 실시간 알림을 대기한다.
 * 로그아웃 시 반드시 .close() 를 호출해야 한다.
 *
 * ⚠️  SSE 인증 제약: token 을 쿼리 파라미터로 전달 (streamFlipped 와 동일한 이유)
 *
 * 수신하는 SSE 이벤트 타입:
 *   study_match    - 새 스터디 매칭 제안 도착 (suggestionId 포함)
 *   study_rejected - 내가 제안한 상대방이 거절
 *   study_accepted - 내가 제안한 상대방이 수락 (groupId 포함 → 채팅방 이동)
 *
 * 사용 예시 (App.tsx 또는 최상위 컴포넌트):
 *   const { isAuthenticated } = useAuth()
 *   useEffect(() => {
 *     if (!isAuthenticated) return
 *     const sse = connectNotificationStream()
 *     sse.onmessage = (e) => {
 *       const event = JSON.parse(e.data)
 *       if (event.type === 'study_match') showMatchNotification(event)
 *     }
 *     return () => sse.close()     // 로그아웃 또는 unmount 시 정리
 *   }, [isAuthenticated])
 */
export function connectNotificationStream(): EventSource {
  // 로그인 직후 SSE 연결. 스터디 매칭·수락·거절 알림 실시간 수신. Authorization 헤더 미지원으로 ?token= 쿼리 파라미터 사용.
  const token = localStorage.getItem('accessToken') ?? ''
  return new EventSource(
    `${import.meta.env.VITE_API_BASE_URL}/api/notifications/stream?token=${token}`,
  )
}

/**
 * getNotifications  —  GET /api/notifications  (JWT 필요)
 *
 * 읽지 않은 알림 목록 조회. isRead === false 인 항목만 반환된다.
 * TopBar 의 알림 벨 아이콘 뱃지 카운트에 활용한다.
 */
export async function getNotifications(): Promise<NotificationItem[]> {
  // 읽지 않은 알림 목록 조회. TopBar 알림 벨 아이콘 뱃지 카운트에 활용.
  return unwrap(api.get<ApiResponse<NotificationItem[]>>('/api/notifications'))
}

/**
 * markNotificationRead  —  PATCH /api/notifications/{id}/read  (JWT 필요)
 *
 * 알림 클릭 시 읽음 처리. NOTIFICATIONS.is_read = true 로 업데이트된다.
 * 응답: { notificationId, isRead: true }
 */
export async function markNotificationRead(notificationId: string): Promise<MarkReadResponse> {
  // 알림 클릭 시 읽음 처리. NOTIFICATIONS.is_read = true 업데이트.
  return unwrap(
    api.patch<ApiResponse<MarkReadResponse>>(`/api/notifications/${notificationId}/read`),
  )
}

// =============================================================================
// 11. 마이페이지 (My Page)  — /api/users/me*
// =============================================================================

/**
 * getMyProfile  —  GET /api/users/me  (JWT 필요)
 *
 * 마이페이지 전체 프로필 조회.
 * getHomeProfile 과 동일한 엔드포인트지만 더 많은 필드를 반환한다:
 *   loginId, name, email, birthDate, interestKeywords,
 *   studySuggestionEnabled, themePreference, status 추가 포함.
 */
export async function getMyProfile(): Promise<UserProfileFull> {
  // 마이페이지 전체 프로필 조회. getHomeProfile과 동일 엔드포인트지만 더 많은 필드 반환.
  return unwrap(api.get<ApiResponse<UserProfileFull>>('/api/users/me'))
}

/**
 * updateProfile  —  PATCH /api/users/me  (JWT 필요)
 *
 * 닉네임, 프로필 사진 URL, 다크/라이트 테마 설정 변경.
 * 변경된 필드만 포함해서 전송하면 된다 (partial update).
 */
export async function updateProfile(body: UpdateProfileRequest): Promise<UpdateProfileResponse> {
  // 닉네임·프로필 이미지·테마 변경. 변경 필드만 포함해서 전송 (partial update).
  return unwrap(api.patch<ApiResponse<UpdateProfileResponse>>('/api/users/me', body))
}

/**
 * updateKeywords  —  PATCH /api/users/me/keywords  (JWT 필요)
 *
 * 관심 키워드 수정. 변경 즉시 홈 화면 AI 추천이 갱신된다.
 */
export async function updateKeywords(body: UpdateKeywordsRequest): Promise<UpdateKeywordsResponse> {
  // 관심 키워드 수정. 변경 즉시 홈 화면 AI 추천 갱신됨.
  return unwrap(api.patch<ApiResponse<UpdateKeywordsResponse>>('/api/users/me/keywords', body))
}

/**
 * deleteAccount  —  DELETE /api/users/me  (JWT 필요)
 *
 * 회원 탈퇴. 모든 개인정보·학습 데이터가 즉시 파기된다.
 * USERS.status = "inactive" 로 전환.
 *
 * ⚠️  호출 후 반드시 AuthContext.clearAuth() 와 로그인 페이지 이동 처리 필요.
 */
export async function deleteAccount(body: DeleteAccountRequest): Promise<void> {
  // 회원 탈퇴. USERS.status = "inactive" 전환, 모든 개인정보·학습 데이터 즉시 파기.
  // DELETE 요청에서 body 는 axios 의 { data: body } 형태로 전달한다
  await api.delete('/api/users/me', { data: body })
}

/**
 * getLearningHistory  —  GET /api/users/me/learning-history  (JWT 필요)
 *
 * 마이페이지의 학습실 이력 목록. 완료한 학습실과 진행 중인 학습실 모두 포함.
 * completionRate 와 status 를 기반으로 이력 카드를 렌더링한다.
 */
export async function getLearningHistory(): Promise<LearningHistoryItem[]> {
  // 마이페이지 학습 이력 조회. 완료·진행 중 학습실 모두 포함. completionRate·status 기반 카드 렌더링.
  return unwrap(api.get<ApiResponse<LearningHistoryItem[]>>('/api/users/me/learning-history'))
}
