import type { ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Clock, CheckCircle2, Target, Calendar, Lock, Flame, ArrowLeft, ArrowRight } from 'lucide-react'
import '../../../styles/StudyDashboard.css'

const WEEKS = [
    { week: 1, title: 'DB Foundation',    status: 'active', progress: 30 },
    { week: 2, title: 'SQL 심화',          status: 'locked', progress: 0  },
    { week: 3, title: '인덱스 & 최적화',   status: 'locked', progress: 0  },
    { week: 4, title: '트랜잭션 & 동시성', status: 'locked', progress: 0  },
    { week: 5, title: '네트워크 기초',     status: 'locked', progress: 0  },
]

interface Stat { icon: ReactNode; label: string; value: ReactNode }

const STATS: Stat[] = [
    {
        icon:  <Clock size={20} strokeWidth={1.5} />,
        label: '총 학습 시간',
        value: '8.5h',
    },
    {
        icon:  <CheckCircle2 size={20} strokeWidth={1.5} />,
        label: '완료한 퀴즈',
        value: '24개',
    },
    {
        icon:  <Target size={20} strokeWidth={1.5} />,
        label: '평균 정답률',
        value: '78%',
    },
    {
        icon:  <Calendar size={20} strokeWidth={1.5} />,
        label: '연속 학습일',
        value: (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                5일 <Flame size={14} strokeWidth={1.5} style={{ color: '#f97316' }} />
            </span>
        ),
    },
]

export default function StudyDashboard() {
    const navigate    = useNavigate()
    const { studyId } = useParams()

    return (
        <>
            <header className="topbar">
                <h2 className="topbar__title">스터디 대시보드</h2>
                <button
                    className="btn-ghost"
                    style={{ padding: '8px 16px', fontSize: '13px', display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                    onClick={() => navigate('/my-studies')}
                >
                    <ArrowLeft size={14} strokeWidth={1.5} />
                    내 스터디
                </button>
            </header>

            <main className="dashboard__content">
                {/* Overview */}
                <div className="dashboard__overview animate-slide-up">
                    <div>
                        <div className="dashboard__overview-meta">STUDY #{studyId}</div>
                        <h2 className="dashboard__overview-title">정보처리기사 완성반</h2>
                        <p className="dashboard__overview-desc">10주 과정 · 하루 3시간 · 초급</p>
                    </div>
                    <div>
                        <div className="dashboard__overview-pct">30%</div>
                        <div className="dashboard__overview-pct-label">전체 진행률</div>
                    </div>
                </div>

                <div className="dashboard__grid">
                    {/* Curriculum */}
                    <div>
                        <h3
                            className="dashboard__curriculum-title"
                            style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
                        >
                            <Calendar size={18} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                            주차별 커리큘럼
                        </h3>
                        <div className="dashboard__week-list">
                            {WEEKS.map((w, i) => (
                                <div
                                    key={w.week}
                                    className={`dashboard__week-card animate-fade-in delay-${i * 100} ${
                                        w.status === 'active' ? 'dashboard__week-card--active' : ''
                                    } ${w.status === 'locked' ? 'dashboard__week-card--locked' : ''}`}
                                    onClick={() => w.status === 'active' && navigate(`/study/${studyId}/classroom`)}
                                >
                                    <div className={`dashboard__week-badge ${w.status === 'active' ? 'dashboard__week-badge--active' : ''}`}>
                                        {w.status === 'locked'
                                            ? <Lock size={13} strokeWidth={1.5} />
                                            : `W${w.week}`}
                                    </div>
                                    <div className="dashboard__week-info">
                                        <div className="dashboard__week-title">Week {w.week}: {w.title}</div>
                                        {w.status === 'active' && (
                                            <>
                                                <div className="dashboard__week-bar-track">
                                                    <div className="dashboard__week-bar-fill" style={{ width: `${w.progress}%` }} />
                                                </div>
                                                <div className="dashboard__week-pct">{w.progress}% 완료</div>
                                            </>
                                        )}
                                    </div>
                                    {w.status === 'active' && (
                                        <button
                                            className="dashboard__week-btn"
                                            style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                                        >
                                            학습하기
                                            <ArrowRight size={13} strokeWidth={1.5} />
                                        </button>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Stats */}
                    <div className="dashboard__stats">
                        {STATS.map((stat, i) => (
                            <div key={i} className={`dashboard__stat-card animate-fade-in delay-${i * 100}`}>
                                <div className="dashboard__stat-icon" style={{ color: 'var(--color-purple-500)' }}>
                                    {stat.icon}
                                </div>
                                <div>
                                    <div className="dashboard__stat-label">{stat.label}</div>
                                    <div className="dashboard__stat-value">{stat.value}</div>
                                </div>
                            </div>
                        ))}

                        <button
                            className="dashboard__enter-btn"
                            style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                            onClick={() => navigate(`/study/${studyId}/classroom`)}
                        >
                            학습실 입장
                            <ArrowRight size={15} strokeWidth={1.5} />
                        </button>
                    </div>
                </div>
            </main>
        </>
    )
}
