import { useNavigate, useParams } from 'react-router-dom'
import { mockStudies, toStudyHeaderProps } from '../../../constants'
import StudyHeader from '../../../components/Study/StudyHeader/StudyHeader'

export default function StudyClassroom() {
    const navigate = useNavigate()
    const { studyId } = useParams()
    const study = mockStudies.find(s => s.id === Number(studyId))

    if (!study) {
        return (
            <div style={{ textAlign: 'center', padding: '4rem' }}>
                <h2>스터디를 찾을 수 없습니다</h2>
                <button onClick={() => navigate('/my-studies')}>내 스터디로 돌아가기</button>
            </div>
        )
    }

    const studyData = toStudyHeaderProps(study)

    return (
        <div className="study-page">
            <StudyHeader {...studyData} />
            {/* 학습실 */}
            <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', padding: '1rem 0' }}>
                <button onClick={() => navigate(`/study/${study.id}/classroom/viewer`)}>자료뷰어</button>
                <button onClick={() => navigate(`/study/${study.id}/classroom/quiz`)}>퀴즈풀이</button>
                <button onClick={() => navigate(`/study/${study.id}/classroom/video`)}>영상뷰어</button>
                <button onClick={() => navigate(`/study/${study.id}/classroom/assignment`)}>과제제출</button>
            </div>
        </div>
    )
}