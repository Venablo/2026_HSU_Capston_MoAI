import { useState } from 'react'
import { Zap } from 'lucide-react'
import Modal from '../common/Modal'

const QUIZ_Q = {
    q: '트랜잭션의 ACID 속성 중 "원자성(Atomicity)"이 의미하는 것은?',
    opts: [
        '트랜잭션 내 모든 연산이 완전히 실행되거나 전혀 실행되지 않아야 한다',
        '트랜잭션 실행 전후 데이터베이스는 일관된 상태여야 한다',
        '동시에 실행되는 트랜잭션들이 서로 간섭하지 않아야 한다',
        '트랜잭션 완료 후 결과는 영구적으로 저장되어야 한다',
    ],
    answer: 0,
    conceptName: 'ACID',
    correctConcept: '원자성 (Atomicity)',
    explanation: '트랜잭션 내의 모든 연산은 완전히 실행되거나 전혀 실행되지 않아야 합니다. 일부만 반영되는 부분 커밋은 허용되지 않으며, 오류 발생 시 ROLLBACK을 통해 원래 상태로 완전히 복구됩니다.',
}

interface Props {
    onClose: () => void
    /** Called with true on correct, false on incorrect — opens result modal via ClassroomModals */
    onResult?: (correct: boolean, conceptName: string, correctConcept: string, explanation: string) => void
}

export default function QuizPassModal({ onClose, onResult }: Props) {
    const [selected, setSelected] = useState<number | null>(null)

    const submit = () => {
        if (selected === null) return
        const correct = selected === QUIZ_Q.answer
        if (onResult) {
            onResult(correct, QUIZ_Q.conceptName, QUIZ_Q.correctConcept, QUIZ_Q.explanation)
        } else {
            onClose()
        }
    }

    return (
        <Modal onClose={onClose}>
            <div className="modal-quiz__badge" style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
                <Zap size={14} strokeWidth={2} />
                1분 패스 퀴즈
            </div>
            <h3 className="modal-quiz__question">{QUIZ_Q.q}</h3>
            <div className="modal-quiz__options">
                {QUIZ_Q.opts.map((opt, i) => (
                    <button
                        key={i}
                        className={`modal-quiz__option ${
                            i === selected
                                ? 'modal-quiz__option--selected'
                                : 'modal-quiz__option--default'
                        }`}
                        onClick={() => setSelected(i)}
                    >
                        <span className="modal-quiz__option-num">{['①', '②', '③', '④'][i]}</span>
                        {opt}
                    </button>
                ))}
            </div>

            <button
                className={`modal-quiz__submit ${selected !== null ? 'modal-quiz__submit--active' : 'modal-quiz__submit--disabled'}`}
                onClick={submit}
                disabled={selected === null}
            >
                정답 확인
            </button>
        </Modal>
    )
}
