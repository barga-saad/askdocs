package com.askdocs.search;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ChunkRepository {

    private static final String INSERT = """
            INSERT INTO chunks (document_id, chunk_index, content, embedding, embedding_model)
            VALUES (?, ?, ?, CAST(? AS vector), ?)
            """;

    // <=> is pgvector's cosine distance. It is 0 for identical directions, so 1 - distance is a similarity.
    private static final String SEARCH = """
            SELECT c.document_id, d.filename, c.chunk_index, c.content,
                   1 - (c.embedding <=> CAST(:query AS vector)) AS score
            FROM chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE d.status = 'READY' AND c.embedding_model = :model
            ORDER BY c.embedding <=> CAST(:query AS vector)
            LIMIT :limit
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbcClient;

    public ChunkRepository(JdbcTemplate jdbcTemplate, JdbcClient jdbcClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcClient = jdbcClient;
    }

    public void insertAll(UUID documentId, List<String> chunks, List<float[]> vectors, String model) {
        List<Object[]> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            rows.add(new Object[] {documentId, i, chunks.get(i), toSqlVector(vectors.get(i)), model});
        }
        jdbcTemplate.batchUpdate(INSERT, rows);
    }

    public List<ChunkMatch> search(float[] query, String model, int limit) {
        return jdbcClient.sql(SEARCH)
                .param("query", toSqlVector(query))
                .param("model", model)
                .param("limit", limit)
                .query((rs, rowNum) -> new ChunkMatch(
                        rs.getObject("document_id", UUID.class),
                        rs.getString("filename"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("score")))
                .list();
    }

    private String toSqlVector(float[] vector) {
        StringBuilder sql = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sql.append(i == 0 ? "" : ",").append(vector[i]);
        }
        return sql.append(']').toString();
    }
}
