/**
 * ============================================================================
 * src/api/axios.ts  —  MoAI 공통 HTTP 클라이언트
 * ============================================================================
 *
 * 이 파일이 하는 일:
 *  1. axios 인스턴스 생성 (baseURL, timeout, 기본 헤더 설정)
 *  2. [요청 인터셉터] 모든 API 요청에 Authorization: Bearer 헤더 자동 첨부
 *  3. [응답 인터셉터] 성공 응답은 그대로 통과, 오류 코드별 공통 처리
 *     - 401: accessToken 만료 → refreshToken으로 토큰 재발급 자동 시도
 *             재발급 성공 시 원본 요청 자동 재시도 (사용자는 아무것도 모름)
 *             재발급 실패 시 전체 인증 정보 삭제 후 로그인 화면 이동
 *     - 403: errorCode 가 TOKEN_EXPIRED_CODES 에 해당하면 갱신 후 재시도, 그 외 즉시 전파
 *     - 404: 리소스 없음 — 오류 전파
 *     - 500: 서버 내부 오류 — 오류 전파
 * ============================================================================
 */

import axios, {
  type AxiosError,
  type AxiosResponse,
  type AxiosResponseTransformer,
  type InternalAxiosRequestConfig,
} from 'axios'

// ── localStorage 키 상수 ──────────────────────────────────────────────────────
// AuthContext (context/AuthContext.tsx) 와 완전히 동일한 키를 사용한다.
// 두 곳에서 같은 키를 참조하므로, 여기서 바꾸면 AuthContext 도 같이 바꿔야 한다.
const TOKEN_KEYS = {
  accessToken:  'accessToken',
  refreshToken: 'refreshToken',
  userId:       'userId',
  nickname:     'nickname',
} as const

// ── 커스텀 에러 클래스 ────────────────────────────────────────────────────────
/**
 * MoaiApiError
 *
 * 스펙에 정의된 공통 에러 코드를 타입-세이프하게 담는 커스텀 에러.
 * 컴포넌트에서 catch(e) 할 때 instanceof MoaiApiError 로 구분할 수 있다.
 *
 * 사용 예시:
 *   try {
 *     await sendEventLog(roomId, payload)
 *   } catch (e) {
 *     if (e instanceof MoaiApiError && e.status === 404) {
 *       // 학습실 없음 처리
 *     }
 *   }
 */
export class MoaiApiError extends Error {
  /** HTTP 상태 코드 (400, 401, 403, 404, 409, 500 …) */
  readonly status: number
  /** 백엔드가 내려주는 에러 코드 문자열 (예: "BAD_REQUEST", "NOT_FOUND") */
  readonly errorCode: string

  constructor(status: number, errorCode: string, message: string) {
    super(message)
    this.name    = 'MoaiApiError'
    this.status  = status
    this.errorCode = errorCode
  }
}

// ── snake_case → camelCase 자동 변환 ─────────────────────────────────────────
/**
 * deepCamelCase
 *
 * 백엔드는 snake_case(예: access_token, room_id)로 응답하고
 * 프론트엔드 타입은 camelCase(예: accessToken, roomId)를 사용한다.
 * 이 함수를 transformResponse 에 등록하면 모든 응답 JSON 키가
 * 자동으로 camelCase 로 변환되어 수동 매핑이 필요 없어진다.
 *
 * 재귀적으로 동작하므로 중첩 객체·배열도 모두 변환된다.
 * 문자열·숫자·null 등 원시값은 그대로 반환한다.
 */
function deepCamelCase<T>(data: T): T {
  if (Array.isArray(data)) {
    return data.map(deepCamelCase) as unknown as T
  }
  if (data !== null && typeof data === 'object') {
    return Object.fromEntries(
      Object.entries(data as Record<string, unknown>).map(([key, value]) => [
        key.replace(/_([a-z])/g, (_, char: string) => char.toUpperCase()),
        deepCamelCase(value),
      ]),
    ) as unknown as T
  }
  return data
}

