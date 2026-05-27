import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import {
    ArrowLeft, Bell, BrainCircuit, Check, CheckCircle2, ChevronRight,
    FileEdit, FileText, Loader2, Lock, LogOut, MessageCircle, MessageSquare,
    Moon, Package, Pause, Play, Search, Send, Sun, Trophy,
    Users, UserCircle, X, Zap,
} from 'lucide-react'
import '../../../styles/FocusClassroom.css'
import { ThemeProvider, useTheme } from '../../../context/ThemeContext'
import { ClassroomModalProvider, useClassroomModal } from '../../../context/ClassroomModalContext'
import type { WeekMatchState } from '../../../context/ClassroomModalContext'
import ClassroomModals from '../../../components/modals/common/ClassroomModals'
import MarkdownContent from '../../../components/MarkdownContent'
import MarkdownViewer from '../../../components/MarkdownViewer'
import { useYouTubePlayer } from '../../../hooks/useYouTubePlayer'
import { useAuth } from '../../../context/AuthContext'
import { Client } from '@stomp/stompjs'
import {
    sendEventLog,
    getMaterialDetail,
    getInstantQuiz,
    getCurriculum,
    getCurriculumWeek,
    updateProgress,
    getNotifications,
    markNotificationRead,
    logout,
    connectNotificationStream,
    requestStudyMatch,
    getCurriculumKeywords,
    getQuizAttempts,
    getQuizAttemptDetail,
    getStudyGroup,
    getStudySuggestions,
    connectGroupChat,
    getGroupMessages,
} from '../../../services/apiService'
import { MoaiApiError } from '../../../api/axios'
import type {
    EventType,
    LearningEventPayload,
    ChatMessage,
    WsChatSend,
    CurriculumWeekDetail,
    CurriculumWeekSummary,
    NotificationItem,
    CurriculumKeywordsResponse,
    QuizAttemptListItem,
    QuizAttemptDetail,
    ResourceItem,
} from '../../../types/api'

// ── 유틸 ──────────────────────────────────────────────────────────────────────
function formatTime(sec: number): string {
    const m = Math.floor(sec / 60)
    const s = Math.floor(sec % 60)
    return `${m}:${s.toString().padStart(2, '0')}`
}

function resolveUrl(url: string): string {
    if (!url) return '#'
    if (/^https?:\/\//i.test(url)) return url
    const base = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '')
    if (url.startsWith('/api/') && base) return `${base}${url}`
    return url
}

function resourceIcon(type: string) {
    if (type === 'pdf')  return <FileText size={16} strokeWidth={1.5} />
    if (type === 'docx') return <FileEdit size={16} strokeWidth={1.5} />
    if (type === 'zip')  return <Package  size={16} strokeWidth={1.5} />
    return <FileEdit size={16} strokeWidth={1.5} />
}

function videoWatchRateToWeekProgress(rate: number): number {
    return Math.min(40, Number(((Math.max(0, Math.min(100, rate)) * 40) / 100).toFixed(2)))
}

