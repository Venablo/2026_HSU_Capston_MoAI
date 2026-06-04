import type { ApiResponse, UserKeywordsResponse, CurriculumKeywordsResponse } from '../../types/api'
import { api, unwrap } from './helpers'

export async function getUserKeywords(roomId: string): Promise<UserKeywordsResponse> {
  return unwrap(api.get<ApiResponse<UserKeywordsResponse>>(`/api/learning-rooms/${roomId}/keywords`))
}

export async function getCurriculumKeywords(roomId: string, weekId: string): Promise<CurriculumKeywordsResponse> {
  return unwrap(api.get<ApiResponse<CurriculumKeywordsResponse>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}/keywords`))
}
