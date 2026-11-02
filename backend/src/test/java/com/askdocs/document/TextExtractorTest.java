package com.askdocs.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor();

    @Test
    void readsPlainTextAsUtf8() throws IOException {
        byte[] content = "Café déjà vu".getBytes(StandardCharsets.UTF_8);

        assertThat(extractor.extract(DocumentType.TEXT, content)).isEqualTo("Café déjà vu");
    }

    @Test
    void readsTextFromAPdf() throws IOException {
        assertThat(extractor.extract(DocumentType.PDF, pdfContaining("Refunds take five days"))).contains("Refunds take five days");
    }

    @Test
    void reportsADamagedPdf() {
        byte[] notAPdf = "this is not a pdf".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(DocumentType.PDF, notAPdf)).isInstanceOf(IOException.class);
    }

    @Test
    void recognisesSupportedFiles() {
        assertThat(DocumentType.fromFilename("Notes.TXT")).contains(DocumentType.TEXT);
        assertThat(DocumentType.fromFilename("readme.md")).contains(DocumentType.MARKDOWN);
        assertThat(DocumentType.fromFilename("report.pdf")).contains(DocumentType.PDF);
        assertThat(DocumentType.fromFilename("virus.exe")).isEmpty();
        assertThat(DocumentType.fromFilename("no-extension")).isEmpty();
    }

    private byte[] pdfContaining(String text) throws IOException {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            pdf.save(out);
            return out.toByteArray();
        }
    }
}
