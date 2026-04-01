import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    X, Check, Calendar, Zap, Sparkles, Bot, BookOpen,
    Lightbulb, Lock, ArrowLeft, ArrowRight, Loader2,
} from 'lucide-react'
import '../styles/OnboardingWizard.css'

interface Props { onClose: () => void }

const GOALS       = ['#컴공', '#정보처리기사', '#웹개발', '#CS면접', '#토익']
const LEVELS      = ['입문 (개념부터 차근차근)', '초급 (기본기는 있어요)', '중급 (심화 내용 중심으로)', '고급 (실전 위주로)']
const DURATIONS   = ['4주', '8주', '10주', '12주', '16주']
const INTENSITIES = ['하루 1시간', '하루 2시간', '하루 3시간', '하루 4시간+']
const LEVEL_CHARS = ['입', '초', '중', '고']

export default function OnboardingWizard({ onClose }: Props) {
    const navigate    = useNavigate()
    const [step, setStep]             = useState(1)
    const [goal, setGoal]             = useState('#정보처리기사')
    const [customGoal, setCustomGoal] = useState('')
    const [level, setLevel]           = useState(1)
    const [duration, setDuration]     = useState('10주')
    const [intensity, setIntensity]   = useState('하루 3시간')
    const [creating, setCreating]     = useState(false)

    const progress = (step / 4) * 100

    const handleFinish = () => {
        setCreating(true)
        setTimeout(() => { onClose(); navigate('/study/1/classroom') }, 1500)
    }

    return (
        <div className="wizard-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
            <div className="wizard animate-scale-in">

                {/* Header */}
                <div className="wizard__header">
                    <div className="wizard__step-row">
                        <span className="wizard__step-label">STEP {step} OF 4</span>
                        <button className="wizard__close" onClick={onClose}>
                            <X size={16} strokeWidth={2} />
                        </button>
                    </div>
                    <div className="wizard__progress-track">
                        <div className="wizard__progress-fill" style={{ width: `${progress}%` }} />
                    </div>
                    <div className="wizard__progress-pct">{Math.round(progress)}%</div>
                </div>

                {/* Body */}
                <div className="wizard__body">

                    {/* Step 1 – Goal */}
                    {step === 1 && (
                        <div className="animate-fade-in">
                            <h2 className="wizard__title">어떤 분야의 성장을<br />목표로 하시나요?</h2>
                            <p className="wizard__subtitle">AI가 목표에 맞는 최적의 커리큘럼을 설계합니다</p>
                            <div className="wizard__chips">
                                {GOALS.map(g => (
                                    <button
                                        key={g}
                                        className={`wizard__chip ${goal === g ? 'wizard__chip--active' : ''}`}
                                        onClick={() => setGoal(g)}
                                    >
                                        {g}
                                        {goal === g && (
                                            <span className="wizard__chip-check">
                                                <Check size={11} strokeWidth={2.5} />
                                            </span>
                                        )}
                                    </button>
                                ))}
                            </div>
                            <div className="wizard__custom-label">또는 직접 입력하기</div>
                            <input
                                className="wizard__input"
                                placeholder="원하는 목표를 자유롭게 적어보세요"
                                value={customGoal}
                                onChange={e => setCustomGoal(e.target.value)}
                            />
                        </div>
                    )}

                    {/* Step 2 – Level */}
                    {step === 2 && (
                        <div className="animate-fade-in">
                            <h2 className="wizard__title">현재 실력이 어느 정도인가요?</h2>
                            <p className="wizard__subtitle">AI가 맞춤 난이도로 커리큘럼을 조정합니다</p>
                            <div className="wizard__levels">
                                {LEVELS.map((l, i) => (
                                    <button
                                        key={i}
                                        className={`wizard__level-btn ${level === i ? 'wizard__level-btn--active' : ''}`}
                                        onClick={() => setLevel(i)}
                                    >
                                        <div className="wizard__level-badge">{LEVEL_CHARS[i]}</div>
                                        <span className="wizard__level-label">{l}</span>
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Step 3 – Duration & Intensity */}
                    {step === 3 && (
                        <div className="animate-fade-in">
                            <h2 className="wizard__title">목표 기간과 학습 강도를<br />설정하세요</h2>
                            <p className="wizard__subtitle">AI가 일정에 맞게 주차별 커리큘럼을 배분합니다</p>

                            <div className="wizard__section-label" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                <Calendar size={15} strokeWidth={1.5} />
                                목표 기간
                            </div>
                            <div className="wizard__duration-row">
                                {DURATIONS.map(d => (
                                    <button
                                        key={d}
                                        className={`wizard__duration-btn ${duration === d ? 'wizard__duration-btn--active' : ''}`}
                                        onClick={() => setDuration(d)}
                                    >
                                        {d}
                                    </button>
                                ))}
                            </div>

                            <div className="wizard__section-label" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                <Zap size={15} strokeWidth={1.5} />
                                일일 학습 강도
                            </div>
                            <div className="wizard__intensity-list">
                                {INTENSITIES.map(iv => (
                                    <button
                                        key={iv}
                                        className={`wizard__intensity-btn ${intensity === iv ? 'wizard__intensity-btn--active' : ''}`}
                                        onClick={() => setIntensity(iv)}
                                    >
                                        {iv}
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Step 4 – Plan summary */}
                    {step === 4 && (
                        <div className="animate-fade-in">
                            <div className="wizard__plan-emoji" style={{ color: 'var(--color-purple-500)' }}>
                                <Sparkles size={40} strokeWidth={1.5} />
                            </div>
                            <p className="wizard__plan-title">
                                {goal.replace('#', '')}({LEVEL_CHARS[level]}급) 과정을
                            </p>
                            <p className="wizard__plan-desc">
                                <span className="wizard__plan-highlight">{duration}</span> 동안 매일{' '}
                                <span className="wizard__plan-highlight">{intensity.replace('하루 ', '')}</span>씩 진행하는<br />
                                맞춤 플랜이 준비되었습니다.
                            </p>

                            <div className="wizard__plan-info">
                                <p style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                                    <Bot size={15} strokeWidth={1.5} style={{ flexShrink: 0, marginTop: '2px' }} />
                                    AI가 데이터를 분석하여 주차별 맞춤 커리큘럼을 즉시 설계합니다.
                                </p>
                                <p style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                                    <BookOpen size={15} strokeWidth={1.5} style={{ flexShrink: 0, marginTop: '2px' }} />
                                    각 주차별로 최적화된 학습 자료와 YouTube 강의가 추천됩니다.
                                </p>
                            </div>

                            <div className="wizard__roadmap-label" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                <Lightbulb size={15} strokeWidth={1.5} />
                                AI 맞춤 커리큘럼 로드맵 (생성 대기 중)
                            </div>

                            {[
                                { weekLabel: 'Week 1', text: 'AI가 첫 주차 목표를 설계할 예정입니다', locked: false, active: true },
                                { weekLabel: 'Week 2', text: '',  locked: true, active: false },
                                { weekLabel: 'Week 3', text: '',  locked: true, active: false },
                            ].map((row, i) => (
                                <div key={i} className="wizard__roadmap-row">
                                    <div
                                        className="wizard__roadmap-dot"
                                        style={{ background: row.active ? 'var(--color-purple-600)' : '#d1d5db' }}
                                    />
                                    <div
                                        className="wizard__roadmap-item"
                                        style={{
                                            background: row.active ? '#ffffff' : '#f9fafb',
                                            color: row.active ? '#374151' : '#9ca3af',
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: '6px',
                                        }}
                                    >
                                        <span>
                                            {row.active ? `${row.weekLabel}: ${row.text}` : row.weekLabel}
                                        </span>
                                        {row.locked && <Lock size={12} strokeWidth={1.5} style={{ flexShrink: 0 }} />}
                                        {row.active && <Sparkles size={12} strokeWidth={1.5} style={{ flexShrink: 0, color: 'var(--color-purple-500)' }} />}
                                        {row.active && (
                                            <div style={{ flex: 1 }}>
                                                <div className="wizard__roadmap-bar-track">
                                                    <div className="wizard__roadmap-bar-fill" />
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                </div>
                            ))}
                            <p className="wizard__roadmap-hint">
                                하단의 개설하기 버튼을 누르면, {duration}간의 커리큘럼이 당신만을 위해 채워집니다!
                            </p>
                        </div>
                    )}

                    {/* Footer */}
                    <div className="wizard__footer">
                        {step > 1 ? (
                            <button
                                className="wizard__back-btn"
                                style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                                onClick={() => setStep(s => s - 1)}
                            >
                                <ArrowLeft size={14} strokeWidth={1.5} />
                                이전으로
                            </button>
                        ) : <div />}

                        {step < 4 ? (
                            <button
                                className="wizard__next-btn"
                                style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                                onClick={() => setStep(s => s + 1)}
                            >
                                다음으로
                                <ArrowRight size={14} strokeWidth={1.5} />
                            </button>
                        ) : (
                            <button
                                className="wizard__finish-btn"
                                style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                                onClick={handleFinish}
                                disabled={creating}
                            >
                                {creating ? (
                                    <><Loader2 size={14} strokeWidth={1.5} className="animate-spin" /> 학습실 생성 중...</>
                                ) : (
                                    <><Sparkles size={14} strokeWidth={1.5} /> 이대로 학습실 개설하기</>
                                )}
                            </button>
                        )}
                    </div>
                </div>
            </div>
        </div>
    )
}
