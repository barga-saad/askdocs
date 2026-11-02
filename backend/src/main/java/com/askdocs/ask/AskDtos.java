package com.askdocs.ask;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class AskDtos {

    private AskDtos() {
    }

    public record AskRequest(@NotBlank @Size(max = 1000) String question) {
    }

    /** How the answer was produced, so the UI can be honest about it. */
    public enum AnswerMode {
        GENERATED,
        EXCERPTS,
        NO_MATCH
    }

    public record Source(int number, UUID documentId, String filename, int chunkIndex, String excerpt, double score) {
    }

    public record AskResponse(String answer, AnswerMode mode, List<Source> sources) {
    }
}
