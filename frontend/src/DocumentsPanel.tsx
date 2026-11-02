import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import type { ChangeEvent } from 'react';
import { api } from './api';
import type { DocumentItem } from './api';
import { MAX_UPLOAD_BYTES, formatBytes } from './format';

function statusText(document: DocumentItem): string {
  switch (document.status) {
    case 'PROCESSING':
      return 'Processing…';
    case 'READY':
      return `${document.chunkCount} ${document.chunkCount === 1 ? 'chunk' : 'chunks'} · ${formatBytes(document.sizeBytes)}`;
    case 'FAILED':
      return document.error ?? 'Processing failed';
  }
}

export function DocumentsPanel() {
  const queryClient = useQueryClient();
  const [uploadError, setUploadError] = useState<string | null>(null);

  // Polls only while something is being processed, so an idle page sends no requests.
  const documents = useQuery({
    queryKey: ['documents'],
    queryFn: api.documents,
    refetchInterval: (query) => (query.state.data?.some((d) => d.status === 'PROCESSING') ? 1000 : false),
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['documents'] });

  const upload = useMutation({
    mutationFn: api.upload,
    onSuccess: refresh,
    onError: (error) => setUploadError(error.message),
  });

  const remove = useMutation({
    mutationFn: api.deleteDocument,
    onSuccess: refresh,
    onError: (error) => setUploadError(error.message),
  });

  function handleFiles(event: ChangeEvent<HTMLInputElement>) {
    setUploadError(null);
    for (const file of Array.from(event.target.files ?? [])) {
      if (file.size > MAX_UPLOAD_BYTES) {
        setUploadError(`${file.name} is larger than ${formatBytes(MAX_UPLOAD_BYTES)}.`);
      } else {
        upload.mutate(file);
      }
    }
    // Clearing the input lets the same file be chosen again.
    event.target.value = '';
  }

  return (
    <section className="panel documents" aria-label="Documents">
      <h2>Documents</h2>
      <label className="button primary upload">
        Upload files
        <input type="file" accept=".txt,.md,.pdf" multiple onChange={handleFiles} />
      </label>
      <p className="muted small">PDF, Markdown or text, up to {formatBytes(MAX_UPLOAD_BYTES)}.</p>

      {uploadError && <p className="error small" role="alert">{uploadError}</p>}
      {documents.error && <p className="error small" role="alert">{documents.error.message}</p>}
      {documents.data?.length === 0 && <p className="muted">No documents yet. Upload one to begin.</p>}

      <ul className="document-list">
        {documents.data?.map((document) => (
          <li key={document.id}>
            <div className="document-text">
              <div className="filename" title={document.filename}>{document.filename}</div>
              <div className={document.status === 'FAILED' ? 'error small' : 'muted small'}>{statusText(document)}</div>
            </div>
            <button
              className="link"
              aria-label={`Delete ${document.filename}`}
              disabled={remove.isPending}
              onClick={() => remove.mutate(document.id)}
            >
              Delete
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
