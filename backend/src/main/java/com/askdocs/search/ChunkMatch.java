package com.askdocs.search;

import java.util.UUID;

public record ChunkMatch(UUID documentId, String filename, int chunkIndex, String content, double score) {
}
