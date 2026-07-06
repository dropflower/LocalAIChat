import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ChatInput from '../../components/ChatInput/ChatInput';

describe('ChatInput', () => {
  const mockOnSend = vi.fn();
  const mockOnStop = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('正常状态（非加载中）', () => {
    it('should render textarea and send button', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={false} />);
      expect(screen.getByPlaceholderText(/输入消息/)).toBeInTheDocument();
      expect(screen.getByText('发送')).toBeInTheDocument();
    });

    it('should disable send button when input is empty', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={false} />);
      const sendBtn = screen.getByText('发送').closest('button');
      expect(sendBtn).toBeDisabled();
    });

    it('should enable send button when input has content', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={false} />);
      const textarea = screen.getByPlaceholderText(/输入消息/);
      fireEvent.change(textarea, { target: { value: 'Hello' } });
      const sendBtn = screen.getByText('发送').closest('button');
      expect(sendBtn).not.toBeDisabled();
    });

    it('should call onSend with trimmed content on send click', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={false} />);
      const textarea = screen.getByPlaceholderText(/输入消息/);
      fireEvent.change(textarea, { target: { value: '  Hello World  ' } });
      fireEvent.click(screen.getByText('发送'));
      expect(mockOnSend).toHaveBeenCalledWith('Hello World');
    });

    it('should clear input after send', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={false} />);
      const textarea = screen.getByPlaceholderText(/输入消息/) as HTMLTextAreaElement;
      fireEvent.change(textarea, { target: { value: 'Hello' } });
      fireEvent.click(screen.getByText('发送'));
      expect(textarea.value).toBe('');
    });

    it('should send on Enter key', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={false} />);
      const textarea = screen.getByPlaceholderText(/输入消息/);
      fireEvent.change(textarea, { target: { value: 'Hello' } });
      fireEvent.keyDown(textarea, { key: 'Enter', shiftKey: false });
      expect(mockOnSend).toHaveBeenCalledWith('Hello');
    });

    it('should not send on Shift+Enter', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={false} />);
      const textarea = screen.getByPlaceholderText(/输入消息/);
      fireEvent.change(textarea, { target: { value: 'Hello' } });
      fireEvent.keyDown(textarea, { key: 'Enter', shiftKey: true });
      expect(mockOnSend).not.toHaveBeenCalled();
    });

    it('should not send empty message', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={false} />);
      const textarea = screen.getByPlaceholderText(/输入消息/);
      fireEvent.change(textarea, { target: { value: '   ' } });
      fireEvent.click(screen.getByText('发送'));
      expect(mockOnSend).not.toHaveBeenCalled();
    });

    it('should not show stop button when not loading', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={false} />);
      expect(screen.queryByText('停止')).not.toBeInTheDocument();
    });
  });

  describe('加载状态', () => {
    it('should show stop button when loading', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={true} />);
      expect(screen.getByText('停止')).toBeInTheDocument();
    });

    it('should disable textarea when loading', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={true} />);
      const textarea = screen.getByPlaceholderText(/输入消息/);
      expect(textarea).toBeDisabled();
    });

    it('should not show send button when loading', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={true} />);
      expect(screen.queryByText('发送')).not.toBeInTheDocument();
    });

    it('should call onStop when stop button clicked', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={true} />);
      fireEvent.click(screen.getByText('停止'));
      expect(mockOnStop).toHaveBeenCalledTimes(1);
    });
  });

  describe('提示文字', () => {
    it('should render disclaimer text', () => {
      render(<ChatInput onSend={mockOnSend} onStop={mockOnStop} isLoading={false} />);
      expect(screen.getByText(/AI 回复仅供参考/)).toBeInTheDocument();
    });
  });
});