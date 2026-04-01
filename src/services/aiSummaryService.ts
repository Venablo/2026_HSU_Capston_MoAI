import type {
    PatternType,
    PatternAnalysisResponse,
    AISummaryResponse,
    MetaEvaluationResponse,
    StudyMatchResponse,
} from '../types/aiEvents'
import type { SummaryItem } from '../components/modals/SummaryDetailModal'

// ── Mock delay ────────────────────────────────────────────────────────────────
// Simulates network round-trip + AI processing time.
const MOCK_DELAY_MS = 1000

// ── Mock data — field names match API Spec v3.0 Response 200 examples ────────

/**
 * Mirrors the `aiTriggered: true` branch of POST /api/learning-rooms/{roomId}/events.
 * The frontend reads `eventType` to decide which flow to launch.
 */
const MOCK_PATTERN_RESPONSES: { [K in PatternType]: PatternAnalysisResponse } = {
    REWIND: {
        patternType:  'REWIND',
        actionType:   'monitoring',
        conceptName:  'ACID',
        reason:       '되감기 3회 이상 감지 — 해당 개념 보충 자료를 추천합니다.',
    },
    FAST_TRACK: {
        patternType:    'FAST_TRACK',
        actionType:     'fast-track',
        conceptName:    'ACID',
        reason:         '평균 대비 빠른 구간 완료 감지 — 심화 학습을 추천합니다.',
        completionRate: 240,        // 2.4× faster than average
        challengeLevel: 'advanced',
    },
}

/**
 * Mirrors GET /api/learning-rooms/{roomId}/materials/{materialId} → summaryItems.
 * API spec uses { label, title, desc }; mapped below to the modal's { letter, title, description }.
 *
 * Raw spec shape (materialId: "m1000000-0000-0000-0000-000000000001"):
 * [
 *   { "label": "A", "title": "Atomicity (원자성)",   "desc": "트랜잭션은 완전히 수행되거나 전혀 수행되지 않아야 합니다." },
 *   { "label": "C", "title": "Consistency (일관성)", "desc": "시스템의 고정 요소는 트랜잭션 수행 전후에 같아야 합니다." },
 *   { "label": "I", "title": "Isolation (고립성)",   "desc": "트랜잭션 실행 중에는 다른 트랜잭션이 끼어들 수 없습니다." },
 *   { "label": "D", "title": "Durability (지속성)",  "desc": "성공적으로 완료된 트랜잭션의 결과는 영구적으로 반영됩니다." }
 * ]
 */
const MOCK_CONCEPT_SUMMARIES: Record<string, SummaryItem[]> = {
    ACID: [
        {
            letter:      'A',
            title:       'Atomicity (원자성)',
            description: '트랜잭션은 완전히 수행되거나 전혀 수행되지 않아야 합니다.',
        },
        {
            letter:      'C',
            title:       'Consistency (일관성)',
            description: '시스템의 고정 요소는 트랜잭션 수행 전후에 같아야 합니다.',
        },
        {
            letter:      'I',
            title:       'Isolation (고립성)',
            description: '트랜잭션 실행 중에는 다른 트랜잭션이 끼어들 수 없습니다.',
        },
        {
            letter:      'D',
            title:       'Durability (지속성)',
            description: '성공적으로 완료된 트랜잭션의 결과는 영구적으로 반영됩니다.',
        },
    ],
}

/**
 * Mirrors POST /api/learning-rooms/{roomId}/flipped/end → flippedResult payload.
 * Raw spec: { sessionId, flippedResult: "pass", score: 95, gainedKeywords, weakKeywords, feedback }
 * Mapped to MetaEvaluationResponse for the meta-evaluation modal.
 */
const MOCK_META_EVALUATION: MetaEvaluationResponse = {
    comprehensionScore: 95,
    strongKeywords:     ['원자성', 'COMMIT', 'ROLLBACK', '트랜잭션'],
    weakKeywords:       ['격리성', '잠금(Lock)', '데드락'],
}

/**
 * Mirrors GET /api/study-groups/suggestions → partner block.
 * Raw spec: { suggestionId, suggestedRole: "mentee", partner: { nickname, profileImageUrl, strengthKeyword },
 *             matchScore: 0.98, matchKeyword, matchReason, status: "pending" }
 * Mapped to StudyMatchResponse for the study-matching modal.
 */
