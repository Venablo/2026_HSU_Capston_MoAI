/**
 * mockHandlers.ts  —  Axios custom adapter for VITE_USE_MOCK=true
 *
 * When mock mode is enabled, this adapter intercepts every request and returns
 * realistic fake data without hitting a real server.  All responses follow the
 * standard backend envelope: { success, data, message, timestamp }.
 *
 * Adding a new mock endpoint:
 *   1. Add mock data constant near the top (MOCK_* section).
 *   2. Add a matching condition inside getMockResponse().
 *   3. URL matching uses the full URL path extracted by extractPath().
 */

import type { InternalAxiosRequestConfig, AxiosResponse } from 'axios'

// ── Envelope helper ────────────────────────────────────────────────────────────

interface Envelope<T = unknown> {
  success:   true
  data:      T
  message:   string
  timestamp: string
}

function ok<T>(data: T): Envelope<T> {
  return { success: true, data, message: 'OK', timestamp: new Date().toISOString() }
}

// ── Mock data ──────────────────────────────────────────────────────────────────

const MOCK_USER_ID = 'u0000000-0000-0000-0000-000000000001'
const MOCK_ROOM_ID = 'r1000000-0000-0000-0000-000000000001'

const MOCK_TOKENS = {
  accessToken:  'mock-access-token-dev',
  refreshToken: 'mock-refresh-token-dev',
  expiresIn:    3600,
  userId:       MOCK_USER_ID,
  nickname:     'MoAI 개발자',
}

const MOCK_PROFILE_FULL = {
  userId:                 MOCK_USER_ID,
  loginId:               'devuser',
  name:                  '개발자',
  nickname:              'MoAI 개발자',
  email:                 'dev@moai.test',
  birthDate:             '1995-01-01',
  profileImageUrl:       '',
  interestKeywords:      ['데이터베이스', '네트워크', 'CS면접'],
  studySuggestionEnabled: true,
  themePreference:       'dark',
  status:                'active',
}

const MOCK_ROOMS = [
  {
    roomId:         MOCK_ROOM_ID,
    subject:        '데이터베이스 기초',
    level:          'beginner',
    currentWeek:    2,
    durationWeeks:  4,
    completionRate: 35,
    status:         'active',
  },
  {
    roomId:         'r1000000-0000-0000-0000-000000000002',
    subject:        '컴퓨터 네트워크',
    level:          'intermediate',
    currentWeek:    4,
    durationWeeks:  4,
    completionRate: 100,
    status:         'completed',
  },
]

const MOCK_CURRICULUM_WEEKS = [
  { weekId: 'w3000000-0000-0000-0000-000000000001', weekNumber: 1, topic: '관계형 데이터베이스 개요', completionRate: 100 },
  { weekId: 'w3000000-0000-0000-0000-000000000002', weekNumber: 2, topic: 'SQL 기초와 CRUD',        completionRate: 60  },
  { weekId: 'w3000000-0000-0000-0000-000000000003', weekNumber: 3, topic: '정규화와 트랜잭션',      completionRate: 0   },
  { weekId: 'w3000000-0000-0000-0000-000000000004', weekNumber: 4, topic: '인덱스와 최적화',        completionRate: 0   },
]

const MOCK_WEEK_DETAIL = {
  weekId:         'w3000000-0000-0000-0000-000000000002',
  weekNumber:     2,
  topic:          'SQL 기초와 CRUD',
  description:    'SELECT, INSERT, UPDATE, DELETE를 마스터하고 실제 데이터를 다루는 방법을 학습합니다.',
  completionRate: 60,
  keywords:       ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'WHERE', 'JOIN'],
  resources:      [],
  mainVideoId:    'dQw4w9WgXcQ',
}

