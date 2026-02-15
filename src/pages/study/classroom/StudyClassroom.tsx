import { useNavigate, useParams } from 'react-router-dom'

export default function StudyClassroom() {
    const navigate = useNavigate()
    const { studyId } = useParams()

    return (
        <div style={{ padding: '2rem' }}>
            <h1>학습실</h1>
            <div style={{ display: 'flex', gap: '1rem', flexDirection: 'column', maxWidth: '200px' }}>
                <button onClick={() => navigate(`/study/${studyId}/classroom/viewer`)}>자료뷰어</button>
                <button onClick={() => navigate(`/study/${studyId}/classroom/quiz`)}>퀴즈풀이</button>
                <button onClick={() => navigate(`/study/${studyId}/classroom/video`)}>영상뷰어</button>
                <button onClick={() => navigate(`/study/${studyId}/classroom/assignment`)}>과제제출</button>
            </div>
            <button onClick={() => navigate(`/study/${studyId}`)} style={{ marginTop: '1rem' }}>
                스터디 대시보드로
            </button>
        </div>
    )
}