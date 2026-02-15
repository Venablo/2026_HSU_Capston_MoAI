import { useNavigate } from 'react-router-dom'
import { mockStudies } from '../../constants/mockData'
import './MyStudiesPage.css'

export default function MyStudiesPage() {
    const navigate = useNavigate()
    const activeStudies = mockStudies.filter(s => s.status === 'active')

    return (
        <div className="my-studies-page">
            <h1>내 스터디</h1>
            <button onClick={() => navigate('/main')}>메인으로</button>

            <div className="studies-section">
                <h2>참가 중인 스터디 ({activeStudies.length})</h2>
                <div className="studies-grid">
                    {activeStudies.map(study => (
                        <div
                            key={study.id}
                            className="study-card active"
                            onClick={() => navigate(`/study/${study.id}`)}
                        >
                            <div className="card-header">
                <span className={`badge ${study.role}`}>
                  {study.role === 'owner' ? '방장' : '참여자'}
                </span>
                                <span className="member-count">멤버 {study.memberCount}명</span>
                            </div>

                            <h3>{study.title}</h3>

                            <div className="tags">
                                {study.tags.map(tag => (
                                    <span key={tag} className="tag">{tag}</span>
                                ))}
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    )
}