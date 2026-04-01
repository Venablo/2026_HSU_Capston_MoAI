// ─────────────────────────────────────────────────────────────────────────────
// MoAI API Service  (function stubs — all 41 endpoints)
// Replace each stub body with the real axios call once the backend is live.
// Every call unwraps the common ApiResponse envelope and returns only `data`.
// ─────────────────────────────────────────────────────────────────────────────
import api from '../api/axios'
import type { ApiResponse } from '../types/api'
import type {
  // Auth
  RegisterRequest, RegisterResponse,
  LoginRequest, LoginResponse,
  LogoutRequest,
  RefreshTokenRequest, RefreshTokenResponse,
  // Home / Users
  UserProfileBasic,
  UserProfileFull,
  UpdateProfileRequest, UpdateProfileResponse,
  UpdateKeywordsRequest, UpdateKeywordsResponse,
  DeleteAccountRequest,
  LearningHistoryItem,
  // Onboarding
  OnboardingKeywordsResponse,
  CreateLearningRoomRequest, CreateLearningRoomResponse,
  // Learning Room
  LearningRoomListItem,
  CurriculumWeekSummary,
  CurriculumWeekDetail,
  UpdateProgressRequest, UpdateProgressResponse,
  RecommendedVideo,
  MaterialListItem,
  MaterialDetail,
  QuizAttemptListItem,
  QuizAttemptDetail,
  // Learning Event Logs
  LearningEventRequest, LearningEventResponse,
  InstantQuizResponse,
  SubmitQuizRequest, SubmitQuizResponse,
  // Flipped Learning
  StartFlippedRequest, StartFlippedResponse,
  EndFlippedRequest, EndFlippedResponse,
  FlippedResultResponse,
  // Keywords
  UserKeywordsResponse,
  // Final Quiz
  FinalQuizResponse,
  SubmitFinalQuizRequest, SubmitFinalQuizResponse,
  QuizReportResponse,
  // Study Matching
  StudySuggestion,
  AcceptSuggestionResponse, RejectSuggestionResponse,
  StudyGroupDetail,
  ChatMessage,
  // Notifications
  NotificationItem, MarkReadResponse,
} from '../types/api'

// ── Helper: unwrap ApiResponse envelope ──────────────────────────────────────

async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const { data: envelope } = await promise
  if (!envelope.success) throw new Error(envelope.message ?? 'API error')
  return envelope.data
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. Auth
// ─────────────────────────────────────────────────────────────────────────────

/** POST /api/auth/register — public */
export async function register(body: RegisterRequest): Promise<RegisterResponse> {
  return unwrap(api.post<ApiResponse<RegisterResponse>>('/api/auth/register', body))
}

/** POST /api/auth/login — public */
export async function login(body: LoginRequest): Promise<LoginResponse> {
  return unwrap(api.post<ApiResponse<LoginResponse>>('/api/auth/login', body))
}

/** POST /api/auth/logout — JWT required */
export async function logout(body: LogoutRequest): Promise<void> {
  await api.post('/api/auth/logout', body)
}

