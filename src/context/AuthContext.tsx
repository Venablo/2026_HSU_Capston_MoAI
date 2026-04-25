/**
 * ============================================================================
 * src/context/AuthContext.tsx  —  전역 인증 상태 관리
 * ============================================================================
 *
 * 역할:
 *   로그인 후 발급받은 JWT 토큰과 사용자 정보를 앱 전역에서 공유한다.
 *   React Context 를 사용하며, Zustand 없이 동일한 역할을 수행한다.
 *
 * 저장 전략:
 *   ┌─────────────────────────────────────────────────────────────────────────┐
 *   │ React state (useState)  │ 현재 렌더 사이클의 값. 컴포넌트 리렌더 트리거. │
 *   │ localStorage            │ 브라우저 탭 닫기/새로고침 후에도 유지.          │
 *   └─────────────────────────────────────────────────────────────────────────┘
 *   두 저장소는 saveAuth / clearAuth 에서 항상 동기화된다.
 *   axios 인터셉터는 React lifecycle 밖에서 실행되므로 localStorage 를 직접 읽는다.
 *
 * 연결 관계:
 *   LoginPage → login() → saveAuth()   → localStorage + React state 업데이트
 *   모든 API 요청 → [axios 요청 인터셉터] → localStorage.getItem('accessToken') 읽기
 *   401 수신 → [axios 응답 인터셉터] → refreshToken 재발급 시도
 *              재발급 실패 → clearStoredAuth() → window.location.href = '/'
 *                            (AuthContext 의 clearAuth 와 독립적으로 동작)
 * ============================================================================
 */

import {
  createContext,
  useContext,
  useState,
  useCallback,
  type ReactNode,
} from 'react'
import type { LoginResponse } from '../types/api'

// ─────────────────────────────────────────────────────────────────────────────
// 타입 정의
// ─────────────────────────────────────────────────────────────────────────────

/**
 * AuthState — React state 에 보관되는 인증 데이터
 *
 * 모든 필드는 null 일 수 있다:
 *   - 앱 최초 진입 시 (localStorage 에 토큰이 없는 경우)
 *   - 로그아웃 후
 */
interface AuthState {
  /** POST /api/auth/login 응답의 accessToken. axios 인터셉터가 헤더에 첨부. */
  accessToken:  string | null
  /** accessToken 만료 시 재발급에 사용. 로그아웃 시 서버 측도 무효화. */
  refreshToken: string | null
  /** 사용자 UUID (예: "a0000000-0000-0000-0000-000000000001") */
  userId:       string | null
  /** 화면 표시용 닉네임 (예: "코딩왕") */
  nickname:     string | null
}

/**
 * AuthContextValue — useAuth() 훅이 반환하는 전체 인터페이스
 *
 * isAuthenticated: accessToken 존재 여부. 라우트 가드에 활용 가능.
 * saveAuth:        로그인 성공 후 호출. 토큰을 state + localStorage 에 저장.
 * clearAuth:       로그아웃 시 호출. 토큰을 state + localStorage 에서 삭제.
 * updateAccessToken: accessToken 만 교체. axios 인터셉터가 토큰 갱신 후 호출하는 용도.
 */
interface AuthContextValue extends AuthState {
  isAuthenticated: boolean
  saveAuth: (data: LoginResponse) => void
  clearAuth: () => void
  updateAccessToken: (newToken: string) => void
}

// ─────────────────────────────────────────────────────────────────────────────
// localStorage 키 상수
// ─────────────────────────────────────────────────────────────────────────────
/**
 * axios.ts 의 TOKEN_KEYS 와 반드시 동일한 값을 사용해야 한다.
 * 두 파일이 같은 키로 localStorage 를 읽고 쓰기 때문이다.
 * 키 이름을 변경할 경우 axios.ts 도 함께 수정해야 한다.
 */
const STORAGE_KEYS = {
  accessToken:  'accessToken',
  refreshToken: 'refreshToken',
  userId:       'userId',
  nickname:     'nickname',
} as const

// ─────────────────────────────────────────────────────────────────────────────
// 초기 상태 로더
// ─────────────────────────────────────────────────────────────────────────────
/**
 * readFromStorage  —  앱 시작 시 localStorage 에서 인증 상태를 복원한다.
 *
 * 사용자가 로그인 후 브라우저를 닫았다가 다시 열면 localStorage 에 토큰이
 * 남아있으므로 이 함수로 상태를 복원한다.
 * 모든 값이 null 이면 비로그인 상태로 처리된다.
 */
