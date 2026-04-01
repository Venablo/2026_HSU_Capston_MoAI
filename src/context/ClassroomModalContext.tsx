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
    setMetacogComplete: (v: boolean) => void
    setPartnerConnected: (v: boolean) => void
}

// ── Context + provider ────────────────────────────────────────────────────────

const ClassroomModalContext = createContext<ClassroomModalContextValue | null>(null)

export function ClassroomModalProvider({ children }: { children: React.ReactNode }) {
    const [modal,            setModal]            = useState<ModalKey>(null)
    const [modalData,        setModalData]        = useState<ModalData | null>(null)
    const [metacogComplete,  setMetacogComplete]  = useState(false)
    const [partnerConnected, setPartnerConnected] = useState(false)

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
