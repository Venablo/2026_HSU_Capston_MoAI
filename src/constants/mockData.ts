export interface Study {
    isPrivate: boolean;
    id: number
    title: string
    status: 'active' | 'none'
    role: 'owner' | 'member'
    memberCount: number
    startDate: string
    endDate: string
    tags: string[]
    studyCode: string
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
        tags: ['#자격증', '#코딩'],
        isPrivate: true,
        studyCode: '6915-8821'
    },
    {
        id: 2,
        title: 'UI/UX 디자인 입문',
        status: 'active',
        role: 'member',
        memberCount: 13,
        startDate: '2024-02-01',
        endDate: '2024-05-31',
        tags: ['#디자인', '#Figma'],
        isPrivate: true,
        studyCode: '3271-4490'
    },
    {
        id: 3,
        title: 'React 심화 과정',
        status: 'active',
        role: 'member',
        memberCount: 12,
        startDate: '2024-01-15',
        endDate: '2024-06-15',
        tags: ['#React'],
        isPrivate: true,
        studyCode: '8834-2201'
    },
    {
        id: 4,
        title: '알고리즘 마스터',
        status: 'none',
        role: 'member',
        memberCount: 35,
        startDate: '2023-09-01',
        endDate: '2023-12-31',
        tags: ['#알고리즘'],
        isPrivate: false,
        studyCode: '5523-7719'
    }
]

/** StudyHeader에 넘길 props 형태로 변환하는 헬퍼 */
export function toStudyHeaderProps(study: Study) {
    return {
        title: study.title,
        studyCode: study.studyCode,
        memberCount: study.memberCount,
        tags: study.tags.map(tag => tag.startsWith('#') ? tag : `#${tag}`)
    }
}