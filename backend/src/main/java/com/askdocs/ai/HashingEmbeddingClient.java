package com.askdocs.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A local embedding that needs no AI model. Words and word pairs are hashed into a fixed vector,
 * so it matches shared keywords rather than meaning. It lets the app run with zero setup.
 */
public class HashingEmbeddingClient implements EmbeddingClient {

    private static final double BIGRAM_WEIGHT = 0.5;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "can", "do", "does", "for", "from",
            "has", "have", "how", "i", "if", "in", "is", "it", "its", "many", "much", "my", "no", "not",
            "of", "on", "or", "our", "should", "so", "that", "the", "their", "there", "they", "this",
            "to", "was", "we", "were", "what", "when", "where", "which", "who", "why", "will", "with",
            "would", "you", "your");

    @Override
    public String modelName() {
        return "hashing-v1";
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream().map(this::embedOne).toList();
    }

    private float[] embedOne(String text) {
        List<String> tokens = tokenize(text);
        double[] vector = new double[DIMENSIONS];
        for (int i = 0; i < tokens.size(); i++) {
            add(vector, tokens.get(i), 1.0);
            if (i + 1 < tokens.size()) {
                add(vector, tokens.get(i) + " " + tokens.get(i + 1), BIGRAM_WEIGHT);
            }
        }
        return normalize(vector);
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty() && !STOP_WORDS.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    /** The sign comes from the hash too, so that unrelated collisions tend to cancel out. */
    private void add(double[] vector, String term, double weight) {
        int hash = mix(term.hashCode());
        vector[Math.floorMod(hash, DIMENSIONS)] += (hash & 0x4000_0000) == 0 ? weight : -weight;
    }

    private int mix(int hash) {
        int mixed = hash * 0x9E3779B1;
        return mixed ^ (mixed >>> 15);
    }

    private float[] normalize(double[] vector) {
        double sum = 0;
        for (double value : vector) {
            sum += value * value;
        }
        float[] result = new float[DIMENSIONS];
        if (sum == 0) {
            // Cosine distance is undefined for an all-zero vector.
            result[0] = 1;
            return result;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < DIMENSIONS; i++) {
            result[i] = (float) (vector[i] / norm);
        }
        return result;
    }
}
