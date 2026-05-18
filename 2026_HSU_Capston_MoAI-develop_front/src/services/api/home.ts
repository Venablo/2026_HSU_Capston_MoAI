import type { ApiResponse, UserProfileBasic, OnboardingKeywordsResponse, CreateLearningRoomRequest, CreateLearningRoomResponse } from '../../types/api'
import { api, unwrap } from './helpers'

export async function getHomeProfile(): Promise<UserProfileBasic> {
  return unwrap(api.get<ApiResponse<UserProfileBasic>>('/api/users/me'))
}

export async function getOnboardingKeywords(): Promise<OnboardingKeywordsResponse> {
  return unwrap(api.get<ApiResponse<OnboardingKeywordsResponse>>('/api/onboarding/keywords'))
}

export async function createLearningRoom(body: CreateLearningRoomRequest): Promise<CreateLearningRoomResponse> {
  return unwrap(api.post<ApiResponse<CreateLearningRoomResponse>>('/api/learning-rooms', body))
}
