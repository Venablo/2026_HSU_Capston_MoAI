import { createContext, useContext, useState } from 'react'
import type { ModalData, StudyMatchResponse } from '../types/aiEvents'
import type { SummaryItem } from '../components/modals/monitoring/SummaryDetailModal'

// ── 저장된 요약 ───────────────────────────────────────────────────────────────

export interface SavedSummary {
    conceptName:  string
    summaryItems: SummaryItem[]
    savedAt:      number  // Date.now()
}

const LS_SUMMARIES = 'moai_summaries'

function loadSummariesFromStorage(roomId: string, weekId: string): SavedSummary[] {
    try {
        const raw = localStorage.getItem(`${LS_SUMMARIES}_${roomId}_${weekId}`)
        return raw ? (JSON.parse(raw) as SavedSummary[]) : []
    } catch { return [] }
}

function persistSummariesToStorage(roomId: string, weekId: string, list: SavedSummary[]) {
    try {
        if (list.length === 0) localStorage.removeItem(`${LS_SUMMARIES}_${roomId}_${weekId}`)
        else localStorage.setItem(`${LS_SUMMARIES}_${roomId}_${weekId}`, JSON.stringify(list))
    } catch {}
}

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

// ── Per-week match state ──────────────────────────────────────────────────────
// 키: `${roomId}_${weekId}` — 스터디룸 × 주차 조합마다 독립적인 매칭 상태를 가진다.

export type MatchStatus = 'idle' | 'searching' | 'pending' | 'waiting_partner' | 'completed'

export interface WeekMatchState {
    matchStatus:      MatchStatus
    partnerConnected: boolean
    partnerInfo:      StudyMatchResponse | null
    groupId:          string | null
    /** 메타인지(거꾸로 학습) 완료 여부 — 주차별로 localStorage에 영속화 */
    metacogComplete:  boolean
    /** 파이널 퀴즈 제출 완료 여부 — 주차별로 localStorage에 영속화 */
    quizSubmitted:    boolean
}

const DEFAULT_WEEK_MATCH: WeekMatchState = {
    matchStatus:      'idle',
    partnerConnected: false,
    partnerInfo:      null,
    groupId:          null,
    metacogComplete:  false,
    quizSubmitted:    false,
}

const LS_MATCH_STATES = 'moai_match_states'

function loadMatchStates(): Record<string, WeekMatchState> {
    try {
        const raw = localStorage.getItem(LS_MATCH_STATES)
        if (!raw) return {}
        return JSON.parse(raw) as Record<string, WeekMatchState>
    } catch { return {} }
}

function persistMatchStates(map: Record<string, WeekMatchState>) {
    // 기본값과 동일한 항목은 저장하지 않아 localStorage를 깔끔하게 유지한다.
    const cleaned = Object.fromEntries(
        Object.entries(map).filter(([, v]) =>
            v.matchStatus !== 'idle' || v.partnerConnected || v.partnerInfo || v.groupId ||
            v.metacogComplete || v.quizSubmitted
        )
    )
    if (Object.keys(cleaned).length === 0) localStorage.removeItem(LS_MATCH_STATES)
    else localStorage.setItem(LS_MATCH_STATES, JSON.stringify(cleaned))
}

// ── Context shape ─────────────────────────────────────────────────────────────

interface ClassroomModalContextValue {
    modal:    ModalKey
    modalData: ModalData | null
    open:  (key: NonNullable<ModalKey>, data?: ModalData) => void
    close: () => void

    /** 현재 주차의 메타인지 완료 여부 (localStorage에 주차별 영속화) */
    metacogComplete:    boolean
    setMetacogComplete: (v: boolean) => void
    /** 현재 주차의 파이널 퀴즈 제출 완료 여부 (localStorage에 주차별 영속화) */
    quizSubmitted:      boolean
    setQuizSubmitted:   (v: boolean) => void

    /** 현재 학습 중인 주차 ID (weekId) */
    currentWeekId:    string | null
    setCurrentWeekId: (weekId: string) => void

    // ── 주차별 매칭 상태 (`${roomId}_${weekId}` 키) ────────────────────────
    /** 현재 활성 주차의 복합 키. 첫 번째 주차 로드 전까지는 null. */
    currentMatchKey:    string | null
    setCurrentMatchKey: (key: string) => void

    /** currentMatchKey에 해당하는 주차의 파생 상태 */
    matchStatus:      MatchStatus
    partnerConnected: boolean
    partnerInfo:      StudyMatchResponse | null
    groupId:          string | null

    setMatchStatus:      (v: MatchStatus) => void
    setPartnerConnected: (v: boolean) => void
    setPartnerInfo:      (info: StudyMatchResponse | null) => void
    setGroupId:          (id: string | null) => void

    /** SSE 핸들러 등 특정 키를 직접 갱신해야 할 때 사용 */
    setMatchStateForKey: (key: string, updater: (prev: WeekMatchState) => WeekMatchState) => void

    /** 전체 주차별 매칭 상태 맵 (읽기 전용 — StudyMatchDropdown 등에서 사용) */
    matchStates: Record<string, WeekMatchState>

