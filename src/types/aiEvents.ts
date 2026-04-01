import type { SummaryItem } from '../components/modals/SummaryDetailModal'

// ─── Pattern type ─────────────────────────────────────────────────────────────

/**
 * The two learning patterns the AI can detect.
 * Sent as { patternType } in the POST /api/ai/analyze request body.
 */
export type PatternType = 'REWIND' | 'FAST_TRACK'

// ─── Backend response shapes ──────────────────────────────────────────────────

/**
 * Discriminated union returned by POST /api/ai/analyze.
 * The actionType field drives which modal the UI opens next.
 *
 * REWIND     → user rewound 3+ times → MonitoringModal (suggest summary)
 * FAST_TRACK → user completed section faster than average → FastTrackModal (suggest challenge)
 */
export type PatternAnalysisResponse =
    | {
        patternType:  'REWIND'
        actionType:   'monitoring'
        conceptName:  string
        reason:       string
      }
    | {
        patternType:    'FAST_TRACK'
        actionType:     'fast-track'
        conceptName:    string
        reason:         string
        /** How much faster than the average learner, e.g. 240 = 2.4× faster */
        completionRate: number
        challengeLevel: 'intermediate' | 'advanced'
      }

/**
 * Shape returned by GET /api/ai/summary/:conceptName
 * Used by ClassroomModals when the user agrees to view the concept breakdown.
 */
export interface AISummaryResponse {
    conceptName: string
    summaryItems: SummaryItem[]
}

// ─── Meta-cognition flow types ────────────────────────────────────────────────

/**
 * Returned by POST /api/ai/meta-evaluate after user submits their explanation.
 * Drives the MetaEvaluationModal display.
 */
export interface MetaEvaluationResponse {
    /** Comprehension percentage, e.g. 95 */
    comprehensionScore: number
    /** Keywords the user explained well (shown as purple tags) */
    strongKeywords: string[]
    /** Keywords that need reinforcement (shown as red tags) */
    weakKeywords: string[]
}

/**
 * Returned by POST /api/ai/match after evaluation completes.
 * Drives the StudyMatchingModal display.
 */
export interface StudyMatchResponse {
    partnerId: string
    partnerName: string
    /** Single emoji or initials used as avatar */
    partnerAvatar: string
    partnerRole: 'mentor' | 'mentee'
    /** AI-computed match percentage, e.g. 98 */
    matchRate: number
    /** Strengths the partner brings (shown as tags) */
    partnerStrengths: string[]
}

// ─── Modal data (discriminated union) ────────────────────────────────────────

/**
 * Typed payload attached to each open modal.
 * The 'type' discriminant always matches the ModalKey it belongs to,
 * so ClassroomModals can narrow without casting.
 */
export type ModalData =
    | { type: 'monitoring';       conceptName: string; reason: string }
    | { type: 'summary-detail';   conceptName: string; summaryItems: SummaryItem[] }
    | { type: 'fast-track';       conceptName: string; reason: string
                                  completionRate: number; challengeLevel: 'intermediate' | 'advanced' }
    | { type: 'quiz-pass' }
    | { type: 'flipped' }
    | { type: 'reverse-learning'; conceptName: string }
    | { type: 'meta-evaluation';  evaluation: MetaEvaluationResponse }
    | { type: 'study-matching';   match: StudyMatchResponse }
    | { type: 'quiz-correct';     conceptName: string }
    | { type: 'quiz-incorrect';   conceptName: string; correctConcept: string; explanation: string }
