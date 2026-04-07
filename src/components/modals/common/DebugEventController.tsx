import { useState } from 'react'
import type { ReactNode } from 'react'
import { Bug, RotateCcw, Rocket, Brain, Settings, ChevronDown, Loader2, CheckCircle2, XCircle } from 'lucide-react'
import '../../../styles/DebugEventController.css'
import { useClassroomModal } from '../../../context/ClassroomModalContext'

// ── Scenario definitions ──────────────────────────────────────────────────────

type ScenarioKey = 'REWIND' | 'FAST_TRACK'
type LoadingKey  = ScenarioKey | 'REVERSE_LEARNING' | null

interface ScenarioDef {
    key:        ScenarioKey
    icon:       ReactNode
    label:      string
    sublabel:   string
    apiHint:    string
    colorClass: string
}

const SCENARIOS: ScenarioDef[] = [
    {
        key:        'REWIND',
        icon:       <RotateCcw size={16} strokeWidth={2} />,
        label:      'Rewind Pattern Detected',
        sublabel:   '되감기 3회 이상',
        apiHint:    'POST /api/learning-rooms/{roomId}/events  { "event_type": "video_rewind" }',
        colorClass: 'debug-controller__btn--rewind',
    },
    {
        key:        'FAST_TRACK',
        icon:       <Rocket size={16} strokeWidth={2} />,
        label:      'Fast Learning Detected',
        sublabel:   '평균 대비 빠른 완료',
        apiHint:    'POST /api/learning-rooms/{roomId}/events  { "event_type": "video_skip" }',
        colorClass: 'debug-controller__btn--fast',
    },
]

// ── Inline mock data (matches real API response shapes) ───────────────────────

/**
 * Mirrors MaterialDetail.summaryItems mapped to SummaryItem format.
 * In production these come from getMaterialDetail(roomId, materialId).
 * Field mapping: { label→letter, desc→description }
 */
const MOCK_ACID_SUMMARY_ITEMS = [
    { letter: 'A', title: 'Atomicity (원자성)',   description: '트랜잭션은 완전히 수행되거나 전혀 수행되지 않아야 합니다.' },
    { letter: 'C', title: 'Consistency (일관성)', description: '시스템의 고정 요소는 트랜잭션 수행 전후에 같아야 합니다.' },
    { letter: 'I', title: 'Isolation (고립성)',   description: '트랜잭션 실행 중에는 다른 트랜잭션이 끼어들 수 없습니다.' },
    { letter: 'D', title: 'Durability (지속성)',  description: '성공적으로 완료된 트랜잭션의 결과는 영구적으로 반영됩니다.' },
]

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * Development-only floating panel for manually triggering AI learning-pattern events.
 * Toggle the panel body with the collapse button in the header.
 *
 * Full flow per button (real API shape):
 *   REWIND      → simulates sendEventLog(video_rewind) aiTriggered:true
 *                  → getMaterialDetail() → open MonitoringModal (with summaryItems)
 *   FAST_TRACK  → simulates sendEventLog(video_skip)   aiTriggered:true
 *                  → open FastTrackModal
 *   REVERSE_LEARNING → direct open ReverseLearningModal (no async)
 *
 * Uses inline mock data with a 1s simulated delay to reproduce the real UX.
 * Swap the trigger() body for real sendEventLog() calls when the backend is ready.
 */
