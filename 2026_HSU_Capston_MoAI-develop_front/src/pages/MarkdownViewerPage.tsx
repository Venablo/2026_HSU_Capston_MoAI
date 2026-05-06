import { useSearchParams } from 'react-router-dom'
import MarkdownViewer from '../components/MarkdownViewer'

export default function MarkdownViewerPage() {
    const [params] = useSearchParams()
    const url = params.get('url') ?? ''

    if (!url) {
        return (
            <div style={{ padding: '40px', textAlign: 'center', color: '#6b7280', fontSize: '14px' }}>
                표시할 파일 URL이 없습니다.
            </div>
        )
    }

    const filename = (() => {
        try { return decodeURIComponent(new URL(url).pathname.split('/').pop() ?? '') }
        catch { return '' }
    })()

    return (
        <div style={{ minHeight: '100vh', background: '#f9fafb' }}>
            <header style={{
                position: 'sticky', top: 0, zIndex: 10,
                background: '#fff', borderBottom: '1px solid #e5e7eb',
                padding: '12px 24px',
                display: 'flex', alignItems: 'center', gap: '12px',
            }}>
                <button
                    onClick={() => window.close()}
                    style={{
                        background: 'none', border: '1px solid #e5e7eb', borderRadius: '6px',
                        padding: '4px 12px', cursor: 'pointer', fontSize: '13px', color: '#374151',
                    }}
                >
                    닫기
                </button>
                {filename && (
                    <span style={{ fontSize: '14px', fontWeight: 600, color: '#111827' }}>
                        {filename}
                    </span>
                )}
            </header>
            <main style={{ maxWidth: '860px', margin: '0 auto', padding: '32px 24px' }}>
                <MarkdownViewer s3Url={url} />
            </main>
        </div>
    )
}
