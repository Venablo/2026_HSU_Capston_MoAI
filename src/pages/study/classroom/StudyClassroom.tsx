/**
 * ============================================================================
 * StudyClassroom.tsx  —  핵심 학습 화면
 * ============================================================================
 *
 * 데이터 로딩 흐름:
 *   1. getCurriculum(roomId) → 전체 주차 목록 조회
 *   2. 진행 중인 첫 번째 주차(completionRate < 100) 선택, 없으면 마지막 주차
 *   3. getCurriculumWeek(roomId, weekId) → 주차 상세 데이터 조회
 *      (topic, description, keywords, resources, mainVideoId, completionRate)
 *
 * 탭별 데이터 (lazy-load — 탭 첫 클릭 시 로드):
 *   docs    → weekData.resources (ResourceItem[])
 *   videos  → getRecommendedVideos(roomId, weekId)
 *   summary → weekData.keywords (string[])
 *   quiz    → getQuizAttempts(roomId, weekId)
 * ============================================================================
 */

import type { ReactNode } from 'react'
import { useState, useEffect, useCallback } from 'react'
import { useParams } from 'react-router-dom'
import {
    Search, Bell, ChevronLeft, ChevronRight,
    FileText, FileEdit, Package,
    PlayCircle,
    ClipboardList, Play, Zap, MessageSquare,
    Calendar, BrainCircuit, CheckCircle2,
    Trophy, Lock, Users, MessageCircle,
    UserCircle, ArrowRight, Loader2,
} from 'lucide-react'
import '../../../styles/StudyClassroom.css'
import '../../../styles/FinalQuizModal.css'
import { ClassroomModalProvider, useClassroomModal } from '../../../context/ClassroomModalContext'
import ClassroomModals from '../../../components/modals/common/ClassroomModals'
import { useYouTubePlayer } from '../../../hooks/useYouTubePlayer'
import { useAuth } from '../../../context/AuthContext'
import {
    sendEventLog,
    getMaterialDetail,
    getInstantQuiz,
    getCurriculum,
    getCurriculumWeek,
    getRecommendedVideos,
    getQuizAttempts,
} from '../../../services/apiService'
import type {
    EventType,
    LearningEventPayload,
    CurriculumWeekDetail,
    RecommendedVideo,
    QuizAttemptListItem,
} from '../../../types/api'

// ── 타입 정의 ─────────────────────────────────────────────────────────────────
type TabKey = 'docs' | 'videos' | 'summary' | 'quiz'
interface Tab { key: TabKey; icon: ReactNode; label: string }

const TABS: Tab[] = [
    { key: 'docs',    icon: <ClipboardList size={14} strokeWidth={1.5} />, label: '주차별 공식 교안' },
    { key: 'videos',  icon: <Play          size={14} strokeWidth={1.5} />, label: 'AI 추천 영상'    },
    { key: 'summary', icon: <Zap           size={14} strokeWidth={1.5} />, label: 'AI 핵심 요약'    },
    { key: 'quiz',    icon: <MessageSquare size={14} strokeWidth={1.5} />, label: '퀴즈 내역'       },
]

// ── 유틸 ──────────────────────────────────────────────────────────────────────
function formatDuration(sec: number): string {
    const m = Math.floor(sec / 60)
    const s = sec % 60
    return `${m}:${s.toString().padStart(2, '0')}`
}

function resourceIcon(type: string): ReactNode {
    if (type === 'pdf')  return <FileText size={20} strokeWidth={1.5} />
    if (type === 'docx') return <FileEdit size={20} strokeWidth={1.5} />
    if (type === 'zip')  return <Package  size={20} strokeWidth={1.5} />
    return <FileText size={20} strokeWidth={1.5} />
}

function TabLoading() {
    return (
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '24px 0', color: 'var(--color-text-secondary)' }}>
            <Loader2 size={16} strokeWidth={1.5} className="animate-spin" />
            <span style={{ fontSize: '13px' }}>불러오는 중...</span>
        </div>
    )
}

