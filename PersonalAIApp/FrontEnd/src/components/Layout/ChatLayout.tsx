import { useEffect } from 'react';
import { Layout } from 'antd';
import { useAppStore } from '../../stores/appStore';
import SessionList from '../SessionList/SessionList';
import ChatArea from '../ChatArea';

const { Sider, Content } = Layout;

/**
 * ChatLayout — 主界面布局组件
 *
 * ## 功能描述
 * 应用的主界面布局，采用 Ant Design Layout 的 Sider + Content 经典布局：
 * - 左侧边栏（280px）：会话列表（SessionList 组件）
 * - 右侧主区域（flex=1）：对话区域（ChatArea 组件）
 *
 * ## 布局结构
 * ┌──────────┬──────────────────────────┐
 * │  Sider   │       Content            │
 * │  280px   │       flex: 1            │
 * │          │                          │
 * │ Session  │       ChatArea           │
 * │  List    │   (模型选择器 + 消息列表   │
 * │          │    + 输入框)              │
 * └──────────┴──────────────────────────┘
 *
 * ## 初始化逻辑
 * 组件挂载时自动加载模型列表和会话列表：
 * - loadModels()：获取 Ollama 模型列表并设置默认选中模型
 * - loadSessions()：加载历史会话列表
 * 使用 useEffect 在组件挂载时执行一次，空依赖数组确保只执行一次
 *
 * ## 依赖关系
 * - 依赖 useAppStore 的 loadModels 和 loadSessions 方法
 * - 嵌套 SessionList 和 ChatArea 两个子组件
 */
export default function ChatLayout() {
  const loadModels = useAppStore((s) => s.loadModels);
  const loadSessions = useAppStore((s) => s.loadSessions);

  // 组件挂载时初始化数据
  useEffect(() => {
    loadModels();
    loadSessions();
  }, [loadModels, loadSessions]);

  return (
    <Layout style={{ height: '100vh' }}>
      {/* 左侧边栏：会话列表 */}
      <Sider
        width={280}
        style={{
          background: '#f5f5f5',
          borderRight: '1px solid #e8e8e8',
          overflow: 'hidden',
        }}
      >
        <SessionList />
      </Sider>
      {/* 右侧主区域：对话界面 */}
      <Content style={{ display: 'flex', flexDirection: 'column', background: '#fff' }}>
        <ChatArea />
      </Content>
    </Layout>
  );
}