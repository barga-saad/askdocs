package com.askdocs.ask;

import com.askdocs.ai.ChatClient;
import com.askdocs.ai.EmbeddingClient;
import com.askdocs.ask.AskDtos.AnswerMode;
import com.askdocs.ask.AskDtos.AskResponse;
import com.askdocs.ask.AskDtos.Source;
import com.askdocs.common.ApiException;
import com.askdocs.config.AskDocsProperties;
import com.askdocs.search.ChunkMatch;
import com.askdocs.search.ChunkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** The RAG flow: embed the question, retrieve the closest chunks, then answer from them. */
@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    private static final int EXCERPT_LENGTH = 400;
    private static final int EXCERPTS_IN_ANSWER = 2;
    private static final String NOT_FOUND = "I could not find this in your documents.";

    private final EmbeddingClient embeddings;
    private final ChunkRepository chunks;
    private final ChatClient chat;
    private final AskDocsProperties properties;
    private final MeterRegistry meters;

    public AskService(EmbeddingClient embeddings, ChunkRepository chunks, ChatClient chat,
                      AskDocsProperties properties, MeterRegistry meters) {
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.chat = chat;
        this.properties = properties;
        this.meters = meters;
    }

    public AskResponse ask(String question) {
        Timer.Sample timer = Timer.start(meters);
        AskResponse response = answer(question.strip());
        timer.stop(meters.timer("askdocs.ask.duration", "mode", response.mode().name().toLowerCase()));
        return response;
    }

    private AskResponse answer(String question) {
        List<ChunkMatch> matches = retrieve(question);
        // With no relevant source there is nothing to ground an answer on, so the model is not called.
        if (matches.isEmpty()) {
            return new AskResponse(NOT_FOUND, AnswerMode.NO_MATCH, List.of());
        }

        List<Source> sources = toSources(matches);
        if (properties.ai().chatEnabled()) {
            try {
                return new AskResponse(chat.answer(question, matches), AnswerMode.GENERATED, sources);
            } catch (RuntimeException ex) {
                log.warn("Answer generation failed, returning excerpts instead: {}", ex.getMessage());
                meters.counter("askdocs.chat.fallbacks").increment();
            }
        }
        return new AskResponse(excerptAnswer(sources), AnswerMode.EXCERPTS, sources);
    }

    private List<ChunkMatch> retrieve(String question) {
        float[] vector;
        try {
            vector = embeddings.embed(List.of(question)).get(0);
        } catch (RuntimeException ex) {
            log.error("Could not embed the question: {}", ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The search service is unavailable. Try again shortly.");
        }
        return chunks.search(vector, embeddings.modelName(), properties.search().topK()).stream()
                .filter(match -> match.score() >= properties.search().minScore())
                .toList();
    }

    private List<Source> toSources(List<ChunkMatch> matches) {
        List<Source> sources = new ArrayList<>(matches.size());
        for (int i = 0; i < matches.size(); i++) {
            ChunkMatch match = matches.get(i);
            sources.add(new Source(i + 1, match.documentId(), match.filename(), match.chunkIndex(),
                    excerpt(match.content()), Math.round(match.score() * 100) / 100.0));
        }
        return sources;
    }

    private String excerpt(String content) {
        return content.length() <= EXCERPT_LENGTH ? content : content.substring(0, EXCERPT_LENGTH).stripTrailing() + "…";
    }

    private String excerptAnswer(List<Source> sources) {
        return sources.stream()
                .limit(EXCERPTS_IN_ANSWER)
                .map(source -> "[%d] %s".formatted(source.number(), source.excerpt()))
                .collect(Collectors.joining("\n\n"));
    }
}
