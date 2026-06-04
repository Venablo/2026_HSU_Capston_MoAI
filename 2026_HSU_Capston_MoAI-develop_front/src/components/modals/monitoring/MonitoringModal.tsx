import { Bot, Loader2 } from 'lucide-react'
import Modal from '../common/Modal'

export interface MonitoringModalProps {
    conceptName: string
    reason: string
    onClose: () => void
    onShowSummary: () => void
    loading?: boolean
}

export default function MonitoringModal({
    conceptName,
    reason,
    onClose,
    onShowSummary,
    loading = false,
}: MonitoringModalProps) {
    return (
        <Modal onClose={onClose}>
            <div className="modal-monitoring">
                <div className="modal-icon-hero">
                    <div className="modal-icon-hero__circle modal-icon-hero__circle--purple">
                        <Bot size={36} strokeWidth={1.5} />
                    </div>
                </div>

                <div className="modal-monitoring__badge">AI 학습 패턴 감지</div>
                <h3 className="modal-monitoring__title">
                    혹시 방금 지나간{' '}
                    <span className="modal-monitoring__accent">'{conceptName}'</span>{' '}
                    개념에 대해<br />요약된 보충 자료가 필요하신가요?
                </h3>
                <div className="modal-monitoring__reason">
                    <span className="modal-monitoring__reason-dot" />
                    {reason}
                </div>
                <div className="modal-monitoring__btn-row">
                    <button
                        className="btn-ghost"
                        style={{ flex: 1, padding: '12px' }}
                        onClick={onClose}
                        disabled={loading}
                    >
                        아니요, 계속 시청할게요
                    </button>
                    <button
                        className="btn-primary"
                        style={{ flex: 1, padding: '12px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                        onClick={onShowSummary}
                        disabled={loading}
                    >
                        {loading ? (
                            <><Loader2 size={14} strokeWidth={2} className="animate-spin" /> 로딩 중...</>
                        ) : '네, 요약본 볼래요'}
                    </button>
                </div>
            </div>
        </Modal>
    )
}
