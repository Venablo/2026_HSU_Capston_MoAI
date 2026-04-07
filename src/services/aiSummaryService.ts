/**
 * aiSummaryService.ts  —  학습 흐름 Mock 서비스 (스터디 매칭)
 *
 * 역할:
 *   백엔드 연결 전 StudyMatchingModal에 표시할 파트너 정보를 흉내내는 Mock 서비스.
 *   현재 이 파일의 함수는 ClassroomModals.tsx에서 호출된다.
 *
 * ─── 실제 API 연결 시 교체 대상 ───────────────────────────────────────────────
 *
 *   fetchStudyMatch (현재 mock)
 *     → getStudySuggestions() (apiService.ts) 로 교체
 *     → 응답 StudySuggestion을 StudyMatchResponse로 매핑:
 *         { matchScore * 100 → matchRate, partner.nickname → partnerName,
 *           partner.strengthKeyword → partnerStrengths[0] }
 *
 * ─── 제거된 Mock 함수 (2026-04-04) ───────────────────────────────────────────
 *
 *   fetchAISummary    → 구 스펙(POST /api/ai/analyze)용 mock. 실제 흐름은
 *                        sendEventLog() → getMaterialDetail()로 대체됨.
 *   fetchConceptSummary → 개념 요약 mock. summaryItems는 이제 StudyClassroom이
 *                         getMaterialDetail() 호출 시 이미 받아서 monitoring
 *                         ModalData에 함께 저장하므로 별도 fetch 불필요.
 *   fetchMetaEvaluation → import하는 곳 없음. endFlippedSession() 응답의
 *                          score/gainedKeywords/weakKeywords를 직접 사용한다.
 */

import type { MetaEvaluationResponse, StudyMatchResponse } from '../types/aiEvents'

const MOCK_DELAY_MS = 1000

/**
 * Mock 스터디 파트너 데이터.
 *
 * 실제 API 응답 형태 (GET /api/study-groups/suggestions → StudySuggestion):
 *   { suggestionId, suggestedRole: "mentee",
 *     partner: { nickname: "B학생", profileImageUrl, strengthKeyword: "고립성_완벽이해" },
 *     matchScore: 0.98, matchKeyword: "...", matchReason: "...", status: "pending" }
 *
 * 매핑 규칙:
 *   partner.nickname       → partnerName
 *   matchScore * 100       → matchRate (98)
 *   partner.strengthKeyword → partnerStrengths[0]
 *   suggestedRole          → partnerRole
 */
const MOCK_STUDY_MATCH: StudyMatchResponse = {
    partnerId:        'c0000000-0000-0000-0000-000000000003',
    partnerName:      'B학생(멘토)',
    partnerAvatar:    '👩‍💻',
    partnerRole:      'mentor',
    matchRate:        98,
    partnerStrengths: ['고립성_완벽이해', 'DB 설계', '트랜잭션 전문가'],
}

/**
 * fetchStudyMatch  —  스터디 파트너 매칭 Mock
 *
 * 메타인지 평가 완료 후 ClassroomModals.handleMatching에서 호출된다.
 * _evaluation 파라미터는 실제 API 호환을 위해 형식상 유지 (현재 미사용).
 *
 * TODO: 실제 API 연결 시 아래 코드로 교체:
 *   import { getStudySuggestions } from './apiService'
 *   const [suggestion] = await getStudySuggestions()
 *   return {
 *     partnerId:        suggestion.partner.profileImageUrl,
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
