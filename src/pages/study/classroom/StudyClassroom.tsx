/**
 * ============================================================================
 * StudyClassroom.tsx  —  핵심 학습 화면
 * ============================================================================
 *
 * 주요 변경 사항:
 *   1. YouTube IFrame API 통합
 *      - useYouTubePlayer 훅으로 영상 재생 + 사용자 행동 폴링
 *      - 되감기/스킵/장시간 정지/탭 이탈 패턴 자동 감지
 *
 *   2. 실제 API 연동 (sendEventLog)
 *      - 패턴 감지 시 POST /learning-rooms/{roomId}/events 호출
 *      - aiTriggered: true 시 응답에 따라 모달 자동 오픈
 *
 *   3. 커리큘럼 데이터 로드
 *      - URL의 :studyId를 roomId로 사용
 *      - getCurriculumWeek()로 주차별 mainVideoId, keywords 조회
 *
 *   4. 파이널 퀴즈 연결
 *      - "퀴즈 도전하기" → 돌발 퀴즈(quiz-pass) 대신 파이널 퀴즈(final-quiz) 모달 오픈
 * ============================================================================
 */

import type { ReactNode } from 'react'
import { useState, useEffect, useCallback } from 'react'
import { useParams } from 'react-router-dom'
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
import '../../../styles/FinalQuizModal.css'
import { ClassroomModalProvider, useClassroomModal } from '../../../context/ClassroomModalContext'
import ClassroomModals from '../../../components/modals/common/ClassroomModals'
import DebugEventController from '../../../components/modals/common/DebugEventController'
import DebugToast from '../../../components/modals/common/DebugToast'
import { useYouTubePlayer } from '../../../hooks/useYouTubePlayer'
import { useDebugToast } from '../../../hooks/useDebugToast'
import {
    sendEventLog,
    getMaterialDetail,
    getInstantQuiz,
} from '../../../services/apiService'
import type { EventType, LearningEventPayload } from '../../../types/api'

// ── 타입 정의 ─────────────────────────────────────────────────────────────────
type TabKey = 'docs' | 'videos' | 'summary' | 'quiz'

interface Doc   { icon: ReactNode; name: string; size: string; type: string }
interface Vid   { thumb: ReactNode; title: string; channel: string; views: string; duration: string }
interface Tab   { key: TabKey; icon: ReactNode; label: string }

// ── Mock 정적 데이터 (백엔드 연결 전 화면 구성용) ─────────────────────────────
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

// ── Mock 주차 데이터 (getCurriculumWeek 응답 형식과 동일) ─────────────────────
// 실제 구현 시 getCurriculumWeek(roomId, weekId)로 교체한다.
const MOCK_WEEK = {
    weekId:         'w3000000-0000-0000-0000-000000000001',
    weekNumber:     1,
    topic:          'DB Foundation',
    description:    'DB의 기본 구조와 ACID 원리를 마스터합니다.',
    completionRate: 30,
    keywords:       ['ACID', '트랜잭션', 'COMMIT', 'ROLLBACK', '원자성'],
    // 실제 YouTube 영상 ID — 백엔드 연결 전 테스트용 공개 DB 강의
    mainVideoId:    'HXV3zeQKqGY',
}

