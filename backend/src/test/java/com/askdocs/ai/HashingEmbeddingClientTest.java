package com.askdocs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class HashingEmbeddingClientTest {

    private final HashingEmbeddingClient client = new HashingEmbeddingClient();

    private float[] embed(String text) {
        return client.embed(List.of(text)).get(0);
    }

    private double cosine(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    @Test
    void producesTheRequiredDimensions() {
        assertThat(embed("hello world")).hasSize(EmbeddingClient.DIMENSIONS);
    }

    @Test
    void isRepeatable() {
        assertThat(embed("refund policy")).isEqualTo(embed("refund policy"));
    }

    @Test
    void producesUnitLengthVectors() {
        float[] vector = embed("a sentence about refund windows");

        assertThat(cosine(vector, vector)).isCloseTo(1.0, within(0.0001));
    }

    @Test
    void ranksSharedKeywordsAboveUnrelatedText() {
        float[] question = embed("How long is the refund window?");
        double related = cosine(question, embed("The refund window is thirty days from the purchase date."));
        double unrelated = cosine(question, embed("Our office is closed on public holidays."));

        assertThat(related).isGreaterThan(unrelated + 0.1);
    }

    @Test
    void ignoresStopWords() {
        assertThat(embed("the refund")).isEqualTo(embed("refund"));
    }

    @Test
    void neverReturnsAZeroVector() {
        assertThat(cosine(embed("the of and"), embed("the of and"))).isCloseTo(1.0, within(0.0001));
    }
}
