import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { message as antdMessage } from 'antd';
import LoginPage from '../../components/LoginPage';

const { mockStore } = vi.hoisted(() => {
  const mockStore = {
    login: vi.fn(),
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

// Mock antd message
vi.mock('antd', async (importOriginal) => {
  const actual: any = await importOriginal();
  return {
    ...actual,
    message: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
    },
  };
});

const loginBtn = () => document.querySelector('.ant-btn-primary')!;

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const getLoginBtn = () => screen.getByRole('button', { name: /登/ });

  describe('页面渲染', () => {
    it('should render the login page title', () => {
      render(<LoginPage />);
      expect(screen.getByText('AI 对话助手')).toBeInTheDocument();
    });

    it('should render the subtitle', () => {
      render(<LoginPage />);
      expect(screen.getByText(/基于本地 Ollama 模型的智能对话系统/)).toBeInTheDocument();
    });

    it('should render API Key input', () => {
      render(<LoginPage />);
      expect(screen.getByPlaceholderText('请输入 API Key')).toBeInTheDocument();
    });

    it('should render login button', () => {
      render(<LoginPage />);
      expect(getLoginBtn()).toBeInTheDocument();
    });

    it('should render default key hint', () => {
      render(<LoginPage />);
      expect(screen.getByText(/默认 Key: ai-chat-default-key/)).toBeInTheDocument();
    });
  });

  describe('登录交互', () => {
    it('should show warning when API Key is empty', async () => {
      render(<LoginPage />);
      fireEvent.click(getLoginBtn());
      await waitFor(() => {
        expect(vi.mocked(antdMessage).warning).toHaveBeenCalledWith('请输入 API Key');
      });
      expect(mockStore.login).not.toHaveBeenCalled();
    });

    it('should call login with trimmed API Key', async () => {
      mockStore.login.mockResolvedValue(true);
      render(<LoginPage />);
      const input = screen.getByPlaceholderText('请输入 API Key');
      await userEvent.type(input, '  test-key-123  ');
      fireEvent.click(getLoginBtn());
      await waitFor(() => {
        expect(mockStore.login).toHaveBeenCalledWith('test-key-123');
      });
    });

    it('should show error when login fails', async () => {
      mockStore.login.mockResolvedValue(false);
      render(<LoginPage />);
      const input = screen.getByPlaceholderText('请输入 API Key');
      await userEvent.type(input, 'wrong-key');
      fireEvent.click(getLoginBtn());
      await waitFor(() => {
        expect(vi.mocked(antdMessage).error).toHaveBeenCalledWith('API Key 无效，请重试');
      });
    });

    it('should show error on network failure', async () => {
      mockStore.login.mockRejectedValue(new Error('Network error'));
      render(<LoginPage />);
      const input = screen.getByPlaceholderText('请输入 API Key');
      await userEvent.type(input, 'test-key');
      fireEvent.click(getLoginBtn());
      await waitFor(() => {
        expect(vi.mocked(antdMessage).error).toHaveBeenCalledWith('无法连接到后端服务');
      });
    });

    it('should submit on Enter key', async () => {
      mockStore.login.mockResolvedValue(true);
      render(<LoginPage />);
      const input = screen.getByPlaceholderText('请输入 API Key');
      await userEvent.type(input, 'test-key');
      fireEvent.keyDown(input, { key: 'Enter' });
      await waitFor(() => {
        expect(mockStore.login).toHaveBeenCalledWith('test-key');
      });
    });
  });

  describe('加载状态', () => {
    it('should show loading state on button during login', async () => {
      mockStore.login.mockImplementation(() => new Promise(() => {}));
      render(<LoginPage />);
      const input = screen.getByPlaceholderText('请输入 API Key');
      await userEvent.type(input, 'test-key');
      fireEvent.click(getLoginBtn());
      await waitFor(() => {
        const btn = getLoginBtn();
        expect(btn.classList.contains('ant-btn-loading')).toBe(true);
      });
    });
  });
});