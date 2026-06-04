import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    BookOpen, Loader2, Save, UserCircle,
    Plus, X, Tag, Trash2, Eye, EyeOff, AlertTriangle,
} from 'lucide-react'
import {
    getLearningHistory,
    getMyProfile,
    updateProfile,
    updateKeywords,
    deleteAccount,
} from '../../services/apiService'
import { useAuth } from '../../context/AuthContext'
import type { LearningHistoryItem, UserProfileFull } from '../../types/api'

// ── Account deletion confirmation modal ──────────────────────────────────────
interface DeleteAccountModalProps {
    onConfirm: (password: string) => Promise<void>
    onCancel: () => void
}

function DeleteAccountModal({ onConfirm, onCancel }: DeleteAccountModalProps) {
    const [password, setPassword]   = useState('')
    const [showPw,   setShowPw]     = useState(false)
    const [deleting, setDeleting]   = useState(false)
    const [error,    setError]      = useState('')

    const handle = async () => {
        if (!password.trim()) return
        setDeleting(true)
        setError('')
        try {
            await onConfirm(password)
        } catch (e) {
            setError(e instanceof Error ? e.message : '탈퇴에 실패했습니다.')
            setDeleting(false)
        }
    }

    return (
        <div style={{
            position: 'fixed', inset: 0, zIndex: 999,
            background: 'rgba(0,0,0,.55)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
            <div style={{
                background: 'var(--color-surface, #fff)',
                border: '1px solid var(--color-border)',
                borderRadius: '12px',
                padding: '28px',
                width: '360px',
                display: 'grid',
                gap: '16px',
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <AlertTriangle size={20} style={{ color: '#ef4444', flexShrink: 0 }} />
                    <h3 style={{ margin: 0, fontSize: '16px', color: 'var(--color-text-primary)' }}>
                        회원 탈퇴
                    </h3>
                </div>
                <p style={{ margin: 0, fontSize: '13px', color: 'var(--color-text-secondary)', lineHeight: 1.6 }}>
                    탈퇴 시 모든 학습 데이터가 즉시 삭제되며 복구할 수 없습니다.<br />
                    계속하려면 비밀번호를 입력하세요.
                </p>

                <div style={{ position: 'relative' }}>
                    <input
                        type={showPw ? 'text' : 'password'}
                        value={password}
                        onChange={e => setPassword(e.target.value)}
                        onKeyDown={e => e.key === 'Enter' && handle()}
                        placeholder="비밀번호 입력"
                        style={{
                            width: '100%', boxSizing: 'border-box',
                            padding: '10px 40px 10px 12px',
                            borderRadius: '8px',
                            border: '1px solid var(--color-border)',
                            background: 'var(--color-surface, #fff)',
                            color: 'var(--color-text-primary)',
                        }}
                    />
                    <button
                        type="button"
                        onClick={() => setShowPw(v => !v)}
                        style={{
                            position: 'absolute', right: '10px', top: '50%',
                            transform: 'translateY(-50%)',
                            background: 'none', border: 'none', cursor: 'pointer',
                            color: 'var(--color-text-secondary)', display: 'flex',
                        }}
                    >
                        {showPw ? <EyeOff size={14} /> : <Eye size={14} />}
                    </button>
                </div>

                {error && (
                    <p style={{ margin: 0, fontSize: '12px', color: '#ef4444' }}>{error}</p>
                )}

                <div style={{ display: 'flex', gap: '8px' }}>
                    <button
                        onClick={onCancel}
                        disabled={deleting}
                        style={{
                            flex: 1, padding: '10px', borderRadius: '8px',
                            border: '1px solid var(--color-border)',
                            background: 'transparent',
                            color: 'var(--color-text-secondary)', cursor: 'pointer',
                        }}
                    >
                        취소
                    </button>
                    <button
                        onClick={handle}
                        disabled={deleting || !password.trim()}
                        style={{
                            flex: 1, padding: '10px', borderRadius: '8px', border: 'none',
                            background: deleting || !password.trim() ? '#fca5a5' : '#ef4444',
                            color: '#fff',
                            cursor: deleting || !password.trim() ? 'default' : 'pointer',
                            display: 'inline-flex',
                            alignItems: 'center', justifyContent: 'center', gap: '6px',
                        }}
                    >
                        {deleting
                            ? <Loader2 size={14} className="animate-spin" />
                            : <Trash2 size={14} />}
                        탈퇴하기
                    </button>
                </div>
            </div>
        </div>
    )
}

// ── Main component ────────────────────────────────────────────────────────────
export default function MyPage() {
    const navigate = useNavigate()
    const { clearAuth } = useAuth()

    const [profile,  setProfile]  = useState<UserProfileFull | null>(null)
    const [history,  setHistory]  = useState<LearningHistoryItem[]>([])
    const [nickname, setNickname] = useState('')
    const [loading,  setLoading]  = useState(true)
    const [saving,   setSaving]   = useState(false)
    const [message,  setMessage]  = useState('')

    // Interest keywords state
    const [keywords,       setKeywords]       = useState<string[]>([])
    const [kwEditMode,     setKwEditMode]      = useState(false)
    const [kwInput,        setKwInput]         = useState('')
    const [kwSaving,       setKwSaving]        = useState(false)
    const [kwMessage,      setKwMessage]       = useState('')

    // Account deletion state
    const [deleteModalOpen, setDeleteModalOpen] = useState(false)

    useEffect(() => {
        let cancelled = false
        Promise.all([getMyProfile(), getLearningHistory()])
            .then(([profileData, historyData]) => {
                if (cancelled) return
                setProfile(profileData)
                setNickname(profileData.nickname)
                setKeywords(profileData.interestKeywords ?? [])
                setHistory(historyData)
            })
            .catch(e => {
                if (!cancelled) setMessage(e instanceof Error ? e.message : '프로필을 불러오지 못했습니다.')
            })
            .finally(() => { if (!cancelled) setLoading(false) })
        return () => { cancelled = true }
    }, [])

    // ── Nickname save ─────────────────────────────────────────────────────────
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

    // ── Keywords save ─────────────────────────────────────────────────────────
    const handleSaveKeywords = async () => {
        setKwSaving(true)
        setKwMessage('')
        try {
            const result = await updateKeywords({ interestKeywords: keywords })
            setKeywords(result.interestKeywords)
            setProfile(prev => prev ? { ...prev, interestKeywords: result.interestKeywords } : prev)
            setKwEditMode(false)
            setKwMessage('키워드가 저장되었습니다.')
        } catch (e) {
            setKwMessage(e instanceof Error ? e.message : '저장하지 못했습니다.')
        } finally {
            setKwSaving(false)
        }
    }

    const handleAddKeyword = () => {
        const kw = kwInput.trim()
        if (!kw || keywords.includes(kw)) { setKwInput(''); return }
        setKeywords(prev => [...prev, kw])
        setKwInput('')
    }

    const handleRemoveKeyword = (kw: string) => {
        setKeywords(prev => prev.filter(k => k !== kw))
    }

    // ── Account deletion ──────────────────────────────────────────────────────
    const handleDeleteAccount = async (password: string) => {
        await deleteAccount({ password })
        clearAuth()
        navigate('/')
    }

    // ── Loading state ─────────────────────────────────────────────────────────
    if (loading) {
        return (
            <main style={{ padding: '32px', color: 'var(--color-text-secondary)' }}>
                <Loader2 size={20} className="animate-spin" /> 프로필을 불러오는 중...
            </main>
        )
    }

    return (
        <main style={{ padding: '32px', display: 'grid', gap: '20px', maxWidth: '640px' }}>
            <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px' }}>
                <div>
                    <h1 style={{ margin: 0, fontSize: '24px', color: 'var(--color-text-primary)' }}>마이페이지</h1>
                    <p style={{ margin: '6px 0 0', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
                        계정 정보와 학습 이력을 확인합니다.
                    </p>
                </div>
                <div style={{
                    width: '44px', height: '44px', borderRadius: '999px',
                    background: 'var(--color-purple-100)', color: 'var(--color-purple-600)',
                    display: 'grid', placeItems: 'center',
                }}>
                    <UserCircle size={28} strokeWidth={1.5} />
                </div>
            </header>

            {/* ── Profile section ── */}
            <section style={{
                background: 'var(--color-surface, #fff)',
                border: '1px solid var(--color-border)',
                borderRadius: '8px', padding: '20px', display: 'grid', gap: '14px',
            }}>
                <h2 style={{ margin: 0, fontSize: '16px', color: 'var(--color-text-primary)' }}>프로필</h2>
                <label style={{ display: 'grid', gap: '6px', fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                    닉네임
                    <input
                        value={nickname}
                        onChange={e => setNickname(e.target.value)}
                        style={{
                            maxWidth: '320px', padding: '10px 12px',
                            borderRadius: '8px', border: '1px solid var(--color-border)',
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
                        width: 'fit-content', display: 'inline-flex', alignItems: 'center', gap: '6px',
                        padding: '9px 14px', borderRadius: '8px', border: 'none',
                        background: 'var(--color-purple-500)', color: '#fff',
                        cursor: saving ? 'default' : 'pointer',
                    }}
                >
                    {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                    저장
                </button>
                {message && <div style={{ fontSize: '13px', color: 'var(--color-text-secondary)' }}>{message}</div>}
            </section>

            {/* ── Interest keywords section ── */}
            <section style={{
                background: 'var(--color-surface, #fff)',
                border: '1px solid var(--color-border)',
                borderRadius: '8px', padding: '20px', display: 'grid', gap: '14px',
            }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <h2 style={{ margin: 0, fontSize: '16px', color: 'var(--color-text-primary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <Tag size={16} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                        관심 키워드
                    </h2>
                    {!kwEditMode && (
                        <button
                            onClick={() => { setKwEditMode(true); setKwMessage('') }}
                            style={{
                                fontSize: '12px', padding: '5px 12px', borderRadius: '6px',
                                border: '1px solid var(--color-purple-300)',
                                background: 'transparent', color: 'var(--color-purple-600)',
                                cursor: 'pointer',
                            }}
                        >
                            편집
                        </button>
                    )}
                </div>

                {/* Tag chips */}
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                    {keywords.length === 0 && (
                        <span style={{ fontSize: '13px', color: 'var(--color-text-secondary)' }}>
                            등록된 키워드가 없습니다.
                        </span>
                    )}
                    {keywords.map(kw => (
                        <span
                            key={kw}
                            style={{
                                display: 'inline-flex', alignItems: 'center', gap: '4px',
                                padding: '4px 10px', borderRadius: '99px', fontSize: '12px',
                                background: 'var(--color-purple-50)', color: 'var(--color-purple-700)',
                                border: '1px solid var(--color-purple-200)',
                            }}
                        >
                            {kw}
                            {kwEditMode && (
                                <button
                                    onClick={() => handleRemoveKeyword(kw)}
                                    style={{
                                        background: 'none', border: 'none', cursor: 'pointer',
                                        color: 'var(--color-purple-400)', display: 'flex', padding: 0,
                                    }}
                                >
                                    <X size={12} strokeWidth={2} />
                                </button>
                            )}
                        </span>
                    ))}
                </div>

                {kwEditMode && (
                    <>
                        {/* Add keyword input */}
                        <div style={{ display: 'flex', gap: '8px', maxWidth: '320px' }}>
                            <input
                                value={kwInput}
                                onChange={e => setKwInput(e.target.value)}
                                onKeyDown={e => e.key === 'Enter' && handleAddKeyword()}
                                placeholder="키워드 입력 후 Enter"
                                style={{
                                    flex: 1, padding: '9px 12px', borderRadius: '8px',
                                    border: '1px solid var(--color-border)',
                                    background: 'var(--color-surface, #fff)',
                                    color: 'var(--color-text-primary)',
                                }}
                            />
                            <button
                                onClick={handleAddKeyword}
                                disabled={!kwInput.trim()}
                                style={{
                                    padding: '9px 14px', borderRadius: '8px', border: 'none',
                                    background: kwInput.trim() ? 'var(--color-purple-500)' : 'var(--color-purple-200)',
                                    color: '#fff', cursor: kwInput.trim() ? 'pointer' : 'default',
                                    display: 'inline-flex', alignItems: 'center', gap: '5px',
                                }}
                            >
                                <Plus size={14} strokeWidth={2} />
                                추가
                            </button>
                        </div>

                        {/* Save / Cancel */}
                        <div style={{ display: 'flex', gap: '8px' }}>
                            <button
                                onClick={handleSaveKeywords}
                                disabled={kwSaving}
                                style={{
                                    display: 'inline-flex', alignItems: 'center', gap: '6px',
                                    padding: '9px 14px', borderRadius: '8px', border: 'none',
                                    background: 'var(--color-purple-500)', color: '#fff',
                                    cursor: kwSaving ? 'default' : 'pointer',
                                }}
                            >
                                {kwSaving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                                저장
                            </button>
                            <button
                                onClick={() => {
                                    setKeywords(profile?.interestKeywords ?? [])
                                    setKwEditMode(false)
                                    setKwMessage('')
                                }}
                                disabled={kwSaving}
                                style={{
                                    padding: '9px 14px', borderRadius: '8px',
                                    border: '1px solid var(--color-border)',
                                    background: 'transparent',
                                    color: 'var(--color-text-secondary)', cursor: 'pointer',
                                }}
                            >
                                취소
                            </button>
                        </div>
                    </>
                )}

                {kwMessage && (
                    <div style={{ fontSize: '13px', color: 'var(--color-text-secondary)' }}>
                        {kwMessage}
                    </div>
                )}
            </section>

            {/* ── Learning history section ── */}
            <section style={{
                background: 'var(--color-surface, #fff)',
                border: '1px solid var(--color-border)',
                borderRadius: '8px', padding: '20px',
            }}>
                <h2 style={{ margin: '0 0 14px', fontSize: '16px', color: 'var(--color-text-primary)' }}>학습 이력</h2>
                {history.length === 0 ? (
                    <p style={{ margin: 0, fontSize: '13px', color: 'var(--color-text-secondary)' }}>
                        아직 학습 이력이 없습니다.
                    </p>
                ) : history.map(item => (
                    <div key={item.roomId} style={{
                        display: 'grid', gridTemplateColumns: 'auto 1fr auto',
                        alignItems: 'center', gap: '12px',
                        padding: '12px 0', borderTop: '1px solid var(--color-border)',
                    }}>
                        <BookOpen size={18} strokeWidth={1.5} style={{ color: 'var(--color-purple-500)' }} />
                        <div>
                            <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--color-text-primary)' }}>
                                {item.subject}
                            </div>
                            <div style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                                {item.level} · {item.status}
                            </div>
                        </div>
                        <div style={{ fontSize: '13px', color: 'var(--color-text-secondary)' }}>
                            {item.completionRate}%
                        </div>
                    </div>
                ))}
            </section>

            {/* ── Danger zone: account deletion ── */}
            <section style={{
                background: 'var(--color-surface, #fff)',
                border: '1px solid #fecaca',
                borderRadius: '8px', padding: '20px', display: 'grid', gap: '12px',
            }}>
                <h2 style={{ margin: 0, fontSize: '16px', color: '#dc2626' }}>위험 구역</h2>
                <p style={{ margin: 0, fontSize: '13px', color: 'var(--color-text-secondary)' }}>
                    회원 탈퇴 시 모든 학습 데이터가 즉시 삭제되며 복구할 수 없습니다.
                </p>
                <button
                    onClick={() => setDeleteModalOpen(true)}
                    style={{
                        width: 'fit-content', display: 'inline-flex', alignItems: 'center', gap: '6px',
                        padding: '9px 14px', borderRadius: '8px',
                        border: '1px solid #fca5a5', background: 'transparent',
                        color: '#dc2626', cursor: 'pointer', fontSize: '13px',
                    }}
                >
                    <Trash2 size={14} strokeWidth={1.5} />
                    회원 탈퇴
                </button>
            </section>

            {/* ── Delete account modal ── */}
            {deleteModalOpen && (
                <DeleteAccountModal
                    onConfirm={handleDeleteAccount}
                    onCancel={() => setDeleteModalOpen(false)}
                />
            )}
        </main>
    )
}
