/**
 * useApiConnectionStatus  —  React hook for live API connection state
 *
 * Subscribes to the apiStatus module so the badge re-renders whenever
 * an API call completes.  Also exposes isMock so callers can branch on mode.
 */

import { useState, useEffect } from 'react'
import { getState, subscribeStatus, type ApiStatusState } from '../api/apiStatus'

export interface ApiConnectionStatus extends ApiStatusState {
  isMock: boolean
}

export function useApiConnectionStatus(): ApiConnectionStatus {
  const [state, setState] = useState<ApiStatusState>(getState)
  const isMock = import.meta.env.VITE_USE_MOCK === 'true'

  useEffect(() => subscribeStatus(setState), [])

  return { ...state, isMock }
}
