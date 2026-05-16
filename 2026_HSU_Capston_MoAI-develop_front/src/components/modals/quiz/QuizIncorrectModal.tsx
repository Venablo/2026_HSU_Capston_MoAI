import { AlertCircle, ArrowRight, RotateCcw } from 'lucide-react'
import Modal from '../common/Modal'

export interface QuizIncorrectModalProps {
    conceptName: string
    correctConcept: string
    explanation: string
    onClose: () => void
    /** 오답 시 되돌아갈 영상 초 위치. 제공되면 되감기 버튼만 노출되고 X·오버레이 닫기가 비활성화된다. */
    rewindToSec?: number | null
    onRewind?: (sec: number) => void
}

export default function QuizIncorrectModal({
    conceptName,
    correctConcept,
    explanation,
    onClose,
    rewindToSec,
    onRewind,
}: QuizIncorrectModalProps) {
    const hasRewind = typeof rewindToSec === 'number' && rewindToSec >= 0 && !!onRewind

    const formatSec = (sec: number) => {
        const m = Math.floor(sec / 60)
        const s = sec % 60
        return m > 0 ? `${m}분 ${s}초` : `${s}초`
    }

    const handleRewind = () => {
        onClose()
        if (hasRewind) onRewind!(rewindToSec as number)
    }

    return (
        <Modal onClose={onClose} disableClose={hasRewind}>
            <div className="modal-quiz-result">
                <div className="modal-icon-hero">
                    <div className="modal-icon-hero__circle modal-icon-hero__circle--red">
                        <AlertCircle size={36} strokeWidth={1.5} />
                    </div>
                </div>

                <h3 className="modal-quiz-result__title">아쉽네요!</h3>
                <p className="modal-quiz-result__subtitle">
                    핵심 개념을 살짝 놓치셨어요.<br />
                    아래 내용을 확인하고 <strong>'{conceptName}'</strong>을 다시 복습해 보세요.
                </p>

                <div className="modal-quiz-result__concept-box">
                    <div className="modal-quiz-result__concept-label">핵심 개념 정리</div>
                    <div className="modal-quiz-result__concept-name">{correctConcept}</div>
                    <p className="modal-quiz-result__concept-desc">{explanation}</p>
                </div>

                {hasRewind ? (
                    <button
                        className="btn-primary"
                        style={{
                            width: '100%',
                            padding: '14px',
                            fontSize: '15px',
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '8px',
                        }}
                        onClick={handleRewind}
                    >
                        <RotateCcw size={16} strokeWidth={2} />
                        {formatSec(rewindToSec as number)} 구간으로 돌아가기
                    </button>
                ) : (
                    <button
                        className="btn-ghost"
                        style={{
                            width: '100%',
                            padding: '14px',
                            fontSize: '15px',
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '8px',
                        }}
                        onClick={onClose}
                    >
                        계속 시청하기
                        <ArrowRight size={16} strokeWidth={2} />
                    </button>
                )}
            </div>
        </Modal>
    )
}
