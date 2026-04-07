import { useState } from 'react'
import { Brain, Bot, Mic, MicOff } from 'lucide-react'
import Modal from '../common/Modal'

interface Props {
    onClose: () => void
}

export default function FlippedModal({ onClose }: Props) {
    const [text, setText]           = useState('')
    const [recording, setRecording] = useState(false)

    const toggleRecording = () => setRecording(r => !r)

    return (
        <Modal onClose={onClose} wide>
            <div className="modal-flipped__badge" style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                <Brain size={16} strokeWidth={2} />
                AI 대상 거꾸로 학습
            </div>
            <h3 className="modal-flipped__title">AI에게 '트랜잭션'을 설명해보세요</h3>
            <p className="modal-flipped__desc">
                AI가 학생 역할을 합니다. 자유롭게 설명해 주시면 이해도를 분석해 드릴게요.
            </p>
            <div className="modal-flipped__ai-bubble">
                <div className="modal-flipped__ai-label" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <Bot size={16} strokeWidth={2} />
                    AI 학생:
                </div>
                <div className="modal-flipped__ai-text">
                    "선생님, 트랜잭션이 뭔가요? 그리고 ACID가 왜 중요한지 모르겠어요."
                </div>
            </div>
            <textarea
                className="modal-flipped__textarea"
                rows={4}
                value={text}
                onChange={e => setText(e.target.value)}
                placeholder="예: 트랜잭션은 데이터베이스의 작업 단위로, 여러 쿼리를 하나의 논리적 묶음으로 처리합니다..."
            />
            <div className="modal-flipped__btn-row">
                <button
                    className={`modal-flipped__voice-btn ${recording ? 'modal-flipped__voice-btn--recording' : ''}`}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                    onClick={toggleRecording}
                >
                    {recording ? <MicOff size={14} strokeWidth={2} /> : <Mic size={14} strokeWidth={2} />}
                    {recording ? '녹음 중... (클릭하여 중지)' : '음성으로 설명하기'}
                </button>
                <button
                    className={`modal-flipped__eval-btn ${text.length > 20 ? 'modal-flipped__eval-btn--active' : 'modal-flipped__eval-btn--disabled'}`}
                    onClick={() => text.length > 20 && onClose()}
                >
                    AI 평가 받기 →
                </button>
            </div>
        </Modal>
    )
}
