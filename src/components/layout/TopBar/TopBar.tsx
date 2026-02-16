import { useNavigate } from 'react-router-dom'
import { Mail, Bell } from 'lucide-react'
import './TopBar.css'

export default function TopBar() {
    const navigate = useNavigate()

    return (
        <header className="topbar">
            <div className="topbar-right">
                <button
                    className="topbar-icon-btn"
                    onClick={() => navigate('/notifications')}
                    title="알림"
                >
                    <Bell size={20} color="#374151" strokeWidth={2} />
                </button>

                <button
                    className="topbar-icon-btn"
                    onClick={() => navigate('/messages')}
                    title="메시지"
                >
                    <Mail size={20} color="#374151" strokeWidth={2} />
                </button>
            </div>
        </header>
    )
}