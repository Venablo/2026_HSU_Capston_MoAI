/**
 * ============================================================================
 * StudyClassroom.tsx  —  핵심 학습 화면
 * ============================================================================
 *
 * 데이터 로딩 흐름:
 *   1. getCurriculum(roomId) → 전체 주차 목록 조회
 *   2. 진행 중인 첫 번째 주차(completionRate < 100) 선택, 없으면 마지막 주차
 *   3. getCurriculumWeek(roomId, weekId) → 주차 상세 데이터 조회
 *      (topic, description, keywords, resources, mainVideoId, completionRate)
 *
 * 탭별 데이터 (lazy-load — 탭 첫 클릭 시 로드):
 *   docs    → weekData.resources (ResourceItem[])
 *   videos  → getRecommendedVideos(roomId, weekId)
 *   summary → weekData.keywords (string[])
 *   quiz    → getQuizAttempts(roomId, weekId)
 * ============================================================================
 */

import type { ReactNode } from 'react'
import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
    Search, Bell, ChevronLeft, ChevronRight,
    FileText, FileEdit, Package,
    PlayCircle,
    ClipboardList, Play, Zap, MessageSquare,
    Calendar, BrainCircuit, CheckCircle2,
    Trophy, Lock, Users, MessageCircle,
    UserCircle, ArrowRight, Loader2,
    Moon, Sun, X, Check, LogOut,
} from 'lucide-react'
import '../../../styles/StudyClassroom.css'
import '../../../styles/FinalQuizModal.css'
import { ClassroomModalProvider, useClassroomModal } from '../../../context/ClassroomModalContext'
import ClassroomModals from '../../../components/modals/common/ClassroomModals'
import { useYouTubePlayer } from '../../../hooks/useYouTubePlayer'
import { useAuth } from '../../../context/AuthContext'
import {
    sendEventLog,
    getMaterialDetail,
    getInstantQuiz,
    getCurriculum,
    getCurriculumWeek,
    getRecommendedVideos,
    getQuizAttempts,
    updateProgress,
    getNotifications,
    markNotificationRead,
    updateProfile,
    logout,
} from '../../../services/apiService'
import type {
    EventType,
    LearningEventPayload,
    CurriculumWeekDetail,
    CurriculumWeekSummary,
    RecommendedVideo,
    QuizAttemptListItem,
    NotificationItem,
} from '../../../types/api'

// ── 타입 정의 ─────────────────────────────────────────────────────────────────
type TabKey = 'docs' | 'videos' | 'summary' | 'quiz'
interface Tab { key: TabKey; icon: ReactNode; label: string }

const TABS: Tab[] = [
    { key: 'docs',    icon: <ClipboardList size={14} strokeWidth={1.5} />, label: '주차별 공식 교안' },
    { key: 'videos',  icon: <Play          size={14} strokeWidth={1.5} />, label: 'AI 추천 영상'    },
    { key: 'summary', icon: <Zap           size={14} strokeWidth={1.5} />, label: 'AI 핵심 요약'    },
    { key: 'quiz',    icon: <MessageSquare size={14} strokeWidth={1.5} />, label: '퀴즈 내역'       },
]

// ── 유틸 ──────────────────────────────────────────────────────────────────────
function formatDuration(sec: number): string {
    const m = Math.floor(sec / 60)
    const s = sec % 60
    return `${m}:${s.toString().padStart(2, '0')}`
}

function resourceIcon(type: string): ReactNode {
    if (type === 'pdf')  return <FileText size={20} strokeWidth={1.5} />
    if (type === 'docx') return <FileEdit size={20} strokeWidth={1.5} />
    if (type === 'zip')  return <Package  size={20} strokeWidth={1.5} />
    return <FileText size={20} strokeWidth={1.5} />
}

function resolveResourceUrl(url: string): string {
    if (!url) return '#'
    if (/^https?:\/\//i.test(url)) return url

    const apiBase = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '')
    if (url.startsWith('/api/') && apiBase) return `${apiBase}${url}`
    return url
}

function withDownloadParam(url: string): string {
    if (!url || url === '#') return '#'

    try {
        const parsed = new URL(url, window.location.origin)
        parsed.searchParams.set('download', 'true')
        return parsed.toString()
    } catch {
        return url.includes('?') ? `${url}&download=true` : `${url}?download=true`
    }
}

function openResource(url: string) {
    if (!url || url === '#') return
    window.open(url, '_blank', 'noopener,noreferrer')
}

function TabLoading() {
    return (
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '24px 0', color: 'var(--color-text-secondary)' }}>
            <Loader2 size={16} strokeWidth={1.5} className="animate-spin" />
            <span style={{ fontSize: '13px' }}>불러오는 중...</span>
        </div>
    )
}

