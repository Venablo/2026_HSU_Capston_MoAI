import { useNavigate } from 'react-router-dom'
import { mockStudies } from '../../constants/mockData'
import './MainPage.css'

export default function MainPage() {
    const navigate = useNavigate()
    const activeStudies = mockStudies.filter(s => s.status === 'active')
    const noneStudies = mockStudies.filter(s => s.status === 'none')

    return (
        <div className="main-page">
            <h1>메인 대시보드</h1>

            <div className="nav-buttons">
                <button onClick={() => navigate('/my-studies')}>내 스터디</button>
                <button onClick={() => navigate('/my-calendar')}>내 캘린더</button>
                <button onClick={() => navigate('/my-page')}>마이페이지</button>
                <button onClick={() => navigate('/notifications')}>알림 보관함</button>
                <button onClick={() => navigate('/messages')}>메시지</button>
            </div>

            <h2>진행 중인 스터디</h2>
            <div className="study-grid">
                {activeStudies.map(study => (
                    <div
                        key={study.id}
                        className="study-card"
                        onClick={() => navigate(`/study/${study.id}`)}
                    >
                        <h3>{study.title}</h3>
                        <p>멤버 {study.memberCount}명</p>
                        <div className="tags">
                            {study.tags.map(tag => (
                                <span key={tag} className="tag">{tag}</span>
                            ))}
                        </div>
                        <button className="active-btn" onClick={(e) => {
                            e.stopPropagation()
                            navigate(`/study/${study.id}`)
                        }}>
                            입장하기
                        </button>
                    </div>
                ))}
                {noneStudies.map(study => (
                    <div
                        key={study.id}
                        className="study-card"
                        onClick={() => navigate(`/study/${study.id}`)}
                    >
                        <h3>{study.title}</h3>
                        <p>멤버 {study.memberCount}명</p>
                        <div className="tags">
                            {study.tags.map(tag => (
                                <span key={tag} className="tag">{tag}</span>
                            ))}
                        </div>
                        <button onClick={(e) => {
                            e.stopPropagation()
                            navigate(`/study/${study.id}`)  //변경 필요 (alert로 신청하였습니다 띄우거나 해두기?)
                        }}>
                            신청하기
                        </button>
                    </div>
                ))}
            </div>
        </div>
    )
}