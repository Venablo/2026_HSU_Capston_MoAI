import { useNavigate, useLocation } from 'react-router-dom'
import { Home, Users, Calendar, User, Moon, Sun, BookOpen } from 'lucide-react'
import { useState } from 'react'
import './Sidebar.css'

export default function Sidebar() {
    const navigate = useNavigate()
    const location = useLocation()
    const [isDark, setIsDark] = useState(false)

    const isActive = (path: string) => {
        return location.pathname === path || location.pathname.startsWith(path)
    }

    return (
        <aside className="sidebar">
            <div className="sidebar-header">
                <div className="logo" onClick={() => navigate('/main')}>
                    <BookOpen size={28} />
                    <span>MoAi</span>
                </div>
            </div>

            <nav className="sidebar-nav">
                <button
                    className={`nav-item ${isActive('/main') ? 'active' : ''}`}
                    onClick={() => navigate('/main')}
                >
                    <Home size={20} className="icon" />
                    <span className="label">홈</span>
                </button>

                <button
                    className={`nav-item ${isActive('/my-studies') ? 'active' : ''}`}
                    onClick={() => navigate('/my-studies')}
                >
                    <Users size={20} className="icon" />
                    <span className="label">내 스터디</span>
                </button>

                <button
                    className={`nav-item ${isActive('/my-calendar') ? 'active' : ''}`}
                    onClick={() => navigate('/my-calendar')}
                >
                    <Calendar size={20} className="icon" />
                    <span className="label">캘린더</span>
                </button>

                <button
                    className={`nav-item ${isActive('/my-page') ? 'active' : ''}`}
                    onClick={() => navigate('/my-page')}
                >
                    <User size={20} className="icon" />
                    <span className="label">마이페이지</span>
                </button>
            </nav>

            <div className="sidebar-footer">
                <button
                    className="nav-item"
                    onClick={() => setIsDark(!isDark)}
                >
                    {isDark ? <Sun size={20} className="icon" /> : <Moon size={20} className="icon" />}
                </button>
            </div>
        </aside>
    )
}