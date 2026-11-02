package com.askdocs.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final int BATCH_SIZE = 32;

    private final OpenAiHttpClient http;
    private final String model;

    public OpenAiEmbeddingClient(OpenAiHttpClient http, String model) {
        this.http = http;
        this.model = model;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()));
            vectors.addAll(embedBatch(batch));
        }
        return vectors;
    }

    private List<float[]> embedBatch(List<String> batch) {
        JsonNode data = http.post("/embeddings", Map.of("model", model, "input", batch)).path("data");
        if (data.size() != batch.size()) {
            throw new IllegalStateException("Expected %d embeddings but received %d".formatted(batch.size(), data.size()));
        }

        // The API does not promise order, so each vector is placed using its index.
        float[][] vectors = new float[batch.size()][];
        for (JsonNode item : data) {
            vectors[item.path("index").asInt()] = toVector(item.path("embedding"));
        }
        return List.of(vectors);
    }

    private float[] toVector(JsonNode embedding) {
        if (embedding.size() != DIMENSIONS) {
            throw new IllegalStateException("Model '%s' returns %d dimensions, but %d are required"
                    .formatted(model, embedding.size(), DIMENSIONS));
        }
        float[] vector = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = embedding.get(i).floatValue();
        }
        return vector;
    }
}
