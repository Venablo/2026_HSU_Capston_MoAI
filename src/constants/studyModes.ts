export type StudyMode = 'entry' | 'ai' | ''

export interface StudyModeOption {
    value: StudyMode
    title: string
    description: string
    icon?: string
}

export const STUDY_MODES: StudyModeOption[] = [
    {
        value: 'entry',
        title: '멘토링 모드',
        description: '방장이 자료와 일정을 직접 관리',
    },
    {
        value: 'ai',
        title: 'AI 코드 모드',
        description: 'AI가 로드맵, 퀴즈를 자동 생성',
    }
] as const