import { useState, useRef, useEffect } from 'react';
import { Input, Button, Space, Tooltip } from 'antd';
import { SendOutlined, StopOutlined, BulbOutlined, SearchOutlined } from '@ant-design/icons';
import { useAppStore } from '../../stores/appStore';

const { TextArea } = Input;

/**
 * ChatInput 组件 Props
 */
interface Props {
  onSend: (content: string) => void;
  onStop: () => void;
  isLoading: boolean;
}

/**
 * ChatInput — 消息输入框组件
 *
 * ## 功能描述
 * 对话界面的输入区域，提供文本输入、发送、停止、深度思考切换、联网搜索切换功能。
 *
 * ## 新增功能
 * - 深度思考按钮：切换深度思考模式，启用后解析模型的 reasoning_content
 * - 联网搜索按钮：切换联网搜索，启用后先搜索网页再结合结果回答
 *
 * ## 交互说明
 * - 正常状态：显示"发送"按钮（蓝色）
 * - 生成中状态：显示"停止"按钮（红色），输入框禁用
 * - 深度思考/搜索按钮：独立的 toggle 按钮，激活时高亮显示
 */
export default function ChatInput({ onSend, onStop, isLoading }: Props) {
  const [value, setValue] = useState('');
  const inputRef = useRef<any>(null);

  const { deepThink, searchEnabled, toggleDeepThink, toggleSearchEnabled } = useAppStore();

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const handleSend = () => {
    if (value.trim() && !isLoading) {
      onSend(value.trim());
      setValue('');
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div
      style={{
        padding: '16px 20px',
        borderTop: '1px solid #f0f0f0',
        background: '#fff',
      }}
    >
      <div style={{ maxWidth: 800, margin: '0 auto' }}>
        {/* 功能切换按钮行 */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
          <Tooltip title={deepThink ? '深度思考已开启' : '开启深度思考（显示模型推理过程）'}>
            <Button
              size="small"
              type={deepThink ? 'primary' : 'default'}
              icon={<BulbOutlined />}
              onClick={toggleDeepThink}
              disabled={isLoading}
              style={{
                borderRadius: 16,
                background: deepThink ? '#722ed1' : undefined,
                borderColor: deepThink ? '#722ed1' : undefined,
              }}
            >
              深度思考
            </Button>
          </Tooltip>

          <Tooltip title={searchEnabled ? '联网搜索已开启' : '开启联网搜索（先搜索网页再回答）'}>
            <Button
              size="small"
              type={searchEnabled ? 'primary' : 'default'}
              icon={<SearchOutlined />}
              onClick={toggleSearchEnabled}
              disabled={isLoading}
              style={{
                borderRadius: 16,
                background: searchEnabled ? '#1677ff' : undefined,
              }}
            >
              联网搜索
            </Button>
          </Tooltip>
        </div>

        <Space.Compact style={{ width: '100%' }}>
          <TextArea
            ref={inputRef}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            autoSize={{ minRows: 1, maxRows: 6 }}
            disabled={isLoading}
            style={{ borderRadius: 8 }}
          />
          {isLoading ? (
            <Button
              danger
              icon={<StopOutlined />}
              onClick={onStop}
              style={{ height: 'auto', borderRadius: 8 }}
            >
              停止
            </Button>
          ) : (
            <Button
              type="primary"
              icon={<SendOutlined />}
              onClick={handleSend}
              disabled={!value.trim()}
              style={{ height: 'auto', borderRadius: 8 }}
            >
              发送
            </Button>
          )}
        </Space.Compact>
        <div style={{ textAlign: 'center', marginTop: 8 }}>
          <span style={{ fontSize: 11, color: '#999' }}>
            AI 回复仅供参考，请核实重要信息
          </span>
        </div>
      </div>
    </div>
  );
}