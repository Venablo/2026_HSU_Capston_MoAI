/**
 * apiStatus  —  Tiny pub/sub module for tracking the last API call result.
 *
 * Used by ConnectionStatusBadge (dev only) to show live/mock status visually.
 * Has no React dependency — plain module-level state + listener set.
 */

export type ConnectionStatus = 'idle' | 'ok' | 'error'

export interface ApiStatusState {
  status:        ConnectionStatus
  lastUrl:       string
  httpStatus?:   number
  errorMessage?: string
}

type Listener = (state: ApiStatusState) => void

const listeners = new Set<Listener>()
let _state: ApiStatusState = { status: 'idle', lastUrl: '' }

export function getState(): ApiStatusState { return _state }

export function subscribeStatus(fn: Listener): () => void {
  listeners.add(fn)
  return () => { listeners.delete(fn) }
}

export function emitStatus(next: ApiStatusState): void {
  _state = next
  listeners.forEach(fn => fn(next))
}