// ── 핵심 컴포넌트 (ClassroomModalProvider 내부에서 렌더링) ─────────────────────
function StudyClassroomContent() {
    // URL 파라미터에서 roomId(=studyId) 추출
    // 라우트: /study/:studyId/classroom
    const { studyId: roomId = 'mock-room' } = useParams<{ studyId: string }>()

    const [rightCollapsed, setRightCollapsed] = useState(false)
    const [tab, setTab] = useState<TabKey>('docs')

    // 현재 주차 데이터 — 실제 구현 시 getCurriculumWeek로 대체
    const [weekData] = useState(MOCK_WEEK)

    const { open, metacogComplete, partnerConnected, setCurrentWeekId } = useClassroomModal()
    const { toasts, addToast, resolveToast } = useDebugToast()

    // 학습실 진입 시 weekId를 Context에 저장하여 모달들이 참조할 수 있게 함
    useEffect(() => {
        setCurrentWeekId(weekData.weekId)
    }, [weekData.weekId, setCurrentWeekId])

    // ── 패턴 감지 핸들러 ─────────────────────────────────────────────────────
    /**
     * useYouTubePlayer 훅이 패턴을 감지할 때마다 이 함수가 호출된다.
     *
     * 처리 흐름:
     *   1. POST /learning-rooms/{roomId}/events 호출
     *   2. aiTriggered: false → 아무것도 하지 않음
     *   3. aiTriggered: true  → eventType에 따라 모달 오픈
     *      - video_rewind / video_pause / tab_departure:
     *          getMaterialDetail(materialId) → MonitoringModal
     *      - video_skip:
     *          getInstantQuiz(weekId) → QuizPassModal
     */
    const handlePatternDetected = useCallback(async (
        eventType: EventType,
        payload: LearningEventPayload,
    ) => {
        // Show blue "Sending" toast immediately when a pattern is detected
        const toastId = addToast(eventType)

        try {
            const result = await sendEventLog(roomId, {
                event_type:    eventType,
                curriculum_id: weekData.weekId,
                payload,
            })

            // Update toast: purple if AI modal will open, dim blue otherwise
            resolveToast(toastId, result.aiTriggered)

            if (!result.aiTriggered) return  // 임계값 미달 → 패턴 미발동

            // ── AI 트리거 발동 → 이벤트 타입별 모달 오픈 ───────────────────
            if (
                result.eventType === 'video_rewind' ||
                result.eventType === 'video_pause'  ||
                result.eventType === 'tab_departure'
            ) {
                // 패턴1/2: materialId로 요약 자료 조회 후 MonitoringModal 표시
                if (result.materialId) {
                    const material = await getMaterialDetail(roomId, result.materialId)
                    open('monitoring', {
                        type:        'monitoring',
                        conceptName: material.title,
                        reason:      `${eventType} 패턴 감지 — 보충 자료를 준비했어요.`,
                        // Map API field names (label/desc) to modal field names (letter/description).
                        // Stored here so SummaryDetailModal opens instantly with no second fetch.
                        summaryItems: material.summaryItems.map(s => ({
                            letter:      s.label,
                            title:       s.title,
                            description: s.desc,
                        })),
                    })
                }
            } else if (result.eventType === 'video_skip') {
                // 패턴3: 스킵 감지 → 돌발 퀴즈 조회 (InstantQuizResponse는 QuizPassModal에서 사용)
                // TODO: getInstantQuiz 응답을 QuizPassModal로 전달하는 로직 추가
                await getInstantQuiz(roomId, weekData.weekId)
                open('quiz-pass')
            }
        } catch {
            // API 미연결 상태(개발 환경)에서는 오류 무시 — DebugEventController로 수동 테스트
            resolveToast(toastId, false)
        }
    }, [roomId, weekData.weekId, open, addToast, resolveToast])

    // ── YouTube IFrame API 훅 연결 ────────────────────────────────────────────
    // playerDivId: <div id={playerDivId} />에 YouTube 플레이어가 마운트됨
    const { playerDivId } = useYouTubePlayer({
        videoId:           weekData.mainVideoId,
        onPatternDetected: handlePatternDetected,
    })

    const progress = weekData.completionRate

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
                    <div className="topbar__avatar">K</div>
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
                        Week {weekData.weekNumber}: {weekData.topic}
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
                        데이터베이스 아키텍처 및 트랜잭션 이해
                    </h1>
                    <p className="classroom__lesson-desc">
                        {weekData.description}
                    </p>

                    {/* ── YouTube IFrame 플레이어 ─────────────────────────────
                     *   useYouTubePlayer가 playerDivId의 div를 IFrame으로 교체한다.
                     *   API가 로드되기 전까지는 빈 div가 표시된다.
                     *
                     *   동작 중인 패턴 감지:
                     *     - 1초 폴링으로 재생 위치 추적 → 되감기/스킵 감지
                     *     - 일시정지 3분 → video_pause 발생
                     *     - visibilitychange → tab_departure 발생
                     * ──────────────────────────────────────────────────────── */}
                    <div className="classroom__video">
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

                    {/* 탭: 문서 */}
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

                    {/* 탭: 영상 */}
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

                    {/* 탭: AI 핵심 요약 */}
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
                                        type:       'reverse-learning',
                                        conceptName: 'ACID',
                                        // roomId와 weekId를 모달에 전달하여 실제 SSE API 호출 가능
                                        roomId,
                                        weekId:     weekData.weekId,
                                    })}
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
                                    ? 'Week 1 전체 내용 최종 평가! 도전해보세요.'
                                    : '메타인지 평가를 완료하면 잠금이 해제됩니다.'}
                            </p>
                            {metacogComplete && (
                                <button
                                    className="weekly-quiz-card__btn"
                                    style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                                    onClick={() => open('final-quiz', {
                                        // 파이널 퀴즈 모달에 roomId, weekId 전달
                                        // → FinalQuizModal이 getFinalQuiz → submitFinalQuiz → 폴링 수행
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

            {/* ── 모달 + 디버그 패널 ── */}
            <DebugEventController />
            <ClassroomModals />
            <DebugToast toasts={toasts} />
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
