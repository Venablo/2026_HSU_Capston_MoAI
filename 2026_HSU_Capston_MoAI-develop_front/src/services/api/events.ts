import type { ApiResponse, LearningEventRequest, LearningEventResponse, InstantQuizResponse, SubmitQuizRequest, SubmitQuizResponse } from '../../types/api'
import { api, unwrap } from './helpers'

export async function sendEventLog(roomId: string, body: LearningEventRequest): Promise<LearningEventResponse> {
  return unwrap(api.post<ApiResponse<LearningEventResponse>>(`/api/learning-rooms/${roomId}/events`, body))
}

export async function getInstantQuiz(roomId: string, weekId: string): Promise<InstantQuizResponse> {
  return unwrap(api.get<ApiResponse<InstantQuizResponse>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}/quizzes/instant`))
}

export async function submitQuiz(body: SubmitQuizRequest): Promise<SubmitQuizResponse> {
  return unwrap(api.post<ApiResponse<SubmitQuizResponse>>('/api/quiz-attempts', body))
}
