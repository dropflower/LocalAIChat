import { Avatar, Button, Typography, message as antdMessage } from 'antd';
import { UserOutlined, RobotOutlined, CopyOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';
import type { ChatMessage } from '../../types';

const { Text } = Typography;

/**
 * ChatBubble 组件 Props
 *
 * @property {ChatMessage} message - 单条消息数据，包含 role（角色）、content（内容）等字段
 */
interface Props {
  message: ChatMessage;
}

/**
 * ChatBubble — 对话消息气泡组件
 *
 * ## 功能描述
 * 渲染单条对话消息的气泡 UI，根据消息角色（user / assistant）展示不同的样式：
 * - 用户消息：蓝色气泡，右对齐
 * - AI 消息：灰色气泡，左对齐，支持 Markdown 渲染和代码语法高亮
 *
 * ## 视觉层次
 * 1. 用户消息气泡 → 蓝色背景
 * 2. "..." 指示器（ChatArea 渲染）→ 气泡外，灰色动画
 * 3. Thinking 面板（ChatArea 渲染）→ 独立于气泡上方，灰色可折叠
 * 4. 主回复内容 → Markdown 渲染，正常字号
 */
export default function ChatBubble({ message }: Props) {
  // 判断当前消息是否为用户发送，用于决定气泡样式和布局方向
  const isUser = message.role === 'user';

  /**
   * 复制整条消息内容到剪贴板
   * 使用 Web Clipboard API 写入文本，成功后通过 antdMessage 弹出提示
   */
  const handleCopy = () => {
    navigator.clipboard.writeText(message.content).then(() => {
      antdMessage.success('已复制');
    });
  };

  return (
    <div
      style={{
        display: 'flex',
        gap: 12,
        marginBottom: 20,
        // 用户消息靠右（row-reverse），AI 消息靠左（默认 row）
        flexDirection: isUser ? 'row-reverse' : 'row',
      }}
    >
      {/* 头像：用户蓝色 / AI 绿色 */}
      <Avatar
        icon={isUser ? <UserOutlined /> : <RobotOutlined />}
        style={{
          background: isUser ? '#1677ff' : '#52c41a',
          flexShrink: 0,
        }}
      />

      {/* 气泡主体 */}
      <div
        style={{
          maxWidth: '75%',
          background: isUser ? '#e6f4ff' : '#f5f5f5',
          borderRadius: 12,
          padding: '12px 16px',
          position: 'relative',
        }}
      >
        {/* === 用户消息：纯文本展示 === */}
        {isUser ? (
          <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
            {message.content}
          </div>
        ) : (
          /* === AI 消息：Markdown 渲染 === */
          <div className="markdown-body">
            <ReactMarkdown
              // 自定义组件渲染器，核心：拦截 code 元素实现语法高亮
              components={{
                /**
                 * 自定义代码块渲染
                 * - 行内代码（无 language-xxx 类名）：使用灰色背景的 inline code 样式
                 * - 代码块（有 language-xxx 类名）：使用 Prism 语法高亮 + 顶部语言标签 + 复制按钮
                 */
                code({ className, children, ...props }) {
                  // 正则匹配 className 中的语言标识，如 "language-python" → "python"
                  const match = /language-(\w+)/.exec(className || '');
                  // 去除末尾换行符，避免代码块底部多余空行
                  const codeStr = String(children).replace(/\n$/, '');

                  if (match) {
                    // === 代码块（有语言标识） ===
                    return (
                      <div style={{ position: 'relative', margin: '8px 0' }}>
                        {/* 代码块顶部栏：语言标签 + 复制按钮 */}
                        <div
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            background: '#f0f0f0',
                            padding: '4px 12px',
                            borderTopLeftRadius: 6,
                            borderTopRightRadius: 6,
                            fontSize: 12,
                          }}
                        >
                          <Text type="secondary">{match[1]}</Text>
                          <Button
                            type="text"
                            size="small"
                            icon={<CopyOutlined />}
                            onClick={() => {
                              navigator.clipboard.writeText(codeStr);
                              antdMessage.success('代码已复制');
                            }}
                          />
                        </div>
                        {/* 语法高亮代码区域 */}
                        <SyntaxHighlighter
                          style={oneLight}
                          language={match[1]}
                          PreTag="div"
                          customStyle={{
                            margin: 0,
                            borderTopLeftRadius: 0,
                            borderTopRightRadius: 0,
                            fontSize: 13,
                          }}
                        >
                          {codeStr}
                        </SyntaxHighlighter>
                      </div>
                    );
                  }

                  // === 行内代码（无语言标识） ===
                  return (
                    <code
                      className={className}
                      {...props}
                      style={{
                        background: '#f0f0f0',
                        padding: '2px 6px',
                        borderRadius: 4,
                      }}
                    >
                      {children}
                    </code>
                  );
                },
              }}
            >
              {message.content}
            </ReactMarkdown>
          </div>
        )}

        {/* === AI 消息操作栏 === */}
        {/* 仅在 AI 消息且内容非空时显示操作按钮 */}
        {!isUser && message.content && (
          <div style={{ marginTop: 8, display: 'flex', gap: 4 }}>
            <Button type="text" size="small" icon={<CopyOutlined />} onClick={handleCopy}>
              复制
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}