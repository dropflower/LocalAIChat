import { describe, it, expect, vi, beforeEach } from 'vitest';
import { getModelCategory, formatSize } from '../../utils/modelUtils';

describe('getModelCategory', () => {
  it('should return qwen category for qwen model', () => {
    expect(getModelCategory('qwen2.5:7b')).toEqual({ label: '通义千问', color: 'blue' });
  });

  it('should return llama category for llama model', () => {
    expect(getModelCategory('llama3:8b')).toEqual({ label: 'Llama', color: 'green' });
  });

  it('should return deepseek category for deepseek model', () => {
    expect(getModelCategory('deepseek-r1:7b')).toEqual({ label: 'DeepSeek', color: 'purple' });
  });

  it('should return mistral category for mistral model', () => {
    expect(getModelCategory('mistral:7b')).toEqual({ label: 'Mistral', color: 'orange' });
  });

  it('should return gemma category for gemma model', () => {
    expect(getModelCategory('gemma2:9b')).toEqual({ label: 'Gemma', color: 'red' });
  });

  it('should return codellama category for codellama model', () => {
    expect(getModelCategory('codellama:7b')).toEqual({ label: 'Code', color: 'cyan' });
  });

  it('should be case-insensitive', () => {
    expect(getModelCategory('QWEN:latest')).toEqual({ label: '通义千问', color: 'blue' });
  });

  it('should return default category for unknown model', () => {
    expect(getModelCategory('custom-model')).toEqual({ label: '其他', color: 'default' });
  });
});

describe('formatSize', () => {
  it('should format GB correctly', () => {
    expect(formatSize(4431088896)).toBe('4.1 GB');
  });

  it('should format MB correctly', () => {
    expect(formatSize(524288000)).toBe('500 MB');
  });

  it('should format bytes correctly', () => {
    expect(formatSize(500)).toBe('500 B');
  });

  it('should format exactly 1 GB', () => {
    expect(formatSize(1073741824)).toBe('1.0 GB');
  });

  it('should format small MB values', () => {
    expect(formatSize(1048576)).toBe('1 MB');
  });
});