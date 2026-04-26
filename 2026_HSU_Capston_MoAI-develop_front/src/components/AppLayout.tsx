import { Outlet } from 'react-router-dom'
import { useState } from 'react'
import Sidebar from './Sidebar'
import '../styles/Sidebar.css'

export default function AppLayout() {
    const [collapsed, setCollapsed] = useState(false)

    return (
        <div className="layout">
            <Sidebar
                collapsed={collapsed}
                onToggle={() => setCollapsed(c => !c)}
            />
            <div className={`layout__main${collapsed ? ' layout__main--left-collapsed' : ''}`}>
                <Outlet />
            </div>
        </div>
    )
}
