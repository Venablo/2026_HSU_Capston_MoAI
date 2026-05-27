import { Fragment, useMemo } from 'react'
import ReactMarkdown from 'react-markdown'

interface MarkdownContentProps {
    content: string
    className?: string
    paper?: boolean
    compact?: boolean
}

type MarkdownBlock =
    | { type: 'markdown'; content: string }
    | { type: 'table'; headers: string[]; rows: string[][] }

type MarkdownNode = {
    type: string
    value?: string
    children?: MarkdownNode[]
}

const markdownContentCss = `
.moai-markdown {
    color: var(--moai-markdown-text, var(--color-text-secondary, #263247));
    font-size: var(--moai-markdown-font-size, inherit);
    line-height: var(--moai-markdown-line-height, 1.7);
}
.moai-markdown > :first-child {
    margin-top: 0;
}
.moai-markdown > :last-child {
    margin-bottom: 0;
}
.moai-markdown h1 {
    font-size: 1.7em;
    font-weight: 800;
    color: var(--moai-markdown-heading, var(--color-text-primary, #111827));
    line-height: 1.3;
    margin: 1em 0 0.45em;
}
.moai-markdown h2 {
    font-size: 1.4em;
    font-weight: 700;
    color: var(--moai-markdown-heading, var(--color-text-primary, #111827));
    line-height: 1.35;
    margin: 0.9em 0 0.4em;
}
.moai-markdown h3 {
    font-size: 1.2em;
    font-weight: 700;
    color: #b45309;
    line-height: 1.35;
    margin: 0.8em 0 0.35em;
}
.moai-markdown h4 {
    font-size: 1.08em;
    font-weight: 700;
    color: #0f766e;
    line-height: 1.35;
    margin: 0.7em 0 0.3em;
}
.dark .moai-markdown h1 { color: #f9fafb; }
.dark .moai-markdown h2 { color: #f9fafb; }
.dark .moai-markdown h3 { color: #fbbf24; }
.dark .moai-markdown h4 { color: #4ade80; }
.moai-markdown p,
.moai-markdown li {
    color: inherit;
    line-height: inherit;
    overflow-wrap: anywhere;
}
.moai-markdown p {
    margin: 0 0 12px;
}
.moai-markdown ul,
.moai-markdown ol {
    margin: 10px 0 14px;
    padding-left: 1.3em;
}
.moai-markdown ul {
    list-style: disc;
}
.moai-markdown ol {
    list-style: decimal;
}
.moai-markdown li::marker {
    color: currentColor;
}
.moai-markdown li + li {
    margin-top: 6px;
}
.moai-markdown li > p {
    margin: 0;
}
.moai-markdown li > ul,
.moai-markdown li > ol {
    margin-top: 4px;
    margin-bottom: 2px;
}
.moai-markdown strong {
    color: var(--moai-markdown-strong, #5b4bdb);
    font-weight: 800;
}
.moai-markdown code {
    border-radius: 4px;
    background: rgba(15, 23, 42, 0.06);
    padding: 1px 4px;
    white-space: pre-wrap;
}
.moai-markdown pre {
    overflow-x: auto;
    border-radius: 8px;
    background: #111827;
    color: #f9fafb;
    padding: 12px;
    white-space: pre;
}
.moai-markdown pre code {
    background: transparent;
    padding: 0;
    white-space: pre;
}
.moai-markdown__table-wrap {
    position: relative;
    overflow-x: auto;
    margin: 16px 0 22px;
    border: 1px solid rgba(124, 92, 255, 0.18);
    border-radius: 12px;
    background: linear-gradient(180deg, rgba(255,255,255,0.96), rgba(248,250,255,0.92)), var(--color-surface, #fff);
    box-shadow: 0 12px 28px rgba(31, 41, 55, 0.08);
}
.moai-markdown__table {
    width: 100%;
    table-layout: fixed;
    border-collapse: separate;
    border-spacing: 0;
    color: var(--moai-markdown-table-text, var(--color-text-primary, #182033));
}
.moai-markdown__table th,
.moai-markdown__table td {
    word-break: break-word;
    overflow-wrap: anywhere;
}
.moai-markdown__table thead th {
    position: sticky;
    top: 0;
    z-index: 1;
    padding: var(--moai-markdown-table-head-padding, 14px 16px);
    border-bottom: 1px solid rgba(124, 92, 255, 0.16);
    background: linear-gradient(180deg, rgba(245,240,255,0.96), rgba(238,244,255,0.96));
    color: var(--moai-markdown-heading, #151b2d);
    font-size: var(--moai-markdown-table-head-size, 13px);
    font-weight: 800;
    line-height: 1.55;
    text-align: left;
    vertical-align: top;
}
.moai-markdown__table tbody td {
    padding: var(--moai-markdown-table-cell-padding, 14px 16px);
    border-top: 1px solid rgba(124, 92, 255, 0.1);
    color: inherit;
    font-size: inherit;
    line-height: inherit;
    vertical-align: top;
}
.moai-markdown__table th + th,
.moai-markdown__table td + td {
    border-left: 1px solid rgba(124, 92, 255, 0.1);
}
.moai-markdown__table tbody tr:nth-child(even) {
    background: rgba(248, 250, 255, 0.76);
}
.moai-markdown__table tbody tr:hover {
    background: rgba(237, 242, 255, 0.9);
}
.moai-markdown__table th:first-child,
.moai-markdown__table td:first-child {
    width: 28%;
    font-weight: 700;
}
.moai-markdown__table p {
    margin: 0;
}
.moai-markdown--paper {
    --moai-markdown-line-height: 1.78;
    --moai-markdown-text: #263247;
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
.moai-markdown--paper .moai-markdown__table-wrap {
    margin: 24px 0 32px;
    border-radius: 14px;
    box-shadow: 0 14px 34px rgba(31, 41, 55, 0.08);
}
.moai-markdown--compact {
    --moai-markdown-table-head-padding: 10px 12px;
    --moai-markdown-table-cell-padding: 10px 12px;
    --moai-markdown-table-head-size: 12px;
}
.moai-markdown--compact p {
    margin: 0 0 4px;
}
.moai-markdown--compact ul,
.moai-markdown--compact ol {
    margin: 4px 0 8px;
}
.moai-markdown--compact h1,
.moai-markdown--compact h2,
.moai-markdown--compact h3,
.moai-markdown--compact h4 {
    font-size: 1em;
    font-weight: 700;
    color: inherit;
    line-height: inherit;
    margin: 0.3em 0 0.1em;
}
.dark .moai-markdown__table-wrap {
    border-color: rgba(167, 139, 250, 0.24);
    background: linear-gradient(180deg, rgba(31,35,54,0.98), rgba(24,28,45,0.96)), #181c2d;
    box-shadow: 0 18px 42px rgba(0, 0, 0, 0.26);
}
.dark .moai-markdown__table thead th {
    border-bottom-color: rgba(167, 139, 250, 0.22);
    background: linear-gradient(180deg, rgba(52,43,86,0.98), rgba(35,45,75,0.96));
    color: #f7f7ff;
}
.dark .moai-markdown__table tbody td {
    border-top-color: rgba(167, 139, 250, 0.14);
    color: #e7e9f5;
}
.dark .moai-markdown__table th + th,
.dark .moai-markdown__table td + td {
    border-left-color: rgba(167, 139, 250, 0.14);
}
.dark .moai-markdown__table tbody tr:nth-child(even) {
    background: rgba(255, 255, 255, 0.035);
}
.dark .moai-markdown__table tbody tr:hover {
    background: rgba(124, 92, 255, 0.14);
}
.dark .moai-markdown strong {
    color: #c4b5fd;
}
@media (max-width: 720px) {
    .moai-markdown--paper {
        border-radius: 10px;
        min-height: auto;
        padding: 28px 22px;
    }
    .moai-markdown--compact {
        --moai-markdown-table-min-width: 360px;
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

// LLM이 구분선 행과 데이터 행을 한 줄에 이어 붙여 출력하는 경우를 분리한다.
// 예: "| :--- | :--- | | 데이터1 | 데이터2 | | 데이터3 | 데이터4 |"
// → "| :--- | :--- |" / "| 데이터1 | 데이터2 |" / "| 데이터3 | 데이터4 |"
function preNormalizeConcatenatedTable(content: string): string {
    const lines = content.split(/\r?\n/)
    const result: string[] = []

    for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed.startsWith('|')) { result.push(line); continue }

        const cells = splitTableRow(trimmed)
        let sepCount = 0
        for (const cell of cells) {
            if (/^:?-{3,}:?$/.test(cell)) sepCount++
            else break
        }

        // 구분선 셀이 없거나 전부 구분선 → 일반 행
        if (sepCount === 0 || sepCount === cells.length) { result.push(line); continue }

        // 구분선 셀 + 데이터 셀이 혼합 → 분리
        result.push('| ' + cells.slice(0, sepCount).join(' | ') + ' |')

        const remaining = cells.slice(sepCount)
        const groups: string[][] = []
        let cur: string[] = []
        for (const cell of remaining) {
            if (cell === '') {
                if (cur.length > 0) { groups.push(cur); cur = [] }
            } else {
                cur.push(cell)
            }
        }
        if (cur.length > 0) groups.push(cur)
        for (const group of groups) {
            result.push('| ' + group.join(' | ') + ' |')
        }
    }

    return result.join('\n')
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
            // 표준 형식: 헤더 행 다음에 구분선 행
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
        } else if (isSeparatorRow(current) && next && isTableRow(next) && !isSeparatorRow(next)) {
            // 헤더 없는 형식: 구분선이 첫 번째 행
            flushMarkdown()
            const columnCount = splitTableRow(current).length
            const rows: string[][] = []
            i += 1
            while (i < lines.length && isTableRow(lines[i]) && !isSeparatorRow(lines[i])) {
                const row = splitTableRow(lines[i])
                rows.push(Array.from({ length: columnCount }, (_, idx) => row[idx] ?? ''))
                i += 1
            }
            i -= 1
            blocks.push({ type: 'table', headers: [], rows })
        } else {
            markdownBuffer.push(current)
        }
    }

    flushMarkdown()
    return blocks
}

function normalizeInlineBreaks(value: string): string {
    return value.replace(/<br\s*\/?>/gi, '\n')
}

// # 제목이 있는 문서에서 ## 섹션 헤더를 ### 으로 강등한다.
// LLM이 h2를 써도 paper/compact 뷰에서 h3 수준으로만 나타나게 한다.
function normalizeHeadingLevels(content: string): string {
    const lines = content.split(/\r?\n/)
    const hasH1 = lines.some(line => /^# [^#]/.test(line))
    if (!hasH1) return content
    return lines.map(line => (/^## [^#]/.test(line) ? '#' + line : line)).join('\n')
}

function collapseBlankLines(content: string): string {
    // Collapse ALL blank lines except those immediately before/after heading lines,
    // and those adjacent to --- / === lines.
    //
    // Critical: --- immediately after text (no blank line) is parsed by CommonMark
    // as a setext-style h2 underline, turning the preceding text into a heading.
    // Preserving a blank line before --- prevents this misparse.
    const isHeading = (line: string) => /^#{1,4} /.test(line)
    const isHrOrSetext = (line: string) => /^-{3,}$|^={3,}$/.test(line.trim())

    const lines = content.split(/\r?\n/)
    const out: string[] = []
    let i = 0
    while (i < lines.length) {
        if (lines[i].trim() === '') {
            let j = i
            while (j < lines.length && lines[j].trim() === '') j++
            const prevLine = out.length > 0 ? out[out.length - 1] : ''
            const nextLine = j < lines.length ? lines[j] : ''
            if (isHeading(prevLine) || isHeading(nextLine) || isHrOrSetext(prevLine) || isHrOrSetext(nextLine)) {
                out.push('')
            }
            i = j
            continue
        }
        out.push(lines[i])
        i++
    }
    return out.join('\n')
}

function splitLooseStrongText(value: string): MarkdownNode[] {
    const parts: MarkdownNode[] = []
    const pattern = /\*\*([^*\n]+?)\*\*/g
    let lastIndex = 0
    let match: RegExpExecArray | null

    while ((match = pattern.exec(value)) !== null) {
        if (match.index > lastIndex) {
            parts.push({ type: 'text', value: value.slice(lastIndex, match.index) })
        }
        parts.push({
            type: 'strong',
            children: [{ type: 'text', value: match[1] }],
        })
        lastIndex = pattern.lastIndex
    }

    if (lastIndex < value.length) {
        parts.push({ type: 'text', value: value.slice(lastIndex) })
    }

    return parts.length > 0 ? parts : [{ type: 'text', value }]
}

function remarkLooseStrong() {
    return (tree: MarkdownNode) => {
        const visit = (node: MarkdownNode) => {
            if (!node.children) return

            node.children = node.children.flatMap((child) => {
                if (child.type === 'text' && child.value?.includes('**')) {
                    return splitLooseStrongText(child.value)
                }

                visit(child)
                return [child]
            })
        }

        visit(tree)
    }
}

const markdownRemarkPlugins = [remarkLooseStrong]

export default function MarkdownContent({
    content,
    className,
    paper = false,
    compact = false,
}: MarkdownContentProps) {
    const blocks = useMemo(
        () => splitMarkdownTables(preNormalizeConcatenatedTable(collapseBlankLines(normalizeHeadingLevels(content)))),
        [content],
    )
    const classes = [
        'moai-markdown',
        paper ? 'moai-markdown--paper' : '',
        compact ? 'moai-markdown--compact' : '',
        className ?? '',
    ].filter(Boolean).join(' ')

    return (
        <>
            <style>{markdownContentCss}</style>
            <div className={classes}>
                {blocks.map((block, index) => (
                    <Fragment key={index}>
                        {block.type === 'markdown' ? (
                            <ReactMarkdown remarkPlugins={markdownRemarkPlugins}>
                                {normalizeInlineBreaks(block.content)}
                            </ReactMarkdown>
                        ) : (
                            <div className="moai-markdown__table-wrap">
                                <table className="moai-markdown__table">
                                    {block.headers.length > 0 && (
                                        <thead>
                                            <tr>
                                                {block.headers.map((header, cellIndex) => (
                                                    <th key={cellIndex}>
                                                        <ReactMarkdown remarkPlugins={markdownRemarkPlugins}>
                                                            {normalizeInlineBreaks(header)}
                                                        </ReactMarkdown>
                                                    </th>
                                                ))}
                                            </tr>
                                        </thead>
                                    )}
                                    <tbody>
                                        {block.rows.map((row, rowIndex) => (
                                            <tr key={rowIndex}>
                                                {row.map((cell, cellIndex) => (
                                                <td key={cellIndex}>
                                                    <ReactMarkdown remarkPlugins={markdownRemarkPlugins}>
                                                        {normalizeInlineBreaks(cell)}
                                                    </ReactMarkdown>
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
        </>
    )
}
