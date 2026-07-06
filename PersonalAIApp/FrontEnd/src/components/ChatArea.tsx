import { useEffect, useRef, useState } from 'react';
import { Space, Typography, Empty, Avatar, Tooltip } from 'antd';
import { RobotOutlined, DownOutlined, RightOutlined, SearchOutlined } from '@ant-design/icons';
import { useAppStore } from '../stores/appStore';
import ModelSelector from './ModelSelector/ModelSelector';
import ChatBubble from './ChatBubble/ChatBubble';
import ChatInput from './ChatInput/ChatInput';
import { apiService } from '../services/api';
import type { ChatMessage } from '../types';

const { Title, Text } = Typography;

/**
 * ChatArea — 对话主区域组件
 *
 * ## 核心流程 — handleSend()
 * 1. 添加用户消息
 * 2. 添加空 AI 消息占位
 * 3. 发起 SSE 流式请求
 * 4. 逐行解析 SSE 事件：
 *    - thinking → appendThinkingToLastMessage（流式追加到消息的 thinkingContent）
 *    - token → appendToLastMessage（流式追加到消息的 content）
 *    - search → setSearchResultsToLastMessage（设置搜索结果元数据）
 * 5. 流结束 → setIsLoading(false)
 *
 * ## 深度思考持久化
 * thinkingContent 存储在每条 ChatMessage 的独立字段中，发送新消息时不清除。
 * 当前生成中的消息自动展开思考面板，历史消息默认折叠可手动展开。
 *
 * ## 联网搜索反馈
 * assistant 消息右下角显示"已搜索了X个网页"，悬停可查看来源链接列表。
 */