function TabEmpty({ message }: { message: string }) {
    return (
        <p style={{ padding: '24px 0', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
            {message}
        </p>
    )
}

// ── 핵심 컴포넌트 (ClassroomModalProvider 내부에서 렌더링) ─────────────────────
function StudyClassroomContent() {
    const { studyId: roomId = '' } = useParams<{ studyId: string }>()

    const [rightCollapsed, setRightCollapsed] = useState(false)
    const [tab, setTab] = useState<TabKey>('docs')

    // 주차 데이터
    const [weekData,    setWeekData]    = useState<CurriculumWeekDetail | null>(null)
    const [weekLoading, setWeekLoading] = useState(true)
    const [weekError,   setWeekError]   = useState<string | null>(null)

    // 탭별 데이터 (lazy-load)
    const [videos,        setVideos]        = useState<RecommendedVideo[] | null>(null)
    const [videosLoading, setVideosLoading] = useState(false)
    const [quizAttempts,  setQuizAttempts]  = useState<QuizAttemptListItem[] | null>(null)
    const [quizLoading,   setQuizLoading]   = useState(false)

    const { open, metacogComplete, partnerConnected, setCurrentWeekId } = useClassroomModal()
    const { nickname } = useAuth()
    const avatarChar = nickname ? nickname.charAt(0).toUpperCase() : '?'

    // ── 주차 데이터 로드 ─────────────────────────────────────────────────────
    useEffect(() => {
        if (!roomId) {
            setWeekError('학습실 ID가 없습니다.')
            setWeekLoading(false)
            return
        }
        setWeekLoading(true)
        setWeekError(null)
        getCurriculum(roomId)
            .then(weeks => {
                if (!weeks.length) throw new Error('이 학습실에 커리큘럼이 없습니다.')
                // 진행 중인 첫 번째 주차 선택, 없으면 마지막 주차
                const active = weeks.find(w => w.completionRate < 100) ?? weeks[weeks.length - 1]
                return getCurriculumWeek(roomId, active.weekId)
            })
            .then(detail => {
                setWeekData(detail)
                setCurrentWeekId(detail.weekId)
            })
            .catch(e => setWeekError(e instanceof Error ? e.message : '주차 데이터를 불러오지 못했습니다.'))
            .finally(() => setWeekLoading(false))
    }, [roomId, setCurrentWeekId])

    // ── 영상 탭: 첫 클릭 시 lazy-load ────────────────────────────────────────
    useEffect(() => {
        if (tab !== 'videos' || !roomId || !weekData || videos !== null) return
        setVideosLoading(true)
        getRecommendedVideos(roomId, weekData.weekId)
            .then(setVideos)
            .catch(() => setVideos([]))
            .finally(() => setVideosLoading(false))
    }, [tab, roomId, weekData, videos])

    // ── 퀴즈 탭: 첫 클릭 시 lazy-load ────────────────────────────────────────
    useEffect(() => {
        if (tab !== 'quiz' || !roomId || !weekData || quizAttempts !== null) return
        setQuizLoading(true)
        getQuizAttempts(roomId, weekData.weekId)
            .then(setQuizAttempts)
            .catch(() => setQuizAttempts([]))
            .finally(() => setQuizLoading(false))
    }, [tab, roomId, weekData, quizAttempts])

    // ── 패턴 감지 핸들러 ─────────────────────────────────────────────────────
    const handlePatternDetected = useCallback(async (
        eventType: EventType,
        payload: LearningEventPayload,
    ) => {
        if (!weekData) return

        try {
            const result = await sendEventLog(roomId, {
                event_type:    eventType,
                curriculum_id: weekData.weekId,
                payload,
            })

            if (!result.aiTriggered) return

            if (
                result.eventType === 'video_rewind' ||
                result.eventType === 'video_pause'  ||
                result.eventType === 'tab_departure'
            ) {
                if (result.materialId) {
                    const material = await getMaterialDetail(roomId, result.materialId)
                    open('monitoring', {
                        type:        'monitoring',
                        conceptName: material.title,
                        reason:      `${eventType} 패턴 감지 — 보충 자료를 준비했어요.`,
                        summaryItems: material.summaryItems.map(s => ({
                            letter:      s.label,
                            title:       s.title,
                            description: s.desc,
                        })),
                    })
                }
            } else if (result.eventType === 'video_skip') {
                const quiz = await getInstantQuiz(roomId, weekData.weekId)
                open('quiz-pass', { type: 'quiz-pass', quiz })
            }
        } catch {
            // pattern detection errors are silent
        }
    }, [roomId, weekData, open])

    // ── YouTube IFrame API 훅 ─────────────────────────────────────────────────
    // weekData가 로드되기 전에는 빈 videoId('')로 초기화.
    // weekData 로드 완료 시 hook 내부에서 플레이어를 재생성한다.
    const { playerDivId } = useYouTubePlayer({
        videoId:           weekData?.mainVideoId ?? '',
        onPatternDetected: handlePatternDetected,
    })

    const progress = weekData?.completionRate ?? 0

    return (
        <>
            {/* ── Topbar ── */}
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
                    <div className="topbar__avatar">{avatarChar}</div>
                </div>
            </header>

            {/* ── Body ── */}
            <div className="classroom">
                {/* ── 메인 패널 ── */}
                <div className="classroom__main">
                    {/* 주차 선택 버튼 */}
                    <button
                        className="classroom__week-btn"
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                    >
                        <Calendar size={14} strokeWidth={1.5} />
                        {weekLoading
                            ? '불러오는 중...'
                            : weekData
                                ? `Week ${weekData.weekNumber}: ${weekData.topic}`
                                : weekError ?? ''
                        }
                        <ChevronRight size={14} strokeWidth={1.5} style={{ transform: 'rotate(90deg)' }} />
                    </button>

                    {/* 진행률 바 */}
                    <div className="classroom__progress-label">
                        COURSE PROGRESS: {progress}%
                    </div>
                    <div className="classroom__progress-track">
                        <div className="classroom__progress-fill" style={{ width: `${progress}%` }} />
                    </div>

                    {/* 강의 정보 */}
                    <h1 className="classroom__lesson-title">
                        {weekLoading ? '...' : weekData?.topic ?? weekError ?? ''}
                    </h1>
                    <p className="classroom__lesson-desc">
                        {weekData?.description ?? ''}
                    </p>

                    {/* ── YouTube IFrame 플레이어 ── */}
                    <div className="classroom__video">
                        {/* 로딩/에러 오버레이 — 플레이어 div는 항상 DOM에 유지 */}
                        {(weekLoading || weekError) && (
                            <div style={{
                                position: 'absolute', inset: 0, display: 'flex',
                                alignItems: 'center', justifyContent: 'center',
                                background: '#0f0f1a', zIndex: 1, borderRadius: '12px',
                            }}>
                                {weekLoading
                                    ? <Loader2 size={32} strokeWidth={1.5} className="animate-spin" style={{ color: 'var(--color-purple-500)' }} />
                                    : <span style={{ color: '#ef4444', fontSize: '13px' }}>⚠ {weekError}</span>
                                }
                            </div>
                        )}
                        <div
                            id={playerDivId}
                            style={{ width: '100%', height: '100%', position: 'absolute', inset: 0 }}
                        />
                    </div>

                    {/* 탭 */}
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

                    {/* 탭: 교안 (주차 resources) */}
                    {tab === 'docs' && (
                        <div className="classroom__doc-list">
                            {weekLoading ? <TabLoading /> : !weekData ? null :
                            weekData.resources.length === 0
                                ? <TabEmpty message="이 주차에 첨부된 교안이 없습니다." />
                                : weekData.resources.map((res, i) => (
                                    <div key={i} className="classroom__doc-item">
                                        <div className="classroom__doc-icon" style={{ color: 'var(--color-purple-500)' }}>
                                            {resourceIcon(res.type)}
                                        </div>
                                        <div className="classroom__doc-info">
                                            <div className="classroom__doc-name">{res.title}</div>
                                            <div className="classroom__doc-meta">{res.size} · {res.type.toUpperCase()}</div>
                                        </div>
                                        <a
                                            href={res.url}
                                            className="classroom__doc-download"
                                            target="_blank"
                                            rel="noreferrer"
                                        >
                                            다운로드
                                        </a>
                                    </div>
                                ))
                            }
                        </div>
                    )}

                    {/* 탭: AI 추천 영상 */}
                    {tab === 'videos' && (
                        <div className="classroom__video-grid">
                            {videosLoading ? <TabLoading /> :
                            videos === null ? null :
                            videos.length === 0
                                ? <TabEmpty message="추천 영상이 없습니다." />
                                : videos.map((v, i) => (
                                    <div key={i} className="classroom__video-card">
                                        <div className="classroom__video-thumb">
                                            <div className="classroom__video-thumb-icon" style={{ color: 'rgba(255,255,255,0.8)' }}>
                                                <PlayCircle size={28} strokeWidth={1.5} />
                                            </div>
                                            <div className="classroom__video-thumb-duration">
                                                {formatDuration(v.durationSec)}
                                            </div>
                                        </div>
                                        <div className="classroom__video-meta">
                                            <div className="classroom__video-title">{v.title}</div>
                                            <div className="classroom__video-channel">
                                                조회수 {v.viewCount.toLocaleString()}회
                                            </div>
                                        </div>
                                    </div>
                                ))
                            }
                        </div>
                    )}

                    {/* 탭: AI 핵심 요약 (주차 keywords) */}
                    {tab === 'summary' && (
                        <div className="classroom__summary">
                            <div
                                className="classroom__summary-heading"
                                style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                            >
                                <Zap size={16} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                                핵심 키워드
                            </div>
                            {weekLoading ? <TabLoading /> :
                            !weekData?.keywords.length
                                ? <TabEmpty message="키워드 데이터가 없습니다." />
                                : weekData.keywords.map((kw, i) => (
                                    <div key={i} className="classroom__summary-row">
                                        <div className="classroom__summary-dot" />
                                        <div className="classroom__summary-text">{kw}</div>
                                    </div>
                                ))
                            }
                        </div>
                    )}

                    {/* 탭: 퀴즈 내역 */}
                    {tab === 'quiz' && (
                        <div className="classroom__quiz-history">
                            <div
                                className="classroom__quiz-history-title"
                                style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                            >
                                <MessageSquare size={16} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                                퀴즈 내역
                            </div>
                            {quizLoading ? <TabLoading /> : (() => {
                                // Defensive: treat non-array (wrapped response) as empty
                                const list = Array.isArray(quizAttempts) ? quizAttempts : []
                                if (quizAttempts === null) return null
                                if (list.length === 0) return (
                                    <div style={{ padding: '24px 0' }}>
                                        <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', marginBottom: '12px' }}>
                                            아직 퀴즈 내역이 없습니다.
                                        </p>
                                        <button
                                            onClick={() => setTab('docs')}
                                            style={{
                                                fontSize: '12px', padding: '6px 14px',
                                                borderRadius: '6px', border: '1px solid var(--color-purple-300)',
                                                background: 'transparent', color: 'var(--color-purple-600)',
                                                cursor: 'pointer',
                                            }}
                                        >
                                            ← 학습으로 돌아가기
                                        </button>
                                    </div>
                                )
                                return list.map((item, i) => (
                                    <div key={item.attemptId} className="classroom__quiz-row">
                                        <div className="classroom__quiz-q">Q{i + 1}. {item.questionTitle}</div>
                                        <div className="classroom__quiz-result-row">
                                            <span className={`classroom__quiz-badge ${item.isCorrect ? 'classroom__quiz-badge--correct' : 'classroom__quiz-badge--wrong'}`}>
                                                {item.isCorrect ? '정답' : '오답'}
                                            </span>
                                        </div>
                                    </div>
                                ))
                            })()}
                        </div>
                    )}
                </div>

                {/* ── 우측 사이드 패널 ── */}
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
                        {/* 메타인지 확인 카드 */}
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
                                    onClick={() => open('reverse-learning', {
                                        type:        'reverse-learning',
                                        conceptName: weekData?.topic ?? '',
                                        roomId,
                                        weekId:      weekData?.weekId,
                                    })}
                                    disabled={!weekData}
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
                                <p className="metacog-card__desc" style={{ margin: '4px 0 0' }}>
                                    거꾸로 학습이 완료되었습니다.
                                </p>
                            </div>
                        )}

                        {/* 주간 파이널 퀴즈 카드 */}
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
                                    ? `Week ${weekData?.weekNumber ?? ''} 전체 내용 최종 평가! 도전해보세요.`
                                    : '메타인지 평가를 완료하면 잠금이 해제됩니다.'}
                            </p>
                            {metacogComplete && weekData && (
                                <button
                                    className="weekly-quiz-card__btn"
                                    style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                                    onClick={() => open('final-quiz', {
                                        type:   'final-quiz',
                                        roomId,
                                        weekId: weekData.weekId,
                                    })}
                                >
                                    퀴즈 도전하기
                                    <ArrowRight size={13} strokeWidth={1.5} />
                                </button>
                            )}
                        </div>

                        {/* 연결된 스터디 파트너 */}
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
                                        <div className="partner-widget__name">파트너 연결됨</div>
                                        <div className="partner-widget__role">스터디 매칭</div>
                                    </div>
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

            {/* ── 모달 ── */}
            <ClassroomModals />
        </>
    )
}

// ── 기본 export: ClassroomModalProvider로 내부 컴포넌트를 감싼다 ────────────────
export default function StudyClassroom() {
    return (
        <ClassroomModalProvider>
            <StudyClassroomContent />
        </ClassroomModalProvider>
    )
}
