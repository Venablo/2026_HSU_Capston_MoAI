import { CheckCircle2, Trophy, AlertTriangle } from 'lucide-react'
import Modal from '../common/Modal'
import type { MetaEvaluationResponse } from '../../../types/aiEvents'

export interface MetaEvaluationModalProps {
    evaluation: MetaEvaluationResponse
    onClose: () => void
}

export default function MetaEvaluationModal({
    evaluation,
    onClose,
}: MetaEvaluationModalProps) {
    return (
        <Modal onClose={onClose} wide>
            <div className="modal-meta-eval">
                <div className="modal-icon-hero">
                    <div className="modal-icon-hero__circle modal-icon-hero__circle--green">
                        <CheckCircle2 size={36} strokeWidth={1.5} />
                    </div>
                </div>

                <div className="modal-meta-eval__badge" style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                    AI 메타인지 평가 완료
                </div>
                <h3 className="modal-meta-eval__title">학습 이해도 분석 결과</h3>

                {/* Score ring */}
                <div className="modal-meta-eval__score-wrap">
                    <div className="modal-meta-eval__score-ring">
                        <span className="modal-meta-eval__score">{evaluation.comprehensionScore}%</span>
                        <span className="modal-meta-eval__score-label">이해도</span>
                    </div>
                </div>

                {/* Strong keywords */}
                <div className="modal-meta-eval__section">
                    <div className="modal-meta-eval__section-label modal-meta-eval__section-label--strong" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <Trophy size={15} strokeWidth={1.5} />
                        잘 아는 키워드
                    </div>
                    <div className="modal-meta-eval__tags">
                        {evaluation.strongKeywords.map(kw => (
                            <span key={kw} className="modal-meta-eval__tag modal-meta-eval__tag--strong">
                                {kw}
                            </span>
                        ))}
                    </div>
                </div>

                {/* Weak keywords */}
                <div className="modal-meta-eval__section">
                    <div className="modal-meta-eval__section-label modal-meta-eval__section-label--weak" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <AlertTriangle size={15} strokeWidth={1.5} />
                        보완이 필요한 키워드
                    </div>
                    <div className="modal-meta-eval__tags">
                        {evaluation.weakKeywords.map(kw => (
                            <span key={kw} className="modal-meta-eval__tag modal-meta-eval__tag--weak">
                                {kw}
                            </span>
                        ))}
                    </div>
                </div>

                {/* 완료 텍스트 — 버튼 위 12px 간격 */}
                <p style={{
                    textAlign: 'center',
                    marginBottom: '12px',
                    fontSize: '12px',
                    fontWeight: 700,
                    color: '#059669',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '5px',
                }}>
                    <CheckCircle2 size={13} strokeWidth={2} />
                    메타인지 평가가 완료되었습니다. 파이널 퀴즈에 도전해보세요!
                </p>

                <div className="modal-meta-eval__btn-row">
                    <button
                        className="btn-ghost"
                        style={{ flex: 1, padding: '12px' }}
                        onClick={onClose}
                    >
                        닫기
                    </button>
                </div>
            </div>
        </Modal>
    )
}
