/**
 * 应用入口文件
 *
 * ## 功能描述
 * React 应用的挂载点，将 App 组件渲染到 index.html 中的 #root 元素。
 * 使用 React.StrictMode 包裹，在开发环境下启用额外的检查和警告。
 *
 * ## React.StrictMode 说明
 * - 仅在开发模式下生效，生产构建时无影响
 * - 会触发组件两次渲染，用于检测副作用问题
 * - 检查过时的 API 使用和不安全的生命周期方法
 *
 * ## 依赖关系
 * - 挂载目标：index.html 中的 <div id="root"></div>
 * - 根组件：App.tsx
 */
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);