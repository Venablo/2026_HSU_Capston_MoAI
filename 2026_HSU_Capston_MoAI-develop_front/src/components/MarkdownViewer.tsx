import { Fragment, useEffect, useMemo, useState } from 'react'
import ReactMarkdown from 'react-markdown'

interface Props {
    s3Url: string
    className?: string
}

type Status = 'loading' | 'error' | 'done'

type MarkdownBlock =
    | { type: 'markdown'; content: string }
    | { type: 'table'; headers: string[]; rows: string[][] }

const markdownViewerTableCss = `
.markdown-viewer__paper {
    background: #ffffff;
    border: 1px solid rgba(17, 24, 39, 0.08);
    border-radius: 12px;
    box-shadow:
        0 28px 80px rgba(15, 23, 42, 0.16),
        0 2px 8px rgba(15, 23, 42, 0.06);
    color: #111827;
    min-height: calc(100vh - 180px);
    padding: 52px 60px;
}
.markdown-viewer__paper > :first-child {
    margin-top: 0;
}
.markdown-viewer__paper > :last-child {
    margin-bottom: 0;
}
.markdown-viewer__paper h1,
.markdown-viewer__paper h2,
.markdown-viewer__paper h3 {
    color: #111827;
}
.markdown-viewer__paper p,
.markdown-viewer__paper li {
    color: #263247;
    line-height: 1.78;
}
.markdown-viewer__table-wrap {
    position: relative;
    overflow-x: auto;
    margin: 24px 0 32px;
    border: 1px solid rgba(124, 92, 255, 0.18);
    border-radius: 14px;
    background: linear-gradient(180deg, rgba(255,255,255,0.96), rgba(248,250,255,0.92)), var(--color-surface, #fff);
    box-shadow: 0 14px 34px rgba(31, 41, 55, 0.08);
}
.markdown-viewer__table {
    width: 100%;
    min-width: 620px;
    border-collapse: separate;
    border-spacing: 0;
    color: var(--color-text-primary, #182033);
}
.markdown-viewer__table thead th {
    position: sticky;
    top: 0;
    z-index: 1;
    padding: 14px 16px;
    border-bottom: 1px solid rgba(124, 92, 255, 0.16);
    background: linear-gradient(180deg, rgba(245,240,255,0.96), rgba(238,244,255,0.96));
    color: var(--color-text-primary, #151b2d);
    font-size: 13px;
    font-weight: 800;
    text-align: left;
}
.markdown-viewer__table tbody td {
    padding: 14px 16px;
    border-top: 1px solid rgba(124, 92, 255, 0.1);
    color: var(--color-text-primary, #202940);
    font-size: 14px;
    line-height: 1.7;
    vertical-align: top;
}
.markdown-viewer__table th + th,
.markdown-viewer__table td + td {
    border-left: 1px solid rgba(124, 92, 255, 0.1);
}
.markdown-viewer__table tbody tr:nth-child(even) {
    background: rgba(248, 250, 255, 0.76);
}
.markdown-viewer__table tbody tr:hover {
    background: rgba(237, 242, 255, 0.9);
}
.markdown-viewer__table th:first-child,
.markdown-viewer__table td:first-child {
    width: 28%;
    font-weight: 700;
}
.markdown-viewer__table p {
    margin: 0;
}
.markdown-viewer__table strong {
    color: #5b4bdb;
    font-weight: 800;
}
.dark .markdown-viewer__table-wrap {
    border-color: rgba(167, 139, 250, 0.24);
    background: linear-gradient(180deg, rgba(31,35,54,0.98), rgba(24,28,45,0.96)), #181c2d;
    box-shadow: 0 18px 42px rgba(0, 0, 0, 0.26);
}
.dark .markdown-viewer__table thead th {
    border-bottom-color: rgba(167, 139, 250, 0.22);
    background: linear-gradient(180deg, rgba(52,43,86,0.98), rgba(35,45,75,0.96));
    color: #f7f7ff;
}
.dark .markdown-viewer__table tbody td {
    border-top-color: rgba(167, 139, 250, 0.14);
    color: #e7e9f5;
}
.dark .markdown-viewer__table th + th,
.dark .markdown-viewer__table td + td {
    border-left-color: rgba(167, 139, 250, 0.14);
}
.dark .markdown-viewer__table tbody tr:nth-child(even) {
    background: rgba(255, 255, 255, 0.035);
}
.dark .markdown-viewer__table tbody tr:hover {
    background: rgba(124, 92, 255, 0.14);
}
.dark .markdown-viewer__table strong {
    color: #c4b5fd;
}
@media (max-width: 720px) {
    .markdown-viewer__paper {
        border-radius: 10px;
        min-height: auto;
        padding: 28px 22px;
    }
}
`

