/**
 * useApiConnectionStatus  —  React hook for live API connection state
 *
 * Subscribes to the apiStatus module so the badge re-renders whenever
 * an API call completes.
 */

import { useState, useEffect } from 'react'
import { getState, subscribeStatus, type ApiStatusState } from '../api/apiStatus'

export function useApiConnectionStatus(): ApiStatusState {
  const [state, setState] = useState<ApiStatusState>(getState)
  useEffect(() => subscribeStatus(setState), [])
  return state
}
