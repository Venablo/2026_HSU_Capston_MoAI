import { useEffect, useState } from 'react'
import { BookOpen, Loader2, Save, UserCircle } from 'lucide-react'
import {
    getLearningHistory,
    getMyProfile,
    updateProfile,
} from '../../services/apiService'
import type { LearningHistoryItem, UserProfileFull } from '../../types/api'

export default function MyPage() {
    const [profile, setProfile] = useState<UserProfileFull | null>(null)
    const [history, setHistory] = useState<LearningHistoryItem[]>([])
    const [nickname, setNickname] = useState('')
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [message, setMessage] = useState('')

    useEffect(() => {
        let cancelled = false

        Promise.all([getMyProfile(), getLearningHistory()])
            .then(([profileData, historyData]) => {
                if (cancelled) return
                setProfile(profileData)
                setNickname(profileData.nickname)
                setHistory(historyData)
            })
            .catch(e => {
                if (!cancelled) setMessage(e instanceof Error ? e.message : '프로필을 불러오지 못했습니다.')
            })
            .finally(() => {
                if (!cancelled) setLoading(false)
            })

        return () => { cancelled = true }
    }, [])

    const handleSave = async () => {
        if (!profile || !nickname.trim()) return
        setSaving(true)
        setMessage('')
        try {
            const result = await updateProfile({ nickname: nickname.trim() })
            setProfile(prev => prev ? { ...prev, nickname: result.nickname } : prev)
            setMessage('저장되었습니다.')
        } catch (e) {
            setMessage(e instanceof Error ? e.message : '저장하지 못했습니다.')
        } finally {
            setSaving(false)
        }
    }

    if (loading) {
        return (
            <main style={{ padding: '32px', color: 'var(--color-text-secondary)' }}>
                <Loader2 size={20} className="animate-spin" /> 프로필을 불러오는 중...
            </main>
        )
    }

    return (
        <main style={{ padding: '32px', display: 'grid', gap: '20px' }}>
            <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px' }}>
                <div>
                    <h1 style={{ margin: 0, fontSize: '24px', color: 'var(--color-text-primary)' }}>마이페이지</h1>
                    <p style={{ margin: '6px 0 0', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
                        계정 정보와 학습 이력을 확인합니다.
                    </p>
                </div>
                <div style={{
                    width: '44px',
                    height: '44px',
                    borderRadius: '999px',
                    background: 'var(--color-purple-100)',
                    color: 'var(--color-purple-600)',
                    display: 'grid',
                    placeItems: 'center',
                }}>
                    <UserCircle size={28} strokeWidth={1.5} />
                </div>
            </header>

            <section style={{
                background: 'var(--color-surface, #fff)',
                border: '1px solid var(--color-border)',
                borderRadius: '8px',
                padding: '20px',
                display: 'grid',
                gap: '14px',
            }}>
                <h2 style={{ margin: 0, fontSize: '16px', color: 'var(--color-text-primary)' }}>프로필</h2>
                <label style={{ display: 'grid', gap: '6px', fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                    닉네임
                    <input
                        value={nickname}
                        onChange={e => setNickname(e.target.value)}
                        style={{
                            maxWidth: '320px',
                            padding: '10px 12px',
                            borderRadius: '8px',
                            border: '1px solid var(--color-border)',
                            background: 'var(--color-surface, #fff)',
                            color: 'var(--color-text-primary)',
                        }}
                    />
                </label>
                <div style={{ fontSize: '13px', color: 'var(--color-text-secondary)' }}>
                    {profile?.email} · {profile?.status}
                </div>
                <button
                    onClick={handleSave}
                    disabled={saving || !nickname.trim()}
                    style={{
                        width: 'fit-content',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '6px',
                        padding: '9px 14px',
                        borderRadius: '8px',
                        border: 'none',
                        background: 'var(--color-purple-500)',
                        color: '#fff',
                        cursor: saving ? 'default' : 'pointer',
                    }}
                >
                    {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                    저장
                </button>
                {message && <div style={{ fontSize: '13px', color: 'var(--color-text-secondary)' }}>{message}</div>}
            </section>

            <section style={{
                background: 'var(--color-surface, #fff)',
                border: '1px solid var(--color-border)',
                borderRadius: '8px',
                padding: '20px',
            }}>
                <h2 style={{ margin: '0 0 14px', fontSize: '16px', color: 'var(--color-text-primary)' }}>학습 이력</h2>
                {history.length === 0 ? (
                    <p style={{ margin: 0, fontSize: '13px', color: 'var(--color-text-secondary)' }}>아직 학습 이력이 없습니다.</p>
                ) : history.map(item => (
                    <div key={item.roomId} style={{
                        display: 'grid',
                        gridTemplateColumns: 'auto 1fr auto',
                        alignItems: 'center',
                        gap: '12px',
                        padding: '12px 0',
                        borderTop: '1px solid var(--color-border)',
                    }}>
                        <BookOpen size={18} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                        <div>
                            <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--color-text-primary)' }}>{item.subject}</div>
                            <div style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>{item.level} · {item.status}</div>
                        </div>
                        <div style={{ fontSize: '13px', color: 'var(--color-text-secondary)' }}>{item.completionRate}%</div>
                    </div>
                ))}
            </section>
        </main>
    )
}
