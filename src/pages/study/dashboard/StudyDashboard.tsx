import { useParams } from 'react-router-dom'
import { Target, TrendingUp, FileText, Sheet, Image as ImageIcon, Calendar as CalendarIcon, ChevronRight, MessageSquare } from 'lucide-react'
import { mockStudies, toStudyHeaderProps } from '../../../constants'
import StudyHeader from '../../../components/Study/StudyHeader/StudyHeader'

import './StudyDashboard.css'

export default function StudyDashboard() {
    const { studyId } = useParams()
    const study = mockStudies.find(s => s.id === Number(studyId))

    if (!study) {
        return (
            <div className="study-dashboard">
                <div style={{ textAlign: 'center', padding: '4rem' }}>
                    <h2>스터디를 찾을 수 없습니다.</h2>
                </div>
            </div>
        )
    }

    const studyData = toStudyHeaderProps(study)

    const weeklyGoal = {
        title: 'Week 4: 데이터베이스 SQL 활용',
        description: 'JOIN 및 서브쿼리 완벽 마스터하기'
    }

    const progress = 75

    const todos = [
        { id: 1, title: 'SQL JOIN 7가지 유형 시각화 자료 정리', completed: true, dueDate: '완료됨 · 오후 2:00' },
        { id: 2, title: '데이터베이스 기출문제 50문항 풀기', completed: false, dueDate: '진행 중 · 오후 6:00까지' },
        { id: 3, title: '오답노트 작성 (섹션 3 - 정규화)', completed: false, dueDate: '예정 · 오늘 오후 9:00' },
    ]

    const popularPosts = [
        { id: 1, number: '01', title: 'SQL JOIN 정리본 (이것만 보면 됨!)' },
        { id: 2, number: '02', title: '2024년 1회 기출문제 오답노트 공유합니다' },
    ]

    const recentFiles = [
        { id: 1, name: '수시시험_기출_요약.pdf', size: 'PDF · 1.2MB', date: '3시간 전' },
        { id: 2, name: '2024_예상출제범위.xlsx', size: 'Excel · 85KB', date: '5시간 전' },
        { id: 3, name: '오답노트_DB_섹션.jpg', size: 'Image · 2.4MB', date: '5시간 전' },
    ]

    const calendarEvents = [{ date: 10, hasEvent: true }]

    return (
        <div className="study-dashboard">
            <StudyHeader {...studyData} />

            {/* Alert Banner */}
            <div className="alert-banner">
                <span className="alert-icon">🔥</span>
                <span className="alert-text">
                    현재 3개의 소모둠이 진행 중입니다! 함께 참여하여 목표를 달성해보세요.
                </span>
                <button className="alert-action">
                    Click to view <ChevronRight size={16} />
                </button>
            </div>

            <div className="dashboard-grid">
                {/* Center : 오늘의 할 일 + 최근 인기글 */}
                <div className="dashboard-center">
                    {/* Left : 이번 주 학습 목표 + 나의 진도율 */}
                    <div className="dashboard-center-top">
                        {/* 이번 주 학습 목표 */}
                        <div className="dashboard-card">
                            <div className="card-icon purple">
                                <Target size={24} />
                            </div>
                            <h3 className="card-title">이번 주 학습 목표</h3>
                            <p className="goal-title">{weeklyGoal.title}</p>
                            <p className="goal-desc">{weeklyGoal.description}</p>
                        </div>

                        {/* 나의 진도율 */}
                        <div className="dashboard-card progress-card">
                            <div className="card-icon green">
                                <TrendingUp size={24} />
                            </div>
                            <h3 className="card-title">나의 진도율</h3>
                            <div className="progress-row">
                                <div>
                                    <p className="progress-value">{progress}%</p>
                                    <p className="progress-label">지난주 대비 +5%</p>
                                </div>
                                <div className="progress-circle">
                                    <svg width="80" height="80" viewBox="0 0 80 80">
                                        <circle cx="40" cy="40" r="34" fill="none" stroke="#F3F4F6" strokeWidth="8" />
                                        <circle
                                            cx="40" cy="40" r="34" fill="none"
                                            stroke="#8B5CF6" strokeWidth="8"
                                            strokeDasharray={`${2 * Math.PI * 34}`}
                                            strokeDashoffset={`${2 * Math.PI * 34 * (1 - progress / 100)}`}
                                            strokeLinecap="round"
                                            transform="rotate(-90 40 40)"
                                        />
                                    </svg>
                                    <div className="progress-text">{progress}%</div>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* 오늘의 할 일 */}
                    <section className="dashboard-section">
                        <h2 className="section-title">오늘의 할 일</h2>
                        <div className="todos-list">
                            {todos.map(todo => (
                                <div key={todo.id} className="todo-item">
                                    <input
                                        type="checkbox"
                                        checked={todo.completed}
                                        className="todo-checkbox"
                                        readOnly
                                    />
                                    <div className="todo-content">
                                        <p className={`todo-title ${todo.completed ? 'completed' : ''}`}>
                                            {todo.title}
                                        </p>
                                        <p className="todo-date">{todo.dueDate}</p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </section>

                    {/* 최근 인기글 */}
                    <section className="dashboard-section">
                        <div className="section-header">
                            <MessageSquare size={18} className="section-icon" />
                            <h2 className="section-title">최근 인기글</h2>
                        </div>
                        <div className="announcements-list">
                            {popularPosts.map(item => (
                                <div key={item.id} className="announcement-item">
                                    <span className="announcement-number">{item.number}</span>
                                    <p className="announcement-title">{item.title}</p>
                                </div>
                            ))}
                        </div>
                    </section>
                </div>

                {/* Right : 최근 학습 자료 + 스터디 현황 */}
                <div className="dashboard-right">
                    {/* 최근 학습 자료 */}
                    <div className="dashboard-card">
                        <div className="card-header">
                            <div className="card-icon-header">
                                <FileText size={20} />
                            </div>
                            <h3 className="card-title">최근 학습 자료</h3>
                            <button className="view-all">전체보기</button>
                        </div>
                        <div className="files-list">
                            {recentFiles.map(file => (
                                <div key={file.id} className="file-item">
                                    <div className="file-icon">
                                        {file.name.endsWith('.pdf') ? <FileText size={22} color="#EF4444" /> :
                                            file.name.endsWith('.xlsx') ? <Sheet size={22} color="#10B981" /> :
                                                <ImageIcon size={22} color="#8B5CF6" />}
                                    </div>
                                    <div className="file-info">
                                        <p className="file-name">{file.name}</p>
                                        <p className="file-meta">{file.size} · {file.date}</p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* 스터디 현황 (캘린더) */}
                    <section className="dashboard-section">
                        <div className="calendar-header">
                            <CalendarIcon size={20} className="calendar-icon" />
                            <h2 className="section-title">스터디 현황</h2>
                            <span className="calendar-date">2026년 3월</span>
                        </div>
                        <div className="mini-calendar">
                            <div className="calendar-weekdays">
                                {['S', 'M', 'T', 'W', 'T', 'F', 'S'].map((day, i) => (
                                    <div key={i} className="weekday">{day}</div>
                                ))}
                            </div>
                            <div className="calendar-days">
                                {[...Array(31)].map((_, i) => {
                                    const day = i + 1
                                    const hasEvent = calendarEvents.some(e => e.date === day)
                                    const isToday = day === 3
                                    return (
                                        <div key={day} className={`calendar-day ${isToday ? 'today' : ''} ${hasEvent ? 'has-event' : ''}`}>
                                            {day}
                                        </div>
                                    )
                                })}
                            </div>
                        </div>
                        <div className="calendar-event">
                            <div className="event-dot"></div>
                            <div className="event-info">
                                <p className="event-title">오늘의 일정</p>
                                <p className="event-time">오후 8:00 - 실기 예상 문제 풀이 (Zoom)</p>
                            </div>
                        </div>
                    </section>
                </div>
            </div>
        </div>
    )
}