import { createContext, useContext, useState } from 'react'
import type { ModalData, StudyMatchResponse } from '../types/aiEvents'

// ── Modal key type ────────────────────────────────────────────────────────────

export type ModalKey =
    | 'monitoring'
    | 'flipped'
    | 'quiz-pass'
    | 'summary-detail'
    | 'fast-track'
    | 'reverse-learning'
    | 'meta-evaluation'
    | 'study-matching'
    | 'quiz-correct'
    | 'quiz-incorrect'
    | 'final-quiz'  // 주간 파이널 퀴즈 + AI 분석 리포트 플로우
    | null

// ── Context shape ─────────────────────────────────────────────────────────────

export type MatchStatus = 'idle' | 'searching' | 'pending' | 'completed'

interface ClassroomModalContextValue {
    /** Which modal is currently visible; null means none */
    modal: ModalKey
    /** Typed payload for the active modal; null for modals with no external data */
    modalData: ModalData | null
    /** Open a modal, optionally attaching backend-response data to it */
    open: (key: NonNullable<ModalKey>, data?: ModalData) => void
    /** Close the active modal and clear its payload */
    close: () => void
    /** True once the user has completed the full meta-cognition evaluation flow */
    metacogComplete: boolean
    /** True once the user has connected with a study partner */
    partnerConnected: boolean
    /** Matched partner's profile data — persisted in localStorage */
    partnerInfo: StudyMatchResponse | null
    /** True once the user has submitted the final quiz and viewed the report */
    quizSubmitted: boolean
    /** Async matching status — persisted in localStorage so it survives page reload */
    matchStatus: MatchStatus
    /** Study group ID received via study_accepted SSE — enables WebSocket chat */
    groupId: string | null
    setMetacogComplete: (v: boolean) => void
    setPartnerConnected: (v: boolean) => void
    setPartnerInfo: (info: StudyMatchResponse | null) => void
    setQuizSubmitted: (v: boolean) => void
    setMatchStatus: (v: MatchStatus) => void
    setGroupId: (id: string | null) => void
    /**
     * 현재 학습 중인 주차 ID (weekId).
     * StudyClassroomContent에서 setCurrentWeekId()로 설정되고,
     * ClassroomModals에서 reverse-learning / final-quiz 모달에 전달된다.
     */
    currentWeekId: string | null
    setCurrentWeekId: (weekId: string) => void
}

// ── Context + provider ────────────────────────────────────────────────────────

const ClassroomModalContext = createContext<ClassroomModalContextValue | null>(null)

export function ClassroomModalProvider({
    children,
    roomId = '',
}: {
    children: React.ReactNode
    roomId?: string
}) {
    // roomId별로 localStorage 키를 분리해 스터디마다 독립적인 매칭 상태를 유지한다.
    const k = (name: string) => roomId ? `moai_${name}_${roomId}` : `moai_${name}`

    const [modal,           setModal]           = useState<ModalKey>(null)
    const [modalData,       setModalData]       = useState<ModalData | null>(null)
    const [metacogComplete, setMetacogComplete] = useState(false)
    const [quizSubmitted,   setQuizSubmitted]   = useState(false)
    // 현재 학습 중인 주차 ID — StudyClassroomContent에서 커리큘럼 로드 후 설정
    const [currentWeekId,   setCurrentWeekId]   = useState<string | null>(null)

    // matchStatus / partnerConnected / partnerInfo — API 응답으로 복원하므로 메모리만 유지
    const [matchStatus,      setMatchStatus]      = useState<MatchStatus>('idle')
    const [partnerConnected, setPartnerConnected] = useState(false)
    const [partnerInfo,      setPartnerInfo]      = useState<StudyMatchResponse | null>(null)

    const [groupId, setGroupIdRaw] = useState<string | null>(
        () => localStorage.getItem(k('study_group_id')),
    )

    const setGroupId = (id: string | null) => {
        if (id) localStorage.setItem(k('study_group_id'), id)
        else    localStorage.removeItem(k('study_group_id'))
        setGroupIdRaw(id)
    }

    const open = (key: NonNullable<ModalKey>, data?: ModalData) => {
        setModalData(data ?? null)
        setModal(key)
    }

    const close = () => {
        setModal(null)
        setModalData(null)
    }

    return (
        <ClassroomModalContext.Provider value={{
            modal, modalData, open, close,
            metacogComplete,  setMetacogComplete,
            partnerConnected, setPartnerConnected,
            partnerInfo,      setPartnerInfo,
            quizSubmitted,    setQuizSubmitted,
            matchStatus,      setMatchStatus,
            groupId,          setGroupId,
            currentWeekId,    setCurrentWeekId,
        }}>
            {children}
        </ClassroomModalContext.Provider>
    )
}

// ── Consumer hook ─────────────────────────────────────────────────────────────

/**
 * Read and control the global classroom modal state from any component
 * rendered inside <ClassroomModalProvider>.
 */
export function useClassroomModal(): ClassroomModalContextValue {
    const ctx = useContext(ClassroomModalContext)
    if (!ctx) throw new Error('useClassroomModal must be used inside <ClassroomModalProvider>')
    return ctx
}
