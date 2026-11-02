package com.askdocs.document;

import com.askdocs.common.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final int MAX_FILENAME_LENGTH = 255;

    private final DocumentRepository documents;
    private final IngestionService ingestion;
    private final ThreadPoolTaskExecutor ingestionExecutor;

    public DocumentService(DocumentRepository documents, IngestionService ingestion,
                           ThreadPoolTaskExecutor ingestionExecutor) {
        this.documents = documents;
        this.ingestion = ingestion;
        this.ingestionExecutor = ingestionExecutor;
    }

    public Document upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file is empty");
        }
        String filename = safeFilename(file.getOriginalFilename());
        DocumentType type = DocumentType.fromFilename(filename).orElseThrow(() ->
                new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only .txt, .md and .pdf files are supported"));
        byte[] content = read(file);

        UUID id = UUID.randomUUID();
        documents.insertProcessing(id, filename, type, content.length);
        try {
            ingestionExecutor.execute(() -> ingestion.ingest(id, type, content));
        } catch (TaskRejectedException ex) {
            documents.delete(id);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Too many uploads are in progress. Try again shortly.");
        }
        return get(id);
    }

    public Document get(UUID id) {
        return documents.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    public List<Document> list() {
        return documents.findRecent();
    }

    public void delete(UUID id) {
        if (!documents.delete(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document not found");
        }
    }

    /** Browsers and scripts can send a full path as the name, so only the last part is kept. */
    private String safeFilename(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = Path.of(name.substring(name.lastIndexOf('/') + 1)).toString().strip();
        return name.length() > MAX_FILENAME_LENGTH ? name.substring(name.length() - MAX_FILENAME_LENGTH) : name;
    }

    private byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
