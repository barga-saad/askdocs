package com.askdocs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.askdocs.config.AskDocsProperties;
import com.askdocs.search.ChunkMatch;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Uses a real local HTTP server, so the request that goes over the wire is tested too. */
class OpenAiClientsTest {

    private HttpServer server;
    private String reply;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedLength = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private AskDocsProperties properties() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        return new AskDocsProperties(null, null, new AskDocsProperties.Ai(url, "", Duration.ofSeconds(5), true,
                "chat-model", AskDocsProperties.EmbeddingProvider.OPENAI, "embed-model"));
    }

    private OpenAiHttpClient http() {
        return new OpenAiHttpClient(properties(), JsonMapper.builder().build());
    }

    private String embeddingReply(int index, int dimensions) {
        String values = String.join(",", Collections.nCopies(dimensions, "0.5"));
        return "{\"index\":%d,\"embedding\":[%s]}".formatted(index, values);
    }

    @Test
    void sendsARequestWithAKnownLength() {
        reply = "{\"data\":[" + embeddingReply(0, EmbeddingClient.DIMENSIONS) + "]}";

        new OpenAiEmbeddingClient(http(), "embed-model").embed(List.of("hello"));

        assertThat(receivedLength.get()).isNotNull();
        assertThat(receivedBody.get()).contains("\"model\":\"embed-model\"").contains("hello");
    }

    @Test
    void keepsVectorsInInputOrder() {
        String first = embeddingReply(1, EmbeddingClient.DIMENSIONS);
        String second = embeddingReply(0, EmbeddingClient.DIMENSIONS).replace("0.5", "0.25");
        reply = "{\"data\":[" + first + "," + second + "]}";

        List<float[]> vectors = new OpenAiEmbeddingClient(http(), "embed-model").embed(List.of("a", "b"));

        assertThat(vectors.get(0)[0]).isEqualTo(0.25f);
        assertThat(vectors.get(1)[0]).isEqualTo(0.5f);
    }

    @Test
    void rejectsAModelWithTheWrongDimensions() {
        reply = "{\"data\":[" + embeddingReply(0, 10) + "]}";

        assertThatThrownBy(() -> new OpenAiEmbeddingClient(http(), "embed-model").embed(List.of("hello")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dimensions");
    }

    @Test
    void sendsTheNumberedSourcesToTheModel() {
        reply = "{\"choices\":[{\"message\":{\"content\":\"Thirty days [1].\"}}]}";
        ChunkMatch source = new ChunkMatch(UUID.randomUUID(), "policy.txt", 0, "Refunds take thirty days.", 0.9);

        String answer = new ChatClient(http(), properties()).answer("How long?", List.of(source));

        assertThat(answer).isEqualTo("Thirty days [1].");
        assertThat(receivedBody.get()).contains("[1] (policy.txt)").contains("Refunds take thirty days.").contains("How long?");
    }

    @Test
    void treatsAnEmptyAnswerAsAFailure() {
        reply = "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}";

        assertThatThrownBy(() -> new ChatClient(http(), properties()).answer("How long?", List.of()))
                .isInstanceOf(IllegalStateException.class);
    }
}
