/**
 * ============================================================================
 * LoginPage.tsx  —  로그인 화면
 * ============================================================================
 *
 * 백엔드 연동 지점:
 *   POST /api/auth/login  (apiService.login)
 *
 * 인증 흐름:
 *   1. 사용자가 아이디(login_id) + 비밀번호(password) 입력 후 "로그인" 클릭
 *   2. 백엔드 POST /api/auth/login 호출
 *   3. 응답 success: true  → saveAuth() 로 토큰 저장 → 성공 토스트 → /main 이동
 *      응답 success: false → unwrap() 이 Error throw  → 실패 토스트 + 인라인 에러 표시
 *
 * 화면 전환 조건:
 *   navigate('/main') 는 반드시 API success: true 확인 후에만 실행된다.
 *   (setTimeout 으로 토스트를 800ms 표시한 뒤 전환)
 * ============================================================================
 */

import { useState } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Mic, Star } from 'lucide-react'

// 백엔드 POST /api/auth/login 연동 지점
import { login } from '../services/apiService'
// 로그인 성공 후 토큰 저장 — localStorage + AuthContext 동기화
import { useAuth } from '../context/AuthContext'
// 성공/실패 토스트 알림
import { useToast } from '../hooks/useToast'
import Toast from '../components/Toast'

import '../styles/LoginPage.css'

interface Feature { icon: ReactNode; label: string }