export default function ChatArea() {
  const {
    currentSessionId,
    messages,
    selectedModel,
    isLoading,
    deepThink,
    searchEnabled,
    loadSessions,
    addMessage,
    appendToLastMessage,
    appendThinkingToLastMessage,
    setSearchResultsToLastMessage,
    setIsLoading,
  } = useAppStore();

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const hasAssistantRef = useRef(false);

  // 每条消息独立的思考面板折叠状态（key=消息index，value=是否展开）
  const [expandedMessages, setExpandedMessages] = useState<Set<number>>(new Set());

  // 当前正在生成的消息自动展开
  useEffect(() => {
    if (isLoading && messages.length > 0) {
      const lastIdx = messages.length - 1;
      setExpandedMessages((prev) => {
        if (prev.has(lastIdx)) return prev;
        const next = new Set(prev);
        next.add(lastIdx);
        return next;
      });
    }
  }, [isLoading, messages.length]);

  // 消息更新时自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const toggleThinking = (index: number) => {
    setExpandedMessages((prev) => {
      const next = new Set(prev);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  };

  const handleSend = async (content: string) => {
    if (!content.trim() || isLoading) return;

    // 1. 添加用户消息
    addMessage({ role: 'user', content });

    setIsLoading(true);
    hasAssistantRef.current = false;

    try {
      const abortController = new AbortController();
      abortControllerRef.current = abortController;

      const response = await apiService.chatCompletionsStream({
        sessionId: currentSessionId ?? undefined,
        modelName: selectedModel,
        message: content,
        deepThink,
        enableSearch: searchEnabled,
      }, abortController.signal);

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body?.getReader();
      if (!reader) throw new Error('无法读取响应流');

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed) continue;

          let jsonStr = trimmed;
          if (trimmed.startsWith('data:')) {
            jsonStr = trimmed.replace(/^data:\s*/, '');
          }

          try {
            const data = JSON.parse(jsonStr);
            switch (data.type) {
              case 'start':
                break;
              case 'thinking':
                if (data.content) {
                  // 确保有 assistant 消息占位
                  if (!hasAssistantRef.current) {
                    addMessage({ role: 'assistant', content: '', thinkingContent: data.content });
                    hasAssistantRef.current = true;
                  } else {
                    appendThinkingToLastMessage(data.content);
                  }
                }
                break;
              case 'search':
                if (data.count !== undefined) {
                  if (!hasAssistantRef.current) {
                    addMessage({
                      role: 'assistant', content: '',
                      searchCount: data.count,
                      searchSources: data.sources || [],
                    });
                    hasAssistantRef.current = true;
                  } else {
                    setSearchResultsToLastMessage(data.count, data.sources || []);
                  }
                }
                break;
              case 'token':
                if (data.content) {
                  if (!hasAssistantRef.current) {
                    addMessage({ role: 'assistant', content: data.content });
                    hasAssistantRef.current = true;
                  } else {
                    appendToLastMessage(data.content);
                  }
                }
                break;
              case 'done':
                break;
              case 'error':
                if (data.content) {
                  if (!hasAssistantRef.current) {
                    addMessage({ role: 'assistant', content: data.content });
                    hasAssistantRef.current = true;
                  } else {
                    appendToLastMessage(data.content);
                  }
                }
                break;
            }
          } catch {
            // 忽略非 JSON 行
          }
        }
      }
    } catch (error: any) {
      if (error?.name !== 'AbortError') {
        console.error('对话失败:', error);
        const errorMsg = '\n\n[请求失败，请检查 Ollama 服务是否运行]';
        if (!hasAssistantRef.current) {
          addMessage({ role: 'assistant', content: errorMsg });
          hasAssistantRef.current = true;
        } else {
          appendToLastMessage(errorMsg);
        }
      }
    } finally {
      setIsLoading(false);
      abortControllerRef.current = null;
      loadSessions();
    }
  };

  const handleStop = () => {
    abortControllerRef.current?.abort();
    setIsLoading(false);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* 顶部栏 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '12px 20px',
          borderBottom: '1px solid #f0f0f0',
        }}
      >
        <ModelSelector />
        <Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            AI 对话助手
          </Text>
        </Space>
      </div>

      {/* 消息区域 */}
      <div
        style={{
          flex: 1,
          overflow: 'auto',
          padding: '20px',
          background: '#fafafa',
        }}
      >
        {messages.length === 0 ? (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
            <Empty
              image={<RobotOutlined style={{ fontSize: 64, color: '#d9d9d9' }} />}
              description={
                <div>
                  <Title level={4} type="secondary">开始对话</Title>
                  <Text type="secondary">在下方输入消息，与 AI 助手开始对话</Text>
                </div>
              }
            />
          </div>
        ) : (
          <div style={{ maxWidth: 800, margin: '0 auto' }}>
            {messages.map((msg, index) => {
              const isCurrentGenerating = isLoading && index === messages.length - 1;
              const isThinkingExpanded = expandedMessages.has(index);
              const hasThinking = msg.role === 'assistant' && msg.thinkingContent && msg.thinkingContent.length > 0;

              return (
                <div key={index}>
                  {/* 思考过程面板：每条 assistant 消息独立显示 */}
                  {hasThinking && (
                    <div style={{ display: 'flex', gap: 12, marginBottom: 8 }}>
                      <Avatar
                        icon={<RobotOutlined />}
                        style={{ background: '#52c41a', flexShrink: 0 }}
                      />
                      <div
                        style={{
                          maxWidth: '75%',
                          border: '1px solid #e8e8e8',
                          borderRadius: 8,
                          overflow: 'hidden',
                        }}
                      >
                        {/* 标题栏：可点击折叠/展开 */}
                        <div
                          onClick={() => toggleThinking(index)}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 6,
                            padding: '6px 12px',
                            background: '#fafafa',
                            cursor: 'pointer',
                            userSelect: 'none',
                            fontSize: 12,
                            fontWeight: 500,
                            color: '#8c8c8c',
                          }}
                        >
                          {isThinkingExpanded ? <DownOutlined /> : <RightOutlined />}
                          <span>Thinking</span>
                          {isCurrentGenerating && (
                            <span style={{ fontSize: 11, color: '#bfbfbf', marginLeft: 4 }}>
                              ...
                            </span>
                          )}
                        </div>
                        {/* 思考内容 */}
                        {isThinkingExpanded && (
                          <div
                            style={{
                              padding: '8px 12px',
                              background: '#fefefe',
                              fontSize: 12,
                              color: '#8c8c8c',
                              whiteSpace: 'pre-wrap',
                              wordBreak: 'break-word',
                              lineHeight: 1.6,
                              maxHeight: 200,
                              overflow: 'auto',
                              borderTop: '1px solid #f0f0f0',
                            }}
                          >
                            {msg.thinkingContent}
                          </div>
                        )}
                      </div>
                    </div>
                  )}

                  {/* 消息气泡 */}
                  <div style={{ position: 'relative' }}>
                    <ChatBubble message={msg} />

                    {/* 联网搜索结果统计：assistant 消息右下角 */}
                    {msg.role === 'assistant' && msg.searchCount && msg.searchCount > 0 && (
                      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 4 }}>
                        <Tooltip
                          title={
                            <div>
                              <div style={{ marginBottom: 4, fontWeight: 500 }}>搜索来源：</div>
                              {(msg.searchSources || []).map((src, i) => (
                                <div key={i} style={{ fontSize: 12, marginBottom: 2 }}>
                                  <a
                                    href={src.url}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    style={{ color: '#91d5ff', textDecoration: 'none' }}
                                  >
                                    {src.title || src.url}
                                  </a>
                                </div>
                              ))}
                            </div>
                          }
                          placement="topRight"
                        >
                          <span
                            style={{
                              fontSize: 11,
                              color: '#8c8c8c',
                              cursor: 'pointer',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: 4,
                              background: '#f5f5f5',
                              padding: '2px 8px',
                              borderRadius: 4,
                            }}
                          >
                            <SearchOutlined style={{ fontSize: 10 }} />
                            已搜索了{msg.searchCount}个网页
                          </span>
                        </Tooltip>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}

            {/* "..." 思考指示器 */}
            {isLoading && !hasAssistantRef.current && (
              <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
                <Avatar
                  icon={<RobotOutlined />}
                  style={{ background: '#52c41a', flexShrink: 0 }}
                />
                <span
                  className="typing-dots"
                  style={{ fontSize: 18, color: '#999', letterSpacing: 2, fontWeight: 'bold', lineHeight: '32px' }}
                >
                  ...
                </span>
              </div>
            )}

            {/* 滚动锚点 */}
            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      {/* 输入区域 */}
      <ChatInput onSend={handleSend} onStop={handleStop} isLoading={isLoading} />
    </div>
  );
}