const MOCK_STUDY_MATCH: StudyMatchResponse = {
    partnerId:        'c0000000-0000-0000-0000-000000000003',
    partnerName:      'B학생(멘토)',
    partnerAvatar:    '👩‍💻',
    partnerRole:      'mentor',
    matchRate:        98,                                         // matchScore 0.98 → 98 %
    partnerStrengths: ['고립성_완벽이해', 'DB 설계', '트랜잭션 전문가'],
}

// ── Service functions ─────────────────────────────────────────────────────────

/**
 * Simulates the frontend-side pattern analysis that triggers AI modals.
 * In production this data comes from POST /api/learning-rooms/{roomId}/events
 * (aiTriggered: true branch) — the patternType is derived from eventType.
 *
 * TODO — replace with real call:
 *   const event = await sendEventLog(roomId, { event_type, curriculum_id, payload })
 *   if (event.aiTriggered) { // use event.eventType to open the correct modal }
 */
export function fetchAISummary(
    { patternType }: { patternType: PatternType }
): Promise<PatternAnalysisResponse> {
    return new Promise(resolve =>
        setTimeout(() => resolve(MOCK_PATTERN_RESPONSES[patternType]), MOCK_DELAY_MS)
    )
}

/**
 * Simulates GET /api/learning-rooms/{roomId}/materials/{materialId}.
 * The real response contains { materialId, title, videoSegment, summaryItems }
 * where summaryItems use { label, title, desc } (API spec field names).
 * This function maps those to { letter, title, description } for SummaryDetailModal.
 *
 * TODO — replace with real call:
 *   import { getMaterialDetail } from './apiService'
 *   const raw = await getMaterialDetail(roomId, materialId)
 *   const summaryItems = raw.summaryItems.map(s => ({
 *     letter: s.label, title: s.title, description: s.desc
 *   }))
 *   return { conceptName: raw.title, summaryItems }
 */
export function fetchConceptSummary(conceptName: string): Promise<AISummaryResponse> {
    return new Promise(resolve =>
        setTimeout(
            () => resolve({
                conceptName,
                summaryItems: MOCK_CONCEPT_SUMMARIES[conceptName] ?? [],
            }),
            MOCK_DELAY_MS
        )
    )
}

/**
 * Simulates the comprehension score from POST /api/learning-rooms/{roomId}/flipped/end.
 * Real response: { sessionId, flippedResult, score, gainedKeywords, weakKeywords, feedback }
 * gainedKeywords  → strongKeywords
 * weakKeywords    → weakKeywords
 * score           → comprehensionScore
 *
 * TODO — replace with real call:
 *   import { endFlippedSession } from './apiService'
 *   const result = await endFlippedSession(roomId, { sessionId })
 *   return {
 *     comprehensionScore: result.score,
 *     strongKeywords:     result.gainedKeywords,
 *     weakKeywords:       result.weakKeywords,
 *   }
 */
export function fetchMetaEvaluation(
    _explanation: string
): Promise<MetaEvaluationResponse> {
    return new Promise(resolve =>
        setTimeout(() => resolve(MOCK_META_EVALUATION), MOCK_DELAY_MS)
    )
}

/**
 * Simulates GET /api/study-groups/suggestions (first pending suggestion).
 * Real response: { suggestionId, suggestedRole, partner: { nickname, profileImageUrl, strengthKeyword },
 *                  matchScore, matchKeyword, matchReason, status }
 * matchScore 0.98 is converted to matchRate 98 for the modal.
 *
 * TODO — replace with real call:
 *   import { getStudySuggestions } from './apiService'
 *   const [suggestion] = await getStudySuggestions()
 *   return {
 *     partnerId:        suggestion.partner.profileImageUrl, // use userId from group detail
 *     partnerName:      suggestion.partner.nickname,
 *     partnerAvatar:    '👩‍💻',
 *     partnerRole:      suggestion.suggestedRole === 'mentee' ? 'mentor' : 'mentee',
 *     matchRate:        Math.round(suggestion.matchScore * 100),
 *     partnerStrengths: [suggestion.partner.strengthKeyword],
 *   }
 */
export function fetchStudyMatch(
    _evaluation: MetaEvaluationResponse
): Promise<StudyMatchResponse> {
    return new Promise(resolve =>
        setTimeout(() => resolve(MOCK_STUDY_MATCH), MOCK_DELAY_MS)
    )
}
