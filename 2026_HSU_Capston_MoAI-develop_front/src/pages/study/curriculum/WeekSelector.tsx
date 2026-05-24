import { useState, useEffect } from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import {
    ArrowLeft, BookOpen, CheckCircle2, Lock, Play,
    Trophy, BarChart2, PlayCircle,
} from 'lucide-react'
import '../../../styles/WeekSelector.css'
import { getCurriculum, getLearningRooms } from '../../../services/apiService'
import type { CurriculumWeekSummary, LearningRoomListItem } from '../../../types/api'

interface LocationState {
    subject?: string
    level?: string
    completionRate?: number
}

// 로딩 중 스켈레톤 카드
function SkeletonGrid() {
    return (
        <div className="ws-body">
            <div className="ws-skeleton-grid">
                {Array.from({ length: 6 }).map((_, i) => (
                    <div key={i} className="ws-skeleton-card">
                        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                            <div className="ws-skeleton-line" style={{ width: 44, height: 44, borderRadius: 8 }} />
                            <div className="ws-skeleton-line" style={{ width: 52, height: 22, borderRadius: 99 }} />
                        </div>
                        <div className="ws-skeleton-line" style={{ width: '80%', height: 18 }} />
                        <div className="ws-skeleton-line" style={{ width: '55%', height: 14 }} />
                        <div className="ws-skeleton-line" style={{ width: '100%', height: 4 }} />
                        <div className="ws-skeleton-line" style={{ width: '100%', height: 34, borderRadius: 8 }} />
                    </div>
                ))}
            </div>
        </div>
    )
}

