package com.moai.backend.global.material;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    /**
     * 구조화된 학습 자료 콘텐츠를 PDF 바이트 배열로 변환한다.
     * 한글 렌더링을 위해 NanumGothic 폰트를 임베딩한다.
     */
    public byte[] generatePdf(MaterialContent content) throws IOException {
        try (PDDocument document = new PDDocument()) {
            // 클래스패스에서 한글 폰트 로드
            InputStream fontStream = new ClassPathResource(FONT_PATH).getInputStream();
            PDFont font = PDType0Font.load(document, fontStream);

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
    private void writePdfPages(PDDocument document, PDFont font, List<PdfLine> lines) throws IOException {
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
            List<String> wrappedLines = wrapText(line.text, font, fontSize, pageWidth);

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
    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
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
    private List<String> wrapByChar(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String candidate = current.toString() + c;
            float width = font.getStringWidth(candidate) / 1000 * fontSize;

            if (width > maxWidth && !current.isEmpty()) {
                result.add(current.toString());
                current = new StringBuilder().append(c);
            } else {
                current.append(c);
            }
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
    private record PdfLine(String text, float fontSize, boolean bold) {
        static PdfLine empty() {
            return new PdfLine(null, 0, false);
        }

        boolean isEmpty() {
            return text == null;
        }
    }
}
