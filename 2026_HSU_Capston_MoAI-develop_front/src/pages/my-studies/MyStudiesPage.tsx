import { useNavigate } from 'react-router-dom'
import { useState, useEffect, useRef, useCallback } from 'react'
import type { MouseEvent as ReactMouseEvent } from 'react'
import {
    BookOpen, Plus, Loader2, Bell, Moon, Sun, X, Check, LogOut, UserCircle, Search, Trash2,
} from 'lucide-react'
import '../../styles/MyStudiesPage.css'
import OnboardingWizard from '../../components/OnboardingWizard'
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
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '48px 0', color: 'var(--color-text-secondary)' }}>
                        <Loader2 size={20} strokeWidth={1.5} className="animate-spin" />
                        <span>학습실 목록을 불러오는 중...</span>
                    </div>
                )}

                {/* Error */}
                {!loading && error && (
                    <div style={{ padding: '48px 0', color: '#ef4444' }}>⚠ {error}</div>
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
                                    onClick={() => navigate(`/study/${room.roomId}/classroom`)}
                                >
                                    <div className="study-card__header">
                                        <div className="study-card__info-row">
                                            <div className="study-card__emoji-wrap" style={{ color: 'var(--color-purple-500)' }}>
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
                                            <span className="study-card__progress-pct" style={{ color: 'var(--color-purple-500)' }}>
                                                {room.completionRate}%
                                            </span>
                                        </div>
                                        <div className="study-card__bar-track">
                                            <div
                                                className="study-card__bar-fill"
                                                style={{ width: `${room.completionRate}%`, background: 'var(--color-purple-500)' }}
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
                            <div style={{ gridColumn: '1 / -1', padding: '40px 0', color: 'var(--color-text-secondary)', textAlign: 'center' }}>
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
