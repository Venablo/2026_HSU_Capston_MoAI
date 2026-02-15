import { useNavigate } from 'react-router-dom'

export default function MyPageDashboard() {  // 함수명도 수정!
    const navigate = useNavigate()

    return (
        <div style={{ padding: '2rem' }}>
            <h1>학습 대시보드(마이페이지)</h1>
            <div style={{ display: 'flex', gap: '1rem', flexDirection: 'column', maxWidth: '200px' }}>
                <button onClick={() => navigate('/my-page/archive')}>통합 학습 보관함</button>
                <button onClick={() => navigate('/my-page/settings')}>환경 설정</button>
                <button onClick={() => navigate('/my-page/wrong-answers')}>통합 오답 노트</button>
                <button onClick={() => navigate('/main')}>메인</button>
            </div>
        </div>
    )
}