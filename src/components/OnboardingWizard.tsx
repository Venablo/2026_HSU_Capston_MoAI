/**
 * ============================================================================
 * OnboardingWizard.tsx  —  온보딩 스텝별 학습실 생성 마법사
 * ============================================================================
 *
 * Step 1~4: 사용자가 목표/난이도/기간/강도를 선택하며 각 스텝 state에 저장
 * Step 4 "이대로 학습실 개설하기" 클릭 시:
 *   → POST /learning-rooms (createLearningRoom)
 *   → 로딩 화면: AI 파이프라인 처리 중 (커리큘럼 생성, 영상 추천 등)
 *   → 성공 시 navigate('/study/{roomId}/classroom')
 *   → 실패 시 에러 메시지 표시 후 재시도 가능
 *
 * Step 1 목표 키워드:
 *   → GET /api/onboarding/keywords (getOnboardingKeywords)
 *   → 로딩 중: 스피너 표시
 *   → 빈 배열: 직접 입력 안내
 *
 * level 매핑 (UI 인덱스 → API 필드):
 *   0 → 'beginner'
 *   1 → 'beginner'
 *   2 → 'intermediate'
 *   3 → 'advanced'
 *
 * hours_per_day 매핑 (UI 문자열 → 숫자):
 *   '하루 1시간' → 1,  '하루 2시간' → 2, ...
 *
 * 로딩 화면은 3초마다 메시지를 순환하여 긴 대기 시간을 자연스럽게 처리한다.
 * (백엔드 AI 파이프라인: LLM 커리큘럼 생성 + YouTube 검색 + 자막 스크래핑 ≈ 10~30초)
 * ============================================================================
 */

import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    X, Check, Calendar, Zap, Sparkles, Bot, BookOpen,
    Lightbulb, Lock, ArrowLeft, ArrowRight, Loader2, AlertCircle,
} from 'lucide-react'
import '../styles/OnboardingWizard.css'
import { createLearningRoom, getOnboardingKeywords } from '../services/apiService'
import type { CreateLearningRoomRequest } from '../types/api'

// ── 상수 ──────────────────────────────────────────────────────────────────────
const LEVELS      = ['입문 (개념부터 차근차근)', '초급 (기본기는 있어요)', '중급 (심화 내용 중심으로)', '고급 (실전 위주로)']
const DURATIONS   = ['4주', '8주', '10주', '12주', '16주']
const INTENSITIES = ['하루 1시간', '하루 2시간', '하루 3시간', '하루 4시간+']
const LEVEL_CHARS = ['입', '초', '중', '고']

// UI 난이도 인덱스 → API level 문자열
const LEVEL_MAP: CreateLearningRoomRequest['level'][] = [
    'beginner', 'beginner', 'intermediate', 'advanced',
]

// UI 강도 문자열 → 시간(숫자)
const HOURS_MAP: Record<string, number> = {
    '하루 1시간': 1, '하루 2시간': 2, '하루 3시간': 3, '하루 4시간+': 4,
}

// 주 수 문자열 → 숫자
const parseWeeks = (duration: string) => parseInt(duration.replace('주', ''), 10)

// 로딩 화면에서 3초마다 순환 표시할 메시지 (백엔드 AI 파이프라인 단계 설명)
const LOADING_MESSAGES = [
    'AI가 커리큘럼 구조를 설계하는 중...',
    '주차별 학습 목표를 생성하는 중...',
    'YouTube에서 최적 강의를 검색하는 중...',
    '강의 자막을 분석하여 키워드를 추출하는 중...',
    '맞춤형 학습실을 준비하는 중...',
]

// ── Props ──────────────────────────────────────────────────────────────────────
interface Props { onClose: () => void }

