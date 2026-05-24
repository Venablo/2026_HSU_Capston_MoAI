import { useNavigate } from 'react-router-dom'
import { useState, useEffect, useRef, useCallback } from 'react'
import type { MouseEvent as ReactMouseEvent } from 'react'
import {
    BookOpen, Plus, Loader2, Bell, Moon, Sun, X, Check, LogOut, UserCircle, Search, Trash2,
} from 'lucide-react'
import '../../styles/MyStudiesPage.css'
import OnboardingWizard from '../../components/OnboardingWizard'
import StudyMatchDropdown from '../../components/StudyMatchDropdown'
import { useTheme } from '../../context/ThemeContext'
import { deleteLearningRoom, getLearningRooms, getNotifications, markNotificationRead, updateProfile, logout } from '../../services/apiService'
import type { LearningRoomListItem, NotificationItem } from '../../types/api'
import { useAuth } from '../../context/AuthContext'

type FilterKey = '전체' | '진행 중' | '완료'

function statusLabel(status: LearningRoomListItem['status']): string {
    if (status === 'active')    return '진행 중'
    if (status === 'completed') return '완료'
    if (status === 'paused')    return '일시정지'
    return status
}

export default function MyStudiesPage() {
    const navigate = useNavigate()
    const { nickname, refreshToken, clearAuth } = useAuth()
    const [filter,  setFilter]  = useState<FilterKey>('전체')
    const [rooms,   setRooms]   = useState<LearningRoomListItem[]>([])
    const [loading, setLoading] = useState(true)
    const [error,   setError]   = useState<string | null>(null)
    const [showWizard, setShowWizard] = useState(false)
    const [deletingRoomId, setDeletingRoomId] = useState<string | null>(null)

    const fetchRooms = useCallback(async (showInitialLoading = true) => {
        if (showInitialLoading) setLoading(true)
        setError(null)
        try {
            const data = await getLearningRooms()
            setRooms(data)
        } catch (e) {
            setError(e instanceof Error ? e.message : '학습실 목록을 불러오지 못했습니다.')
        } finally {
            if (showInitialLoading) setLoading(false)
        }
    }, [])

    useEffect(() => {
        void fetchRooms()
    }, [fetchRooms])

    const avatarChar = nickname ? nickname.charAt(0).toUpperCase() : '?'

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
    const [notifOpen,     setNotifOpen]     = useState(false)
    const [notifications, setNotifications] = useState<NotificationItem[]>([])
    const [notifLoading,  setNotifLoading]  = useState(false)
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

    const filtered = rooms.filter(r => {
        const matchesFilter =
            filter === '전체' ? true :
            filter === '진행 중' ? (r.status === 'active' || r.status === 'paused') :
            filter === '완료' ? r.status === 'completed' : true
        const matchesSearch = !normalizedSearch || r.subject.toLowerCase().includes(normalizedSearch)
        return matchesFilter && matchesSearch
    })

    const handleDeleteRoom = async (room: LearningRoomListItem, e: ReactMouseEvent<HTMLButtonElement>) => {
        e.stopPropagation()
        const confirmed = window.confirm(`'${room.subject}' 학습실을 삭제할까요?\n삭제하면 커리큘럼, 퀴즈, 학습자료가 함께 삭제됩니다.`)
        if (!confirmed) return

        setDeletingRoomId(room.roomId)
        try {
            await deleteLearningRoom(room.roomId)
            setRooms(prev => prev.filter(item => item.roomId !== room.roomId))
        } catch (err) {
            window.alert(err instanceof Error ? err.message : '학습실 삭제에 실패했습니다.')
        } finally {
            setDeletingRoomId(null)
        }
    }

    return (
        <>
            <header className="topbar">
                <h2 className="topbar__title">내 스터디</h2>
                <div className="topbar__actions">
                    {/* 검색 */}
                    <div ref={searchRef} className="msp-search-wrapper">
                        <div className="msp-search-box">
                            <Search size={16} strokeWidth={1.5} className="msp-search-icon" />
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
                                className="msp-search-input"
                            />
                            {searchValue && (
                                <button onClick={() => { setSearchValue(''); setSearchFocus(false) }}
                                    className="msp-search-clear-btn">
                                    <X size={14} strokeWidth={1.5} />
                                </button>
                            )}
                        </div>
                        {searchFocus && normalizedSearch && (
                            <div className="msp-search-dropdown">
                                {filteredRooms.length === 0 ? (
                                    <div className="msp-search-empty">검색 결과가 없습니다.</div>
                                ) : filteredRooms.map(r => (
                                    <button key={r.roomId}
                                        onClick={() => { navigate(`/study/${r.roomId}/curriculum`, { state: { subject: r.subject, level: r.level, completionRate: r.completionRate } }); setSearchValue(''); setSearchFocus(false) }}
                                        className="msp-search-result-btn">
                                        <span>{r.subject}</span>
                                        <span className="msp-search-result-pct">{r.completionRate}%</span>
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
                    <div ref={notifRef} className="msp-notif-wrapper">
                        <button className="topbar__icon-btn msp-notif-btn" onClick={handleOpenNotif}>
                            <Bell size={18} strokeWidth={1.5} />
                            {unreadCount > 0 && (
                                <span className="msp-notif-badge">
                                    {unreadCount > 9 ? '9+' : unreadCount}
                                </span>
                            )}
                        </button>
                        {notifOpen && (
                            <div className="msp-notif-dropdown">
                                <div className="msp-notif-header">
                                    <span>알림</span>
                                    <button onClick={() => setNotifOpen(false)} className="msp-notif-close-btn"><X size={14} /></button>
                                </div>
                                {notifLoading ? (
                                    <div className="msp-notif-loading"><Loader2 size={18} className="animate-spin" /></div>
                                ) : notifications.length === 0 ? (
                                    <div className="msp-notif-empty">알림이 없습니다.</div>
                                ) : notifications.map(n => (
                                    <div key={n.notificationId}
                                        className="msp-notif-item"
                                        style={{ background: n.isRead ? 'transparent' : 'var(--color-purple-50)' }}
                                    >
                                        <div className="msp-notif-item__body">
                                            <div className="msp-notif-item__message">{n.message}</div>
                                            <div className="msp-notif-item__date">{new Date(n.createdAt).toLocaleString('ko-KR')}</div>
                                        </div>
                                        {!n.isRead && (
                                            <button onClick={() => handleMarkRead(n.notificationId)} title="읽음 처리"
                                                className="msp-notif-read-btn">
                                                <Check size={14} strokeWidth={2} />
                                            </button>
                                        )}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* 아바타 / 프로필 드롭다운 */}
                    <div ref={profileRef} className="msp-profile-wrapper">
                        <div className="topbar__avatar msp-avatar-btn" onClick={() => setProfileOpen(v => !v)}>
                            {avatarChar}
                        </div>
                        {profileOpen && (
                            <div className="msp-profile-dropdown">
                                <div className="msp-profile-header">
                                    <div className="msp-profile-name">{nickname ?? '사용자'}</div>
                                </div>
                                <button onClick={() => { setProfileOpen(false); navigate('/my-page') }}
                                    className="msp-profile-menu-btn">
                                    <UserCircle size={14} strokeWidth={1.5} />
                                    마이페이지
                                </button>
                                <button onClick={handleLogout}
                                    className="msp-profile-logout-btn">
                                    <LogOut size={14} strokeWidth={1.5} />
                                    로그아웃
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </header>

            <main className="my-studies__content">
                {/* Filter */}
                <div className="my-studies__filter-row">
                    <div className="my-studies__filter-tabs">
                        {(['전체', '진행 중', '완료'] as FilterKey[]).map(f => (
                            <button
                                key={f}
                                className={`my-studies__filter-btn ${filter === f ? 'my-studies__filter-btn--active' : ''}`}
                                onClick={() => setFilter(f)}
                            >
                                {f}
                            </button>
                        ))}
                    </div>
                    <button className="btn-primary my-studies__create-btn" onClick={() => setShowWizard(true)}>
                        <Plus size={14} strokeWidth={2} />
                        새 학습실 만들기
                    </button>
                </div>

                {/* Loading */}
                {loading && (
                    <div className="msp-loading-state">
                        <Loader2 size={20} strokeWidth={1.5} className="animate-spin" />
                        <span>학습실 목록을 불러오는 중...</span>
                    </div>
                )}

                {/* Error */}
                {!loading && error && (
                    <div className="msp-error-state">⚠ {error}</div>
                )}

                {/* Grid */}
                {!loading && !error && (
                    <div className="study-grid">
                        {filtered.map((room, i) => {
                            const label  = statusLabel(room.status)
                            const isDone = room.status === 'completed'
                            const weekStr = isDone
                                ? '완료'
                                : `Week ${room.currentWeek} / ${room.durationWeeks}주`

                            return (
                                <div
                                    key={room.roomId}
                                    className={`study-card animate-fade-in delay-${i * 100}`}
                                    onClick={() => navigate(`/study/${room.roomId}/curriculum`, { state: { subject: room.subject, level: room.level, completionRate: room.completionRate } })}
                                >
                                    <div className="study-card__header">
                                        <div className="study-card__info-row">
                                            <div className="study-card__emoji-wrap msp-emoji-wrap--purple">
                                                <BookOpen size={22} strokeWidth={1.5} />
                                            </div>
                                            <div>
                                                <div className="study-card__title">{room.subject}</div>
                                                <div className="study-card__week">{weekStr}</div>
                                            </div>
                                        </div>
                                        <div className="study-card__actions">
                                            <span
                                                className={`study-card__status-badge ${
                                                    isDone
                                                        ? 'study-card__status-badge--done'
                                                        : 'study-card__status-badge--active'
                                                }`}
                                            >
                                                {label}
                                            </span>
                                            <button
                                                className="study-card__delete-btn"
                                                type="button"
                                                title="학습실 삭제"
                                                aria-label={`${room.subject} 학습실 삭제`}
                                                disabled={deletingRoomId === room.roomId}
                                                onClick={(e) => handleDeleteRoom(room, e)}
                                            >
                                                {deletingRoomId === room.roomId
                                                    ? <Loader2 size={14} strokeWidth={1.8} className="animate-spin" />
                                                    : <Trash2 size={14} strokeWidth={1.8} />}
                                            </button>
                                        </div>
                                    </div>

                                    <div className="study-card__topic">레벨: {room.level}</div>

                                    <div>
                                        <div className="study-card__progress-header">
                                            <span className="study-card__progress-label">진행률</span>
                                            <span className="study-card__progress-pct msp-progress-pct--purple">
                                                {room.completionRate}%
                                            </span>
                                        </div>
                                        <div className="study-card__bar-track">
                                            <div
                                                className="study-card__bar-fill msp-bar-fill--purple"
                                                style={{ width: `${room.completionRate}%` }}
                                            />
                                        </div>
                                    </div>

                                    <div className="study-card__tags">
                                        <span className="study-card__tag">#{room.subject}</span>
                                        <span className="study-card__tag">#{room.level}</span>
                                    </div>
                                </div>
                            )
                        })}

                        {/* Empty state */}
                        {filtered.length === 0 && (
                            <div className="msp-empty-state">
                                {filter === '전체' && !normalizedSearch ? '아직 학습실이 없습니다.' : `검색 결과가 없습니다.`}
                            </div>
                        )}

                    </div>
                )}
            </main>

            {showWizard && (
                <OnboardingWizard
                    onClose={() => setShowWizard(false)}
                    onCreated={() => { void fetchRooms(false) }}
                    redirectOnSuccess={false}
                />
            )}
        </>
    )
}
