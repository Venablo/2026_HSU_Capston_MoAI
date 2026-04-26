package com.moai.backend.global.material;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MaterialGeneratorService {

    private static final String FONT_PATH = "fonts/NanumGothic.ttf";

    // PDF 레이아웃 상수
    private static final float PAGE_MARGIN = 50f;
    private static final float TITLE_FONT_SIZE = 18f;
    private static final float HEADING_FONT_SIZE = 14f;
    private static final float BODY_FONT_SIZE = 11f;
    private static final float LINE_SPACING = 1.5f;
    private static final int PDF_IMAGE_WIDTH = 1240;
    private static final int PDF_IMAGE_HEIGHT = 1754;
    private static final int PDF_IMAGE_MARGIN = 90;
    private static final Color PDF_TEXT_COLOR = new Color(28, 35, 49);
    private static final Color PDF_MUTED_COLOR = new Color(94, 101, 118);
    private static final String[] PDF_FONT_FAMILIES = {
            "Malgun Gothic", "NanumGothic", "Segoe UI Emoji", "Dialog", "SansSerif"
    };
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)");

    public byte[] generateMarkdown(MaterialContent content) {
        return buildMarkdown(content).getBytes(StandardCharsets.UTF_8);
    }

    public String buildMarkdown(MaterialContent content) {
        String title = content.getTitle() != null && !content.getTitle().isBlank()
                ? content.getTitle()
                : "학습 자료";
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(title).append("\n\n");

        List<MaterialContent.Section> sections =
                content.getSections() != null ? content.getSections() : Collections.emptyList();

        for (MaterialContent.Section section : sections) {
            String heading = section.getHeading();
            if (heading != null && !heading.isBlank()) {
                markdown.append("## ").append(heading.trim()).append("\n\n");
            }

            String body = section.getContent();
            if (body != null && !body.isBlank()) {
                markdown.append(body.trim()).append("\n\n");
            }
        }

        return markdown.toString().trim() + "\n";
    }

    public String renderMarkdownHtml(String markdown, String fallbackTitle) {
        String title = extractMarkdownTitle(markdown, fallbackTitle);
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                  <style>
                    :root {
                      color-scheme: light;
                      --text: #1f2937;
                      --muted: #6b7280;
                      --line: #e5e7eb;
                      --accent: #7c3aed;
                      --surface: #ffffff;
                      --soft: #f5f3ff;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      background: #f7f3ff;
                      color: var(--text);
                      font-family: "Pretendard", "Apple SD Gothic Neo", "Malgun Gothic", system-ui, sans-serif;
                      line-height: 1.75;
                    }
                    .md {
                      width: min(940px, calc(100vw - 32px));
                      margin: 32px auto;
                      padding: 48px;
                      background: var(--surface);
                      border: 1px solid #ebe5ff;
                      border-radius: 8px;
                      box-shadow: 0 18px 45px rgba(72, 50, 139, 0.08);
                    }
                    h1, h2, h3, h4 {
                      margin: 1.6em 0 0.55em;
                      line-height: 1.35;
                      letter-spacing: 0;
                    }
                    h1 {
                      margin-top: 0;
                      padding-bottom: 18px;
                      border-bottom: 2px solid var(--accent);
                      font-size: 32px;
                    }
                    h2 { font-size: 24px; }
                    h3 { font-size: 20px; color: #4c1d95; }
                    h4 { font-size: 18px; }
                    p { margin: 0.75em 0; }
                    ul, ol { padding-left: 1.45rem; }
                    li { margin: 0.28em 0; }
                    blockquote {
                      margin: 1rem 0;
                      padding: 0.75rem 1rem;
                      border-left: 4px solid var(--accent);
                      background: var(--soft);
                      color: #4b5563;
                    }
                    code {
                      padding: 0.1rem 0.35rem;
                      border-radius: 6px;
                      background: #f3f4f6;
                      font-family: "JetBrains Mono", Consolas, monospace;
                      font-size: 0.92em;
                    }
                    pre {
                      overflow-x: auto;
                      padding: 1rem;
                      border-radius: 8px;
                      background: #111827;
                      color: #f9fafb;
                    }
                    pre code { padding: 0; background: transparent; color: inherit; }
                    table {
                      width: 100%%;
                      border-collapse: collapse;
                      margin: 1rem 0;
                      font-size: 0.95rem;
                    }
                    th, td {
                      padding: 0.7rem 0.8rem;
                      border: 1px solid var(--line);
                      text-align: left;
                      vertical-align: top;
                    }
                    th { background: var(--soft); }
                    hr { border: 0; border-top: 1px solid var(--line); margin: 1.5rem 0; }
                    @media (max-width: 640px) {
                      .md { width: 100%%; min-height: 100vh; margin: 0; padding: 28px 20px; border-radius: 0; }
                      h1 { font-size: 26px; }
                    }
                  </style>
                </head>
                <body>
                  <main class="md">%s</main>
                </body>
                </html>
                """.formatted(escapeHtml(title), markdownToHtml(markdown));
    }

    public byte[] generatePdfFromMarkdown(String markdown, String fallbackTitle) throws IOException {
        List<RenderLine> lines = buildRenderLines(markdown, fallbackTitle);
        List<BufferedImage> pages = renderMarkdownPages(lines);

        try (PDDocument document = new PDDocument()) {
            for (BufferedImage image : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                PDImageXObject pageImage = LosslessFactory.createFromImage(document, image);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.drawImage(pageImage, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * 구조화된 학습 자료 콘텐츠를 PDF 바이트 배열로 변환한다.
     * 한글 렌더링을 위해 NanumGothic 폰트를 임베딩한다.
     */
    public byte[] generatePdf(MaterialContent content) throws IOException {
        try (PDDocument document = new PDDocument()) {
            // 클래스패스에서 한글 폰트 로드
            InputStream fontStream = new ClassPathResource(FONT_PATH).getInputStream();
            PDType0Font font = PDType0Font.load(document, fontStream);

            // 전체 텍스트 라인을 먼저 구성한 뒤, 페이지에 순차적으로 배치
            List<PdfLine> lines = buildPdfLines(content);
            writePdfPages(document, font, lines);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    // --- PDF 내부 로직 ---

    /**
     * 콘텐츠를 PDF 렌더링용 라인 객체 리스트로 변환한다.
     * 각 라인은 폰트 크기와 볼드 여부, 텍스트를 포함한다.
     * sections나 content가 null인 경우를 안전하게 처리한다.
     */
    private List<PdfLine> buildPdfLines(MaterialContent content) {
        List<PdfLine> lines = new ArrayList<>();

        // 제목 + 빈 줄 (null 방어)
        String title = content.getTitle() != null ? content.getTitle() : "학습 자료";
        lines.add(new PdfLine(title, TITLE_FONT_SIZE, true));
        lines.add(PdfLine.empty());

        // sections가 null이면 빈 리스트로 처리
        List<MaterialContent.Section> sections =
                content.getSections() != null ? content.getSections() : Collections.emptyList();

        for (MaterialContent.Section section : sections) {
            // 섹션 제목 (null 방어)
            String heading = section.getHeading() != null ? section.getHeading() : "";
            lines.add(new PdfLine(heading, HEADING_FONT_SIZE, true));

            // 본문: null이면 빈 줄 하나로 처리
            String sectionContent = section.getContent();
            if (sectionContent != null && !sectionContent.isBlank()) {
                String[] bodyLines = sectionContent.split("\n");
                for (String bodyLine : bodyLines) {
                    lines.add(new PdfLine(bodyLine, BODY_FONT_SIZE, false));
                }
            }
            lines.add(PdfLine.empty());
        }

        return lines;
    }

    /**
     * PDF 라인 리스트를 페이지에 순차적으로 배치한다.
     * 페이지 하단에 도달하면 자동으로 새 페이지를 추가한다.
     * 긴 텍스트는 페이지 너비에 맞춰 자동 줄바꿈(word wrap)한다.
     */
    private void writePdfPages(PDDocument document, PDType0Font font, List<PdfLine> lines) throws IOException {
        float pageWidth = PDRectangle.A4.getWidth() - (PAGE_MARGIN * 2);
        float pageHeight = PDRectangle.A4.getHeight();
        float bottomMargin = PAGE_MARGIN;

        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDPageContentStream stream = new PDPageContentStream(document, page);
        float currentY = pageHeight - PAGE_MARGIN;

        for (PdfLine line : lines) {
            // 빈 줄 처리
            if (line.isEmpty()) {
                currentY -= BODY_FONT_SIZE * LINE_SPACING;
                if (currentY < bottomMargin) {
                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    currentY = pageHeight - PAGE_MARGIN;
                }
                continue;
            }

            float fontSize = line.fontSize;

            // 긴 텍스트를 페이지 너비에 맞게 여러 줄로 분할
            String safeText = sanitizeForFont(line.text, font);
            if (safeText.isBlank()) {
                currentY -= fontSize * LINE_SPACING;
                continue;
            }
            List<String> wrappedLines = wrapText(safeText, font, fontSize, pageWidth);

            for (String wrappedLine : wrappedLines) {
                // 페이지 하단 도달 시 새 페이지 생성
                if (currentY - (fontSize * LINE_SPACING) < bottomMargin) {
                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    currentY = pageHeight - PAGE_MARGIN;
                }

                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(PAGE_MARGIN, currentY);
                stream.showText(wrappedLine);
                stream.endText();

                currentY -= fontSize * LINE_SPACING;
            }
        }

        stream.close();
    }

    /**
     * 텍스트를 지정된 최대 너비에 맞게 단어 단위로 줄바꿈한다.
     * 한글은 공백이 적으므로, 글자 단위 폴백도 포함한다.
     */
    private List<String> wrapText(String text, PDType0Font font, float fontSize, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();

        if (text == null || text.isBlank()) {
            result.add("");
            return result;
        }

        // 공백 기준 분할 후, 너비 초과 시 줄바꿈
        String[] words = text.split("(?<=\\s)|(?=\\s)");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String candidate = currentLine + word;
            float width = font.getStringWidth(candidate.trim()) / 1000 * fontSize;

            if (width > maxWidth && !currentLine.isEmpty()) {
                result.add(currentLine.toString().trim());
                currentLine = new StringBuilder(word);
            } else {
                currentLine.append(word);
            }
        }

        if (!currentLine.isEmpty()) {
            result.add(currentLine.toString().trim());
        }

        // 단어 하나가 페이지 너비를 초과하는 경우 글자 단위로 분할
        List<String> finalResult = new ArrayList<>();
        for (String line : result) {
            float lineWidth = font.getStringWidth(line) / 1000 * fontSize;
            if (lineWidth <= maxWidth) {
                finalResult.add(line);
            } else {
                finalResult.addAll(wrapByChar(line, font, fontSize, maxWidth));
            }
        }

        return finalResult;
    }

    /**
     * 글자 단위 줄바꿈 — 공백 분할로도 너비를 초과하는 긴 단어/한글 문장 처리용
     */
    private List<String> wrapByChar(String text, PDType0Font font, float fontSize, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            String nextChar = new String(Character.toChars(codePoint));
            String candidate = current + nextChar;
            float width = font.getStringWidth(candidate) / 1000 * fontSize;

            if (width > maxWidth && !current.isEmpty()) {
                result.add(current.toString());
                current = new StringBuilder(nextChar);
            } else {
                current.append(nextChar);
            }
            i += Character.charCount(codePoint);
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        return result;
    }

    // --- 내부 DTO ---

    /**
     * PDF 렌더링용 라인 데이터. 빈 줄은 text=null로 표현한다.
     */
    private String sanitizeForFont(String text, PDType0Font font) throws IOException {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            if (codePoint == '\t') {
                result.append(' ');
            } else if (codePoint == '\n') {
                result.append('\n');
            } else if (!Character.isISOControl(codePoint) && canEncodeGlyph(font, codePoint)) {
                result.appendCodePoint(codePoint);
            }
            i += Character.charCount(codePoint);
        }
        return result.toString();
    }

    /**
     * hasGlyph()는 CMap 조회만 하므로 실제 인코딩 가능 여부와 불일치할 수 있다.
     * getStringWidth()로 직접 인코딩을 시도하여 실패 시 false를 반환한다.
     * (PDFBox 3.x에서 hasGlyph=true이더라도 getStringWidth가 예외를 던지는 케이스 방어)
     */
    private static boolean canEncodeGlyph(PDType0Font font, int codePoint) {
        try {
            if (!font.hasGlyph(codePoint)) return false;
            font.getStringWidth(new String(Character.toChars(codePoint)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String markdownToHtml(String markdown) {
        StringBuilder html = new StringBuilder();
        boolean inCodeBlock = false;
        boolean inUl = false;
        boolean inOl = false;
        boolean inParagraph = false;
        List<String> tableRows = new ArrayList<>();

        String[] lines = (markdown == null ? "" : markdown).replace("\r\n", "\n").split("\n", -1);
        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    html.append("</code></pre>\n");
                    inCodeBlock = false;
                } else {
                    flushParagraph(html, inParagraph);
                    inParagraph = false;
                    flushLists(html, inUl, inOl);
                    inUl = false;
                    inOl = false;
                    flushTable(html, tableRows);
                    html.append("<pre><code>");
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                html.append(escapeHtml(line)).append("\n");
                continue;
            }

            if (trimmed.isEmpty()) {
                flushParagraph(html, inParagraph);
                inParagraph = false;
                flushLists(html, inUl, inOl);
                inUl = false;
                inOl = false;
                flushTable(html, tableRows);
                continue;
            }

            if (isTableRow(trimmed)) {
                flushParagraph(html, inParagraph);
                inParagraph = false;
                flushLists(html, inUl, inOl);
                inUl = false;
                inOl = false;
                if (!isTableSeparator(trimmed)) {
                    tableRows.add(trimmed);
                }
                continue;
            }

            flushTable(html, tableRows);

            if (trimmed.startsWith("#")) {
                flushParagraph(html, inParagraph);
                inParagraph = false;
                flushLists(html, inUl, inOl);
                inUl = false;
                inOl = false;

                int level = Math.min(countPrefix(trimmed, '#'), 4);
                String text = trimmed.substring(level).trim();
                html.append("<h").append(level).append(">")
                        .append(renderInlineMarkdown(text))
                        .append("</h").append(level).append(">\n");
                continue;
            }

            if (trimmed.matches("^[-*+]\\s+.*")) {
                flushParagraph(html, inParagraph);
                inParagraph = false;
                if (!inUl) {
                    if (inOl) {
                        html.append("</ol>\n");
                        inOl = false;
                    }
                    html.append("<ul>\n");
                    inUl = true;
                }
                html.append("<li>").append(renderInlineMarkdown(trimmed.replaceFirst("^[-*+]\\s+", ""))).append("</li>\n");
                continue;
            }

            if (trimmed.matches("^\\d+\\.\\s+.*")) {
                flushParagraph(html, inParagraph);
                inParagraph = false;
                if (!inOl) {
                    if (inUl) {
                        html.append("</ul>\n");
                        inUl = false;
                    }
                    html.append("<ol>\n");
                    inOl = true;
                }
                html.append("<li>").append(renderInlineMarkdown(trimmed.replaceFirst("^\\d+\\.\\s+", ""))).append("</li>\n");
                continue;
            }

            if (trimmed.startsWith(">")) {
                flushParagraph(html, inParagraph);
                inParagraph = false;
                flushLists(html, inUl, inOl);
                inUl = false;
                inOl = false;
                html.append("<blockquote>").append(renderInlineMarkdown(trimmed.replaceFirst("^>\\s?", ""))).append("</blockquote>\n");
                continue;
            }

            if (trimmed.matches("^-{3,}$")) {
                flushParagraph(html, inParagraph);
                inParagraph = false;
                flushLists(html, inUl, inOl);
                inUl = false;
                inOl = false;
                html.append("<hr>\n");
                continue;
            }

            flushLists(html, inUl, inOl);
            inUl = false;
            inOl = false;

            if (!inParagraph) {
                html.append("<p>");
                inParagraph = true;
            } else {
                html.append("<br>");
            }
            html.append(renderInlineMarkdown(trimmed));
        }

        if (inCodeBlock) {
            html.append("</code></pre>\n");
        }
        flushParagraph(html, inParagraph);
        flushLists(html, inUl, inOl);
        flushTable(html, tableRows);
        return html.toString();
    }

    private List<RenderLine> buildRenderLines(String markdown, String fallbackTitle) {
        List<RenderLine> lines = new ArrayList<>();
        String source = markdown == null ? "" : markdown.replace("\r\n", "\n");

        for (String rawLine : source.split("\n", -1)) {
            if (rawLine.trim().isEmpty()) {
                lines.add(RenderLine.blank());
                continue;
            }
            // 마크다운 원본 그대로(모노스페이스)를 출력하여 표나 구조가 깨지지 않게 함
            lines.add(new RenderLine(rawLine, 16, false, true, 0, 8));
        }

        if (lines.isEmpty()) {
            lines.add(new RenderLine(extractMarkdownTitle(markdown, fallbackTitle), 16, true, false, 0, 20));
        }
        return lines;
    }

    private List<BufferedImage> renderMarkdownPages(List<RenderLine> lines) {
        List<BufferedImage> pages = new ArrayList<>();
        BufferedImage image = createPdfPageImage();
        Graphics2D graphics = image.createGraphics();
        setupGraphics(graphics);

        int maxWidth = PDF_IMAGE_WIDTH - (PDF_IMAGE_MARGIN * 2);
        int currentY = PDF_IMAGE_MARGIN;

        for (RenderLine line : lines) {
            if (line.isBlank()) {
                currentY += 18;
                if (currentY > PDF_IMAGE_HEIGHT - PDF_IMAGE_MARGIN) {
                    graphics.dispose();
                    pages.add(image);
                    image = createPdfPageImage();
                    graphics = image.createGraphics();
                    setupGraphics(graphics);
                    currentY = PDF_IMAGE_MARGIN;
                }
                continue;
            }

            Font font = createFont(line);
            List<String> wrappedLines = wrapForGraphics(line.text(), graphics, font, maxWidth - line.indent());
            for (String wrappedLine : wrappedLines) {
                int lineHeight = Math.max(line.fontSize() + 12, graphics.getFontMetrics(font).getHeight() + 4);
                if (currentY + lineHeight > PDF_IMAGE_HEIGHT - PDF_IMAGE_MARGIN) {
                    graphics.dispose();
                    pages.add(image);
                    image = createPdfPageImage();
                    graphics = image.createGraphics();
                    setupGraphics(graphics);
                    currentY = PDF_IMAGE_MARGIN;
                }

                FontMetrics metrics = graphics.getFontMetrics(font);
                int baseline = currentY + metrics.getAscent();
                Color color = line.fontSize() >= 23 ? PDF_TEXT_COLOR : PDF_MUTED_COLOR;
                drawFallbackString(graphics, wrappedLine, PDF_IMAGE_MARGIN + line.indent(), baseline, font, color);
                currentY += lineHeight;
            }
            currentY += line.afterGap();
        }

        graphics.dispose();
        pages.add(image);
        return pages;
    }

    private BufferedImage createPdfPageImage() {
        BufferedImage image = new BufferedImage(PDF_IMAGE_WIDTH, PDF_IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, PDF_IMAGE_WIDTH, PDF_IMAGE_HEIGHT);
        graphics.dispose();
        return image;
    }

    private void setupGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private Font createFont(RenderLine line) {
        String family = line.code() ? Font.MONOSPACED : PDF_FONT_FAMILIES[0];
        int style = line.bold() ? Font.BOLD : Font.PLAIN;
        return new Font(family, style, line.fontSize());
    }

    private List<String> wrapForGraphics(String text, Graphics2D graphics, Font font, int maxWidth) {
        List<String> result = new ArrayList<>();
        String source = text == null ? "" : text;
        if (source.isBlank()) {
            result.add("");
            return result;
        }

        FontMetrics metrics = graphics.getFontMetrics(font);
        String[] tokens = source.split("(?<=\\s)|(?=\\s)");
        StringBuilder current = new StringBuilder();

        for (String token : tokens) {
            String candidate = current + token;
            if (metrics.stringWidth(candidate) > maxWidth && !current.isEmpty()) {
                result.add(current.toString().trim());
                current = new StringBuilder(token);
            } else {
                current.append(token);
            }
        }

        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }

        List<String> finalResult = new ArrayList<>();
        for (String line : result) {
            if (metrics.stringWidth(line) <= maxWidth) {
                finalResult.add(line);
            } else {
                finalResult.addAll(wrapByCodePoint(line, graphics, font, maxWidth));
            }
        }
        return finalResult;
    }

    private List<String> wrapByCodePoint(String text, Graphics2D graphics, Font font, int maxWidth) {
        List<String> result = new ArrayList<>();
        FontMetrics metrics = graphics.getFontMetrics(font);
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            String next = new String(Character.toChars(codePoint));
            String candidate = current + next;
            if (metrics.stringWidth(candidate) > maxWidth && !current.isEmpty()) {
                result.add(current.toString());
                current = new StringBuilder(next);
            } else {
                current.append(next);
            }
            i += Character.charCount(codePoint);
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private void drawFallbackString(Graphics2D graphics, String text, int x, int baseline, Font preferred, Color color) {
        graphics.setColor(color);
        int cursorX = x;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            String glyph = new String(Character.toChars(codePoint));
            Font font = chooseDisplayFont(preferred, codePoint);
            graphics.setFont(font);
            graphics.drawString(glyph, cursorX, baseline);
            cursorX += graphics.getFontMetrics(font).stringWidth(glyph);
            i += Character.charCount(codePoint);
        }
    }

    private Font chooseDisplayFont(Font preferred, int codePoint) {
        if (preferred.canDisplay(codePoint)) {
            return preferred;
        }

        for (String family : PDF_FONT_FAMILIES) {
            Font candidate = new Font(family, preferred.getStyle(), preferred.getSize());
            if (candidate.canDisplay(codePoint)) {
                return candidate;
            }
        }
        return preferred;
    }

    private String extractMarkdownTitle(String markdown, String fallbackTitle) {
        if (markdown != null) {
            for (String line : markdown.replace("\r\n", "\n").split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#")) {
                    return cleanMarkdownText(trimmed.replaceFirst("^#+\\s*", ""));
                }
            }
        }
        return fallbackTitle != null && !fallbackTitle.isBlank() ? fallbackTitle : "학습 자료";
    }

    private String cleanMarkdownText(String text) {
        if (text == null) return "";
        String cleaned = LINK_PATTERN.matcher(text).replaceAll("$1 ($2)");
        cleaned = cleaned.replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("[x]", "☑")
                .replace("[X]", "☑")
                .replace("[ ]", "☐");
        return cleaned;
    }

    private String renderInlineMarkdown(String text) {
        String result = escapeHtml(text)
                .replace("[x]", "☑")
                .replace("[X]", "☑")
                .replace("[ ]", "☐");

        Matcher codeMatcher = INLINE_CODE_PATTERN.matcher(result);
        StringBuffer codeBuffer = new StringBuffer();
        while (codeMatcher.find()) {
            codeMatcher.appendReplacement(codeBuffer, "<code>" + Matcher.quoteReplacement(codeMatcher.group(1)) + "</code>");
        }
        codeMatcher.appendTail(codeBuffer);
        result = codeBuffer.toString();

        Matcher boldMatcher = BOLD_PATTERN.matcher(result);
        StringBuffer boldBuffer = new StringBuffer();
        while (boldMatcher.find()) {
            boldMatcher.appendReplacement(boldBuffer, "<strong>" + Matcher.quoteReplacement(boldMatcher.group(1)) + "</strong>");
        }
        boldMatcher.appendTail(boldBuffer);

        Matcher linkMatcher = LINK_PATTERN.matcher(boldBuffer.toString());
        StringBuffer linkBuffer = new StringBuffer();
        while (linkMatcher.find()) {
            String label = Matcher.quoteReplacement(linkMatcher.group(1));
            String href = Matcher.quoteReplacement(linkMatcher.group(2));
            linkMatcher.appendReplacement(linkBuffer, "<a href=\"" + href + "\" target=\"_blank\" rel=\"noopener noreferrer\">" + label + "</a>");
        }
        linkMatcher.appendTail(linkBuffer);
        return linkBuffer.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void flushParagraph(StringBuilder html, boolean inParagraph) {
        if (inParagraph) {
            html.append("</p>\n");
        }
    }

    private void flushLists(StringBuilder html, boolean inUl, boolean inOl) {
        if (inUl) {
            html.append("</ul>\n");
        }
        if (inOl) {
            html.append("</ol>\n");
        }
    }

    private void flushTable(StringBuilder html, List<String> tableRows) {
        if (tableRows.isEmpty()) {
            return;
        }

        html.append("<table>\n");
        for (int rowIndex = 0; rowIndex < tableRows.size(); rowIndex++) {
            String row = tableRows.get(rowIndex);
            html.append("<tr>");
            List<String> cells = splitTableRow(row);
            String tag = rowIndex == 0 ? "th" : "td";
            for (String cell : cells) {
                html.append("<").append(tag).append(">")
                        .append(renderInlineMarkdown(cell.trim()))
                        .append("</").append(tag).append(">");
            }
            html.append("</tr>\n");
        }
        html.append("</table>\n");
        tableRows.clear();
    }

    private List<String> splitTableRow(String row) {
        String trimmed = row.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return List.of(trimmed.split("\\|"));
    }

    private boolean isTableRow(String line) {
        return line.contains("|") && line.indexOf('|') != line.lastIndexOf('|');
    }

    private boolean isTableSeparator(String line) {
        return line.matches("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$");
    }

    private int countPrefix(String value, char prefix) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == prefix) {
            count++;
        }
        return count;
    }

    private record PdfLine(String text, float fontSize, boolean bold) {
        static PdfLine empty() {
            return new PdfLine(null, 0, false);
        }

        boolean isEmpty() {
            return text == null;
        }
    }

    private record RenderLine(String text, int fontSize, boolean bold, boolean code, int indent, int afterGap) {
        static RenderLine blank() {
            return new RenderLine(null, 0, false, false, 0, 0);
        }

        boolean isBlank() {
            return text == null;
        }
    }
}
