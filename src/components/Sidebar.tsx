import { NavLink, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { Home, BookOpen, User, Moon, LogOut, ChevronLeft, ChevronRight } from 'lucide-react'
import '../styles/Sidebar.css'

interface SidebarProps {
    title?: string
    subtitle?: string
    collapsed?: boolean
    onToggle?: () => void
}

const NAV_ITEMS = [
    { to: '/main',       label: 'Home',     Icon: Home },
    { to: '/my-studies', label: 'My Study', Icon: BookOpen },
    { to: '/main',       label: 'My Page',  Icon: User },
]

export default function Sidebar({
    title = 'MoAI',
    subtitle = 'INTELLIGENT TUTOR',
    collapsed = false,
    onToggle,
}: SidebarProps) {
    const navigate = useNavigate()
    const [, setDark] = useState(false)

    return (
        <aside className={`sidebar animate-slide-up${collapsed ? ' sidebar--collapsed' : ''}`}>
            <div className="sidebar__logo-wrap">
                {!collapsed && <div className="sidebar__logo-name">{title}</div>}
                {!collapsed && <div className="sidebar__logo-sub">{subtitle}</div>}
            </div>
            <nav className="sidebar__nav">
                {NAV_ITEMS.map(({ to, label, Icon }) => (
                    <NavLink
                        key={label}
                        to={to}
                        className={({ isActive }) =>
                            isActive && label !== 'My Page'
                                ? 'sidebar__nav-btn sidebar__nav-btn--active'
                                : 'sidebar__nav-btn'
                        }
                        title={collapsed ? label : undefined}
                    >
                        <span className="sidebar__nav-icon"><Icon size={18} strokeWidth={2} /></span>
                        {!collapsed && <span className="sidebar__nav-label">{label}</span>}
                    </NavLink>
                ))}
            </nav>
            <div className="sidebar__bottom">
                <button
                    className="sidebar__bottom-btn sidebar__toggle-btn"
                    onClick={onToggle}
                    title={collapsed ? 'Expand' : 'Collapse'}
                >
                    {collapsed ? <ChevronRight size={16} strokeWidth={2} /> : <ChevronLeft size={16} strokeWidth={2} />}
                    {!collapsed && <span className="sidebar__nav-label">접기</span>}
                </button>
                <button
                    className="sidebar__bottom-btn"
                    onClick={() => setDark(d => !d)}
                    title={collapsed ? 'Dark Mode' : undefined}
                >
                    <Moon size={16} strokeWidth={2} />
                    {!collapsed && <span className="sidebar__nav-label">Dark Mode</span>}
                </button>
                <button
                    className="sidebar__bottom-btn"
                    onClick={() => navigate('/')}
                    title={collapsed ? 'Logout' : undefined}
                >
                    <LogOut size={16} strokeWidth={2} />
                    {!collapsed && <span className="sidebar__nav-label">Logout</span>}
                </button>
            </div>
        </aside>
    )
}
