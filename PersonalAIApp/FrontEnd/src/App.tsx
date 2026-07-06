import { ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useAppStore } from './stores/appStore';
import LoginPage from './components/LoginPage';
import ChatLayout from './components/Layout/ChatLayout';

/**
 * App 根组件
 *
 * ## 功能描述
 * 应用的顶层组件，负责：
 * 1. 配置 Ant Design 的全局主题和国际化
 * 2. 根据认证状态切换登录页和主界面
 *
 * ## 路由策略
 * 不使用 React Router，采用条件渲染实现页面切换：
 * - 未认证（isAuthenticated=false）→ 显示 LoginPage
 * - 已认证（isAuthenticated=true） → 显示 ChatLayout（含侧边栏和对话区）
 *
 * 这种设计适合单页业务应用，避免了不必要的路由复杂度。
 *
 * ## Ant Design 配置
 * - locale：使用 zh_CN 中文语言包
 * - theme.token.colorPrimary：主题色 #1677ff（Ant Design 默认蓝）
 * - theme.token.borderRadius：圆角 8px
 *
 * ## 依赖关系
 * - 依赖 useAppStore 获取 isAuthenticated 状态
 * - 依赖 LoginPage 和 ChatLayout 两个主要页面组件
 */
function App() {
  const isAuthenticated = useAppStore((s) => s.isAuthenticated);

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 8,
        },
      }}
    >
      {isAuthenticated ? <ChatLayout /> : <LoginPage />}
    </ConfigProvider>
  );
}

export default App;