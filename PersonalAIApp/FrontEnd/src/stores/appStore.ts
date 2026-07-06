import { create } from 'zustand';
import type { Session, ChatMessage, ModelInfo, SearchSource } from '../types';
import { apiService } from '../services/api';

/**
 * 全局状态管理（Zustand Store）
 *
 * ## 功能描述
 * 使用 Zustand 管理前端应用的全局状态，包含四大模块：
 * 1. 认证状态：isAuthenticated、apiKey、login()、logout()
 * 2. 模型状态：models、selectedModel、ollamaAvailable、loadModels()、setSelectedModel()
 * 3. 会话状态：sessions、currentSessionId、loadSessions()、selectSession()、CRUD 操作
 * 4. 消息状态：messages、isLoading、loadMessages()、addMessage()、appendToLastMessage()
 *
 * ## 深度思考持久化策略
 * thinkingContent 存储在每条 ChatMessage 独立的 thinkingContent 字段中，
 * 而非全局单例。这样当用户发送新消息时，原有思考内容不会丢失。
 * - appendThinkingToLastMessage()：流式追加到当前最后一条 assistant 消息
 * - 每条消息的思考面板独立折叠/展开，当前生成中的消息自动展开
 *
 * ## 联网搜索反馈
 * 搜索结果元数据（count 和 sources）存储在 ChatMessage 的 searchCount/searchSources 字段中。
 * 后端在搜索完成后发送 search 类型 SSE 事件，前端将其附加到当前 assistant 消息。
 */

interface AppState {
  // ==================== 认证模块 ====================
  isAuthenticated: boolean;
  apiKey: string;
  login: (apiKey: string) => Promise<boolean>;
  logout: () => void;

  // ==================== 模型模块 ====================
  models: ModelInfo[];
  selectedModel: string;
  ollamaAvailable: boolean;
  loadModels: () => Promise<void>;
  setSelectedModel: (model: string) => void;

  // ==================== 会话模块 ====================
  sessions: Session[];
  currentSessionId: number | null;
  loadSessions: () => Promise<void>;
  createSession: () => Promise<number>;
  selectSession: (id: number) => void;
  updateSessionTitle: (id: number, title: string) => Promise<void>;
  togglePinSession: (id: number) => Promise<void>;
  deleteSession: (id: number) => Promise<void>;

  // ==================== 消息模块 ====================
  messages: ChatMessage[];
  isLoading: boolean;
  deepThink: boolean;
  searchEnabled: boolean;
  toggleDeepThink: () => void;
  toggleSearchEnabled: () => void;
  loadMessages: (sessionId: number) => Promise<void>;
  addMessage: (msg: ChatMessage) => void;
  appendToLastMessage: (content: string) => void;
  appendThinkingToLastMessage: (content: string) => void;
  setSearchResultsToLastMessage: (count: number, sources: SearchSource[]) => void;
  setIsLoading: (loading: boolean) => void;
  clearMessages: () => void;
}

export const useAppStore = create<AppState>((set, get) => ({
  // ==================== 认证模块 ====================
  isAuthenticated: !!localStorage.getItem('apiKey'),
  apiKey: localStorage.getItem('apiKey') || '',

  login: async (apiKey: string) => {
    const ok = await apiService.login(apiKey);
    if (ok) {
      localStorage.setItem('apiKey', apiKey);
      set({ isAuthenticated: true, apiKey });
    }
    return ok;
  },

  logout: () => {
    localStorage.removeItem('apiKey');
    set({ isAuthenticated: false, apiKey: '' });
  },

  // ==================== 模型模块 ====================
  models: [],
  selectedModel: '',
  ollamaAvailable: false,

  loadModels: async () => {
    try {
      const status = await apiService.checkOllamaStatus();
      set({
        models: status.models,
        ollamaAvailable: status.ollamaAvailable,
      });
      if (!get().selectedModel && status.models.length > 0) {
        set({ selectedModel: status.models[0].name });
      }
    } catch {
      // Ollama 未启动时静默失败
    }
  },

  setSelectedModel: (model: string) => set({ selectedModel: model }),

  // ==================== 会话模块 ====================
  sessions: [],
  currentSessionId: null,

  loadSessions: async () => {
    try {
      const sessions = await apiService.getSessions();
      set({ sessions });
    } catch {
      // 静默失败
    }
  },

  createSession: async () => {
    set({ currentSessionId: null, messages: [] });
    return 0;
  },

  selectSession: async (id: number) => {
    set({ currentSessionId: id });
    await get().loadMessages(id);
  },

  updateSessionTitle: async (id: number, title: string) => {
    await apiService.updateSessionTitle(id, title);
    await get().loadSessions();
  },

  togglePinSession: async (id: number) => {
    await apiService.togglePinSession(id);
    await get().loadSessions();
  },

  deleteSession: async (id: number) => {
    await apiService.deleteSession(id);
    if (get().currentSessionId === id) {
      set({ currentSessionId: null, messages: [] });
    }
    await get().loadSessions();
  },

  // ==================== 消息模块 ====================
  messages: [],
  isLoading: false,
  deepThink: false,
  searchEnabled: false,

  toggleDeepThink: () => set((state) => ({ deepThink: !state.deepThink })),
  toggleSearchEnabled: () => set((state) => ({ searchEnabled: !state.searchEnabled })),

  loadMessages: async (sessionId: number) => {
    try {
      const messages = await apiService.getMessages(sessionId);
      set({ messages });
    } catch {
      set({ messages: [] });
    }
  },

  addMessage: (msg: ChatMessage) => {
    set((state) => ({ messages: [...state.messages, msg] }));
  },

  appendToLastMessage: (content: string) => {
    set((state) => {
      const messages = [...state.messages];
      if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
        messages[messages.length - 1] = {
          ...messages[messages.length - 1],
          content: messages[messages.length - 1].content + content,
        };
      }
      return { messages };
    });
  },

  /**
   * 流式追加思考内容到最后一条 assistant 消息
   * 思考内容存储在消息对象的 thinkingContent 字段中，实现持久化
   */
  appendThinkingToLastMessage: (content: string) => {
    set((state) => {
      const messages = [...state.messages];
      if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
        const last = messages[messages.length - 1];
        messages[messages.length - 1] = {
          ...last,
          thinkingContent: (last.thinkingContent || '') + content,
        };
      }
      return { messages };
    });
  },

  /**
   * 设置搜索结果元数据到最后一条 assistant 消息
   */
  setSearchResultsToLastMessage: (count: number, sources: SearchSource[]) => {
    set((state) => {
      const messages = [...state.messages];
      if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
        const last = messages[messages.length - 1];
        messages[messages.length - 1] = {
          ...last,
          searchCount: count,
          searchSources: sources,
        };
      }
      return { messages };
    });
  },

  setIsLoading: (loading: boolean) => set({ isLoading: loading }),
  clearMessages: () => set({ messages: [] }),
}));
