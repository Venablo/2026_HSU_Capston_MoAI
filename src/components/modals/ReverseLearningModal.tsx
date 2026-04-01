import { useState } from 'react'
import { Brain, Bot, Target, Loader2, ArrowRight, CheckCircle2 } from 'lucide-react'
import Modal from './Modal'

const MAX_TURNS = 4

const getAIQuestion = (conceptName: string, turn: number): string => {
    const questions = [
        `'${conceptName}'이 무엇인지, 왜 중요한지 쉽게 설명해 주실 수 있나요?`,
        `좋아요! 그렇다면 '${conceptName}'가 없다면 실제로 어떤 문제가 발생할까요?`,
        `훌륭해요! 현실 시스템에서 '${conceptName}'가 어떻게 적용되는지 예시를 들어볼까요?`,
        `완벽해요! 마지막으로, '${conceptName}'의 한계점이나 주의해야 할 점이 있다면 무엇인가요?`,
    ]
    return questions[Math.min(turn - 1, questions.length - 1)]
}

export interface ReverseLearningModalProps {
    conceptName: string
    onClose: () => void
    onSubmitExplanation: (explanation: string) => void
    loading?: boolean
}

export default function ReverseLearningModal({
    conceptName,
    onClose,
    onSubmitExplanation,
    loading = false,
}: ReverseLearningModalProps) {
    const [currentTurn, setCurrentTurn]   = useState(1)
    const [history, setHistory]           = useState<{ q: string; a: string }[]>([])
    const [currentAnswer, setCurrentAnswer] = useState('')

    const isAllTurnsDone = history.length >= MAX_TURNS
    const isLastTurn     = currentTurn === MAX_TURNS
    const canSubmitTurn  = currentAnswer.trim().length > 0 && !loading

    const handleNextTurn = () => {
        const q = getAIQuestion(conceptName, currentTurn)
        setHistory(prev => [...prev, { q, a: currentAnswer }])
        setCurrentAnswer('')
        setCurrentTurn(t => t + 1)
    }

    const handleFinalEvaluation = () => {
        const fullExplanation = history
            .map((h, i) => `Q${i + 1}: ${h.q}\nA: ${h.a}`)
            .join('\n\n')
        onSubmitExplanation(fullExplanation)
    }

    return (
        <Modal onClose={onClose} wide>
            <div className="modal-reverse">
                {/* Top-center icon hero */}
                <div className="modal-icon-hero">
                    <div className="modal-icon-hero__circle modal-icon-hero__circle--purple">
                        <Brain size={36} strokeWidth={1.5} />
                    </div>
                </div>

                {/* Turn progress */}
                <div className="modal-reverse__turn-header">
                    <span className="modal-reverse__turn-label">
                        {isAllTurnsDone ? 'All steps complete!' : `Step ${currentTurn} of ${MAX_TURNS}`}
                    </span>
                    <div className="modal-reverse__turn-dots">
                        {Array.from({ length: MAX_TURNS }).map((_, i) => (
                            <div
                                key={i}
                                className={`modal-reverse__turn-dot ${
                                    i < history.length
                                        ? 'modal-reverse__turn-dot--done'
                                        : i === currentTurn - 1 && !isAllTurnsDone
                                            ? 'modal-reverse__turn-dot--active'
                                            : ''
                                }`}
                            />
                        ))}
                    </div>
                </div>

                <div className="modal-reverse__badge">역방향 학습 (메타인지)</div>
                <h3 className="modal-reverse__title">AI에게 설명해주세요!</h3>
                <p className="modal-reverse__desc">
                    AI가 학생이 되어 {MAX_TURNS}가지 질문을 합니다. 방금 배운 내용을 자유롭게 설명해 주세요.
                </p>

                {/* Conversation history */}
                {history.length > 0 && (
                    <div className="modal-reverse__history">
                        {history.map((turn, i) => (
                            <div key={i} className="modal-reverse__history-turn">
                                <div className="modal-reverse__history-q">Q{i + 1}. {turn.q}</div>
                                <div className="modal-reverse__history-a">{turn.a}</div>
                            </div>
                        ))}
                    </div>
                )}

                {!isAllTurnsDone ? (
                    <>
                        {/* Current AI question bubble */}
                        <div className="modal-reverse__ai-bubble">
                            <div className="modal-reverse__ai-header">
                                <span className="modal-reverse__ai-avatar">
                                    <Bot size={15} strokeWidth={2} style={{ color: '#ffffff' }} />
                                </span>
                                <span className="modal-reverse__ai-name">AI 학생</span>
                            </div>
                            <p className="modal-reverse__ai-text">
                                {getAIQuestion(conceptName, currentTurn)}
                            </p>
                        </div>

                        <textarea
                            className="modal-reverse__textarea"
                            value={currentAnswer}
                            onChange={e => setCurrentAnswer(e.target.value)}
                            placeholder={`예: ${conceptName}은 데이터베이스 트랜잭션이 지켜야 할 네 가지 속성입니다...`}
                            rows={4}
                            disabled={loading}
                        />

                        <div className="modal-reverse__btn-row">
                            <button
                                className="btn-ghost"
                                style={{ flex: 1, padding: '12px' }}
                                onClick={onClose}
                                disabled={loading}
                            >
                                취소
                            </button>
                            <button
                                className={`modal-reverse__eval-btn ${canSubmitTurn ? 'modal-reverse__eval-btn--active' : 'modal-reverse__eval-btn--disabled'}`}
                                style={{ flex: 2, padding: '12px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                                disabled={!canSubmitTurn}
                                onClick={handleNextTurn}
                            >
                                {isLastTurn
                                    ? <><CheckCircle2 size={14} strokeWidth={2} /> 마지막 설명 제출</>
                                    : <>다음 질문으로 <ArrowRight size={14} strokeWidth={2} /></>
                                }
                            </button>
                        </div>
                    </>
                ) : (
                    /* All turns done — show final evaluation CTA */
                    <div className="modal-reverse__final-section">
                        <p className="modal-reverse__final-desc">
                            총 {MAX_TURNS}번의 설명이 완료되었습니다.<br />
                            AI가 종합 이해도를 평가합니다.
                        </p>
                        <button
                            className="modal-reverse__final-btn"
                            onClick={handleFinalEvaluation}
                            disabled={loading}
                        >
                            {loading ? (
                                <><Loader2 size={14} strokeWidth={2} className="animate-spin" /> AI 평가 중...</>
                            ) : (
                                <><Target size={14} strokeWidth={1.5} /> 최종 평가 받기</>
                            )}
                        </button>
                    </div>
                )}
            </div>
        </Modal>
    )
}
