import { describe, expect, it } from 'vitest';
import { formatBytes, modeLabel } from './format';

describe('formatBytes', () => {
  it('picks a readable unit', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
  });
});

describe('modeLabel', () => {
  it('says how the answer was produced', () => {
    expect(modeLabel('GENERATED')).toBe('AI answer');
    expect(modeLabel('EXCERPTS')).toBe('Matching passages');
    expect(modeLabel('NO_MATCH')).toBe('No match');
  });
});
