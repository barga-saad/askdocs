package com.askdocs.document;

import com.askdocs.ai.EmbeddingClient;
import com.askdocs.search.ChunkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs on a background thread: extract, chunk, embed, then store. */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final TextExtractor extractor;
    private final TextChunker chunker;
    private final EmbeddingClient embeddings;
    private final TransactionTemplate transaction;
    private final MeterRegistry meters;

    public IngestionService(DocumentRepository documents, ChunkRepository chunks, TextExtractor extractor,
                            TextChunker chunker, EmbeddingClient embeddings, TransactionTemplate transaction,
                            MeterRegistry meters) {
        this.documents = documents;
        this.chunks = chunks;
        this.extractor = extractor;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.transaction = transaction;
        this.meters = meters;
    }

    public void ingest(UUID id, DocumentType type, byte[] content) {
        try {
            List<String> pieces = chunker.chunk(extractor.extract(type, content));
            if (pieces.isEmpty()) {
                fail(id, "No readable text was found in this file.");
                return;
            }
            // Embedding can be slow, so it runs before the transaction and holds no database connection.
            List<float[]> vectors = embeddings.embed(pieces);

            transaction.executeWithoutResult(status -> {
                chunks.insertAll(id, pieces, vectors, embeddings.modelName());
                documents.markReady(id, pieces.size());
            });
            count("ready");
            log.info("Ingested document {} into {} chunks", id, pieces.size());
        } catch (IOException ex) {
            log.warn("Could not read document {}: {}", id, ex.getMessage());
            fail(id, "This file could not be read. It may be damaged or password protected.");
        } catch (RuntimeException ex) {
            // Deleting a document mid-way is allowed: the foreign key rejects its chunks, and that is not a failure.
            if (documents.findById(id).isEmpty()) {
                log.info("Document {} was deleted during processing", id);
                return;
            }
            log.error("Ingestion failed for document {}", id, ex);
            fail(id, "Processing failed. Please try again.");
        }
    }

    private void fail(UUID id, String message) {
        documents.markFailed(id, message);
        count("failed");
    }

    private void count(String outcome) {
        meters.counter("askdocs.documents.ingested", "outcome", outcome).increment();
    }

    @Configuration
    static class StartupRecovery {

        @Bean
        ApplicationRunner failInterruptedDocuments(DocumentRepository documents) {
            return args -> {
                int interrupted = documents.failInterrupted("Processing was interrupted. Please upload the file again.");
                if (interrupted > 0) {
                    log.warn("Marked {} interrupted documents as failed", interrupted);
                }
            };
        }
    }
}
