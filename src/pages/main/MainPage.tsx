import { useNavigate } from 'react-router-dom'

export default function MainPage() {
    const navigate = useNavigate()

    return (
        <div style={{ padding: '2rem' }}>
            <h1>메인 대시보드</h1>
            <div style={{ display: 'flex', gap: '1rem', flexDirection: 'column', maxWidth: '200px' }}>
                <button onClick={() => navigate('/my-studies')}>내 스터디</button>
                <button onClick={() => navigate('/my-calendar')}>내 캘린더</button>
                <button onClick={() => navigate('/my-page/dashboard')}>마이페이지</button>
                <button onClick={() => navigate('/notifications')}>알림 보관함</button>
                <button onClick={() => navigate('/messages')}>메시지</button>
                <button onClick={() => navigate('/study/1')}>스터디 입장 (예시)</button>
            </div>
        </div>
    )
}