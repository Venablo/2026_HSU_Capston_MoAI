import { UserPlus, CheckCircle, ArrowLeftRight } from 'lucide-react'
import Modal from '../common/Modal'
import { useAuth } from '../../../context/AuthContext'
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
    const { nickname } = useAuth()
    const myName   = nickname ?? '나'
    const myRole   = match.partnerRole === 'mentor' ? '멘티' : '멘토'
    const myAvatar = myName.charAt(0).toUpperCase()

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

                {/* 1:1 Mentor/Mentee 카드 */}
                <div className="modal-matching__pair">
                    {/* 나 */}
                    <div className="modal-matching__pair-card modal-matching__pair-card--me">
                        <div className="modal-matching__pair-avatar modal-matching__pair-avatar--me">
                            {myAvatar}
                        </div>
                        <div className="modal-matching__pair-name">{myName}</div>
                        <div className={`modal-matching__pair-role ${myRole === '멘토' ? 'modal-matching__pair-role--mentor' : 'modal-matching__pair-role--mentee'}`}>
                            {myRole}
                        </div>
                    </div>

                    {/* 가운데 매칭률 + 화살표 */}
                    <div className="modal-matching__pair-center">
                        <div className="modal-matching__rate-ring">
                            <span className="modal-matching__rate-value">{match.matchRate}%</span>
                            <span className="modal-matching__rate-label">매칭률</span>
                        </div>
                        <ArrowLeftRight size={18} strokeWidth={1.5} style={{ color: 'var(--color-purple-400)', marginTop: '6px' }} />
                    </div>

                    {/* 파트너 */}
                    <div className="modal-matching__pair-card modal-matching__pair-card--partner">
                        <div className="modal-matching__pair-avatar modal-matching__pair-avatar--partner">
                            {match.partnerAvatar}
                        </div>
                        <div className="modal-matching__pair-name">{match.partnerName}</div>
                        <div className={`modal-matching__pair-role ${match.partnerRole === 'mentor' ? 'modal-matching__pair-role--mentor' : 'modal-matching__pair-role--mentee'}`}>
                            {match.partnerRole === 'mentor' ? '멘토' : '멘티'}
                        </div>
                    </div>
                </div>

                {/* 파트너 강점 태그 */}
                {match.partnerStrengths.length > 0 && (
                    <div style={{ marginBottom: '12px' }}>
                        <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--color-text-muted)', marginBottom: '6px', textAlign: 'center', letterSpacing: '0.5px' }}>
                            파트너 강점 키워드
                        </div>
                        <div className="modal-matching__strengths">
                            {match.partnerStrengths.map(s => (
                                <span key={s} className="modal-matching__strength-tag">{s}</span>
                            ))}
                        </div>
                    </div>
                )}

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
