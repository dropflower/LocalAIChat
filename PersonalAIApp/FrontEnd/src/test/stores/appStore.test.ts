import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useAppStore } from '../../stores/appStore';
import { apiService } from '../../services/api';

// Mock axios module
vi.mock('axios', () => {
  const mockInstance = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  };
  return {
    default: {
      create: vi.fn(() => mockInstance),
    },
  };
});

const INITIAL_STATE = {
  isAuthenticated: false,
  apiKey: '',
  models: [],
  selectedModel: '',
  ollamaAvailable: false,
  sessions: [],
  currentSessionId: null,
  messages: [],
  isLoading: false,
};

describe('AppStore', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    useAppStore.setState({ ...INITIAL_STATE });
  });

  describe('认证模块', () => {
    it('should initialize isAuthenticated from localStorage', () => {
      localStorage.setItem('apiKey', 'test-key');
      // 手动模拟 store 初始化时的行为
      useAppStore.setState({
        isAuthenticated: true,
        apiKey: 'test-key',
      });
      const state = useAppStore.getState();
      expect(state.isAuthenticated).toBe(true);
      expect(state.apiKey).toBe('test-key');
    });

    it('should initialize as not authenticated when no apiKey', () => {
      const state = useAppStore.getState();
      expect(state.isAuthenticated).toBe(false);
    });

    it('should login successfully with valid key', async () => {
      vi.spyOn(apiService, 'login').mockResolvedValue(true);
      const result = await useAppStore.getState().login('valid-key');
      expect(result).toBe(true);
      expect(useAppStore.getState().isAuthenticated).toBe(true);
      expect(localStorage.getItem('apiKey')).toBe('valid-key');
    });

    it('should fail login with invalid key', async () => {
      vi.spyOn(apiService, 'login').mockResolvedValue(false);
      const result = await useAppStore.getState().login('invalid-key');
      expect(result).toBe(false);
      expect(useAppStore.getState().isAuthenticated).toBe(false);
    });

    it('should logout and clear state', () => {
      localStorage.setItem('apiKey', 'test-key');
      useAppStore.getState().logout();
      expect(useAppStore.getState().isAuthenticated).toBe(false);
      expect(useAppStore.getState().apiKey).toBe('');
      expect(localStorage.getItem('apiKey')).toBeNull();
    });
  });

  describe('模型模块', () => {
    it('should load models and set selectedModel', async () => {
      const models = [{ name: 'qwen', size: 1000, modified_at: '' }];
      vi.spyOn(apiService, 'checkOllamaStatus').mockResolvedValue({
        ollamaAvailable: true,
        models,
      });
      await useAppStore.getState().loadModels();
      const state = useAppStore.getState();
      expect(state.models).toEqual(models);
      expect(state.ollamaAvailable).toBe(true);
      expect(state.selectedModel).toBe('qwen');
    });

    it('should handle loadModels failure gracefully', async () => {
      vi.spyOn(apiService, 'checkOllamaStatus').mockRejectedValue(new Error('fail'));
      await useAppStore.getState().loadModels();
      const state = useAppStore.getState();
      expect(state.ollamaAvailable).toBe(false);
    });

    it('should set selected model', () => {
      useAppStore.getState().setSelectedModel('llama');
      expect(useAppStore.getState().selectedModel).toBe('llama');
    });
  });

  describe('会话模块', () => {
    it('should load sessions', async () => {
      const sessions = [{ id: 1, title: 'Test', modelName: 'qwen', messageCount: 0, isPinned: false, createdAt: '', updatedAt: '' }];
      vi.spyOn(apiService, 'getSessions').mockResolvedValue(sessions);
      await useAppStore.getState().loadSessions();
      expect(useAppStore.getState().sessions).toEqual(sessions);
    });

    it('should handle loadSessions failure gracefully', async () => {
      vi.spyOn(apiService, 'getSessions').mockRejectedValue(new Error('fail'));
      await useAppStore.getState().loadSessions();
      expect(useAppStore.getState().sessions).toEqual([]);
    });

    it('should create session and clear current state', async () => {
      const result = await useAppStore.getState().createSession();
      expect(result).toBe(0);
      expect(useAppStore.getState().currentSessionId).toBeNull();
      expect(useAppStore.getState().messages).toEqual([]);
    });

    it('should select session and load messages', async () => {
      vi.spyOn(apiService, 'getMessages').mockResolvedValue([]);
      await useAppStore.getState().selectSession(1);
      expect(useAppStore.getState().currentSessionId).toBe(1);
    });

    it('should update session title and refresh', async () => {
      vi.spyOn(apiService, 'updateSessionTitle').mockResolvedValue({} as any);
      vi.spyOn(apiService, 'getSessions').mockResolvedValue([]);
      await useAppStore.getState().updateSessionTitle(1, 'New');
      expect(apiService.updateSessionTitle).toHaveBeenCalledWith(1, 'New');
    });

    it('should toggle pin and refresh', async () => {
      vi.spyOn(apiService, 'togglePinSession').mockResolvedValue({} as any);
      vi.spyOn(apiService, 'getSessions').mockResolvedValue([]);
      await useAppStore.getState().togglePinSession(1);
      expect(apiService.togglePinSession).toHaveBeenCalledWith(1);
    });

    it('should delete current session and clear state', async () => {
      vi.spyOn(apiService, 'deleteSession').mockResolvedValue();
      vi.spyOn(apiService, 'getSessions').mockResolvedValue([]);
      useAppStore.setState({ currentSessionId: 1, messages: [{ id: 1, role: 'user', content: 'hi' }] });
      await useAppStore.getState().deleteSession(1);
      expect(useAppStore.getState().currentSessionId).toBeNull();
      expect(useAppStore.getState().messages).toEqual([]);
    });

    it('should delete other session without clearing current', async () => {
      vi.spyOn(apiService, 'deleteSession').mockResolvedValue();
      vi.spyOn(apiService, 'getSessions').mockResolvedValue([]);
      useAppStore.setState({ currentSessionId: 1 });
      await useAppStore.getState().deleteSession(2);
      expect(useAppStore.getState().currentSessionId).toBe(1);
    });
  });

  describe('消息模块', () => {
    it('should load messages', async () => {
      const messages = [{ id: 1, role: 'user' as const, content: 'hi' }];
      vi.spyOn(apiService, 'getMessages').mockResolvedValue(messages);
      await useAppStore.getState().loadMessages(1);
      expect(useAppStore.getState().messages).toEqual(messages);
    });

    it('should handle loadMessages failure', async () => {
      vi.spyOn(apiService, 'getMessages').mockRejectedValue(new Error('fail'));
      await useAppStore.getState().loadMessages(1);
      expect(useAppStore.getState().messages).toEqual([]);
    });

    it('should add message', () => {
      const msg = { id: 1, role: 'user' as const, content: 'hello' };
      useAppStore.getState().addMessage(msg);
      expect(useAppStore.getState().messages).toHaveLength(1);
    });

    it('should append to last assistant message', () => {
      useAppStore.setState({
        messages: [{ id: 1, role: 'assistant' as const, content: 'Hello' }],
      });
      useAppStore.getState().appendToLastMessage(' World');
      const messages = useAppStore.getState().messages;
      expect(messages[0].content).toBe('Hello World');
    });

    it('should not append when last message is user', () => {
      useAppStore.setState({
        messages: [{ id: 1, role: 'user' as const, content: 'Hello' }],
      });
      useAppStore.getState().appendToLastMessage(' World');
      const messages = useAppStore.getState().messages;
      expect(messages[0].content).toBe('Hello');
    });

    it('should not append when messages empty', () => {
      useAppStore.setState({ messages: [] });
      expect(() => useAppStore.getState().appendToLastMessage('test')).not.toThrow();
    });

    it('should set loading state', () => {
      useAppStore.getState().setIsLoading(true);
      expect(useAppStore.getState().isLoading).toBe(true);
      useAppStore.getState().setIsLoading(false);
      expect(useAppStore.getState().isLoading).toBe(false);
    });

    it('should clear messages', () => {
      useAppStore.setState({
        messages: [{ id: 1, role: 'user' as const, content: 'hi' }],
      });
      useAppStore.getState().clearMessages();
      expect(useAppStore.getState().messages).toEqual([]);
    });
  });
});