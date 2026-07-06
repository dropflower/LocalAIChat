import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            // 移除 Accept-Encoding 防止后端对 SSE 响应进行压缩
            // 压缩会导致整个响应被缓冲，破坏流式传输
            proxyReq.removeHeader('Accept-Encoding');
          });
          proxy.on('proxyRes', (proxyRes) => {
            // 对 SSE 响应添加防缓冲头，确保代理逐块转发
            const ct = proxyRes.headers['content-type'];
            if (ct && ct.includes('text/event-stream')) {
              proxyRes.headers['cache-control'] = 'no-cache, no-transform';
              proxyRes.headers['x-accel-buffering'] = 'no';
              proxyRes.headers['connection'] = 'keep-alive';
              // 移除 content-length 让代理使用 chunked transfer
              delete proxyRes.headers['content-length'];
            }
          });
        },
      },
    },
  },
})