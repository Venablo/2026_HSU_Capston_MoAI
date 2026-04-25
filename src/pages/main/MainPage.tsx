import { useState, useEffect } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, Settings, Eye, Mic, Star, Sparkles, BrainCircuit, BookOpen, ArrowRight, Loader2 } from 'lucide-react'
import OnboardingWizard from '../../components/OnboardingWizard'
import '../../styles/MainPage.css'
import { getLearningRooms } from '../../services/apiService'
import type { LearningRoomListItem } from '../../types/api'
import { useAuth } from '../../context/AuthContext'

interface Feature { icon: ReactNode; title: string; desc: string }

const FEATURES: Feature[] = [
    {
        icon:  <Eye size={32} strokeWidth={1.5} />,
        title: '행동 분석 능동 가이드',
        desc:  '사용자의 학습 패턴을 실시간으로 분석하여 집중력이 떨어지는 순간 적절한 개입을 제공합니다.',
    },
    {
        icon:  <Mic size={32} strokeWidth={1.5} />,
        title: 'AI 대상 거꾸로 학습',
        desc:  '배운 내용을 AI에게 직접 설명하며 개념을 완벽하게 내재화하는 메타인지 학습법을 지원합니다.',
    },
    {
        icon:  <Star size={32} strokeWidth={1.5} />,
        title: '약점 보완 지능형 매칭',
        desc:  '오답 데이터를 기반으로 취약한 개념을 추출하여 가장 효과적인 보충 문항을 자동 추천합니다.',
    },
]

export default function MainPage() {
    const navigate = useNavigate()
    const { nickname } = useAuth()
    const [showWizard, setShowWizard] = useState(false)

    const [activeRoom,   setActiveRoom]   = useState<LearningRoomListItem | null>(null)
    const [roomsLoading, setRoomsLoading] = useState(true)
    const [roomsError,   setRoomsError]   = useState<string | null>(null)

    useEffect(() => {
        let cancelled = false
        getLearningRooms()
            .then(rooms => {
                if (cancelled) return
                const active = rooms.find(r => r.status === 'active' || r.status === 'paused') ?? null
                setActiveRoom(active)
            })
            .catch(e    => { if (!cancelled) setRoomsError(e instanceof Error ? e.message : '학습실 목록을 불러오지 못했습니다.') })
            .finally(() => { if (!cancelled) setRoomsLoading(false) })
        return () => { cancelled = true }
    }, [])

    const displayName = nickname ?? ''
    const avatarChar  = displayName ? displayName.charAt(0).toUpperCase() : '?'

    return (
        <>
            {/* Topbar */}
            <header className="topbar">
                <h2 className="topbar__title">
                    {displayName ? `Welcome, ${displayName}!` : 'Welcome!'}
                </h2>
                <div className="topbar__actions">
                    <button className="topbar__icon-btn"><Bell size={20} strokeWidth={1.5} /></button>
                    <button className="topbar__icon-btn"><Settings size={20} strokeWidth={1.5} /></button>
                    <div className="topbar__avatar">{avatarChar}</div>
                </div>
            </header>

            <main className="main-page__content">
                {/* Hero */}
                <div className="hero animate-slide-up">
                    <div className="hero__deco-circle-lg" />
                    <div className="hero__deco-circle-sm" />

                    <div className="hero__body">
                        <div className="hero__badge">PERSONALIZED LEARNING</div>
                        <h1 className="hero__title">
                            방황은 끝, 이제 오직{' '}
                            <span className="hero__title-accent">당신만을<br />위한</span>{' '}
                            학습을 시작하세요.
                        </h1>
                        <p className="hero__desc">
                            AI가 학습자님의 목표와 실력을 분석하여 맞춤형 커리큘럼을 설계합니다.
                        </p>
                        <button
                            className="hero__cta"
                            style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}
                            onClick={() => setShowWizard(true)}
                        >
                            <Sparkles size={16} strokeWidth={1.5} />
                            AI 맞춤 커리큘럼 시작하기
                        </button>
                    </div>

                    <div className="hero__illustration" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--color-purple-500)' }}>
                        <BrainCircuit size={80} strokeWidth={1.5} />
                    </div>
                </div>

                {/* Section heading */}
                <div className="section-heading animate-fade-in delay-200">
                    <h2 className="section-heading__title">
                        MoAI가 당신의 성장을 돕는 3가지 방법
                    </h2>
                    <div className="section-heading__bar" />
                </div>

                {/* Feature cards */}
                <div className="feature-grid">
                    {FEATURES.map((f, i) => (
                        <div
                            key={i}
                            className={`feature-card animate-fade-in delay-${(i + 3) * 100}`}
                        >
                            <div className="feature-card__icon-wrap" style={{ color: 'var(--color-purple-500)' }}>
                                {f.icon}
                            </div>
                            <h3 className="feature-card__title">{f.title}</h3>
                            <p className="feature-card__desc">{f.desc}</p>
                        </div>
                    ))}
                </div>

                {/* Active study */}
                <div className="animate-fade-in delay-500">
                    <h3 className="active-study__label">진행 중인 학습</h3>

                    {roomsLoading ? (
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '20px 0', color: 'var(--color-text-secondary)' }}>
                            <Loader2 size={16} strokeWidth={1.5} className="animate-spin" />
                            <span style={{ fontSize: '13px' }}>학습실 불러오는 중...</span>
                        </div>
                    ) : roomsError ? (
                        <div style={{ padding: '20px 0', fontSize: '13px', color: '#ef4444' }}>
                            ⚠ {roomsError}
                        </div>
                    ) : !activeRoom ? (
                        <div style={{ padding: '20px 0', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
                            진행 중인 학습실이 없습니다. AI 맞춤 커리큘럼을 시작해보세요!
                        </div>
                    ) : (
                        <div
                            className="active-study__card"
                            onClick={() => navigate(`/study/${activeRoom.roomId}/classroom`)}
                        >
                            <div className="active-study__emoji" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--color-purple-500)' }}>
                                <BookOpen size={28} strokeWidth={1.5} />
                            </div>
                            <div className="active-study__info">
                                <div className="active-study__name">{activeRoom.subject}</div>
                                <div className="active-study__meta">
                                    Week {activeRoom.currentWeek} · {activeRoom.completionRate}% 완료
                                </div>
                                <div className="active-study__bar-track">
                                    <div className="active-study__bar-fill" style={{ width: `${activeRoom.completionRate}%` }} />
                                </div>
                            </div>
                            <button className="active-study__btn" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                                학습 계속
                                <ArrowRight size={14} strokeWidth={1.5} />
                            </button>
                        </div>
                    )}
                </div>
            </main>

            {showWizard && <OnboardingWizard onClose={() => setShowWizard(false)} />}
        </>
    )
}