const MOCK_RECOMMENDED_VIDEOS = {
  videos: [
    { videoId: 'dQw4w9WgXcQ', title: 'SQL 완전 정복 1강 - SELECT 기초',   durationSec: 1200, viewCount: 152000 },
    { videoId: 'abc123xyz00', title: 'SQL 완전 정복 2강 - JOIN 완벽 이해', durationSec: 980,  viewCount: 98000  },
  ],
}

const MOCK_MATERIAL_DETAIL = {
  materialId:   'mat-001',
  title:        'SELECT 구문 완벽 정리',
  videoSegment: '04:28 – 08:30',
  summaryItems: [
    { label: 'A', title: 'SELECT 기본 구조', desc: 'SELECT 컬럼명 FROM 테이블명 WHERE 조건으로 데이터를 조회합니다.' },
    { label: 'B', title: 'WHERE 절 활용',    desc: '비교 연산자(=, >, <), LIKE, IN, BETWEEN 등으로 조건을 표현합니다.' },
    { label: 'C', title: 'ORDER BY 정렬',    desc: 'ASC(오름차순)와 DESC(내림차순)로 결과를 정렬합니다.' },
  ],
}

const MOCK_INSTANT_QUIZ = {
  questionId:     'q0000000-0000-0000-0000-000000000001',
  quizId:         'qz000000-0000-0000-0000-000000000001',
  questionType:   'multiple',
  question:       'SQL에서 특정 조건을 만족하는 행만 필터링할 때 사용하는 절은?',
  options:        [
    { label: 'A', text: 'WHERE' },
    { label: 'B', text: 'HAVING' },
    { label: 'C', text: 'GROUP BY' },
    { label: 'D', text: 'ORDER BY' },
  ],
  timeLimitSec:   60,
  relatedKeyword: 'WHERE',
}

const MOCK_FINAL_QUIZ = {
  quizId: 'qz000000-0000-0000-0000-000000000002',
  title:  'SQL 기초와 CRUD — 최종 퀴즈',
  questions: [
    {
      questionId:   'q0000000-0000-0000-0000-000000000010',
      questionType: 'essay',
      order:        1,
      question:     'SELECT 문의 논리적 실행 순서를 FROM부터 단계별로 설명해주세요.',
      maxLength:    500,
      tip:          'FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY 순서를 생각해보세요.',
    },
    {
      questionId:   'q0000000-0000-0000-0000-000000000011',
      questionType: 'essay',
      order:        2,
      question:     'INNER JOIN과 LEFT JOIN의 차이점을 예시를 들어 설명해주세요.',
      maxLength:    500,
      tip:          '일치하지 않는 행이 어떻게 처리되는지 중심으로 설명해보세요.',
    },
  ],
}

const MOCK_QUIZ_REPORT = {
  finalScore: 88,
  radarData:  { '개념이해도': 90, '응용력': 85, '논리력': 92, '키워드적중률': 85 },
  questions:  [],
}

const MOCK_USER_KEYWORDS = {
  strengths:   [{ keyword: 'SELECT',  isResolved: false }, { keyword: 'WHERE', isResolved: false }],
  weaknesses:  [{ keyword: 'JOIN',    weaknessCount: 2,  isResolved: false }],
}

// ── URL pattern helpers ────────────────────────────────────────────────────────

function extractPath(fullUrl: string): string {
  try   { return new URL(fullUrl).pathname }
  catch { return fullUrl }
}

// ── Route dispatcher ───────────────────────────────────────────────────────────

