package com.askdocs.document;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    private static final int MAX_LISTED = 100;

    private final JdbcClient jdbc;

    public DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertProcessing(UUID id, String filename, DocumentType type, long sizeBytes) {
        jdbc.sql("""
                INSERT INTO documents (id, filename, type, size_bytes, status)
                VALUES (:id, :filename, :type, :size, 'PROCESSING')
                """)
                .param("id", id)
                .param("filename", filename)
                .param("type", type.name())
                .param("size", sizeBytes)
                .update();
    }

    public void markReady(UUID id, int chunkCount) {
        jdbc.sql("UPDATE documents SET status = 'READY', chunk_count = :count WHERE id = :id")
                .param("count", chunkCount)
                .param("id", id)
                .update();
    }

    public void markFailed(UUID id, String error) {
        jdbc.sql("UPDATE documents SET status = 'FAILED', error = :error WHERE id = :id")
                .param("error", error)
                .param("id", id)
                .update();
    }

    /** Work queued in memory is lost on a restart, so those documents can never finish. */
    public int failInterrupted(String error) {
        return jdbc.sql("UPDATE documents SET status = 'FAILED', error = :error WHERE status = 'PROCESSING'")
                .param("error", error)
                .update();
    }

    public Optional<Document> findById(UUID id) {
        return jdbc.sql("SELECT * FROM documents WHERE id = :id").param("id", id).query(this::map).optional();
    }

    public List<Document> findRecent() {
        return jdbc.sql("SELECT * FROM documents ORDER BY created_at DESC LIMIT :limit")
                .param("limit", MAX_LISTED)
                .query(this::map)
                .list();
    }

    public boolean delete(UUID id) {
        return jdbc.sql("DELETE FROM documents WHERE id = :id").param("id", id).update() > 0;
    }

    private Document map(ResultSet rs, int rowNum) throws SQLException {
        return new Document(
                rs.getObject("id", UUID.class),
                rs.getString("filename"),
                DocumentType.valueOf(rs.getString("type")),
                rs.getLong("size_bytes"),
                Document.Status.valueOf(rs.getString("status")),
                rs.getString("error"),
                rs.getInt("chunk_count"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
