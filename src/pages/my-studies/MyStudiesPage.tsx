import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { Database, Globe, Monitor, Plus } from 'lucide-react'
import '../../styles/MyStudiesPage.css'

type FilterKey = '전체' | '진행 중' | '완료'

interface Study {
    id:       number
    icon:     ReactNode
    title:    string
    week:     string
    topic:    string
    progress: number
    status:   string
    tags:     string[]
    color:    string
}

const STUDIES: Study[] = [
    {
        id:       1,
        icon:     <Database size={22} strokeWidth={1.5} />,
        title:    '정보처리기사 완성반',
        week:     'Week 1 / 10주',
        topic:    'DB Foundation',
        progress: 30,
        status:   '진행 중',
        tags:     ['#정보처리기사', '#초급'],
        color:    '#7c3aed',
    },
    {
        id:       2,
        icon:     <Globe size={22} strokeWidth={1.5} />,
        title:    '웹개발 기초 부트캠프',
        week:     'Week 3 / 8주',
        topic:    'CSS & Flexbox',
        progress: 45,
        status:   '진행 중',
        tags:     ['#웹개발', '#CSS'],
        color:    '#0ea5e9',
    },
    {
        id:       3,
        icon:     <Monitor size={22} strokeWidth={1.5} />,
        title:    'CS 면접 완성',
        week:     '완료',
        topic:    '운영체제, 네트워크',
        progress: 100,
        status:   '완료',
        tags:     ['#CS면접', '#완료'],
        color:    '#10b981',
    },
]

export default function MyStudiesPage() {
    const navigate = useNavigate()
    const [filter, setFilter] = useState<FilterKey>('전체')

    const filtered = filter === '전체' ? STUDIES : STUDIES.filter(s => s.status === filter)

    return (
        <>
            <header className="topbar">
                <h2 className="topbar__title">내 스터디</h2>
                <button
                    className="btn-primary"
                    style={{ padding: '8px 20px', fontSize: '13px', display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                    onClick={() => navigate('/main')}
                >
                    <Plus size={14} strokeWidth={2} />
                    새 학습실 만들기
                </button>
            </header>

            <main className="my-studies__content">
                {/* Filter */}
                <div className="my-studies__filter-row">
                    {(['전체', '진행 중', '완료'] as FilterKey[]).map(f => (
                        <button
                            key={f}
                            className={`my-studies__filter-btn ${filter === f ? 'my-studies__filter-btn--active' : ''}`}
                            onClick={() => setFilter(f)}
                        >
                            {f}
                        </button>
                    ))}
                </div>

                {/* Grid */}
                <div className="study-grid">
                    {filtered.map((study, i) => (
                        <div
                            key={study.id}
                            className={`study-card animate-fade-in delay-${i * 100}`}
                            onClick={() => navigate(`/study/${study.id}/classroom`)}
                        >
                            <div className="study-card__header">
                                <div className="study-card__info-row">
                                    <div className="study-card__emoji-wrap" style={{ color: study.color }}>
                                        {study.icon}
                                    </div>
                                    <div>
                                        <div className="study-card__title">{study.title}</div>
                                        <div className="study-card__week">{study.week}</div>
                                    </div>
                                </div>
                                <span
                                    className={`study-card__status-badge ${
                                        study.status === '완료'
                                            ? 'study-card__status-badge--done'
                                            : 'study-card__status-badge--active'
                                    }`}
                                >
                                    {study.status}
                                </span>
                            </div>

                            <div className="study-card__topic">현재: {study.topic}</div>

                            <div>
                                <div className="study-card__progress-header">
                                    <span className="study-card__progress-label">진행률</span>
                                    <span className="study-card__progress-pct" style={{ color: study.color }}>
                                        {study.progress}%
                                    </span>
                                </div>
                                <div className="study-card__bar-track">
                                    <div
                                        className="study-card__bar-fill"
                                        style={{ width: `${study.progress}%`, background: study.color }}
                                    />
                                </div>
                            </div>

                            <div className="study-card__tags">
                                {study.tags.map(tag => (
                                    <span key={tag} className="study-card__tag">{tag}</span>
                                ))}
                            </div>
                        </div>
                    ))}

                    {/* Add card */}
                    <div
                        className="study-card--add"
                        onClick={() => navigate('/main')}
                    >
                        <div className="study-card--add__icon">
                            <Plus size={24} strokeWidth={1.5} />
                        </div>
                        <div className="study-card--add__label">새 학습실 만들기</div>
                    </div>
                </div>
            </main>
        </>
    )
}
