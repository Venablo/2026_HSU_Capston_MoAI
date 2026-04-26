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
 *     - 403: 권한 없음 — 오류 전파 (UI 레이어가 처리)
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
 * │ 403  │ FORBIDDEN        │ MoaiApiError 로 전파 → "권한 없음" 알림           │
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

    // ── 401 UNAUTHORIZED: 토큰 만료 → 자동 갱신 시도 ──────────────────────
    /**
     * 401 처리 흐름 (토큰 재발급 사이클):
     *
     *  [401 수신]
     *     │
     *     ▼
     *  refreshToken 이 localStorage 에 있는가?
     *     │ YES                         │ NO
     *     ▼                             ▼
     *  isRefreshing 중인가?         clearStoredAuth() → 로그인 이동
     *     │ NO         │ YES
     *     ▼            ▼
     *  갱신 요청     pendingQueue 에 추가하여
     *  POST /auth/   갱신 완료 후 자동 재시도
     *  refresh
     *     │ 성공                        │ 실패
     *     ▼                             ▼
     *  새 accessToken             clearStoredAuth() → 로그인 이동
     *  localStorage 업데이트
     *  pendingQueue 전체 재시도
     *  원본 요청 재시도 (사용자는 아무것도 모름)
     */
    if (status === 401) {
      const storedRefreshToken = localStorage.getItem(TOKEN_KEYS.refreshToken)

      // refreshToken 이 없으면 재발급이 불가능하므로 즉시 로그인 이동
      if (!storedRefreshToken) {
        clearStoredAuth()
        window.location.href = '/'
        return Promise.reject(new MoaiApiError(401, errorCode, message))
      }

      // 이미 다른 요청이 갱신 중이라면 이 요청은 큐에 넣고 대기
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingQueue.push({
            resolve: (newToken: string) => {
              // 갱신 완료 후 원본 요청에 새 토큰을 넣어 재시도
              if (error.config) {
                error.config.headers.Authorization = `Bearer ${newToken}`
                resolve(api(error.config))
              }
            },
            reject,
          })
        })
      }

      // 이 요청이 최초 갱신 시도 — isRefreshing 플래그 설정
      isRefreshing = true

      try {
        // POST /api/auth/refresh — 공개 엔드포인트이므로 인터셉터를 거치지 않도록
        // 원시 axios 를 사용한다 (api 인스턴스 사용 시 401 무한루프 가능).
        // 원시 axios 는 transformResponse 가 없으므로 deepCamelCase 를 수동 적용한다.
        const rawRefresh = await axios.post(
          `${import.meta.env.VITE_API_BASE_URL}/api/auth/refresh`,
          { refreshToken: storedRefreshToken },
          { headers: { 'Content-Type': 'application/json' } },
        )
        const refreshBody = deepCamelCase(rawRefresh.data) as {
          success: boolean
          data: { accessToken: string; expiresIn: number }
        }

        const newAccessToken = refreshBody.data.accessToken

        // 새 accessToken 을 localStorage 에 저장 (AuthContext 도 다음 렌더에서 동기화)
        localStorage.setItem(TOKEN_KEYS.accessToken, newAccessToken)

        // 대기 중이던 모든 요청에 새 토큰 전달 → 자동 재시도
        flushPendingQueue(newAccessToken)

        // 원본 요청도 새 토큰으로 재시도
        if (error.config) {
          error.config.headers.Authorization = `Bearer ${newAccessToken}`
          return api(error.config)
        }
      } catch (refreshError) {
        // refreshToken 도 만료되었거나 유효하지 않음 → 완전히 로그아웃 처리
        flushPendingQueue(null, refreshError)
        clearStoredAuth()
        window.location.href = '/'
        return Promise.reject(new MoaiApiError(401, 'REFRESH_FAILED', '세션이 만료되었습니다. 다시 로그인해 주세요.'))
      } finally {
        // 성공/실패 여부에 관계없이 플래그 초기화
        isRefreshing = false
      }
    }

    // ── 403 FORBIDDEN: 권한 없음 ─────────────────────────────────────────────
    // 예) 다른 사용자의 학습실에 접근 시도
    // UI 레이어에서 catch 하여 "접근 권한이 없습니다" 알림을 표시한다
    if (status === 403) {
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
