/**
 * aiSummaryService.ts  —  스터디 매칭 서비스
 *
 * fetchStudyMatch: 메타인지 평가 완료 후 스터디 파트너를 조회하여 StudyMatchResponse로 변환한다.
 * GET /api/study-groups/suggestions → StudySuggestion[] → StudyMatchResponse
 *
 * 매핑 규칙:
 *   suggestions[0].suggestionId          → partnerId
 *   suggestions[0].partner.nickname      → partnerName
 *   suggestions[0].matchScore * 100      → matchRate
 *   suggestions[0].partner.strengthKeyword → partnerStrengths[0]
 *   suggestions[0].suggestedRole === 'mentee' → partnerRole = 'mentor' (나의 역할이 멘티면 파트너는 멘토)
 */

import type { MetaEvaluationResponse, StudyMatchResponse } from '../types/aiEvents'
import { getStudySuggestions } from './apiService'

/**
 * fetchStudyMatch  —  스터디 파트너 매칭 조회
 *
 * ClassroomModals.handleMatching 에서 호출된다.
 * _evaluation 파라미터는 API 응답에서 매칭이 결정되므로 현재 미사용.
 */
export async function fetchStudyMatch(
    _evaluation: MetaEvaluationResponse,
): Promise<StudyMatchResponse> {
    const suggestions = await getStudySuggestions()

    if (!suggestions.length) {
        throw new Error('매칭 가능한 파트너가 없습니다. 잠시 후 다시 시도해 주세요.')
    }

    const s = suggestions[0]
    return {
        partnerId:        s.suggestionId,
        partnerName:      s.partner.nickname,
        partnerAvatar:    '👤',
        partnerRole:      s.suggestedRole === 'mentee' ? 'mentor' : 'mentee',
        matchRate:        Math.round(s.matchScore * 100),
        partnerStrengths: [s.partner.strengthKeyword],
    }
}
