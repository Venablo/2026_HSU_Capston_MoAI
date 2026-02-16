import { useNavigate } from 'react-router-dom'

export default function MyPageWrongAnswers() {  // 함수명 수정!
    const navigate = useNavigate()

    return (
        <div style={{ padding: '2rem' }}>
            <h1>통합 오답 노트</h1>
            <div style={{ display: 'flex', gap: '1rem', flexDirection: 'column', maxWidth: '200px' }}>
                <button onClick={() => navigate('/my-page')}>마이페이지 대시보드</button>
            </div>
        </div>
    )
}