// ── Axios 인스턴스 생성 ───────────────────────────────────────────────────────
/**
 * 모든 API 요청에서 공유하는 단일 axios 인스턴스.
 *
 * baseURL:  .env 파일의 VITE_API_BASE_URL 값. 예) https://api.moai.app/v1
 *           빌드 환경(dev / prod)에 따라 자동으로 다른 URL 을 사용한다.
 * timeout:  60,000ms (60초). AI 백엔드 처리 시간을 고려한 값. 이 시간 안에 응답이 없으면 ECONNABORTED 에러 발생.
 * headers:  JSON 통신이 기본. SSE / WebSocket 은 axios 가 아닌 EventSource /
 *           WebSocket 을 직접 사용하므로 여기 헤더가 적용되지 않는다.
 * transformResponse: axios 기본 JSON.parse 이후 deepCamelCase 를 체이닝한다.
 *   → 성공·오류 응답 모두 JSON 파싱 직후 변환되므로 인터셉터보다 먼저 실행된다.
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 60_000,
  headers: {
    'Content-Type': 'application/json',
  },
  transformResponse: ([] as AxiosResponseTransformer[]).concat(
    axios.defaults.transformResponse ?? [],  // 기본 JSON.parse
    (data: unknown) => deepCamelCase(data),  // snake_case → camelCase
  ),
})
console.log('[axios] instance created — timeout:', api.defaults.timeout, 'ms, baseURL:', api.defaults.baseURL)

// ── 토큰 갱신 상태 관리 ───────────────────────────────────────────────────────
/**
 * 동시에 여러 요청이 401을 받았을 때 토큰 갱신을 한 번만 시도하기 위한 플래그.
 *
 * 시나리오:
 *   요청 A, B, C 가 거의 동시에 나갔고 accessToken 이 만료된 경우
 *   → A 가 401 을 받으면 isRefreshing = true 로 설정하고 갱신 요청을 보냄
 *   → B, C 도 401 을 받지만 isRefreshing = true 이므로 갱신 요청을 보내지 않고
 *     pendingQueue 에 들어가서 갱신 완료 후 자동으로 재시도
 */
let isRefreshing = false
let refreshingSetAt = 0   // epoch ms when isRefreshing was set — used for deadlock detection

/**
 * 토큰 갱신이 진행 중일 때 대기 중인 요청들의 콜백 큐.
 * 갱신 성공 시 resolve(새 토큰), 실패 시 reject(에러) 호출.
 */
let pendingQueue: Array<{
  resolve: (newToken: string) => void
  reject:  (err: unknown)    => void
}> = []

/** 대기 중인 모든 요청을 처리 (성공 또는 실패) */
function flushPendingQueue(newToken: string | null, error: unknown = null) {
  pendingQueue.forEach(({ resolve, reject }) => {
    if (newToken) {
      resolve(newToken)
    } else {
      reject(error)
    }
  })
  pendingQueue = []
}

/** 인증 관련 localStorage 항목 전체 삭제 */
function clearStoredAuth() {
  Object.values(TOKEN_KEYS).forEach(key => localStorage.removeItem(key))
}

/**
 * 백엔드가 토큰 만료를 나타낼 때 사용하는 errorCode 화이트리스트.
 *
 * 403 응답의 errorCode 가 이 목록에 있을 때만 토큰 갱신을 시도한다.
 * 비즈니스 로직 403 (예: PREVIOUS_WEEK_NOT_COMPLETED, FORBIDDEN) 은
 * 이 목록에 포함되지 않으므로 갱신 없이 즉시 에러가 전파된다.
 * clearStoredAuth() / 로그인 리다이렉트는 절대 비즈니스 403으로 인해 발생하지 않는다.
 *
 * 백엔드에서 새로운 토큰 만료 코드가 추가되면 여기에만 추가하면 된다.
 */
const TOKEN_EXPIRED_CODES = new Set([
  'TOKEN_EXPIRED',
  'INVALID_TOKEN',
  'EXPIRED_TOKEN',
  'JWT_EXPIRED',
  'ACCESS_TOKEN_EXPIRED',
])

/**
 * refreshAndRetry
 *
 * 401·403 수신 시 refreshToken 으로 accessToken 을 갱신한 뒤 원본 요청을 재시도한다.
 * isRefreshing / pendingQueue 패턴으로 동시 다발 갱신 요청을 안전하게 직렬화한다.
 *
 * 흐름:
 *  1. refreshToken 없음 → clearStoredAuth + 로그인 이동
 *  2. 이미 갱신 중(isRefreshing) → pendingQueue 에 추가하고 갱신 완료 시 자동 재시도
 *  3. 최초 갱신 시도 → POST /api/auth/refresh → 새 token 저장 → 큐 flush → 원본 재시도
 *  4. 갱신 실패 → clearStoredAuth + 로그인 이동
 */
