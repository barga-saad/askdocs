package com.askdocs.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("askdocs")
public record AskDocsProperties(@Valid @NotNull Ingestion ingestion,
                                @Valid @NotNull Search search,
                                @Valid @NotNull Ai ai) {

    public record Ingestion(@Min(100) int chunkSize, @Min(0) int chunkOverlap) {
    }

    public record Search(@Min(1) @Max(20) int topK, double minScore) {
    }

    public enum EmbeddingProvider {
        HASHING,
        OPENAI
    }

    public record Ai(@NotBlank String baseUrl,
                     String apiKey,
                     @NotNull Duration timeout,
                     boolean chatEnabled,
                     @NotBlank String chatModel,
                     @NotNull EmbeddingProvider embeddingProvider,
                     @NotBlank String embeddingModel) {
    }
}
