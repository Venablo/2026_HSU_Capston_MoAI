import type { ReactNode } from 'react'
import { useState } from 'react'
import {
    Search, Bell, ChevronLeft, ChevronRight,
    FileText, FileEdit, Package,
    PlayCircle, Video,
    ClipboardList, Play, Zap, MessageSquare,
    Calendar, BrainCircuit, CheckCircle2,
    Trophy, Lock, Users, MessageCircle,
    UserCircle, ArrowRight,
} from 'lucide-react'
import '../../../styles/StudyClassroom.css'
import { ClassroomModalProvider, useClassroomModal } from '../../../context/ClassroomModalContext'
import ClassroomModals from '../../../components/modals/ClassroomModals'
import DebugEventController from '../../../components/modals/DebugEventController'

// ── Types & constants ───────────────────────────────────────────────────────
type TabKey = 'docs' | 'videos' | 'summary' | 'quiz'

interface Doc   { icon: ReactNode; name: string; size: string; type: string }
interface Vid   { thumb: ReactNode; title: string; channel: string; views: string; duration: string }
interface Tab   { key: TabKey; icon: ReactNode; label: string }

const DOCS: Doc[] = [
    { icon: <FileText size={20} strokeWidth={1.5} />,  name: 'Week 1 데이터베이스 기초 완벽 정리.pdf', size: '2.4MB', type: 'PDF Document'       },
    { icon: <FileEdit size={20} strokeWidth={1.5} />,  name: '기출문제 풀이집 및 해설.docx',            size: '1.1MB', type: 'Microsoft Word'      },
    { icon: <Package  size={20} strokeWidth={1.5} />,  name: 'SQL 실습용 데이터셋.zip',                 size: '15MB',  type: 'Compressed Archive'  },
]

const VIDEOS: Vid[] = [
    { thumb: <PlayCircle size={28} strokeWidth={1.5} />, title: '10분 만에 끝내는 DB 트랜잭션 완벽 이해',   channel: 'MoAI AI 큐레이션', views: '1.2만회', duration: '10:24' },
    { thumb: <Video     size={28} strokeWidth={1.5} />, title: '비전공자를 위한 ACID 속성 가장 쉬운 설명', channel: 'MoAI AI 큐레이션', views: '8.5천회',  duration: '08:15' },
]

const SUMMARY_ITEMS = [
    '트랜잭션(Transaction)은 데이터베이스 작업의 논리적 단위입니다.',
    'ACID: Atomicity(원자성) · Consistency(일관성) · Isolation(격리성) · Durability(지속성)',
    '원자성: ALL or NOTHING — 트랜잭션 내 모든 연산은 전부 실행되거나 전혀 실행되지 않아야 합니다.',
    'COMMIT은 트랜잭션을 확정, ROLLBACK은 이전 상태로 되돌립니다.',
]

const QUIZ_HISTORY = [
    { q: 'DB 구조와 스키마의 차이점은?',       result: '정답', score: '100점' },
    { q: 'PRIMARY KEY의 특성 2가지를 말하시오', result: '오답', score: '0점'  },
    { q: 'NULL과 빈 문자열의 차이점은?',        result: '정답', score: '100점' },
]

const TABS: Tab[] = [
    { key: 'docs',    icon: <ClipboardList size={14} strokeWidth={1.5} />, label: '주차별 공식 교안' },
    { key: 'videos',  icon: <Play          size={14} strokeWidth={1.5} />, label: 'AI 추천 영상'    },
    { key: 'summary', icon: <Zap           size={14} strokeWidth={1.5} />, label: 'AI 핵심 요약'    },
    { key: 'quiz',    icon: <MessageSquare size={14} strokeWidth={1.5} />, label: '퀴즈 내역'       },
]

