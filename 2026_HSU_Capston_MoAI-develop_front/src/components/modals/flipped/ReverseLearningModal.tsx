/**
 * ============================================================================
 * ReverseLearningModal.tsx  —  거꾸로 학습(Flipped Learning) 모달
 * ============================================================================
 *
 * 전체 SSE 스트리밍 흐름:
 *
 *  [모달 오픈]
 *      │
 *      ▼
 *  ① POST /flipped/start
 *      → sessionId 발급
 *      → firstMessage 수신 ("이번 주차 키워드에 대해 설명해주세요!")
 *      │
 *      ▼
 *  ② 사용자가 설명을 textarea에 입력 후 "전송" 클릭
 *      │
 *      ▼
 *  ③ SSE EventSource 연결: GET /flipped/stream?sessionId=X&message=Y&token=Z
 *      → type: 'token'            → aiBuffer에 토큰 append → 실시간 채팅 버블 업데이트
 *      → type: 'counter_question' → 역질문으로 강조 표시
 *      → type: 'done'             → EventSource.close(), 다음 입력 활성화
 *      │
 *  ④ "설명 완료하고 최종 평가받기" 클릭
 *      │
 *      ▼
 *  ⑤ POST /flipped/end
 *      → score, gainedKeywords, weakKeywords, feedback 수신
 *      → onSessionEnd 콜백 호출 → MetaEvaluationModal로 전환
 * ============================================================================
 */

import { useState, useEffect, useRef, useCallback } from 'react'
import { Brain, Bot, Target, Loader2, Send, CheckCircle2 } from 'lucide-react'
import Modal from '../common/Modal'
import {
    startFlippedSession,
    streamFlipped,
    endFlippedSession,
} from '../../../services/apiService'
import type { EndFlippedResponse, FlippedStreamEvent } from '../../../types/api'

// ── 채팅 메시지 타입 ─────────────────────────────────────────────────────────
interface ChatMessage {
    /** 'ai' = AI 메시지, 'user' = 사용자 메시지 */
    role: 'ai' | 'user'
    content: string
    /** true면 AI 역질문 스타일로 강조 표시 */
    isCounterQuestion?: boolean
    /** true면 SSE 스트리밍 중인 메시지 (마지막 AI 메시지에만 적용) */
    isStreaming?: boolean
}

// ── Props 정의 ────────────────────────────────────────────────────────────────
export interface ReverseLearningModalProps {
    conceptName: string
    onClose:     () => void
    /**
     * SSE 거꾸로 학습 세션이 완전히 끝난 후 호출.
     * EndFlippedResponse를 MetaEvaluationResponse로 변환하여 다음 모달로 전환하는 데 사용.
     */
    onSessionEnd?: (result: EndFlippedResponse) => void
    /** 이전 방식 호환용 (onSessionEnd가 없을 때 폴백으로 호출) */
    onSubmitExplanation?: (explanation: string) => void
    /** 학습실 ID — 실제 API 호출에 필요 */
    roomId?: string
    /** 현재 주차 ID (startFlippedSession의 curriculum_id로 사용) */
    weekId?: string
    loading?: boolean
}

