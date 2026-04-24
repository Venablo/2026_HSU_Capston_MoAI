/**
 * ConnectionStatusBadge  —  Development-only connection mode indicator
 *
 * Renders a fixed pill in the bottom-left corner.  Hidden in production.
 *
 * Mock mode  → amber  "MOCK"  (no real requests are made)
 * Live / idle → neutral "LIVE —"
 * Live / ok   → green  "LIVE ✓ 200"
 * Live / error → red   "LIVE ✗ <message>"
 */

import type { CSSProperties } from 'react'
import { useApiConnectionStatus } from '../../../hooks/useApiConnectionStatus'

export default function ConnectionStatusBadge() {
  if (!import.meta.env.DEV) return null

  const { isMock, status, httpStatus, errorMessage, lastUrl } = useApiConnectionStatus()

  // ── Mock mode ─────────────────────────────────────────────────────────────
  if (isMock) {
    return (
      <div style={WRAPPER_STYLE} title="VITE_USE_MOCK=true — no real HTTP calls">
        <span style={{ ...DOT_STYLE, background: '#f59e0b' }} />
        <span style={{ color: '#fbbf24', fontWeight: 700 }}>MOCK</span>
        <span style={{ opacity: 0.45 }}>·</span>
        <span style={{ opacity: 0.7, maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis' }}>
          mock data active
        </span>
      </div>
    )
  }

  // ── Real server mode ──────────────────────────────────────────────────────
  const isIdle  = status === 'idle'
  const isOk    = status === 'ok'
  const isError = status === 'error'

  const dotColor = isOk ? '#22c55e' : isError ? '#ef4444' : '#94a3b8'
  const label    = isOk ? `✓ ${httpStatus ?? 200}` : isError ? `✗ ${httpStatus ?? 'ERR'}` : '—'
  const detail   = isError ? (errorMessage ?? 'network error') : isIdle ? 'waiting…' : shortPath(lastUrl)

  return (
    <div
      style={WRAPPER_STYLE}
      title={`VITE_API_BASE_URL=${import.meta.env.VITE_API_BASE_URL}`}
    >
      <span style={{ ...DOT_STYLE, background: dotColor }} />
      <span style={{ color: '#e2e8f0', fontWeight: 700 }}>LIVE</span>
      <span style={{ opacity: 0.45 }}>·</span>
      <span style={{ color: isOk ? '#86efac' : isError ? '#fca5a5' : '#94a3b8', fontWeight: 600 }}>
        {label}
      </span>
      <span style={{ opacity: 0.45 }}>·</span>
      <span style={{ opacity: 0.7, maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis' }}>
        {detail}
      </span>
    </div>
  )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function shortPath(url: string): string {
  try   { return new URL(url).pathname }
  catch { return url }
}

// ── Styles ────────────────────────────────────────────────────────────────────

const WRAPPER_STYLE: CSSProperties = {
  position:        'fixed',
  bottom:          '16px',
  left:            '16px',
  zIndex:          9999,
  display:         'flex',
  alignItems:      'center',
  gap:             '6px',
  padding:         '6px 11px',
  borderRadius:    '8px',
  border:          '1px solid rgba(255,255,255,0.1)',
  backgroundColor: 'rgba(15, 23, 42, 0.85)',
  backdropFilter:  'blur(8px)',
  boxShadow:       '0 2px 10px rgba(0,0,0,0.4)',
  fontFamily:      'ui-monospace, SFMono-Regular, monospace',
  fontSize:        '11px',
  color:           '#cbd5e1',
  whiteSpace:      'nowrap',
  pointerEvents:   'none',
}

const DOT_STYLE: CSSProperties = {
  width:        '7px',
  height:       '7px',
  borderRadius: '50%',
  flexShrink:   0,
}
