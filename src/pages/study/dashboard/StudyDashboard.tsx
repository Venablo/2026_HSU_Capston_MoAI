import { useNavigate, useParams } from 'react-router-dom'
import { mockStudies } from '../../../constants/mockData'
import '../Study.css'

export default function StudyDashboard() {
    const navigate = useNavigate()
    const { studyId } = useParams()
    const currentStudy = mockStudies.find(s => s.id === Number(studyId))

    if (!currentStudy) {
        return (
            <div className="study-dashboard">
                <h1>스터디를 찾을 수 없습니다</h1>
                <button onClick={() => navigate('/my-studies')}>내 스터디로 돌아가기</button>
            </div>
        )
    }

    return (
        <div className="study-dashboard">
            <div className="study-header">
                <h1>{currentStudy.title}</h1>
                <div className="study-info">
          <span className={`badge ${currentStudy.role}`}>
            {currentStudy.role === 'owner' ? '방장' : '참여자'}
          </span>
                    <span>멤버 {currentStudy.memberCount}명</span>
                </div>
            </div>

            <h2>스터디 메뉴</h2>
            <div className="menu-grid">
                <button className="now-btn" onClick={() => navigate(`/study/${studyId}/dashboard`)}>대시보드</button>
                <button onClick={() => navigate(`/study/${studyId}/board`)}>게시판</button>
                <button onClick={() => navigate(`/study/${studyId}/classroom`)}>학습실</button>
                <button onClick={() => navigate(`/study/${studyId}/members`)}>멤버</button>
                <button onClick={() => navigate(`/study/${studyId}/calendar`)}>캘린더</button>
                <button onClick={() => navigate(`/study/${studyId}/ranking`)}>랭킹</button>
                {currentStudy.role === 'owner' && (
                    <button onClick={() => navigate(`/study/${studyId}/manage`)}>
                        관리
                    </button>
                )}
            </div>
        </div>
    )
}