function isTableRow(line: string): boolean {
    const trimmed = line.trim()
    return trimmed.includes('|') && trimmed.replace(/\|/g, '').trim().length > 0
}

function splitTableRow(line: string): string[] {
    const trimmed = line.trim().replace(/^\|/, '').replace(/\|$/, '')
    return trimmed.split('|').map(cell => cell.trim())
}

function isSeparatorRow(line: string): boolean {
    if (!isTableRow(line)) return false
    const cells = splitTableRow(line)
    return cells.length > 1 && cells.every(cell => /^:?-{3,}:?$/.test(cell))
}

function splitMarkdownTables(markdown: string): MarkdownBlock[] {
    const lines = markdown.split(/\r?\n/)
    const blocks: MarkdownBlock[] = []
    let markdownBuffer: string[] = []

    const flushMarkdown = () => {
        const content = markdownBuffer.join('\n')
        if (content.trim()) {
            blocks.push({ type: 'markdown', content })
        }
        markdownBuffer = []
    }

    for (let i = 0; i < lines.length; i += 1) {
        const current = lines[i]
        const next = lines[i + 1]

        if (isTableRow(current) && next && isSeparatorRow(next)) {
            flushMarkdown()
            const headers = splitTableRow(current)
            const rows: string[][] = []
            i += 2
            while (i < lines.length && isTableRow(lines[i]) && !isSeparatorRow(lines[i])) {
                const row = splitTableRow(lines[i])
                rows.push(headers.map((_, index) => row[index] ?? ''))
                i += 1
            }
            i -= 1
            blocks.push({ type: 'table', headers, rows })
        } else {
            markdownBuffer.push(current)
        }
    }

    flushMarkdown()
    return blocks
}

export default function MarkdownViewer({ s3Url, className }: Props) {
    const [content, setContent] = useState('')
    const [status, setStatus] = useState<Status>('loading')
    const blocks = useMemo(() => splitMarkdownTables(content), [content])

    useEffect(() => {
        if (!s3Url) return
        setStatus('loading')
        setContent('')

        const controller = new AbortController()

        // Pass auth token for backend API URLs; skip for direct S3/CDN URLs
        const token = localStorage.getItem('accessToken') ?? ''
        const isApiUrl = !/^https?:\/\/.*\.amazonaws\.com/i.test(s3Url) &&
                         !s3Url.includes('s3.') &&
                         s3Url.includes('/api/')
        const headers: HeadersInit = (isApiUrl && token)
            ? { Authorization: `Bearer ${token}` }
            : {}

        const fetchUrl = s3Url.includes('?') ? `${s3Url}&download=true` : `${s3Url}?download=true`
        fetch(fetchUrl, { signal: controller.signal, cache: 'no-store', headers })
            .then((res) => {
                if (!res.ok) throw new Error(`HTTP ${res.status}`)
                return res.text()
            })
            .then((text) => {
                setContent(text)
                setStatus('done')
            })
            .catch((err) => {
                if (err.name !== 'AbortError') setStatus('error')
            })

        return () => controller.abort()
    }, [s3Url])

    if (status === 'loading') {
        return (
            <div className={`flex items-center justify-center py-10 text-gray-400 ${className ?? ''}`}>
                <span className="animate-pulse">불러오는 중...</span>
            </div>
        )
    }

    if (status === 'error') {
        return (
            <div className={`flex items-center justify-center py-10 text-red-400 ${className ?? ''}`}>
                파일을 불러오지 못했습니다.
            </div>
        )
    }

    return (
        <div className={`markdown-viewer__paper prose prose-sm max-w-none dark:prose-invert ${className ?? ''}`}>
            <style>{markdownViewerTableCss}</style>
            {blocks.map((block, index) => (
                <Fragment key={index}>
                    {block.type === 'markdown' ? (
                        <ReactMarkdown>{block.content}</ReactMarkdown>
                    ) : (
                        <div className="markdown-viewer__table-wrap">
                            <table className="markdown-viewer__table">
                                <thead>
                                    <tr>
                                        {block.headers.map((header, cellIndex) => (
                                            <th key={cellIndex}>
                                                <ReactMarkdown>{header}</ReactMarkdown>
                                            </th>
                                        ))}
                                    </tr>
                                </thead>
                                <tbody>
                                    {block.rows.map((row, rowIndex) => (
                                        <tr key={rowIndex}>
                                            {row.map((cell, cellIndex) => (
                                                <td key={cellIndex}>
                                                    <ReactMarkdown>{cell}</ReactMarkdown>
                                                </td>
                                            ))}
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </Fragment>
            ))}
        </div>
    )
}
