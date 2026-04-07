import { UserPlus, CheckCircle } from 'lucide-react'
import Modal from '../common/Modal'
import type { StudyMatchResponse } from '../../../types/aiEvents'

export interface StudyMatchingModalProps {
    match: StudyMatchResponse
    onClose: () => void
    onConnect: () => void
}

export default function StudyMatchingModal({
    match,
    onClose,
    onConnect,
}: StudyMatchingModalProps) {
    return (
        <Modal onClose={onClose} wide>
            <div className="modal-matching">
                <div className="modal-icon-hero">
                    <div className="modal-icon-hero__circle modal-icon-hero__circle--teal">
                        <UserPlus size={36} strokeWidth={1.5} />
                    </div>
                </div>

                <div className="modal-matching__badge" style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                    AI 스터디 매칭
                </div>
                <h3 className="modal-matching__title">최적의 파트너를 찾았어요!</h3>

                {/* Match rate */}
                <div className="modal-matching__rate-wrap">
                    <div className="modal-matching__rate-ring">
                        <span className="modal-matching__rate-value">{match.matchRate}%</span>
                        <span className="modal-matching__rate-label">AI 매칭률</span>
                    </div>
                </div>

                {/* Partner profile card */}
                <div className="modal-matching__profile">
                    <div className="modal-matching__avatar">{match.partnerAvatar}</div>
                    <div className="modal-matching__info">
                        <div className="modal-matching__name">{match.partnerName}</div>
                        <div className="modal-matching__role-badge">
                            {match.partnerRole === 'mentor' ? '멘토' : '멘티'}
                        </div>
                        <div className="modal-matching__strengths">
                            {match.partnerStrengths.map(s => (
                                <span key={s} className="modal-matching__strength-tag">{s}</span>
                            ))}
                        </div>
                    </div>
                </div>

                <p className="modal-matching__desc">
                    AI가 여러분의 취약 키워드와 파트너의 강점을 분석하여 가장 잘 맞는 학습 파트너를 선택했어요.
                </p>

                <div className="modal-matching__btn-row">
                    <button
                        className="btn-ghost"
                        style={{ flex: 1, padding: '12px' }}
                        onClick={onClose}
                    >
                        다음에 하기
                    </button>
                    <button
                        className="btn-primary modal-matching__connect-btn"
                        style={{ flex: 2, padding: '12px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                        onClick={onConnect}
                    >
                        <CheckCircle size={14} strokeWidth={2} />
                        연결하기
                    </button>
                </div>
            </div>
        </Modal>
    )
}
