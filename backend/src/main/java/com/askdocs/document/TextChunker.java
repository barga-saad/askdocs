package com.askdocs.document;

import com.askdocs.config.AskDocsProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Splits text into overlapping pieces. Pieces end at a natural break where possible, and the
 * overlap keeps a sentence that falls on a boundary searchable from either side.
 */
@Component
public class TextChunker {

    private final int chunkSize;
    private final int overlap;

    @Autowired
    public TextChunker(AskDocsProperties properties) {
        this(properties.ingestion().chunkSize(), properties.ingestion().chunkOverlap());
    }

    TextChunker(int chunkSize, int overlap) {
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("The overlap must be smaller than the chunk size");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<String> chunk(String text) {
        String clean = normalize(text);
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < clean.length()) {
            int end = Math.min(start + chunkSize, clean.length());
            if (end < clean.length()) {
                end = findBreak(clean, start, end);
            }
            String chunk = clean.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end == clean.length()) {
                break;
            }
            start = nextStart(clean, start, end);
        }
        return chunks;
    }

    private String normalize(String text) {
        return text.replace('\0', ' ')
                .replace("\r\n", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    /** Prefers a paragraph, then a sentence, then a word. Only the second half is searched. */
    private int findBreak(String text, int start, int end) {
        int earliest = start + (end - start) / 2;
        for (String separator : List.of("\n\n", ". ", "\n", " ")) {
            int found = text.lastIndexOf(separator, end - separator.length());
            if (found >= earliest) {
                return found + separator.length();
            }
        }
        return end;
    }

    private int nextStart(String text, int start, int end) {
        int next = end - overlap;
        if (next <= start) {
            return end;
        }
        // Moves forward to a word start, so that the overlap never begins mid-word.
        int space = text.indexOf(' ', next);
        return space >= 0 && space + 1 < end ? space + 1 : next;
    }
}
