import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'
import type { ApiResponse, StudySuggestion, AcceptSuggestionResponse, RejectSuggestionResponse, StudyGroupDetail, ChatMessage } from '../../types/api'
import { api, unwrap } from './helpers'

export async function requestStudyMatch(roomId: string, curriculumId: string): Promise<void> {
  await api.post(`/api/learning-rooms/${roomId}/match`, { curriculumId })
}

export async function getMatchableWeakness(roomId: string, weekId: string): Promise<boolean> {
  const res = await unwrap(api.get<ApiResponse<{ hasMatchableWeakness: boolean }>>(
    `/api/learning-rooms/${roomId}/curriculum/${weekId}/matchable-weakness`
  ))
  return res.hasMatchableWeakness
}

export async function getStudySuggestions(): Promise<StudySuggestion[]> {
  return unwrap(api.get<ApiResponse<StudySuggestion[]>>('/api/study-groups/suggestions'))
}

export async function acceptStudySuggestion(suggestionId: string): Promise<AcceptSuggestionResponse> {
  return unwrap(api.post<ApiResponse<AcceptSuggestionResponse>>(`/api/study-groups/suggestions/${suggestionId}/accept`))
}

export async function rejectStudySuggestion(suggestionId: string): Promise<RejectSuggestionResponse> {
  return unwrap(api.post<ApiResponse<RejectSuggestionResponse>>(`/api/study-groups/suggestions/${suggestionId}/reject`))
}

export async function getStudyGroup(groupId: string): Promise<StudyGroupDetail> {
  return unwrap(api.get<ApiResponse<StudyGroupDetail>>(`/api/study-groups/${groupId}`))
}

export async function getGroupMessages(groupId: string, page = 0, size = 50): Promise<ChatMessage[]> {
  return unwrap(api.get<ApiResponse<ChatMessage[]>>(`/api/study-groups/${groupId}/messages?page=${page}&size=${size}`))
}

export function connectGroupChat(groupId: string, onMessage: (msg: ChatMessage) => void): Client {
  const token = localStorage.getItem('accessToken') ?? ''
  const httpBase = import.meta.env.VITE_API_BASE_URL as string
  const client = new Client({
    webSocketFactory: () => new SockJS(`${httpBase}/ws/study-groups`),
    connectHeaders: { Authorization: `Bearer ${token}` },
    debug: () => {},
    reconnectDelay: 5000,
    onConnect: () => {
      client.subscribe(`/sub/chat/${groupId}`, (frame) => {
        try { onMessage(JSON.parse(frame.body) as ChatMessage) } catch {}
      })
    },
    onStompError: () => {},
  })
  client.activate()
  return client
}
