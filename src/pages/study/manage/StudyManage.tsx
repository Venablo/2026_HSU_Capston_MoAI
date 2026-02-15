import {useNavigate, useParams} from 'react-router-dom'

export default function MainPage() {
    const navigate = useNavigate()
    const { studyId } = useParams()

    return (
        <div style={{ padding: '2rem' }}>
            <h1>스터디 관리</h1>
            <div style={{ display: 'flex', gap: '1rem', flexDirection: 'column', maxWidth: '200px' }}>
                <button onClick={() => navigate(`/study/${studyId}`)} style={{ marginTop: '1rem' }}>
                    스터디 대시보드로
                </button>
            </div>
        </div>
    )
}