// ── FocusClassroomContent ─────────────────────────────────────────────────────
function FocusClassroomContent() {
    const { studyId: roomId = '' } = useParams<{ studyId: string }>()
    const [searchParams] = useSearchParams()
    const targetCurriculumId = searchParams.get('curriculumId')
    const navigate = useNavigate()

    // ── Context / Auth / Theme ─────────────────────────────────────────────────
    const {
        open,
        metacogComplete,
        setCurrentWeekId,
        quizSubmitted,
        matchStatus, setMatchStatus,
        partnerConnected, partnerInfo, setPartnerInfo, setPartnerConnected,
        groupId, setGroupId,
        currentMatchKey, setCurrentMatchKey, setMatchStateForKey,
    } = useClassroomModal()

    const { nickname, refreshToken, clearAuth } = useAuth()
    const { darkMode, toggleDark } = useTheme()
    const avatarChar = nickname ? nickname.charAt(0).toUpperCase() : '?'

    // ── 주차 데이터 ──────────────────────────────────────────────────────────
    const [weekData,    setWeekData]    = useState<CurriculumWeekDetail | null>(null)
    const [weekLoading, setWeekLoading] = useState(true)
    const [weekError,   setWeekError]   = useState<string | null>(null)
    const [allWeeks,    setAllWeeks]    = useState<CurriculumWeekSummary[]>([])

    // ── 비디오 컨트롤 ─────────────────────────────────────────────────────────
    const [currentTime,  setCurrentTime]  = useState(0)
    const [duration,     setDuration]     = useState(0)
    const [isPlaying,    setIsPlaying]    = useState(false)
    const [playbackRate, setPlaybackRateUI] = useState(1)
    const [videoProgress, setVideoProgress] = useState(0)

    // ── UI 상태 ───────────────────────────────────────────────────────────────
    const [leftRatio,  setLeftRatio]  = useState(52)
    const [isDragging, setIsDragging] = useState(false)

    // 우측 패널 문서 선택
    const [selectedMdIdx, setSelectedMdIdx] = useState(0)

    // 좌측 패널 섹션 펼치기/접기
    const [quizHistoryOpen,  setQuizHistoryOpen]  = useState(false)
    const [keywordsOpen,     setKeywordsOpen]      = useState(true)
    const [keywordsExpanded, setKeywordsExpanded] = useState(false)

    // ── 퀴즈 이력 ─────────────────────────────────────────────────────────────
    const [quizAttempts,     setQuizAttempts]     = useState<QuizAttemptListItem[] | null>(null)
    const [quizLoading,      setQuizLoading]      = useState(false)
    const [expandedQuizId,   setExpandedQuizId]   = useState<string | null>(null)
    const [quizDetailCache,  setQuizDetailCache]  = useState<Record<string, QuizAttemptDetail>>({})
    const [quizDetailLoading, setQuizDetailLoading] = useState<string | null>(null)
    const [quizCompletedCount, setQuizCompletedCount] = useState(0)

    // ── 스터디 파트너 채팅 ────────────────────────────────────────────────────
    interface ChatMsg { id: number; role: 'me' | 'partner' | 'ai'; text: string; time: string }
    const [chatOpen,     setChatOpen]     = useState(false)
    const [chatMessages, setChatMessages] = useState<ChatMsg[]>([])
    const [chatInput,    setChatInput]    = useState('')
    const chatBottomRef = useRef<HTMLDivElement | null>(null)
    const stompRef      = useRef<Client | null>(null)

    // ── 알림 ─────────────────────────────────────────────────────────────────
    const [notifOpen,    setNotifOpen]    = useState(false)
    const [notifications, setNotifications] = useState<NotificationItem[]>([])
    const [notifLoading, setNotifLoading] = useState(false)
    const notifRef   = useRef<HTMLDivElement>(null)
    const profileRef = useRef<HTMLDivElement>(null)
    const [profileOpen, setProfileOpen] = useState(false)
    const unreadCount = notifications.filter(n => !n.isRead).length

    // ── 키워드 ───────────────────────────────────────────────────────────────
    const [userKeywords,    setUserKeywords]    = useState<CurriculumKeywordsResponse | null>(null)
    const [keywordsLoading, setKeywordsLoading] = useState(false)

    // ── Refs ─────────────────────────────────────────────────────────────────
    const bodyRef            = useRef<HTMLDivElement>(null)
    const pausePlayerRef     = useRef<() => void>(() => {})
    const seekPlayerRef      = useRef<(sec: number) => void>(() => {})
    const matchTimeoutRef    = useRef<number | null>(null)
    const matchRequestKeyRef = useRef<string | null>(null)
    const navigateRef        = useRef(navigate)
    useEffect(() => { navigateRef.current = navigate }, [navigate])
    const nicknameRef        = useRef(nickname)
    useEffect(() => { nicknameRef.current = nickname }, [nickname])
    // BUG-05: 타임아웃 시점의 실제 matchStatus를 읽기 위한 ref
    const matchStatusRef     = useRef(matchStatus)
    useEffect(() => { matchStatusRef.current = matchStatus }, [matchStatus])

    // ── 파생값 ───────────────────────────────────────────────────────────────
    const activeVideoId  = weekData?.mainVideoId ?? ''
    const progress       = Math.max(Number(weekData?.completionRate) || 0, videoProgress)
    const safeKeywords   = weekData?.keywords ?? []
    const safeResources: ResourceItem[] = weekData?.resources ?? []
    const mdResources    = safeResources.filter(r => r.type === 'md')
    const otherResources = safeResources.filter(r => r.type !== 'md')
    const activeMdUrl    = mdResources.length > 0 ? resolveUrl(mdResources[selectedMdIdx]?.url ?? '') : ''

    const canStartMetacog   = Boolean(weekData && activeVideoId && progress >= 40)
    const canStartFinalQuiz = Boolean(weekData && metacogComplete && progress >= 70)
    const safeUserKeywords  = userKeywords ?? { strengths: [], weaknesses: [] }

    // ── 주차 로드 ────────────────────────────────────────────────────────────
    const loadWeek = useCallback(async (weekId: string) => {
        if (!roomId) return
        setWeekLoading(true)
        setWeekError(null)
        setQuizAttempts(null)
        try {
            const data = await getCurriculumWeek(roomId, weekId)
            setWeekData(data)
            setCurrentWeekId(weekId)
            setCurrentMatchKey(`${roomId}_${weekId}`)
            setVideoProgress(0)
            setSelectedMdIdx(0)

            setKeywordsLoading(true)
            getCurriculumKeywords(roomId, weekId)
                .then(setUserKeywords).catch(() => {})
                .finally(() => setKeywordsLoading(false))
        } catch (e) {
            setWeekError(e instanceof MoaiApiError ? e.message : '데이터를 불러오지 못했습니다.')
        } finally {
            setWeekLoading(false)
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [roomId])

    // ── 커리큘럼 로드 ────────────────────────────────────────────────────────
    useEffect(() => {
        if (!roomId) return
        getCurriculum(roomId)
            .then(weeks => {
                setAllWeeks(weeks)
                const firstIncomplete = weeks.find(w => w.completionRate < 100)

                const target = targetCurriculumId
                    ? weeks.find(w => w.weekId === targetCurriculumId)
                    : (firstIncomplete ?? weeks[weeks.length - 1])
                if (target) loadWeek(target.weekId)
            })
            .catch(() => { setWeekError('커리큘럼을 불러오지 못했습니다.'); setWeekLoading(false) })
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [roomId])

    // ── 주차 전환 시 매칭 상태 검증 ──────────────────────────────────────────
    useEffect(() => {
        if (!currentMatchKey) return
        const reset = () => { setMatchStatus('idle'); setPartnerConnected(false); setPartnerInfo(null); setGroupId(null) }
        if (matchStatus === 'completed' && groupId) {
            getStudyGroup(groupId).catch(() => reset())
        } else if (matchStatus === 'pending') {
            getStudySuggestions().then(list => { if (list.length === 0) reset() }).catch(() => reset())
        } else if (matchStatus === 'searching' || matchStatus === 'waiting_partner') {
            reset()
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [currentMatchKey])

    // ── 퀴즈 완료 시 이력 갱신 ──────────────────────────────────────────────
    useEffect(() => {
        if (quizCompletedCount === 0 || !weekData) return
        getCurriculumKeywords(roomId, weekData.weekId).then(setUserKeywords).catch(() => {})
        if (quizHistoryOpen) {
            setQuizLoading(true)
            getQuizAttempts(roomId, weekData.weekId)
                .then(setQuizAttempts).catch(() => {}).finally(() => setQuizLoading(false))
        } else {
            setQuizAttempts(null) // 다음 열 때 새로 로드
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [quizCompletedCount])

    // ── 퀴즈 이력 토글 (lazy load) ───────────────────────────────────────────
    const handleToggleQuizHistory = useCallback(() => {
        const opening = !quizHistoryOpen
        setQuizHistoryOpen(opening)
        if (opening && quizAttempts === null && weekData) {
            setQuizLoading(true)
            getQuizAttempts(roomId, weekData.weekId)
                .then(setQuizAttempts).catch(() => setQuizAttempts([])).finally(() => setQuizLoading(false))
        }
    }, [quizHistoryOpen, quizAttempts, weekData, roomId])

    // ── 퀴즈 상세 확장 ───────────────────────────────────────────────────────
    const handleToggleQuizDetail = useCallback((attemptId: string) => {
        const next = expandedQuizId === attemptId ? null : attemptId
        setExpandedQuizId(next)
        if (next && !quizDetailCache[next] && !quizDetailLoading) {
            setQuizDetailLoading(next)
            getQuizAttemptDetail(next)
                .then(detail => setQuizDetailCache(prev => ({ ...prev, [next]: detail })))
                .catch(() => {})
                .finally(() => setQuizDetailLoading(null))
        }
    }, [expandedQuizId, quizDetailCache, quizDetailLoading])

    // ── 이벤트 로그 ─────────────────────────────────────────────────────────
    const handlePatternDetected = useCallback(async (eventType: EventType, payload: LearningEventPayload) => {
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
                    pausePlayerRef.current()
                    open('monitoring', {
                        type:        'monitoring',
                        conceptName: material.title,
                        reason:      result.eventType === 'video_rewind'
                            ? '해당 구간을 되감기 하신 것을 감지했어요. AI 핵심 요약본을 준비했습니다.'
                            : result.eventType === 'video_pause'
                                ? '영상을 일시정지하신 것을 감지했어요. AI 요약본을 확인해 보세요.'
                                : '학습 중 이탈을 감지했어요. 집중도 유지를 위한 AI 요약본을 준비했습니다.',
                        summaryItems: material.summaryItems.map(s => ({
                            letter:      s.label,
                            title:       s.title,
                            description: s.desc,
                        })),
                    })
                }
            } else if (result.eventType === 'video_skip' || result.eventType === 'video_speed_up') {
                const quiz = await getInstantQuiz(roomId, weekData.weekId)
                pausePlayerRef.current()
                open('quiz-pass', { type: 'quiz-pass', quiz })
            }
        } catch {
            // pattern detection errors are silent
        }

        if (['video_rewind', 'video_pause', 'tab_departure'].includes(eventType)) {
            getCurriculumKeywords(roomId, weekData.weekId).then(setUserKeywords).catch(() => {})
        }
    }, [weekData, roomId, open])

    const handleProgressMilestone = useCallback((rate: number) => {
        if (!weekData) return
        const contrib = videoWatchRateToWeekProgress(rate)
        setVideoProgress(prev => Math.max(prev, contrib))
        updateProgress(roomId, weekData.weekId, { completionRate: contrib }).catch(() => {})
    }, [weekData, roomId])

    // ── YouTube Player ────────────────────────────────────────────────────────
    const { playerHostRef, pausePlayer, playVideo, seekPlayer, setPlaybackRate } = useYouTubePlayer({
        videoId:             activeVideoId,
        onPatternDetected:   handlePatternDetected,
        onProgressMilestone: handleProgressMilestone,
        onTimeUpdate: (cur, dur, playing, rate) => {
            setCurrentTime(cur)
            setDuration(dur)
            setIsPlaying(playing)
            setPlaybackRateUI(rate)
        },
    })
    pausePlayerRef.current = pausePlayer
    seekPlayerRef.current  = seekPlayer

    // ── SSE ──────────────────────────────────────────────────────────────────
    useEffect(() => {
        const sse = connectNotificationStream()
        const handleGroupActivated = (e: MessageEvent) => {
            try {
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const data = JSON.parse(e.data as string) as any
                const activatedRoomId = String(data.roomId ?? '')
                const activatedCurriculumId = String(data.curriculumId ?? '')
                const activatedGroupId = data.groupId ? String(data.groupId) : null
                if (!activatedRoomId || !activatedCurriculumId) return
                setMatchStateForKey(
                    `${activatedRoomId}_${activatedCurriculumId}`,
                    (prev): WeekMatchState => ({
                        ...prev,
                        matchStatus: 'completed',
                        partnerConnected: true,
                        partnerInfo: null,
                        groupId: activatedGroupId,
                    }),
                )
                navigateRef.current(`/study/${activatedRoomId}/focus?curriculumId=${activatedCurriculumId}`)
            } catch { /* ignore malformed SSE data */ }
        }
        const dispatch = (e: MessageEvent) => {
            try {
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const data = JSON.parse(e.data as string) as any
                const type = String(data.type ?? '')
                if (type === 'study_group_activated') {
                    handleGroupActivated(e)
                } else if (type === 'week_unlocked') {
                    if (roomId) getCurriculum(roomId).then(setAllWeeks).catch(() => {})
                } else if (type === 'study_match') {
                    // BUG-04 fix: 파트너 매칭 제안 SSE 처리
                    // 백엔드가 매칭 상대를 찾아 보낸 알림 → pending 상태로 전환
                    if (matchStatusRef.current === 'searching') {
                        const partner = data.partner as { nickname?: string; role?: string; strengthKeyword?: string } | undefined
                        const matchedPartnerInfo = {
                            partnerId:       String(data.suggestionId ?? ''),
                            partnerName:     String(partner?.nickname ?? '파트너'),
                            partnerAvatar:   String(partner?.nickname ?? '?').charAt(0).toUpperCase(),
                            partnerRole:     (partner?.role === 'mentor' ? 'mentor' : 'mentee') as 'mentor' | 'mentee',
                            matchRate:       Math.round((Number(data.matchScore) || 0) * 100),
                            partnerStrengths: partner?.strengthKeyword ? [partner.strengthKeyword] : [],
                            matchKeyword:    String(data.matchKeyword ?? ''),
                        }
                        if (matchTimeoutRef.current) window.clearTimeout(matchTimeoutRef.current)
                        setMatchStatus('pending')
                        setPartnerInfo(matchedPartnerInfo)
                    }
                } else if (type === 'study_no_candidate') {
                    // BUG-04 fix: 매칭 후보 없음 SSE 처리 → searching 상태 해제
                    if (matchStatusRef.current === 'searching') {
                        if (matchTimeoutRef.current) window.clearTimeout(matchTimeoutRef.current)
                        setMatchStatus('idle')
                    }
                }
            } catch { /* ignore malformed SSE data */ }
        }
        sse.addEventListener('study_group_activated', handleGroupActivated as EventListener)
        sse.addEventListener('notification', dispatch as EventListener)
        sse.onmessage = dispatch
        return () => {
            sse.close()
            if (matchTimeoutRef.current) window.clearTimeout(matchTimeoutRef.current)
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    // ── 스터디 매칭 요청 ─────────────────────────────────────────────────────
    const handleRequestMatching = useCallback(async () => {
        if (!weekData) return
        const key = `${roomId}_${weekData.weekId}`
        matchRequestKeyRef.current = key
        setMatchStatus('searching')
        try {
            await requestStudyMatch(roomId, weekData.weekId)
            // BUG-05 fix: 타임아웃 시 'searching' 상태일 때만 idle로 리셋
            // (파트너가 60초 이내에 찾아져 pending/completed가 된 경우 리셋하지 않음)
            matchTimeoutRef.current = window.setTimeout(() => {
                if (matchRequestKeyRef.current === key && matchStatusRef.current === 'searching') {
                    setMatchStatus('idle')
                }
            }, 60_000)
        } catch { setMatchStatus('idle') }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [weekData, roomId])

    // ── 알림 / 로그아웃 ──────────────────────────────────────────────────────
    const handleOpenNotif = () => {
        const opening = !notifOpen
        setNotifOpen(opening)
        if (opening) {
            setNotifLoading(true)
            getNotifications().then(setNotifications).catch(() => {}).finally(() => setNotifLoading(false))
        }
    }
    const handleMarkRead = (id: string) =>
        markNotificationRead(id).then(() =>
            setNotifications(prev => prev.map(n => n.notificationId === id ? { ...n, isRead: true } : n))
        ).catch(() => {})

    const handleLogout = useCallback(async () => {
        try { if (refreshToken) await logout({ refreshToken }) } catch { /* ignore logout errors */ }
        clearAuth(); navigate('/')
    }, [refreshToken, clearAuth, navigate])

    // 드롭다운 외부 클릭 닫기
    useEffect(() => {
        const h = (e: MouseEvent) => {
            if (notifRef.current && !notifRef.current.contains(e.target as Node)) setNotifOpen(false)
            if (profileRef.current && !profileRef.current.contains(e.target as Node)) setProfileOpen(false)
        }
        document.addEventListener('mousedown', h)
        return () => document.removeEventListener('mousedown', h)
    }, [])

    // ── STOMP 채팅 — groupId 확정 시 연결 ────────────────────────────────────
    useEffect(() => {
        if (!groupId) return
        getGroupMessages(groupId)
            .then(msgs => {
                setChatMessages(msgs.map((m: ChatMessage) => ({
                    id:   Number(m.messageId) || Date.now() + Math.random(),
                    role: m.senderType === 'ai'
                        ? 'ai'
                        : m.senderNickname === nicknameRef.current ? 'me' : 'partner',
                    text: m.content,
                    time: new Date(m.sentAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }),
                })))
            })
            .catch(() => {})
        const client = connectGroupChat(groupId, (msg) => {
            setChatMessages(prev => [...prev, {
                id:   Number(msg.messageId) || Date.now(),
                role: msg.senderType === 'ai'
                    ? 'ai'
                    : msg.senderNickname === nicknameRef.current ? 'me' : 'partner',
                text: msg.content,
                time: new Date(msg.sentAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }),
            }])
        })
        stompRef.current = client
        return () => { client.deactivate(); stompRef.current = null }
    }, [groupId])

    // ── 채팅 자동 스크롤 ─────────────────────────────────────────────────────
    useEffect(() => {
        chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }, [chatMessages])

    // ── 채팅 메시지 전송 ─────────────────────────────────────────────────────
    const handleSendChat = useCallback(() => {
        const text = chatInput.trim()
        if (!text || !groupId) return
        if (stompRef.current?.connected) {
            stompRef.current.publish({
                destination: `/pub/chat/${groupId}`,
                body: JSON.stringify({ content: text } satisfies WsChatSend),
            })
        }
        setChatInput('')
    }, [chatInput, groupId])

    // ── 리사이저 드래그 ───────────────────────────────────────────────────────
    const handleResizerMouseDown = useCallback((e: React.MouseEvent) => {
        e.preventDefault()
        const startX    = e.clientX
        const startRatio = leftRatio
        const bodyWidth = bodyRef.current?.offsetWidth ?? 1000
        setIsDragging(true)
        document.body.style.cursor    = 'col-resize'
        document.body.style.userSelect = 'none'
        const onMove = (ev: MouseEvent) => {
            const delta = ev.clientX - startX
            setLeftRatio(Math.min(72, Math.max(28, startRatio + (delta / bodyWidth) * 100)))
        }
        const onUp = () => {
            document.body.style.cursor = ''; document.body.style.userSelect = ''
            setIsDragging(false)
            document.removeEventListener('mousemove', onMove)
            document.removeEventListener('mouseup', onUp)
        }
        document.addEventListener('mousemove', onMove)
        document.addEventListener('mouseup', onUp)
    }, [leftRatio])

    // ── 퀴즈 이력 렌더 ───────────────────────────────────────────────────────
    const renderQuizHistory = () => {
        if (quizLoading) return <div className="fc-quiz-loading"><Loader2 size={14} className="animate-spin" /> 불러오는 중...</div>
        const list = Array.isArray(quizAttempts) ? quizAttempts : []
        if (quizAttempts !== null && list.length === 0) return <p className="fc-quiz-empty">아직 퀴즈 이력이 없습니다.</p>

        const correct = list.filter(q => q.isCorrect)
        const wrong   = list.filter(q => !q.isCorrect)

        const renderItem = (item: QuizAttemptListItem) => {
            const isOpen    = expandedQuizId === item.attemptId
            const isLoading = quizDetailLoading === item.attemptId
            const detail    = quizDetailCache[item.attemptId]
            return (
                <div key={item.attemptId} className="fc-quiz-item">
                    <button className="fc-quiz-item__header" onClick={() => handleToggleQuizDetail(item.attemptId)}>
                        <span className={`fc-quiz-item__dot ${item.isCorrect ? 'fc-quiz-item__dot--correct' : 'fc-quiz-item__dot--wrong'}`} />
                        <span className="fc-quiz-item__title">{item.questionTitle}</span>
                        <ChevronRight size={12} strokeWidth={2} style={{ transform: isOpen ? 'rotate(90deg)' : 'none', transition: 'transform 0.15s', flexShrink: 0 }} />
                    </button>
                    {isOpen && (
                        <div className="fc-quiz-item__detail">
                            {isLoading ? (
                                <div className="fc-quiz-loading"><Loader2 size={12} className="animate-spin" /> 불러오는 중...</div>
                            ) : detail ? (
                                <>
                                    <p className="fc-quiz-item__question">{detail.question}</p>
                                    {detail.options.length > 0 ? (
                                        <div className="fc-quiz-item__options">
                                            {detail.options.map(opt => {
                                                const isMyAns = opt.label === detail.myAnswer || opt.text === detail.myAnswer
                                                const isCorrect = opt.label === detail.correctAnswer || opt.text === detail.correctAnswer
                                                return (
                                                    <div key={opt.label} className={`fc-quiz-item__opt ${isCorrect ? 'fc-quiz-item__opt--correct' : isMyAns ? 'fc-quiz-item__opt--wrong' : ''}`}>
                                                        <span className="fc-quiz-item__opt-label">{opt.label}</span>
                                                        <span>{opt.text}</span>
                                                        {isCorrect && <span className="fc-quiz-item__badge fc-quiz-item__badge--correct">정답</span>}
                                                        {isMyAns && !isCorrect && <span className="fc-quiz-item__badge fc-quiz-item__badge--wrong">내 답</span>}
                                                    </div>
                                                )
                                            })}
                                        </div>
                                    ) : (
                                        <div className="fc-quiz-item__essay">
                                            <span className="fc-quiz-item__essay-label">내 답변:</span>
                                            <span className={detail.isCorrect ? 'fc-quiz-item__essay-ans--correct' : 'fc-quiz-item__essay-ans--wrong'}>
                                                {detail.myAnswer || '(미작성)'}
                                            </span>
                                        </div>
                                    )}
                                    {detail.aiExplanation && (
                                        <div className="fc-quiz-item__explain">
                                            <span className="fc-quiz-item__explain-label">AI 해설</span>
                                            <p>{detail.aiExplanation}</p>
                                        </div>
                                    )}
                                </>
                            ) : <p className="fc-quiz-loading">상세 정보를 불러오지 못했습니다.</p>}
                        </div>
                    )}
                </div>
            )
        }

        const renderGroup = (items: QuizAttemptListItem[], label: string, colorClass: string) =>
            items.length === 0 ? null : (
                <div className="fc-quiz-group">
                    <div className={`fc-quiz-group__label ${colorClass}`}>{label} ({items.length})</div>
                    {items.map(renderItem)}
                </div>
            )

        return (
            <>
                {renderGroup(correct, '✓ 정답', 'fc-quiz-group__label--correct')}
                {renderGroup(wrong,   '✗ 오답', 'fc-quiz-group__label--wrong')}
            </>
        )
    }

    // ── 렌더 ─────────────────────────────────────────────────────────────────
    const currentWeekEntry = allWeeks.find(w => w.weekId === weekData?.weekId)

    return (
        <div className="fc-root">
            {/* ── TopBar ── */}
            <header className="fc-topbar">
                <button
                    className="fc-topbar__back"
                    onClick={() => navigate(`/study/${roomId}/curriculum`)}
                >
                    <ArrowLeft size={14} strokeWidth={2} />
                    주차 선택
                </button>

                <div className="fc-topbar__center">
                    <span className="fc-topbar__title">
                        {weekLoading ? 'AI 집중학습실' : weekData?.topic ?? 'AI 집중학습실'}
                    </span>
                    {weekData && (
                        <span className="fc-topbar__week-badge">Week {weekData.weekNumber}</span>
                    )}
                    <div className="fc-topbar__progress-wrap">
                        <div className="fc-topbar__progress-fill" style={{ width: `${progress}%` }} />
                    </div>
                    <span className="fc-topbar__progress-pct">{progress}%</span>
                </div>

                <div className="fc-topbar__right">
                    <button className="fc-topbar__icon-btn" onClick={toggleDark} title={darkMode ? '라이트 모드' : '다크 모드'}>
                        {darkMode ? <Sun size={16} strokeWidth={1.5} /> : <Moon size={16} strokeWidth={1.5} />}
                    </button>

                    <div ref={notifRef} className="fc-notif-wrap">
                        <button className="fc-topbar__icon-btn" onClick={handleOpenNotif}>
                            <Bell size={16} strokeWidth={1.5} />
                            {unreadCount > 0 && <span className="fc-notif-badge">{unreadCount > 9 ? '9+' : unreadCount}</span>}
                        </button>
                        {notifOpen && (
                            <div className="fc-notif-dropdown">
                                <div className="fc-notif-header">
                                    알림
                                    <button onClick={() => setNotifOpen(false)} className="fc-notif-close"><X size={14} /></button>
                                </div>
                                {notifLoading ? <div className="fc-notif-loading"><Loader2 size={16} className="animate-spin" /></div>
                                : notifications.length === 0 ? <div className="fc-notif-empty">알림이 없습니다.</div>
                                : notifications.map(n => (
                                    <div key={n.notificationId} className="fc-notif-item" style={{ background: n.isRead ? 'transparent' : 'var(--color-purple-50)' }}>
                                        <div style={{ flex: 1 }}>
                                            <div style={{ fontSize: 12 }}>{n.message}</div>
                                            <div style={{ fontSize: 10, color: 'var(--color-text-muted)', marginTop: 2 }}>{new Date(n.createdAt).toLocaleString('ko-KR')}</div>
                                        </div>
                                        {!n.isRead && <button onClick={() => handleMarkRead(n.notificationId)} className="fc-notif-item__read-btn"><Check size={12} /></button>}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    <div ref={profileRef} className="fc-profile-wrap">
                        <div className="fc-topbar__avatar" onClick={() => setProfileOpen(v => !v)}>{avatarChar}</div>
                        {profileOpen && (
                            <div className="fc-profile-dropdown">
                                <div className="fc-profile-name">{nickname ?? '사용자'}</div>
                                <button onClick={() => { setProfileOpen(false); navigate('/my-page') }} className="fc-profile-menu-btn">
                                    <UserCircle size={13} /> 마이페이지
                                </button>
                                <button onClick={handleLogout} className="fc-profile-logout-btn">
                                    <LogOut size={13} /> 로그아웃
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </header>

            {/* ── Body ── */}
            <div className="fc-body" ref={bodyRef}>

                {/* ══════════ Left Panel ══════════ */}
                <div className="fc-left" style={{ width: `${leftRatio}%` }}>
                    <div className="fc-left-scroll">

                        {/* YouTube 플레이어 */}
                        <div className="fc-video-section">
                            <div className="fc-video-wrapper">
                                {(weekLoading || weekError || !activeVideoId) && (
                                    <div className="fc-video-overlay">
                                        {weekLoading ? <><Loader2 size={28} className="animate-spin" /> 불러오는 중...</>
                                            : weekError ? <span style={{ color: '#ef4444' }}>⚠ {weekError}</span>
                                            : '영상 준비 중...'}
                                    </div>
                                )}
                                <div ref={playerHostRef} className="fc-player-host" />
                            </div>

                            {/* 컨트롤 바 */}
                            <div className="fc-controls">
                                <button className="fc-controls__play" onClick={() => isPlaying ? pausePlayer() : playVideo()}>
                                    {isPlaying ? <Pause size={13} strokeWidth={2} /> : <Play size={13} strokeWidth={2} />}
                                </button>
                                <span className="fc-controls__time">{formatTime(currentTime)} / {formatTime(duration)}</span>
                                <input
                                    type="range" className="fc-controls__timeline"
                                    min={0} max={duration || 100} step={0.5} value={currentTime}
                                    onChange={e => seekPlayer(Number(e.target.value))}
                                />
                                <div className="fc-controls__speeds">
                                    {[0.75, 1, 1.5, 2].map(r => (
                                        <button key={r} className={`fc-speed-btn${playbackRate === r ? ' fc-speed-btn--active' : ''}`} onClick={() => setPlaybackRate(r)}>
                                            {r}×
                                        </button>
                                    ))}
                                </div>
                            </div>

                            {/* 영상 제목 */}
                            <div className="fc-video-meta">
                                <h2>{weekLoading ? '불러오는 중...' : weekData?.topic ?? weekError ?? ''}</h2>
                                <span className="fc-video-meta-sub">
                                    {currentWeekEntry ? `Week ${currentWeekEntry.weekNumber} · ${Number(currentWeekEntry.completionRate).toFixed(0)}% 완료` : ''}
                                </span>
                            </div>
                        </div>

                        {/* ── 학습 기능 영역 ── */}
                        <div className="fc-learning-panel">

                            {/* 메타인지 확인 */}
                            <div className={`fc-section-card${metacogComplete ? ' fc-section-card--complete' : ''}`}>
                                <div className="fc-section-card__header">
                                    <BrainCircuit size={15} strokeWidth={1.5} className={`fc-section-card__icon${metacogComplete ? ' fc-section-card__icon--done' : ''}`} />
                                    <span>메타인지 확인 (거꾸로 학습)</span>
                                    {metacogComplete && <CheckCircle2 size={14} strokeWidth={2} className="fc-section-card__check" />}
                                </div>
                                {metacogComplete ? (
                                    <p className="fc-section-card__done-text">거꾸로 학습이 완료되었습니다.</p>
                                ) : canStartMetacog ? (
                                    <>
                                        <p className="fc-section-card__desc">방금 배운 내용을 AI에게 설명해보세요. 이해도를 실시간 분석합니다.</p>
                                        <button className="fc-section-card__btn" onClick={() => {
                                            pausePlayerRef.current()
                                            open('reverse-learning', { type: 'reverse-learning', conceptName: weekData?.topic ?? '', roomId, weekId: weekData?.weekId })
                                        }}>
                                            <BrainCircuit size={13} strokeWidth={2} /> AI에게 설명하기
                                        </button>
                                    </>
                                ) : (
                                    <p className="fc-section-card__locked-text">
                                        진행률 40% 이상부터 가능합니다.{progress > 0 && progress < 40 && ` (현재 ${progress}%)`}
                                    </p>
                                )}
                            </div>

                            {/* 주간 최종 퀴즈 */}
                            <div className={`fc-section-card${!canStartFinalQuiz && !quizSubmitted ? ' fc-section-card--locked' : ''}`}>
                                <div className="fc-section-card__header">
                                    {canStartFinalQuiz || quizSubmitted
                                        ? <Trophy size={15} strokeWidth={1.5} className="fc-section-card__icon" />
                                        : <Lock   size={15} strokeWidth={1.5} className="fc-section-card__icon fc-section-card__icon--lock" />}
                                    <span>주간 최종 퀴즈</span>
                                </div>
                                <p className="fc-section-card__desc">
                                    {quizSubmitted ? 'AI 종합 분석 리포트를 확인하세요.'
                                        : canStartFinalQuiz ? `Week ${weekData?.weekNumber} 전체 내용 최종 평가`
                                        : '메타인지 확인 완료 후 진행 가능합니다.'}
                                </p>
                                {weekData && (
                                    <button
                                        className="fc-section-card__btn fc-section-card__btn--secondary"
                                        disabled={!quizSubmitted && !canStartFinalQuiz}
                                        onClick={() => {
                                            if (quizSubmitted || canStartFinalQuiz) {
                                                pausePlayerRef.current()
                                                open('final-quiz', { type: 'final-quiz', roomId, weekId: weekData.weekId, ...(quizSubmitted ? { reviewMode: true } : {}) })
                                            }
                                        }}
                                    >
                                        <Trophy size={13} strokeWidth={1.5} />
                                        {quizSubmitted ? '결과 복습하기' : '퀴즈 도전하기'}
                                    </button>
                                )}
                            </div>

                            {/* 퀴즈 이력 */}
                            <div className="fc-section-card">
                                <button className="fc-section-card__toggle" onClick={handleToggleQuizHistory}>
                                    <MessageSquare size={15} strokeWidth={1.5} className="fc-section-card__icon" />
                                    <span>퀴즈 이력</span>
                                    {quizAttempts !== null && <span className="fc-section-card__count">{quizAttempts.length}개</span>}
                                    <ChevronRight size={14} strokeWidth={2} style={{ marginLeft: 'auto', transform: quizHistoryOpen ? 'rotate(90deg)' : 'none', transition: 'transform 0.15s' }} />
                                </button>
                                {quizHistoryOpen && (
                                    <div className="fc-quiz-history">
                                        {renderQuizHistory()}
                                    </div>
                                )}
                            </div>

                            {/* 핵심 키워드 */}
                            <div className="fc-section-card">
                                <button className="fc-section-card__toggle" onClick={() => setKeywordsOpen(v => !v)}>
                                    <Zap size={15} strokeWidth={1.5} className="fc-section-card__icon" />
                                    <span>핵심 키워드</span>
                                    {safeKeywords.length > 0 && <span className="fc-section-card__count">총 {safeKeywords.length}개</span>}
                                    <ChevronRight size={14} strokeWidth={2} style={{ marginLeft: 'auto', transform: keywordsOpen ? 'rotate(90deg)' : 'none', transition: 'transform 0.15s' }} />
                                </button>
                                {keywordsOpen && (
                                    <div className="fc-keywords-content">
                                        {weekLoading ? (
                                            <div className="fc-quiz-loading"><Loader2 size={13} className="animate-spin" /></div>
                                        ) : safeKeywords.length === 0 ? (
                                            <p className="fc-quiz-empty">키워드 데이터가 없습니다.</p>
                                        ) : (
                                            <>
                                                {(keywordsExpanded ? safeKeywords : safeKeywords.slice(0, 5)).map((kw, i) => (
                                                    <div key={i} className="fc-kw-item">
                                                        <span className="fc-kw-dot" />
                                                        <span className="fc-kw-text">{kw}</span>
                                                    </div>
                                                ))}
                                                {safeKeywords.length > 5 && (
                                                    <button className="fc-kw-toggle" onClick={() => setKeywordsExpanded(v => !v)}>
                                                        {keywordsExpanded ? '접기 ↑' : `더 보기 +${safeKeywords.length - 5}개`}
                                                    </button>
                                                )}
                                                {/* 약점 키워드 */}
                                                {(keywordsLoading || safeUserKeywords.weaknesses.length > 0) && (
                                                    <div style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid var(--color-border)' }}>
                                                        <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--color-text-muted)', marginBottom: 6, textTransform: 'uppercase' }}>약점 키워드</div>
                                                        {keywordsLoading ? (
                                                            <div className="fc-quiz-loading" style={{ fontSize: 11 }}><Loader2 size={12} className="animate-spin" /> 분석 중...</div>
                                                        ) : (
                                                            <div className="fc-weakness-tags">
                                                                {safeUserKeywords.weaknesses.map(w => (
                                                                    <span key={w.keyword} className="fc-weakness-badge">
                                                                        {w.keyword}{w.weaknessCount > 1 && <span style={{ marginLeft: 3, opacity: 0.7 }}>×{w.weaknessCount}</span>}
                                                                    </span>
                                                                ))}
                                                            </div>
                                                        )}
                                                    </div>
                                                )}
                                            </>
                                        )}
                                    </div>
                                )}
                            </div>

                            {/* 스터디 매칭 */}
                            <div className="fc-section-card">
                                <div className="fc-section-card__header">
                                    <Users size={15} strokeWidth={1.5} className="fc-section-card__icon" />
                                    <span>스터디 매칭</span>
                                    {partnerConnected && <CheckCircle2 size={14} strokeWidth={2} className="fc-section-card__check" />}
                                </div>
                                {partnerConnected ? (
                                    <p className="fc-section-card__done-text">스터디 파트너가 연결되어 있습니다.</p>
                                ) : matchStatus === 'searching' ? (
                                    <div className="fc-match-searching">
                                        <Search size={13} strokeWidth={2} className="animate-spin" />
                                        <span>파트너 매칭 중...</span>
                                        <button className="fc-match-cancel-btn" onClick={() => { if (matchTimeoutRef.current) window.clearTimeout(matchTimeoutRef.current); setMatchStatus('idle') }}>취소</button>
                                    </div>
                                ) : matchStatus === 'waiting_partner' ? (
                                    <div className="fc-match-waiting">
                                        <Loader2 size={13} strokeWidth={2} className="animate-spin" />
                                        <span>상대 수락 대기 중...</span>
                                    </div>
                                ) : matchStatus === 'pending' && partnerInfo ? (
                                    <>
                                        <p className="fc-section-card__desc" style={{ color: 'var(--color-purple-600)' }}>🎉 파트너를 찾았습니다!</p>
                                        <button className="fc-section-card__btn fc-section-card__btn--secondary" onClick={() => open('study-matching', { type: 'study-matching', match: partnerInfo })}>
                                            <CheckCircle2 size={13} strokeWidth={2} /> 매칭 수락/거절하기
                                        </button>
                                    </>
                                ) : (
                                    <>
                                        <p className="fc-section-card__desc">메타인지 완료 후 멘토와 스터디할 수 있습니다.</p>
                                        <button
                                            className="fc-section-card__btn fc-section-card__btn--secondary"
                                            disabled={!metacogComplete}
                                            onClick={handleRequestMatching}
                                        >
                                            <Users size={13} strokeWidth={2} /> 멘토에게 스터디 요청하기
                                        </button>
                                    </>
                                )}
                            </div>

                        {/* ── 파트너 채팅 위젯 ── */}
                        {partnerConnected && (() => {
                            const partner = partnerInfo
                            const myRole  = partner?.partnerRole === 'mentor' ? '멘티' : '멘토'
                            const pName   = partner?.partnerName  ?? '파트너'
                            const pAvatar = partner?.partnerAvatar ?? '?'
                            const pRole   = partner?.partnerRole === 'mentor' ? '멘토' : '멘티'
                            return (
                                <div className="fc-partner-widget">
                                    {/* 헤더 */}
                                    <div className="fc-partner-widget__header">
                                        <span className="fc-partner-widget__online-dot" />
                                        <span className="fc-partner-widget__header-text">스터디 파트너 연결됨</span>
                                    </div>

                                    {/* 프로필 */}
                                    <div className="fc-partner-widget__profile">
                                        <div className="fc-partner-widget__avatar">
                                            {pAvatar}
                                        </div>
                                        <div className="fc-partner-widget__info">
                                            <div className="fc-partner-widget__name">{pName}</div>
                                            <div className="fc-partner-widget__roles">
                                                <span className="fc-partner-widget__role-badge fc-partner-widget__role-badge--partner">{pRole}</span>
                                                <span className="fc-partner-widget__role-sep">↔</span>
                                                <span className="fc-partner-widget__role-badge fc-partner-widget__role-badge--me">{myRole} (나)</span>
                                            </div>
                                        </div>
                                    </div>

                                    {/* 강점 태그 */}
                                    {partner?.partnerStrengths && partner.partnerStrengths.length > 0 && (
                                        <div className="fc-partner-widget__strengths">
                                            {partner.partnerStrengths.map(s => (
                                                <span key={s} className="fc-partner-widget__strength-tag">{s}</span>
                                            ))}
                                        </div>
                                    )}

                                    {/* 채팅 토글 버튼 */}
                                    <button
                                        className="fc-section-card__btn fc-section-card__btn--secondary"
                                        onClick={() => setChatOpen(v => !v)}
                                    >
                                        <MessageCircle size={13} strokeWidth={1.5} />
                                        {chatOpen ? '채팅 닫기' : '1:1 메시지 보내기'}
                                    </button>

                                    {/* 채팅 영역 */}
                                    {chatOpen && (
                                        <div className="fc-partner-widget__chat">
                                            {!groupId ? (
                                                <p className="fc-partner-widget__chat-placeholder">
                                                    <Loader2 size={13} strokeWidth={1.5} className="animate-spin" style={{ display: 'inline', marginRight: 6 }} />
                                                    파트너가 수락하면 채팅이 열립니다.
                                                </p>
                                            ) : (
                                                <>
                                                    <div className="fc-partner-widget__chat-log">
                                                        {chatMessages.length === 0 ? (
                                                            <p className="fc-partner-widget__chat-placeholder">
                                                                연결된 스터디 파트너와 대화를 시작해보세요!
                                                            </p>
                                                        ) : chatMessages.map(msg => (
                                                            <div
                                                                key={msg.id}
                                                                className={`fc-partner-widget__chat-bubble-wrap ${msg.role === 'me' ? 'fc-partner-widget__chat-bubble-wrap--me' : 'fc-partner-widget__chat-bubble-wrap--partner'}`}
                                                            >
                                                                <div className={`fc-partner-widget__chat-bubble fc-partner-widget__chat-bubble--${msg.role}`}>
                                                                    {msg.role === 'ai' ? (
                                                                        <MarkdownContent
                                                                            content={msg.text}
                                                                            compact
                                                                            className="fc-partner-widget__chat-ai-text"
                                                                        />
                                                                    ) : (
                                                                        <span>{msg.text}</span>
                                                                    )}
                                                                    <span className="fc-partner-widget__chat-time">{msg.time}</span>
                                                                </div>
                                                            </div>
                                                        ))}
                                                        <div ref={chatBottomRef} />
                                                    </div>
                                                    <div className="fc-partner-widget__chat-input-row">
                                                        <input
                                                            className="fc-partner-widget__chat-input"
                                                            value={chatInput}
                                                            onChange={e => setChatInput(e.target.value)}
                                                            onKeyDown={e => {
                                                                if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
                                                                    e.preventDefault()
                                                                    handleSendChat()
                                                                }
                                                            }}
                                                            placeholder="메시지 입력... (Enter 전송)"
                                                        />
                                                        <button
                                                            className={`fc-partner-widget__chat-send${chatInput.trim() ? ' fc-partner-widget__chat-send--active' : ''}`}
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

                        </div>{/* /fc-learning-panel */}
                    </div>
                </div>

                {/* ── Resizer ── */}
                <div className={`fc-resizer${isDragging ? ' fc-resizer--dragging' : ''}`} onMouseDown={handleResizerMouseDown} />

                {/* ══════════ Right Panel ══════════ */}
                <div className="fc-right">
                    {/* 툴바: 키워드 + 문서 선택 + 다운로드 */}
                    <div className="fc-md-toolbar">
                        <div className="fc-md-toolbar-meta">
                            <div className="fc-keywords">
                                {safeKeywords.slice(0, 5).map(kw => (
                                    <span key={kw} className="fc-keyword-badge">{kw}</span>
                                ))}
                            </div>
                            {mdResources.length > 1 && mdResources.map((r, i) => (
                                <button
                                    key={i}
                                    onClick={() => setSelectedMdIdx(i)}
                                    style={{
                                        marginLeft: 4, padding: '2px 8px', borderRadius: 6, flexShrink: 0,
                                        border: '1px solid var(--color-border)',
                                        background: selectedMdIdx === i ? 'var(--color-purple-100)' : 'none',
                                        color: selectedMdIdx === i ? 'var(--color-purple-700)' : 'var(--color-text-muted)',
                                        fontSize: 11, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit',
                                    }}
                                    title={r.title}
                                >
                                    {r.title}
                                </button>
                            ))}
                        </div>
                        {activeMdUrl && (
                            <a
                                href={`${activeMdUrl}${activeMdUrl.includes('?') ? '&' : '?'}download=true`}
                                className="fc-md-download-btn"
                            >
                                <FileText size={13} strokeWidth={1.5} /> 다운로드
                            </a>
                        )}
                    </div>

                    {/* 학습 자료 뷰어 */}
                    <div className="fc-md-scroll">
                        {weekLoading ? (
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 120, gap: 8, color: 'var(--color-text-muted)', fontSize: 13 }}>
                                <Loader2 size={18} className="animate-spin" /> 불러오는 중...
                            </div>
                        ) : activeMdUrl ? (
                            <MarkdownViewer s3Url={activeMdUrl} />
                        ) : (
                            <MarkdownContent content={weekData?.description ?? ''} paper />
                        )}
                    </div>

                    {/* 다른 학습 자료 목록 */}
                    {(otherResources.length > 0 || (!activeMdUrl && safeResources.length === 0 && !weekLoading)) && (
                        <div className="fc-resource-section">
                            {otherResources.length > 0 && (
                                <>
                                    <div className="fc-resource-label">다른 학습 자료</div>
                                    {otherResources.map((res, i) => (
                                        <a key={i} href={resolveUrl(res.url)} target="_blank" rel="noreferrer" className="fc-resource-item">
                                            <span className="fc-resource-icon">{resourceIcon(res.type)}</span>
                                            <span className="fc-resource-info">
                                                <span className="fc-resource-name">{res.title}</span>
                                                <span className="fc-resource-meta">{res.size} · {res.type.toUpperCase()}</span>
                                            </span>
                                            <span className="fc-resource-action">다운로드</span>
                                        </a>
                                    ))}
                                </>
                            )}
                            {!weekLoading && safeResources.length === 0 && (
                                <p style={{ fontSize: 12, color: 'var(--color-text-muted)', padding: '12px 0' }}>
                                    AI 교안 생성 중입니다. 잠시 후 확인해주세요.
                                </p>
                            )}
                        </div>
                    )}
                </div>

            </div>{/* /fc-body */}

            {/* 모달 */}
            <ClassroomModals
                onSeekPlayer={sec => seekPlayerRef.current(sec)}
                onAnyQuizComplete={() => setQuizCompletedCount(prev => prev + 1)}
            />

        </div>
    )
}

export default function FocusClassroom() {
    return (
        <ThemeProvider>
            <ClassroomModalProvider>
                <FocusClassroomContent />
            </ClassroomModalProvider>
        </ThemeProvider>
    )
}
