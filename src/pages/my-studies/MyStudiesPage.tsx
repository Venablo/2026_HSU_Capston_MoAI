import { useNavigate } from 'react-router-dom'
import { useState, useEffect } from 'react'
import { BookOpen, Plus, Loader2 } from 'lucide-react'
import '../../styles/MyStudiesPage.css'
import { getLearningRooms } from '../../services/apiService'
import type { LearningRoomListItem } from '../../types/api'

type FilterKey = '전체' | '진행 중' | '완료'

function statusLabel(status: LearningRoomListItem['status']): string {
    if (status === 'active')    return '진행 중'
    if (status === 'completed') return '완료'
    if (status === 'paused')    return '일시정지'
    return status
}

export default function MyStudiesPage() {
    const navigate = useNavigate()
    const [filter,  setFilter]  = useState<FilterKey>('전체')
    const [rooms,   setRooms]   = useState<LearningRoomListItem[]>([])
    const [loading, setLoading] = useState(true)
    const [error,   setError]   = useState<string | null>(null)

    useEffect(() => {
        let cancelled = false
        getLearningRooms()
            .then(data  => { if (!cancelled) setRooms(data) })
            .catch(e    => { if (!cancelled) setError(e instanceof Error ? e.message : '학습실 목록을 불러오지 못했습니다.') })
            .finally(() => { if (!cancelled) setLoading(false) })
        return () => { cancelled = true }
    }, [])

    const filtered = rooms.filter(r => {
        if (filter === '전체')   return true
        if (filter === '진행 중') return r.status === 'active' || r.status === 'paused'
        if (filter === '완료')   return r.status === 'completed'
        return true
    })

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

                {/* Loading */}
                {loading && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '48px 0', color: 'var(--color-text-secondary)' }}>
                        <Loader2 size={20} strokeWidth={1.5} className="animate-spin" />
                        <span>학습실 목록을 불러오는 중...</span>
                    </div>
                )}

                {/* Error */}
                {!loading && error && (
                    <div style={{ padding: '48px 0', color: '#ef4444' }}>⚠ {error}</div>
                )}

                {/* Grid */}
                {!loading && !error && (
                    <div className="study-grid">
                        {filtered.map((room, i) => {
                            const label  = statusLabel(room.status)
                            const isDone = room.status === 'completed'
                            const weekStr = isDone
                                ? '완료'
                                : `Week ${room.currentWeek} / ${room.durationWeeks}주`

                            return (
                                <div
                                    key={room.roomId}
                                    className={`study-card animate-fade-in delay-${i * 100}`}
                                    onClick={() => navigate(`/study/${room.roomId}/classroom`)}
                                >
                                    <div className="study-card__header">
                                        <div className="study-card__info-row">
                                            <div className="study-card__emoji-wrap" style={{ color: 'var(--color-purple-500)' }}>
                                                <BookOpen size={22} strokeWidth={1.5} />
                                            </div>
                                            <div>
                                                <div className="study-card__title">{room.subject}</div>
                                                <div className="study-card__week">{weekStr}</div>
                                            </div>
                                        </div>
                                        <span
                                            className={`study-card__status-badge ${
                                                isDone
                                                    ? 'study-card__status-badge--done'
                                                    : 'study-card__status-badge--active'
                                            }`}
                                        >
                                            {label}
                                        </span>
                                    </div>

                                    <div className="study-card__topic">레벨: {room.level}</div>

                                    <div>
                                        <div className="study-card__progress-header">
                                            <span className="study-card__progress-label">진행률</span>
                                            <span className="study-card__progress-pct" style={{ color: 'var(--color-purple-500)' }}>
                                                {room.completionRate}%
                                            </span>
                                        </div>
                                        <div className="study-card__bar-track">
                                            <div
                                                className="study-card__bar-fill"
                                                style={{ width: `${room.completionRate}%`, background: 'var(--color-purple-500)' }}
                                            />
                                        </div>
                                    </div>

                                    <div className="study-card__tags">
                                        <span className="study-card__tag">#{room.subject}</span>
                                        <span className="study-card__tag">#{room.level}</span>
                                    </div>
                                </div>
                            )
                        })}

                        {/* Empty state */}
                        {filtered.length === 0 && (
                            <div style={{ gridColumn: '1 / -1', padding: '40px 0', color: 'var(--color-text-secondary)', textAlign: 'center' }}>
                                {filter === '전체' ? '아직 학습실이 없습니다.' : `'${filter}' 학습실이 없습니다.`}
                            </div>
                        )}

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
                )}
            </main>
        </>
    )
}
