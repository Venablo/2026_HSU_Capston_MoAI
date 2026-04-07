// ─────────────────────────────────────────────────────────────────────────────
// MoAI API Types  (matches API Specification v3.0)
// Base URL: https://api.moai.app/v1
// Auth:     Authorization: Bearer {accessToken}
// ─────────────────────────────────────────────────────────────────────────────

// ── Common ────────────────────────────────────────────────────────────────────

/** Envelope wrapping every successful REST response */
export interface ApiResponse<T = unknown> {
  success: boolean
  data: T
  message: string
  timestamp: string
}

/** Standard error payload from 4xx / 5xx responses */
export interface ApiError {
  success: false
  message: string
  errorCode: string
}

// ── 1. Auth ───────────────────────────────────────────────────────────────────

export interface RegisterRequest {
  login_id: string
  password: string
  name: string
  nickname: string
  email: string
  birth_date: string                  // "YYYY-MM-DD"
  interest_keywords: string[]
  // 백엔드 POST /api/auth/register — 스터디 매칭 허용 여부
  // 미전송 시 서버 기본값 true 적용. 마이페이지에서 나중에 변경 가능.
  study_suggestion_enabled?: boolean
}

export interface RegisterResponse {
  userId: string
  nickname: string
  message: string
}

export interface LoginRequest {
  login_id: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number            // seconds, e.g. 3600
  userId: string
  nickname: string
}