function getMockResponse(method: string, fullUrl: string): Envelope {
  const path = extractPath(fullUrl)
  const m    = method.toUpperCase()

  // ── Auth ──────────────────────────────────────────────────────────────────
  if (m === 'POST'  && path === '/api/auth/login')
    return ok(MOCK_TOKENS)

  if (m === 'POST'  && path === '/api/auth/register')
    return ok({ userId: MOCK_USER_ID, nickname: 'MoAI 개발자', message: '회원가입이 완료되었습니다.' })

  if (m === 'POST'  && path === '/api/auth/logout')
    return ok(null)

  if (m === 'POST'  && path === '/api/auth/refresh')
    return ok({ accessToken: 'mock-new-access-token', expiresIn: 3600 })

  // ── Users ─────────────────────────────────────────────────────────────────
  if (m === 'GET'    && path === '/api/users/me')
    return ok(MOCK_PROFILE_FULL)

  if (m === 'PATCH'  && path === '/api/users/me')
    return ok({ nickname: 'MoAI 개발자', profileImageUrl: '', themePreference: 'dark' })

  if (m === 'PATCH'  && path === '/api/users/me/keywords')
    return ok({ interestKeywords: [] })

  if (m === 'DELETE' && path === '/api/users/me')
    return ok(null)

  if (m === 'GET'    && path === '/api/users/me/learning-history')
    return ok(MOCK_ROOMS)

  // ── Onboarding ────────────────────────────────────────────────────────────
  if (m === 'GET' && path === '/api/onboarding/keywords')
    return ok({ keywords: ['정보처리기사', '데이터베이스', '네트워크', '알고리즘', '운영체제', 'CS면접', '웹개발', 'Spring Boot', 'React', 'Docker'] })

  // ── Learning Rooms ────────────────────────────────────────────────────────
  if (m === 'POST' && path === '/api/learning-rooms')
    return ok({ roomId: MOCK_ROOM_ID, subject: '신규 학습실', level: 'beginner', durationWeeks: 4, curriculum: [{ weekNumber: 1, topic: '기초 개념' }] })

  if (m === 'GET'  && path === '/api/learning-rooms')
    return ok(MOCK_ROOMS)

  // ── Curriculum — order: most specific patterns first ──────────────────────

  // GET /api/learning-rooms/{id}/curriculum/{weekId}/recommended-videos
  if (m === 'GET' && /\/curriculum\/[^/]+\/recommended-videos$/.test(path))
    return ok(MOCK_RECOMMENDED_VIDEOS)

  // GET /api/learning-rooms/{id}/curriculum/{weekId}/quiz-attempts
  if (m === 'GET' && /\/curriculum\/[^/]+\/quiz-attempts$/.test(path))
    return ok([])

  // GET /api/learning-rooms/{id}/curriculum/{weekId}/quizzes/final/submit
  if (m === 'POST' && /\/quizzes\/final\/submit$/.test(path))
    return ok({ reportId: 'report-001', status: 'completed', estimatedSec: 0 })

  // GET /api/learning-rooms/{id}/curriculum/{weekId}/quizzes/final
  if (m === 'GET' && /\/quizzes\/final$/.test(path))
    return ok(MOCK_FINAL_QUIZ)

  // GET /api/learning-rooms/{id}/curriculum/{weekId}/quizzes/instant
  if (m === 'GET' && /\/quizzes\/instant$/.test(path))
    return ok(MOCK_INSTANT_QUIZ)

  // GET /api/learning-rooms/{id}/curriculum/{weekId}/quiz-report
  if (m === 'GET' && /\/quiz-report$/.test(path))
    return ok(MOCK_QUIZ_REPORT)

  // PATCH /api/learning-rooms/{id}/curriculum/{weekId}/progress
  if (m === 'PATCH' && /\/curriculum\/[^/]+\/progress$/.test(path))
    return ok({ completionRate: 75 })

  // GET /api/learning-rooms/{id}/curriculum/{weekId} (specific week)
  if (m === 'GET' && /\/curriculum\/[^/]+$/.test(path))
    return ok(MOCK_WEEK_DETAIL)

  // GET /api/learning-rooms/{id}/curriculum (all weeks)
  if (m === 'GET' && /\/curriculum$/.test(path))
    return ok(MOCK_CURRICULUM_WEEKS)

  // ── Materials ─────────────────────────────────────────────────────────────
  if (m === 'GET' && /\/materials\/[^/]+$/.test(path))
    return ok(MOCK_MATERIAL_DETAIL)

  if (m === 'GET' && /\/materials$/.test(path))
    return ok([])

  // ── Learning Event Logs ───────────────────────────────────────────────────
  if (m === 'POST' && /\/events$/.test(path))
    return ok({ aiTriggered: false })

  // ── User Keywords ─────────────────────────────────────────────────────────
  if (m === 'GET' && /\/keywords$/.test(path))
    return ok(MOCK_USER_KEYWORDS)

  // ── Flipped Learning ──────────────────────────────────────────────────────
  if (m === 'POST' && /\/flipped\/start$/.test(path))
    return ok({ sessionId: 'session-mock-001', firstMessage: '이번 주차에서 배운 SELECT, INSERT, UPDATE, DELETE에 대해 설명해주세요!' })

  if (m === 'POST' && /\/flipped\/end$/.test(path))
    return ok({ sessionId: 'session-mock-001', flippedResult: 'pass', score: 85, gainedKeywords: ['SELECT', 'WHERE'], weakKeywords: ['JOIN'], feedback: '이해도 85% - 기본 개념이 잘 잡혀있어요! JOIN을 조금 더 연습해보세요.' })

  if (m === 'GET' && /\/flipped\/result\//.test(path))
    return ok({ score: 85, gainedKeywords: ['SELECT', 'WHERE'], weakKeywords: ['JOIN'], feedback: '기본기 탄탄!', conversations: [] })

  // ── Quiz Attempts ─────────────────────────────────────────────────────────
  if (m === 'POST' && path === '/api/quiz-attempts')
    return ok({ attemptId: 'att-001', isCorrect: true, correctAnswer: 'A', aiExplanation: 'WHERE 절은 SELECT 전에 행을 필터링합니다.', relatedVideoId: 'dQw4w9WgXcQ', relatedTimestamp: 120 })

  if (m === 'GET' && /\/api\/quiz-attempts\//.test(path))
    return ok({ question: '예시 질문', options: [], myAnswer: 'A', correctAnswer: 'A', isCorrect: true, aiExplanation: '정답입니다!' })

  // ── Study Groups ──────────────────────────────────────────────────────────
  if (m === 'GET'  && path === '/api/study-groups/suggestions')
    return ok([])

  if (m === 'POST' && /\/suggestions\/[^/]+\/accept$/.test(path))
    return ok({ suggestionId: 'sug-001', status: 'accepted', groupStatus: 'active' })

  if (m === 'POST' && /\/suggestions\/[^/]+\/reject$/.test(path))
    return ok({ status: 'rejected' })

  if (m === 'GET'  && /\/api\/study-groups\/[^/]+\/messages/.test(path))
    return ok([])

  if (m === 'GET'  && /\/api\/study-groups\/[^/]+$/.test(path))
    return ok({ groupId: 'group-001', matchKeyword: 'JOIN', status: 'active', partner: { userId: 'u2', nickname: '파트너', profileImageUrl: '', role: 'mentor', isOnline: true, strengthKeyword: 'JOIN' } })

  // ── Notifications ─────────────────────────────────────────────────────────
  if (m === 'GET'   && path === '/api/notifications')
    return ok([])

  if (m === 'PATCH' && /\/api\/notifications\/[^/]+\/read$/.test(path))
    return ok({ notificationId: 'notif-001', isRead: true })

  // ── Fallback ──────────────────────────────────────────────────────────────
  console.warn(`[Mock] No handler for ${m} ${path} — returning null`)
  return ok(null)
}

// ── Axios custom adapter ───────────────────────────────────────────────────────

export async function mockAdapter(config: InternalAxiosRequestConfig): Promise<AxiosResponse> {
  // Simulate a realistic network round-trip delay
  await new Promise<void>(resolve => setTimeout(resolve, 120))

  const envelope = getMockResponse(config.method ?? 'get', config.url ?? '')

  return {
    data:       envelope,
    status:     200,
    statusText: 'OK',
    headers:    {},
    config,
    request:    {},
  }
}
