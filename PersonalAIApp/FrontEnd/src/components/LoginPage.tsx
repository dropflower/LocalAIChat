import { useState } from 'react';
import { Card, Input, Button, Typography, Space, message } from 'antd';
import { RobotOutlined, KeyOutlined } from '@ant-design/icons';
import { useAppStore } from '../stores/appStore';

const { Title, Text } = Typography;

/**
 * LoginPage — 登录页组件
 *
 * ## 功能描述
 * 应用的登录界面，用户输入 API Key 进行身份验证。
 * 认证成功后通过 Zustand Store 更新全局认证状态，App 组件自动切换到主界面。
 *
 * ## 界面布局
 * - 居中卡片式布局，紫色渐变背景
 * - 顶部：RobotOutlined 图标 + "AI 对话助手" 标题
 * - 中部：API Key 输入框（密码类型，支持回车提交）
 * - 底部："登录" 按钮 + 默认 Key 提示
 *
 * ## 交互流程
 * 1. 用户输入 API Key
 * 2. 点击"登录"按钮或按 Enter 键
 * 3. 调用 useAppStore.login(apiKey) 验证
 * 4. 成功：App 组件自动切换到 ChatLayout
 * 5. 失败：显示错误提示（API Key 无效 / 无法连接后端）
 *
 * ## 状态管理
 * - apiKey：本地 state，存储用户输入的 API Key
 * - loading：控制按钮的加载状态，防止重复提交
 * - login 函数：来自 Zustand Store，验证成功后更新全局状态
 *
 * ## 注意
 * 默认 API Key 提示仅用于开发环境，生产环境应移除
 */
export default function LoginPage() {
  const [apiKey, setApiKey] = useState('');
  const [loading, setLoading] = useState(false);
  const login = useAppStore((s) => s.login);

  /**
   * 处理登录提交
   * 验证 API Key 非空，调用后端验证，处理成功/失败/异常三种情况
   */
  const handleLogin = async () => {
    if (!apiKey.trim()) {
      message.warning('请输入 API Key');
      return;
    }
    setLoading(true);
    try {
      const ok = await login(apiKey.trim());
      if (!ok) {
        message.error('API Key 无效，请重试');
      }
    } catch {
      message.error('无法连接到后端服务');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    }}>
      <Card style={{ width: 420, borderRadius: 12, boxShadow: '0 8px 32px rgba(0,0,0,0.2)' }}>
        <Space direction="vertical" size="large" style={{ width: '100%', textAlign: 'center' }}>
          <RobotOutlined style={{ fontSize: 48, color: '#1677ff' }} />
          <Title level={3} style={{ margin: 0 }}>AI 对话助手</Title>
          <Text type="secondary">基于本地 Ollama 模型的智能对话系统</Text>

          <div style={{ textAlign: 'left' }}>
            <Text strong>API Key</Text>
            <Input.Password
              prefix={<KeyOutlined />}
              placeholder="请输入 API Key"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              onPressEnter={handleLogin}
              style={{ marginTop: 8 }}
              size="large"
            />
          </div>

          <Button
            type="primary"
            size="large"
            block
            loading={loading}
            onClick={handleLogin}
          >
            登录
          </Button>

          <Text type="secondary" style={{ fontSize: 12 }}>
            默认 Key: ai-chat-default-key
          </Text>
        </Space>
      </Card>
    </div>
  );
}