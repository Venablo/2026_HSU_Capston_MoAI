import api from '../../api/axios'
import type { ApiResponse, ResourceItem, CurriculumWeekDetail, CurriculumWeekSummary, RecommendedVideo } from '../../types/api'

export async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const { data: envelope } = await promise
  if (!envelope.success) throw new Error(envelope.message ?? 'API 요청이 실패했습니다.')
  return envelope.data
}

export function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

export function stringValue(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback
}

export function numberValue(value: unknown, fallback = 0): number {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : fallback
}

export function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

export function wrappedArray(value: unknown, keys: string[]): unknown[] {
  if (Array.isArray(value)) return value
  const obj = asRecord(value)
  if (!obj) return []
  for (const key of keys) {
    if (Array.isArray(obj[key])) return obj[key] as unknown[]
  }
  return []
}

export function youtubeIdFromUrl(url: string): string {
  const match = url.match(/(?:youtu\.be\/|youtube\.com\/(?:watch\?v=|embed\/|shorts\/))([A-Za-z0-9_-]{11})/)
  return match?.[1] ?? ''
}

export function normalizeResource(value: unknown): ResourceItem | null {
  const obj = asRecord(value)
  if (!obj) return null
  const url = stringValue(obj.url) || stringValue(obj.fileUrl) || stringValue(obj.resourceUrl) || stringValue(obj.downloadUrl)
  const title = stringValue(obj.title) || stringValue(obj.name) || stringValue(obj.fileName) || '학습자료'
  const type = stringValue(obj.type) || stringValue(obj.resourceType) || stringValue(obj.fileType) || 'pdf'
  return { type, title, url, size: stringValue(obj.size) || stringValue(obj.fileSize) || '-' }
}

export function normalizeWeekDetail(raw: unknown, fallbackWeekId: string): CurriculumWeekDetail {
  const root = asRecord(raw)
  const obj =
    root && !('weekId' in root) && !('weekNumber' in root)
      ? (asRecord(root.week) ?? asRecord(root.weekDetail) ?? asRecord(root.curriculum) ?? asRecord(root.data) ?? root)
      : root
  const source: Record<string, unknown> = obj ?? {}
  const resourceSource = source.resources ?? source.materials ?? source.learningResources ?? source
  const resources = wrappedArray(resourceSource, ['resources', 'materials', 'learningResources', 'items', 'content'])
    .map(normalizeResource)
    .filter((item): item is ResourceItem => item !== null)
  return {
    weekId: stringValue(source.weekId, fallbackWeekId),
    weekNumber: numberValue(source.weekNumber),
    topic: stringValue(source.topic, 'Untitled week'),
    description: stringValue(source.description),
    completionRate: numberValue(source.completionRate),
    keywords: stringArray(source.keywords),
    resources,
    mainVideoId: stringValue(source.mainVideoId)
      || youtubeIdFromUrl(stringValue(source.mainVideoUrl))
      || youtubeIdFromUrl(stringValue(source.videoUrl)),
  }
}

export function normalizeWeekSummary(value: unknown): CurriculumWeekSummary | null {
  const obj = asRecord(value)
  if (!obj) return null
  const weekId = stringValue(obj.weekId)
  if (!weekId) return null
  return {
    weekId,
    weekNumber: numberValue(obj.weekNumber),
    topic: stringValue(obj.topic, 'Untitled week'),
    completionRate: numberValue(obj.completionRate),
  }
}

export function normalizeRecommendedVideo(value: unknown): RecommendedVideo | null {
  const obj = asRecord(value)
  if (!obj) return null
  const url = stringValue(obj.url) || stringValue(obj.videoUrl)
  const videoId = stringValue(obj.videoId) || stringValue(obj.youtubeVideoId) || youtubeIdFromUrl(url)
  if (!videoId) return null
  return {
    videoId,
    title: stringValue(obj.title) || stringValue(obj.name) || 'YouTube 영상',
    durationSec: numberValue(obj.durationSec),
    viewCount: numberValue(obj.viewCount),
    isMain: Boolean(obj.isMain),
    thumbnailUrl: stringValue(obj.thumbnailUrl) || undefined,
  }
}

// api 인스턴스 re-export (도메인 파일에서 공통 사용)
export { api }
