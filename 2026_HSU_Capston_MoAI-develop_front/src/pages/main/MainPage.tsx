import { useState, useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    Bell, Eye, Mic, Star, Sparkles, BrainCircuit, BookOpen, ArrowRight,
    Loader2, Moon, Sun, X, Check, LogOut, UserCircle, Search,
} from 'lucide-react'
import OnboardingWizard from '../../components/OnboardingWizard'
import '../../styles/MainPage.css'
import { getLearningRooms, getNotifications, markNotificationRead, updateProfile, logout } from '../../services/apiService'
import type { LearningRoomListItem, NotificationItem } from '../../types/api'
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
    const { nickname, refreshToken, clearAuth } = useAuth()
    const [showWizard, setShowWizard] = useState(false)

    const [rooms,        setRooms]        = useState<LearningRoomListItem[]>([])
    const [roomsLoading, setRoomsLoading] = useState(true)
    const [roomsError,   setRoomsError]   = useState<string | null>(null)

    useEffect(() => {
        let cancelled = false
        getLearningRooms()
            .then(data => { if (!cancelled) setRooms(data) })
            .catch(e   => { if (!cancelled) setRoomsError(e instanceof Error ? e.message : '학습실 목록을 불러오지 못했습니다.') })
            .finally(() => { if (!cancelled) setRoomsLoading(false) })
        return () => { cancelled = true }
    }, [])

    const activeRoom = rooms.find(r => r.status === 'active' || r.status === 'paused') ?? null
    const displayName = nickname ?? ''
    const avatarChar  = displayName ? displayName.charAt(0).toUpperCase() : '?'

    // ── 검색 ─────────────────────────────────────────────────────────────────
    const [searchValue, setSearchValue] = useState('')
    const [searchFocus, setSearchFocus] = useState(false)
    const searchRef = useRef<HTMLDivElement>(null)
    const normalizedSearch = searchValue.trim().toLowerCase()
    const filteredRooms = normalizedSearch
        ? rooms.filter(r => r.subject.toLowerCase().includes(normalizedSearch))
        : []
    useEffect(() => {
        if (!searchFocus || !searchValue.trim()) return
        const handler = (e: MouseEvent) => {
            if (searchRef.current && !searchRef.current.contains(e.target as Node)) setSearchFocus(false)
        }
        document.addEventListener('mousedown', handler)
        return () => document.removeEventListener('mousedown', handler)
    }, [searchFocus, searchValue])

    // ── 다크 모드 ─────────────────────────────────────────────────────────────
    const [darkMode, setDarkMode] = useState(() => localStorage.getItem('theme') === 'dark')
    useEffect(() => {
        if (darkMode) {
            document.documentElement.classList.add('dark')
            localStorage.setItem('theme', 'dark')
        } else {
            document.documentElement.classList.remove('dark')
            localStorage.setItem('theme', 'light')
        }
    }, [darkMode])
    const handleToggleDark = () => {
        const next = !darkMode
        setDarkMode(next)
        updateProfile({ themePreference: next ? 'dark' : 'light' }).catch(() => {})
    }

    // ── 알림 ─────────────────────────────────────────────────────────────────
    const [notifOpen,      setNotifOpen]      = useState(false)
    const [notifications,  setNotifications]  = useState<NotificationItem[]>([])
    const [notifLoading,   setNotifLoading]   = useState(false)
    const notifRef = useRef<HTMLDivElement>(null)
    const unreadCount = notifications.filter(n => !n.isRead).length

    const handleOpenNotif = () => {
        const opening = !notifOpen
        setNotifOpen(opening)
        if (opening) {
            setNotifLoading(true)
            getNotifications()
                .then(setNotifications)
                .catch(() => {})
                .finally(() => setNotifLoading(false))
        }
    }
    const handleMarkRead = (notificationId: string) => {
        markNotificationRead(notificationId)
            .then(() => setNotifications(prev =>
                prev.map(n => n.notificationId === notificationId ? { ...n, isRead: true } : n)))
            .catch(() => {})
    }
    useEffect(() => {
        if (!notifOpen) return
        const handler = (e: MouseEvent) => {
            if (notifRef.current && !notifRef.current.contains(e.target as Node)) setNotifOpen(false)
        }
        document.addEventListener('mousedown', handler)
        return () => document.removeEventListener('mousedown', handler)
    }, [notifOpen])

    // ── 프로필 드롭다운 ───────────────────────────────────────────────────────
    const [profileOpen, setProfileOpen] = useState(false)
    const profileRef = useRef<HTMLDivElement>(null)
    const handleLogout = async () => {
        try { if (refreshToken) await logout({ refreshToken }) } catch {}
        clearAuth()
        navigate('/')
    }
    useEffect(() => {
        if (!profileOpen) return
        const handler = (e: MouseEvent) => {
            if (profileRef.current && !profileRef.current.contains(e.target as Node)) setProfileOpen(false)
        }
        document.addEventListener('mousedown', handler)
        return () => document.removeEventListener('mousedown', handler)
    }, [profileOpen])

    return (
        <>
            {/* Topbar */}
            <header className="topbar">
                <h2 className="topbar__title">
                    {displayName ? `Welcome, ${displayName}!` : 'Welcome!'}
                </h2>
                <div className="topbar__actions">
                    {/* 검색 */}
                    <div ref={searchRef} style={{ position: 'relative' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', background: 'var(--color-purple-50)', borderRadius: '10px', padding: '8px 14px' }}>
                            <Search size={16} strokeWidth={1.5} style={{ color: 'var(--color-text-secondary)' }} />
                            <input
                                placeholder="학습실 검색..."
                                value={searchValue}
                                onChange={e => setSearchValue(e.target.value)}
                                onFocus={() => setSearchFocus(true)}
                                onKeyDown={e => {
                                    if (e.key === 'Enter' && filteredRooms[0]) {
                                        navigate(`/study/${filteredRooms[0].roomId}/classroom`)
                                        setSearchValue('')
                                        setSearchFocus(false)
                                    }
                                }}
                                style={{ border: 'none', background: 'transparent', outline: 'none', fontSize: '13px', width: '160px', color: 'var(--color-text-primary)', fontFamily: 'inherit' }}
                            />
                            {searchValue && (
                                <button onClick={() => { setSearchValue(''); setSearchFocus(false) }}
                                    style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, color: 'var(--color-text-secondary)', display: 'flex' }}>
                                    <X size={14} strokeWidth={1.5} />
                                </button>
                            )}
                        </div>
                        {searchFocus && normalizedSearch && (
                            <div style={{
                                position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 200,
                                background: 'var(--color-surface, #fff)', border: '1px solid var(--color-border)',
                                borderRadius: '10px', boxShadow: '0 8px 24px rgba(0,0,0,.12)',
                                maxHeight: '240px', overflowY: 'auto', marginTop: '4px',
                            }}>
                                {filteredRooms.length === 0 ? (
                                    <div style={{ padding: '10px 14px', fontSize: '12px', color: 'var(--color-text-secondary)' }}>검색 결과가 없습니다.</div>
                                ) : filteredRooms.map(r => (
                                    <button key={r.roomId}
                                        onClick={() => { navigate(`/study/${r.roomId}/classroom`); setSearchValue(''); setSearchFocus(false) }}
                                        style={{
                                            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                            width: '100%', padding: '9px 14px', background: 'none', border: 'none',
                                            textAlign: 'left', cursor: 'pointer', fontSize: '13px',
                                            color: 'var(--color-text-primary)',
                                        }}>
                                        <span>{r.subject}</span>
                                        <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginLeft: '8px' }}>{r.completionRate}%</span>
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* 다크 모드 토글 */}
                    <button className="topbar__icon-btn" onClick={handleToggleDark} title={darkMode ? '라이트 모드' : '다크 모드'}>
                        {darkMode ? <Sun size={18} strokeWidth={1.5} /> : <Moon size={18} strokeWidth={1.5} />}
                    </button>

                    {/* 알림 */}
                    <div ref={notifRef} style={{ position: 'relative' }}>
                        <button className="topbar__icon-btn" onClick={handleOpenNotif} style={{ position: 'relative' }}>
                            <Bell size={18} strokeWidth={1.5} />
                            {unreadCount > 0 && (
                                <span style={{
                                    position: 'absolute', top: '2px', right: '2px',
                                    background: '#ef4444', color: '#fff',
                                    fontSize: '9px', fontWeight: 700,
                                    minWidth: '14px', height: '14px', borderRadius: '99px',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '0 3px',
                                }}>
                                    {unreadCount > 9 ? '9+' : unreadCount}
                                </span>
                            )}
                        </button>
                        {notifOpen && (
                            <div style={{
                                position: 'absolute', top: '100%', right: 0, zIndex: 200,
                                background: 'var(--color-surface, #fff)', border: '1px solid var(--color-border)',
                                borderRadius: '12px', boxShadow: '0 8px 24px rgba(0,0,0,.15)',
                                width: '320px', maxHeight: '400px', overflowY: 'auto', marginTop: '6px',
                            }}>
                                <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--color-border)', fontWeight: 700, fontSize: '13px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                    <span>알림</span>
                                    <button onClick={() => setNotifOpen(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--color-text-secondary)', display: 'flex' }}><X size={14} /></button>
                                </div>
                                {notifLoading ? (
                                    <div style={{ padding: '24px', display: 'flex', justifyContent: 'center' }}><Loader2 size={18} className="animate-spin" /></div>
                                ) : notifications.length === 0 ? (
                                    <div style={{ padding: '24px 16px', fontSize: '13px', color: 'var(--color-text-secondary)', textAlign: 'center' }}>알림이 없습니다.</div>
                                ) : notifications.map(n => (
                                    <div key={n.notificationId} style={{
                                        padding: '12px 16px', borderBottom: '1px solid var(--color-border)',
                                        background: n.isRead ? 'transparent' : 'var(--color-purple-50)',
                                        display: 'flex', alignItems: 'flex-start', gap: '10px',
                                    }}>
                                        <div style={{ flex: 1 }}>
                                            <div style={{ fontSize: '12px', color: 'var(--color-text-primary)', lineHeight: 1.5 }}>{n.message}</div>
                                            <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '4px' }}>{new Date(n.createdAt).toLocaleString('ko-KR')}</div>
                                        </div>
                                        {!n.isRead && (
                                            <button onClick={() => handleMarkRead(n.notificationId)} title="읽음 처리"
                                                style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--color-purple-500)', display: 'flex', flexShrink: 0, marginTop: '2px' }}>
                                                <Check size={14} strokeWidth={2} />
                                            </button>
                                        )}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* 아바타 / 프로필 드롭다운 */}
                    <div ref={profileRef} style={{ position: 'relative' }}>
                        <div className="topbar__avatar" onClick={() => setProfileOpen(v => !v)} style={{ cursor: 'pointer' }}>
                            {avatarChar}
                        </div>
                        {profileOpen && (
                            <div style={{
                                position: 'absolute', top: '100%', right: 0, zIndex: 200,
                                background: 'var(--color-surface, #fff)', border: '1px solid var(--color-border)',
                                borderRadius: '12px', boxShadow: '0 8px 24px rgba(0,0,0,.15)',
                                minWidth: '160px', marginTop: '6px', overflow: 'hidden',
                            }}>
                                <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--color-border)' }}>
                                    <div style={{ fontWeight: 700, fontSize: '13px', color: 'var(--color-text-primary)' }}>{nickname ?? '사용자'}</div>
                                </div>
                                <button onClick={() => { setProfileOpen(false); navigate('/my-page') }}
                                    style={{
                                        display: 'flex', alignItems: 'center', gap: '8px',
                                        width: '100%', padding: '11px 16px', background: 'none', border: 'none',
                                        textAlign: 'left', cursor: 'pointer', fontSize: '13px',
                                        color: 'var(--color-text-primary)',
                                    }}>
                                    <UserCircle size={14} strokeWidth={1.5} />
                                    마이페이지
                                </button>
                                <button onClick={handleLogout}
                                    style={{
                                        display: 'flex', alignItems: 'center', gap: '8px',
                                        width: '100%', padding: '11px 16px', background: 'none', border: 'none',
                                        textAlign: 'left', cursor: 'pointer', fontSize: '13px',
                                        color: '#ef4444',
                                    }}>
                                    <LogOut size={14} strokeWidth={1.5} />
                                    로그아웃
                                </button>
                            </div>
                        )}
                    </div>
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
