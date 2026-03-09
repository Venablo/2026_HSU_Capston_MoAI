import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Search, PenSquare, Eye, ChevronLeft, ChevronRight, Pin, ThumbsUp } from 'lucide-react'
import { mockStudies, toStudyHeaderProps, mockPosts, BOARD_CATEGORIES, CATEGORY_STYLE } from '../../../constants'
import type { BoardCategory } from '../../../constants'
import StudyHeader from '../../../components/Study/StudyHeader/StudyHeader'
import './StudyBoard.css'

{/* 한 페이지에 띄울 게시글 수 */}
const POSTS_PER_PAGE = 8

export default function StudyBoard() {
    const navigate = useNavigate()
    const { studyId } = useParams()
    const study = mockStudies.find(s => s.id === Number(studyId))

    const [activeCategory, setActiveCategory] = useState<BoardCategory>('전체')
    const [searchQuery, setSearchQuery] = useState('')
    const [currentPage, setCurrentPage] = useState(1)

    if (!study) {
        return (
            <div style={{ textAlign: 'center', padding: '4rem' }}>
                <h2>스터디를 찾을 수 없습니다</h2>
                <button onClick={() => navigate('/my-studies')}>내 스터디로 돌아가기</button>
            </div>
        )
    }

    const studyData = toStudyHeaderProps(study)

    const filtered = mockPosts.filter(p => {
        const matchCategory = activeCategory === '전체' || p.category === activeCategory
        const matchSearch = p.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
            p.author.includes(searchQuery)
        return matchCategory && matchSearch
    })

    const pinned = filtered.filter(p => p.isPinned)
    const normal = filtered.filter(p => !p.isPinned)
    const totalPages = Math.ceil(normal.length / POSTS_PER_PAGE)
    const paginated = normal.slice((currentPage - 1) * POSTS_PER_PAGE, currentPage * POSTS_PER_PAGE)
    const displayed = [...pinned, ...paginated]

    return (
        <div className="study-board-page">
            <StudyHeader {...studyData} />

            <div className="board-container">
                {/* 카테고리 탭, 검색, 글쓰기 */}
                <div className="board-toolbar">
                    <div className="board-categories">
                        {BOARD_CATEGORIES.map(cat => (
                            <button
                                key={cat}
                                className={`category-tab ${activeCategory === cat ? 'active' : ''}`}
                                onClick={() => { setActiveCategory(cat); setCurrentPage(1) }}
                            >
                                {cat}
                            </button>
                        ))}
                    </div>
                    <div className="board-actions">
                        <div className="board-search">
                            <Search size={16} />
                            <input
                                placeholder="제목, 작성자 검색..."
                                value={searchQuery}
                                onChange={e => { setSearchQuery(e.target.value); setCurrentPage(1) }}
                            />
                        </div>
                        <button className="write-btn">
                            <PenSquare size={16} />
                            글쓰기
                        </button>
                    </div>
                </div>

                {/* 게시글 목록 */}
                <div className="board-list">
                    <div className="board-list-header">
                        <span className="col-category">분류</span>
                        <span className="col-title">제목</span>
                        <span className="col-author">작성자</span>
                        <span className="col-date">날짜</span>
                        <span className="col-stats">조회 · 좋아요</span>
                    </div>

                    {displayed.length === 0 ? (
                        <div className="board-empty"><p>게시글이 없습니다.</p></div>
                    ) : (
                        displayed.map(post => (
                            <div
                                key={post.id}
                                className={`board-row ${post.isPinned ? 'pinned' : ''}`}
                                onClick={() => navigate(`/study/${studyId}/board/${post.id}`)}
                            >
                                <span className="col-category">
                                    {post.isPinned
                                        ? <span className="pin-badge"><Pin size={12} /> 공지</span>
                                        : <span className="category-badge" style={CATEGORY_STYLE[post.category]}>{post.category}</span>
                                    }
                                </span>
                                <span className="col-title">
                                    <span className="post-title">{post.title}</span>
                                    {post.comments > 0 && (
                                        <span className="comment-count">[{post.comments}]</span>
                                    )}
                                </span>
                                <span className="col-author">
                                    <span className="author-avatar">{post.avatar}</span>
                                    {post.author}
                                </span>
                                <span className="col-date">{post.date}</span>
                                <span className="col-stats">
                                    <span className="stat"><Eye size={13} />{post.views}</span>
                                    <span className="stat"><ThumbsUp size={13} />{post.likes}</span>
                                </span>
                            </div>
                        ))
                    )}
                </div>

                {/* 페이지 처리 */}
                {totalPages > 1 && (
                    <div className="board-pagination">
                        <button className="page-btn" onClick={() => setCurrentPage(p => Math.max(1, p - 1))} disabled={currentPage === 1}>
                            <ChevronLeft size={16} />
                        </button>
                        {Array.from({ length: totalPages }, (_, i) => i + 1).map(page => (
                            <button
                                key={page}
                                className={`page-btn ${currentPage === page ? 'active' : ''}`}
                                onClick={() => setCurrentPage(page)}
                            >
                                {page}
                            </button>
                        ))}
                        <button className="page-btn" onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))} disabled={currentPage === totalPages}>
                            <ChevronRight size={16} />
                        </button>
                    </div>
                )}
            </div>
        </div>
    )
}