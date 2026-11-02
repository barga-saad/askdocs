package com.askdocs.document;

import java.util.Locale;
import java.util.Optional;

public enum DocumentType {
    TEXT,
    MARKDOWN,
    PDF;

    public static Optional<DocumentType> fromFilename(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".txt")) {
            return Optional.of(TEXT);
        }
        if (name.endsWith(".md")) {
            return Optional.of(MARKDOWN);
        }
        return name.endsWith(".pdf") ? Optional.of(PDF) : Optional.empty();
    }
}
