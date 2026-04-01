export const COLORS = {
    // Primary
    primary: {
        main: '#8B5CF6',
        light: '#A78BFA',
        dark: '#7C3AED',
        bg: '#F3E8FF'
    },

    // Secondary
    secondary: {
        green: '#10B981',
        greenDark: '#059669',
        blue: '#3B82F6',
        red: '#EF4444',
        yellow: '#F59E0B',
        orange: '#F97316'
    },

    // Status Colors
    status: {
        active: '#DBEAFE',
        activeText: '#1E40AF',
        none: '#FEE2E2',
        noneText: '#991B1B',
        success: '#10B981',
        warning: '#F59E0B',
        error: '#EF4444'
    },

    // Text Colors
    text: {
        primary: '#1F2937',
        secondary: '#6B7280',
        tertiary: '#9CA3AF',
        disabled: '#D1D5DB'
    },

    // Background Colors
    bg: {
        white: '#FFFFFF',
        light: '#F9FAFB',
        gray: '#F3F4F6',
        border: '#E5E7EB'
    },

    // Gradient
    gradient: {
        gold: 'linear-gradient(135deg, #FEF3C7 0%, #FDE68A 100%)',
        purple: 'linear-gradient(135deg, #8B5CF6 0%, #7C3AED 100%)',
        green: 'linear-gradient(135deg, #10B981, #059669)',
        blue: 'linear-gradient(135deg, #6366F1, #4F46E5)'
    },

    // Ranking Colors
    ranking: {
        gold: '#FFD700',
        silver: '#C0C0C0',
        bronze: '#CD7F32'
    }
} as const

export type ColorTheme = typeof COLORS