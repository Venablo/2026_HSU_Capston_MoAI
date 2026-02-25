export interface PrivacyOption {
    value: boolean
    title: string
    description: string
    icon?: string
}

export const PRIVACY_OPTIONS: PrivacyOption[] = [
    {
        value: false,
        title: '공개 그룹',
        description: '누구나 가입하고 검색 가능',
        icon: '🌐'
    },
    {
        value: true,
        title: '비공개 그룹',
        description: '초대를 통해서만 가입 가능',
        icon: '🔒'
    }
] as const

// 스터디 상태
export const STUDY_STATUS = {
    ACTIVE: 'ACTIVE',
    COMPLETED: 'COMPLETED',
    SUSPENDED: 'SUSPENDED'
} as const

export type StudyStatus = typeof STUDY_STATUS[keyof typeof STUDY_STATUS]

// 모집 상태
export const RECRUITMENT_STATUS = {
    RECRUITING: 'RECRUITING',
    CLOSED: 'CLOSED'
} as const

export type RecruitmentStatus = typeof RECRUITMENT_STATUS[keyof typeof RECRUITMENT_STATUS]

// 최대 인원 설정
export const MEMBER_LIMITS = {
    MIN: 4,
    MAX: 50,
    DEFAULT: 20
} as const

// 필터 타입
export type FilterType = 'all' | 'public' | 'private'

export const FILTER_OPTIONS = [
    { value: 'all' as const, label: '전체' },
    { value: 'public' as const, label: '공개 그룹' },
    { value: 'private' as const, label: '비공개 그룹' }
] as const