package com.askdocs.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.askdocs.ai.ChatClient;
import com.askdocs.ai.EmbeddingClient;
import com.askdocs.ask.AskDtos.AnswerMode;
import com.askdocs.ask.AskDtos.AskResponse;
import com.askdocs.common.ApiException;
import com.askdocs.config.AskDocsProperties;
import com.askdocs.search.ChunkMatch;
import com.askdocs.search.ChunkRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AskServiceTest {

    private static final double MIN_SCORE = 0.3;

    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final ChunkRepository chunks = mock(ChunkRepository.class);
    private final ChatClient chat = mock(ChatClient.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        when(embeddings.modelName()).thenReturn("test-model");
        when(embeddings.embed(anyList())).thenReturn(List.of(new float[] {1f}));
    }

    private AskService service(boolean chatEnabled) {
        AskDocsProperties properties = new AskDocsProperties(null, new AskDocsProperties.Search(5, MIN_SCORE),
                new AskDocsProperties.Ai("http://unused", "", Duration.ofSeconds(1), chatEnabled, "chat",
                        AskDocsProperties.EmbeddingProvider.HASHING, "embed"));
        return new AskService(embeddings, chunks, chat, properties, meters);
    }

    private void searchReturns(double score) {
        ChunkMatch match = new ChunkMatch(UUID.randomUUID(), "policy.txt", 0, "Refunds take thirty days.", score);
        when(chunks.search(any(), anyString(), anyInt())).thenReturn(List.of(match));
    }

    @Test
    void doesNotCallTheModelWhenNothingIsRelevant() {
        searchReturns(MIN_SCORE - 0.01);

        AskResponse response = service(true).ask("Unrelated question");

        assertThat(response.mode()).isEqualTo(AnswerMode.NO_MATCH);
        assertThat(response.sources()).isEmpty();
        verifyNoInteractions(chat);
    }

    @Test
    void generatesAnAnswerWhenChatIsEnabled() {
        searchReturns(0.9);
        when(chat.answer(anyString(), anyList())).thenReturn("Thirty days [1].");

        AskResponse response = service(true).ask("How long do refunds take?");

        assertThat(response.mode()).isEqualTo(AnswerMode.GENERATED);
        assertThat(response.answer()).isEqualTo("Thirty days [1].");
        assertThat(response.sources()).hasSize(1);
    }

    @Test
    void returnsExcerptsWhenChatIsDisabled() {
        searchReturns(0.9);

        AskResponse response = service(false).ask("How long do refunds take?");

        assertThat(response.mode()).isEqualTo(AnswerMode.EXCERPTS);
        assertThat(response.answer()).startsWith("[1] Refunds take thirty days.");
        verifyNoInteractions(chat);
    }

    @Test
    void fallsBackToExcerptsWhenTheModelFails() {
        searchReturns(0.9);
        when(chat.answer(anyString(), anyList())).thenThrow(new IllegalStateException("model offline"));

        AskResponse response = service(true).ask("How long do refunds take?");

        assertThat(response.mode()).isEqualTo(AnswerMode.EXCERPTS);
        assertThat(meters.counter("askdocs.chat.fallbacks").count()).isEqualTo(1);
    }

    @Test
    void reportsUnavailableWhenTheQuestionCannotBeEmbedded() {
        when(embeddings.embed(anyList())).thenThrow(new IllegalStateException("embedding server offline"));

        assertThatThrownBy(() -> service(true).ask("How long do refunds take?"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("unavailable");
    }
}
