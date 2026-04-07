/**
 * DebugToast  —  Development-only event log overlay
 *
 * Renders a fixed stack of semi-transparent toast pills in the top-right corner.
 * Each pill represents one learning event and cycles through three visual states:
 *
 *   sending     → blue   (⬆)  event detected, waiting for API response
 *   triggered   → purple (✦)  API responded with aiTriggered: true
 *   no-trigger  → dim blue (–) API responded with aiTriggered: false
 *
 * Hidden completely in production (import.meta.env.DEV === false).
 */

import type { DebugToastItem } from '../../../hooks/useDebugToast'

interface Props {
    toasts: DebugToastItem[]
}

// ── Per-status visual config ──────────────────────────────────────────────────
const STATUS_CONFIG = {
    sending: {
        bg:     'rgba(37, 99, 235, 0.82)',   // blue-700
        border: 'rgba(147, 197, 253, 0.4)',  // blue-300
        icon:   '⬆',
        label:  'Sending',
        aiTriggeredText: '—',
    },
    triggered: {
        bg:     'rgba(109, 40, 217, 0.88)',  // violet-700
        border: 'rgba(196, 181, 253, 0.4)', // violet-300
        icon:   '✦',
        label:  'AI Triggered',
        aiTriggeredText: 'true',
    },
    'no-trigger': {
        bg:     'rgba(37, 99, 235, 0.45)',   // blue, dimmed
        border: 'rgba(147, 197, 253, 0.25)',
        icon:   '–',
        label:  'No Trigger',
        aiTriggeredText: 'false',
    },
} as const satisfies Record<DebugToastItem['status'], {
    bg: string; border: string; icon: string; label: string; aiTriggeredText: string
}>

export default function DebugToast({ toasts }: Props) {
    if (!import.meta.env.DEV || toasts.length === 0) return null

    return (
        <div
            style={{
                position:      'fixed',
                top:           '16px',
                right:         '16px',
                zIndex:        9999,
                display:       'flex',
                flexDirection: 'column',
                gap:           '6px',
                pointerEvents: 'none',
            }}
            aria-hidden="true"
        >
            {toasts.map(toast => {
                const cfg = STATUS_CONFIG[toast.status]
                return (
                    <div
                        key={toast.id}
                        style={{
                            display:        'flex',
                            alignItems:     'center',
                            gap:            '8px',
                            padding:        '7px 12px',
                            borderRadius:   '8px',
                            border:         `1px solid ${cfg.border}`,
                            backgroundColor: cfg.bg,
                            backdropFilter: 'blur(8px)',
                            boxShadow:      '0 2px 10px rgba(0,0,0,0.3)',
                            animation:      'fadeIn 0.15s ease',
                            fontFamily:     'ui-monospace, SFMono-Regular, monospace',
                            fontSize:       '11px',
                            color:          '#fff',
                            whiteSpace:     'nowrap',
                        }}
                    >
                        {/* Status icon */}
                        <span style={{ fontSize: '12px', opacity: 0.9 }}>
                            {cfg.icon}
                        </span>

                        {/* Label */}
                        <span style={{ fontWeight: 700, letterSpacing: '0.03em' }}>
                            {cfg.label}
                        </span>

                        <span style={{ opacity: 0.4 }}>·</span>

                        {/* Event type */}
                        <span style={{ opacity: 0.9, color: '#e0e7ff' }}>
                            {toast.eventType}
                        </span>

                        <span style={{ opacity: 0.4 }}>·</span>

                        {/* aiTriggered value */}
                        <span style={{ opacity: 0.7 }}>
                            aiTriggered:&nbsp;
                            <span style={{
                                fontWeight: 700,
                                color: cfg.aiTriggeredText === 'true'
                                    ? '#c4b5fd'   // violet-300
                                    : cfg.aiTriggeredText === 'false'
                                        ? '#93c5fd'  // blue-300
                                        : '#94a3b8', // slate (pending)
                            }}>
                                {cfg.aiTriggeredText}
                            </span>
                        </span>
                    </div>
                )
            })}
        </div>
    )
}