function TabEmpty({ message }: { message: string }) {
    return (
        <p style={{ padding: '24px 0', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
            {message}
        </p>
    )
}

// ── 핵심 컴포넌트 (ClassroomModalProvider 내부에서 렌더링) ─────────────────────
function StudyClassroomContent() {
    const { studyId: roomId = '' } = useParams<{ studyId: string }>()

    const [rightCollapsed, setRightCollapsed] = useState(false)
    const [tab, setTab] = useState<TabKey>('docs')

    // 주차 데이터
    const [weekData,    setWeekData]    = useState<CurriculumWeekDetail | null>(null)
    const [weekLoading, setWeekLoading] = useState(true)
    const [weekError,   setWeekError]   = useState<string | null>(null)

    // 전체 주차 목록 (드롭다운용)
    const [allWeeks,     setAllWeeks]     = useState<CurriculumWeekSummary[]>([])
    const [weekDropdown, setWeekDropdown] = useState(false)

    // 탭별 데이터 (lazy-load)
    const [videos,        setVideos]        = useState<RecommendedVideo[] | null>(null)
    const [videosLoading, setVideosLoading] = useState(false)
    const [quizAttempts,  setQuizAttempts]  = useState<QuizAttemptListItem[] | null>(null)
    const [quizLoading,   setQuizLoading]   = useState(false)

    const { open, metacogComplete, partnerConnected, setCurrentWeekId, setMetacogComplete } = useClassroomModal()
    const { nickname, refreshToken, clearAuth } = useAuth()
    const navigate = useNavigate()
    const avatarChar = nickname ? nickname.charAt(0).toUpperCase() : '?'
    const dropdownRef = useRef<HTMLDivElement>(null)

    // ── 검색 ─────────────────────────────────────────────────────────────────
    const [searchValue,  setSearchValue]  = useState('')
    const [searchFocus,  setSearchFocus]  = useState(false)
    const searchRef = useRef<HTMLDivElement>(null)
    const normalizedSearch = searchValue.trim().toLowerCase()
    const filteredWeeks = normalizedSearch
        ? allWeeks.filter(w =>
            `${w.weekNumber} ${w.topic}`.toLowerCase().includes(normalizedSearch))
        : []

    // 검색 외부 클릭 시 닫기
    useEffect(() => {
        if (!searchFocus || !searchValue.trim()) return
        const handler = (e: MouseEvent) => {
            if (searchRef.current && !searchRef.current.contains(e.target as Node)) {
                setSearchFocus(false)
            }
        }
        document.addEventListener('mousedown', handler)
        return () => document.removeEventListener('mousedown', handler)
    }, [searchFocus, searchValue])

    // ── 알림 ─────────────────────────────────────────────────────────────────
    const [notifOpen,    setNotifOpen]    = useState(false)
    const [notifications, setNotifications] = useState<NotificationItem[]>([])
    const [notifLoading, setNotifLoading] = useState(false)
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
            .then(() => {
                setNotifications(prev =>
                    prev.map(n => n.notificationId === notificationId ? { ...n, isRead: true } : n))
            })
            .catch(() => {})
    }

    // 알림 외부 클릭 시 닫기
    useEffect(() => {
        if (!notifOpen) return
        const handler = (e: MouseEvent) => {
            if (notifRef.current && !notifRef.current.contains(e.target as Node)) {
                setNotifOpen(false)
            }
        }
        document.addEventListener('mousedown', handler)
        return () => document.removeEventListener('mousedown', handler)
    }, [notifOpen])

    // ── 프로필 드롭다운 ───────────────────────────────────────────────────────
    const [profileOpen, setProfileOpen] = useState(false)
    const profileRef = useRef<HTMLDivElement>(null)

    const handleLogout = async () => {
        try {
            if (refreshToken) await logout({ refreshToken })
        } catch {}
        clearAuth()
        navigate('/')
    }

    // 프로필 드롭다운 외부 클릭 시 닫기
    useEffect(() => {
        if (!profileOpen) return
        const handler = (e: MouseEvent) => {
            if (profileRef.current && !profileRef.current.contains(e.target as Node)) {
                setProfileOpen(false)
            }
        }
        document.addEventListener('mousedown', handler)
        return () => document.removeEventListener('mousedown', handler)
    }, [profileOpen])

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

    // 드롭다운 외부 클릭 시 닫기
    useEffect(() => {
        if (!weekDropdown) return
        const handleClickOutside = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
                setWeekDropdown(false)
            }
        }
        document.addEventListener('mousedown', handleClickOutside)
        return () => document.removeEventListener('mousedown', handleClickOutside)
    }, [weekDropdown])

    const applyWeekDetail = useCallback((
        detail: CurriculumWeekDetail,
        options: { syncMetacog?: boolean } = {},
    ) => {
        setWeekData(detail)
        setCurrentWeekId(detail.weekId)
        if (options.syncMetacog ?? true) {
            setMetacogComplete((Number(detail.completionRate) || 0) >= 70)
        }
    }, [setCurrentWeekId, setMetacogComplete])

    // ── 주차 데이터 로드 ─────────────────────────────────────────────────────
    useEffect(() => {
        if (!roomId) {
            setWeekError('학습실 ID가 없습니다.')
            setWeekLoading(false)
            return
        }
        setWeekLoading(true)
        setWeekError(null)
        getCurriculum(roomId)
            .then(weeks => {
                if (!weeks.length) throw new Error('이 학습실에 커리큘럼이 없습니다.')
                setAllWeeks(weeks)
                // 진행 중인 첫 번째 주차 선택, 없으면 마지막 주차
                const active = weeks.find(w => w.completionRate < 100) ?? weeks[weeks.length - 1]
                return getCurriculumWeek(roomId, active.weekId)
            })
            .then(detail => {
                applyWeekDetail(detail)
            })
            .catch(e => setWeekError(e instanceof Error ? e.message : '주차 데이터를 불러오지 못했습니다.'))
            .finally(() => setWeekLoading(false))
    }, [roomId, applyWeekDetail])

    // ── 주차 전환 ─────────────────────────────────────────────────────────────
    const handleWeekSwitch = useCallback((weekId: string) => {
        if (!roomId || weekId === weekData?.weekId) { setWeekDropdown(false); return }
        setWeekDropdown(false)
        setWeekLoading(true)
        setWeekError(null)
        setVideos(null)
        setQuizAttempts(null)
        setMetacogComplete(false)
        getCurriculumWeek(roomId, weekId)
            .then(detail => {
                applyWeekDetail(detail)
            })
            .catch(e => setWeekError(e instanceof Error ? e.message : '주차 데이터를 불러오지 못했습니다.'))
            .finally(() => setWeekLoading(false))
    }, [roomId, weekData?.weekId, applyWeekDetail, setMetacogComplete])

    // ── 영상 탭: 첫 클릭 시 lazy-load ────────────────────────────────────────
    const loadVideosForWeek = useCallback((showLoading = true) => {
        if (!roomId || !weekData) return Promise.resolve()
        if (showLoading) setVideosLoading(true)
        return getRecommendedVideos(roomId, weekData.weekId)
            .then(setVideos)
            .catch(() => setVideos([]))
            .finally(() => {
                if (showLoading) setVideosLoading(false)
            })
    }, [roomId, weekData?.weekId])

    useEffect(() => {
        if (tab !== 'videos' || !roomId || !weekData || videos !== null) return
        loadVideosForWeek()
    }, [tab, roomId, weekData, videos, loadVideosForWeek])

    // ── 퀴즈 탭: 첫 클릭 시 lazy-load ────────────────────────────────────────
    useEffect(() => {
        if (tab !== 'quiz' || !roomId || !weekData || quizAttempts !== null) return
        setQuizLoading(true)
        getQuizAttempts(roomId, weekData.weekId)
            .then(setQuizAttempts)
            .catch(() => setQuizAttempts([]))
            .finally(() => setQuizLoading(false))
    }, [tab, roomId, weekData, quizAttempts])

    useEffect(() => {
        if (!roomId || !weekData || weekLoading) return

        const hasMainVideo = Boolean(weekData.mainVideoId)
        const hasResources = (weekData.resources?.length ?? 0) > 0
        if (hasMainVideo && hasResources) return

        let cancelled = false
        let attempts = 0
        const timer = window.setInterval(async () => {
            attempts += 1
            try {
                const detail = await getCurriculumWeek(roomId, weekData.weekId)
                if (cancelled) return
                applyWeekDetail(detail, { syncMetacog: false })
                if (tab === 'videos') void loadVideosForWeek(false)

                const ready = Boolean(detail.mainVideoId) && detail.resources.length > 0
                if (ready || attempts >= 12) window.clearInterval(timer)
            } catch {
                if (attempts >= 12) window.clearInterval(timer)
            }
        }, 5_000)

        return () => {
            cancelled = true
            window.clearInterval(timer)
        }
    }, [
        roomId,
        weekData?.weekId,
        weekData?.mainVideoId,
        weekData?.resources?.length,
        weekLoading,
        tab,
        applyWeekDetail,
        loadVideosForWeek,
    ])

    useEffect(() => {
        if (!metacogComplete || !roomId || !weekData?.weekId) return

        setWeekData(prev => prev
            ? { ...prev, completionRate: Math.max(Number(prev.completionRate) || 0, 70) }
            : prev)

        getCurriculumWeek(roomId, weekData.weekId)
            .then(detail => applyWeekDetail({
                ...detail,
                completionRate: Math.max(Number(detail.completionRate) || 0, 70),
            }, { syncMetacog: false }))
            .catch(() => {})
    }, [metacogComplete, roomId, weekData?.weekId, applyWeekDetail])

    // ── 패턴 감지 핸들러 ─────────────────────────────────────────────────────
    const handlePatternDetected = useCallback(async (
        eventType: EventType,
        payload: LearningEventPayload,
    ) => {
        if (!weekData) return

        try {
            const result = await sendEventLog(roomId, {
                event_type:    eventType,
                curriculum_id: weekData.weekId,
                payload,
            })

            if (!result.aiTriggered) return

            if (
                result.eventType === 'video_rewind' ||
                result.eventType === 'video_pause'  ||
                result.eventType === 'tab_departure'
            ) {
                if (result.materialId) {
                    const material = await getMaterialDetail(roomId, result.materialId)
                    open('monitoring', {
                        type:        'monitoring',
                        conceptName: material.title,
                        reason:      `${eventType} 패턴 감지 — 보충 자료를 준비했어요.`,
                        summaryItems: material.summaryItems.map(s => ({
                            letter:      s.label,
                            title:       s.title,
                            description: s.desc,
                        })),
                    })
                }
            } else if (result.eventType === 'video_skip' || result.eventType === 'video_speed_up') {
                const quiz = await getInstantQuiz(roomId, weekData.weekId)
                open('quiz-pass', { type: 'quiz-pass', quiz })
            }
        } catch {
            // pattern detection errors are silent
        }
    }, [roomId, weekData, open])

    // ── 메인 영상 ID 결정 ────────────────────────────────────────────────────
    const activeVideoId = weekData?.mainVideoId || ''

    // AI 추천 영상 탭: API에서 받은 영상 목록 전체 표시 (메인 영상 포함)
    const serverRecommendedList = Array.isArray(videos) ? videos : []
    const recommendedList = activeVideoId && !serverRecommendedList.some(v => v.videoId === activeVideoId)
        ? [
            {
                videoId: activeVideoId,
                title: weekData?.topic ? `${weekData.topic} 대표 영상` : '대표 학습 영상',
                durationSec: 0,
                viewCount: 0,
                isMain: true,
            },
            ...serverRecommendedList,
        ]
        : serverRecommendedList

    // ── 진행률 마일스톤 핸들러 ────────────────────────────────────────────────
    const handleProgressMilestone = useCallback((rate: number) => {
        if (!weekData || !roomId) return
        // 현재 저장된 completionRate보다 높을 때만 업데이트
        const currentRate = Number(weekData.completionRate) || 0
        if (rate <= currentRate) return
        updateProgress(roomId, weekData.weekId, { completionRate: rate })
            .then(res => {
                setWeekData(prev => prev ? { ...prev, completionRate: res.completionRate } : prev)
            })
            .catch(() => { /* 진행률 업데이트 실패 시 조용히 무시 */ })
    }, [roomId, weekData])

    // ── YouTube IFrame API 훅 ─────────────────────────────────────────────────
    const { playerHostRef } = useYouTubePlayer({
        videoId:             activeVideoId,
        onPatternDetected:   handlePatternDetected,
        onProgressMilestone: handleProgressMilestone,
    })

    const progress = Number(weekData?.completionRate) || 0
    const safeResources = weekData?.resources ?? []
    const safeKeywords = weekData?.keywords ?? []
    const canStartMetacog = Boolean(weekData && activeVideoId && progress >= 40)
    const canStartFinalQuiz = Boolean(weekData && metacogComplete && progress >= 70)

    return (
        <>
            {/* ── Topbar ── */}
            <header className="topbar">
                <h2 className="topbar__title">AI 상세 학습실</h2>
                <div className="topbar__actions">
                    {/* 검색 */}
                    <div ref={searchRef} style={{ position: 'relative' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', background: 'var(--color-purple-50)', borderRadius: '10px', padding: '8px 14px' }}>
                            <Search size={16} strokeWidth={1.5} style={{ color: 'var(--color-text-secondary)' }} />
                            <input
                                placeholder="주차 검색..."
                                value={searchValue}
                                onChange={e => setSearchValue(e.target.value)}
                                onFocus={() => setSearchFocus(true)}
                                onKeyDown={e => {
                                    if (e.key === 'Enter' && filteredWeeks[0]) {
                                        handleWeekSwitch(filteredWeeks[0].weekId)
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
                                {filteredWeeks.length === 0 ? (
                                    <div style={{ padding: '10px 14px', fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                                        검색 결과가 없습니다.
                                    </div>
                                ) : filteredWeeks.map(w => (
                                    <button key={w.weekId} onClick={() => { handleWeekSwitch(w.weekId); setSearchValue(''); setSearchFocus(false) }}
                                        style={{
                                            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                            width: '100%', padding: '9px 14px', background: 'none', border: 'none',
                                            textAlign: 'left', cursor: 'pointer', fontSize: '13px',
                                            color: 'var(--color-text-primary)',
                                        }}>
                                        <span>Week {w.weekNumber}: {w.topic}</span>
                                        <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginLeft: '8px' }}>{Number(w.completionRate).toFixed(0)}%</span>
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

            {/* ── Body ── */}
            <div className="classroom">
                {/* ── 메인 패널 ── */}
                <div className="classroom__main">
                    {/* 주차 선택 버튼 + 드롭다운 */}
                    <div ref={dropdownRef} style={{ position: 'relative', display: 'inline-block' }}>
                        <button
                            className="classroom__week-btn"
                            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                            onClick={() => !weekLoading && allWeeks.length > 0 && setWeekDropdown(v => !v)}
                        >
                            <Calendar size={14} strokeWidth={1.5} />
                            {weekLoading
                                ? '불러오는 중...'
                                : weekData
                                    ? `Week ${weekData.weekNumber}: ${weekData.topic}`
                                    : weekError ?? ''
                            }
                            <ChevronRight size={14} strokeWidth={1.5} style={{ transform: weekDropdown ? 'rotate(270deg)' : 'rotate(90deg)', transition: 'transform .2s' }} />
                        </button>
                        {weekDropdown && (
                            <div style={{
                                position: 'absolute', top: '100%', left: 0, zIndex: 100,
                                background: 'var(--color-surface, #fff)', border: '1px solid var(--color-border, #e5e7eb)',
                                borderRadius: '10px', boxShadow: '0 8px 24px rgba(0,0,0,.12)',
                                minWidth: '280px', maxHeight: '320px', overflowY: 'auto', marginTop: '4px',
                            }}>
                                {allWeeks.map(w => {
                                    const isLocked = w.locked === true
                                    return (
                                    <button
                                        key={w.weekId}
                                        disabled={isLocked}
                                        onClick={() => !isLocked && handleWeekSwitch(w.weekId)}
                                        style={{
                                            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                            width: '100%', padding: '10px 16px', border: 'none',
                                            textAlign: 'left',
                                            cursor: isLocked ? 'not-allowed' : 'pointer',
                                            fontSize: '13px',
                                            color: isLocked
                                                ? 'var(--color-text-secondary, #9ca3af)'
                                                : w.weekId === weekData?.weekId
                                                    ? 'var(--color-purple-600, #7c3aed)'
                                                    : 'var(--color-text-primary, #111)',
                                            background: !isLocked && w.weekId === weekData?.weekId ? 'var(--color-purple-50, #f5f3ff)' : 'transparent',
                                            fontWeight: !isLocked && w.weekId === weekData?.weekId ? 600 : 400,
                                            opacity: isLocked ? 0.55 : 1,
                                        }}
                                    >
                                        <span style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                                            {isLocked && <Lock size={11} strokeWidth={1.5} />}
                                            Week {w.weekNumber}: {w.topic}
                                        </span>
                                        <span style={{ fontSize: '11px', color: 'var(--color-text-secondary, #6b7280)', marginLeft: '8px' }}>
                                            {isLocked ? '잠금' : `${Number(w.completionRate).toFixed(0)}%`}
                                        </span>
                                    </button>
                                    )
                                })}
                            </div>
                        )}
                    </div>

                    {/* 진행률 바 */}
                    <div className="classroom__progress-label">
                        COURSE PROGRESS: {progress}%
                    </div>
                    <div className="classroom__progress-track">
                        <div className="classroom__progress-fill" style={{ width: `${progress}%` }} />
                    </div>

                    {/* 강의 정보 */}
                    <h1 className="classroom__lesson-title">
                        {weekLoading ? '...' : weekData?.topic ?? weekError ?? ''}
                    </h1>
                    <p className="classroom__lesson-desc">
                        {weekData?.description ?? ''}
                    </p>

                    {/* ── YouTube IFrame 플레이어 ── */}
                    <div className="classroom__video">
                        {/* 로딩/에러 오버레이 — 플레이어 div는 항상 DOM에 유지 */}
                        {(weekLoading || weekError || !activeVideoId) && (
                            <div style={{
                                position: 'absolute', inset: 0, display: 'flex',
                                alignItems: 'center', justifyContent: 'center',
                                background: '#0f0f1a', zIndex: 1, borderRadius: '12px',
                            }}>
                                {weekLoading
                                    ? <Loader2 size={32} strokeWidth={1.5} className="animate-spin" style={{ color: 'var(--color-purple-500)' }} />
                                    : weekError
                                        ? <span style={{ color: '#ef4444', fontSize: '13px' }}>⚠ {weekError}</span>
                                        : <span style={{ color: '#fff', fontSize: '13px' }}>AI 영상 생성 중입니다. 잠시 뒤 자동으로 다시 확인합니다.</span>
                                }
                            </div>
                        )}
                        <div
                            ref={playerHostRef}
                            style={{ width: '100%', height: '100%', position: 'absolute', inset: 0 }}
                        />
                    </div>

                    {/* 탭 */}
                    <div className="classroom__tabs">
                        {TABS.map(t => (
                            <button
                                key={t.key}
                                className={`classroom__tab-btn ${tab === t.key ? 'classroom__tab-btn--active' : ''}`}
                                style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                                onClick={() => setTab(t.key)}
                            >
                                {t.icon}
                                {t.label}
                            </button>
                        ))}
                    </div>

                    {/* 탭: 교안 (주차 resources) */}
                    {tab === 'docs' && (
                        <div className="classroom__doc-list">
                            {weekLoading ? <TabLoading /> : !weekData ? null :
                            safeResources.length === 0
                                ? <TabEmpty message="AI 교안 생성 중이거나 아직 연결된 교안이 없습니다. 잠시 뒤 자동으로 다시 확인합니다." />
                                : (() => {
                                    const normalDocs = safeResources.filter(r => r.tag !== 'weakness')
                                    const weakDocs   = safeResources.filter(r => r.tag === 'weakness')
                                    const renderDocItem = (res: typeof safeResources[number], i: number) => {
                                        const viewUrl = resolveResourceUrl(res.url)
                                        const downloadUrl = withDownloadParam(viewUrl)
                                        return (
                                            <div
                                                key={i}
                                                className="classroom__doc-item"
                                                role="button"
                                                tabIndex={0}
                                                onClick={() => openResource(viewUrl)}
                                                onKeyDown={e => {
                                                    if (e.key === 'Enter' || e.key === ' ') {
                                                        e.preventDefault()
                                                        openResource(viewUrl)
                                                    }
                                                }}
                                            >
                                                <div className="classroom__doc-icon" style={{ color: res.tag === 'weakness' ? '#f59e0b' : 'var(--color-purple-500)' }}>
                                                    {resourceIcon(res.type)}
                                                </div>
                                                <div className="classroom__doc-info">
                                                    <div className="classroom__doc-name">{res.title}</div>
                                                    <div className="classroom__doc-meta">{res.size} · {res.type.toUpperCase()}</div>
                                                </div>
                                                <a
                                                    href={downloadUrl}
                                                    className="classroom__doc-download"
                                                    onClick={e => e.stopPropagation()}
                                                >
                                                    다운로드
                                                </a>
                                            </div>
                                        )
                                    }
                                    return (
                                        <>
                                            {normalDocs.map((res, i) => renderDocItem(res, i))}
                                            {weakDocs.length > 0 && (
                                                <>
                                                    <div style={{
                                                        display: 'flex', alignItems: 'center', gap: '6px',
                                                        margin: '16px 0 8px', padding: '6px 10px',
                                                        background: 'rgba(245,158,11,0.08)',
                                                        borderLeft: '3px solid #f59e0b',
                                                        borderRadius: '0 6px 6px 0',
                                                        fontSize: '12px', fontWeight: 600,
                                                        color: '#b45309',
                                                    }}>
                                                        <BrainCircuit size={13} strokeWidth={1.5} />
                                                        약점 보충 학습자료
                                                    </div>
                                                    {weakDocs.map((res, i) => renderDocItem(res, normalDocs.length + i))}
                                                </>
                                            )}
                                        </>
                                    )
                                })()
                            }
                        </div>
                    )}

                    {/* 탭: AI 추천 영상 */}
                    {tab === 'videos' && (
                        <div className="classroom__video-grid">
                            {videosLoading ? <TabLoading /> :
                            videos === null ? null :
                            recommendedList.length === 0
                                ? <TabEmpty message="AI 추천 영상 생성 중이거나 아직 연결된 영상이 없습니다. 잠시 뒤 자동으로 다시 확인합니다." />
                                : (() => {
                                    const normalVideos = recommendedList.filter(v => v.tag !== 'weakness')
                                    const weakVideos   = recommendedList.filter(v => v.tag === 'weakness')
                                    const renderVideoCard = (v: typeof recommendedList[number], i: number) => (
                                        <a
                                            key={i}
                                            href={`https://www.youtube.com/watch?v=${v.videoId}`}
                                            target="_blank"
                                            rel="noreferrer"
                                            style={{ textDecoration: 'none', color: 'inherit' }}
                                        >
                                            <div className="classroom__video-card" style={{ cursor: 'pointer' }}>
                                                <div className="classroom__video-thumb">
                                                    <img
                                                        src={`https://img.youtube.com/vi/${v.videoId}/mqdefault.jpg`}
                                                        alt={v.title}
                                                        style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: '8px' }}
                                                        onError={e => { (e.target as HTMLImageElement).style.display = 'none' }}
                                                    />
                                                    <div className="classroom__video-thumb-icon" style={{ color: 'rgba(255,255,255,0.9)', position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)' }}>
                                                        <PlayCircle size={28} strokeWidth={1.5} />
                                                    </div>
                                                    {v.durationSec > 0 && (
                                                        <div className="classroom__video-thumb-duration">
                                                            {formatDuration(v.durationSec)}
                                                        </div>
                                                    )}
                                                    {v.videoId === activeVideoId && (
                                                        <div style={{ position: 'absolute', top: 6, left: 6, background: 'var(--color-purple-500,#7c3aed)', color: '#fff', fontSize: '10px', padding: '2px 7px', borderRadius: '4px', fontWeight: 600 }}>
                                                            재생 중
                                                        </div>
                                                    )}
                                                </div>
                                                <div className="classroom__video-meta">
                                                    <div className="classroom__video-title">{v.title}</div>
                                                    <div className="classroom__video-channel">
                                                        {v.viewCount != null ? `조회수 ${v.viewCount.toLocaleString()}회` : 'YouTube'}
                                                    </div>
                                                </div>
                                            </div>
                                        </a>
                                    )
                                    return (
                                        <>
                                            {normalVideos.map((v, i) => renderVideoCard(v, i))}
                                            {weakVideos.length > 0 && (
                                                <>
                                                    <div style={{
                                                        gridColumn: '1 / -1',
                                                        display: 'flex', alignItems: 'center', gap: '6px',
                                                        margin: '12px 0 4px', padding: '6px 10px',
                                                        background: 'rgba(245,158,11,0.08)',
                                                        borderLeft: '3px solid #f59e0b',
                                                        borderRadius: '0 6px 6px 0',
                                                        fontSize: '12px', fontWeight: 600,
                                                        color: '#b45309',
                                                    }}>
                                                        <BrainCircuit size={13} strokeWidth={1.5} />
                                                        약점 보충 영상
                                                    </div>
                                                    {weakVideos.map((v, i) => renderVideoCard(v, normalVideos.length + i))}
                                                </>
                                            )}
                                        </>
                                    )
                                })()
                            }
                        </div>
                    )}

                    {/* 탭: AI 핵심 요약 (주차 keywords) */}
                    {tab === 'summary' && (
                        <div className="classroom__summary">
                            <div
                                className="classroom__summary-heading"
                                style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                            >
                                <Zap size={16} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                                핵심 키워드
                            </div>
                            {weekLoading ? <TabLoading /> :
                            safeKeywords.length === 0
                                ? <TabEmpty message="키워드 데이터가 없습니다." />
                                : safeKeywords.map((kw, i) => (
                                    <div key={i} className="classroom__summary-row">
                                        <div className="classroom__summary-dot" />
                                        <div className="classroom__summary-text">{kw}</div>
                                    </div>
                                ))
                            }
                        </div>
                    )}

                    {/* 탭: 퀴즈 내역 */}
                    {tab === 'quiz' && (
                        <div className="classroom__quiz-history">
                            <div
                                className="classroom__quiz-history-title"
                                style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                            >
                                <MessageSquare size={16} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                                퀴즈 내역
                            </div>
                            {quizLoading ? <TabLoading /> : (() => {
                                // Defensive: treat non-array (wrapped response) as empty
                                const list = Array.isArray(quizAttempts) ? quizAttempts : []
                                if (quizAttempts === null) return null
                                if (list.length === 0) return (
                                    <div style={{ padding: '24px 0' }}>
                                        <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', marginBottom: '12px' }}>
                                            아직 퀴즈 내역이 없습니다.
                                        </p>
                                        <button
                                            onClick={() => setTab('docs')}
                                            style={{
                                                fontSize: '12px', padding: '6px 14px',
                                                borderRadius: '6px', border: '1px solid var(--color-purple-300)',
                                                background: 'transparent', color: 'var(--color-purple-600)',
                                                cursor: 'pointer',
                                            }}
                                        >
                                            ← 학습으로 돌아가기
                                        </button>
                                    </div>
                                )
                                return list.map((item, i) => (
                                    <div key={item.attemptId} className="classroom__quiz-row">
                                        <div className="classroom__quiz-q">Q{i + 1}. {item.questionTitle}</div>
                                        <div className="classroom__quiz-result-row">
                                            <span className={`classroom__quiz-badge ${item.isCorrect ? 'classroom__quiz-badge--correct' : 'classroom__quiz-badge--wrong'}`}>
                                                {item.isCorrect ? '정답' : '오답'}
                                            </span>
                                        </div>
                                    </div>
                                ))
                            })()}
                        </div>
                    )}
                </div>

                {/* ── 우측 사이드 패널 ── */}
                <aside className={`classroom__aside${rightCollapsed ? ' classroom__aside--collapsed' : ''}`}>
                    <div className="classroom__aside-header">
                        <button
                            className="classroom__aside-toggle"
                            onClick={() => setRightCollapsed(c => !c)}
                            title={rightCollapsed ? '패널 열기' : '패널 닫기'}
                        >
                            {rightCollapsed
                                ? <ChevronLeft  size={16} strokeWidth={1.5} />
                                : <ChevronRight size={16} strokeWidth={1.5} />}
                        </button>
                    </div>
                    <div className="classroom__aside-scroll">
                        {/* 메타인지 확인 카드 */}
                        {!metacogComplete ? (
                            <div className="metacog-card">
                                <div
                                    className="metacog-card__title"
                                    style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                                >
                                    <BrainCircuit size={16} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                                    메타인지 확인
                                </div>
                                <p className="metacog-card__desc">
                                    {activeVideoId
                                        ? progress >= 40
                                            ? '방금 배운 내용을 AI에게 소리 내어 설명해보세요. 이해도를 실시간 분석해 드립니다.'
                                            : `영상 시청률이 40% 이상이어야 시작할 수 있습니다. 현재 ${progress}%입니다.`
                                        : '대표 영상이 준비되면 메타인지 학습을 시작할 수 있습니다.'}
                                </p>
                                <button
                                    className="metacog-card__btn"
                                    onClick={() => open('reverse-learning', {
                                        type:        'reverse-learning',
                                        conceptName: weekData?.topic ?? '',
                                        roomId,
                                        weekId:      weekData?.weekId,
                                    })}
                                    disabled={!canStartMetacog}
                                >
                                    AI에게 설명하기
                                </button>
                            </div>
                        ) : (
                            <div className="metacog-card metacog-card--complete">
                                <div
                                    className="metacog-card__title"
                                    style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                                >
                                    <CheckCircle2 size={16} strokeWidth={1.5} />
                                    메타인지 평가 완료
                                </div>
                                <p className="metacog-card__desc" style={{ margin: '4px 0 0' }}>
                                    거꾸로 학습이 완료되었습니다.
                                </p>
                            </div>
                        )}

                        {/* 주간 파이널 퀴즈 카드 */}
                        <div className={`weekly-quiz-card${canStartFinalQuiz ? ' weekly-quiz-card--active' : ' weekly-quiz-card--locked'}`}>
                            <div
                                className="weekly-quiz-card__title"
                                style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                            >
                                {canStartFinalQuiz
                                    ? <Trophy size={16} strokeWidth={1.5} />
                                    : <Lock   size={16} strokeWidth={1.5} />}
                                주간 최종 퀴즈
                            </div>
                            <p className="weekly-quiz-card__desc">
                                {canStartFinalQuiz
                                    ? `Week ${weekData?.weekNumber ?? ''} 전체 내용 최종 평가! 도전해보세요.`
                                    : '메타인지 평가를 완료하면 잠금이 해제됩니다.'}
                            </p>
                            {canStartFinalQuiz && weekData && (
                                <button
                                    className="weekly-quiz-card__btn"
                                    style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                                    onClick={() => open('final-quiz', {
                                        type:   'final-quiz',
                                        roomId,
                                        weekId: weekData.weekId,
                                    })}
                                >
                                    퀴즈 도전하기
                                    <ArrowRight size={13} strokeWidth={1.5} />
                                </button>
                            )}
                        </div>

                        {/* 연결된 스터디 파트너 */}
                        {partnerConnected && (
                            <div className="partner-widget">
                                <div
                                    className="partner-widget__title"
                                    style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                                >
                                    <Users size={15} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                                    연결된 스터디 파트너
                                </div>
                                <div className="partner-widget__profile">
                                    <div className="partner-widget__avatar">
                                        <UserCircle size={32} strokeWidth={1.5} style={{ color: 'var(--color-purple-400)' }} />
                                    </div>
                                    <div className="partner-widget__info">
                                        <div className="partner-widget__name">파트너 연결됨</div>
                                        <div className="partner-widget__role">스터디 매칭</div>
                                    </div>
                                </div>
                                <button
                                    className="partner-widget__msg-btn"
                                    style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                                >
                                    <MessageCircle size={14} strokeWidth={1.5} />
                                    메시지 보내기
                                </button>
                            </div>
                        )}
                    </div>
                </aside>
            </div>

            {/* ── 모달 ── */}
            <ClassroomModals />
        </>
    )
}

// ── 기본 export: ClassroomModalProvider로 내부 컴포넌트를 감싼다 ────────────────
export default function StudyClassroom() {
    return (
        <ClassroomModalProvider>
            <StudyClassroomContent />
        </ClassroomModalProvider>
    )
}
