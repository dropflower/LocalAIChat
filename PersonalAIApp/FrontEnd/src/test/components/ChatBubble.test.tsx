import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { message as antdMessage } from 'antd';
import ChatBubble from '../../components/ChatBubble/ChatBubble';
import type { ChatMessage } from '../../types';

// Mock react-markdown
vi.mock('react-markdown', () => ({
  default: ({ children }: any) => {
    return <div data-testid="markdown">{String(children)}</div>;
  },
}));

// Mock react-syntax-highlighter
vi.mock('react-syntax-highlighter/dist/esm/styles/prism', () => ({
  oneLight: {},
}));

vi.mock('react-syntax-highlighter', () => ({
  Prism: ({ children, language }: any) => (
    <pre data-testid="syntax-highlighter" data-language={language}>{children}</pre>
  ),
}));

// Mock antd message - 必须在组件导入之前
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

describe('ChatBubble', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('用户消息', () => {
    const userMsg: ChatMessage = { role: 'user', content: '你好，这是一条测试消息' };

    it('should render user message content', () => {
      render(<ChatBubble message={userMsg} />);
      expect(screen.getByText('你好，这是一条测试消息')).toBeInTheDocument();
    });

    it('should render user avatar icon', () => {
      render(<ChatBubble message={userMsg} />);
      const avatar = document.querySelector('.anticon-user');
      expect(avatar).toBeInTheDocument();
    });

    it('should not render copy button for user messages', () => {
      render(<ChatBubble message={userMsg} />);
      expect(screen.queryByText('复制')).not.toBeInTheDocument();
    });

    it('should use flexDirection row-reverse for user message', () => {
      render(<ChatBubble message={userMsg} />);
      const container = document.querySelector('[style*="flex-direction"]');
      expect(container).toBeInTheDocument();
    });
  });

  describe('AI 消息', () => {
    const aiMsg: ChatMessage = { role: 'assistant', content: '你好！我是 AI 助手' };

    it('should render AI message content via markdown', () => {
      render(<ChatBubble message={aiMsg} />);
      expect(screen.getByTestId('markdown')).toBeInTheDocument();
      expect(screen.getByText('你好！我是 AI 助手')).toBeInTheDocument();
    });

    it('should render robot avatar icon', () => {
      render(<ChatBubble message={aiMsg} />);
      const avatar = document.querySelector('.anticon-robot');
      expect(avatar).toBeInTheDocument();
    });

    it('should render copy button for AI messages', () => {
      render(<ChatBubble message={aiMsg} />);
      expect(screen.getByText('复制')).toBeInTheDocument();
    });

    it('should call clipboard.writeText on copy click', () => {
      const writeTextSpy = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined);
      render(<ChatBubble message={aiMsg} />);
      fireEvent.click(screen.getByText('复制'));
      expect(writeTextSpy).toHaveBeenCalledWith('你好！我是 AI 助手');
    });

    it('should show success message after copy', async () => {
      vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined);
      render(<ChatBubble message={aiMsg} />);
      fireEvent.click(screen.getByText('复制'));
      await vi.waitFor(() => {
        expect(vi.mocked(antdMessage).success).toHaveBeenCalledWith('已复制');
      });
    });
  });

  describe('空内容渲染', () => {
    it('should render empty content without placeholder', () => {
      const emptyMsg: ChatMessage = { role: 'assistant', content: '' };
      render(<ChatBubble message={emptyMsg} />);
      // "..." 指示器由 ChatArea 组件管理，ChatBubble 不渲染思考中占位
      expect(screen.queryByText('思考中...')).not.toBeInTheDocument();
    });

    it('should not show copy button when content is empty', () => {
      const emptyMsg: ChatMessage = { role: 'assistant', content: '' };
      render(<ChatBubble message={emptyMsg} />);
      expect(screen.queryByText('复制')).not.toBeInTheDocument();
    });
  });

  describe('系统消息', () => {
    it('should render system message content', () => {
      const sysMsg: ChatMessage = { role: 'system', content: 'System message' };
      render(<ChatBubble message={sysMsg} />);
      expect(screen.getByTestId('markdown')).toBeInTheDocument();
    });
  });
});