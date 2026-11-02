package com.askdocs.ai;

import com.askdocs.config.AskDocsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    EmbeddingClient embeddingClient(AskDocsProperties properties, OpenAiHttpClient http) {
        return switch (properties.ai().embeddingProvider()) {
            case HASHING -> new HashingEmbeddingClient();
            case OPENAI -> new OpenAiEmbeddingClient(http, properties.ai().embeddingModel());
        };
    }
}
