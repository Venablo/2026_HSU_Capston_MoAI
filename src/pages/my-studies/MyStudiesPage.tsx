import { useNavigate } from 'react-router-dom'

export default function MyStudiesPage() {
    const navigate = useNavigate()

    return (
        <div style={{ padding: '2rem' }}>
            <h1>내 스터디</h1>
            <button onClick={() => navigate('/main')}>메인으로</button>
            <div style={{ marginTop: '1rem' }}>
                <button onClick={() => navigate('/study/1')}>정보처리기사 스터디 입장</button>
            </div>
        </div>
    )
}