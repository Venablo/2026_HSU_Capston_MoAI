import { useNavigate, useParams, useLocation } from 'react-router-dom'
import { Users } from 'lucide-react'
import './StudyHeader.css'

interface StudyHeaderProps {
    title: string
    studyCode: string
    memberCount: number
    tags: string[]
    backgroundImage?: string
}

const tabs = [
    { id: 'dashboard', label: '대시보드', path: '' },
    { id: 'board',     label: '게시판',   path: '/board' },
    { id: 'classroom', label: '학습실',   path: '/classroom' },
    { id: 'members',   label: '멤버',     path: '/members' },
    { id: 'calendar',  label: '캘린더',   path: '/calendar' },
    { id: 'ranking',   label: '랭킹',     path: '/ranking' },
    { id: 'manage',    label: '관리',     path: '/manage' },
]

export default function StudyHeader({
                                        title, studyCode, memberCount, tags, backgroundImage
                                    }: StudyHeaderProps) {
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
        <div
            className="study-header"
            style={{ backgroundImage: backgroundImage ? `url(${backgroundImage})` : undefined }}
        >
            <div className="study-header-overlay">
                <div className="study-header-content">
                    {/* Tags */}
                    <div className="study-tags">
                        {tags.map(tag => (
                            <span key={tag} className="study-tag">{tag}</span>
                        ))}
                    </div>

                    {/* Title */}
                    <h1 className="study-title">{title}</h1>

                    {/* Meta */}
                    <div className="study-meta">
                        <span className="study-code">초대 코드: {studyCode}</span>
                        <span className="study-separator">•</span>
                        <span className="study-members">
                            <Users size={15} />
                            멤버: {memberCount}명
                        </span>
                        <span className="study-separator">•</span>
                        <span className="study-status-dot"></span>
                        <span className="study-status">참여 중</span>
                    </div>
                </div>
            </div>

            {/* Tabs */}
            <div className="study-header-tabs">
                {tabs.map(tab => (
                    <button
                        key={tab.id}
                        className={`header-tab ${isActive(tab.path) ? 'active' : ''}`}
                        onClick={() => handleTabClick(tab.path)}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>
        </div>
    )
}