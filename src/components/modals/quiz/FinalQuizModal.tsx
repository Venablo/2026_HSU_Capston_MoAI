/**
 * ============================================================================
 * FinalQuizModal.tsx  —  주간 파이널 퀴즈 + AI 분석 리포트 모달
 * ============================================================================
 *
 * 전체 플로우:
 *
 *  [모달 오픈]
 *      │
 *      ▼
 *  ① GET /quizzes/final?roomId=X&weekId=Y
 *      → FinalQuizResponse { quizId, title, questions[5] } 수신
 *      │
 *      ▼
 *  ② 문제를 Step 형태로 한 번에 하나씩 표시 (1/5, 2/5, ...)
 *     각 문제는 서술형(essay) — 사용자가 textarea에 자유롭게 입력
 *      │
 *  ③ 5문제 모두 작성 후 "전체 제출" 클릭
 *      │
 *      ▼
 *  ④ POST /quizzes/final/submit
 *      → HTTP 202 Accepted
 *      → { reportId, status: 'analyzing', estimatedSec } 수신
 *      │
 *      ▼
 *  ⑤ "AI 분석 중" 로딩 화면 표시
 *     estimatedSec 간격으로 GET /quiz-report 폴링
 *      │
 *      └── status = 'completed' → 리포트 화면으로 전환
 *      │
 *      ▼
 *  ⑥ AI 종합 분석 리포트 표시
 *     - 총점 (finalScore)
 *     - 레이더 차트 (radarData: 개념이해도, 응용력, 논리력, 키워드적중률)
 *     - 문항별 AI 해설 (gainedKeywords, weakKeywords, aiComment)
 *
 * ============================================================================
 */

import { useState, useEffect, useRef, useCallback } from 'react'
import {
    Trophy, ChevronRight, ChevronLeft, Loader2,
    BrainCircuit, CheckCircle2, XCircle, Zap,
} from 'lucide-react'
import Modal from '../common/Modal'
import {
    getFinalQuiz,
    submitFinalQuiz,
    getQuizReport,
} from '../../../services/apiService'
import type {
    FinalQuizResponse,
    QuizReportResponse,
    FinalQuizAnswer,
} from '../../../types/api'
import {
    RadarChart, PolarGrid, PolarAngleAxis,
    Radar, ResponsiveContainer, Tooltip,
} from 'recharts'

// ── 레이더 차트 데이터 변환 ────────────────────────────────────────────────────
// recharts RadarChart는 { subject: string, value: number } 배열을 요구한다.
function toRadarData(radarMap: Record<string, number>) {
    return Object.entries(radarMap).map(([subject, value]) => ({ subject, value }))
}

// ── 진행 단계 타입 ────────────────────────────────────────────────────────────
type Phase =
    | 'loading-questions' // 문제 로딩 중
    | 'answering'         // 문제 풀기 (step 1~N)
    | 'submitting'        // 제출 중
    | 'analyzing'         // AI 분석 중 (폴링)
    | 'report'            // 리포트 표시
    | 'error'             // API 오류

// ── Props 정의 ────────────────────────────────────────────────────────────────
interface Props {
    roomId: string
    weekId: string
    onClose: () => void
}

