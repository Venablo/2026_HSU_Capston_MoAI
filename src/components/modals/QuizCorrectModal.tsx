import { Trophy, ArrowRight } from 'lucide-react'
import Modal from './Modal'

export interface QuizCorrectModalProps {
    conceptName: string
    onClose: () => void
}

export default function QuizCorrectModal({ conceptName, onClose }: QuizCorrectModalProps) {
    return (
        <Modal onClose={onClose}>
            <div className="modal-quiz-result">
                <div className="modal-icon-hero">
                    <div className="modal-icon-hero__circle modal-icon-hero__circle--green">
                        <Trophy size={36} strokeWidth={1.5} />
                    </div>
                </div>

                <h3 className="modal-quiz-result__title">정답입니다!</h3>
                <p className="modal-quiz-result__subtitle">
                    완벽하게 이해하셨네요!<br />
                    <strong>'{conceptName}'</strong> 구간을 성공적으로 마스터했습니다.
                </p>

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
                    onClick={onClose}
                >
                    계속 시청하기
                    <ArrowRight size={16} strokeWidth={2} />
                </button>
            </div>
        </Modal>
    )
}
