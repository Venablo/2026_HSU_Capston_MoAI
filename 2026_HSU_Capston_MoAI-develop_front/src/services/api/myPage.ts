import type { ApiResponse, UserProfileFull, UpdateProfileRequest, UpdateProfileResponse, UpdateKeywordsRequest, UpdateKeywordsResponse, DeleteAccountRequest, LearningHistoryItem } from '../../types/api'
import { api, unwrap } from './helpers'

export async function getMyProfile(): Promise<UserProfileFull> {
  return unwrap(api.get<ApiResponse<UserProfileFull>>('/api/users/me'))
}

export async function updateProfile(body: UpdateProfileRequest): Promise<UpdateProfileResponse> {
  return unwrap(api.patch<ApiResponse<UpdateProfileResponse>>('/api/users/me', body))
}

export async function updateKeywords(body: UpdateKeywordsRequest): Promise<UpdateKeywordsResponse> {
  return unwrap(api.patch<ApiResponse<UpdateKeywordsResponse>>('/api/users/me/keywords', body))
}

export async function deleteAccount(body: DeleteAccountRequest): Promise<void> {
  await api.delete('/api/users/me', { data: body })
}

export async function getLearningHistory(): Promise<LearningHistoryItem[]> {
  return unwrap(api.get<ApiResponse<LearningHistoryItem[]>>('/api/users/me/learning-history'))
}
