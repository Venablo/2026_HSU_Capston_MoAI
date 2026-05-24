import { useState, useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    Bell, Eye, Mic, Star, Sparkles, BrainCircuit, BookOpen, ArrowRight,
    Loader2, Moon, Sun, X, Check, LogOut, UserCircle, Search,
} from 'lucide-react'
import OnboardingWizard from '../../components/OnboardingWizard'
import StudyMatchDropdown from '../../components/StudyMatchDropdown'
import '../../styles/MainPage.css'
import { useTheme } from '../../context/ThemeContext'
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
    const { darkMode, toggleDark } = useTheme()
    const handleToggleDark = () => {
        toggleDark()
        updateProfile({ themePreference: !darkMode ? 'dark' : 'light' }).catch(() => {})
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
                    <div ref={searchRef} className="mp-relative">
                        <div className="mp-search-wrap">
                            <Search size={16} strokeWidth={1.5} className="mp-search-icon" />
                            <input
                                placeholder="학습실 검색..."
                                value={searchValue}
                                onChange={e => setSearchValue(e.target.value)}
                                onFocus={() => setSearchFocus(true)}
                                onKeyDown={e => {
                                    if (e.key === 'Enter' && filteredRooms[0]) {
                                        navigate(`/study/${filteredRooms[0].roomId}/curriculum`, { state: { subject: filteredRooms[0].subject, level: filteredRooms[0].level, completionRate: filteredRooms[0].completionRate } })
                                        setSearchValue('')
                                        setSearchFocus(false)
                                    }
                                }}
                                className="mp-search-input"
                            />
                            {searchValue && (
                                <button onClick={() => { setSearchValue(''); setSearchFocus(false) }}
                                    className="mp-search-clear-btn">
                                    <X size={14} strokeWidth={1.5} />
                                </button>
                            )}
                        </div>
                        {searchFocus && normalizedSearch && (
                            <div className="mp-search-dropdown">
                                {filteredRooms.length === 0 ? (
                                    <div className="mp-search-empty">검색 결과가 없습니다.</div>
                                ) : filteredRooms.map(r => (
                                    <button key={r.roomId}
                                        onClick={() => { navigate(`/study/${r.roomId}/curriculum`, { state: { subject: r.subject, level: r.level, completionRate: r.completionRate } }); setSearchValue(''); setSearchFocus(false) }}
                                        className="mp-search-result-btn">
                                        <span>{r.subject}</span>
                                        <span className="mp-search-result-rate">{r.completionRate}%</span>
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* 스터디 매칭 드롭다운 */}
                    <StudyMatchDropdown />

                    {/* 다크 모드 토글 */}
                    <button className="topbar__icon-btn" onClick={handleToggleDark} title={darkMode ? '라이트 모드' : '다크 모드'}>
                        {darkMode ? <Sun size={18} strokeWidth={1.5} /> : <Moon size={18} strokeWidth={1.5} />}
                    </button>

                    {/* 알림 */}
                    <div ref={notifRef} className="mp-relative">
                        <button className="topbar__icon-btn mp-notif-btn" onClick={handleOpenNotif}>
                            <Bell size={18} strokeWidth={1.5} />
                            {unreadCount > 0 && (
                                <span className="mp-notif-badge">
                                    {unreadCount > 9 ? '9+' : unreadCount}
                                </span>
                            )}
                        </button>
                        {notifOpen && (
                            <div className="mp-notif-dropdown">
                                <div className="mp-notif-header">
                                    <span>알림</span>
                                    <button onClick={() => setNotifOpen(false)} className="mp-notif-close-btn"><X size={14} /></button>
                                </div>
                                {notifLoading ? (
                                    <div className="mp-notif-loading"><Loader2 size={18} className="animate-spin" /></div>
                                ) : notifications.length === 0 ? (
                                    <div className="mp-notif-empty">알림이 없습니다.</div>
                                ) : notifications.map(n => (
                                    <div key={n.notificationId}
                                        className="mp-notif-item"
                                        style={{ background: n.isRead ? 'transparent' : 'var(--color-purple-50)' }}>
                                        <div className="mp-notif-item__body">
                                            <div className="mp-notif-item__message">{n.message}</div>
                                            <div className="mp-notif-item__time">{new Date(n.createdAt).toLocaleString('ko-KR')}</div>
                                        </div>
                                        {!n.isRead && (
                                            <button onClick={() => handleMarkRead(n.notificationId)} title="읽음 처리"
                                                className="mp-notif-read-btn">
                                                <Check size={14} strokeWidth={2} />
                                            </button>
                                        )}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* 아바타 / 프로필 드롭다운 */}
                    <div ref={profileRef} className="mp-relative">
                        <div className="topbar__avatar mp-avatar-btn" onClick={() => setProfileOpen(v => !v)}>
                            {avatarChar}
                        </div>
                        {profileOpen && (
                            <div className="mp-profile-dropdown">
                                <div className="mp-profile-header">
                                    <div className="mp-profile-name">{nickname ?? '사용자'}</div>
                                </div>
                                <button onClick={() => { setProfileOpen(false); navigate('/my-page') }}
                                    className="mp-profile-menu-btn">
                                    <UserCircle size={14} strokeWidth={1.5} />
                                    마이페이지
                                </button>
                                <button onClick={handleLogout}
                                    className="mp-profile-logout-btn">
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
                            className="hero__cta hero__cta--inline"
                            onClick={() => setShowWizard(true)}
                        >
                            <Sparkles size={16} strokeWidth={1.5} />
                            AI 맞춤 커리큘럼 시작하기
                        </button>
                    </div>

                    <div className="hero__illustration hero__illustration--colored">
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
                            <div className="feature-card__icon-wrap mp-feature-icon">
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
                        <div className="mp-rooms-loading">
                            <Loader2 size={16} strokeWidth={1.5} className="animate-spin" />
                            <span className="mp-rooms-loading__text">학습실 불러오는 중...</span>
                        </div>
                    ) : roomsError ? (
                        <div className="mp-rooms-error">
                            ⚠ {roomsError}
                        </div>
                    ) : !activeRoom ? (
                        <div className="mp-rooms-empty">
                            진행 중인 학습실이 없습니다. AI 맞춤 커리큘럼을 시작해보세요!
                        </div>
                    ) : (
                        <div
                            className="active-study__card"
                            onClick={() => navigate(`/study/${activeRoom.roomId}/curriculum`, { state: { subject: activeRoom.subject, level: activeRoom.level, completionRate: activeRoom.completionRate } })}
                        >
                            <div className="active-study__emoji mp-active-emoji">
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
                            <button className="active-study__btn mp-active-btn--inline">
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
