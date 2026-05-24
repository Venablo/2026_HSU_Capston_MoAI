import { Routes, Route } from 'react-router-dom'

import AppLayout from './components/AppLayout'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import MainPage from './pages/main/MainPage'
import MyStudiesPage from './pages/my-studies/MyStudiesPage'
import MyPage from './pages/my-page/MyPage'
import StudyClassroom from './pages/study/classroom/StudyClassroom'
import FocusClassroom from './pages/study/focus/FocusClassroom'
import WeekSelector from './pages/study/curriculum/WeekSelector'
import MarkdownViewerPage from './pages/MarkdownViewerPage'

function App() {
    return (
        <Routes>
            <Route path="/" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/md-viewer" element={<MarkdownViewerPage />} />
            {/* 집중학습실: 사이드바 없는 풀스크린 (자체 Provider 포함) */}
            <Route path="/study/:studyId/focus" element={<FocusClassroom />} />
            <Route element={<AppLayout />}>
                <Route path="/main" element={<MainPage />} />
                <Route path="/my-studies" element={<MyStudiesPage />} />
                <Route path="/my-page" element={<MyPage />} />
                {/* 주차 선택 화면: 내 학습실 → 집중학습실 진입점 */}
                <Route path="/study/:studyId/curriculum" element={<WeekSelector />} />
                {/* 기존 학습실 (직접 URL 접근 또는 레거시 링크 유지) */}
                <Route path="/study/:studyId/classroom" element={<StudyClassroom />} />
            </Route>
        </Routes>
    )
}

export default App
