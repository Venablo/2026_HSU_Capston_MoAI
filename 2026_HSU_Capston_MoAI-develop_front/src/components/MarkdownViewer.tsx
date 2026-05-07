import { useEffect, useState } from 'react'
import ReactMarkdown from 'react-markdown'

interface Props {
    s3Url: string
    className?: string
}

type Status = 'loading' | 'error' | 'done'

export default function MarkdownViewer({ s3Url, className }: Props) {
    const [content, setContent] = useState('')
    const [status, setStatus] = useState<Status>('loading')

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

        fetch(s3Url, { signal: controller.signal, cache: 'no-store', headers })
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
        <div className={`prose prose-sm max-w-none dark:prose-invert ${className ?? ''}`}>
            <ReactMarkdown>{content}</ReactMarkdown>
        </div>
    )
}
