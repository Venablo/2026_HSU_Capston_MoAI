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
    Moon, Sun, X, Check, LogOut, Send,
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
    getQuizAttemptDetail,
    updateProgress,
    getNotifications,
    markNotificationRead,
    updateProfile,
    logout,
    connectNotificationStream,
    requestStudyMatch,
    connectGroupChat,
    getGroupMessages,
    getStudyGroup,
    getStudySuggestions,
} from '../../../services/apiService'
import { MoaiApiError } from '../../../api/axios'
import type {
    EventType,
    LearningEventPayload,
    CurriculumWeekDetail,
    CurriculumWeekSummary,
    RecommendedVideo,
    QuizAttemptListItem,
    QuizAttemptDetail,
    NotificationItem,
    ChatMessage,
    WsChatSend,
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
    if (type === 'md')   return <FileEdit size={20} strokeWidth={1.5} />
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

    // 잠금 상태: 초기 로드 시 설정된 현재 진행 가능한 최대 weekNumber
    const [unlockedUpToWeekNumber, setUnlockedUpToWeekNumber] = useState<number | null>(null)
    // 잠긴 주차 접근 시 표시되는 토스트 메시지
    const [lockedToast, setLockedToast] = useState<string | null>(null)

    // 탭별 데이터 (lazy-load)
    const [videos,        setVideos]        = useState<RecommendedVideo[] | null>(null)
    const [videosLoading, setVideosLoading] = useState(false)
    const [quizAttempts,  setQuizAttempts]  = useState<QuizAttemptListItem[] | null>(null)
    const [quizLoading,   setQuizLoading]   = useState(false)

    // 아코디언 / 더 보기 UI 상태
    const [keywordsExpanded,  setKeywordsExpanded]  = useState(false)
    const [quizCorrectOpen,   setQuizCorrectOpen]   = useState(true)
    const [quizWrongOpen,     setQuizWrongOpen]     = useState(true)

    // 영상 진행률 로컬 추적 — 서버 응답에 상관없이 즉각 반영
    const [videoProgress, setVideoProgress] = useState(0)

    // 키워드 상세 확장 상태
    const [expandedKeyword, setExpandedKeyword] = useState<string | null>(null)

    // 퀴즈 내역 상세 확장 + 캐시
    const [expandedQuizId,    setExpandedQuizId]    = useState<string | null>(null)
    const [quizDetailCache,   setQuizDetailCache]   = useState<Record<string, QuizAttemptDetail>>({})
    const [quizDetailLoading, setQuizDetailLoading] = useState<string | null>(null)

    // 스터디 파트너 채팅 UI 상태
    interface ChatMsg { id: number; role: 'me' | 'partner'; text: string; time: string }
    const [chatOpen,     setChatOpen]     = useState(false)
    const [chatMessages, setChatMessages] = useState<ChatMsg[]>([])
    const [chatInput,    setChatInput]    = useState('')
    const chatBottomRef = useRef<HTMLDivElement | null>(null)
    const wsRef         = useRef<WebSocket | null>(null)

    const { open, metacogComplete, partnerConnected, partnerInfo, setCurrentWeekId, setMetacogComplete, quizSubmitted, setQuizSubmitted, matchStatus, setMatchStatus, setPartnerInfo, groupId, setGroupId, setPartnerConnected } = useClassroomModal()

    // ── 마운트 시 localStorage의 매칭 상태를 백엔드로 검증 ───────────────────
    // DB 기록을 직접 삭제하거나 매칭이 해소된 경우 프론트 상태를 자동 초기화한다.
    useEffect(() => {
        const resetMatchState = () => {
            setMatchStatus('idle')
            setPartnerConnected(false)
            setPartnerInfo(null)
            setGroupId(null)
        }

        if (matchStatus === 'completed' && groupId) {
            getStudyGroup(groupId).catch(() => resetMatchState())
        } else if (matchStatus === 'pending') {
            getStudySuggestions()
                .then(list => { if (list.length === 0) resetMatchState() })
                .catch(() => resetMatchState())
        } else if (matchStatus === 'searching') {
            resetMatchState()
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    interface MatchToastState { message: string; type: 'success' | 'warning' | 'error'; exiting: boolean }
    const [matchToast,   setMatchToast]   = useState<MatchToastState | null>(null)
    const toastTimerRef    = useRef<number | null>(null)
    const matchTimeoutRef  = useRef<number | null>(null)
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
                setUnlockedUpToWeekNumber(active.weekNumber)
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

        // 롤백용 스냅샷 — 403 발생 시 이전 상태로 복원
        const prevWeekData      = weekData
        const prevVideos        = videos
        const prevQuizAttempts  = quizAttempts
        const prevQuizSubmitted = quizSubmitted
        const prevVideoProgress = videoProgress

        setWeekDropdown(false)
        setWeekLoading(true)
        setWeekError(null)
        setVideos(null)
        setQuizAttempts(null)
        setMetacogComplete(false)
        setQuizSubmitted(false)
        setVideoProgress(0)
        setExpandedKeyword(null)
        setExpandedQuizId(null)
        setQuizDetailCache({})
        getCurriculumWeek(roomId, weekId)
            .then(detail => {
                applyWeekDetail(detail)
            })
            .catch(e => {
                const isLocked =
                    e instanceof MoaiApiError &&
                    e.status === 403 &&
                    (e.errorCode === 'CURRICULUM_002' || e.errorCode === 'PREVIOUS_WEEK_NOT_COMPLETED')

                if (isLocked) {
                    // 이전 주차 상태 복원 — UI 데드락 방지
                    if (prevWeekData) {
                        setWeekData(prevWeekData)
                        setCurrentWeekId(prevWeekData.weekId)
                        setMetacogComplete((Number(prevWeekData.completionRate) || 0) >= 70)
                    }
                    setVideos(prevVideos)
                    setQuizAttempts(prevQuizAttempts)
                    setQuizSubmitted(prevQuizSubmitted)
                    setVideoProgress(prevVideoProgress)
                    setLockedToast('아직 잠긴 주차입니다. 이전 주차를 먼저 완료해주세요.')
                } else {
                    setWeekError(e instanceof Error ? e.message : '주차 데이터를 불러오지 못했습니다.')
                }
            })
            .finally(() => setWeekLoading(false))
    }, [roomId, weekData, videos, quizAttempts, quizSubmitted, videoProgress, applyWeekDetail, setCurrentWeekId, setMetacogComplete, setQuizSubmitted])

    // ── 잠긴 주차 토스트 자동 닫기 ───────────────────────────────────────────
    useEffect(() => {
        if (!lockedToast) return
        const t = window.setTimeout(() => setLockedToast(null), 4000)
        return () => window.clearTimeout(t)
    }, [lockedToast])

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
                        reason:      result.eventType === 'video_rewind'
                            ? '해당 구간을 3회 이상 뒤로 감기 하신 것을 감지했어요. AI가 3분 만에 이해할 수 있는 핵심 요약본과 추천 영상을 준비해 두었습니다.'
                            : result.eventType === 'video_pause'
                                ? '영상을 일시정지하신 것을 감지했어요. 이해가 어려운 부분이 있다면 AI 요약본을 확인해 보세요.'
                                : '학습 중 이탈을 감지했어요. 집중도 유지를 위한 AI 핵심 요약본을 준비했습니다.',
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
        // 로컬 진행률 즉시 업데이트 (서버 응답에 의존하지 않음)
        setVideoProgress(prev => Math.max(prev, rate))
        // 서버에도 동기 (서버 응답값이 낮더라도 로컬 videoProgress는 유지됨)
        const currentRate = Number(weekData.completionRate) || 0
        if (rate <= currentRate) return
        updateProgress(roomId, weekData.weekId, { completionRate: rate })
            .then(res => {
                setWeekData(prev => prev
                    ? { ...prev, completionRate: Math.max(Number(prev.completionRate) || 0, res.completionRate) }
                    : prev)
            })
            .catch(() => {})
    }, [roomId, weekData])

    // ── 스터디 매칭 신청 (사이드바 버튼) ────────────────────────────────────
    const handleRequestMatching = useCallback(async () => {
        if (!weekData?.weekId) return
        try {
            await requestStudyMatch(roomId, weekData.weekId)
            // 202 Accepted — 백그라운드 매칭 시작. SSE로 결과 수신.
            setMatchStatus('searching')
            // 3분 프런트엔드 타임아웃 — 백엔드 자동 취소가 없으므로 프런트에서 보장
            if (matchTimeoutRef.current) window.clearTimeout(matchTimeoutRef.current)
            matchTimeoutRef.current = window.setTimeout(() => {
                setMatchStatus('idle')
                showMatchToast('⚠️ 매칭 대기 시간이 초과되었습니다. 다시 시도해 주세요.', 'warning')
            }, 180_000)
        } catch (e) {
            if (e instanceof MoaiApiError && e.status === 403) {
                showMatchToast('스터디 제안이 비활성화되어 있습니다. 마이페이지 → 설정에서 스터디 제안을 켜주세요.', 'error')
            } else {
                showMatchToast('스터디 매칭 요청에 실패했습니다. 잠시 후 다시 시도해주세요.', 'error')
            }
        }
    }, [roomId, weekData?.weekId, setMatchStatus])

    // ── 매칭 토스트 표시 / 닫기 ─────────────────────────────────────────────
    const showMatchToast = useCallback((message: string, type: MatchToastState['type']) => {
        if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current)
        setMatchToast({ message, type, exiting: false })
        // 3.7 s 후 exit 애니메이션 시작 → 300 ms 뒤 DOM에서 제거 (합계 4 s)
        toastTimerRef.current = window.setTimeout(() => {
            setMatchToast(prev => prev ? { ...prev, exiting: true } : null)
            toastTimerRef.current = window.setTimeout(() => setMatchToast(null), 300)
        }, 3700)
    }, [])

    const dismissMatchToast = useCallback(() => {
        if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current)
        setMatchToast(prev => prev ? { ...prev, exiting: true } : null)
        toastTimerRef.current = window.setTimeout(() => setMatchToast(null), 260)
    }, [])

    // ── SSE 알림 스트림 — study_match / study_no_candidate 수신 ──────────────
    // Refs keep latest callbacks without triggering reconnect on every render
    const openRef             = useRef(open)
    const setPartnerInfoRef   = useRef(setPartnerInfo)
    const setMatchStatusRef   = useRef(setMatchStatus)
    const showMatchToastRef   = useRef(showMatchToast)
    openRef.current           = open
    setPartnerInfoRef.current = setPartnerInfo
    setMatchStatusRef.current = setMatchStatus
    showMatchToastRef.current = showMatchToast

    useEffect(() => {
        const sse = connectNotificationStream()

        const handleStudyMatch = (e: MessageEvent) => {
            try {
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const data = JSON.parse(e.data as string) as any
                const match = {
                    partnerId:        String(data.suggestionId ?? ''),
                    partnerName:      String(data.partner?.nickname ?? '파트너'),
                    partnerAvatar:    String(data.partner?.nickname ?? '파').charAt(0).toUpperCase(),
                    partnerRole:      (data.partner?.role === 'mentor' ? 'mentor' : 'mentee') as 'mentor' | 'mentee',
                    matchRate:        Math.round(Number(data.matchScore ?? 0) * 100),
                    partnerStrengths: data.partner?.strengthKeyword ? [String(data.partner.strengthKeyword)] : [],
                }
                if (matchTimeoutRef.current) { window.clearTimeout(matchTimeoutRef.current); matchTimeoutRef.current = null }
                setPartnerInfoRef.current(match)
                setMatchStatusRef.current('pending')
                showMatchToastRef.current('🎉 멘토를 찾았습니다! 매칭 정보를 확인해주세요.', 'success')
                openRef.current('study-matching', { type: 'study-matching', match })
            } catch {}
        }

        const handleNoCandidate = () => {
            if (matchTimeoutRef.current) { window.clearTimeout(matchTimeoutRef.current); matchTimeoutRef.current = null }
            showMatchToastRef.current(
                '⚠️ 현재 매칭 가능한 파트너가 없습니다. 잠시 후 다시 시도해 주세요.',
                'warning',
            )
            setMatchStatusRef.current('idle')
        }

        // 상대방이 수락하면 백엔드가 groupId를 포함한 study_accepted SSE를 푸시한다.
        const handleStudyAccepted = (e: MessageEvent) => {
            try {
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const data = JSON.parse(e.data as string) as any
                if (data.groupId) setGroupId(String(data.groupId))
            } catch {}
        }

        // Named-event listeners (fires when backend sends `event: study_match` header)
        sse.addEventListener('study_match',        handleStudyMatch    as EventListener)
        sse.addEventListener('study_no_candidate', handleNoCandidate)
        sse.addEventListener('study_accepted',     handleStudyAccepted as EventListener)

        // 백엔드가 `event: notification` 으로 전송하는 경우 — data.type 으로 분기
        const dispatchByType = (e: MessageEvent) => {
            try {
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const data = JSON.parse(e.data as string) as any
                const type = String(data.type ?? '')
                if      (type === 'study_match')        handleStudyMatch(e)
                else if (type === 'study_no_candidate') handleNoCandidate()
                else if (type === 'study_accepted')     handleStudyAccepted(e)
            } catch {}
        }
        sse.addEventListener('notification', dispatchByType as EventListener)

        // Unnamed-event fallback (fires when backend sends only `data:` with no `event:` line).
        sse.onmessage = dispatchByType

        return () => {
            sse.close()
            if (matchTimeoutRef.current) { window.clearTimeout(matchTimeoutRef.current); matchTimeoutRef.current = null }
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    // ── WebSocket 채팅 — groupId 확정 시 연결 ──────────────────────────────────
    useEffect(() => {
        if (!groupId) return

        // 이전 대화 이력 로드
        getGroupMessages(groupId)
            .then(msgs => {
                setChatMessages(msgs.map((m: ChatMessage) => ({
                    id:   Number(m.messageId) || Date.now() + Math.random(),
                    role: m.senderNickname === nickname ? 'me' : 'partner',
                    text: m.content,
                    time: new Date(m.sentAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }),
                })))
            })
            .catch(() => {})

        // WebSocket 연결
        const ws = connectGroupChat(groupId)
        wsRef.current = ws

        ws.onmessage = (e) => {
            try {
                const msg = JSON.parse(e.data as string) as ChatMessage
                setChatMessages(prev => [...prev, {
                    id:   Number(msg.messageId) || Date.now(),
                    role: msg.senderNickname === nickname ? 'me' : 'partner',
                    text: msg.content,
                    time: new Date(msg.sentAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }),
                }])
            } catch {}
        }

        return () => { ws.close(); wsRef.current = null }
    }, [groupId, nickname])

    // ── 채팅 자동 스크롤 ────────────────────────────────────────────────────
    useEffect(() => {
        chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }, [chatMessages])

    // ── 채팅 메시지 전송 ────────────────────────────────────────────────────
    // groupId가 확정된 경우 WebSocket으로 전송. 백엔드가 모든 참여자에게 에코하므로
    // 낙관적 추가 없이 onmessage에서만 chatMessages를 업데이트한다.
    const handleSendChat = useCallback(() => {
        const text = chatInput.trim()
        if (!text) return
        if (wsRef.current?.readyState === WebSocket.OPEN) {
            wsRef.current.send(JSON.stringify({ content: text } satisfies WsChatSend))
        }
        setChatInput('')
    }, [chatInput])

    // ── 퀴즈 상세 확장 핸들러 ────────────────────────────────────────────────
    const handleExpandQuiz = useCallback(async (attemptId: string) => {
        if (expandedQuizId === attemptId) { setExpandedQuizId(null); return }
        setExpandedQuizId(attemptId)
        if (quizDetailCache[attemptId]) return
        setQuizDetailLoading(attemptId)
        try {
            const detail = await getQuizAttemptDetail(attemptId)
            setQuizDetailCache(prev => ({ ...prev, [attemptId]: detail }))
        } catch {
            // 상세 조회 실패 시 조용히 무시
        } finally {
            setQuizDetailLoading(null)
        }
    }, [expandedQuizId, quizDetailCache])

    // ── YouTube IFrame API 훅 ─────────────────────────────────────────────────
    const { playerHostRef } = useYouTubePlayer({
        videoId:             activeVideoId,
        onPatternDetected:   handlePatternDetected,
        onProgressMilestone: handleProgressMilestone,
    })

    const isWeekLocked = (w: CurriculumWeekSummary) =>
        unlockedUpToWeekNumber !== null && w.weekNumber > unlockedUpToWeekNumber

    // progress: 서버 값과 로컬 추적 값 중 높은 것을 표시 (서버 캡 우회)
    const progress = Math.max(Number(weekData?.completionRate) || 0, videoProgress)
    const safeResources = weekData?.resources ?? []
    const safeKeywords = weekData?.keywords ?? []
    const canStartMetacog = Boolean(weekData && activeVideoId && progress >= 100)
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
                                    if (e.key === 'Enter') {
                                        const first = filteredWeeks.find(w => !isWeekLocked(w))
                                        if (first) {
                                            handleWeekSwitch(first.weekId)
                                            setSearchValue('')
                                            setSearchFocus(false)
                                        }
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
                                ) : filteredWeeks.map(w => {
                                    const locked = isWeekLocked(w)
                                    return (
                                        <button key={w.weekId}
                                            onClick={() => {
                                                if (!locked) {
                                                    handleWeekSwitch(w.weekId)
                                                    setSearchValue('')
                                                    setSearchFocus(false)
                                                }
                                            }}
                                            style={{
                                                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                                width: '100%', padding: '9px 14px', background: 'none', border: 'none',
                                                textAlign: 'left', fontSize: '13px',
                                                cursor: locked ? 'not-allowed' : 'pointer',
                                                opacity: locked ? 0.45 : 1,
                                                color: locked ? 'var(--color-text-muted)' : 'var(--color-text-primary)',
                                            }}>
                                            <span style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                                                {locked && <Lock size={11} strokeWidth={1.5} />}
                                                Week {w.weekNumber}: {w.topic}
                                            </span>
                                            <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)', marginLeft: '8px' }}>{Number(w.completionRate).toFixed(0)}%</span>
                                        </button>
                                    )
                                })}
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
                                    const locked = isWeekLocked(w)
                                    return (
                                        <button
                                            key={w.weekId}
                                            onClick={() => !locked && handleWeekSwitch(w.weekId)}
                                            style={{
                                                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                                width: '100%', padding: '10px 16px', border: 'none',
                                                textAlign: 'left', fontSize: '13px',
                                                cursor: locked ? 'not-allowed' : 'pointer',
                                                opacity: locked ? 0.45 : 1,
                                                color: locked
                                                    ? 'var(--color-text-muted, #9ca3af)'
                                                    : w.weekId === weekData?.weekId
                                                        ? 'var(--color-purple-600, #7c3aed)'
                                                        : 'var(--color-text-primary, #111)',
                                                background: w.weekId === weekData?.weekId ? 'var(--color-purple-50, #f5f3ff)' : 'transparent',
                                                fontWeight: w.weekId === weekData?.weekId ? 600 : 400,
                                            }}
                                        >
                                            <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                                {locked && <Lock size={12} strokeWidth={1.5} />}
                                                Week {w.weekNumber}: {w.topic}
                                            </span>
                                            <span style={{ fontSize: '11px', color: 'var(--color-text-secondary, #6b7280)', marginLeft: '8px' }}>
                                                {Number(w.completionRate).toFixed(0)}%
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
                                : safeResources.map((res, i) => {
                                    const viewUrl = resolveResourceUrl(res.url)
                                    const downloadUrl = withDownloadParam(viewUrl)

                                    if (res.type === 'md') {
                                        const mdViewerUrl = `/md-viewer?url=${encodeURIComponent(viewUrl)}`
                                        return (
                                        <a
                                            key={i}
                                            href={mdViewerUrl}
                                            target="_blank"
                                            rel="noreferrer"
                                            className="classroom__doc-item"
                                            style={{ textDecoration: 'none', color: 'inherit', cursor: 'pointer' }}
                                        >
                                            <div className="classroom__doc-icon" style={{ color: 'var(--color-purple-500)' }}>
                                                {resourceIcon(res.type)}
                                            </div>
                                            <div className="classroom__doc-info">
                                                <div className="classroom__doc-name">{res.title}</div>
                                                <div className="classroom__doc-meta">{res.size} · {res.type.toUpperCase()}</div>
                                            </div>
                                            <span className="classroom__doc-download">보기</span>
                                        </a>
                                        )
                                    }

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
                                        <div className="classroom__doc-icon" style={{ color: 'var(--color-purple-500)' }}>
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
                                })
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
                                : recommendedList.map((v, i) => (
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
                                ))
                            }
                        </div>
                    )}

                    {/* 탭: AI 핵심 요약 (주차 keywords) — 클릭 시 상세 확장 */}
                    {tab === 'summary' && (
                        <div className="classroom__summary">
                            <div
                                className="classroom__summary-heading"
                                style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                            >
                                <Zap size={16} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                                핵심 키워드
                                {safeKeywords.length > 0 && (
                                    <span style={{ marginLeft: 'auto', fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: 400 }}>
                                        총 {safeKeywords.length}개 · 클릭하면 상세 보기
                                    </span>
                                )}
                            </div>
                            {weekLoading ? <TabLoading /> :
                            safeKeywords.length === 0
                                ? <TabEmpty message="키워드 데이터가 없습니다." />
                                : (() => {
                                    const PREVIEW = 4
                                    const shown = keywordsExpanded ? safeKeywords : safeKeywords.slice(0, PREVIEW)
                                    const hasMore = safeKeywords.length > PREVIEW
                                    return (
                                        <>
                                            {shown.map((kw, i) => {
                                                const isOpen = expandedKeyword === kw
                                                return (
                                                    <div key={i} className={`classroom__kw-item${isOpen ? ' classroom__kw-item--open' : ''}`}>
                                                        <button
                                                            className="classroom__kw-row"
                                                            onClick={() => setExpandedKeyword(isOpen ? null : kw)}
                                                        >
                                                            <div className="classroom__kw-dot" />
                                                            <span className="classroom__kw-text">{kw}</span>
                                                            <ChevronRight
                                                                size={13}
                                                                strokeWidth={2}
                                                                className="classroom__kw-chevron"
                                                                style={{ transform: isOpen ? 'rotate(90deg)' : 'none' }}
                                                            />
                                                        </button>
                                                        {isOpen && (
                                                            <div className="classroom__kw-detail">
                                                                <div className="classroom__kw-detail-label">
                                                                    Week {weekData?.weekNumber} · {weekData?.topic}
                                                                </div>
                                                                <p className="classroom__kw-detail-desc">
                                                                    {weekData?.description || '이번 주차의 핵심 학습 개념입니다.'}
                                                                </p>
                                                            </div>
                                                        )}
                                                    </div>
                                                )
                                            })}
                                            {hasMore && (
                                                <button
                                                    className="classroom__accordion-toggle"
                                                    onClick={() => setKeywordsExpanded(v => !v)}
                                                >
                                                    {keywordsExpanded
                                                        ? '접기 ↑'
                                                        : `더 보기 +${safeKeywords.length - PREVIEW}개`}
                                                </button>
                                            )}
                                        </>
                                    )
                                })()
                            }
                        </div>
                    )}

                    {/* 탭: 퀴즈 내역 — 정답/오답 그룹 + 클릭 시 상세 확장 */}
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
                                                borderRadius: '6px', border: '1px solid var(--color-purple-200)',
                                                background: 'transparent', color: 'var(--color-purple-600)',
                                                cursor: 'pointer',
                                            }}
                                        >
                                            ← 학습으로 돌아가기
                                        </button>
                                    </div>
                                )
                                const correctItems = list.filter(q => q.isCorrect)
                                const wrongItems   = list.filter(q => !q.isCorrect)

                                const renderQuizItem = (item: QuizAttemptListItem, i: number) => {
                                    const isOpen   = expandedQuizId === item.attemptId
                                    const isLoading = quizDetailLoading === item.attemptId
                                    const detail   = quizDetailCache[item.attemptId]
                                    return (
                                        <div key={item.attemptId} className={`classroom__quiz-item${isOpen ? ' classroom__quiz-item--open' : ''}`}>
                                            <button
                                                className="classroom__quiz-row classroom__quiz-row--clickable"
                                                onClick={() => handleExpandQuiz(item.attemptId)}
                                            >
                                                <div className="classroom__quiz-q">Q{i + 1}. {item.questionTitle}</div>
                                                <div className="classroom__quiz-result-row">
                                                    <span className={`classroom__quiz-badge ${item.isCorrect ? 'classroom__quiz-badge--correct' : 'classroom__quiz-badge--wrong'}`}>
                                                        {item.isCorrect ? '정답' : '오답'}
                                                    </span>
                                                    <ChevronRight
                                                        size={13}
                                                        strokeWidth={2}
                                                        style={{ color: 'var(--color-text-muted)', transform: isOpen ? 'rotate(90deg)' : 'none', transition: 'transform .2s', flexShrink: 0 }}
                                                    />
                                                </div>
                                            </button>

                                            {isOpen && (
                                                <div className="classroom__quiz-detail">
                                                    {isLoading ? (
                                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '12px 0', color: 'var(--color-text-muted)', fontSize: '12px' }}>
                                                            <Loader2 size={14} strokeWidth={2} className="animate-spin" />
                                                            상세 정보 불러오는 중...
                                                        </div>
                                                    ) : detail ? (
                                                        <>
                                                            <p className="classroom__quiz-detail-question">{detail.question}</p>
                                                            {detail.options.length > 0 && (
                                                                <div className="classroom__quiz-detail-options">
                                                                    {detail.options.map(opt => {
                                                                        const isMyAnswer      = opt.label === detail.myAnswer || opt.text === detail.myAnswer
                                                                        const isCorrectAnswer = opt.label === detail.correctAnswer || opt.text === detail.correctAnswer
                                                                        return (
                                                                            <div
                                                                                key={opt.label}
                                                                                className={`classroom__quiz-detail-opt ${
                                                                                    isCorrectAnswer ? 'classroom__quiz-detail-opt--correct'
                                                                                    : isMyAnswer    ? 'classroom__quiz-detail-opt--wrong'
                                                                                    : ''
                                                                                }`}
                                                                            >
                                                                                <span className="classroom__quiz-detail-opt-label">{opt.label}</span>
                                                                                <span>{opt.text}</span>
                                                                                {isCorrectAnswer && <span className="classroom__quiz-detail-badge classroom__quiz-detail-badge--correct">정답</span>}
                                                                                {isMyAnswer && !isCorrectAnswer && <span className="classroom__quiz-detail-badge classroom__quiz-detail-badge--wrong">내 답</span>}
                                                                            </div>
                                                                        )
                                                                    })}
                                                                </div>
                                                            )}
                                                            {detail.options.length === 0 && (
                                                                <div className="classroom__quiz-detail-essay">
                                                                    <div className="classroom__quiz-detail-essay-row">
                                                                        <span className="classroom__quiz-detail-essay-label">내 답변</span>
                                                                        <span className={detail.isCorrect ? 'classroom__quiz-detail-essay-ans--correct' : 'classroom__quiz-detail-essay-ans--wrong'}>{detail.myAnswer || '(미작성)'}</span>
                                                                    </div>
                                                                    {!detail.isCorrect && (
                                                                        <div className="classroom__quiz-detail-essay-row">
                                                                            <span className="classroom__quiz-detail-essay-label">정답</span>
                                                                            <span className="classroom__quiz-detail-essay-ans--correct">{detail.correctAnswer}</span>
                                                                        </div>
                                                                    )}
                                                                </div>
                                                            )}
                                                            {detail.aiExplanation && (
                                                                <div className="classroom__quiz-detail-explain">
                                                                    <span className="classroom__quiz-detail-explain-label">AI 해설</span>
                                                                    <p className="classroom__quiz-detail-explain-text">{detail.aiExplanation}</p>
                                                                </div>
                                                            )}
                                                        </>
                                                    ) : (
                                                        <p style={{ fontSize: '12px', color: 'var(--color-text-muted)', padding: '8px 0' }}>상세 정보를 불러오지 못했습니다.</p>
                                                    )}
                                                </div>
                                            )}
                                        </div>
                                    )
                                }

                                const renderGroup = (
                                    items: typeof list,
                                    label: string,
                                    isOpen: boolean,
                                    toggle: () => void,
                                    colorClass: string,
                                ) => items.length === 0 ? null : (
                                    <div className="classroom__quiz-group">
                                        <button
                                            className={`classroom__quiz-group-header ${colorClass}`}
                                            onClick={toggle}
                                        >
                                            <span>{label} ({items.length})</span>
                                            <ChevronRight
                                                size={14}
                                                strokeWidth={2}
                                                style={{ transform: isOpen ? 'rotate(90deg)' : 'none', transition: 'transform .2s' }}
                                            />
                                        </button>
                                        {isOpen && items.map((item, i) => renderQuizItem(item, i))}
                                    </div>
                                )
                                return (
                                    <>
                                        {renderGroup(correctItems, '✓ 정답', quizCorrectOpen, () => setQuizCorrectOpen(v => !v), 'classroom__quiz-group-header--correct')}
                                        {renderGroup(wrongItems,   '✗ 오답', quizWrongOpen,   () => setQuizWrongOpen(v => !v),   'classroom__quiz-group-header--wrong')}
                                    </>
                                )
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
                                {canStartMetacog ? (
                                    <>
                                        <p className="metacog-card__desc">
                                            방금 배운 내용을 AI에게 소리 내어 설명해보세요. 이해도를 실시간 분석해 드립니다.
                                        </p>
                                        <button
                                            className="metacog-card__btn"
                                            onClick={() => open('reverse-learning', {
                                                type:        'reverse-learning',
                                                conceptName: weekData?.topic ?? '',
                                                roomId,
                                                weekId:      weekData?.weekId,
                                            })}
                                        >
                                            AI에게 설명하기
                                        </button>
                                    </>
                                ) : (
                                    <p className="metacog-card__status">
                                        영상을 100% 시청한 후 시작할 수 있습니다.{' '}
                                        {progress > 0 && progress < 100 && `(현재 ${progress}%)`}
                                    </p>
                                )}
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
                                {!partnerConnected && (
                                    matchStatus === 'searching' ? (
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '10px' }}>
                                            <Search size={14} strokeWidth={2} className="animate-spin" style={{ color: '#ffffff' }} />
                                            <span style={{ fontSize: '12px', color: '#ffffff', fontWeight: 600 }}>
                                                파트너 매칭 중...
                                            </span>
                                            <button
                                                onClick={() => {
                                                    if (matchTimeoutRef.current) { window.clearTimeout(matchTimeoutRef.current); matchTimeoutRef.current = null }
                                                    setMatchStatus('idle')
                                                }}
                                                style={{
                                                    marginLeft: '4px',
                                                    padding: '2px 8px',
                                                    fontSize: '11px',
                                                    fontWeight: 600,
                                                    color: '#ffffff',
                                                    background: 'rgba(255,255,255,0.18)',
                                                    border: '1px solid rgba(255,255,255,0.45)',
                                                    borderRadius: '4px',
                                                    cursor: 'pointer',
                                                    lineHeight: 1.6,
                                                }}
                                            >
                                                취소
                                            </button>
                                        </div>
                                    ) : matchStatus === 'pending' && partnerInfo ? (
                                        <div style={{ marginTop: '10px' }}>
                                            <div style={{ fontSize: '12px', color: '#ffffff', fontWeight: 600, marginBottom: '6px' }}>
                                                🎉 파트너를 찾았습니다!
                                            </div>
                                            <button
                                                className="metacog-card__btn"
                                                style={{ marginTop: '0', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                                                onClick={() => open('study-matching', { type: 'study-matching', match: partnerInfo })}
                                            >
                                                <CheckCircle2 size={14} strokeWidth={2} />
                                                매칭 수락/거절하기
                                            </button>
                                        </div>
                                    ) : (
                                        <button
                                            className="metacog-card__btn"
                                            style={{ marginTop: '10px', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                                            onClick={handleRequestMatching}
                                        >
                                            <Users size={14} strokeWidth={2} />
                                            멘토에게 스터디 요청하기
                                        </button>
                                    )
                                )}
                            </div>
                        )}

                        {/* 주간 파이널 퀴즈 카드 */}
                        <div className={`weekly-quiz-card${(canStartFinalQuiz || quizSubmitted) ? ' weekly-quiz-card--active' : ' weekly-quiz-card--locked'}`}>
                            <div
                                className="weekly-quiz-card__title"
                                style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                            >
                                {(canStartFinalQuiz || quizSubmitted)
                                    ? <Trophy size={16} strokeWidth={1.5} />
                                    : <Lock   size={16} strokeWidth={1.5} />}
                                주간 최종 퀴즈
                            </div>
                            <p className="weekly-quiz-card__desc">
                                {quizSubmitted
                                    ? 'AI 종합 분석 리포트를 다시 확인해보세요.'
                                    : canStartFinalQuiz
                                        ? `Week ${weekData?.weekNumber ?? ''} 전체 내용 최종 평가! 도전해보세요.`
                                        : '메타인지 확인 완료 후 진행 가능합니다.'}
                            </p>
                            {weekData && (
                                <button
                                    className="weekly-quiz-card__btn"
                                    style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                                    disabled={!quizSubmitted && !canStartFinalQuiz}
                                    onClick={() => {
                                        if (quizSubmitted || canStartFinalQuiz) {
                                            open('final-quiz', {
                                                type:       'final-quiz',
                                                roomId,
                                                weekId:     weekData.weekId,
                                                ...(quizSubmitted ? { reviewMode: true } : {}),
                                            })
                                        }
                                    }}
                                >
                                    {quizSubmitted ? '퀴즈 결과 복습하기' : '퀴즈 도전하기'}
                                    <ArrowRight size={13} strokeWidth={1.5} />
                                </button>
                            )}
                        </div>

                        {/* 연결된 스터디 파트너 채팅 위젯 */}
                        {partnerConnected && (() => {
                            const partner = partnerInfo
                            const myRole  = partner?.partnerRole === 'mentor' ? '멘티' : '멘토'
                            const pName   = partner?.partnerName  ?? '파트너'
                            const pAvatar = partner?.partnerAvatar ?? '?'
                            const pRole   = partner?.partnerRole === 'mentor' ? '멘토' : '멘티'
                            return (
                                <div className="partner-widget">
                                    {/* 헤더: 온라인 상태 + 타이틀 */}
                                    <div className="partner-widget__header">
                                        <span className="partner-widget__online-dot" />
                                        <span className="partner-widget__header-text">스터디 파트너 연결됨</span>
                                    </div>

                                    {/* 프로필 */}
                                    <div className="partner-widget__profile">
                                        <div className="partner-widget__avatar partner-widget__avatar--text">
                                            {pAvatar}
                                        </div>
                                        <div className="partner-widget__info">
                                            <div className="partner-widget__name">{pName}</div>
                                            <div className="partner-widget__roles">
                                                <span className="partner-widget__role-badge partner-widget__role-badge--partner">{pRole}</span>
                                                <span className="partner-widget__role-sep">↔</span>
                                                <span className="partner-widget__role-badge partner-widget__role-badge--me">{myRole} (나)</span>
                                            </div>
                                        </div>
                                    </div>

                                    {/* 강점 태그 */}
                                    {partner?.partnerStrengths && partner.partnerStrengths.length > 0 && (
                                        <div className="partner-widget__strengths">
                                            {partner.partnerStrengths.map(s => (
                                                <span key={s} className="partner-widget__strength-tag">{s}</span>
                                            ))}
                                        </div>
                                    )}

                                    {/* 1:1 메시지 토글 버튼 */}
                                    <button
                                        className="partner-widget__msg-btn"
                                        style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                                        onClick={() => setChatOpen(v => !v)}
                                    >
                                        <MessageCircle size={14} strokeWidth={1.5} />
                                        {chatOpen ? '채팅 닫기' : '1:1 메시지 보내기'}
                                    </button>

                                    {/* 채팅 영역 */}
                                    {chatOpen && (
                                        <div className="partner-widget__chat">
                                            {!groupId ? (
                                                /* 상대방 수락 대기 */
                                                <p className="partner-widget__chat-placeholder" style={{ textAlign: 'center', padding: '16px 4px' }}>
                                                    <Loader2 size={14} strokeWidth={1.5} className="animate-spin" style={{ display: 'block', margin: '0 auto 6px' }} />
                                                    파트너가 수락하면 채팅이 열립니다.
                                                </p>
                                            ) : (
                                                <>
                                                    <div className="partner-widget__chat-log">
                                                        {chatMessages.length === 0 ? (
                                                            <p className="partner-widget__chat-placeholder">
                                                                연결된 스터디 파트너와 대화를 시작해보세요!
                                                                모르는 부분을 서로 질문하며 함께 학습할 수 있습니다.
                                                            </p>
                                                        ) : chatMessages.map(msg => (
                                                            <div
                                                                key={msg.id}
                                                                className={`partner-widget__chat-bubble-wrap ${msg.role === 'me' ? 'partner-widget__chat-bubble-wrap--me' : 'partner-widget__chat-bubble-wrap--partner'}`}
                                                            >
                                                                <div className={`partner-widget__chat-bubble ${msg.role === 'me' ? 'partner-widget__chat-bubble--me' : 'partner-widget__chat-bubble--partner'}`}>
                                                                    <span>{msg.text}</span>
                                                                    <span className="partner-widget__chat-time">{msg.time}</span>
                                                                </div>
                                                            </div>
                                                        ))}
                                                        <div ref={chatBottomRef} />
                                                    </div>

                                                    {/* 입력창 */}
                                                    <div className="partner-widget__chat-input-row">
                                                        <input
                                                            className="partner-widget__chat-input"
                                                            value={chatInput}
                                                            onChange={e => setChatInput(e.target.value)}
                                                            onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSendChat() } }}
                                                            placeholder="메시지 입력... (Enter 전송)"
                                                        />
                                                        <button
                                                            className={`partner-widget__chat-send ${chatInput.trim() ? 'partner-widget__chat-send--active' : ''}`}
                                                            onClick={handleSendChat}
                                                            disabled={!chatInput.trim()}
                                                        >
                                                            <Send size={13} strokeWidth={2} />
                                                        </button>
                                                    </div>
                                                </>
                                            )}
                                        </div>
                                    )}
                                </div>
                            )
                        })()}
                    </div>
                </aside>
            </div>

            {/* ── 모달 ── */}
            <ClassroomModals />

            {/* ── 잠긴 주차 토스트 알림 ── */}
            {lockedToast && (
                <div className="sc-locked-toast">
                    <Lock size={14} strokeWidth={1.5} style={{ color: '#f59e0b', flexShrink: 0 }} />
                    {lockedToast}
                </div>
            )}

            {/* ── 매칭 알림 top-toast ── */}
            {matchToast && (
                <div className={`sc-match-toast sc-match-toast--${matchToast.type}${matchToast.exiting ? ' sc-match-toast--exit' : ''}`}>
                    <span className="sc-match-toast__body">
                        <span className="sc-match-toast__title">{matchToast.message}</span>
                    </span>
                    <button className="sc-match-toast__close" onClick={dismissMatchToast} aria-label="닫기">
                        <X size={14} strokeWidth={2.5} />
                    </button>
                </div>
            )}
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
