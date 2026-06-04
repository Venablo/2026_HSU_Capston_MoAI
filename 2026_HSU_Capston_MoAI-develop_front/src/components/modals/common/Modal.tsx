import { X } from 'lucide-react'

export default function Modal({
    children,
    onClose,
    wide,
    disableClose,
}: {
    children: React.ReactNode
    onClose: () => void
    wide?: boolean
    /** X 버튼과 오버레이 클릭을 비활성화해 강제 진행 모달로 만든다 */
    disableClose?: boolean
}) {
    return (
        <div
            className="modal-overlay"
            onClick={e => e.target === e.currentTarget && !disableClose && onClose()}
        >
            <div className={`modal-box animate-scale-in ${wide ? 'modal-box--wide' : ''}`}>
                {!disableClose && (
                    <button className="modal__close" onClick={onClose}>
                        <X size={16} strokeWidth={2.5} />
                    </button>
                )}
                {children}
            </div>
        </div>
    )
}
