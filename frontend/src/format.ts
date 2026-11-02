import type { AnswerMode } from './api';

export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function modeLabel(mode: AnswerMode): string {
  switch (mode) {
    case 'GENERATED':
      return 'AI answer';
    case 'EXCERPTS':
      return 'Matching passages';
    case 'NO_MATCH':
      return 'No match';
  }
}
