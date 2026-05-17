import { Sparkles, FileText } from 'lucide-react'
import MarkdownContent from '../../MarkdownContent'
import Modal from '../common/Modal'

/** A single principle item in the summary list (e.g. one ACID entry) */
export interface SummaryItem {
    /** Short letter label shown in the circular badge, e.g. "A" */
    letter: string
    /** Full principle name, e.g. "원자성 (Atomicity)" */
    title: string
    /** Explanation paragraph for this principle */
    description: string
}

export interface SummaryDetailModalProps {
    /** Concept name used in the modal heading, e.g. "ACID" */
    conceptName: string
    /** Array of principle items to render as a list */
    summaryItems: SummaryItem[]
    onClose: () => void
    /** Optional — called when the user requests a PDF download */
    onDownloadPDF?: () => void
}

export default function SummaryDetailModal({
    conceptName,
    summaryItems,
    onClose,
    onDownloadPDF,
}: SummaryDetailModalProps) {
    return (
        <Modal onClose={onClose} wide>
            <div className="modal-summary animate-slide-up" style={{ maxHeight: '80vh', display: 'flex', flexDirection: 'column' }}>
                <div className="modal-summary__badge" style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                    <Sparkles size={14} strokeWidth={2} />
                    AI 생성 요약본
                </div>
                <h3 className="modal-summary__title" style={{ marginBottom: '14px' }}>{conceptName} 핵심 요약</h3>

                <div className="modal-summary__list" style={{ flex: 1, maxHeight: '320px', overflowY: 'auto' }}>
                    {summaryItems.map((item, i) => (
                        <div
                            key={item.letter}
                            className={`modal-summary__item animate-fade-in delay-${(i + 1) * 100}`}
                        >
                            <div className="modal-summary__letter">{item.letter}</div>
                            <div className="modal-summary__content">
                                <div className="modal-summary__item-title">{item.title}</div>
                                <MarkdownContent
                                    content={item.description}
                                    compact
                                    className="modal-summary__item-desc"
                                />
                            </div>
                        </div>
                    ))}
                </div>

                <div className="modal-summary__btn-row">
                    <button
                        className="btn-ghost"
                        style={{ flex: 1, padding: '12px', fontSize: '14px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                        onClick={onDownloadPDF}
                    >
                        <FileText size={14} strokeWidth={2} />
                        PDF 다운로드
                    </button>
                    <button
                        className="btn-primary"
                        style={{ flex: 1, padding: '12px', fontSize: '14px' }}
                        onClick={onClose}
                    >
                        확인 및 닫기
                    </button>
                </div>
            </div>
        </Modal>
    )
}