// ── 컴포넌트 ──────────────────────────────────────────────────────────────────
export default function OnboardingWizard({ onClose }: Props) {
    const navigate = useNavigate()

    // 스텝 상태 (1~4)
    const [step,        setStep]        = useState(1)

    // 사용자 선택 값
    const [goal,        setGoal]        = useState('')
    const [customGoal,  setCustomGoal]  = useState('')
    const [level,       setLevel]       = useState(1)
    const [duration,    setDuration]    = useState('10주')
    const [intensity,   setIntensity]   = useState('하루 3시간')

    // Step 1 키워드 API 로딩
    const [goals,        setGoals]        = useState<string[]>([])
    const [goalsLoading, setGoalsLoading] = useState(true)

    // API 호출 상태
    const [creating,    setCreating]    = useState(false)
    const [loadingMsg,  setLoadingMsg]  = useState(LOADING_MESSAGES[0])
    const [error,       setError]       = useState<string | null>(null)

    // 로딩 메시지 순환 인터벌 ID
    const msgIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
    const msgIndexRef    = useRef(0)

    // 진행률 표시 (step/4 * 100)
    const progress = (step / 4) * 100

    // ── 온보딩 키워드 로드 ───────────────────────────────────────────────────
    useEffect(() => {
        getOnboardingKeywords()
            .then(data => {
                setGoals(data.keywords)
                if (data.keywords.length > 0) setGoal(data.keywords[0])
            })
            .catch(() => setGoals([]))
            .finally(() => setGoalsLoading(false))
    }, [])

    // ── 로딩 메시지 순환 시작/정지 ──────────────────────────────────────────
    const startLoadingMessages = () => {
        msgIndexRef.current = 0
        setLoadingMsg(LOADING_MESSAGES[0])
        msgIntervalRef.current = setInterval(() => {
            msgIndexRef.current = (msgIndexRef.current + 1) % LOADING_MESSAGES.length
            setLoadingMsg(LOADING_MESSAGES[msgIndexRef.current])
        }, 3_000)
    }

    const stopLoadingMessages = () => {
        if (msgIntervalRef.current) {
            clearInterval(msgIntervalRef.current)
            msgIntervalRef.current = null
        }
    }

    // unmount 시 인터벌 정리
    useEffect(() => () => stopLoadingMessages(), [])

    // ── Step 4: "학습실 개설하기" 클릭 처리 ─────────────────────────────────
    const handleFinish = async () => {
        setCreating(true)
        setError(null)
        startLoadingMessages()

        // 사용자 선택값을 API 요청 형식으로 변환
        const subject = customGoal.trim() || goal.replace('#', '')
        const body: CreateLearningRoomRequest = {
            subject,
            level:          LEVEL_MAP[level],
            duration_weeks: parseWeeks(duration),
            hours_per_day:  HOURS_MAP[intensity] ?? 1,
        }

        try {
            const result = await createLearningRoom(body)
            console.log('[createLearningRoom] response:', JSON.stringify(result, null, 2))

            stopLoadingMessages()
            onClose()
            // AI content is generated asynchronously on the backend.
            // Navigate to My Studies so the user can enter the room once it is ready.
            navigate('/my-studies')
        } catch (err) {
            console.error('[createLearningRoom] error:', err)
            stopLoadingMessages()
            setCreating(false)
            setError('학습실 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.')
        }
    }

    // ── 로딩 화면 렌더링 ────────────────────────────────────────────────────
    if (creating) {
        return (
            <div className="wizard-overlay">
                <div className="wizard animate-scale-in">
                    <div className="wizard__loading">
                        {/* 회전 아이콘 */}
                        <div className="wizard__loading-icon">
                            <Sparkles
                                size={36}
                                strokeWidth={1.5}
                                style={{ color: 'var(--color-purple-500)' }}
                                className="animate-spin"
                            />
                        </div>

                        <h3 className="wizard__loading-title">
                            AI가 맞춤 커리큘럼을 생성하고 있습니다
                        </h3>

                        {/* 순환되는 작업 메시지 */}
                        <p className="wizard__loading-msg">{loadingMsg}</p>

                        {/* 진행 도트 애니메이션 */}
                        <div className="wizard__loading-dots">
                            {[0, 1, 2].map(i => (
                                <div
                                    key={i}
                                    className="wizard__loading-dot"
                                    style={{ animationDelay: `${i * 0.3}s` }}
                                />
                            ))}
                        </div>

                        {/* 학습실 정보 요약 */}
                        <div className="wizard__loading-summary">
                            <span className="wizard__loading-tag">
                                {customGoal || goal.replace('#', '')}
                            </span>
                            <span className="wizard__loading-tag">
                                {LEVEL_CHARS[level]}급
                            </span>
                            <span className="wizard__loading-tag">{duration}</span>
                            <span className="wizard__loading-tag">{intensity}</span>
                        </div>

                        <p className="wizard__loading-hint">
                            YouTube 강의 분석 및 키워드 추출이 포함되어<br />
                            약 10~30초 소요될 수 있습니다.
                        </p>
                    </div>
                </div>
            </div>
        )
    }

    // ── 일반 마법사 화면 렌더링 ──────────────────────────────────────────────
    return (
        <div className="wizard-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
            <div className="wizard animate-scale-in">

                {/* 헤더 */}
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

                {/* 본문 */}
                <div className="wizard__body">

                    {/* Step 1 — 목표 선택 */}
                    {step === 1 && (
                        <div className="animate-fade-in">
                            <h2 className="wizard__title">어떤 분야의 성장을<br />목표로 하시나요?</h2>
                            <p className="wizard__subtitle">AI가 목표에 맞는 최적의 커리큘럼을 설계합니다</p>

                            {goalsLoading ? (
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '16px 0', color: 'var(--color-text-secondary)' }}>
                                    <Loader2 size={14} strokeWidth={1.5} className="animate-spin" />
                                    <span style={{ fontSize: '13px' }}>키워드 불러오는 중...</span>
                                </div>
                            ) : goals.length === 0 ? (
                                <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', padding: '8px 0' }}>
                                    직접 입력하여 목표를 설정해 주세요.
                                </p>
                            ) : (
                                <div className="wizard__chips">
                                    {goals.map(g => (
                                        <button
                                            key={g}
                                            className={`wizard__chip ${goal === g ? 'wizard__chip--active' : ''}`}
                                            onClick={() => { setGoal(g); setCustomGoal('') }}
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
                            )}

                            <div className="wizard__custom-label">또는 직접 입력하기</div>
                            <input
                                className="wizard__input"
                                placeholder="원하는 목표를 자유롭게 적어보세요"
                                value={customGoal}
                                onChange={e => { setCustomGoal(e.target.value); if (e.target.value) setGoal('') }}
                            />

                        </div>
                    )}

                    {/* Step 2 — 난이도 선택 */}
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

                    {/* Step 3 — 기간 & 강도 선택 */}
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

                    {/* Step 4 — 플랜 요약 + 개설하기 */}
                    {step === 4 && (
                        <div className="animate-fade-in">
                            <div className="wizard__plan-emoji" style={{ color: 'var(--color-purple-500)' }}>
                                <Sparkles size={40} strokeWidth={1.5} />
                            </div>
                            <p className="wizard__plan-title">
                                {(customGoal || goal.replace('#', '') || '설정된 목표')}({LEVEL_CHARS[level]}급) 과정을
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

                            {/* 커리큘럼 로드맵 미리보기 */}
                            <div className="wizard__roadmap-label" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                <Lightbulb size={15} strokeWidth={1.5} />
                                AI 맞춤 커리큘럼 로드맵 (생성 대기 중)
                            </div>

                            {[
                                { weekLabel: 'Week 1', text: 'AI가 첫 주차 목표를 설계할 예정입니다', locked: false, active: true  },
                                { weekLabel: 'Week 2', text: '',  locked: true,  active: false },
                                { weekLabel: 'Week 3', text: '',  locked: true,  active: false },
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
                                        {row.locked  && <Lock     size={12} strokeWidth={1.5} style={{ flexShrink: 0 }} />}
                                        {row.active  && <Sparkles size={12} strokeWidth={1.5} style={{ flexShrink: 0, color: 'var(--color-purple-500)' }} />}
                                        {row.active  && (
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

                            {/* 에러 메시지 */}
                            {error && (
                                <div
                                    style={{
                                        display: 'flex', alignItems: 'center', gap: '6px',
                                        background: '#fef2f2', border: '1px solid #fecaca',
                                        borderRadius: '8px', padding: '10px 14px',
                                        fontSize: '13px', color: '#dc2626',
                                    }}
                                >
                                    <AlertCircle size={14} strokeWidth={2} />
                                    {error}
                                </div>
                            )}
                        </div>
                    )}

                    {/* ── 하단 네비게이션 버튼 ── */}
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