    // ── 저장된 요약 ──────────────────────────────────────────────────────────
    /** 현재 주차의 저장된 요약 목록 */
    savedSummaries: SavedSummary[]
    /** 요약 저장 (같은 conceptName이면 최신으로 교체) */
    saveSummary: (roomId: string, weekId: string, conceptName: string, summaryItems: SummaryItem[]) => void
    /** 주차 전환 시 해당 주차의 저장 요약 로드 */
    loadSummariesForWeek: (roomId: string, weekId: string) => void
}

// ── Context + provider ────────────────────────────────────────────────────────

const ClassroomModalContext = createContext<ClassroomModalContextValue | null>(null)

export function ClassroomModalProvider({ children }: { children: React.ReactNode }) {
    const [modal,     setModal]     = useState<ModalKey>(null)
    const [modalData, setModalData] = useState<ModalData | null>(null)

    const [currentWeekId,   setCurrentWeekId]   = useState<string | null>(null)

    // 주차별 상태 맵 — 매칭·메타인지·퀴즈 완료 여부 포함, 새로고침 시 localStorage에서 복원
    const [matchStates,     setMatchStates]     = useState<Record<string, WeekMatchState>>(loadMatchStates)
    const [currentMatchKey, setCurrentMatchKey] = useState<string | null>(null)

    // 저장된 요약 — 주차 전환 시 loadSummariesForWeek로 교체
    const [savedSummaries, setSavedSummaries] = useState<SavedSummary[]>([])

    // 현재 주차의 파생 상태 (metacogComplete, quizSubmitted 포함)
    const currentMatch   = currentMatchKey ? (matchStates[currentMatchKey] ?? DEFAULT_WEEK_MATCH) : DEFAULT_WEEK_MATCH
    const { matchStatus, partnerConnected, partnerInfo, groupId, metacogComplete, quizSubmitted } = currentMatch

    const updateCurrentMatch = (updater: (prev: WeekMatchState) => WeekMatchState) => {
        if (!currentMatchKey) return
        setMatchStates(prev => {
            const next = { ...prev, [currentMatchKey]: updater(prev[currentMatchKey] ?? DEFAULT_WEEK_MATCH) }
            persistMatchStates(next)
            return next
        })
    }

    const setMatchStateForKey = (key: string, updater: (prev: WeekMatchState) => WeekMatchState) => {
        setMatchStates(prev => {
            const next = { ...prev, [key]: updater(prev[key] ?? DEFAULT_WEEK_MATCH) }
            persistMatchStates(next)
            return next
        })
    }

    const setMatchStatus      = (v: MatchStatus)                  => updateCurrentMatch(p => ({ ...p, matchStatus:      v    }))
    const setPartnerConnected = (v: boolean)                      => updateCurrentMatch(p => ({ ...p, partnerConnected:  v    }))
    const setPartnerInfo      = (info: StudyMatchResponse | null) => updateCurrentMatch(p => ({ ...p, partnerInfo:       info }))
    const setGroupId          = (id: string | null)               => updateCurrentMatch(p => ({ ...p, groupId:           id   }))
    // metacogComplete · quizSubmitted — 주차별 WeekMatchState에 포함되어 localStorage에 자동 영속화
    const setMetacogComplete  = (v: boolean)                      => updateCurrentMatch(p => ({ ...p, metacogComplete:   v    }))
    const setQuizSubmitted    = (v: boolean)                      => updateCurrentMatch(p => ({ ...p, quizSubmitted:     v    }))

    const open  = (key: NonNullable<ModalKey>, data?: ModalData) => { setModalData(data ?? null); setModal(key) }
    const close = () => { setModal(null); setModalData(null) }

    const saveSummary = (roomId: string, weekId: string, conceptName: string, summaryItems: SummaryItem[]) => {
        setSavedSummaries(prev => {
            const filtered = prev.filter(s => s.conceptName !== conceptName)
            const next = [{ conceptName, summaryItems, savedAt: Date.now() }, ...filtered]
            persistSummariesToStorage(roomId, weekId, next)
            return next
        })
    }

    const loadSummariesForWeek = (roomId: string, weekId: string) => {
        setSavedSummaries(loadSummariesFromStorage(roomId, weekId))
    }

    return (
        <ClassroomModalContext.Provider value={{
            modal, modalData, open, close,
            metacogComplete,  setMetacogComplete,
            quizSubmitted,    setQuizSubmitted,
            currentWeekId,    setCurrentWeekId,
            currentMatchKey,  setCurrentMatchKey,
            matchStatus,      partnerConnected, partnerInfo, groupId,
            setMatchStatus,   setPartnerConnected, setPartnerInfo, setGroupId,
            setMatchStateForKey, matchStates,
            savedSummaries, saveSummary, loadSummariesForWeek,
        }}>
            {children}
        </ClassroomModalContext.Provider>
    )
}

// ── Consumer hook ─────────────────────────────────────────────────────────────

export function useClassroomModal(): ClassroomModalContextValue {
    const ctx = useContext(ClassroomModalContext)
    if (!ctx) throw new Error('useClassroomModal must be used inside <ClassroomModalProvider>')
    return ctx
}
