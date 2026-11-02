package com.askdocs.ai;

import java.util.List;

/** Turns text into vectors, so that similar meanings end up close together. */
public interface EmbeddingClient {

    /** Must match the vector(768) column in the database. */
    int DIMENSIONS = 768;

    /** Stored with each chunk: vectors from different models cannot be compared. */
    String modelName();

    List<float[]> embed(List<String> texts);
}
