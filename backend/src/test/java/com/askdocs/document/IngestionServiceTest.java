package com.askdocs.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.askdocs.ai.HashingEmbeddingClient;
import com.askdocs.search.ChunkRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class IngestionServiceTest {

    private static final UUID ID = UUID.randomUUID();
    private static final byte[] CONTENT = "Some readable text.".getBytes(StandardCharsets.UTF_8);

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final ChunkRepository chunks = mock(ChunkRepository.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final IngestionService service = new IngestionService(documents, chunks, new TextExtractor(),
            new TextChunker(100, 10), new HashingEmbeddingClient(), transaction, meters);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void runTransactionsDirectly() {
        doAnswer(call -> {
            call.getArgument(0, Consumer.class).accept(mock(TransactionStatus.class));
            return null;
        }).when(transaction).executeWithoutResult(any());
    }

    private void chunkInsertFails() {
        doThrow(new DataIntegrityViolationException("foreign key")).when(chunks).insertAll(any(), anyList(), anyList(), anyString());
    }

    private double count(String outcome) {
        return meters.counter("askdocs.documents.ingested", "outcome", outcome).count();
    }

    @Test
    void marksTheDocumentReady() {
        service.ingest(ID, DocumentType.TEXT, CONTENT);

        verify(documents).markReady(ID, 1);
        assertThat(count("ready")).isEqualTo(1);
    }

    @Test
    void ignoresADocumentDeletedDuringProcessing() {
        chunkInsertFails();
        when(documents.findById(ID)).thenReturn(Optional.empty());

        service.ingest(ID, DocumentType.TEXT, CONTENT);

        verify(documents, never()).markFailed(any(), anyString());
        assertThat(count("failed")).isZero();
    }

    @Test
    void reportsARealFailure() {
        chunkInsertFails();
        Document stillThere = new Document(ID, "a.txt", DocumentType.TEXT, 1, Document.Status.PROCESSING, null, 0, Instant.now());
        when(documents.findById(ID)).thenReturn(Optional.of(stillThere));

        service.ingest(ID, DocumentType.TEXT, CONTENT);

        verify(documents).markFailed(eq(ID), anyString());
        assertThat(count("failed")).isEqualTo(1);
    }
}
