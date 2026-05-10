import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { BookOpen, ChevronDown, ArrowRight } from 'lucide-react'
import { getLearningRooms, getCurriculum } from '../services/apiService'
import { useClassroomModal } from '../context/ClassroomModalContext'
import './StudyMatchDropdown.css'

interface ActiveMatchItem {
    roomId:      string
    weekId:      string
    subject:     string
    partnerName: string
}

export default function StudyMatchDropdown() {
    const navigate = useNavigate()
    const { matchStates } = useClassroomModal()

    const [open,          setOpen]          = useState(false)
    const [activeMatches, setActiveMatches] = useState<ActiveMatchItem[]>([])
    const [weekNumMap,    setWeekNumMap]    = useState<Record<string, number>>({})
    const ref            = useRef<HTMLDivElement>(null)
    const loadedRoomsRef = useRef(new Set<string>())

    // matchStates(Context)에서 completed 매칭을 추출해 학습실 subject와 연결
    useEffect(() => {
        let cancelled = false
        const hasCompleted = Object.values(matchStates).some(
            s => s.matchStatus === 'completed' && s.groupId && s.partnerInfo
        )
        if (!hasCompleted) { setActiveMatches([]); return }

        getLearningRooms().then(rooms => {
            if (cancelled) return
            const matches: ActiveMatchItem[] = []
            for (const [key, state] of Object.entries(matchStates)) {
                if (state.matchStatus !== 'completed' || !state.groupId || !state.partnerInfo) continue
                const sep = key.lastIndexOf('_')
                if (sep === -1) continue
                const roomId = key.slice(0, sep)
                const weekId = key.slice(sep + 1)
                const room   = rooms.find(r => r.roomId === roomId)
                if (!room) continue
                matches.push({ roomId, weekId, subject: room.subject, partnerName: state.partnerInfo!.partnerName })
            }
            setActiveMatches(matches)
        }).catch(() => {})

        return () => { cancelled = true }
    }, [matchStates])

    // 외부 클릭 시 닫기
    useEffect(() => {
        if (!open) return
        const handler = (e: MouseEvent) => {
            if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
        }
        document.addEventListener('mousedown', handler)
        return () => document.removeEventListener('mousedown', handler)
    }, [open])

    // 드롭다운 열릴 때 주차 번호 lazy-load
    useEffect(() => {
        if (!open || activeMatches.length === 0) return
        activeMatches.forEach(m => {
            if (loadedRoomsRef.current.has(m.roomId)) return
            loadedRoomsRef.current.add(m.roomId)
            getCurriculum(m.roomId)
                .then(weeks => setWeekNumMap(prev => ({
                    ...prev,
                    ...Object.fromEntries(weeks.map(w => [`${m.roomId}_${w.weekId}`, w.weekNumber])),
                })))
                .catch(() => {})
        })
    }, [open, activeMatches])

    if (activeMatches.length === 0) return null

    return (
        <div ref={ref} className="smd-root">
            <button
                onClick={() => setOpen(v => !v)}
                className={`smd-trigger${open ? ' smd-trigger--open' : ''}`}
            >
                <BookOpen size={14} strokeWidth={1.5} />
                매칭 {activeMatches.length}
                <ChevronDown
                    size={13} strokeWidth={2}
                    className={`smd-chevron${open ? ' smd-chevron--open' : ''}`}
                />
            </button>

            {open && (
                <div className="smd-panel">
                    <div className="smd-panel__header">
                        진행 중인 스터디 매칭
                    </div>
                    {activeMatches.map(m => {
                        const weekNum = weekNumMap[`${m.roomId}_${m.weekId}`]
                        return (
                            <button
                                key={`${m.roomId}_${m.weekId}`}
                                onClick={() => {
                                    navigate(`/study/${m.roomId}/classroom?curriculumId=${m.weekId}`)
                                    setOpen(false)
                                }}
                                className="smd-item"
                            >
                                <div className="smd-item__avatar">
                                    {m.partnerName.charAt(0).toUpperCase()}
                                </div>
                                <div className="smd-item__body">
                                    <div className="smd-item__title">
                                        {m.subject}{weekNum != null ? ` ${weekNum}주차` : ''}
                                    </div>
                                    <div className="smd-item__sub">
                                        {m.partnerName} 매칭 중
                                    </div>
                                </div>
                                <ArrowRight size={13} strokeWidth={1.5} className="smd-item__arrow" />
                            </button>
                        )
                    })}
                </div>
            )}
        </div>
    )
}
