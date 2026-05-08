import { Outlet, useLocation } from 'react-router-dom'
import { useState, useEffect } from 'react'
import Sidebar from './Sidebar'
import StudyMatchingModal from './modals/flipped/StudyMatchingModal'
import { connectNotificationStream, acceptStudySuggestion } from '../services/apiService'
import type { StudyMatchResponse } from '../types/aiEvents'
import '../styles/Sidebar.css'

export default function AppLayout() {
    const [collapsed, setCollapsed] = useState(false)
    const location = useLocation()

    const [pendingMatch, setPendingMatch] = useState<StudyMatchResponse | null>(null)
    const [connecting,   setConnecting]   = useState(false)

    useEffect(() => {
        const isInClassroom = location.pathname.includes('/classroom')
        if (isInClassroom) return

        const sse = connectNotificationStream()

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
                        partnerStrengths: data.matchKeyword ? [String(data.matchKeyword)] : [],
                    })
                } else if (type === 'study_accepted' && data.groupId) {
                    // 상대방이 수락 완료 → groupId를 저장해 두면 이후 클래스룸 진입 시 복원된다
                    localStorage.setItem('moai_study_group_id', String(data.groupId))
                }
            } catch {}
        }

        sse.addEventListener('notification',    dispatch as EventListener)
        sse.addEventListener('study_match',     dispatch as EventListener)
        sse.addEventListener('study_accepted',  dispatch as EventListener)
        sse.onmessage = dispatch

        return () => sse.close()
    }, [location.pathname])

    const handleConnect = async () => {
        if (!pendingMatch) return
        setConnecting(true)
        try {
            const res = await acceptStudySuggestion(pendingMatch.partnerId)
            // groupId를 user-level key로 저장 — 이후 클래스룸 진입 시 파트너 카드·채팅 복원에 사용
            if (res.groupId) localStorage.setItem('moai_study_group_id', res.groupId)
        } catch {}
        setConnecting(false)
        setPendingMatch(null)
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
