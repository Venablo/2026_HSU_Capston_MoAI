import { Rocket, Zap, Target } from 'lucide-react'
import Modal from '../common/Modal'

export interface FastTrackModalProps {
    conceptName: string
    reason: string
    completionRate: number
    challengeLevel: 'intermediate' | 'advanced'
    onClose: () => void
    onStartChallenge: () => void
}

const LEVEL_LABEL: Record<'intermediate' | 'advanced', string> = {
    intermediate: '심화',
    advanced:     '고급',
}

export default function FastTrackModal({
    conceptName,
    reason,
    completionRate,
    challengeLevel,
    onClose,
    onStartChallenge,
}: FastTrackModalProps) {
    return (
        <Modal onClose={onClose}>
            <div className="modal-fast-track">
                <div className="modal-icon-hero">
                    <div className="modal-icon-hero__circle modal-icon-hero__circle--amber">
                        <Rocket size={36} strokeWidth={1.5} />
                    </div>
                </div>

                <div className="modal-fast-track__badge">AI 빠른 학습 패턴 감지</div>

                <h3 className="modal-fast-track__title">
                    엄청난 속도네요! 혹시 이미<br />
                    <span className="modal-fast-track__accent">'완벽히 아는 내용'</span>인가요?
                </h3>

                <div className="modal-fast-track__stats">
                    <div className="modal-fast-track__stat modal-fast-track__stat--speed" style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                        <Zap size={13} strokeWidth={2} />
                        평균 대비 <strong>{completionRate}%</strong> 빠름
                    </div>
                    <div className="modal-fast-track__stat modal-fast-track__stat--level" style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                        <Target size={13} strokeWidth={2} />
                        {LEVEL_LABEL[challengeLevel]} 도전 가능
                    </div>
                </div>

                <div className="modal-fast-track__reason">
                    <span className="modal-fast-track__reason-dot" />
                    {reason}
                </div>

                <p className="modal-fast-track__desc">
                    <strong>'{conceptName}'</strong> 구간의 빠른 스킵이 감지되었습니다. 핵심만 짚는 [1분 패스 퀴즈]의 정답을 맞히면, 이 구간을 마스터한 것으로 인정하고 즉시 통과시켜 드릴게요!
                </p>

                <div className="modal-fast-track__btn-row">
                    <button
                        className="btn-ghost"
                        style={{ flex: 1, padding: '12px' }}
                        onClick={onClose}
                    >
                        아니요, 마저 시청할게요
                    </button>
                    <button
                        className="btn-primary modal-fast-track__challenge-btn"
                        style={{ flex: 1, padding: '12px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                        onClick={onStartChallenge}
                    >
                        <Rocket size={14} strokeWidth={2} />
                        1분 패스 퀴즈 도전
                    </button>
                </div>
            </div>
        </Modal>
    )
}