export default function LoginPage() {
    const navigate   = useNavigate()
    const { saveAuth } = useAuth()
    const { toast, showToast } = useToast()

    // ── 폼 상태 ──────────────────────────────────────────────────────────────
    const [loginId,  setLoginId]  = useState('')
    const [password, setPassword] = useState('')
    const [showPw,   setShowPw]   = useState(false)
    const [loading,  setLoading]  = useState(false)
    // 인라인 에러 — 입력 필드 바로 아래 표시용 (토스트와 병행)
    const [fieldError, setFieldError] = useState('')

    // ── 백엔드 POST /api/auth/login 연동 지점 ────────────────────────────────
    const handleLogin = async () => {
        // 클라이언트 사이드 필수값 검증 — API 호출 전 차단
        if (!loginId.trim() || !password) {
            setFieldError('아이디와 비밀번호를 입력해 주세요.')
            return
        }

        setLoading(true)
        setFieldError('')

        try {
            /**
             * ✅ 백엔드 POST /api/auth/login 연동 지점
             *
             * 요청 바디:
             *   {
             *     "login_id": string,   // 회원가입 시 등록한 아이디
             *     "password": string    // 비밀번호 (평문 — HTTPS 전송, 백엔드 BCrypt 검증)
             *   }
             *
             * 성공 응답 (success: true):
             *   {
             *     "accessToken":  string,  // 이후 모든 API 요청 Authorization 헤더에 사용
             *     "refreshToken": string,  // accessToken 만료 시 재발급용
             *     "expiresIn":    number,  // accessToken 유효기간 (초, 예: 3600)
             *     "userId":       string,  // 사용자 UUID
             *     "nickname":     string   // 화면 표시용 닉네임
             *   }
             *
             * 실패 응답 → unwrap() 이 Error throw → catch 블록으로 이동
             *   - 401: 아이디 또는 비밀번호 불일치
             *   - 400: 요청 형식 오류
             */
            const result = await login({ login_id: loginId, password })

            // ── 토큰 저장 ────────────────────────────────────────────────────
            // saveAuth() 는 accessToken, refreshToken, userId, nickname 을
            // localStorage 와 React AuthContext 에 동시 저장한다.
            // 이후 axios 인터셉터가 localStorage 에서 자동으로 토큰을 읽어 헤더에 첨부.
            saveAuth(result)

            // ── 성공 토스트 + 화면 전환 ──────────────────────────────────────
            // ✅ 화면 전환은 API success: true 확인 후에만 실행됨
            // (실패 시 unwrap() 이 throw 하므로 이 라인에 도달하지 않는다)
            showToast('success', `환영합니다, ${result.nickname}님!`)
            setTimeout(() => navigate('/main'), 800)

        } catch (e: unknown) {
            // ── 실패 처리 ────────────────────────────────────────────────────
            // HTTP 4xx/5xx: axios 인터셉터가 MoaiApiError 로 변환
            // success: false: unwrap() 이 envelope.message 로 throw
            const msg = e instanceof Error ? e.message : '로그인에 실패했습니다.'
            showToast('error', msg)
            setFieldError(msg)

        } finally {
            setLoading(false)
        }
    }

    const FEATURES: Feature[] = [
        { icon: <Eye  size={24} strokeWidth={1.5} />, label: '행동 분석\n능동 가이드' },
        { icon: <Mic  size={24} strokeWidth={1.5} />, label: 'AI 대상\n거꾸로 학습'  },
        { icon: <Star size={24} strokeWidth={1.5} />, label: '약점 보완\n지능형 매칭' },
    ]

    return (
        <>
            {/* ── 성공/실패 토스트 — 화면 상단 중앙 고정 ── */}
            <Toast toast={toast} />

            <div className="login">
                {/* ── 좌측 브랜드 패널 ── */}
                <div className="login__left">
                    <div className="animate-slide-up">
                        <div className="login__brand-name">MoAI</div>
                        <div className="login__brand-sub">INTELLIGENT TUTOR</div>

                        <h1 className="login__headline">
                            방황은 끝,<br />
                            <span className="login__headline-accent">당신만을 위한</span><br />
                            AI 학습이 시작됩니다.
                        </h1>
                        <p className="login__description">
                            행동 분석 · 거꾸로 학습 · 지능형 멘토 매칭<br />
                            MoAI가 당신의 목표를 현실로 만듭니다.
                        </p>

                        <div className="login__features">
                            {FEATURES.map((f, i) => (
                                <div key={i} className={`login__feature-card animate-fade-in delay-${i + 2}00`}>
                                    <div className="login__feature-icon" style={{ color: 'var(--color-purple-500)' }}>
                                        {f.icon}
                                    </div>
                                    <div className="login__feature-label">{f.label}</div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>

                {/* ── 우측 폼 패널 ── */}
                <div className="login__right">
                    <div className="login__form-wrap animate-fade-in">
                        <div className="login__form-title">로그인</div>
                        <div className="login__form-sub">아이디와 비밀번호를 입력하세요.</div>

                        <div className="login__fields">
                            {/* 아이디 — 백엔드 필드명: login_id */}
                            <div>
                                <label className="login__label">아이디</label>
                                <input
                                    type="text"
                                    className="login__input"
                                    placeholder="로그인 ID를 입력하세요"
                                    value={loginId}
                                    onChange={e => { setLoginId(e.target.value); setFieldError('') }}
                                    autoComplete="username"
                                />
                            </div>

                            {/* 비밀번호 — 백엔드 필드명: password */}
                            <div>
                                <label className="login__label">비밀번호</label>
                                <div style={{ position: 'relative' }}>
                                    <input
                                        type={showPw ? 'text' : 'password'}
                                        className="login__input"
                                        placeholder="비밀번호를 입력하세요"
                                        value={password}
                                        onChange={e => { setPassword(e.target.value); setFieldError('') }}
                                        onKeyDown={e => e.key === 'Enter' && handleLogin()}
                                        style={{ paddingRight: '44px' }}
                                        autoComplete="current-password"
                                    />
                                    <button
                                        type="button"
                                        className="login__pw-toggle"
                                        onClick={() => setShowPw(v => !v)}
                                        aria-label={showPw ? '비밀번호 숨기기' : '비밀번호 보기'}
                                    >
                                        {showPw ? <EyeOff size={16} /> : <Eye size={16} />}
                                    </button>
                                </div>
                            </div>
                        </div>

                        {/* 인라인 필드 에러 — 토스트와 병행 표시 */}
                        {fieldError && <p className="login__error">{fieldError}</p>}

                        {/* 로그인 — 백엔드 POST /api/auth/login 호출 */}
                        <button
                            className="login__btn-primary"
                            onClick={handleLogin}
                            disabled={loading}
                        >
                            {loading ? '로그인 중...' : '로그인'}
                        </button>

                        {/* 회원가입 — /register 이동 (API 호출 없음) */}
                        <button
                            className="login__btn-ghost"
                            onClick={() => navigate('/register')}
                        >
                            회원가입하기 →
                        </button>

                    </div>
                </div>
            </div>
        </>
    )
}
