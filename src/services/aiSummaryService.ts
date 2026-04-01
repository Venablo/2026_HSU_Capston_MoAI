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

// ── Mock data ─────────────────────────────────────────────────────────────────

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
        completionRate: 240,         // 2.4× faster than average
        challengeLevel: 'advanced',
    },
}

const MOCK_CONCEPT_SUMMARIES: Record<string, SummaryItem[]> = {
    ACID: [
        {
            letter: 'A',
            title: '원자성 (Atomicity)',
            description:
                '트랜잭션 내의 모든 연산은 완전히 실행되거나 전혀 실행되지 않아야 합니다. 일부만 반영되는 상황은 허용되지 않습니다.',
        },
        {
            letter: 'C',
            title: '일관성 (Consistency)',
            description:
                '트랜잭션 실행 전후로 데이터베이스는 항상 일관된 상태를 유지해야 합니다. 정의된 규칙과 제약 조건이 항상 만족되어야 합니다.',
        },
        {
            letter: 'I',
            title: '격리성 (Isolation)',
            description:
                '동시에 실행되는 여러 트랜잭션은 서로 간섭하지 않아야 합니다. 각 트랜잭션은 독립적으로 실행되는 것처럼 동작해야 합니다.',
        },
        {
            letter: 'D',
            title: '지속성 (Durability)',
            description:
                '성공적으로 완료된 트랜잭션의 결과는 영구적으로 저장되어야 합니다. 시스템 오류가 발생하더라도 데이터가 유지되어야 합니다.',
        },
    ],
}

const MOCK_META_EVALUATION: MetaEvaluationResponse = {
    comprehensionScore: 95,
    strongKeywords: ['원자성', 'COMMIT', 'ROLLBACK', '트랜잭션'],
    weakKeywords: ['격리성', '잠금(Lock)', '데드락'],
}

const MOCK_STUDY_MATCH: StudyMatchResponse = {
    partnerId:        'user_kim_mentor',
    partnerName:      '김지현',
    partnerAvatar:    '👩‍💻',
    partnerRole:      'mentor',
    matchRate:        98,
    partnerStrengths: ['DB 설계', '트랜잭션 전문가', '격리성 심화'],
}

// ── Service functions ─────────────────────────────────────────────────────────

/**
 * Analyses a detected learning pattern and returns the AI recommendation.
 *
 * Request body: { patternType: PatternType }
 * The response shape differs by pattern — use the actionType discriminant
 * to decide which modal to open.
 *
 * TODO — replace mock with real call:
 *   import axios from 'axios'
 *   const { data } = await axios.post<PatternAnalysisResponse>(
 *       '/api/ai/analyze',
 *       { patternType }
 *   )
 *   return data
 */
export function fetchAISummary(
    { patternType }: { patternType: PatternType }
): Promise<PatternAnalysisResponse> {
    return new Promise(resolve =>
        setTimeout(() => resolve(MOCK_PATTERN_RESPONSES[patternType]), MOCK_DELAY_MS)
    )
}

/**
 * Fetches an AI-generated structured concept breakdown.
 * Called by ClassroomModals after the user accepts the summary suggestion.
 *
 * TODO — replace mock with real call:
 *   import axios from 'axios'
 *   const { data } = await axios.get<AISummaryResponse>(
 *       `/api/ai/summary/${conceptName}`
 *   )
 *   return data
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
 * Evaluates the user's explanation text and returns a meta-cognition score.
 *
 * Request body: { explanation: string }
 *
 * TODO — replace mock with real call:
 *   import axios from 'axios'
 *   const { data } = await axios.post<MetaEvaluationResponse>(
 *       '/api/ai/meta-evaluate',
 *       { explanation }
 *   )
 *   return data
 */
export function fetchMetaEvaluation(
    _explanation: string
): Promise<MetaEvaluationResponse> {
    return new Promise(resolve =>
        setTimeout(() => resolve(MOCK_META_EVALUATION), MOCK_DELAY_MS)
    )
}

/**
 * Finds the best 1:1 study partner based on the user's evaluation results.
 *
 * Request body: { evaluation: MetaEvaluationResponse }
 *
 * TODO — replace mock with real call:
 *   import axios from 'axios'
 *   const { data } = await axios.post<StudyMatchResponse>(
 *       '/api/ai/match',
 *       { evaluation }
 *   )
 *   return data
 */
export function fetchStudyMatch(
    _evaluation: MetaEvaluationResponse
): Promise<StudyMatchResponse> {
    return new Promise(resolve =>
        setTimeout(() => resolve(MOCK_STUDY_MATCH), MOCK_DELAY_MS)
    )
}
