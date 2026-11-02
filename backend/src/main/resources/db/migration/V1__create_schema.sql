CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id          UUID PRIMARY KEY,
    filename    VARCHAR(255) NOT NULL,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('TEXT', 'MARKDOWN', 'PDF')),
    size_bytes  BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    error       VARCHAR(300),
    chunk_count INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_status ON documents (status);

-- The dimension must match EmbeddingClient.DIMENSIONS.
CREATE TABLE chunks (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id     UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index     INTEGER      NOT NULL,
    content         TEXT         NOT NULL,
    embedding       vector(768)  NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    UNIQUE (document_id, chunk_index)
);

-- HNSW finds nearest neighbours without comparing the query to every row.
CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);
