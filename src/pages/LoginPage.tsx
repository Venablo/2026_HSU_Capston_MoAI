import { useState } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Eye, Mic, Star } from 'lucide-react'
import '../styles/LoginPage.css'

interface Feature { icon: ReactNode; label: string }

export default function LoginPage() {
    const navigate = useNavigate()
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [loading, setLoading] = useState(false)

    const handleLogin = () => {
        setLoading(true)
        setTimeout(() => navigate('/main'), 800)
    }

    const FEATURES: Feature[] = [
        { icon: <Eye size={24} strokeWidth={1.5} />,  label: '행동 분석\n능동 가이드' },
        { icon: <Mic size={24} strokeWidth={1.5} />,  label: 'AI 대상\n거꾸로 학습' },
        { icon: <Star size={24} strokeWidth={1.5} />, label: '약점 보완\n지능형 매칭' },
    ]

    return (
        <div className="login">
            {/* Left */}
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

            {/* Right */}
            <div className="login__right">
                <div className="login__form-wrap animate-fade-in">
                    <div className="login__form-title">로그인</div>
                    <div className="login__form-sub">학습을 이어가려면 로그인하세요</div>

                    <div className="login__fields">
                        <div>
                            <label className="login__label">이메일</label>
                            <input
                                type="email"
                                className="login__input"
                                placeholder="kim@example.com"
                                value={email}
                                onChange={e => setEmail(e.target.value)}
                            />
                        </div>
                        <div>
                            <label className="login__label">비밀번호</label>
                            <input
                                type="password"
                                className="login__input"
                                placeholder="••••••••"
                                value={password}
                                onChange={e => setPassword(e.target.value)}
                                onKeyDown={e => e.key === 'Enter' && handleLogin()}
                            />
                        </div>
                    </div>

                    <button
                        className="login__btn-primary"
                        onClick={handleLogin}
                        disabled={loading}
                    >
                        {loading ? '로그인 중...' : '로그인'}
                    </button>

                    <button className="login__btn-ghost" onClick={handleLogin}>
                        게스트로 체험하기 →
                    </button>

                    <div className="login__signup-hint">
                        계정이 없으신가요?{' '}
                        <span className="login__signup-link">회원가입</span>
                    </div>
                </div>
            </div>
        </div>
    )
}
