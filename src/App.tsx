import { Routes, Route } from 'react-router-dom'
import './App.css'

// Pages
import LoginPage from './pages/LoginPage'
import MainPage from './pages/main/MainPage.tsx'
import MyStudiesPage from './pages/my-studies/MyStudiesPage.tsx'
import MyCalendarPage from './pages/my-calendar/MyCalendarPage.tsx'
import NotificationsPage from './pages/notifications/NotificationsPage.tsx'
import MessagesPage from './pages/messages/MessagesPage.tsx'

// Study Pages
import StudyDashboard from './pages/study/dashboard/StudyDashboard.tsx'
import StudyBoard from './pages/study/board/StudyBoard.tsx'
import StudyClassroom from './pages/study/classroom/StudyClassroom.tsx'
import StudyMembers from './pages/study/menbers/StudyMembers.tsx'
import StudyCalendar from './pages/study/calendar/StudyCalendar.tsx'
import StudyRanking from './pages/study/ranking/StudyRanking.tsx'
import StudyManage from './pages/study/manage/StudyManage.tsx'

// Classroom Pages
import ClassroomViewer from './pages/study/classroom/viewer/ClassroomViewer.tsx'
import ClassroomQuiz from './pages/study/classroom/quiz/ClassroomQuiz.tsx'
import ClassroomVideo from './pages/study/classroom/video/ClassroomVideo.tsx'
import ClassroomAssignment from './pages/study/classroom/assignment/ClassroomAssignment.tsx'

// MyPage
import MyPageDashboard from './pages/my-page/dashboard/MyPageDashboard.tsx'
import MyPageArchive from './pages/my-page/archive/MyPageArchive.tsx'
import MyPageWrongAnswers from './pages/my-page/wrong-answers/MyPageWrongAnswers.tsx'
import MyPageSettings from './pages/my-page/settings/MyPageSettings.tsx'

function App() {
    return (
        <Routes>
            {/* 로그인 */}
            <Route path="/" element={<LoginPage />} />

            {/* 메인 */}
            <Route path="/main" element={<MainPage />} />
            <Route path="/my-studies" element={<MyStudiesPage />} />
            <Route path="/my-calendar" element={<MyCalendarPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/messages" element={<MessagesPage />} />

            {/* 스터디 */}
            <Route path="/study/:studyId">
                <Route index element={<StudyDashboard />} />
                <Route path="dashboard" element={<StudyDashboard />} />
                <Route path="board" element={<StudyBoard />} />
                <Route path="classroom" element={<StudyClassroom />} />
                <Route path="classroom/viewer" element={<ClassroomViewer />} />
                <Route path="classroom/quiz" element={<ClassroomQuiz />} />
                <Route path="classroom/video" element={<ClassroomVideo />} />
                <Route path="classroom/assignment" element={<ClassroomAssignment />} />
                <Route path="members" element={<StudyMembers />} />
                <Route path="calendar" element={<StudyCalendar />} />
                <Route path="ranking" element={<StudyRanking />} />
                <Route path="manage" element={<StudyManage />} />
            </Route>

            {/* 마이페이지 */}
            <Route path="/my-page">
                <Route index element={<MyPageDashboard />} />
                <Route path="dashboard" element={<MyPageDashboard />} />
                <Route path="archive" element={<MyPageArchive />} />
                <Route path="wrong-answers" element={<MyPageWrongAnswers />} />
                <Route path="settings" element={<MyPageSettings />} />
            </Route>
        </Routes>
    )
}

export default App