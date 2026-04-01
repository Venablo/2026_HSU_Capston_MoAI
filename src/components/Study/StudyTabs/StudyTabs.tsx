import { useNavigate, useParams, useLocation } from 'react-router-dom'
import './StudyTabs.css'

const tabs = [
    { id: 'dashboard', label: '대시보드', path: '' },
    { id: 'board', label: '게시판', path: '/board' },
    { id: 'classroom', label: '학습실', path: '/classroom' },
    { id: 'members', label: '멤버', path: '/members' },
    { id: 'calendar', label: '캘린더', path: '/calendar' },
    { id: 'ranking', label: '랭킹', path: '/ranking' },
    { id: 'manage', label: '관리', path: '/manage' }
]

export default function StudyTabs() {
    const navigate = useNavigate()
    const { studyId } = useParams()
    const location = useLocation()

    const isActive = (tabPath: string) => {
        const currentPath = location.pathname
        const basePath = `/study/${studyId}`

        if (tabPath === '') {
            return currentPath === basePath || currentPath === `${basePath}/dashboard`
        }

        return currentPath.startsWith(`${basePath}${tabPath}`)
    }

    const handleTabClick = (tabPath: string) => {
        navigate(`/study/${studyId}${tabPath}`)
    }

    return (
        <div className="study-tabs">
            <div className="study-tabs-container">
                {tabs.map(tab => (
                    <button
                        key={tab.id}
                        className={`study-tab ${isActive(tab.path) ? 'active' : ''}`}
                        onClick={() => handleTabClick(tab.path)}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>
        </div>
    )
}