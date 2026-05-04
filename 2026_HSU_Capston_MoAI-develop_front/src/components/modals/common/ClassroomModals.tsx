/**
 * ============================================================================
 * ClassroomModals.tsx  —  학습실 모달 통합 라우터
 * ============================================================================
 *
 * 역할:
 *   ClassroomModalContext의 modal 키를 읽어 현재 활성화된 모달을 렌더링한다.
 *   모달 간 전환 흐름을 이 파일에서 조율한다.
 *
 * 모달 전환 맵:
 *   monitoring      → (AI 분석 fetch) → summary-detail
 *   fast-track      → quiz-pass
 *   reverse-learning → (SSE + endFlippedSession) → meta-evaluation
 *   meta-evaluation → 닫기 (매칭 버튼은 사이드바로 이동)
 *   사이드바 '스터디 매칭 신청' → (fetchStudyMatch) → study-matching
 *   study-matching  → (accept) → 사이드바 상태 갱신
 *   quiz-pass       → quiz-correct | quiz-incorrect
 *   final-quiz      → (내부에서 submit + 폴링) → 리포트 (모달 내부 처리)
 *
 * roomId / weekId 획득 방법:
 *   - roomId: URL 파라미터 (:studyId) → useParams()로 읽음
 *   - weekId: ClassroomModalContext의 currentWeekId
 * ============================================================================
 */

import { useParams } from 'react-router-dom'
import { useClassroomModal } from '../../../context/ClassroomModalContext'
import type { SummaryItem } from '../monitoring/SummaryDetailModal'
import MonitoringModal from '../monitoring/MonitoringModal'
import SummaryDetailModal from '../monitoring/SummaryDetailModal'
import FastTrackModal from '../monitoring/FastTrackModal'
import QuizPassModal from '../quiz/QuizPassModal'
import QuizCorrectModal from '../quiz/QuizCorrectModal'
import QuizIncorrectModal from '../quiz/QuizIncorrectModal'
import FlippedModal from '../flipped/FlippedModal'
import ReverseLearningModal from '../flipped/ReverseLearningModal'
import MetaEvaluationModal from '../flipped/MetaEvaluationModal'
import StudyMatchingModal from '../flipped/StudyMatchingModal'
import FinalQuizModal from '../quiz/FinalQuizModal'
import type { MetaEvaluationResponse } from '../../../types/aiEvents'
import type { EndFlippedResponse } from '../../../types/api'

