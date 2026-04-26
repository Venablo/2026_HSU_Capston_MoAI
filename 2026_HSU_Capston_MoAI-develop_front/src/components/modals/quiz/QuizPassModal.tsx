import { useState } from 'react'
import { Zap, Loader2 } from 'lucide-react'
import Modal from '../common/Modal'
import { submitQuiz } from '../../../services/apiService'
import type { InstantQuizResponse } from '../../../types/api'

interface Props {
    quiz: InstantQuizResponse
    onClose: () => void
    /**
     * Called after the answer is submitted and graded by the backend.
     * @param correct         true if the selected answer was correct
     * @param conceptName     the keyword this question tests (quiz.relatedKeyword)
     * @param correctConcept  the text of the correct option (from SubmitQuizResponse)
     * @param explanation     AI-generated explanation (from SubmitQuizResponse)
     */
    onResult?: (correct: boolean, conceptName: string, correctConcept: string, explanation: string) => void
}

export default function QuizPassModal({ quiz, onClose, onResult }: Props) {
    const [selected,   setSelected]   = useState<string | null>(null)
    const [submitting, setSubmitting] = useState(false)

    const submit = async () => {
        if (!selected || submitting) return
        setSubmitting(true)
        try {
            const response = await submitQuiz({
                questionId: quiz.questionId,
                quizId:     quiz.quizId,
                selected,
            })
            // Look up the full text of the correct option for the result modal
            const correctText = quiz.options.find(o => o.label === response.correctAnswer)?.text
                ?? response.correctAnswer

            if (onResult) {
                onResult(response.isCorrect, quiz.relatedKeyword, correctText, response.aiExplanation)
            } else {
                onClose()
            }
        } catch {
            onClose()
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <Modal onClose={onClose}>
            <div className="modal-quiz__badge" style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
                <Zap size={14} strokeWidth={2} />
                1분 패스 퀴즈
            </div>
            <h3 className="modal-quiz__question">{quiz.question}</h3>
            <div className="modal-quiz__options">
                {quiz.options.map((opt, i) => (
                    <button
                        key={opt.label}
                        className={`modal-quiz__option ${
                            opt.label === selected
                                ? 'modal-quiz__option--selected'
                                : 'modal-quiz__option--default'
                        }`}
                        onClick={() => setSelected(opt.label)}
                        disabled={submitting}
                    >
                        <span className="modal-quiz__option-num">
                            {(['①', '②', '③', '④'] as const)[i] ?? opt.label}
                        </span>
                        {opt.text}
                    </button>
                ))}
            </div>

            <button
                className={`modal-quiz__submit ${selected !== null ? 'modal-quiz__submit--active' : 'modal-quiz__submit--disabled'}`}
                onClick={submit}
                disabled={selected === null || submitting}
            >
                {submitting ? (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                        <Loader2 size={14} strokeWidth={2} className="animate-spin" />
                        채점 중...
                    </span>
                ) : '정답 확인'}
            </button>
        </Modal>
    )
}