// ── 컴포넌트 ─────────────────────────────────────────────────────────────────
export default function ReverseLearningModal({
    conceptName,
    onClose,
    onSessionEnd,
    onSubmitExplanation,
    roomId,
    weekId,
    loading: externalLoading = false,
}: ReverseLearningModalProps) {
    // 세션 초기화 관련 상태
    const [sessionId,   setSessionId]   = useState<string | null>(null)
    const [initLoading, setInitLoading] = useState(true)
    const [endLoading,  setEndLoading]  = useState(false)
    const [initError,   setInitError]   = useState<string | null>(null)
    const [evalError,   setEvalError]   = useState<string | null>(null)

    // 채팅 UI 상태
    const [messages,    setMessages]    = useState<ChatMessage[]>([])
    const [inputText,   setInputText]   = useState('')
    const [isStreaming, setIsStreaming]  = useState(false)

    // 현재 스트리밍 중인 AI 메시지 버퍼
    const streamBufferRef = useRef('')

    // SSE EventSource 인스턴스 — unmount 시 반드시 .close() 호출 필요
    const sseRef = useRef<EventSource | null>(null)

    // 채팅 영역 자동 스크롤을 위한 ref
    const chatBottomRef = useRef<HTMLDivElement | null>(null)

    // ── 초기화: 세션 시작 ────────────────────────────────────────────────────
    useEffect(() => {
        let cancelled = false

        const initialize = async () => {
            setInitLoading(true)
            setInitError(null)
            try {
                const { sessionId: sid, firstMessage } =
                    await startFlippedSession(roomId!, { curriculum_id: weekId! })
                if (cancelled) return
                setSessionId(sid)
                setMessages([{ role: 'ai', content: firstMessage }])
            } catch (e) {
                if (!cancelled) {
                    setInitError(e instanceof Error ? e.message : '세션 시작에 실패했습니다.')
                }
            } finally {
                if (!cancelled) setInitLoading(false)
            }
        }

        initialize()
        return () => { cancelled = true }
    }, [roomId, weekId])

    // ── 채팅 자동 스크롤 ─────────────────────────────────────────────────────
    useEffect(() => {
        chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }, [messages])

    // ── 메시지 전송 + SSE 스트리밍 처리 ─────────────────────────────────────
    const handleSend = useCallback(() => {
        const trimmed = inputText.trim()
        if (!trimmed || isStreaming || !sessionId || !roomId) return

        // 사용자 메시지를 채팅 UI에 추가
        setMessages(prev => [...prev, { role: 'user', content: trimmed }])
        setInputText('')
        setIsStreaming(true)
        streamBufferRef.current = ''

        // AI 스트리밍 메시지 자리 확보 (빈 메시지로 추가, 이후 내용이 채워짐)
        setMessages(prev => [
            ...prev,
            { role: 'ai', content: '', isStreaming: true },
        ])

        // streamFlipped()는 accessToken을 쿼리 파라미터로 전달하는 EventSource를 반환
        const sse = streamFlipped(roomId, sessionId, trimmed)
        sseRef.current = sse

        let isCounterQuestion = false

        sse.onmessage = (e) => {
            try {
                const event: FlippedStreamEvent = JSON.parse(e.data)

                if (event.type === 'token') {
                    streamBufferRef.current += event.content
                    const currentContent = streamBufferRef.current
                    setMessages(prev => {
                        const updated = [...prev]
                        updated[updated.length - 1] = {
                            role: 'ai',
                            content: currentContent,
                            isStreaming: true,
                        }
                        return updated
                    })
                } else if (event.type === 'counter_question') {
                    isCounterQuestion = true
                    streamBufferRef.current += event.content
                    const currentContent = streamBufferRef.current
                    setMessages(prev => {
                        const updated = [...prev]
                        updated[updated.length - 1] = {
                            role: 'ai',
                            content: currentContent,
                            isCounterQuestion: true,
                            isStreaming: true,
                        }
                        return updated
                    })
                } else if (event.type === 'done') {
                    sse.close()
                    sseRef.current = null
                    const finalContent = streamBufferRef.current
                    setMessages(prev => {
                        const updated = [...prev]
                        updated[updated.length - 1] = {
                            role: 'ai',
                            content: finalContent,
                            isCounterQuestion,
                            isStreaming: false,
                        }
                        return updated
                    })
                    setIsStreaming(false)
                }
            } catch {
                // JSON 파싱 실패 시 무시 (불완전한 SSE 패킷)
            }
        }

        sse.onerror = () => {
            sse.close()
            sseRef.current = null
            setIsStreaming(false)
        }
    }, [inputText, isStreaming, sessionId, roomId])

    // ── 최종 평가 요청 (세션 종료) ────────────────────────────────────────────
    const handleFinalEvaluation = useCallback(async () => {
        if (!sessionId || endLoading || !roomId) return
        setEndLoading(true)
        setEvalError(null)

        try {
            const result = await endFlippedSession(roomId, { sessionId })
            if (onSessionEnd) {
                onSessionEnd(result)
            } else if (onSubmitExplanation) {
                const fullText = messages
                    .filter(m => m.role === 'user')
                    .map(m => m.content)
                    .join('\n')
                onSubmitExplanation(fullText)
            }
        } catch (e) {
            setEvalError(e instanceof Error ? e.message : '평가 요청에 실패했습니다.')
        } finally {
            setEndLoading(false)
        }
    }, [sessionId, endLoading, roomId, messages, onSessionEnd, onSubmitExplanation])

    // ── Unmount cleanup ───────────────────────────────────────────────────────
    useEffect(() => {
        return () => {
            if (sseRef.current) {
                sseRef.current.close()
                sseRef.current = null
            }
        }
    }, [])

    // Enter 키로 메시지 전송 (Shift+Enter는 줄바꿈, IME 조합 중에는 전송 안 함)
    const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault()
            handleSend()
        }
    }

    const canFinish =
        !!sessionId &&
        !isStreaming &&
        !endLoading &&
        !externalLoading &&
        messages.some(m => m.role === 'user')

    const isLoading = initLoading || endLoading || externalLoading

    return (
        <Modal onClose={onClose} wide>
            <div className="modal-reverse">
                {/* 상단 아이콘 */}
                <div className="modal-icon-hero">
                    <div className="modal-icon-hero__circle modal-icon-hero__circle--purple">
                        <Brain size={36} strokeWidth={1.5} />
                    </div>
                </div>

                <div className="modal-reverse__badge">역방향 학습 (메타인지)</div>
                <h3 className="modal-reverse__title">AI에게 설명해주세요!</h3>
                <p className="modal-reverse__desc">
                    AI가 학생이 되어 역질문합니다. 방금 배운 내용을 자유롭게 설명해 주세요.
                </p>

                {/* 채팅 영역 */}
                <div className="modal-reverse__chat">
                    {initLoading ? (
                        <div className="modal-reverse__chat-loading">
                            <Loader2 size={20} strokeWidth={2} className="animate-spin" />
                            <span>AI 학생을 준비하고 있어요...</span>
                        </div>
                    ) : initError ? (
                        <div className="modal-reverse__chat-loading">
                            <span style={{ color: '#ef4444' }}>⚠ {initError}</span>
                        </div>
                    ) : (
                        messages.map((msg, i) => (
                            <div
                                key={i}
                                className={`modal-reverse__msg ${
                                    msg.role === 'ai'
                                        ? msg.isCounterQuestion
                                            ? 'modal-reverse__msg--ai-question'
                                            : 'modal-reverse__msg--ai'
                                        : 'modal-reverse__msg--user'
                                }`}
                            >
                                {msg.role === 'ai' && (
                                    <div className="modal-reverse__msg-avatar">
                                        <Bot size={14} strokeWidth={2} />
                                    </div>
                                )}
                                <div className="modal-reverse__msg-bubble">
                                    {msg.isCounterQuestion && (
                                        <div className="modal-reverse__msg-label">
                                            💡 AI 역질문
                                        </div>
                                    )}
                                    <p>{msg.content}</p>
                                    {msg.isStreaming && (
                                        <span className="modal-reverse__cursor">▊</span>
                                    )}
                                </div>
                            </div>
                        ))
                    )}
                    {/* 자동 스크롤 앵커 */}
                    <div ref={chatBottomRef} />
                </div>

                {/* 입력창 — Slack/Notion 스타일 통합 컨테이너 */}
                {!initLoading && !initError && (
                    <>
                        <div className="modal-reverse__input-row">
                            <textarea
                                className="modal-reverse__textarea"
                                value={inputText}
                                onChange={e => setInputText(e.target.value)}
                                onKeyDown={handleKeyDown}
                                placeholder={`예: ${conceptName}은 트랜잭션이 지켜야 할 네 가지 속성입니다...`}
                                rows={3}
                                disabled={isStreaming || isLoading}
                            />
                            <button
                                className={`modal-reverse__send-btn${inputText.trim() && !isStreaming ? ' modal-reverse__send-btn--active' : ''}`}
                                onClick={handleSend}
                                disabled={!inputText.trim() || isStreaming || isLoading}
                                title="전송 (Enter)"
                            >
                                {isStreaming
                                    ? <Loader2 size={14} strokeWidth={2} className="animate-spin" />
                                    : <Send size={14} strokeWidth={2} />
                                }
                                전송
                            </button>
                        </div>
                        <p className="modal-reverse__input-hint">
                            * Enter 키를 누르면 전송되며, Shift+Enter로 줄바꿈할 수 있습니다.
                        </p>
                    </>
                )}

                {/* 평가 오류 메시지 */}
                {evalError && (
                    <p style={{ color: '#ef4444', fontSize: '12px', textAlign: 'center', margin: '4px 0' }}>
                        ⚠ {evalError}
                    </p>
                )}

                {/* 설명 완료 안내 — 버튼 위에 배치 */}
                {canFinish && (
                    <p className="modal-reverse__finish-hint">
                        <CheckCircle2 size={13} strokeWidth={2} style={{ display: 'inline', marginRight: '5px', verticalAlign: 'middle' }} />
                        메타인지 평가가 완료되었습니다! 이제 우측의 [주간 최종 퀴즈]에 도전하여 학습을 마무리해보세요.
                    </p>
                )}

                {/* 하단 버튼 */}
                <div className="modal-reverse__btn-row">
                    <button
                        className="btn-ghost"
                        style={{ flex: 1, padding: '12px' }}
                        onClick={onClose}
                        disabled={isLoading}
                    >
                        취소
                    </button>
                    <button
                        className={`modal-reverse__eval-btn ${
                            canFinish
                                ? 'modal-reverse__eval-btn--active'
                                : 'modal-reverse__eval-btn--disabled'
                        }`}
                        style={{
                            flex: 2,
                            padding: '12px',
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '6px',
                        }}
                        onClick={handleFinalEvaluation}
                        disabled={!canFinish}
                    >
                        {endLoading ? (
                            <><Loader2 size={14} strokeWidth={2} className="animate-spin" /> AI 평가 중...</>
                        ) : (
                            <><Target size={14} strokeWidth={1.5} /> 설명 완료하고 최종 평가받기</>
                        )}
                    </button>
                </div>
            </div>
        </Modal>
    )
}
