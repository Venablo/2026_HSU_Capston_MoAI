export const STUDY_TAGS = [
    '#Frontend',
    '#TOEIC',
    '#기사',
    '#공무원',
    '#JAVA',
    '#Spring',
    '#Python',
    '#변리사',
    '#세무사',
    '#회계사',
    '#Language',
    '#Backend'
] as const

export type StudyTag = typeof STUDY_TAGS[number]

export const TAG_CATEGORIES = {
    DEVELOPMENT: ['#Frontend', '#Backend', '#JAVA', '#Spring', '#Python'],
    CERTIFICATION: ['#기사', '#공무원', '#변리사', '#세무사', '#회계사'],
    LANGUAGE: ['#TOEIC', '#Language']
} as const