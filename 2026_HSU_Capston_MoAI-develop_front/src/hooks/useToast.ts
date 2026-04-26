/**
 * useToast  —  경량 토스트 알림 훅
 *
 * 로그인 / 회원가입 등 인증 흐름에서 성공·실패를 사용자에게 알리기 위한 훅.
 * DebugToast(개발 전용 이벤트 로그)와 독립적으로 동작한다.
 *
 * 사용 예시:
 *   const { showToast, toast } = useToast()
 *   showToast('success', '로그인 성공!')   // 3초 후 자동 사라짐
 *   showToast('error', '아이디 또는 비밀번호가 틀렸습니다.')
 *   <Toast toast={toast} />
 */

import { useState, useCallback, useRef } from 'react'

export type ToastType = 'success' | 'error' | 'info'

export interface ToastState {
    visible: boolean
    type: ToastType
    message: string
}

const HIDDEN: ToastState = { visible: false, type: 'info', message: '' }

export function useToast(durationMs = 3000) {
    const [toast, setToast] = useState<ToastState>(HIDDEN)
    const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

    /**
     * showToast  —  토스트 표시
     *
     * @param type    'success' | 'error' | 'info'
     * @param message 사용자에게 보여줄 메시지
     *
     * 이전 타이머가 있으면 교체 후 새 타이머 시작.
     * durationMs(기본 3000ms) 후 자동으로 사라짐.
     */
    const showToast = useCallback((type: ToastType, message: string) => {
        if (timerRef.current) clearTimeout(timerRef.current)
        setToast({ visible: true, type, message })
        timerRef.current = setTimeout(() => setToast(HIDDEN), durationMs)
    }, [durationMs])

    const hideToast = useCallback(() => {
        if (timerRef.current) clearTimeout(timerRef.current)
        setToast(HIDDEN)
    }, [])

    return { toast, showToast, hideToast }
}
