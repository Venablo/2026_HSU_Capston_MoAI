import type { ApiResponse, StartFlippedRequest, StartFlippedResponse, EndFlippedRequest, EndFlippedResponse, FlippedResultResponse } from '../../types/api'
import { api, unwrap } from './helpers'

export async function startFlippedSession(roomId: string, body: StartFlippedRequest): Promise<StartFlippedResponse> {
  return unwrap(api.post<ApiResponse<StartFlippedResponse>>(`/api/learning-rooms/${roomId}/flipped/start`, body))
}

export function streamFlipped(roomId: string, sessionId: string, message: string): EventSource {
  const token = localStorage.getItem('accessToken') ?? ''
  const params = new URLSearchParams({ sessionId, message, token })
  return new EventSource(`${import.meta.env.VITE_API_BASE_URL}/api/learning-rooms/${roomId}/flipped/stream?${params}`)
}

export async function endFlippedSession(roomId: string, body: EndFlippedRequest): Promise<EndFlippedResponse> {
  return unwrap(api.post<ApiResponse<EndFlippedResponse>>(`/api/learning-rooms/${roomId}/flipped/end`, body))
}

export async function getFlippedResult(roomId: string, sessionId: string): Promise<FlippedResultResponse> {
  return unwrap(api.get<ApiResponse<FlippedResultResponse>>(`/api/learning-rooms/${roomId}/flipped/result/${sessionId}`))
}
