/**
 * useDebugToast  —  Development-only debug toast state manager
 *
 * Lifecycle per event:
 *   1. addToast(eventType) → shows a blue "Sending" toast, returns its id
 *   2. resolveToast(id, aiTriggered) → replaces it with:
 *        - purple "AI Triggered" toast  (aiTriggered: true)
 *        - dim blue "No Trigger" toast  (aiTriggered: false)
 *
 * No-ops in production (import.meta.env.DEV === false).
 */

import { useState, useCallback, useRef } from 'react'

export type DebugToastStatus = 'sending' | 'triggered' | 'no-trigger'

export interface DebugToastItem {
    id: number
    eventType: string
    status: DebugToastStatus
}

let _nextId = 0

export function useDebugToast() {
    const [toasts, setToasts] = useState<DebugToastItem[]>([])

    // Store per-toast timeout handles so we can reset them on resolve
    const timersRef = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map())

    const removeToast = useCallback((id: number) => {
        setToasts(prev => prev.filter(t => t.id !== id))
        timersRef.current.delete(id)
    }, [])

    const scheduleRemoval = useCallback((id: number, delayMs: number) => {
        // Clear any existing timer for this id before setting a new one
        const existing = timersRef.current.get(id)
        if (existing !== undefined) clearTimeout(existing)

        const handle = setTimeout(() => removeToast(id), delayMs)
        timersRef.current.set(id, handle)
    }, [removeToast])

    /**
     * Show a blue "Sending" toast immediately.
     * Returns the toast id to pass into resolveToast().
     * Returns -1 in production (no-op).
     */
    const addToast = useCallback((eventType: string): number => {
        if (!import.meta.env.DEV) return -1

        const id = ++_nextId
        setToasts(prev => [...prev, { id, eventType, status: 'sending' }])
        scheduleRemoval(id, 3000)
        return id
    }, [scheduleRemoval])

    /**
     * Update an existing toast once the API response arrives.
     * - aiTriggered: true  → purple "AI Triggered"
     * - aiTriggered: false → dim blue "No Trigger"
     * The toast auto-removes 3 s after this call.
     */
    const resolveToast = useCallback((id: number, aiTriggered: boolean) => {
        if (!import.meta.env.DEV || id === -1) return

        const status: DebugToastStatus = aiTriggered ? 'triggered' : 'no-trigger'
        setToasts(prev => prev.map(t => t.id === id ? { ...t, status } : t))
        scheduleRemoval(id, 3000)
    }, [scheduleRemoval])

    return { toasts, addToast, resolveToast }
}
