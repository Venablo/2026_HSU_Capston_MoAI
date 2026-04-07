import { X } from 'lucide-react'

export default function Modal({
    children,
    onClose,
    wide,
}: {
    children: React.ReactNode
    onClose: () => void
    wide?: boolean
}) {
    return (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
            <div className={`modal-box animate-scale-in ${wide ? 'modal-box--wide' : ''}`}>
                <button className="modal__close" onClick={onClose}>
                    <X size={16} strokeWidth={2.5} />
                </button>
                {children}
            </div>
        </div>
    )
}