// ── 컴포넌트 ─────────────────────────────────────────────────────────────────
export default function FinalQuizModal({ roomId, weekId, onClose }: Props) {
    const [phase,       setPhase]       = useState<Phase>('loading-questions')
    const [quiz,        setQuiz]        = useState<FinalQuizResponse | null>(null)
    const [answers,     setAnswers]     = useState<string[]>([])  // 각 문제 답안
    const [step,        setStep]        = useState(0)              // 현재 문제 인덱스 (0-based)
    const [report,        setReport]        = useState<QuizReportResponse | null>(null)
    const [analyzeMsg,    setAnalyzeMsg]    = useState('AI가 답변을 분석하고 있습니다...')
    const [errorMessage,  setErrorMessage]  = useState('')

    // 폴링 인터벌 ID — unmount 시 반드시 clearInterval 필요
    const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)
    // 분석 메시지 인덱스 (폴링 중 메시지 순환 표시용)
    const analyzeStepRef = useRef(0)

    // ── 분석 중 메시지 순환 ───────────────────────────────────────────────────
    const ANALYZE_MESSAGES = [
        'AI가 답변을 분석하고 있습니다...',
        '키워드 적중률을 계산하는 중...',
        '개념 이해도를 평가하는 중...',
        '최종 리포트를 생성하는 중...',
    ]

    // ── 폴링 정지 (cleanup) ───────────────────────────────────────────────────
    const stopPolling = useCallback(() => {
        if (pollRef.current !== null) {
            clearInterval(pollRef.current)
            pollRef.current = null
        }
    }, [])

    useEffect(() => () => stopPolling(), [stopPolling])

    // ── ① 문제 로드 ──────────────────────────────────────────────────────────
    useEffect(() => {
        let cancelled = false
        const load = async () => {
            try {
                const data = await getFinalQuiz(roomId, weekId)
                if (!cancelled) {
                    setQuiz(data)
                    setAnswers(new Array(data.questions.length).fill(''))
                    setPhase('answering')
                }
            } catch (e) {
                if (!cancelled) {
                    setErrorMessage(e instanceof Error ? e.message : '퀴즈를 불러오지 못했습니다.')
                    setPhase('error')
                }
            }
        }
        load()
        return () => { cancelled = true }
    }, [roomId, weekId])

    // ── ④ 전체 제출 ──────────────────────────────────────────────────────────
    const handleSubmit = useCallback(async () => {
        if (!quiz) return
        setPhase('submitting')

        // 답안 배열을 API 형식으로 변환
        const formattedAnswers: FinalQuizAnswer[] = quiz.questions.map((q, i) => ({
            questionId: q.questionId,
            answer:     answers[i] || '(미작성)',
        }))

        try {
            const { estimatedSec } = await submitFinalQuiz(roomId, weekId, {
                quizId:  quiz.quizId,
                answers: formattedAnswers,
            })

            // 제출 성공 → 분석 화면으로 전환 + 폴링 시작
            setPhase('analyzing')
            startPolling(estimatedSec)
        } catch (e) {
            setErrorMessage(e instanceof Error ? e.message : '제출에 실패했습니다. 다시 시도해 주세요.')
            setPhase('error')
        }
    }, [quiz, answers, roomId, weekId])

    // ── ⑤ 폴링: GET /quiz-report ─────────────────────────────────────────────
    const startPolling = useCallback((estimatedSec: number) => {
        // 분석 메시지 순환: 3초마다 메시지 변경
        const msgInterval = setInterval(() => {
            analyzeStepRef.current = (analyzeStepRef.current + 1) % ANALYZE_MESSAGES.length
            setAnalyzeMsg(ANALYZE_MESSAGES[analyzeStepRef.current])
        }, 3_000)

        // 실제 리포트 폴링: estimatedSec 간격으로 API 호출
        pollRef.current = setInterval(async () => {
            try {
                const result = await getQuizReport(roomId, weekId)
                // 응답이 오면 분석 완료로 간주 (QuizReportResponse에는 별도 status 필드 없음)
                if (result) {
                    clearInterval(msgInterval)
                    stopPolling()
                    setReport(result)
                    setPhase('report')
                }
            } catch {
                // 아직 분석 중이면 계속 폴링 (404나 202는 정상 상태)
            }
        }, Math.max(estimatedSec * 1000, 3_000))

    }, [roomId, weekId, stopPolling, ANALYZE_MESSAGES])

    // ── 현재 문제 데이터 ──────────────────────────────────────────────────────
    const questions  = quiz?.questions ?? []
    const totalSteps = questions.length
    const currentQ   = questions[step]
    const currentAns = answers[step] ?? ''

    const updateAnswer = (text: string) => {
        setAnswers(prev => {
            const updated = [...prev]
            updated[step] = text
            return updated
        })
    }

    // 현재 문제에 답이 작성됐는지 확인
    const hasAnswer = currentAns.trim().length > 0

    // ── 렌더: 오류 ──────────────────────────────────────────────────────────
    if (phase === 'error') {
        return (
            <Modal onClose={onClose} wide>
                <div className="fq-loading">
                    <p style={{ color: '#ef4444', fontSize: '14px' }}>⚠ {errorMessage}</p>
                    <button
                        className="fq-report__close-btn"
                        onClick={onClose}
                        style={{ marginTop: '16px' }}
                    >
                        닫기
                    </button>
                </div>
            </Modal>
        )
    }

    // ── 렌더: 문제 로딩 중 ───────────────────────────────────────────────────
    if (phase === 'loading-questions') {
        return (
            <Modal onClose={onClose} wide>
                <div className="fq-loading">
                    <Loader2 size={32} strokeWidth={1.5} className="animate-spin" style={{ color: 'var(--color-purple-500)' }} />
                    <p>파이널 퀴즈를 불러오는 중...</p>
                </div>
            </Modal>
        )
    }

    // ── 렌더: AI 분석 중 ─────────────────────────────────────────────────────
    if (phase === 'submitting' || phase === 'analyzing') {
        return (
            <Modal onClose={() => {}} wide>
                {/* 분석 중에는 닫기 방지: onClose에 빈 함수 전달 */}
                <div className="fq-analyzing">
                    <div className="fq-analyzing__icon">
                        <BrainCircuit size={48} strokeWidth={1.2} style={{ color: 'var(--color-purple-500)' }} />
                    </div>
                    <h3 className="fq-analyzing__title">AI 종합 분석 중</h3>
                    <p className="fq-analyzing__msg">{analyzeMsg}</p>

                    {/* 진행 도트 애니메이션 */}
                    <div className="fq-analyzing__dots">
                        {[0, 1, 2].map(i => (
                            <div
                                key={i}
                                className="fq-analyzing__dot"
                                style={{ animationDelay: `${i * 0.3}s` }}
                            />
                        ))}
                    </div>

                    <p className="fq-analyzing__sub">
                        5문제를 AI가 동시에 채점하고 있습니다.<br />
                        잠시만 기다려주세요.
                    </p>
                </div>
            </Modal>
        )
    }

    // ── 렌더: AI 분석 리포트 ─────────────────────────────────────────────────
    if (phase === 'report' && report) {
        const radarData   = toRadarData(report.radarData)
        const correctCount = report.questions.filter(q => q.isCorrect).length

        return (
            <Modal onClose={onClose} wide>
                <div className="fq-report">
                    {/* 상단 총점 */}
                    <div className="fq-report__header">
                        <div className="fq-report__trophy">
                            <Trophy size={28} strokeWidth={1.5} />
                        </div>
                        <h3 className="fq-report__title">AI 종합 분석 리포트</h3>
                        <div className="fq-report__score">{report.finalScore}점</div>
                        <p className="fq-report__score-sub">
                            {totalSteps}문제 중 {correctCount}문제 통과
                        </p>
                    </div>

                    {/* 레이더 차트 */}
                    <div className="fq-report__chart-wrap">
                        <p className="fq-report__chart-label">역량 분석 레이더</p>
                        <ResponsiveContainer width="100%" height={220}>
                            <RadarChart data={radarData}>
                                <PolarGrid stroke="var(--color-purple-100)" />
                                <PolarAngleAxis
                                    dataKey="subject"
                                    tick={{ fontSize: 11, fill: 'var(--color-text-secondary)' }}
                                />
                                <Radar
                                    dataKey="value"
                                    stroke="var(--color-purple-500)"
                                    fill="var(--color-purple-500)"
                                    fillOpacity={0.25}
                                />
                                <Tooltip formatter={(v) => [`${v}점`, '점수']} />
                            </RadarChart>
                        </ResponsiveContainer>
                    </div>

                    {/* 문항별 AI 해설 */}
                    <div className="fq-report__questions">
                        <p className="fq-report__questions-title">문항별 AI 해설</p>
                        {report.questions.map(q => (
                            <div
                                key={q.order}
                                className={`fq-report__q-item ${
                                    q.isCorrect
                                        ? 'fq-report__q-item--correct'
                                        : 'fq-report__q-item--wrong'
                                }`}
                            >
                                <div className="fq-report__q-header">
                                    <span className="fq-report__q-num">Q{q.order}</span>
                                    {q.isCorrect
                                        ? <CheckCircle2 size={14} strokeWidth={2} style={{ color: '#10b981' }} />
                                        : <XCircle size={14} strokeWidth={2} style={{ color: '#ef4444' }} />
                                    }
                                    <span className="fq-report__q-score">
                                        {q.score}/{q.maxScore}점
                                    </span>
                                </div>
                                <p className="fq-report__q-comment">{q.aiComment}</p>
                                {q.gainedKeywords.length > 0 && (
                                    <div className="fq-report__q-kw-row">
                                        {q.gainedKeywords.map(kw => (
                                            <span key={kw} className="fq-report__kw fq-report__kw--gain">{kw}</span>
                                        ))}
                                        {q.weakKeywords.map(kw => (
                                            <span key={kw} className="fq-report__kw fq-report__kw--miss">{kw}</span>
                                        ))}
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>

                    <button className="fq-report__close-btn" onClick={onClose}>
                        확인
                    </button>
                </div>
            </Modal>
        )
    }

    // ── 렌더: 문제 풀기 (answering 단계) ────────────────────────────────────
    return (
        <Modal onClose={onClose} wide>
            <div className="fq-quiz">
                {/* 헤더 */}
                <div className="fq-quiz__header">
                    <div className="fq-quiz__badge">
                        <Zap size={13} strokeWidth={2} />
                        주간 파이널 퀴즈
                    </div>
                    <span className="fq-quiz__step">{step + 1} / {totalSteps}</span>
                </div>

                {/* 진행 바 */}
                <div className="fq-quiz__progress-track">
                    <div
                        className="fq-quiz__progress-fill"
                        style={{ width: `${((step + 1) / totalSteps) * 100}%` }}
                    />
                </div>

                {/* 퀴즈 제목 */}
                <h3 className="fq-quiz__title">{quiz?.title}</h3>

                {/* 현재 문제 */}
                {currentQ && (
                    <>
                        <p className="fq-quiz__question">{currentQ.question}</p>

                        {/* 힌트 */}
                        {currentQ.tip && (
                            <p className="fq-quiz__tip">
                                💡 힌트: {currentQ.tip}
                            </p>
                        )}

                        {/* 답안 입력 */}
                        <textarea
                            className="fq-quiz__textarea"
                            value={currentAns}
                            onChange={e => updateAnswer(e.target.value)}
                            placeholder="여기에 답을 작성해 주세요..."
                            rows={6}
                            maxLength={currentQ.maxLength}
                        />
                        <div className="fq-quiz__char-count">
                            {currentAns.length} / {currentQ.maxLength}자
                        </div>
                    </>
                )}

                {/* 이전/다음 버튼 */}
                <div className="fq-quiz__nav">
                    <button
                        className="fq-quiz__nav-btn"
                        onClick={() => setStep(s => s - 1)}
                        disabled={step === 0}
                    >
                        <ChevronLeft size={16} strokeWidth={2} />
                        이전
                    </button>

                    {step < totalSteps - 1 ? (
                        <button
                            className={`fq-quiz__nav-btn fq-quiz__nav-btn--next ${hasAnswer ? '' : 'fq-quiz__nav-btn--dim'}`}
                            onClick={() => setStep(s => s + 1)}
                        >
                            다음
                            <ChevronRight size={16} strokeWidth={2} />
                        </button>
                    ) : (
                        // 마지막 문제: 제출 버튼
                        <button
                            className={`fq-quiz__submit-btn ${
                                answers.every(a => a.trim().length > 0)
                                    ? 'fq-quiz__submit-btn--active'
                                    : 'fq-quiz__submit-btn--dim'
                            }`}
                            onClick={handleSubmit}
                        >
                            <Trophy size={15} strokeWidth={1.5} />
                            전체 제출하고 AI 평가받기
                        </button>
                    )}
                </div>

                {/* 미작성 문제 표시 */}
                <div className="fq-quiz__dots">
                    {answers.map((ans, i) => (
                        <div
                            key={i}
                            className={`fq-quiz__dot ${
                                i === step
                                    ? 'fq-quiz__dot--current'
                                    : ans.trim().length > 0
                                        ? 'fq-quiz__dot--done'
                                        : ''
                            }`}
                            onClick={() => setStep(i)}
                            title={`문제 ${i + 1}`}
                        />
                    ))}
                </div>
            </div>
        </Modal>
    )
}