async function refreshAndRetry(
  error: AxiosError<{ success?: false; message?: string; errorCode?: string; code?: string }>,
  originalStatus: number,
  originalErrorCode: string,
  originalMessage: string,
): Promise<AxiosResponse> {
  // ── 재시도 루프 방지 ───────────────────────────────────────────────────────
  // 갱신 후 재시도한 요청이 또 401/403을 받은 경우, 진짜 권한·서버 문제이므로
  // 갱신을 다시 시도하지 않고 에러를 그대로 전파한다.
  // (이 플래그가 없으면 refresh 성공 → retry → 403 → refresh 성공 → ... 무한루프)
  if ((error.config as unknown as Record<string, unknown>)?._retried === true) {
    console.log('[axios] Already retried after refresh — propagating as genuine error:', originalStatus, originalErrorCode)
    return Promise.reject(new MoaiApiError(originalStatus, originalErrorCode, originalMessage))
  }

  const storedRefreshToken = localStorage.getItem(TOKEN_KEYS.refreshToken)

  if (!storedRefreshToken) {
    clearStoredAuth()
    window.location.href = '/'
    return Promise.reject(new MoaiApiError(originalStatus, originalErrorCode, originalMessage))
  }

  if (isRefreshing) {
    // Deadlock guard: refresh running > 15 s → force-reset and redirect to login
    if (Date.now() - refreshingSetAt > 15_000) {
      isRefreshing = false
      refreshingSetAt = 0
      flushPendingQueue(null, new Error('Token refresh deadlock — forcing reset'))
      clearStoredAuth()
      window.location.href = '/'
      return Promise.reject(new MoaiApiError(originalStatus, 'REFRESH_TIMEOUT', '세션이 만료되었습니다. 다시 로그인해 주세요.'))
    }
    return new Promise((resolve, reject) => {
      pendingQueue.push({
        resolve: (newToken: string) => {
          if (error.config) {
            console.log('[axios] Retrying (queued) with new token...')
            error.config.headers.Authorization = `Bearer ${newToken}`
            ;(error.config as unknown as Record<string, unknown>)._retried = true
            resolve(api(error.config))
          }
        },
        reject,
      })
    })
  }

  console.log('[axios] Refreshing token...')
  isRefreshing = true
  refreshingSetAt = Date.now()

  try {
    // 원시 axios 사용 — api 인스턴스에는 인터셉터가 걸려있어 401/403 무한루프 위험
    const rawRefresh = await axios.post(
      `${import.meta.env.VITE_API_BASE_URL}/api/auth/refresh`,
      { refreshToken: storedRefreshToken },
      { headers: { 'Content-Type': 'application/json' }, timeout: 10_000 },
    )
    const refreshBody = deepCamelCase(rawRefresh.data) as {
      success: boolean
      data: { accessToken: string; expiresIn: number }
    }
    const newAccessToken = refreshBody.data.accessToken
    localStorage.setItem(TOKEN_KEYS.accessToken, newAccessToken)
    flushPendingQueue(newAccessToken)
    if (error.config) {
      console.log('[axios] Retrying with new token...')
      error.config.headers.Authorization = `Bearer ${newAccessToken}`
      ;(error.config as unknown as Record<string, unknown>)._retried = true
      return api(error.config)
    }
  } catch (refreshError) {
    flushPendingQueue(null, refreshError)
    clearStoredAuth()
    window.location.href = '/'
    return Promise.reject(new MoaiApiError(401, 'REFRESH_FAILED', '세션이 만료되었습니다. 다시 로그인해 주세요.'))
  } finally {
    isRefreshing = false
    refreshingSetAt = 0
  }
  return Promise.reject(new MoaiApiError(originalStatus, originalErrorCode, originalMessage))
}

// ── ① 요청 인터셉터 — Authorization 헤더 자동 첨부 ──────────────────────────
/**
 * 동작 원리:
 *   axios 는 요청을 보내기 직전에 이 함수를 호출한다.
 *   localStorage 에서 accessToken 을 읽어 있으면 Authorization 헤더에 첨부한다.
 *
 * 왜 localStorage 를 직접 읽는가?
 *   AuthContext(React state) 는 클로저 문제로 인터셉터에서 항상 최신 값을
 *   참조하기 어렵다. localStorage 는 항상 현재 값을 반환하므로 안전하다.
 *   (AuthContext 와 localStorage 는 saveAuth / clearAuth 에서 항상 동기화됨)
 *
 * Bearer 형식:
 *   HTTP 표준 RFC 6750 에 따라 "Bearer <토큰>" 형식으로 전달한다.
 *   백엔드 Spring Security 가 이 헤더를 파싱해 JWT 검증을 수행한다.
 */
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // localStorage 에서 현재 유효한 accessToken 을 읽는다
    const accessToken = localStorage.getItem(TOKEN_KEYS.accessToken)
      console.log("보내려는 토큰:", accessToken);
    if (accessToken) {
      // 스펙 명세: Authorization: Bearer {accessToken}
      // 모든 JWT 필요 엔드포인트에 자동 첨부됨 (41개 중 38개)
      config.headers.Authorization = `Bearer ${accessToken}`
    }
    // token 이 없으면 헤더를 첨부하지 않는다 (공개 엔드포인트용: 로그인, 회원가입, 토큰 갱신)

    return config
  },
  // 요청 설정 자체가 실패한 경우 (네트워크 에러 전 단계) — 거의 발생하지 않음
  (error: unknown) => Promise.reject(error),
)

