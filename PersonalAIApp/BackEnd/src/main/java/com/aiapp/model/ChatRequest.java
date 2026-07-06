package com.aiapp.model;

import lombok.Data;
import java.util.List;

/**
 * 对话请求 DTO（Data Transfer Object）
 *
 * ## 功能描述
 * 前端发起对话时提交的请求体数据结构。
 * 由 ChatController 接收并传递给 ChatService 处理。
 *
 * ## 字段说明
 * - sessionId：会话 ID（可选）
 *   - null 时：后端自动创建新会话
 *   - 非 null 时：在已有会话中继续对话
 * - modelName：使用的 AI 模型名称（如 "qwen2.5:7b"），必填
 * - message：用户当前发送的消息内容，必填
 * - deepThink：是否启用深度思考模式（默认 false）
 *   - 启用后，后端会解析 Ollama 返回的 reasoning_content 字段
 *   - 前端会实时展示思考过程（可折叠面板）
 *   - 仅对支持 thinking 能力的模型生效（如 deepseek-r1、qwen3.5）
 * - enableSearch：是否启用联网搜索（默认 false）
 *   - 启用后，后端先搜索网页获取参考资料
 *   - 将搜索结果作为上下文注入到 AI 对话中
 *   - 搜索来源：DuckDuckGo HTML API
 * - history：历史对话记录（预留字段，当前未使用）
 *   - 当前上下文由后端从数据库自动构建，无需前端传递
 *
 * ## 内部类 ChatMessage
 * 历史消息的简化结构，仅包含 role 和 content 两个字段
 */
@Data
public class ChatRequest {

    /** 会话 ID，null 表示创建新会话 */
    private Long sessionId;

    /** 模型名称，必填 */
    private String modelName;

    /** 用户消息内容，必填 */
    private String message;

    /** 是否启用深度思考模式 */
    private boolean deepThink;

    /** 是否启用联网搜索 */
    private boolean enableSearch;

    /** 历史对话记录（预留） */
    private List<ChatMessage> history;

    /**
     * 历史消息结构（简化版）
     */
    @Data
    public static class ChatMessage {
        /** 角色：user/assistant/system */
        private String role;

        /** 消息内容 */
        private String content;
    }
}