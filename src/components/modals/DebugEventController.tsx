import { useState } from 'react'
import type { ReactNode } from 'react'
import { Bug, RotateCcw, Rocket, Brain, Settings, ChevronDown, Loader2, CheckCircle2, XCircle } from 'lucide-react'
import '../../styles/DebugEventController.css'
import { useClassroomModal } from '../../context/ClassroomModalContext'
import { fetchAISummary } from '../../services/aiSummaryService'
import type { PatternType } from '../../types/aiEvents'

// ── Scenario definitions ──────────────────────────────────────────────────────

type LoadingKey = PatternType | 'REVERSE_LEARNING' | null

interface ScenarioDef {
    key:        PatternType
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
        apiHint:    'POST /api/ai/analyze  { "patternType": "REWIND" }',
        colorClass: 'debug-controller__btn--rewind',
    },
    {
        key:        'FAST_TRACK',
        icon:       <Rocket size={16} strokeWidth={2} />,
        label:      'Fast Learning Detected',
        sublabel:   '평균 대비 빠른 완료',
        apiHint:    'POST /api/ai/analyze  { "patternType": "FAST_TRACK" }',
        colorClass: 'debug-controller__btn--fast',
    },
]

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * Development-only floating panel for manually triggering AI learning-pattern events.
 * Toggle the panel body with the collapse button in the header.
 *
 * Full flow per button:
 *   1. Call fetchAISummary({ patternType })  ← mock POST /api/ai/analyze (1 s delay)
 *   2. Receive PatternAnalysisResponse (shape differs per pattern)
 *   3. context.open() → correct modal renders with live backend data
 *
 * REWIND           → MonitoringModal
 * FAST_TRACK       → FastTrackModal
 * REVERSE_LEARNING → ReverseLearningModal (direct open, no async)
 */
export default function DebugEventController() {
    const { open } = useClassroomModal()
    const [loading,   setLoading]   = useState<LoadingKey>(null)
    const [collapsed, setCollapsed] = useState(false)

    const trigger = async (patternType: PatternType) => {
        if (loading) return
        setLoading(patternType)
        try {
            const response = await fetchAISummary({ patternType })

            if (response.actionType === 'monitoring') {
                open('monitoring', {
                    type:        'monitoring',
                    conceptName: response.conceptName,
                    reason:      response.reason,
                })
            } else {
                open('fast-track', {
                    type:           'fast-track',
                    conceptName:    response.conceptName,
                    reason:         response.reason,
                    completionRate: response.completionRate,
                    challengeLevel: response.challengeLevel,
                })
            }
        } finally {
            setLoading(null)
        }
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
                        Swap service functions for real Axios calls when ready.
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
                            title="POST /api/ai/meta-evaluate  { explanation }"
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
