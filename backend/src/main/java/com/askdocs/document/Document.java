package com.askdocs.document;

import java.time.Instant;
import java.util.UUID;

public record Document(UUID id,
                       String filename,
                       DocumentType type,
                       long sizeBytes,
                       Status status,
                       String error,
                       int chunkCount,
                       Instant createdAt) {

    public enum Status {
        PROCESSING,
        READY,
        FAILED
    }
}
