import { AlertCircle, ArrowRight } from 'lucide-react'
import Modal from './Modal'

export interface QuizIncorrectModalProps {
    conceptName: string
    correctConcept: string
    explanation: string
    onClose: () => void
}

export default function QuizIncorrectModal({
    conceptName,
    correctConcept,
    explanation,
    onClose,
}: QuizIncorrectModalProps) {
    return (
        <Modal onClose={onClose}>
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

                {/* Concept explanation card */}
                <div className="modal-quiz-result__concept-box">
                    <div className="modal-quiz-result__concept-label">핵심 개념 정리</div>
                    <div className="modal-quiz-result__concept-name">{correctConcept}</div>
                    <p className="modal-quiz-result__concept-desc">{explanation}</p>
                </div>

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
            </div>
        </Modal>
    )
}
