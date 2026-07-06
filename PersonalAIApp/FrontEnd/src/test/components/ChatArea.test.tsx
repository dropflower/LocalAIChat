import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ChatArea from '../../components/ChatArea';
import type { ChatMessage } from '../../types';

const { mockStore } = vi.hoisted(() => {
  const mockStore = {  
    currentSessionId: null as number | null,
    messages: [] as ChatMessage[],
    selectedModel: 'qwen2.5:7b',
    isLoading: false,
    deepThink: false,
    searchEnabled: false,
    thinkingContent: '',
    loadSessions: vi.fn(),
    addMessage: vi.fn(),
    appendToLastMessage: vi.fn(),
    appendThinkingContent: vi.fn(),
    clearThinkingContent: vi.fn(),
    setIsLoading: vi.fn(),
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

// Mock apiService
vi.mock('../../services/api', () => ({
  apiService: {
    chatCompletionsStream: vi.fn(),
  },
}));

// Mock ModelSelector
vi.mock('../../components/ModelSelector/ModelSelector', () => ({
  default: () => <div data-testid="model-selector">ModelSelector</div>,
}));

// Mock ChatBubble
vi.mock('../../components/ChatBubble/ChatBubble', () => ({
  default: ({ message }: any) => <div data-testid="chat-bubble">{message.content}</div>,
}));

// Mock ChatInput
vi.mock('../../components/ChatInput/ChatInput', () => ({
  default: ({ onSend, onStop, isLoading }: any) => (
    <div data-testid="chat-input">
      <button data-testid="mock-send" onClick={() => onSend('test message')}>Send</button>
      <button data-testid="mock-stop" onClick={onStop}>Stop</button>
      <span data-testid="mock-loading">{String(isLoading)}</span>
    </div>
  ),
}));

describe('ChatArea', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockStore.messages = [];
    mockStore.currentSessionId = null;
    mockStore.isLoading = false;
  });

  describe('空状态', () => {
    it('should show empty state when no messages', () => {
      render(<ChatArea />);
      expect(screen.getByText('开始对话')).toBeInTheDocument();
      expect(screen.getByText(/在下方输入消息/)).toBeInTheDocument();
    });

    it('should render model selector', () => {
      render(<ChatArea />);
      expect(screen.getByTestId('model-selector')).toBeInTheDocument();
    });

    it('should render chat input', () => {
      render(<ChatArea />);
      expect(screen.getByTestId('chat-input')).toBeInTheDocument();
    });
  });

  describe('消息列表', () => {
    it('should render messages when they exist', () => {
      mockStore.messages = [
        { role: 'user', content: 'Hello' },
        { role: 'assistant', content: 'Hi there' },
      ];
      render(<ChatArea />);
      const bubbles = screen.getAllByTestId('chat-bubble');
      expect(bubbles).toHaveLength(2);
      expect(bubbles[0].textContent).toBe('Hello');
      expect(bubbles[1].textContent).toBe('Hi there');
    });
  });

  describe('发送消息', () => {
    it('should call addMessage for user message on send', () => {
      render(<ChatArea />);
      fireEvent.click(screen.getByTestId('mock-send'));
      expect(mockStore.addMessage).toHaveBeenCalledWith({ role: 'user', content: 'test message' });
    });

    it('should set isLoading to true on send', () => {
      render(<ChatArea />);
      fireEvent.click(screen.getByTestId('mock-send'));
      expect(mockStore.setIsLoading).toHaveBeenCalledWith(true);
    });
  });

  describe('停止生成', () => {
    it('should call setIsLoading(false) on stop', () => {
      render(<ChatArea />);
      fireEvent.click(screen.getByTestId('mock-stop'));
      expect(mockStore.setIsLoading).toHaveBeenCalledWith(false);
    });
  });

  describe('AI 助手文字', () => {
    it('should render header text', () => {
      render(<ChatArea />);
      expect(screen.getByText('AI 对话助手')).toBeInTheDocument();
    });
  });
});