function readFromStorage(): AuthState {
  return {
    accessToken:  localStorage.getItem(STORAGE_KEYS.accessToken),
    refreshToken: localStorage.getItem(STORAGE_KEYS.refreshToken),
    userId:       localStorage.getItem(STORAGE_KEYS.userId),
    nickname:     localStorage.getItem(STORAGE_KEYS.nickname),
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Context 생성
// ─────────────────────────────────────────────────────────────────────────────
/**
 * null 로 초기화한다. useAuth() 에서 null 여부를 확인해 Provider 외부 사용을 방지.
 */
const AuthContext = createContext<AuthContextValue | null>(null)

// ─────────────────────────────────────────────────────────────────────────────
// Provider
// ─────────────────────────────────────────────────────────────────────────────
/**
 * AuthProvider
 *
 * main.tsx 에서 앱 최상위에 감싸야 한다:
 *   <AuthProvider>
 *     <App />
 *   </AuthProvider>
 *
 * 이렇게 하면 모든 컴포넌트에서 useAuth() 로 인증 상태에 접근할 수 있다.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  // ── 초기 상태: localStorage 에서 복원 ──────────────────────────────────────
  // useState 의 인자로 함수를 전달하면 최초 렌더에만 실행된다 (lazy initialization).
  // readFromStorage 를 직접 전달하면 매 렌더마다 호출되지 않는다.
  const [auth, setAuth] = useState<AuthState>(readFromStorage)

  // ── saveAuth ──────────────────────────────────────────────────────────────
  /**
   * saveAuth  —  로그인 성공 후 호출
   *
   * 처리 순서:
   *   1. localStorage 에 4개 항목 저장 (axios 인터셉터가 즉시 사용 가능)
   *   2. React state 업데이트 (컴포넌트 리렌더 트리거)
   *
   * 호출 시점 (LoginPage.tsx):
   *   const result = await login({ login_id, password })
   *   saveAuth(result)    ← 이 시점부터 모든 API 요청에 토큰이 자동 첨부됨
   *   navigate('/main')
   *
   * useCallback 사용 이유:
   *   이 함수를 자식 컴포넌트의 prop 으로 전달할 때 불필요한 리렌더를 방지.
   */
  const saveAuth = useCallback((data: LoginResponse) => {
    // localStorage 에 먼저 저장 — axios 인터셉터가 동기적으로 읽을 수 있도록
    // Backend returns snake_case keys: access_token, refresh_token
    localStorage.setItem(STORAGE_KEYS.accessToken,  data.access_token)
    localStorage.setItem(STORAGE_KEYS.refreshToken, data.refresh_token)
    localStorage.setItem(STORAGE_KEYS.userId,       data.userId)
    localStorage.setItem(STORAGE_KEYS.nickname,     data.nickname)

    // React state 업데이트 — 컴포넌트의 isAuthenticated 등이 즉시 갱신됨
    setAuth({
      accessToken:  data.access_token,
      refreshToken: data.refresh_token,
      userId:       data.userId,
      nickname:     data.nickname,
    })
  }, [])

  // ── clearAuth ─────────────────────────────────────────────────────────────
  /**
   * clearAuth  —  로그아웃 시 호출
   *
   * 처리 순서:
   *   1. localStorage 의 4개 항목 삭제
   *   2. React state 를 null 로 초기화
   *
   * 호출 시점:
   *   - 사용자가 로그아웃 버튼 클릭 후 logout() API 호출 성공 시
   *   - axios 응답 인터셉터가 401 + refreshToken 재발급 실패 시
   *     (이 경우 인터셉터가 localStorage 를 직접 정리하고 window.location.href = '/' 로 이동)
   *
   * ⚠️  주의: 인터셉터에서 발생하는 자동 로그아웃은 React 바깥에서 일어나므로
   *          clearAuth 가 호출되지 않아도 localStorage 는 이미 정리된다.
   *          다음에 앱을 열 때 readFromStorage 가 null 을 반환하게 된다.
   */
  const clearAuth = useCallback(() => {
    Object.values(STORAGE_KEYS).forEach(key => localStorage.removeItem(key))
    setAuth({ accessToken: null, refreshToken: null, userId: null, nickname: null })
  }, [])

  // ── updateAccessToken ─────────────────────────────────────────────────────
  /**
   * updateAccessToken  —  accessToken 만 교체
   *
   * 사용 시나리오:
   *   axios 인터셉터가 401 수신 후 refreshToken 으로 새 accessToken 을 발급받으면
   *   localStorage 는 인터셉터가 직접 업데이트한다.
   *   그 후 이 함수를 호출해 React state 도 동기화시킨다.
   *   (refreshToken 과 사용자 정보는 변경되지 않으므로 accessToken 만 교체)
   *
   * 호출 위치:
   *   현재는 인터셉터가 localStorage 만 업데이트하고 React state 는 업데이트하지 않는다.
   *   인터셉터가 React Context 에 직접 접근할 수 없기 때문이다.
   *   다음 useAuth() 를 사용하는 컴포넌트 렌더 시 readFromStorage 로 동기화된다.
   *   선제적 동기화가 필요한 경우 이 함수를 사용한다.
   */
  const updateAccessToken = useCallback((newToken: string) => {
    localStorage.setItem(STORAGE_KEYS.accessToken, newToken)
    setAuth(prev => ({ ...prev, accessToken: newToken }))
  }, [])

  return (
    <AuthContext.Provider
      value={{
        ...auth,
        // accessToken 이 null 이 아니면 인증된 상태로 간주
        isAuthenticated: auth.accessToken !== null,
        saveAuth,
        clearAuth,
        updateAccessToken,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// 커스텀 훅
// ─────────────────────────────────────────────────────────────────────────────
/**
 * useAuth  —  인증 상태에 접근하는 커스텀 훅
 *
 * 반드시 <AuthProvider> 내부의 컴포넌트에서만 사용해야 한다.
 * Provider 외부에서 호출하면 명확한 에러 메시지와 함께 throw 한다.
 *
 * 사용 예시:
 *   // 로그인 여부 확인
 *   const { isAuthenticated, nickname } = useAuth()
 *
 *   // 로그인 처리
 *   const { saveAuth } = useAuth()
 *   const result = await login(credentials)
 *   saveAuth(result)
 *
 *   // 로그아웃 처리
 *   const { clearAuth, refreshToken } = useAuth()
 *   await logout({ refreshToken: refreshToken! })
 *   clearAuth()
 *   navigate('/')
 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth() 는 <AuthProvider> 내부에서만 사용할 수 있습니다.')
  }
  return ctx
}
