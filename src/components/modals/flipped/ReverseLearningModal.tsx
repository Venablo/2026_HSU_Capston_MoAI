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
 *
 * 백엔드 미연결 상태에서는 MOCK_MODE로 동작:
 *   - startFlippedSession 실패 시 로컬 mock firstMessage 사용
 *   - streamFlipped 대신 setTimeout으로 가짜 스트리밍 시뮬레이션
 *   - endFlippedSession 실패 시 mock 평가 결과 사용
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

// ── Mock 모드 설정 ────────────────────────────────────────────────────────────
// 백엔드가 준비되기 전 UI 테스트용 Mock 응답 데이터
const MOCK_FIRST_MESSAGE =
    "이번 주차에서 배운 'ACID'에 대해 설명해주세요! 트랜잭션 속성의 각 문자가 무엇을 의미하는지 알려주세요."

const MOCK_AI_RESPONSES = [
    '흥미롭네요! 그렇다면 원자성(Atomicity)이 보장되지 않으면 실제로 어떤 문제가 발생할 수 있을까요?',
    '완벽해요! 마지막으로, ACID 속성 중 격리성(Isolation)이 낮을 때 발생하는 문제(dirty read 등)를 설명해줄 수 있나요?',
]

const MOCK_EVAL: EndFlippedResponse = {
    sessionId:       'mock-session-id',
    flippedResult:   'pass',
    score:           88,
    gainedKeywords:  ['원자성', 'COMMIT', 'ROLLBACK', '트랜잭션'],
    weakKeywords:    ['격리성', '잠금(Lock)', '데드락'],
    feedback:        '이해도 88% — ACID 핵심 개념을 훌륭히 설명했어요! 격리성 부분을 조금 더 보완해 보세요.',
}

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
    /** 이전 mock 방식 호환용 (onSessionEnd가 없을 때 폴백으로 호출) */
    onSubmitExplanation?: (explanation: string) => void
    /** roomId가 있으면 실제 API를 호출하고, 없으면 Mock 모드로 동작 */
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
    const [sessionId,      setSessionId]      = useState<string | null>(null)
    const [initLoading,    setInitLoading]     = useState(true)   // startFlippedSession 대기 중
    const [endLoading,     setEndLoading]      = useState(false)  // endFlippedSession 대기 중

    // 채팅 UI 상태
    const [messages,    setMessages]    = useState<ChatMessage[]>([])
    const [inputText,   setInputText]   = useState('')
    const [isStreaming, setIsStreaming]  = useState(false)  // SSE 스트리밍 중 여부

    // 현재 스트리밍 중인 AI 메시지 버퍼
    // (setState 배치 처리로 인해 ref를 통해 최신 값을 보장)
    const streamBufferRef = useRef('')

    // SSE EventSource 인스턴스 — unmount 시 반드시 .close() 호출 필요
    const sseRef = useRef<EventSource | null>(null)

    // 채팅 영역 자동 스크롤을 위한 ref
    const chatBottomRef = useRef<HTMLDivElement | null>(null)

    // Mock 모드 여부: roomId가 없으면 Mock으로 동작
    const isMockMode = !roomId || !weekId

    // Mock 응답 인덱스 (Mock 모드에서 번갈아 응답 반환)
    const mockTurnRef = useRef(0)

    // ── 초기화: 세션 시작 ────────────────────────────────────────────────────
    useEffect(() => {
        let cancelled = false

        const initialize = async () => {
            setInitLoading(true)
            try {
                if (!isMockMode) {
                    // ── 실제 API 호출 ────────────────────────────────────────
                    // POST /flipped/start → sessionId와 AI 첫 메시지 수신
                    const { sessionId: sid, firstMessage } =
                        await startFlippedSession(roomId!, { curriculum_id: weekId! })

                    if (cancelled) return

                    setSessionId(sid)
                    // AI의 첫 번째 질문을 채팅 메시지로 추가
                    setMessages([{ role: 'ai', content: firstMessage }])
                } else {
                    // ── Mock 모드: 가짜 세션 시작 ────────────────────────────
                    setSessionId('mock-session')
                    setMessages([{ role: 'ai', content: MOCK_FIRST_MESSAGE }])
                }
            } catch {
                // API 실패 시 Mock 데이터로 폴백
                if (!cancelled) {
                    setSessionId('mock-session')
                    setMessages([{ role: 'ai', content: MOCK_FIRST_MESSAGE }])
                }
            } finally {
                if (!cancelled) setInitLoading(false)
            }
        }

        initialize()

        return () => { cancelled = true }
    }, [roomId, weekId, isMockMode])

    // ── 채팅 자동 스크롤 ─────────────────────────────────────────────────────
    useEffect(() => {
        chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }, [messages])

    // ── 메시지 전송 + SSE 스트리밍 처리 ─────────────────────────────────────
    const handleSend = useCallback(() => {
        const trimmed = inputText.trim()
        if (!trimmed || isStreaming || !sessionId) return

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

        if (!isMockMode && roomId && sessionId !== 'mock-session') {
            // ── 실제 SSE 스트리밍 ──────────────────────────────────────────
            // streamFlipped()는 accessToken을 쿼리 파라미터로 전달하는 EventSource를 반환
            const sse = streamFlipped(roomId, sessionId, trimmed)
            sseRef.current = sse

            let isCounterQuestion = false

            sse.onmessage = (e) => {
                try {
                    const event: FlippedStreamEvent = JSON.parse(e.data)

                    if (event.type === 'token') {
                        // 일반 토큰: 버퍼에 추가하고 마지막 AI 메시지를 업데이트
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
                        // 역질문 토큰: 강조 스타일로 표시
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
                        // 스트리밍 완료: EventSource 닫기, isStreaming 플래그 해제
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
                // SSE 연결 오류 시 연결 종료 및 UI 복구
                sse.close()
                sseRef.current = null
                setIsStreaming(false)
            }
        } else {
            // ── Mock 스트리밍: setTimeout으로 가짜 토큰 스트리밍 시뮬레이션 ─
            const mockResponse =
                MOCK_AI_RESPONSES[mockTurnRef.current % MOCK_AI_RESPONSES.length]
            mockTurnRef.current += 1
            const isLastMock = mockTurnRef.current >= 3

            // 1글자씩 50ms 간격으로 타이핑 효과 구현
            const chars = mockResponse.split('')
            let charIndex = 0

            const typeNextChar = () => {
                if (charIndex >= chars.length) {
                    // 마지막 글자 → 스트리밍 완료
                    setMessages(prev => {
                        const updated = [...prev]
                        updated[updated.length - 1] = {
                            role: 'ai',
                            content: mockResponse,
                            isCounterQuestion: true,
                            isStreaming: false,
                        }
                        return updated
                    })
                    setIsStreaming(false)
                    // mock 마지막 턴에서 "완료하기" 버튼 활성화 힌트 표시용 로직
                    if (isLastMock) {
                        setMessages(prev => [
                            ...prev,
                            {
                                role: 'ai',
                                content:
                                    '훌륭해요! 이제 "최종 평가받기" 버튼을 눌러 AI가 여러분의 이해도를 종합 평가하도록 해주세요.',
                                isStreaming: false,
                            },
                        ])
                    }
                    return
                }
                streamBufferRef.current += chars[charIndex]
                charIndex++
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
                setTimeout(typeNextChar, 30)
            }

            setTimeout(typeNextChar, 500) // 0.5초 딜레이 후 타이핑 시작
        }
    }, [inputText, isStreaming, sessionId, isMockMode, roomId])

    // ── 최종 평가 요청 (세션 종료) ────────────────────────────────────────────
    const handleFinalEvaluation = useCallback(async () => {
        if (!sessionId || endLoading) return
        setEndLoading(true)

        try {
            if (!isMockMode && roomId && sessionId !== 'mock-session') {
                // ── 실제 API: POST /flipped/end ──────────────────────────────
                const result = await endFlippedSession(roomId, { sessionId })
                if (onSessionEnd) {
                    onSessionEnd(result)
                } else if (onSubmitExplanation) {
                    // 폴백: 전체 대화를 텍스트로 직렬화하여 기존 방식으로 처리
                    const fullText = messages
                        .filter(m => m.role === 'user')
                        .map(m => m.content)
                        .join('\n')
                    onSubmitExplanation(fullText)
                }
            } else {
                // ── Mock 평가 결과 반환 ───────────────────────────────────────
                await new Promise(r => setTimeout(r, 1000)) // 1초 로딩 시뮬레이션
                if (onSessionEnd) {
                    onSessionEnd(MOCK_EVAL)
                } else if (onSubmitExplanation) {
                    onSubmitExplanation('mock evaluation complete')
                }
            }
        } catch {
            // API 실패 시 Mock 결과로 폴백
            await new Promise(r => setTimeout(r, 500))
            onSessionEnd?.(MOCK_EVAL)
        } finally {
            setEndLoading(false)
        }
    }, [sessionId, endLoading, isMockMode, roomId, messages, onSessionEnd, onSubmitExplanation])

    // ── Unmount cleanup ───────────────────────────────────────────────────────
    useEffect(() => {
        return () => {
            // 컴포넌트가 사라질 때 SSE 연결이 열려있으면 반드시 닫아야 메모리 누수 방지
            if (sseRef.current) {
                sseRef.current.close()
                sseRef.current = null
            }
        }
    }, [])

    // Enter 키로 메시지 전송 (Shift+Enter는 줄바꿈)
    const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault()
            handleSend()
        }
    }

    // "최종 평가" 버튼 활성화 조건:
    //   - 세션이 초기화됨
    //   - 현재 스트리밍 중이 아님
    //   - endFlippedSession 호출 중이 아님
    //   - 적어도 한 번 이상 대화가 있음 (사용자 메시지 존재)
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
                        // 세션 초기화 로딩 상태
                        <div className="modal-reverse__chat-loading">
                            <Loader2 size={20} strokeWidth={2} className="animate-spin" />
                            <span>AI 학생을 준비하고 있어요...</span>
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
                                    {/* 스트리밍 중 커서 효과 */}
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

                {/* 입력창 */}
                {!initLoading && (
                    <div className="modal-reverse__input-row">
                        <textarea
                            className="modal-reverse__textarea"
                            value={inputText}
                            onChange={e => setInputText(e.target.value)}
                            onKeyDown={handleKeyDown}
                            placeholder={`예: ${conceptName}은 트랜잭션이 지켜야 할 네 가지 속성입니다... (Shift+Enter: 줄바꿈)`}
                            rows={3}
                            disabled={isStreaming || isLoading}
                        />
                        <button
                            className={`modal-reverse__send-btn ${
                                inputText.trim() && !isStreaming
                                    ? 'modal-reverse__send-btn--active'
                                    : 'modal-reverse__send-btn--disabled'
                            }`}
                            onClick={handleSend}
                            disabled={!inputText.trim() || isStreaming || isLoading}
                            title="전송 (Enter)"
                        >
                            {isStreaming
                                ? <Loader2 size={16} strokeWidth={2} className="animate-spin" />
                                : <Send size={16} strokeWidth={2} />
                            }
                        </button>
                    </div>
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

                {/* 설명 완료 안내 */}
                {canFinish && (
                    <p className="modal-reverse__finish-hint">
                        <CheckCircle2 size={13} strokeWidth={2} style={{ display: 'inline', marginRight: '4px' }} />
                        대화가 준비됐어요! 평가를 받거나 계속 질문에 답할 수 있어요.
                    </p>
                )}
            </div>
        </Modal>
    )
}