// ── Inner component (must live inside ClassroomModalProvider) ────────────────
function StudyClassroomContent() {
    const [rightCollapsed, setRightCollapsed] = useState(false)
    const [tab, setTab] = useState<TabKey>('docs')
    const progress = 30

    const { open, metacogComplete, partnerConnected } = useClassroomModal()

    return (
        <>
            {/* Topbar */}
            <header className="topbar">
                <h2 className="topbar__title">AI 상세 학습실</h2>
                <div className="topbar__actions">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', background: 'var(--color-purple-50)', borderRadius: '10px', padding: '8px 14px' }}>
                        <Search size={16} strokeWidth={1.5} />
                        <input
                            placeholder="Search lessons..."
                            style={{ border: 'none', background: 'transparent', outline: 'none', fontSize: '13px', width: '160px', color: 'var(--color-text-primary)', fontFamily: 'inherit' }}
                        />
                    </div>
                    <button className="topbar__icon-btn"><Bell size={18} strokeWidth={1.5} /></button>
                    <div className="topbar__avatar">K</div>
                </div>
            </header>

            {/* Body */}
            <div className="classroom">
                {/* ── Main panel ── */}
                <div className="classroom__main">
                    {/* Week selector */}
                    <button
                        className="classroom__week-btn"
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                    >
                        <Calendar size={14} strokeWidth={1.5} />
                        Week 1: DB Foundation
                        <ChevronRight size={14} strokeWidth={1.5} style={{ transform: 'rotate(90deg)' }} />
                    </button>

                    {/* Progress */}
                    <div className="classroom__progress-label">
                        COURSE PROGRESS: {progress}%
                    </div>
                    <div className="classroom__progress-track">
                        <div className="classroom__progress-fill" style={{ width: `${progress}%` }} />
                    </div>

                    {/* Lesson info */}
                    <h1 className="classroom__lesson-title">
                        데이터베이스 아키텍처 및 트랜잭션 이해
                    </h1>
                    <p className="classroom__lesson-desc">
                        이번 주차에는 DB의 기본 구조와 ACID 원리를 마스터합니다.
                    </p>

                    {/* Video player */}
                    <div className="classroom__video">
                        <div className="classroom__video-bg">
                            <pre className="classroom__video-code">{
`BEGIN TRANSACTION;
  SELECT * FROM accounts WHERE id = 1;
  UPDATE accounts SET balance = balance - 100;
  UPDATE accounts SET balance = balance + 100;
COMMIT;

-- ACID Properties:
-- Atomicity | Consistency | Isolation | Durability`
                            }</pre>
                        </div>
                        <div className="classroom__video-play">
                            <span style={{ color: 'white', fontSize: '28px', marginLeft: '4px' }}>▶</span>
                        </div>
                        <div className="classroom__video-duration">12:34</div>
                    </div>

                    {/* Tabs */}
                    <div className="classroom__tabs">
                        {TABS.map(t => (
                            <button
                                key={t.key}
                                className={`classroom__tab-btn ${tab === t.key ? 'classroom__tab-btn--active' : ''}`}
                                style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                                onClick={() => setTab(t.key)}
                            >
                                {t.icon}
                                {t.label}
                            </button>
                        ))}
                    </div>

                    {/* Tab: docs */}
                    {tab === 'docs' && (
                        <div className="classroom__doc-list">
                            {DOCS.map((doc, i) => (
                                <div key={i} className="classroom__doc-item">
                                    <div className="classroom__doc-icon" style={{ color: 'var(--color-purple-500)' }}>
                                        {doc.icon}
                                    </div>
                                    <div className="classroom__doc-info">
                                        <div className="classroom__doc-name">{doc.name}</div>
                                        <div className="classroom__doc-meta">{doc.size} · {doc.type}</div>
                                    </div>
                                    <button className="classroom__doc-download">다운로드</button>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Tab: videos */}
                    {tab === 'videos' && (
                        <div className="classroom__video-grid">
                            {VIDEOS.map((v, i) => (
                                <div key={i} className="classroom__video-card">
                                    <div className="classroom__video-thumb">
                                        <div className="classroom__video-thumb-icon" style={{ color: 'rgba(255,255,255,0.8)' }}>
                                            {v.thumb}
                                        </div>
                                        <div className="classroom__video-thumb-duration">{v.duration}</div>
                                    </div>
                                    <div className="classroom__video-meta">
                                        <div className="classroom__video-title">{v.title}</div>
                                        <div className="classroom__video-channel">{v.channel} · 조회수 {v.views}</div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Tab: summary */}
                    {tab === 'summary' && (
                        <div className="classroom__summary">
                            <div
                                className="classroom__summary-heading"
                                style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                            >
                                <Zap size={16} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                                AI 핵심 요약
                            </div>
                            {SUMMARY_ITEMS.map((s, i) => (
                                <div key={i} className="classroom__summary-row">
                                    <div className="classroom__summary-dot" />
                                    <div className="classroom__summary-text">{s}</div>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Tab: quiz history */}
                    {tab === 'quiz' && (
                        <div className="classroom__quiz-history">
                            <div
                                className="classroom__quiz-history-title"
                                style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                            >
                                <MessageSquare size={16} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                                퀴즈 내역
                            </div>
                            {QUIZ_HISTORY.map((item, i) => (
                                <div key={i} className="classroom__quiz-row">
                                    <div className="classroom__quiz-q">Q{i + 1}. {item.q}</div>
                                    <div className="classroom__quiz-result-row">
                                        <span className={`classroom__quiz-badge ${item.result === '정답' ? 'classroom__quiz-badge--correct' : 'classroom__quiz-badge--wrong'}`}>
                                            {item.result}
                                        </span>
                                        <span className="classroom__quiz-score">{item.score}</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* ── Right aside ── */}
                <aside className={`classroom__aside${rightCollapsed ? ' classroom__aside--collapsed' : ''}`}>
                    <div className="classroom__aside-header">
                        <button
                            className="classroom__aside-toggle"
                            onClick={() => setRightCollapsed(c => !c)}
                            title={rightCollapsed ? '패널 열기' : '패널 닫기'}
                        >
                            {rightCollapsed
                                ? <ChevronLeft  size={16} strokeWidth={1.5} />
                                : <ChevronRight size={16} strokeWidth={1.5} />}
                        </button>
                    </div>
                    <div className="classroom__aside-scroll">
                        {/* Metacognition card */}
                        {!metacogComplete ? (
                            <div className="metacog-card">
                                <div
                                    className="metacog-card__title"
                                    style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                                >
                                    <BrainCircuit size={16} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                                    메타인지 확인
                                </div>
                                <p className="metacog-card__desc">
                                    방금 배운 내용을 AI에게 소리 내어 설명해보세요. 이해도를 실시간 분석해 드립니다.
                                </p>
                                <button
                                    className="metacog-card__btn"
                                    onClick={() => open('reverse-learning', { type: 'reverse-learning', conceptName: 'ACID' })}
                                >
                                    AI에게 설명하기
                                </button>
                            </div>
                        ) : (
                            <div className="metacog-card metacog-card--complete">
                                <div
                                    className="metacog-card__title"
                                    style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                                >
                                    <CheckCircle2 size={16} strokeWidth={1.5} />
                                    메타인지 평가 완료
                                </div>
                                <div className="metacog-card__score">이해도 95%</div>
                                <div className="metacog-card__keywords">
                                    <span className="metacog-card__kw metacog-card__kw--strong">원자성</span>
                                    <span className="metacog-card__kw metacog-card__kw--strong">COMMIT</span>
                                    <span className="metacog-card__kw metacog-card__kw--weak">격리성</span>
                                    <span className="metacog-card__kw metacog-card__kw--weak">데드락</span>
                                </div>
                            </div>
                        )}

                        {/* Weekly Final Quiz */}
                        <div className={`weekly-quiz-card${metacogComplete ? ' weekly-quiz-card--active' : ' weekly-quiz-card--locked'}`}>
                            <div
                                className="weekly-quiz-card__title"
                                style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                            >
                                {metacogComplete
                                    ? <Trophy size={16} strokeWidth={1.5} />
                                    : <Lock   size={16} strokeWidth={1.5} />}
                                주간 최종 퀴즈
                            </div>
                            <p className="weekly-quiz-card__desc">
                                {metacogComplete
                                    ? 'Week 1 전체 내용 최종 평가! 도전해보세요.'
                                    : '메타인지 평가를 완료하면 잠금이 해제됩니다.'}
                            </p>
                            {metacogComplete && (
                                <button
                                    className="weekly-quiz-card__btn"
                                    style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                                    onClick={() => open('quiz-pass')}
                                >
                                    퀴즈 도전하기
                                    <ArrowRight size={13} strokeWidth={1.5} />
                                </button>
                            )}
                        </div>

                        {/* Connected Study Partner */}
                        {partnerConnected && (
                            <div className="partner-widget">
                                <div
                                    className="partner-widget__title"
                                    style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                                >
                                    <Users size={15} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                                    연결된 스터디 파트너
                                </div>
                                <div className="partner-widget__profile">
                                    <div className="partner-widget__avatar">
                                        <UserCircle size={32} strokeWidth={1.5} style={{ color: 'var(--color-purple-400)' }} />
                                    </div>
                                    <div className="partner-widget__info">
                                        <div className="partner-widget__name">김지현</div>
                                        <div className="partner-widget__role">멘토</div>
                                    </div>
                                    <div className="partner-widget__match-badge">98% 매칭</div>
                                </div>
                                <button
                                    className="partner-widget__msg-btn"
                                    style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                                >
                                    <MessageCircle size={14} strokeWidth={1.5} />
                                    메시지 보내기
                                </button>
                            </div>
                        )}
                    </div>
                </aside>
            </div>

            {/* ── Modals + debug panel ── */}
            <DebugEventController />
            <ClassroomModals />
        </>
    )
}

// ── Default export: wraps inner component with the global modal provider ──────
export default function StudyClassroom() {
    return (
        <ClassroomModalProvider>
            <StudyClassroomContent />
        </ClassroomModalProvider>
    )
}
