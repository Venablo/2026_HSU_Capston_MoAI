import type { ReactNode } from 'react'
import Sidebar from '../Sidebar/Sidebar'
import TopBar from '../TopBar/TopBar'
import './MainLayout.css'

interface MainLayoutProps {
    children: ReactNode
}

export default function MainLayout({ children }: MainLayoutProps) {
    return (
        <div className="main-layout">
            <Sidebar />
            <TopBar />
            <main className="main-content">
                {children}
            </main>
        </div>
    )
}