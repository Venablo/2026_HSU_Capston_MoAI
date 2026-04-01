export type BoardCategory = '전체' | '공지' | '자유' | '자료' | '질문'
export type PostCategory = Exclude<BoardCategory, '전체'>

export interface Post {
    id: number
    category: PostCategory
    title: string
    author: string
    avatar: string
    date: string
    views: number
    likes: number
    comments: number
    isPinned?: boolean
}

export const BOARD_CATEGORIES: BoardCategory[] = ['전체', '공지', '자유', '자료', '질문']

export const CATEGORY_STYLE: Record<PostCategory, { bg: string; color: string }> = {
    '공지': { bg: '#FEF3C7', color: '#92400E' },
    '자유': { bg: '#DBEAFE', color: '#1E40AF' },
    '자료': { bg: '#D1FAE5', color: '#065F46' },
    '질문': { bg: '#EDE9FE', color: '#5B21B6' },
}

export const mockPosts: Post[] = [
    { id: 1,  category: '공지', title: '[필독] 4월 스터디 일정 및 규칙 안내',           author: '김방장', avatar: '김', date: '2026.03.01', views: 234, likes: 18, comments: 5,  isPinned: true },
    { id: 2,  category: '공지', title: '이번 주 zoom 링크 공유합니다',                   author: '김방장', avatar: '김', date: '2026.03.03', views: 189, likes: 12, comments: 3,  isPinned: true },
    { id: 3,  category: '자료', title: 'SQL JOIN 정리본 (이것만 보면 됨!)',               author: '이영희', avatar: '이', date: '2026.03.05', views: 312, likes: 47, comments: 14 },
    { id: 4,  category: '질문', title: 'INNER JOIN vs LEFT JOIN 차이점이 헷갈려요',      author: '박민수', avatar: '박', date: '2026.03.06', views: 98,  likes: 6,  comments: 8  },
    { id: 5,  category: '자유', title: '어제 퀴즈 너무 어려웠는데 다들 어떠셨나요?',     author: '최지원', avatar: '최', date: '2026.03.07', views: 143, likes: 23, comments: 19 },
    { id: 6,  category: '자료', title: '2024년 1회 기출문제 오답노트 공유합니다',         author: '정다은', avatar: '정', date: '2026.03.07', views: 276, likes: 38, comments: 11 },
    { id: 7,  category: '질문', title: '서브쿼리 vs JOIN 성능 차이 질문드립니다',         author: '오준혁', avatar: '오', date: '2026.03.08', views: 87,  likes: 4,  comments: 6  },
    { id: 8,  category: '자유', title: '오늘 스터디 너무 유익했어요 🙌',                  author: '한소희', avatar: '한', date: '2026.03.08', views: 64,  likes: 15, comments: 7  },
    { id: 9,  category: '자료', title: '정규화 1NF~3NF 핵심 정리 PDF',                   author: '이영희', avatar: '이', date: '2026.03.09', views: 198, likes: 31, comments: 9  },
    { id: 10, category: '질문', title: 'GROUP BY에서 HAVING절 사용 시 주의사항이 있나요?', author: '윤재호', avatar: '윤', date: '2026.03.09', views: 72,  likes: 3,  comments: 4  },
    { id: 11,  category: '자료', title: '테스트용 게시글 데이터@@@',               author: '이영희', avatar: '이', date: '2026.03.05', views: 312, likes: 47, comments: 14 },
    { id: 12,  category: '질문', title: '테스트용 게시글 데이터@@@@@@@',      author: '박민수', avatar: '박', date: '2026.03.06', views: 98,  likes: 6,  comments: 8  },
    { id: 13,  category: '자유', title: '테스트용 게시글 데이터#####',     author: '최지원', avatar: '최', date: '2026.03.07', views: 143, likes: 23, comments: 19 },
    { id: 14,  category: '자료', title: '테스트용 게시글 데이터@@@##########',         author: '정다은', avatar: '정', date: '2026.03.07', views: 276, likes: 38, comments: 11 },
    { id: 15,  category: '질문', title: '테스트용 게시글 데이터@@@$$$$$$$$$$$$$$$$$$',         author: '오준혁', avatar: '오', date: '2026.03.08', views: 87,  likes: 4,  comments: 6  },
    { id: 16,  category: '자유', title: '테스트용 게시글 데이터@@@***********************',                  author: '한소희', avatar: '한', date: '2026.03.08', views: 64,  likes: 15, comments: 7  },
    { id: 17,  category: '자료', title: '테스트용 게시글 데이터@@@',                   author: '이영희', avatar: '이', date: '2026.03.09', views: 198, likes: 31, comments: 9  },
    { id: 18, category: '질문', title: '테스트용 게시글 데이터@@@!!!!!!!!!!', author: '윤재호', avatar: '윤', date: '2026.03.09', views: 72,  likes: 3,  comments: 4  },

]