import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SessionList from '../../components/SessionList/SessionList';
import type { Session } from '../../types';

const { mockStore } = vi.hoisted(() => {
  const mockStore = {
    sessions: [] as Session[],
    currentSessionId: null as number | null,
    createSession: vi.fn(),
    selectSession: vi.fn(),
    updateSessionTitle: vi.fn(),
    togglePinSession: vi.fn(),
    deleteSession: vi.fn(),
    loadSessions: vi.fn(),
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
  const actual = (await importOriginal()) as any;
  return {
    ...actual,
    message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    Modal: {
      ...actual.Modal,
      confirm: (props: any) => {
        // 直接调用 onOk 模拟确认操作，避免 Modal 渲染问题
        if (props.onOk) props.onOk();
        return { destroy: vi.fn(), update: vi.fn() };
      },
    },
  };
});

const createMockSession = (overrides: Partial<Session> = {}): Session => ({
  id: 1,
  title: '测试会话',
  modelName: 'qwen2.5:7b',
  messageCount: 5,
  isPinned: false,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-02T00:00:00',
  ...overrides,
});

describe('SessionList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockStore.sessions = [];
    mockStore.currentSessionId = null;
  });

  describe('基本渲染', () => {
    it('should render "新对话" button', () => {
      render(<SessionList />);
      expect(screen.getByText('新对话')).toBeInTheDocument();
    });

    it('should render search input', () => {
      render(<SessionList />);
      expect(screen.getByPlaceholderText('搜索会话...')).toBeInTheDocument();
    });

    it('should show empty state when no sessions', () => {
      render(<SessionList />);
      expect(screen.getByText('暂无会话')).toBeInTheDocument();
    });
  });

  describe('会话列表', () => {
    beforeEach(() => {
      mockStore.sessions = [
        createMockSession({ id: 1, title: '第一个会话', messageCount: 3 }),
        createMockSession({ id: 2, title: '第二个会话', messageCount: 10, modelName: 'llama3.2' }),
      ];
    });

    it('should render session titles', () => {
      render(<SessionList />);
      expect(screen.getByText('第一个会话')).toBeInTheDocument();
      expect(screen.getByText('第二个会话')).toBeInTheDocument();
    });

    it('should render session info (model + message count)', () => {
      render(<SessionList />);
      expect(screen.getByText(/qwen2.5:7b · 3 条消息/)).toBeInTheDocument();
      expect(screen.getByText(/llama3.2 · 10 条消息/)).toBeInTheDocument();
    });

    it('should highlight current session', () => {
      mockStore.currentSessionId = 1;
      render(<SessionList />);
      const items = screen.getAllByRole('listitem');
      const firstItem = items[0];
      expect(firstItem.style.background).toBe('rgb(230, 244, 255)');
    });

    it('should call selectSession on click', () => {
      render(<SessionList />);
      fireEvent.click(screen.getByText('第一个会话'));
      expect(mockStore.selectSession).toHaveBeenCalledWith(1);
    });
  });

  describe('排序规则', () => {
    it('should show pinned sessions first', () => {
      mockStore.sessions = [
        createMockSession({ id: 1, title: '普通会话', isPinned: false, updatedAt: '2026-01-03T00:00:00' }),
        createMockSession({ id: 2, title: '置顶会话', isPinned: true, updatedAt: '2026-01-01T00:00:00' }),
      ];
      render(<SessionList />);
      const items = screen.getAllByRole('listitem');
      expect(items[0].textContent).toContain('置顶会话');
    });
  });

  describe('新对话按钮', () => {
    it('should call createSession on click', () => {
      render(<SessionList />);
      fireEvent.click(screen.getByText('新对话'));
      expect(mockStore.createSession).toHaveBeenCalled();
    });
  });

  describe('搜索功能', () => {
    it('should update search keyword on input', async () => {
      render(<SessionList />);
      const searchInput = screen.getByPlaceholderText('搜索会话...');
      await userEvent.type(searchInput, '测试');
      expect(searchInput).toHaveValue('测试');
    });
  });

  describe('重命名功能', () => {
    it('should enter edit mode and save title', async () => {
      mockStore.sessions = [createMockSession({ id: 1, title: '旧标题' })];
      render(<SessionList />);

      const moreBtn = screen.getByLabelText('more');
      fireEvent.click(moreBtn);

      await waitFor(() => {
        const renameItem = screen.getByText('重命名');
        fireEvent.click(renameItem);
      });

      await waitFor(() => {
        const editInput = document.querySelector('input[value="旧标题"]');
        expect(editInput).toBeInTheDocument();
      });

      const editInput = document.querySelector('input[value="旧标题"]') as HTMLInputElement;
      if (editInput) {
        fireEvent.change(editInput, { target: { value: '新标题' } });
        fireEvent.keyDown(editInput, { key: 'Enter' });
      }

      await waitFor(() => {
        expect(mockStore.updateSessionTitle).toHaveBeenCalledWith(1, '新标题');
      });
    });
  });

  describe('置顶功能', () => {
    it('should toggle pin on session', async () => {
      mockStore.sessions = [createMockSession({ id: 1, title: '可置顶会话' })];
      render(<SessionList />);

      const moreBtn = screen.getByLabelText('more');
      fireEvent.click(moreBtn);

      await waitFor(() => {
        const pinItem = screen.getByText('置顶');
        fireEvent.click(pinItem);
      });

      expect(mockStore.togglePinSession).toHaveBeenCalledWith(1);
    });
  });

  describe('删除功能', () => {
    it('should call deleteSession when clicking delete menu item', async () => {
      mockStore.sessions = [createMockSession({ id: 1, title: '待删除会话' })];
      render(<SessionList />);

      const moreBtn = screen.getByLabelText('more');
      fireEvent.click(moreBtn);

      await waitFor(() => {
        const deleteItem = screen.getByText('删除');
        fireEvent.click(deleteItem);
      });

      // Modal.confirm 被 mock 为直接调用 onOk，所以 deleteSession 应该立即被调用
      await waitFor(() => {
        expect(mockStore.deleteSession).toHaveBeenCalledWith(1);
      });
    });
  });
});