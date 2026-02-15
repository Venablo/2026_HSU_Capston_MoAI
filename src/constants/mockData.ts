export interface Study {
    id: number
    title: string
    status: 'active' | 'none'
    role: 'owner' | 'member'
    memberCount: number
    startDate: string
    endDate: string
    tags: string[]
}

export const mockStudies: Study[] = [
    {
        id: 1,
        title: '정보처리기사 실기 준비',
        status: 'active',
        role: 'owner',
        memberCount: 12,
        startDate: '2024-01-01',
        endDate: '2024-04-30',
        tags: ['#자격증', '#코딩']
    },
    {
        id: 2,
        title: 'UI/UX 디자인 입문',
        status: 'active',
        role: 'member',
        memberCount: 13,
        startDate: '2024-02-01',
        endDate: '2024-05-31',
        tags: ['#디자인', '#Figma']
    },
    {
        id: 3,
        title: 'React 심화 과정',
        status: 'active',
        role: 'member',
        memberCount: 12,
        startDate: '2024-01-15',
        endDate: '2024-06-15',
        tags: ['#React']
    },
    {
        id: 4,
        title: '알고리즘 마스터',
        status: 'none',
        role: 'member',
        memberCount: 35,
        startDate: '2023-09-01',
        endDate: '2023-12-31',
        tags: ['#알고리즘']
    }
]