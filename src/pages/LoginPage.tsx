import { useNavigate } from 'react-router-dom'

export default function LoginPage() {
    const navigate = useNavigate()

    return (
        <div style={{ padding: '2rem' }}>
            <h1>로그인</h1>
            <button onClick={() => navigate('/main')}>
                메인으로 이동
            </button>
        </div>
    )
}