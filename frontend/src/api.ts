export type DocumentStatus = 'PROCESSING' | 'READY' | 'FAILED';
export type AnswerMode = 'GENERATED' | 'EXCERPTS' | 'NO_MATCH';

export interface DocumentItem {
  id: string;
  filename: string;
  type: string;
  sizeBytes: number;
  status: DocumentStatus;
  error: string | null;
  chunkCount: number;
  createdAt: string;
}

export interface Source {
  number: number;
  documentId: string;
  filename: string;
  chunkIndex: number;
  excerpt: string;
  score: number;
}

export interface AskResponse {
  answer: string;
  mode: AnswerMode;
  sources: Source[];
}

const BASE_URL = import.meta.env.VITE_API_URL ?? '';

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${BASE_URL}/api${path}`, init);
  if (response.ok) {
    return (response.status === 204 ? undefined : await response.json()) as T;
  }
  const problem = await response.json().catch(() => ({}));
  throw new Error(problem.detail ?? 'Something went wrong');
}

export const api = {
  documents: () => request<DocumentItem[]>('/documents'),

  // No Content-Type here: the browser must set it, because it includes the multipart boundary.
  upload: (file: File) => {
    const body = new FormData();
    body.append('file', file);
    return request<DocumentItem>('/documents', { method: 'POST', body });
  },

  deleteDocument: (id: string) => request<void>(`/documents/${id}`, { method: 'DELETE' }),

  ask: (question: string) =>
    request<AskResponse>('/ask', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question }),
    }),
};
