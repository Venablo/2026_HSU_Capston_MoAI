import type { ApiResponse, LearningRoomListItem, CurriculumWeekSummary, CurriculumWeekDetail, UpdateProgressRequest, UpdateProgressResponse, RecommendedVideo, MaterialListItem, MaterialDetail, QuizAttemptListItem, QuizAttemptDetail } from '../../types/api'
import { api, unwrap, wrappedArray, normalizeWeekSummary, normalizeWeekDetail, normalizeRecommendedVideo } from './helpers'

export async function getLearningRooms(): Promise<LearningRoomListItem[]> {
  const raw = await unwrap(api.get<ApiResponse<unknown>>('/api/learning-rooms'))
  if (Array.isArray(raw)) return raw as LearningRoomListItem[]
  if (raw && typeof raw === 'object') {
    const obj = raw as Record<string, unknown>
    for (const key of ['rooms', 'items', 'learningRooms', 'content', 'data']) {
      if (Array.isArray(obj[key])) return obj[key] as LearningRoomListItem[]
    }
  }
  return []
}

export async function deleteLearningRoom(roomId: string): Promise<void> {
  await api.delete(`/api/learning-rooms/${roomId}`)
}

export async function getCurriculum(roomId: string): Promise<CurriculumWeekSummary[]> {
  const raw = await unwrap(api.get<ApiResponse<unknown>>(`/api/learning-rooms/${roomId}/curriculum`))
  return wrappedArray(raw, ['weeklyCurriculums', 'curriculums', 'weeks', 'curriculum', 'content', 'items'])
    .map(normalizeWeekSummary)
    .filter((item): item is CurriculumWeekSummary => item !== null)
}

export async function getCurriculumWeek(roomId: string, weekId: string): Promise<CurriculumWeekDetail> {
  const raw = await unwrap(api.get<ApiResponse<unknown>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}`))
  return normalizeWeekDetail(raw, weekId)
}

export async function updateProgress(roomId: string, weekId: string, body: UpdateProgressRequest): Promise<UpdateProgressResponse> {
  return unwrap(api.patch<ApiResponse<UpdateProgressResponse>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}/progress`, body))
}

export async function getRecommendedVideos(roomId: string, weekId: string): Promise<RecommendedVideo[]> {
  const raw = await unwrap(api.get<ApiResponse<unknown>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}/recommended-videos`))
  return wrappedArray(raw, ['videos', 'recommendedVideos', 'items', 'content'])
    .map(normalizeRecommendedVideo)
    .filter((item): item is RecommendedVideo => item !== null)
}

export async function getMaterials(roomId: string): Promise<MaterialListItem[]> {
  return unwrap(api.get<ApiResponse<MaterialListItem[]>>(`/api/learning-rooms/${roomId}/materials`))
}

export async function getMaterialDetail(roomId: string, materialId: string): Promise<MaterialDetail> {
  return unwrap(api.get<ApiResponse<MaterialDetail>>(`/api/learning-rooms/${roomId}/materials/${materialId}`))
}

export async function getQuizAttempts(roomId: string, weekId: string): Promise<QuizAttemptListItem[]> {
  const raw = await unwrap(api.get<ApiResponse<unknown>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}/quiz-attempts`))
  if (Array.isArray(raw)) return raw as QuizAttemptListItem[]
  if (raw && typeof raw === 'object') {
    const obj = raw as Record<string, unknown>
    for (const key of ['quizAttempts', 'attempts', 'items', 'content']) {
      if (Array.isArray(obj[key])) return obj[key] as QuizAttemptListItem[]
    }
  }
  return []
}

export async function getQuizAttemptDetail(attemptId: string): Promise<QuizAttemptDetail> {
  return unwrap(api.get<ApiResponse<QuizAttemptDetail>>(`/api/quiz-attempts/${attemptId}`))
}