export default function WeekSelector() {
    const { studyId: roomId = '' } = useParams<{ studyId: string }>()
    const navigate = useNavigate()
    const location = useLocation()
    const state = (location.state ?? {}) as LocationState

    const [weeks,   setWeeks]   = useState<CurriculumWeekSummary[]>([])
    const [room,    setRoom]    = useState<LearningRoomListItem | null>(null)
    const [loading, setLoading] = useState(true)
    const [error,   setError]   = useState<string | null>(null)
    const [unlockedUpTo, setUnlockedUpTo] = useState<number>(0)
    const [lockedMsg,    setLockedMsg]    = useState<string | null>(null)

    const load = () => {
        if (!roomId) return
        setLoading(true)
        setError(null)

        Promise.all([
            getCurriculum(roomId),
            state.subject ? Promise.resolve(null) : getLearningRooms(),
        ])
            .then(([curriculumWeeks, rooms]) => {
                setWeeks(curriculumWeeks)
                const firstIncomplete = curriculumWeeks.find(w => w.completionRate < 100)
                setUnlockedUpTo(
                    firstIncomplete ? firstIncomplete.weekNumber : curriculumWeeks.length
                )
                if (rooms) {
                    const found = rooms.find((r: LearningRoomListItem) => r.roomId === roomId)
                    if (found) setRoom(found)
                }
            })
            .catch(() => setError('커리큘럼을 불러오지 못했습니다.'))
            .finally(() => setLoading(false))
    }

    // eslint-disable-next-line react-hooks/exhaustive-deps
    useEffect(() => { load() }, [roomId])

    const subject     = state.subject ?? room?.subject ?? '학습실'
    const level       = state.level   ?? room?.level
    const overallRate = state.completionRate ?? room?.completionRate ?? 0
    const totalWeeks  = weeks.length
    const completedCount = weeks.filter(w => w.completionRate >= 100).length

    const isLocked = (w: CurriculumWeekSummary) => w.weekNumber > unlockedUpTo

    const getStatus = (w: CurriculumWeekSummary): 'completed' | 'active' | 'locked' => {
        if (isLocked(w))             return 'locked'
        if (w.completionRate >= 100) return 'completed'
        return 'active'
    }

    const goToWeek = (w: CurriculumWeekSummary) => {
        if (isLocked(w)) {
            setLockedMsg(`Week ${w.weekNumber}은 이전 주차를 완료해야 열립니다.`)
            window.setTimeout(() => setLockedMsg(null), 2500)
            return
        }
        navigate(`/study/${roomId}/focus?curriculumId=${w.weekId}`)
    }

    // 현재 진행 중인 주차 (첫 번째 미완료 + 잠금 아닌 것)
    const activeWeek = weeks.find(w => !isLocked(w) && w.completionRate < 100)
    // 그리드에는 active 주차 제외하고 나머지 표시
    const gridWeeks = activeWeek ? weeks.filter(w => w.weekId !== activeWeek.weekId) : weeks

    // Hero 통계
    const stats = [
        { value: totalWeeks,      label: '전체 주차', icon: <BookOpen size={14} /> },
        { value: completedCount,  label: '완료',      icon: <Trophy size={14} /> },
        { value: `${Number(overallRate).toFixed(0)}%`, label: '전체 진도', icon: <BarChart2 size={14} /> },
    ]

    return (
        <div className="ws-root">
            {/* ── Hero Banner ── */}
            <div className="ws-hero">
                <button className="ws-hero__back" onClick={() => navigate('/my-studies')}>
                    <ArrowLeft size={14} strokeWidth={2} />
                    내 학습실 목록
                </button>

                <h1 className="ws-hero__subject">{subject}</h1>

                <div className="ws-hero__badges">
                    {level && <span className="ws-hero__badge">{level}</span>}
                    {completedCount > 0 && completedCount === totalWeeks && (
                        <span className="ws-hero__badge ws-hero__badge--green">🎉 전체 완료</span>
                    )}
                    {completedCount > 0 && completedCount < totalWeeks && (
                        <span className="ws-hero__badge ws-hero__badge--green">
                            {completedCount}주차 완료
                        </span>
                    )}
                </div>

                {!loading && totalWeeks > 0 && (
                    <>
                        <div className="ws-hero__stats">
                            {stats.map(s => (
                                <div key={s.label} className="ws-hero__stat">
                                    <span className="ws-hero__stat-value">{s.value}</span>
                                    <span className="ws-hero__stat-label">{s.label}</span>
                                </div>
                            ))}
                        </div>

                        <div className="ws-hero__progress">
                            <div className="ws-hero__progress-header">
                                <span className="ws-hero__progress-label">전체 진도율</span>
                                <span className="ws-hero__progress-pct">{Number(overallRate).toFixed(0)}%</span>
                            </div>
                            <div className="ws-hero__progress-track">
                                <div className="ws-hero__progress-fill" style={{ width: `${overallRate}%` }} />
                            </div>
                        </div>
                    </>
                )}
            </div>

            {/* ── Loading ── */}
            {loading && <SkeletonGrid />}

            {/* ── Error ── */}
            {!loading && error && (
                <div className="ws-body">
                    <div className="ws-error-box">
                        <div className="ws-error-box__icon">⚠️</div>
                        <div className="ws-error-box__msg">{error}</div>
                        <button className="ws-error-box__retry" onClick={load}>다시 시도</button>
                    </div>
                </div>
            )}

            {/* ── Content ── */}
            {!loading && !error && (
                <div className="ws-body">
                    {/* 현재 학습 중 하이라이트 */}
                    {activeWeek && (
                        <div className="ws-continue-section">
                            <div className="ws-continue-label">
                                <PlayCircle size={13} strokeWidth={2} />
                                계속 학습하기
                            </div>
                            <div className="ws-continue-card" onClick={() => goToWeek(activeWeek)}>
                                <div className="ws-continue-card__num">
                                    {activeWeek.weekNumber}
                                    <span className="ws-continue-card__num-sub">WEEK</span>
                                </div>
                                <div className="ws-continue-card__info">
                                    <div className="ws-continue-card__topic">{activeWeek.topic}</div>
                                    <div className="ws-continue-card__progress-row">
                                        <div className="ws-continue-card__progress-track">
                                            <div
                                                className="ws-continue-card__progress-fill"
                                                style={{ width: `${activeWeek.completionRate}%` }}
                                            />
                                        </div>
                                        <span className="ws-continue-card__progress-pct">
                                            {Number(activeWeek.completionRate).toFixed(0)}%
                                        </span>
                                    </div>
                                </div>
                                <button
                                    className="ws-continue-card__btn"
                                    onClick={e => { e.stopPropagation(); goToWeek(activeWeek) }}
                                >
                                    <Play size={14} strokeWidth={2} />
                                    학습 시작
                                </button>
                            </div>
                        </div>
                    )}

                    {/* 전체 주차 그리드 */}
                    {gridWeeks.length > 0 && (
                        <>
                            <div className="ws-all-section-label">
                                전체 {totalWeeks}주차 커리큘럼
                            </div>
                            <div className="ws-grid">
                                {gridWeeks.map(w => {
                                    const status = getStatus(w)
                                    return (
                                        <div
                                            key={w.weekId}
                                            className={`ws-card ws-card--${status}`}
                                            onClick={() => goToWeek(w)}
                                        >
                                            <div className="ws-card__header">
                                                <div className={`ws-card__num-badge ws-card__num-badge--${status}`}>
                                                    {w.weekNumber}
                                                    <span className="ws-card__num-badge-sub">WEEK</span>
                                                </div>
                                                <span className={`ws-card__status ws-card__status--${status}`}>
                                                    {status === 'completed' ? '완료' : status === 'active' ? '학습 가능' : '잠금'}
                                                </span>
                                            </div>

                                            <div className="ws-card__topic">{w.topic}</div>

                                            <div className="ws-card__progress">
                                                <div className="ws-card__progress-meta">
                                                    <span>진도율</span>
                                                    <span>{Number(w.completionRate).toFixed(0)}%</span>
                                                </div>
                                                <div className="ws-card__progress-track">
                                                    <div
                                                        className="ws-card__progress-fill"
                                                        style={{ width: `${w.completionRate}%` }}
                                                    />
                                                </div>
                                            </div>

                                            <button
                                                className={`ws-card__btn ws-card__btn--${
                                                    status === 'locked' ? 'locked'
                                                    : status === 'completed' ? 'review'
                                                    : 'start'
                                                }`}
                                                onClick={e => { e.stopPropagation(); goToWeek(w) }}
                                                disabled={status === 'locked'}
                                            >
                                                {status === 'locked'    ? <><Lock size={13} strokeWidth={2} /> 잠금</> :
                                                 status === 'completed' ? <><CheckCircle2 size={13} strokeWidth={2} /> 다시 학습하기</> :
                                                                          <><Play size={13} strokeWidth={2} /> 학습 시작</>}
                                            </button>
                                        </div>
                                    )
                                })}
                            </div>
                        </>
                    )}
                </div>
            )}

            {lockedMsg && (
                <div className="ws-locked-toast">
                    <Lock size={13} strokeWidth={1.5} />
                    {lockedMsg}
                </div>
            )}
        </div>
    )
}