export interface LogoutRequest {
  refreshToken: string
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export interface RefreshTokenResponse {
  accessToken: string
  expiresIn: number
}

// ── 2. Home ───────────────────────────────────────────────────────────────────

/** Lightweight profile returned on home-screen entry */
export interface UserProfileBasic {
  userId: string
  nickname: string
  profileImageUrl: string
}

// ── 3. Onboarding ─────────────────────────────────────────────────────────────

export interface OnboardingKeywordsResponse {
  keywords: string[]
}

export interface CreateLearningRoomRequest {
  subject: string
  level: 'beginner' | 'intermediate' | 'advanced'
  duration_weeks: number
  hours_per_day: number
}

export interface CurriculumSummaryItem {
  weekNumber: number
  topic: string
}

export interface CreateLearningRoomResponse {
  roomId: string
  subject: string
  level: string
  durationWeeks: number
  curriculum: CurriculumSummaryItem[]
}

// ── 4. Learning Room ──────────────────────────────────────────────────────────

export interface LearningRoomListItem {
  roomId: string
  subject: string
  level: string
  currentWeek: number
  durationWeeks: number
  completionRate: number
  status: 'active' | 'completed' | 'paused'
}

export interface CurriculumWeekSummary {
  weekId: string
  weekNumber: number
  topic: string
  completionRate: number
}

export interface ResourceItem {
  type: 'pdf' | 'docx' | 'zip' | string
  title: string
  url: string
  size: string
}

export interface CurriculumWeekDetail {
  weekId: string
  weekNumber: number
  topic: string
  description: string
  completionRate: number
  keywords: string[]
  resources: ResourceItem[]
  mainVideoId: string
}

export interface UpdateProgressRequest {
  completionRate: number
}

export interface UpdateProgressResponse {
  completionRate: number
}

export interface RecommendedVideo {
  videoId: string
  title: string
  durationSec: number
  viewCount: number
}

/** Wrapper shape returned by GET …/recommended-videos */
export interface RecommendedVideosData {
  videos: RecommendedVideo[]
}

export interface MaterialListItem {
  materialId: string
  title: string
  videoSegment: string
  triggerKeywords: string[]
  createdAt: string            // ISO 8601
}

/** Raw API shape for a summary card (field names from spec: label / desc) */
export interface MaterialSummaryItemApi {
  label: string                // e.g. "A"
  title: string                // e.g. "Atomicity (원자성)"
  desc: string                 // explanation paragraph
}

export interface MaterialDetail {
  materialId: string
  title: string
  videoSegment: string
  summaryItems: MaterialSummaryItemApi[]
}

export interface QuizAttemptListItem {
  attemptId: string
  questionTitle: string
  isCorrect: boolean
  videoSegment: string
  attemptedAt: string          // ISO 8601
}

export interface QuizOption {
  label: string
  text: string
}

export interface QuizAttemptDetail {
  question: string
  options: QuizOption[]
  myAnswer: string
  correctAnswer: string
  isCorrect: boolean
  aiExplanation: string
}

// ── 5. Learning Event Logs ────────────────────────────────────────────────────

export type EventType = 'video_rewind' | 'video_pause' | 'tab_departure' | 'video_skip'

export interface VideoRewindPayload {
  video_id: string
  rewind_target_sec: number
}

export interface VideoPausePayload {
  video_id: string
  pause_start_sec: number
  pause_duration_sec: number
  trigger_type: 'long_pause' | string
}

export interface TabDeparturePayload {
  video_id: string
  departure_sec: number
  return_sec: number
  departure_count: number
  current_keyword: string
}

export interface VideoSkipPayload {
  video_id: string
  skip_from_sec: number
  skip_to_sec: number
}

export type LearningEventPayload =
  | VideoRewindPayload
  | VideoPausePayload
  | TabDeparturePayload
  | VideoSkipPayload

export interface LearningEventRequest {
  event_type: EventType
  curriculum_id: string
  payload: LearningEventPayload
}

/** Discriminated union: ai not triggered vs triggered with optional materialId */
export type LearningEventResponse =
  | { aiTriggered: false }
  | {
      aiTriggered: true
      eventType: EventType
      extractedKeywords?: string[]
      materialId?: string        // present for rewind / pause / tab_departure patterns
    }

// ── Instant Quiz (Pattern 3) ──────────────────────────────────────────────────

export interface InstantQuizResponse {
  questionId: string
  quizId: string
  questionType: 'multiple' | string
  question: string
  options: QuizOption[]
  timeLimitSec: number
  relatedKeyword: string
}

export interface SubmitQuizRequest {
  questionId: string
  quizId: string
  selected: string
}

export interface SubmitQuizResponse {
  attemptId: string
  isCorrect: boolean
  correctAnswer: string
  aiExplanation: string
  relatedVideoId: string
  relatedTimestamp: number
}

// ── 6. Flipped Learning ───────────────────────────────────────────────────────

export interface StartFlippedRequest {
  curriculum_id: string
}

export interface StartFlippedResponse {
  sessionId: string
  firstMessage: string
}

export interface FlippedStreamRequest {
  sessionId: string
  message: string
}

/** Individual token event from the SSE stream */
export type FlippedStreamEvent =
  | { type: 'token';            content: string }
  | { type: 'counter_question'; content: string }
  | { type: 'done';             interactionId: string }

export interface EndFlippedRequest {
  sessionId: string
}

export interface EndFlippedResponse {
  sessionId: string
  flippedResult: 'pass' | 'fail'
  score: number
  gainedKeywords: string[]
  weakKeywords: string[]
  feedback: string
}

export interface ConversationMessage {
  role: 'assistant' | 'user'
  content: string
}

export interface FlippedResultResponse {
  score: number
  gainedKeywords: string[]
  weakKeywords: string[]
  feedback: string
  conversations: ConversationMessage[]
}

// ── 7. User Keywords ──────────────────────────────────────────────────────────

export interface StrengthKeyword {
  keyword: string
  isResolved: boolean
}

export interface WeaknessKeyword {
  keyword: string
  weaknessCount: number
  isResolved: boolean
}

export interface UserKeywordsResponse {
  strengths: StrengthKeyword[]
  weaknesses: WeaknessKeyword[]
}

// ── 8. Final Quiz ─────────────────────────────────────────────────────────────

export interface FinalQuizQuestion {
  questionId: string
  questionType: 'essay' | string
  order: number
  question: string
  maxLength: number
  tip: string
}

export interface FinalQuizResponse {
  quizId: string
  title: string
  questions: FinalQuizQuestion[]
}

export interface FinalQuizAnswer {
  questionId: string
  answer: string
}

export interface SubmitFinalQuizRequest {
  quizId: string
  answers: FinalQuizAnswer[]
}

export interface SubmitFinalQuizResponse {
  reportId: string
  status: 'analyzing' | 'completed'
  estimatedSec: number
}

export interface QuizReportQuestionResult {
  order: number
  question: string
  score: number
  maxScore: number
  isCorrect: boolean
  myAnswer: string
  gainedKeywords: string[]
  missingKeywords: string[]
  aiComment: string
}

export interface QuizReportRadarData {
  [dimension: string]: number   // e.g. { "개념이해도": 90, "응용력": 85, ... }
}

export interface QuizReportResponse {
  finalScore: number
  radarData: QuizReportRadarData
  questions: QuizReportQuestionResult[]
}

// ── 9. Study Matching ─────────────────────────────────────────────────────────

export interface SuggestionPartner {
  nickname: string
  profileImageUrl: string
  strengthKeyword: string
}

export interface StudySuggestion {
  suggestionId: string
  suggestedRole: 'mentor' | 'mentee'
  partner: SuggestionPartner
  matchScore: number
  matchKeyword: string
  matchReason: string
  status: 'pending' | 'accepted' | 'rejected'
}

export interface AcceptSuggestionResponse {
  suggestionId: string
  status: 'accepted'
  groupStatus: 'pending_acceptance' | 'active'
}

export interface RejectSuggestionResponse {
  status: 'rejected'
}

export interface StudyGroupPartner {
  userId: string
  nickname: string
  profileImageUrl: string
  role: 'mentor' | 'mentee'
  isOnline: boolean
  strengthKeyword: string
}

export interface StudyGroupDetail {
  groupId: string
  matchKeyword: string
  status: 'active' | 'closed'
  partner: StudyGroupPartner
}

export interface ChatMessage {
  messageId: string
  senderType: 'user' | 'ai'
  senderId: string
  senderNickname: string
  content: string
  isAiCorrection: boolean
  sentAt: string               // ISO 8601
}

export interface WsChatSend {
  content: string
}

// ── 10. Notifications ─────────────────────────────────────────────────────────

export type NotificationType = 'study_match' | 'study_rejected' | 'study_accepted'

export interface NotificationItem {
  notificationId: string
  type: NotificationType
  message: string
  referenceId: string
  isRead: boolean
  createdAt: string            // ISO 8601
}

export interface MarkReadResponse {
  notificationId: string
  isRead: true
}

/** SSE notification event shapes from /api/notifications/stream */
export type NotificationSseEvent =
  | {
      type: 'study_match'
      message: string
      suggestionId: string
      partner: { nickname: string; role: string; strengthKeyword: string }
      matchScore: number
      matchKeyword: string
    }
  | {
      type: 'study_rejected'
      message: string
      suggestionId: string
    }
  | {
      type: 'study_accepted'
      message: string
      groupId: string
    }

// ── 11. My Page ───────────────────────────────────────────────────────────────

export interface UserProfileFull {
  userId: string
  loginId: string
  name: string
  nickname: string
  email: string
  birthDate: string
  profileImageUrl: string
  interestKeywords: string[]
  studySuggestionEnabled: boolean
  themePreference: 'light' | 'dark'
  status: 'active' | 'inactive'
}

export interface UpdateProfileRequest {
  nickname?: string
  profileImageUrl?: string
  themePreference?: 'light' | 'dark'
}

export interface UpdateProfileResponse {
  nickname: string
  profileImageUrl: string
  themePreference: string
}

export interface UpdateKeywordsRequest {
  interestKeywords: string[]
}

export interface UpdateKeywordsResponse {
  interestKeywords: string[]
}

export interface DeleteAccountRequest {
  password: string
}

export interface LearningHistoryItem {
  roomId: string
  subject: string
  level: string
  completionRate: number
  status: 'active' | 'completed'
  createdAt: string            // ISO 8601
}
