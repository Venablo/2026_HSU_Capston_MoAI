import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useState, useEffect, useRef } from 'react'
import Sidebar from './Sidebar'
import StudyMatchingModal from './modals/flipped/StudyMatchingModal'
import { connectNotificationStream, acceptStudySuggestion } from '../services/apiService'
import type { StudyMatchResponse } from '../types/aiEvents'
import { ThemeProvider } from '../context/ThemeContext'
import { ClassroomModalProvider, useClassroomModal } from '../context/ClassroomModalContext'
import type { WeekMatchState } from '../context/ClassroomModalContext'
import '../styles/Sidebar.css'

// Inner component so useClassroomModal() is inside ClassroomModalProvider
function AppLayoutContent() {
    const [collapsed, setCollapsed] = useState(false)
    const location = useLocation()
    const navigate = useNavigate()

    const [pendingMatch, setPendingMatch] = useState<StudyMatchResponse | null>(null)
    const [connecting,   setConnecting]   = useState(false)

    const { setMatchStateForKey } = useClassroomModal()

    // Stable refs so SSE callbacks don't re-register on every render
    const setMatchStateForKeyRef = useRef(setMatchStateForKey)
    const navigateRef            = useRef(navigate)
    setMatchStateForKeyRef.current = setMatchStateForKey
    navigateRef.current            = navigate

    // Preserve partner info across the pending_acceptance wait window
    // (modal is closed immediately after accept, but SSE may arrive later)
    const acceptedMatchRef = useRef<StudyMatchResponse | null>(null)

    useEffect(() => {
        const isInClassroom = location.pathname.includes('/classroom')
        if (isInClassroom) return

        const sse = connectNotificationStream()

        const handleStudyGroupActivated = (e: MessageEvent) => {
            try {
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const data = JSON.parse(e.data as string) as any
                const activatedRoomId       = String(data.roomId       ?? '')
                const activatedCurriculumId = String(data.curriculumId ?? '')
                const activatedGroupId      = data.groupId ? String(data.groupId) : null

                if (!activatedRoomId || !activatedCurriculumId) return

                setMatchStateForKeyRef.current(
                    `${activatedRoomId}_${activatedCurriculumId}`,
                    (): WeekMatchState => ({
                        matchStatus:      'completed',
                        partnerConnected: true,
                        partnerInfo:      acceptedMatchRef.current,
                        groupId:          activatedGroupId,
                    }),
                )
                acceptedMatchRef.current = null
                const curriculumParam = `?curriculumId=${activatedCurriculumId}`
                navigateRef.current(`/study/${activatedRoomId}/classroom${curriculumParam}`)
            } catch {}
        }

        const dispatch = (e: MessageEvent) => {
            try {
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const data = JSON.parse(e.data as string) as any
                const type = String(data.type ?? '')
                if (type === 'study_match') {
                    setPendingMatch({
                        partnerId:        String(data.suggestionId ?? ''),
                        partnerName:      String(data.partner?.nickname ?? '파트너'),
                        partnerAvatar:    String(data.partner?.nickname ?? '파').charAt(0).toUpperCase(),
                        partnerRole:      (data.partner?.role === 'mentor' ? 'mentor' : 'mentee') as 'mentor' | 'mentee',
                        matchRate:        Math.round(Number(data.matchScore ?? 0) * 100),
                        partnerStrengths: data.partner?.strengthKeyword ? [String(data.partner.strengthKeyword)] : [],
                    })
                } else if (type === 'study_group_activated') {
                    handleStudyGroupActivated(e)
                }
            } catch {}
        }

        sse.addEventListener('notification',          dispatch                      as EventListener)
        sse.addEventListener('study_match',           dispatch                      as EventListener)
        sse.addEventListener('study_accepted',        dispatch                      as EventListener)
        sse.addEventListener('study_group_activated', handleStudyGroupActivated     as EventListener)
        sse.onmessage = dispatch

        return () => sse.close()
    }, [location.pathname])

    const handleConnect = async () => {
        if (!pendingMatch) return
        setConnecting(true)
        try {
            const res = await acceptStudySuggestion(pendingMatch.partnerId)

            if (res.groupStatus === 'active' && res.roomId) {
                // Both sides accepted — navigate to classroom immediately
                if (res.curriculumId) {
                    setMatchStateForKey(
                        `${res.roomId}_${res.curriculumId}`,
                        (): WeekMatchState => ({
                            matchStatus:      'completed',
                            partnerConnected: true,
                            partnerInfo:      pendingMatch,
                            groupId:          res.groupId ?? null,
                        }),
                    )
                }
                setPendingMatch(null)
                const curriculumParam = res.curriculumId ? `?curriculumId=${res.curriculumId}` : ''
                navigate(`/study/${res.roomId}/classroom${curriculumParam}`)
            } else {
                // I accepted first — wait for study_group_activated SSE before navigating
                acceptedMatchRef.current = pendingMatch
                setPendingMatch(null)
            }
        } catch {}
        setConnecting(false)
    }

    const handleDecline = () => setPendingMatch(null)

    return (
        <div className="layout">
            <Sidebar
                collapsed={collapsed}
                onToggle={() => setCollapsed(c => !c)}
            />
            <div className={`layout__main${collapsed ? ' layout__main--left-collapsed' : ''}`}>
                <Outlet />
            </div>

            {pendingMatch && (
                <StudyMatchingModal
                    match={pendingMatch}
                    onClose={handleDecline}
                    onConnect={handleConnect}
                    isConnecting={connecting}
                />
            )}
        </div>
    )
}

export default function AppLayout() {
    return (
        <ThemeProvider>
            <ClassroomModalProvider>
                <AppLayoutContent />
            </ClassroomModalProvider>
        </ThemeProvider>
    )
}
