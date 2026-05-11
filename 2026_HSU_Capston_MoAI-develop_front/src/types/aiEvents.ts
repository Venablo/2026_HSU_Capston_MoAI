import type { SummaryItem } from '../components/modals/monitoring/SummaryDetailModal'
import type { InstantQuizResponse } from './api'

// ─── Meta-cognition evaluation ────────────────────────────────────────────────

/**
 * Comprehension result shown in MetaEvaluationModal.
 *
 * Mapped from EndFlippedResponse (api.ts) inside ClassroomModals.handleSessionEnd:
 *   EndFlippedResponse.score          → comprehensionScore
 *   EndFlippedResponse.gainedKeywords → strongKeywords
 *   EndFlippedResponse.weakKeywords   → weakKeywords
 */
export interface MetaEvaluationResponse {
    comprehensionScore: number
    strongKeywords: string[]
    weakKeywords: string[]
}

// ─── Study partner matching ───────────────────────────────────────────────────

/**
 * Partner data shown in StudyMatchingModal.
 *
 * Mapped from StudySuggestion (api.ts) inside aiSummaryService.fetchStudyMatch:
 *   matchScore * 100           → matchRate
 *   partner.nickname           → partnerName
 *   partner.strengthKeyword    → partnerStrengths[0]
 *   matchKeyword               → matchKeyword
 *   suggestedRole              → partnerRole
 */
export interface StudyMatchResponse {
    partnerId: string
    partnerName: string
    /** Single emoji or initials rendered as the avatar */
    partnerAvatar: string
    partnerRole: 'mentor' | 'mentee'
    /** matchScore (0–1) × 100, e.g. 98 */
    matchRate: number
    partnerStrengths: string[]
    /** The specific keyword that triggered this match (user's weak ↔ partner's strong) */
    matchKeyword: string
}

// ─── Modal data (discriminated union) ────────────────────────────────────────

/**
 * Typed payload attached to each open modal.
 * The 'type' discriminant always matches the ModalKey it belongs to,
 * so ClassroomModals can narrow the union without casting.
 *
 * 'monitoring' note:
 *   summaryItems are pre-fetched from getMaterialDetail() in StudyClassroom
 *   before this modal is opened, so SummaryDetailModal can display them
 *   instantly with no additional network call.
 */
export type ModalData =
    | {
        type: 'monitoring'
        conceptName: string
        reason: string
        /** Mapped from MaterialDetail.summaryItems: { label→letter, desc→description } */
        summaryItems: SummaryItem[]
      }
    | { type: 'summary-detail';  conceptName: string; summaryItems: SummaryItem[] }
    | {
        type: 'fast-track'
        conceptName: string
        reason: string
        completionRate: number
        challengeLevel: 'intermediate' | 'advanced'
      }
    | { type: 'quiz-pass'; quiz: InstantQuizResponse }
    | { type: 'flipped' }
    | {
        type: 'reverse-learning'
        conceptName: string
        /** roomId for startFlippedSession / streamFlipped / endFlippedSession */
        roomId?: string
        /** curriculum_id for startFlippedSession body */
        weekId?: string
      }
    | { type: 'meta-evaluation'; evaluation: MetaEvaluationResponse }
    | { type: 'study-matching';  match: StudyMatchResponse }
    | { type: 'quiz-correct';    conceptName: string }
    | { type: 'quiz-incorrect';  conceptName: string; correctConcept: string; explanation: string }
    | {
        type: 'final-quiz'
        /** Used by FinalQuizModal for getFinalQuiz / submitFinalQuiz / polling */
        roomId: string
        weekId: string
        /** If true, skip answering and go straight to the existing report */
        reviewMode?: boolean
      }
