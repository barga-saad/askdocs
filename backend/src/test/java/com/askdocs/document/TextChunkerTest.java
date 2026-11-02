package com.askdocs.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(100, 20);

    @Test
    void keepsShortTextInOneChunk() {
        assertThat(chunker.chunk("  A short note.  ")).containsExactly("A short note.");
    }

    @Test
    void returnsNothingForBlankText() {
        assertThat(chunker.chunk(" \n\n \t ")).isEmpty();
    }

    @Test
    void neverExceedsTheChunkSize() {
        List<String> chunks = chunker.chunk("word ".repeat(500));

        assertThat(chunks).hasSizeGreaterThan(1).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(100));
    }

    @Test
    void prefersToEndAtAParagraph() {
        String first = "a".repeat(60) + " end of paragraph.";
        List<String> chunks = chunker.chunk(first + "\n\n" + "b ".repeat(100));

        assertThat(chunks.get(0)).isEqualTo(first);
    }

    @Test
    void repeatsTextBetweenChunks() {
        List<String> chunks = chunker.chunk("alpha beta gamma delta epsilon zeta eta theta iota kappa ".repeat(10));
        String endOfFirst = chunks.get(0).substring(chunks.get(0).length() - 10);

        assertThat(chunks.get(1)).contains(endOfFirst);
    }

    @Test
    void handlesTextWithNoSpaces() {
        assertThat(chunker.chunk("x".repeat(350))).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(100));
    }

    @Test
    void removesCharactersPostgresCannotStore() {
        assertThat(chunker.chunk("before\0after")).containsExactly("before after");
    }

    @Test
    void rejectsAnOverlapLargerThanTheChunk() {
        assertThatThrownBy(() -> new TextChunker(100, 100)).isInstanceOf(IllegalArgumentException.class);
    }
}
