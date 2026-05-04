import { Routes, Route } from 'react-router-dom'

import AppLayout from './components/AppLayout'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import MainPage from './pages/main/MainPage'
import MyStudiesPage from './pages/my-studies/MyStudiesPage'
import MyPage from './pages/my-page/MyPage'
import StudyClassroom from './pages/study/classroom/StudyClassroom'
import MarkdownViewerPage from './pages/MarkdownViewerPage'

function App() {
    return (
        <Routes>
            <Route path="/" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/md-viewer" element={<MarkdownViewerPage />} />
            <Route element={<AppLayout />}>
                <Route path="/main" element={<MainPage />} />
                <Route path="/my-studies" element={<MyStudiesPage />} />
                <Route path="/my-page" element={<MyPage />} />
                <Route path="/study/:studyId/classroom" element={<StudyClassroom />} />
            </Route>
        </Routes>
    )
}

export default App
