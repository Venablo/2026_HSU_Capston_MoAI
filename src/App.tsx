import { Routes, Route } from 'react-router-dom'

import AppLayout from './components/AppLayout'
import LoginPage from './pages/LoginPage'
import MainPage from './pages/main/MainPage'
import MyStudiesPage from './pages/my-studies/MyStudiesPage'
import StudyDashboard from './pages/study/dashboard/StudyDashboard'
import StudyClassroom from './pages/study/classroom/StudyClassroom'

function App() {
    return (
        <Routes>
            <Route path="/" element={<LoginPage />} />
            <Route element={<AppLayout />}>
                <Route path="/main" element={<MainPage />} />
                <Route path="/my-studies" element={<MyStudiesPage />} />
                <Route path="/study/:studyId" element={<StudyDashboard />} />
                <Route path="/study/:studyId/dashboard" element={<StudyDashboard />} />
                <Route path="/study/:studyId/classroom" element={<StudyClassroom />} />
            </Route>
        </Routes>
    )
}

export default App