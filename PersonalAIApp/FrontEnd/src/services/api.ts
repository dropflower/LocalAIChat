import axios, { AxiosInstance } from 'axios';
import type { ApiResponse, ModelInfo, Session, ChatMessage, ChatRequest } from '../types';

/**
 * API 服务层
 *
 * ## 功能描述
 * 封装所有后端 API 调用，提供统一的请求/响应处理。
 * 使用 axios 处理普通 REST 请求，使用原生 fetch 处理 SSE 流式请求。
 *
 * ## 为什么流式对话使用 fetch 而非 axios？
 * axios 基于 XMLHttpRequest，不支持 ReadableStream 的流式读取。
 * 原生 fetch API 返回的 Response.body 是 ReadableStream，可以逐块读取 SSE 数据。
 * 这就是为什么 chatCompletionsStream() 方法单独使用 fetch 实现。
 *
 * ## 拦截器机制
 * 1. 请求拦截器：自动从 localStorage 读取 API Key 并附加到 X-API-Key 请求头
 *    - 好处：前端代码无需手动传递 API Key，统一管理
 * 2. 响应拦截器：检测 401 状态码，自动清除认证状态并刷新页面
 *    - 好处：API Key 过期或无效时自动跳转登录页
 *
 * ## API_BASE
 * 使用相对路径 '/api'，配合 Vite 代理（vite.config.ts）转发到 http://localhost:8080
 * 开发环境无需处理跨域问题，生产环境由 Nginx 反向代理处理
 *
 * ## 单例模式
 * 导出 apiService 单例，全局共享同一个 AxiosInstance 和拦截器配置
 */
const API_BASE = '/api';

class ApiService {
  private client: AxiosInstance;

  constructor() {
    this.client = axios.create({
      baseURL: API_BASE,
      timeout: 30000, // 30 秒超时（普通请求），流式请求不受此限制
    });

    // ==================== 请求拦截器 ====================
    // 自动从 localStorage 读取 API Key 并附加到请求头
    this.client.interceptors.request.use((config) => {
      const apiKey = localStorage.getItem('apiKey');
      if (apiKey) {
        config.headers['X-API-Key'] = apiKey;
      }
      return config;
    });

    // ==================== 响应拦截器 ====================
    // 401 时自动清除认证状态并刷新页面
    this.client.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401) {
          localStorage.removeItem('apiKey');
          window.location.reload();
        }
        return Promise.reject(error);
      }
    );
  }

  // ==================== 认证接口 ====================

  /**
   * 登录 — 验证 API Key
   * 调用 POST /api/auth/login
   *
   * @param apiKey 用户输入的 API Key
   * @returns true 认证成功，false 认证失败
   */
  async login(apiKey: string): Promise<boolean> {
    const res = await this.client.post<ApiResponse<{ status: string }>>('/auth/login', { apiKey });
    return res.data.code === 200;
  }

  // ==================== 模型接口 ====================

  /**
   * 获取可用模型列表
   * 调用 GET /api/models
   */
  async getModels(): Promise<ModelInfo[]> {
    const res = await this.client.get<ApiResponse<ModelInfo[]>>('/models');
    return res.data.data;
  }

  /**
   * 检查 Ollama 服务状态和模型列表
   * 调用 GET /api/models/status
   */
  async checkOllamaStatus(): Promise<{ ollamaAvailable: boolean; models: ModelInfo[] }> {
    const res = await this.client.get<ApiResponse<{ ollamaAvailable: boolean; models: ModelInfo[] }>>('/models/status');
    return res.data.data;
  }

  // ==================== 会话接口 ====================

  /**
   * 分页查询会话列表
   * 调用 GET /api/sessions
   */
  async getSessions(page = 0, size = 50): Promise<Session[]> {
    const res = await this.client.get<ApiResponse<Session[]>>('/sessions', { params: { page, size } });
    return res.data.data;
  }

  /**
   * 按标题模糊搜索会话
   * 调用 GET /api/sessions/search
   */
  async searchSessions(keyword: string): Promise<Session[]> {
    const res = await this.client.get<ApiResponse<Session[]>>('/sessions/search', { params: { keyword } });
    return res.data.data;
  }

  /**
   * 重命名会话标题
   * 调用 PUT /api/sessions/{id}/title
   */
  async updateSessionTitle(id: number, title: string): Promise<Session> {
    const res = await this.client.put<ApiResponse<Session>>(`/sessions/${id}/title`, { title });
    return res.data.data;
  }

  /**
   * 切换会话置顶状态
   * 调用 PUT /api/sessions/{id}/pin
   */
  async togglePinSession(id: number): Promise<Session> {
    const res = await this.client.put<ApiResponse<Session>>(`/sessions/${id}/pin`);
    return res.data.data;
  }

  /**
   * 删除会话及所有消息
   * 调用 DELETE /api/sessions/{id}
   */
  async deleteSession(id: number): Promise<void> {
    await this.client.delete(`/sessions/${id}`);
  }

  // ==================== 消息接口 ====================

  /**
   * 获取会话消息历史
   * 调用 GET /api/chat/sessions/{sessionId}/messages
   */
  async getMessages(sessionId: number, page = 0, size = 100): Promise<ChatMessage[]> {
    const res = await this.client.get<ApiResponse<ChatMessage[]>>(`/chat/sessions/${sessionId}/messages`, {
      params: { page, size },
    });
    return res.data.data;
  }

  // ==================== 流式对话接口 ====================

  /**
   * 流式对话（SSE）— 使用原生 fetch 实现
   *
   * 为什么使用 fetch 而非 axios？
   * - axios 基于 XMLHttpRequest，不支持 ReadableStream
   * - fetch 返回的 Response.body 是 ReadableStream，可以逐块读取 SSE 流
   * - 调用方通过 response.body.getReader() 逐行解析 JSON 数据
   *
   * 调用 POST /api/chat/completions
   *
   * @param request 对话请求参数
   * @returns fetch Response 对象，调用方自行处理 ReadableStream
   */
  async chatCompletionsStream(request: ChatRequest, signal?: AbortSignal): Promise<Response> {
    const apiKey = localStorage.getItem('apiKey');
    return fetch(`${API_BASE}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': apiKey || '',
      },
      body: JSON.stringify(request),
      signal,
    });
  }
}

/** API 服务单例，全局共享 */
export const apiService = new ApiService();