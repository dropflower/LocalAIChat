/**
 * TypeScript 类型定义文件
 *
 * ## 功能描述
 * 定义前端应用中所有核心数据结构的 TypeScript 接口和类型。
 * 集中管理类型定义，确保前后端数据结构一致，提供编译时类型安全检查。
 *
 * ## 类型分类
 * 1. 数据实体类型：Session、ChatMessage、ModelInfo — 对应后端实体
 * 2. API 通信类型：ApiResponse、ChatRequest — 对应后端 DTO
 * 3. SSE 流式类型：SSEEventType、SSEData — 实时通信数据结构
 */

// ==================== 数据实体类型 ====================

/**
 * 会话实体
 * 对应后端 Session 实体和 sc_session 表
 */
export interface Session {
  id: number;           // 会话 ID
  title: string;        // 会话标题
  modelName: string;    // 使用的模型名称
  messageCount: number; // 消息总数
  isPinned: boolean;    // 是否置顶
  createdAt: string;    // 创建时间
  updatedAt: string;    // 最后更新时间
}

/**
 * 搜索来源链接
 */
export interface SearchSource {
  title: string;
  url: string;
}

/**
 * 消息实体
 * 对应后端 Message 实体和 sc_message 表
 * role 字段限定为三个值：user（用户）、assistant（AI）、system（系统）
 */
export interface ChatMessage {
  id?: number;                        // 消息 ID（可选，前端创建时无 ID）
  role: 'user' | 'assistant' | 'system'; // 消息角色
  content: string;                    // 消息内容（已解压的明文）
  tokenCount?: number;                // Token 估算数量
  createdAt?: string;                 // 创建时间
  thinkingContent?: string;           // 深度思考过程内容（每条 assistant 消息独立存储）
  searchCount?: number;               // 联网搜索结果数量
  searchSources?: SearchSource[];     // 联网搜索来源链接列表
}

/**
 * 模型信息
 * 对应 Ollama 返回的模型数据 + 数据库配置合并
 */
export interface ModelInfo {
  name: string;           // 模型名称（如 qwen2.5:7b）
  displayName?: string;   // 前端展示名称
  size?: number;          // 模型文件大小（字节）
  modified_at?: string;   // 最后修改时间
  temperature?: number;   // 生成温度（来自数据库配置）
  maxTokens?: number;     // 最大 Token 数（来自数据库配置）
}

// ==================== API 通信类型 ====================

/**
 * API 统一响应体
 * 对应后端 ApiResponse<T> 泛型类
 */
export interface ApiResponse<T> {
  code: number;      // 业务状态码
  message: string;   // 提示信息
  data: T;           // 响应数据（泛型）
  timestamp: number; // 响应时间戳（毫秒）
}

/**
 * 对话请求体
 * 对应后端 ChatRequest DTO
 */
export interface ChatRequest {
  sessionId?: number;   // 会话 ID（null 时创建新会话）
  modelName: string;    // 模型名称
  message: string;      // 用户消息内容
  deepThink?: boolean;  // 是否启用深度思考模式（默认 false）
  enableSearch?: boolean; // 是否启用联网搜索（默认 false）
}

// ==================== SSE 流式类型 ====================

/**
 * SSE 事件类型
 * - start：流式响应已启动（用于前端显示"..."加载指示器）
 * - thinking：深度思考过程（模型推理步骤）
 * - token：模型生成的文本片段
 * - done：生成完成
 * - error：生成出错
 */
export type SSEEventType = 'start' | 'thinking' | 'token' | 'search' | 'done' | 'error';

/**
 * SSE 数据格式
 * 后端 ChatController 返回的 SSE 事件数据结构
 */
export interface SSEData {
  type: SSEEventType;  // 事件类型
  content?: string;    // 文本内容（type=token/thinking/error 时）
  count?: number;      // 搜索结果数量（type=search 时）
  sources?: SearchSource[]; // 搜索来源链接（type=search 时）
  message?: string;    // 错误消息（type=error 时）
  usage?: {            // Token 用量统计（type=done 时）
    prompt_tokens: number;
    completion_tokens: number;
  };
}