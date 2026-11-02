package com.askdocs;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Runs the full flow against a real PostgreSQL with pgvector, started in Docker for the test. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    private static final String POLICY = """
            Refund policy. Customers can request a refund within thirty days of the purchase date.
            Refunds are paid back to the original payment method.
            """;

    @Autowired
    private MockMvc mvc;

    @AfterEach
    void deleteDocuments() throws Exception {
        String list = mvc.perform(get("/api/documents")).andReturn().getResponse().getContentAsString();
        for (String id : JsonPath.<List<String>>read(list, "$[*].id")) {
            mvc.perform(delete("/api/documents/" + id)).andExpect(status().isNoContent());
        }
    }

    private ResultActions upload(String filename, String content) throws Exception {
        var file = new MockMultipartFile("file", filename, MediaType.TEXT_PLAIN_VALUE, content.getBytes(StandardCharsets.UTF_8));
        return mvc.perform(multipart("/api/documents").file(file));
    }

    private String uploadAndWait(String filename, String content, String expectedStatus) throws Exception {
        String response = upload(filename, content)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.id");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mvc.perform(get("/api/documents/" + id)).andExpect(jsonPath("$.status").value(expectedStatus)));
        return id;
    }

    private ResultActions ask(String question) throws Exception {
        return mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"%s\"}".formatted(question)));
    }

    @Test
    void answersFromAnUploadedDocumentWithItsSource() throws Exception {
        uploadAndWait("policy.txt", POLICY, "READY");

        ask("What is the refund window after the purchase date?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("EXCERPTS"))
                .andExpect(jsonPath("$.answer").value(containsString("thirty days")))
                .andExpect(jsonPath("$.sources[0].filename").value("policy.txt"))
                .andExpect(jsonPath("$.sources[0].number").value(1));
    }

    @Test
    void picksTheRelevantDocument() throws Exception {
        uploadAndWait("policy.txt", POLICY, "READY");
        uploadAndWait("holidays.txt", "The office closes on public holidays and during the winter break.", "READY");

        ask("When does the office close?").andExpect(jsonPath("$.sources[0].filename").value("holidays.txt"));
    }

    @Test
    void saysSoWhenTheAnswerIsNotInTheDocuments() throws Exception {
        uploadAndWait("policy.txt", POLICY, "READY");

        ask("Explain quantum chromodynamics")
                .andExpect(jsonPath("$.mode").value("NO_MATCH"))
                .andExpect(jsonPath("$.sources", hasSize(0)));
    }

    @Test
    void deletedDocumentsAreNoLongerSearched() throws Exception {
        String id = uploadAndWait("policy.txt", POLICY, "READY");

        mvc.perform(delete("/api/documents/" + id)).andExpect(status().isNoContent());

        ask("What is the refund window?").andExpect(jsonPath("$.mode").value("NO_MATCH"));
    }

    @Test
    void marksAnUnreadableFileAsFailed() throws Exception {
        String id = uploadAndWait("broken.pdf", "this is not a pdf", "FAILED");

        mvc.perform(get("/api/documents/" + id)).andExpect(jsonPath("$.error").value(containsString("could not be read")));
    }

    @Test
    void marksAFileWithNoTextAsFailed() throws Exception {
        uploadAndWait("blank.txt", "   \n\n  ", "FAILED");
    }

    @Test
    void rejectsUnsupportedFileTypes() throws Exception {
        upload("virus.exe", "MZ").andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rejectsEmptyFiles() throws Exception {
        upload("empty.txt", "").andExpect(status().isBadRequest());
    }

    @Test
    void keepsOnlyTheLastPartOfAFilePath() throws Exception {
        upload("..\\..\\secret\\notes.txt", POLICY).andExpect(jsonPath("$.filename").value("notes.txt"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mvc.perform(get("/api/documents")).andExpect(jsonPath("$[0].status").value("READY")));
    }

    @Test
    void rejectsABlankQuestion() throws Exception {
        ask("   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.question").exists());
    }

    @Test
    void reportsAMissingDocument() throws Exception {
        mvc.perform(get("/api/documents/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Document not found"));
    }

    @Test
    void exposesHealthChecks() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
