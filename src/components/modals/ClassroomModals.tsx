import { useState } from 'react'
import { useClassroomModal } from '../../context/ClassroomModalContext'
import { fetchConceptSummary, fetchMetaEvaluation, fetchStudyMatch } from '../../services/aiSummaryService'
import MonitoringModal from './MonitoringModal'
import SummaryDetailModal from './SummaryDetailModal'
import FastTrackModal from './FastTrackModal'
import QuizPassModal from './QuizPassModal'
import QuizCorrectModal from './QuizCorrectModal'
import QuizIncorrectModal from './QuizIncorrectModal'
import FlippedModal from './FlippedModal'
import ReverseLearningModal from './ReverseLearningModal'
import MetaEvaluationModal from './MetaEvaluationModal'
import StudyMatchingModal from './StudyMatchingModal'
import type { MetaEvaluationResponse } from '../../types/aiEvents'

/**
 * Renders whichever classroom modal is currently active.
 *
 * Reads modal key + typed payload from ClassroomModalContext — no props needed.
 * Owns all async modal transitions:
 *   monitoring      → summary-detail      (fetch concept summary)
 *   reverse-learning → meta-evaluation    (fetch evaluation)
 *   meta-evaluation  → study-matching     (fetch study match)
 *   study-matching   → sidebar state      (setMetacogComplete + setPartnerConnected)
 */
export default function ClassroomModals() {
    const {
        modal, modalData, open, close,
        setMetacogComplete, setPartnerConnected,
    } = useClassroomModal()

    const [summaryLoading, setSummaryLoading] = useState(false)
    const [evalLoading,    setEvalLoading]    = useState(false)
    const [matchLoading,   setMatchLoading]   = useState(false)

    // monitoring → summary-detail
    const handleShowSummary = async (conceptName: string) => {
        setSummaryLoading(true)
        try {
            const summary = await fetchConceptSummary(conceptName)
            open('summary-detail', {
                type:         'summary-detail',
                conceptName:  summary.conceptName,
                summaryItems: summary.summaryItems,
            })
        } finally {
            setSummaryLoading(false)
        }
    }

    // reverse-learning → meta-evaluation
    const handleEvaluation = async (explanation: string) => {
        setEvalLoading(true)
        try {
            const evaluation = await fetchMetaEvaluation(explanation)
            open('meta-evaluation', {
                type: 'meta-evaluation',
                evaluation,
            })
        } finally {
            setEvalLoading(false)
        }
    }

    // meta-evaluation → study-matching
    const handleMatching = async (evaluation: MetaEvaluationResponse) => {
        setMatchLoading(true)
        try {
            const match = await fetchStudyMatch(evaluation)
            open('study-matching', {
                type: 'study-matching',
                match,
            })
        } finally {
            setMatchLoading(false)
        }
    }

    // study-matching → sidebar completion state
    const handleConnect = () => {
        setMetacogComplete(true)
        setPartnerConnected(true)
        close()
    }

    if (!modal) return null

    return (
        <>
            {modal === 'monitoring' && modalData?.type === 'monitoring' && (
                <MonitoringModal
                    conceptName={modalData.conceptName}
                    reason={modalData.reason}
                    loading={summaryLoading}
                    onClose={close}
                    onShowSummary={() => handleShowSummary(modalData.conceptName)}
                />
            )}
            {modal === 'summary-detail' && modalData?.type === 'summary-detail' && (
                <SummaryDetailModal
                    conceptName={modalData.conceptName}
                    summaryItems={modalData.summaryItems}
                    onClose={close}
                />
            )}
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
            {modal === 'quiz-pass' && (
                <QuizPassModal
                    onClose={close}
                    onResult={(correct, conceptName, correctConcept, explanation) => {
                        if (correct) {
                            open('quiz-correct', { type: 'quiz-correct', conceptName })
                        } else {
                            open('quiz-incorrect', { type: 'quiz-incorrect', conceptName, correctConcept, explanation })
                        }
                    }}
                />
            )}
            {modal === 'quiz-correct' && modalData?.type === 'quiz-correct' && (
                <QuizCorrectModal
                    conceptName={modalData.conceptName}
                    onClose={close}
                />
            )}
            {modal === 'quiz-incorrect' && modalData?.type === 'quiz-incorrect' && (
                <QuizIncorrectModal
                    conceptName={modalData.conceptName}
                    correctConcept={modalData.correctConcept}
                    explanation={modalData.explanation}
                    onClose={close}
                />
            )}
            {modal === 'flipped' && (
                <FlippedModal onClose={close} />
            )}
            {modal === 'reverse-learning' && modalData?.type === 'reverse-learning' && (
                <ReverseLearningModal
                    conceptName={modalData.conceptName}
                    onClose={close}
                    onSubmitExplanation={handleEvaluation}
                    loading={evalLoading}
                />
            )}
            {modal === 'meta-evaluation' && modalData?.type === 'meta-evaluation' && (
                <MetaEvaluationModal
                    evaluation={modalData.evaluation}
                    onClose={close}
                    onRequestMatching={() => handleMatching(modalData.evaluation)}
                    loading={matchLoading}
                />
            )}
            {modal === 'study-matching' && modalData?.type === 'study-matching' && (
                <StudyMatchingModal
                    match={modalData.match}
                    onClose={close}
                    onConnect={handleConnect}
                />
            )}
        </>
    )
}
