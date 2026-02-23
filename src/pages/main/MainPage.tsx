import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, Plus, BookOpen, Users, Lock } from 'lucide-react'
import { mockStudies } from '../../constants/mockData'
import './MainPage.css'

export default function MainPage() {
    const navigate = useNavigate()
    const [searchQuery, setSearchQuery] = useState('')
    const [selectedFilter, setSelectedFilter] = useState<'all' | 'public' | 'private'>('all')
    const [selectedTags, setSelectedTags] = useState<string[]>([])

    // 태그 목록
    const availableTags = [
        '#Frontend', '#TOEIC', '#기사', '#공무원', '#JAVA',
        '#Spring', '#Python', '#변리사', '#세무사', '#회계사',
        '#Language', '#Backend'
    ]

    // 필터링된 스터디
    const filteredStudies = mockStudies.filter(study => {
        // 검색어 필터
        if (searchQuery && !study.title.toLowerCase().includes(searchQuery.toLowerCase())) {
            return false
        }
        // 공개/비공개 필터
        if (selectedFilter === 'public' && study.isPrivate) return false
        if (selectedFilter === 'private' && !study.isPrivate) return false
        // 태그 필터
        if (selectedTags.length > 0) {
            const hasTag = study.tags.some(tag =>
                selectedTags.some(selectedTag =>
                    selectedTag.toLowerCase().includes(tag.toLowerCase())
                )
            )
            if (!hasTag) return false
        }
        return true
    })

    const toggleTag = (tag: string) => {
        setSelectedTags(prev =>
            prev.includes(tag)
                ? prev.filter(t => t !== tag)
                : [...prev, tag]
        )
    }

    return (
        <div className="main-page">
            {/* Header */}
            <div className="main-header">
                <h1>MoAi</h1>
                <p className="main-subtitle">관심사에 맞는 스터디 그룹을 찾고 함께 학습하세요</p>
            </div>

            {/* TOP Ranking */}
            <section className="top-ranking-section">
                <h2 className="section-title"> 이번 주 TOP 랭킹 그룹</h2>
                <div className="top-ranking-grid">

                </div>
            </section>

            {/* Search & Filter */}
            <section className="search-section">
                <div className="search-bar">
                    <Search size={20} className="search-icon" />
                    <input
                        type="text"
                        placeholder="그룹명, 태그, 설명으로 검색..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                    />
                </div>
                <button className="create-group-btn" onClick={() => navigate('')}>
                    <Plus size={20} />
                    <span>그룹 만들기</span>
                </button>
            </section>

            {/* Filter Buttons */}
            <div className="filter-buttons">
                <button
                    className={`filter-btn ${selectedFilter === 'all' ? 'active' : ''}`}
                    onClick={() => setSelectedFilter('all')}
                >
                    전체
                </button>
                <button
                    className={`filter-btn ${selectedFilter === 'public' ? 'active' : ''}`}
                    onClick={() => setSelectedFilter('public')}
                >
                    <BookOpen size={16} />
                    공개 그룹
                </button>
                <button
                    className={`filter-btn ${selectedFilter === 'private' ? 'active' : ''}`}
                    onClick={() => setSelectedFilter('private')}
                >
                    <Lock size={16} />
                    비공개 그룹
                </button>
            </div>

            {/* Tag Filter */}
            <div className="tag-filter">
                {availableTags.map(tag => (
                    <button
                        key={tag}
                        className={`tag-filter-btn ${selectedTags.includes(tag) ? 'active' : ''}`}
                        onClick={() => toggleTag(tag)}
                    >
                        {tag}
                    </button>
                ))}
            </div>

            {/* Study Grid */}
            <div className="study-grid">
                {filteredStudies.map(study => (
                    <div
                        key={study.id}
                        className="study-card"
                        onClick={() => navigate(`/study/${study.id}`)}
                    >
                        <div className="card-header">
                            <div className={`study-icon ${study.isPrivate ? 'private' : 'public'}`}>
                                {study.isPrivate ? <Lock size={20} /> : <BookOpen size={20} />}
                            </div>
                        </div>
                        <h3>{study.title}</h3>
                        <div className="tags">
                            {study.tags.map(tag => (
                                <span key={tag} className="tag">#{tag}</span>
                            ))}
                        </div>
                        <div className="card-footer">
                            <div className="member-count">
                                <Users size={16} />
                                <span>{study.memberCount}명</span>
                            </div>
                            <span className={`status-badge ${study.status}`}>
                                {study.status === 'active' ? '진행중' : '모집중'}
                            </span>
                        </div>
                    </div>
                ))}
            </div>

            {filteredStudies.length === 0 && (
                <div className="no-results">
                    <p>검색 결과가 없습니다.</p>
                </div>
            )}
        </div>
    )
}