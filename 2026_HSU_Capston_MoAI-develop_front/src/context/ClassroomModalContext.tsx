import { createContext, useContext, useState } from 'react'
import type { ModalData } from '../types/aiEvents'

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
    /** True once the user has submitted the final quiz and viewed the report */
    quizSubmitted: boolean
    setMetacogComplete: (v: boolean) => void
    setPartnerConnected: (v: boolean) => void
    setQuizSubmitted: (v: boolean) => void
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

export function ClassroomModalProvider({ children }: { children: React.ReactNode }) {
    const [modal,            setModal]            = useState<ModalKey>(null)
    const [modalData,        setModalData]        = useState<ModalData | null>(null)
    const [metacogComplete,  setMetacogComplete]  = useState(false)
    const [partnerConnected, setPartnerConnected] = useState(false)
    const [quizSubmitted,    setQuizSubmitted]    = useState(false)
    // 현재 학습 중인 주차 ID — StudyClassroomContent에서 커리큘럼 로드 후 설정
    const [currentWeekId,    setCurrentWeekId]    = useState<string | null>(null)

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
            quizSubmitted,    setQuizSubmitted,
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