export default function DebugEventController() {
    const { open } = useClassroomModal()
    const [loading,   setLoading]   = useState<LoadingKey>(null)
    const [collapsed, setCollapsed] = useState(false)

    const trigger = (key: ScenarioKey) => {
        if (loading) return
        setLoading(key)

        // Simulate 1 s network round-trip (replace with real sendEventLog call)
        setTimeout(() => {
            if (key === 'REWIND') {
                open('monitoring', {
                    type:        'monitoring',
                    conceptName: 'ACID',
                    reason:      'video_rewind 패턴 감지 — 해당 개념 보충 자료를 추천합니다.',
                    summaryItems: MOCK_ACID_SUMMARY_ITEMS,
                })
            } else {
                open('fast-track', {
                    type:           'fast-track',
                    conceptName:    'ACID',
                    reason:         '평균 대비 빠른 구간 완료 감지 — 심화 학습을 추천합니다.',
                    completionRate: 240,
                    challengeLevel: 'advanced',
                })
            }
            setLoading(null)
        }, 1000)
    }

    const triggerReverseLeaning = () => {
        if (loading) return
        open('reverse-learning', {
            type:        'reverse-learning',
            conceptName: 'ACID',
        })
    }

    const triggerQuizCorrect = () => {
        if (loading) return
        open('quiz-correct', { type: 'quiz-correct', conceptName: 'ACID' })
    }

    const triggerQuizIncorrect = () => {
        if (loading) return
        open('quiz-incorrect', {
            type:           'quiz-incorrect',
            conceptName:    'ACID',
            correctConcept: '원자성 (Atomicity)',
            explanation:    '트랜잭션 내의 모든 연산은 완전히 실행되거나 전혀 실행되지 않아야 합니다. 일부만 반영되는 부분 커밋은 허용되지 않으며, 오류 발생 시 ROLLBACK을 통해 원래 상태로 완전히 복구됩니다.',
        })
    }

    const activeHint = loading && loading !== 'REVERSE_LEARNING'
        ? SCENARIOS.find(s => s.key === loading)?.apiHint ?? ''
        : collapsed
            ? ''
            : 'hover a button to see the API shape'

    return (
        <div className={`debug-controller${collapsed ? ' debug-controller--collapsed' : ''}`}>
            <div className="debug-controller__header">
                {!collapsed && (
                    <>
                        <span className="debug-controller__badge">
                            <Bug size={11} strokeWidth={2.5} />
                            DEBUG
                        </span>
                        <span className="debug-controller__title">Event Simulator</span>
                    </>
                )}
                <button
                    className="debug-controller__collapse-btn"
                    onClick={() => setCollapsed(c => !c)}
                    title={collapsed ? 'Expand panel' : 'Minimize panel'}
                >
                    {collapsed ? <Settings size={16} strokeWidth={2} /> : <ChevronDown size={14} strokeWidth={2} />}
                </button>
            </div>

            {!collapsed && (
                <>
                    <p className="debug-controller__desc">
                        Triggers mock backend calls (1 s delay).<br />
                        Swap trigger() body for real sendEventLog() when ready.
                    </p>

                    <div className="debug-controller__btn-list">
                        {SCENARIOS.map(s => {
                            const isActive = loading === s.key
                            return (
                                <button
                                    key={s.key}
                                    className={[
                                        'debug-controller__btn',
                                        s.colorClass,
                                        isActive ? 'debug-controller__btn--loading' : '',
                                    ].join(' ')}
                                    onClick={() => trigger(s.key)}
                                    disabled={loading !== null}
                                    title={s.apiHint}
                                >
                                    <span className="debug-controller__btn-icon">
                                        {isActive
                                            ? <Loader2 size={16} strokeWidth={2} className="debug-controller__spinner" />
                                            : s.icon}
                                    </span>
                                    <span className="debug-controller__btn-text">
                                        <span className="debug-controller__btn-label">
                                            {isActive ? 'Analyzing...' : s.label}
                                        </span>
                                        <span className="debug-controller__btn-sublabel">
                                            {isActive ? 'awaiting mock response' : s.sublabel}
                                        </span>
                                    </span>
                                </button>
                            )
                        })}

                        {/* Direct flow triggers — no async fetch needed */}
                        <button
                            className="debug-controller__btn debug-controller__btn--reverse"
                            onClick={triggerReverseLeaning}
                            disabled={loading !== null}
                            title="POST /api/learning-rooms/{roomId}/flipped/start"
                        >
                            <span className="debug-controller__btn-icon">
                                <Brain size={16} strokeWidth={2} />
                            </span>
                            <span className="debug-controller__btn-text">
                                <span className="debug-controller__btn-label">Reverse Learning</span>
                                <span className="debug-controller__btn-sublabel">역방향 학습 3-step flow</span>
                            </span>
                        </button>

                        <button
                            className="debug-controller__btn debug-controller__btn--quiz-correct"
                            onClick={triggerQuizCorrect}
                            disabled={loading !== null}
                            title="Direct: open QuizCorrectModal"
                        >
                            <span className="debug-controller__btn-icon">
                                <CheckCircle2 size={16} strokeWidth={2} />
                            </span>
                            <span className="debug-controller__btn-text">
                                <span className="debug-controller__btn-label">Quiz Correct</span>
                                <span className="debug-controller__btn-sublabel">정답 결과 모달</span>
                            </span>
                        </button>

                        <button
                            className="debug-controller__btn debug-controller__btn--quiz-wrong"
                            onClick={triggerQuizIncorrect}
                            disabled={loading !== null}
                            title="Direct: open QuizIncorrectModal"
                        >
                            <span className="debug-controller__btn-icon">
                                <XCircle size={16} strokeWidth={2} />
                            </span>
                            <span className="debug-controller__btn-text">
                                <span className="debug-controller__btn-label">Quiz Incorrect</span>
                                <span className="debug-controller__btn-sublabel">오답 결과 모달</span>
                            </span>
                        </button>
                    </div>

                    <p className="debug-controller__api-hint">{activeHint}</p>
                </>
            )}
        </div>
    )
}
