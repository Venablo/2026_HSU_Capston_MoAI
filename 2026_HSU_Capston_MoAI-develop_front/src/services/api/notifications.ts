import type { ApiResponse, NotificationItem, MarkReadResponse } from '../../types/api'
import { api, unwrap } from './helpers'

export function connectNotificationStream(): EventSource {
  const token = localStorage.getItem('accessToken') ?? ''
  return new EventSource(`${import.meta.env.VITE_API_BASE_URL}/api/notifications/stream?token=${token}`)
}

export async function getNotifications(): Promise<NotificationItem[]> {
  return unwrap(api.get<ApiResponse<NotificationItem[]>>('/api/notifications'))
}

export async function markNotificationRead(notificationId: string): Promise<MarkReadResponse> {
  return unwrap(api.patch<ApiResponse<MarkReadResponse>>(`/api/notifications/${notificationId}/read`))
}