/** POST /api/auth/refresh — public */
export async function refreshToken(body: RefreshTokenRequest): Promise<RefreshTokenResponse> {
  return unwrap(api.post<ApiResponse<RefreshTokenResponse>>('/api/auth/refresh', body))
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Home
// ─────────────────────────────────────────────────────────────────────────────

/** GET /api/users/me — returns lightweight home-screen profile */
export async function getHomeProfile(): Promise<UserProfileBasic> {
  return unwrap(api.get<ApiResponse<UserProfileBasic>>('/api/users/me'))
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Onboarding
// ─────────────────────────────────────────────────────────────────────────────

/** GET /api/onboarding/keywords — recommended keyword tag list */
export async function getOnboardingKeywords(): Promise<OnboardingKeywordsResponse> {
  return unwrap(api.get<ApiResponse<OnboardingKeywordsResponse>>('/api/onboarding/keywords'))
}

/** POST /api/learning-rooms — create room + AI curriculum generation */
export async function createLearningRoom(body: CreateLearningRoomRequest): Promise<CreateLearningRoomResponse> {
  return unwrap(api.post<ApiResponse<CreateLearningRoomResponse>>('/api/learning-rooms', body))
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Learning Room
// ─────────────────────────────────────────────────────────────────────────────

/** GET /api/learning-rooms — my room list */
export async function getLearningRooms(): Promise<LearningRoomListItem[]> {
  return unwrap(api.get<ApiResponse<LearningRoomListItem[]>>('/api/learning-rooms'))
}

/** GET /api/learning-rooms/{roomId}/curriculum — all weeks dropdown */
export async function getCurriculum(roomId: string): Promise<CurriculumWeekSummary[]> {
  return unwrap(api.get<ApiResponse<CurriculumWeekSummary[]>>(`/api/learning-rooms/${roomId}/curriculum`))
}

/** GET /api/learning-rooms/{roomId}/curriculum/{weekId} — week detail */
export async function getCurriculumWeek(roomId: string, weekId: string): Promise<CurriculumWeekDetail> {
  return unwrap(api.get<ApiResponse<CurriculumWeekDetail>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}`))
}

/** PATCH /api/learning-rooms/{roomId}/curriculum/{weekId}/progress */
export async function updateProgress(
  roomId: string,
  weekId: string,
  body: UpdateProgressRequest,
): Promise<UpdateProgressResponse> {
  return unwrap(
    api.patch<ApiResponse<UpdateProgressResponse>>(
      `/api/learning-rooms/${roomId}/curriculum/${weekId}/progress`,
      body,
    ),
  )
}

/** GET /api/learning-rooms/{roomId}/curriculum/{weekId}/recommended-videos */
export async function getRecommendedVideos(roomId: string, weekId: string): Promise<RecommendedVideo[]> {
  return unwrap(
    api.get<ApiResponse<RecommendedVideo[]>>(
      `/api/learning-rooms/${roomId}/curriculum/${weekId}/recommended-videos`,
    ),
  )
}

/** GET /api/learning-rooms/{roomId}/materials — AI summary material list */
export async function getMaterials(roomId: string): Promise<MaterialListItem[]> {
  return unwrap(api.get<ApiResponse<MaterialListItem[]>>(`/api/learning-rooms/${roomId}/materials`))
}

/** GET /api/learning-rooms/{roomId}/materials/{materialId} — material detail with summaryItems */
export async function getMaterialDetail(roomId: string, materialId: string): Promise<MaterialDetail> {
  return unwrap(
    api.get<ApiResponse<MaterialDetail>>(`/api/learning-rooms/${roomId}/materials/${materialId}`),
  )
}

/** GET /api/learning-rooms/{roomId}/quiz-attempts — quiz history list */
export async function getQuizAttempts(roomId: string): Promise<QuizAttemptListItem[]> {
  return unwrap(api.get<ApiResponse<QuizAttemptListItem[]>>(`/api/learning-rooms/${roomId}/quiz-attempts`))
}

/** GET /api/quiz-attempts/{attemptId} — quiz detail with AI explanation */
export async function getQuizAttemptDetail(attemptId: string): Promise<QuizAttemptDetail> {
  return unwrap(api.get<ApiResponse<QuizAttemptDetail>>(`/api/quiz-attempts/${attemptId}`))
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Learning Event Logs
// ─────────────────────────────────────────────────────────────────────────────

/**
 * POST /api/learning-rooms/{roomId}/events
 * Send a user behaviour event; backend responds with aiTriggered + optional materialId.
 *
 * Frontend branching (spec §5):
 *   eventType = "video_rewind"                → getMaterialDetail → show monitoring modal
 *   eventType = "video_pause" | "tab_departure" → getMaterialDetail → show summary modal
 *   eventType = "video_skip"                  → getInstantQuiz    → show quiz modal
 */
export async function sendEventLog(
  roomId: string,
  body: LearningEventRequest,
): Promise<LearningEventResponse> {
  return unwrap(api.post<ApiResponse<LearningEventResponse>>(`/api/learning-rooms/${roomId}/events`, body))
}

/** GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/instant — pattern-3 pop-up quiz */
export async function getInstantQuiz(roomId: string, weekId: string): Promise<InstantQuizResponse> {
  return unwrap(
    api.get<ApiResponse<InstantQuizResponse>>(
      `/api/learning-rooms/${roomId}/curriculum/${weekId}/quizzes/instant`,
    ),
  )
}

/** POST /api/quiz-attempts — submit answer for instant quiz or final quiz */
export async function submitQuiz(body: SubmitQuizRequest): Promise<SubmitQuizResponse> {
  return unwrap(api.post<ApiResponse<SubmitQuizResponse>>('/api/quiz-attempts', body))
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. Flipped Learning
// ─────────────────────────────────────────────────────────────────────────────

/**
 * POST /api/learning-rooms/{roomId}/flipped/start
 * Opens a session; returns sessionId + firstMessage.
 * Flow: getCurriculumWeek → startFlippedSession → open modal → streamFlipped (SSE) → endFlippedSession
 */
export async function startFlippedSession(
  roomId: string,
  body: StartFlippedRequest,
): Promise<StartFlippedResponse> {
  return unwrap(
    api.post<ApiResponse<StartFlippedResponse>>(`/api/learning-rooms/${roomId}/flipped/start`, body),
  )
}

/**
 * SSE /api/learning-rooms/{roomId}/flipped/stream
 * Opens a Server-Sent Events stream for AI counter-question tokens.
 * Returns an EventSource; caller is responsible for closing it.
 *
 * NOTE: SSE requires the token in a query param because EventSource cannot
 * set custom headers. The backend must support ?token=... for this endpoint.
 */
export function streamFlipped(roomId: string, sessionId: string, message: string): EventSource {
  const token = localStorage.getItem('accessToken') ?? ''
  const params = new URLSearchParams({ sessionId, message, token })
  return new EventSource(
    `${import.meta.env.VITE_API_BASE_URL}/api/learning-rooms/${roomId}/flipped/stream?${params}`,
  )
}

/** POST /api/learning-rooms/{roomId}/flipped/end — close session + AI final evaluation */
export async function endFlippedSession(
  roomId: string,
  body: EndFlippedRequest,
): Promise<EndFlippedResponse> {
  return unwrap(
    api.post<ApiResponse<EndFlippedResponse>>(`/api/learning-rooms/${roomId}/flipped/end`, body),
  )
}

/** GET /api/learning-rooms/{roomId}/flipped/result/{sessionId} — evaluation + conversation log */
export async function getFlippedResult(roomId: string, sessionId: string): Promise<FlippedResultResponse> {
  return unwrap(
    api.get<ApiResponse<FlippedResultResponse>>(
      `/api/learning-rooms/${roomId}/flipped/result/${sessionId}`,
    ),
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. User Keywords
// ─────────────────────────────────────────────────────────────────────────────

/** GET /api/learning-rooms/{roomId}/keywords — strength / weakness keyword list */
export async function getUserKeywords(roomId: string): Promise<UserKeywordsResponse> {
  return unwrap(api.get<ApiResponse<UserKeywordsResponse>>(`/api/learning-rooms/${roomId}/keywords`))
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. Final Quiz
// ─────────────────────────────────────────────────────────────────────────────

/** GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final — 5 essay questions */
export async function getFinalQuiz(roomId: string, weekId: string): Promise<FinalQuizResponse> {
  return unwrap(
    api.get<ApiResponse<FinalQuizResponse>>(
      `/api/learning-rooms/${roomId}/curriculum/${weekId}/quizzes/final`,
    ),
  )
}

/** POST /api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final/submit — async AI grading */
export async function submitFinalQuiz(
  roomId: string,
  weekId: string,
  body: SubmitFinalQuizRequest,
): Promise<SubmitFinalQuizResponse> {
  return unwrap(
    api.post<ApiResponse<SubmitFinalQuizResponse>>(
      `/api/learning-rooms/${roomId}/curriculum/${weekId}/quizzes/final/submit`,
      body,
    ),
  )
}

/** GET /api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-report — AI comprehensive report */
export async function getQuizReport(roomId: string, weekId: string): Promise<QuizReportResponse> {
  return unwrap(
    api.get<ApiResponse<QuizReportResponse>>(
      `/api/learning-rooms/${roomId}/curriculum/${weekId}/quiz-report`,
    ),
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// 9. Study Matching
// ─────────────────────────────────────────────────────────────────────────────

/** GET /api/study-groups/suggestions — pending match suggestions */
export async function getStudySuggestions(): Promise<StudySuggestion[]> {
  return unwrap(api.get<ApiResponse<StudySuggestion[]>>('/api/study-groups/suggestions'))
}

/** POST /api/study-groups/suggestions/{id}/accept */
export async function acceptStudySuggestion(suggestionId: string): Promise<AcceptSuggestionResponse> {
  return unwrap(
    api.post<ApiResponse<AcceptSuggestionResponse>>(
      `/api/study-groups/suggestions/${suggestionId}/accept`,
    ),
  )
}

/** POST /api/study-groups/suggestions/{id}/reject */
export async function rejectStudySuggestion(suggestionId: string): Promise<RejectSuggestionResponse> {
  return unwrap(
    api.post<ApiResponse<RejectSuggestionResponse>>(
      `/api/study-groups/suggestions/${suggestionId}/reject`,
    ),
  )
}

/** GET /api/study-groups/{groupId} — group detail with partner info */
export async function getStudyGroup(groupId: string): Promise<StudyGroupDetail> {
  return unwrap(api.get<ApiResponse<StudyGroupDetail>>(`/api/study-groups/${groupId}`))
}

/** GET /api/study-groups/{groupId}/messages — paginated chat history */
export async function getGroupMessages(
  groupId: string,
  page = 0,
  size = 50,
): Promise<ChatMessage[]> {
  return unwrap(
    api.get<ApiResponse<ChatMessage[]>>(
      `/api/study-groups/${groupId}/messages?page=${page}&size=${size}`,
    ),
  )
}

/**
 * WS wss://api.moai.app/ws/study-groups/{groupId}
 * Opens a real-time chat WebSocket.  Load history first via getGroupMessages.
 * Returns a WebSocket instance; caller manages send / close lifecycle.
 */
export function connectGroupChat(groupId: string): WebSocket {
  const token = localStorage.getItem('accessToken') ?? ''
  const wsBase = (import.meta.env.VITE_API_BASE_URL as string).replace(/^http/, 'ws')
  return new WebSocket(`${wsBase}/ws/study-groups/${groupId}?token=${token}`)
}

// ─────────────────────────────────────────────────────────────────────────────
// 10. Notifications
// ─────────────────────────────────────────────────────────────────────────────

/**
 * SSE /api/notifications/stream
 * Opens a persistent SSE connection for real-time notifications.
 * Call once on login; close on logout.
 */
export function connectNotificationStream(): EventSource {
  const token = localStorage.getItem('accessToken') ?? ''
  return new EventSource(
    `${import.meta.env.VITE_API_BASE_URL}/api/notifications/stream?token=${token}`,
  )
}

/** GET /api/notifications — unread notification list */
export async function getNotifications(): Promise<NotificationItem[]> {
  return unwrap(api.get<ApiResponse<NotificationItem[]>>('/api/notifications'))
}

/** PATCH /api/notifications/{id}/read — mark a notification as read */
export async function markNotificationRead(notificationId: string): Promise<MarkReadResponse> {
  return unwrap(
    api.patch<ApiResponse<MarkReadResponse>>(`/api/notifications/${notificationId}/read`),
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// 11. My Page
// ─────────────────────────────────────────────────────────────────────────────

/** GET /api/users/me — full profile for My Page (differs from home-screen variant) */
export async function getMyProfile(): Promise<UserProfileFull> {
  return unwrap(api.get<ApiResponse<UserProfileFull>>('/api/users/me'))
}

/** PATCH /api/users/me — update nickname / profile image / theme */
export async function updateProfile(body: UpdateProfileRequest): Promise<UpdateProfileResponse> {
  return unwrap(api.patch<ApiResponse<UpdateProfileResponse>>('/api/users/me', body))
}

/** PATCH /api/users/me/keywords — update interest keywords */
export async function updateKeywords(body: UpdateKeywordsRequest): Promise<UpdateKeywordsResponse> {
  return unwrap(api.patch<ApiResponse<UpdateKeywordsResponse>>('/api/users/me/keywords', body))
}

/** DELETE /api/users/me — account deletion */
export async function deleteAccount(body: DeleteAccountRequest): Promise<void> {
  await api.delete('/api/users/me', { data: body })
}

/** GET /api/users/me/learning-history — past learning room list */
export async function getLearningHistory(): Promise<LearningHistoryItem[]> {
  return unwrap(api.get<ApiResponse<LearningHistoryItem[]>>('/api/users/me/learning-history'))
}