export default function ClassroomModals() {
    const {
        modal, modalData, open, close,
        setMetacogComplete, setPartnerConnected, setPartnerInfo,
        setQuizSubmitted,
        currentWeekId,
    } = useClassroomModal()

    // URL 파라미터에서 roomId를 가져온다.
    // 이 컴포넌트는 /study/:studyId/classroom 라우트 내부에서 렌더링되므로 항상 존재한다.
    const { studyId: roomId = '' } = useParams<{ studyId: string }>()

    // ── monitoring → summary-detail 전환 ────────────────────────────────────
    // summaryItems은 StudyClassroom이 getMaterialDetail() 호출 시 이미 받아서
    // ModalData.monitoring에 저장해 두었다. 추가 네트워크 요청 없이 바로 오픈.
    const handleShowSummary = (conceptName: string, summaryItems: SummaryItem[]) => {
        open('summary-detail', {
            type:        'summary-detail',
            conceptName,
            summaryItems,
        })
    }

    // ── reverse-learning(SSE 완료) → meta-evaluation 전환 ───────────────────
    /**
     * ReverseLearningModal의 onSessionEnd 콜백.
     * endFlippedSession 응답(EndFlippedResponse)을 MetaEvaluationResponse로 변환한다.
     *
     * 매핑:
     *   EndFlippedResponse.score           → MetaEvaluationResponse.comprehensionScore
     *   EndFlippedResponse.gainedKeywords  → MetaEvaluationResponse.strongKeywords
     *   EndFlippedResponse.weakKeywords    → MetaEvaluationResponse.weakKeywords
     */
    const handleSessionEnd = (result: EndFlippedResponse) => {
        // 거꾸로 학습 세션 완료 → 파이널 퀴즈 잠금 해제
        setMetacogComplete(true)
        const evaluation: MetaEvaluationResponse = {
            comprehensionScore: result.score,
            strongKeywords:     result.gainedKeywords,
            weakKeywords:       result.weakKeywords,
        }
        open('meta-evaluation', { type: 'meta-evaluation', evaluation })
    }

    // Fallback: onSessionEnd was not provided (should not happen in normal flow).
    const handleEvaluationFallback = (_explanation: string) => { close() }

    // ── study-matching → 사이드바 상태 갱신 (파트너 연결 완료) ───────────────
    const handleConnect = () => {
        setMetacogComplete(true)
        setPartnerConnected(true)
        // 매칭 데이터를 컨텍스트에 저장 (localStorage 포함) — 사이드바 채팅 위젯에서 사용
        if (modalData?.type === 'study-matching') setPartnerInfo(modalData.match)
        close()
    }

    // 열린 모달이 없으면 아무것도 렌더링하지 않음
    if (!modal) return null

    return (
        <>
            {/* ── 패턴1/2: 되감기·일시정지 감지 → 개념 요약 제안 ── */}
            {modal === 'monitoring' && modalData?.type === 'monitoring' && (
                <MonitoringModal
                    conceptName={modalData.conceptName}
                    reason={modalData.reason}
                    onClose={close}
                    onShowSummary={() => handleShowSummary(modalData.conceptName, modalData.summaryItems)}
                />
            )}

            {/* ── 개념 요약 카드 (ACID 등 핵심 개념 상세 설명) ── */}
            {modal === 'summary-detail' && modalData?.type === 'summary-detail' && (
                <SummaryDetailModal
                    conceptName={modalData.conceptName}
                    summaryItems={modalData.summaryItems}
                    onClose={close}
                />
            )}

            {/* ── 패턴3: 빠른 완료 감지 → 심화 학습 제안 ── */}
            {modal === 'fast-track' && modalData?.type === 'fast-track' && (
                <FastTrackModal
                    conceptName={modalData.conceptName}
                    reason={modalData.reason}
                    completionRate={modalData.completionRate}
                    challengeLevel={modalData.challengeLevel}
                    onClose={close}
                    onStartChallenge={() => open('quiz-pass')}
                />
            )}

            {/* ── 패턴3(스킵) 돌발 퀴즈 / fast-track 연계 4지선다 퀴즈 ── */}
            {modal === 'quiz-pass' && modalData?.type === 'quiz-pass' && (
                <QuizPassModal
                    quiz={modalData.quiz}
                    onClose={close}
                    onResult={(correct, conceptName, correctConcept, explanation) => {
                        if (correct) {
                            open('quiz-correct', { type: 'quiz-correct', conceptName })
                        } else {
                            open('quiz-incorrect', {
                                type: 'quiz-incorrect',
                                conceptName,
                                correctConcept,
                                explanation,
                            })
                        }
                    }}
                />
            )}

            {/* ── 퀴즈 정답 ── */}
            {modal === 'quiz-correct' && modalData?.type === 'quiz-correct' && (
                <QuizCorrectModal
                    conceptName={modalData.conceptName}
                    onClose={close}
                />
            )}

            {/* ── 퀴즈 오답 ── */}
            {modal === 'quiz-incorrect' && modalData?.type === 'quiz-incorrect' && (
                <QuizIncorrectModal
                    conceptName={modalData.conceptName}
                    correctConcept={modalData.correctConcept}
                    explanation={modalData.explanation}
                    onClose={close}
                />
            )}

            {/* ── 거꾸로 학습 진입 안내 모달 ── */}
            {modal === 'flipped' && (
                <FlippedModal onClose={close} />
            )}

            {/* ── 거꾸로 학습(SSE 채팅) ─────────────────────────────────────
             *   roomId: URL 파라미터(:studyId)
             *   weekId: ClassroomModalContext의 currentWeekId
             *   onSessionEnd: endFlippedSession 응답 → meta-evaluation으로 전환
             * ─────────────────────────────────────────────────────────────── */}
            {modal === 'reverse-learning' && modalData?.type === 'reverse-learning' && (
                <ReverseLearningModal
                    conceptName={modalData.conceptName}
                    onClose={close}
                    roomId={modalData.roomId ?? roomId}
                    weekId={modalData.weekId ?? currentWeekId ?? undefined}
                    onSessionEnd={handleSessionEnd}
                    onSubmitExplanation={handleEvaluationFallback}
                />
            )}

            {/* ── 메타인지 평가 결과 (이해도 점수 + 강점/약점 키워드) ── */}
            {modal === 'meta-evaluation' && modalData?.type === 'meta-evaluation' && (
                <MetaEvaluationModal
                    evaluation={modalData.evaluation}
                    onClose={close}
                />
            )}

            {/* ── 스터디 파트너 매칭 ── */}
            {modal === 'study-matching' && modalData?.type === 'study-matching' && (
                <StudyMatchingModal
                    match={modalData.match}
                    onClose={close}
                    onConnect={handleConnect}
                />
            )}

            {/* ── 주간 파이널 퀴즈 + AI 리포트 ──────────────────────────────
             *   내부적으로 getFinalQuiz → submitFinalQuiz → polling → 리포트
             *   표시까지 모달 안에서 완결된다.
             * ─────────────────────────────────────────────────────────────── */}
            {modal === 'final-quiz' && modalData?.type === 'final-quiz' && (
                <FinalQuizModal
                    roomId={modalData.roomId}
                    weekId={modalData.weekId}
                    onClose={close}
                    onComplete={() => setQuizSubmitted(true)}
                    reviewMode={modalData.reviewMode}
                />
            )}
        </>
    )
}
