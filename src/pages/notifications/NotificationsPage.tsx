import { useNavigate } from 'react-router-dom'

export default function MainPage() {
    const navigate = useNavigate()

    return (
        <div style={{ padding: '2rem' }}>
            <h1>알림 보관함</h1>
            <div style={{ display: 'flex', gap: '1rem', flexDirection: 'column', maxWidth: '200px' }}>
                <button onClick={() => navigate('/main')}>메인</button>
            </div>
        </div>
    )
}