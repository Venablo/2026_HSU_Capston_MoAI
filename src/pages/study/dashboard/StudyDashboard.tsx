import { useNavigate, useParams } from 'react-router-dom'

export default function StudyDashboard() {
    const navigate = useNavigate()
    const { studyId } = useParams()

    return (
        <div style={{ padding: '2rem' }}>
            <h1>스터디 대시보드 (ID: {studyId})</h1>
            <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
                <button onClick={() => navigate(`/study/${studyId}/board`)}>게시판</button>
                <button onClick={() => navigate(`/study/${studyId}/classroom`)}>학습실</button>
                <button onClick={() => navigate(`/study/${studyId}/members`)}>멤버</button>
                <button onClick={() => navigate(`/study/${studyId}/calendar`)}>캘린더</button>
                <button onClick={() => navigate(`/study/${studyId}/ranking`)}>랭킹</button>
                <button onClick={() => navigate(`/study/${studyId}/manage`)}>관리</button>
            </div>
            <button onClick={() => navigate('/my-studies')} style={{ marginTop: '1rem' }}>
                내 스터디로 돌아가기
            </button>
        </div>
    )
}