// ── ② 응답 인터셉터 — HTTP 에러 코드별 공통 처리 ────────────────────────────
/**
 * 스펙에 정의된 공통 에러 코드 처리 전략:
 *
 * ┌──────┬──────────────────┬──────────────────────────────────────────────────┐
 * │ HTTP │ errorCode        │ 처리 방식                                          │
 * ├──────┼──────────────────┼──────────────────────────────────────────────────┤
 * │ 400  │ BAD_REQUEST      │ MoaiApiError 로 전파 → 폼 유효성 검사 UI 처리     │
 * │ 401  │ UNAUTHORIZED     │ refreshToken 으로 재발급 시도 (아래 상세 설명)    │
 * │ 403  │ TOKEN_EXPIRED*   │ TOKEN_EXPIRED_CODES 매핑 시 갱신; 그 외 즉시 전파 │
 * │ 404  │ NOT_FOUND        │ MoaiApiError 로 전파 → "리소스 없음" 처리         │
 * │ 409  │ CONFLICT         │ MoaiApiError 로 전파 → "이미 존재" 처리           │
 * │ 500  │ INTERNAL_ERROR   │ MoaiApiError 로 전파 → "서버 오류" 알림           │
 * └──────┴──────────────────┴──────────────────────────────────────────────────┘
 */
api.interceptors.response.use(
  // ─ 성공 응답 (2xx) — 그대로 통과 ─────────────────────────────────────────
  (response: AxiosResponse) => response,

  // ─ 오류 응답 (4xx / 5xx) ─────────────────────────────────────────────────
  async (error: AxiosError<{ success?: false; message?: string; errorCode?: string; code?: string }>) => {
    const status    = error.response?.status
    const payload   = error.response?.data
    const errorCode = payload?.errorCode ?? payload?.code ?? 'UNKNOWN_ERROR'
    const message   = payload?.message ?? error.message

    // ── 401 UNAUTHORIZED: 토큰 만료 → refreshAndRetry ───────────────────────
    if (status === 401) {
      return refreshAndRetry(error, 401, errorCode, message)
    }

    // ── 403 FORBIDDEN ────────────────────────────────────────────────────────
    // TOKEN_EXPIRED_CODES 에 해당하는 errorCode 만 갱신을 시도한다.
    // 비즈니스 로직 403 (예: PREVIOUS_WEEK_NOT_COMPLETED, FORBIDDEN) 은
    // clearStoredAuth() / 리다이렉트 없이 에러를 즉시 전파한다.
    if (status === 403) {
      if (TOKEN_EXPIRED_CODES.has(errorCode)) {
        return refreshAndRetry(error, 403, errorCode, message || '해당 리소스에 대한 권한이 없습니다.')
      }
      console.log('[axios] Business logic 403 detected - skipping refresh:', errorCode)
      return Promise.reject(new MoaiApiError(403, errorCode, message || '해당 리소스에 대한 권한이 없습니다.'))
    }

    // ── 404 NOT_FOUND: 리소스 없음 ───────────────────────────────────────────
    // 예) 존재하지 않는 roomId, materialId, attemptId 조회 시
    // UI 레이어에서 catch 하여 "존재하지 않는 콘텐츠" 안내를 표시한다
    if (status === 404) {
      return Promise.reject(new MoaiApiError(404, errorCode, message || '요청한 리소스를 찾을 수 없습니다.'))
    }

    // ── 409 CONFLICT: 이미 존재 ──────────────────────────────────────────────
    // 예) 이미 가입된 login_id 로 회원가입 시도 시
    if (status === 409) {
      return Promise.reject(new MoaiApiError(409, errorCode, message || '이미 존재하는 리소스입니다.'))
    }

    // ── 500 INTERNAL_ERROR: 서버 내부 오류 ───────────────────────────────────
    // 예) AI 커리큘럼 생성 중 LLM 서버 장애 등
    // 사용자에게 "잠시 후 다시 시도해 주세요" 안내를 표시한다
    if (status === 500) {
      return Promise.reject(new MoaiApiError(500, errorCode, message || '서버 내부 오류입니다. 잠시 후 다시 시도해 주세요.'))
    }

    // ── 그 외 에러 (네트워크 단절, 타임아웃 등) ──────────────────────────────
    // MoaiApiError 로 감싸지 않고 원본 axios 에러를 그대로 전파
    return Promise.reject(error)
  },
)

export default api
