import type { ApiResponse, FinalQuizResponse, SubmitFinalQuizRequest, SubmitFinalQuizResponse, QuizReportResponse } from '../../types/api'
import { api, unwrap } from './helpers'

export async function getFinalQuiz(roomId: string, weekId: string): Promise<FinalQuizResponse> {
  return unwrap(api.get<ApiResponse<FinalQuizResponse>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}/quizzes/final`))
}

export async function submitFinalQuiz(roomId: string, weekId: string, body: SubmitFinalQuizRequest): Promise<SubmitFinalQuizResponse> {
  return unwrap(api.post<ApiResponse<SubmitFinalQuizResponse>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}/quizzes/final/submit`, body))
}

export async function getQuizReport(roomId: string, weekId: string): Promise<QuizReportResponse> {
  return unwrap(api.get<ApiResponse<QuizReportResponse>>(`/api/learning-rooms/${roomId}/curriculum/${weekId}/quiz-report`))
}
