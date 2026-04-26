/**
 * Toast  —  인증 흐름용 토스트 알림 컴포넌트
 *
 * 화면 상단 중앙에 고정(fixed) 렌더링됨.
 * visible === false 이면 아무것도 렌더하지 않는다.
 *
 * 사용:
 *   const { toast, showToast } = useToast()
 *   <Toast toast={toast} />
 */

import type { ToastState } from '../hooks/useToast'
import { CheckCircle2, XCircle, Info } from 'lucide-react'

interface Props {
    toast: ToastState
}

// 타입별 시각 설정
const CONFIG = {
    success: {
        bg:     '#f0fdf4',
        border: '#86efac',
        text:   '#166534',
        icon:   <CheckCircle2 size={18} color="#16a34a" strokeWidth={2} />,
    },
    error: {
        bg:     '#fef2f2',
        border: '#fca5a5',
        text:   '#991b1b',
        icon:   <XCircle size={18} color="#dc2626" strokeWidth={2} />,
    },
    info: {
        bg:     '#eff6ff',
        border: '#93c5fd',
        text:   '#1e40af',
        icon:   <Info size={18} color="#2563eb" strokeWidth={2} />,
    },
} as const satisfies Record<ToastState['type'], {
    bg: string; border: string; text: string; icon: React.ReactNode
}>

import React from 'react'

export default function Toast({ toast }: Props) {
    if (!toast.visible) return null

    const cfg = CONFIG[toast.type]

    return (
        <div
            role="alert"
            aria-live="polite"
            className="toast-container"
            style={{
                position:        'fixed',
                top:             '20px',
                left:            '50%',
                transform:       'translateX(-50%)',
                zIndex:          9999,
                display:         'flex',
                alignItems:      'center',
                gap:             '10px',
                padding:         '12px 20px',
                borderRadius:    '12px',
                border:          `1.5px solid ${cfg.border}`,
                backgroundColor: cfg.bg,
                color:           cfg.text,
                fontSize:        '14px',
                fontWeight:      600,
                boxShadow:       '0 4px 20px rgba(0,0,0,0.1)',
                animation:       'toastIn 0.25s ease',
                whiteSpace:      'nowrap',
                maxWidth:        '420px',
                pointerEvents:   'none',
            }}
        >
            {cfg.icon}
            <span style={{ flex: 1, whiteSpace: 'normal' }}>{toast.message}</span>
        </div>
    )
}
