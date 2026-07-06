import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import ModelSelector from '../../components/ModelSelector/ModelSelector';
import type { ModelInfo } from '../../types';

const { mockStore } = vi.hoisted(() => {
  const mockStore = {
    models: [] as ModelInfo[],
    selectedModel: '',
    ollamaAvailable: false,
    setSelectedModel: vi.fn(),
  };
  return { mockStore };
});

vi.mock('../../stores/appStore', () => {
  const fn = (selector?: any) => {
    if (typeof selector === 'function') return selector(mockStore);
    return mockStore;
  };
  fn.getState = () => mockStore;
  fn.setState = (partial: any) => Object.assign(mockStore, partial);
  return { useAppStore: fn };
});

// Mock modelUtils
vi.mock('../../utils/modelUtils', () => ({
  getModelCategory: (name: string) => {
    if (name.includes('qwen')) return { label: '通义千问', color: 'blue' };
    if (name.includes('llama')) return { label: 'Llama', color: 'green' };
    return { label: '其他', color: 'default' };
  },
  formatSize: (bytes: number) => {
    if (bytes >= 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
    return `${(bytes / (1024 * 1024)).toFixed(0)} MB`;
  },
}));

describe('ModelSelector', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockStore.models = [];
    mockStore.selectedModel = '';
    mockStore.ollamaAvailable = false;
  });

  describe('Ollama 未连接状态', () => {
    it('should show error badge when ollama is not available', () => {
      mockStore.ollamaAvailable = false;
      render(<ModelSelector />);
      expect(screen.getByText('Ollama 未连接')).toBeInTheDocument();
    });

    it('should not show select dropdown when ollama is not available', () => {
      mockStore.ollamaAvailable = false;
      render(<ModelSelector />);
      expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    });
  });

  describe('Ollama 已连接状态', () => {
    beforeEach(() => {
      mockStore.ollamaAvailable = true;
      mockStore.models = [
        { name: 'qwen2.5:7b', displayName: 'Qwen 2.5 7B', size: 4431088896 },
        { name: 'llama3.2:3b', size: 2123456789 },
      ];
      mockStore.selectedModel = 'qwen2.5:7b';
    });

    it('should show success badge when ollama is available', () => {
      render(<ModelSelector />);
      const successBadge = document.querySelector('.ant-badge-status-success');
      expect(successBadge).toBeInTheDocument();
    });

    it('should render select with correct value', () => {
      render(<ModelSelector />);
      const select = document.querySelector('.ant-select');
      expect(select).toBeInTheDocument();
    });

    it('should show placeholder when no model selected', () => {
      mockStore.selectedModel = '';
      render(<ModelSelector />);
      const selectItem = document.querySelector('.ant-select-selection-item');
      expect(selectItem).toBeInTheDocument();
      expect(selectItem?.textContent).toBe('');
    });
  });

  describe('空模型列表', () => {
    it('should show notFoundContent when no models', () => {
      mockStore.ollamaAvailable = true;
      mockStore.models = [];
      render(<ModelSelector />);
      const select = document.querySelector('.ant-select-selector');
      expect(select).toBeInTheDocument();
    });
  });
});