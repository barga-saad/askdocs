package com.askdocs.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class TextExtractor {

    public String extract(DocumentType type, byte[] content) throws IOException {
        return switch (type) {
            case TEXT, MARKDOWN -> new String(content, StandardCharsets.UTF_8);
            case PDF -> extractPdf(content);
        };
    }

    private String extractPdf(byte[] content) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(pdf);
        }
    }
}
