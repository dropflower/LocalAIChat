import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * 前端 SSE 解析器测试
 *
 * <p>功能描述：直接测试 ChatArea.tsx 中的 SSE 解析逻辑（不依赖 React 渲染），
 * 验证从后端接收的原始 SSE 文本能否被正确解析为 ChatMessage 对象。</p>
 *
 * <p>测试策略：提取 ChatArea.tsx 中的核心解析逻辑，使用模拟数据验证：
 * 1. 每种事件类型的 JSON.parse 是否成功
 * 2. 按 type 分发后的状态变化是否正确
 * 3. 最终还原的消息内容是否完整且非空</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>正常对话流（start → token → done）能正确还原 AI 回复</li>
 *   <li>深度思考模式（thinking + token）能分别累积两种内容</li>
 *   <li>搜索模式（search + token）能正确设置搜索元数据</li>
 *   <li>错误场景（error 事件）能显示错误消息而非空内容</li>
 *   <li>Ollama 空回复不会导致空 assistant 消息</li>
 * </ul>
 * </p>
 */

/**
 * 模拟前端的 SSE 解析逻辑（提取自 ChatArea.tsx 的 handleSend 方法）
 *
 * @param sseText 后端返回的原始 SSE 文本（包含 data: 前缀和换行）
 * @returns 解析后的消息对象，包含最终内容和思考过程
 */
function parseSSE(sseText: string): {
  content: string;
  thinkingContent: string;
  hasAssistantMessage: boolean;
  searchCount: number;
  searchSources: Array<{ title: string; url: string }>;
} {
  let content = '';
  let thinkingContent = '';
  const searchSources: Array<{ title: string; url: string }> = [];
  let searchCount = 0;
  let hasAssistantMessage = false;

  // 模拟 ChatArea.tsx 的逐行解析逻辑
  const lines = sseText.split('\n');
  for (const rawLine of lines) {
    const trimmed = rawLine.trim();
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
            if (!hasAssistantMessage) {
              thinkingContent = data.content;
              hasAssistantMessage = true;
            } else {
              thinkingContent += data.content;
            }
          }
          break;
        case 'search':
          if (data.count !== undefined) {
            searchCount = data.count;
            if (data.sources && Array.isArray(data.sources)) {
              for (const src of data.sources) {
                searchSources.push(src as { title: string; url: string });
              }
            }
            if (!hasAssistantMessage) {
              hasAssistantMessage = true;
            }
          }
          break;
        case 'token':
          if (data.content) {
            if (!hasAssistantMessage) {
              content = data.content;
              hasAssistantMessage = true;
            } else {
              content += data.content;
            }
          }
          break;
        case 'done':
          break;
        case 'error':
          if (data.content) {
            if (!hasAssistantMessage) {
              content = data.content;
              hasAssistantMessage = true;
            } else {
              content += data.content;
            }
          }
          break;
      }
    } catch {
      // 忽略非 JSON 行（与前端行为一致）
    }
  }

  return { content, thinkingContent, hasAssistantMessage, searchCount, searchSources };
}

describe('SSE Parsing', () => {

  describe('正常对话流', () => {
    it('should parse normal chat stream and restore complete content', () => {
      const sseText = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"token","content":"你"}',
        '',
        'data:{"type":"token","content":"好"}',
        '',
        'data:{"type":"token","content":"！"}',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\n');

      const result = parseSSE(sseText);

      expect(result.hasAssistantMessage).toBe(true);
      expect(result.content).toBe('你好！');
    });

    it('should not create empty assistant message when Ollama returns empty content', () => {
      const sseText = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\n');

      const result = parseSSE(sseText);

      expect(result.hasAssistantMessage).toBe(false);
      expect(result.content).toBe('');
    });

    it('should handle multi-line content correctly', () => {
      const sseText = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"token","content":"第一行\\n第二行"}',
        '',
        'data:{"type":"token","content":"\\n第三行"}',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\n');

      const result = parseSSE(sseText);

      expect(result.hasAssistantMessage).toBe(true);
      expect(result.content).toContain('第一行');
      expect(result.content).toContain('第三行');
    });
  });

  describe('深度思考模式', () => {
    it('should accumulate thinking and content separately', () => {
      const sseText = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"thinking","content":"思考1"}',
        '',
        'data:{"type":"thinking","content":"思考2"}',
        '',
        'data:{"type":"token","content":"回答"}',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\n');

      const result = parseSSE(sseText);

      expect(result.hasAssistantMessage).toBe(true);
      expect(result.thinkingContent).toBe('思考1思考2');
      expect(result.content).toBe('回答');
    });

    it('should handle thinking-only response without token events', () => {
      const sseText = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"thinking","content":"思考中"}',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\n');

      const result = parseSSE(sseText);

      // thinking 事件会创建 assistant 消息占位
      expect(result.hasAssistantMessage).toBe(true);
      expect(result.thinkingContent).toBe('思考中');
      // 但没有 token 事件时 content 应该为空字符串（不是 undefined）
      expect(result.content).toBe('');
    });

    it('should handle chunk with both reasoning_content and content', () => {
      const sseText = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"thinking","content":"思考过程"}',
        '',
        'data:{"type":"token","content":"实际回复"}',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\n');

      const result = parseSSE(sseText);

      expect(result.thinkingContent).toBe('思考过程');
      expect(result.content).toBe('实际回复');
    });
  });

  describe('搜索模式', () => {
    it('should parse search event and set metadata', () => {
      const sseText = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"search","count":3,"sources":[{"title":"结果1","url":"https://a.com"},{"title":"结果2","url":"https://b.com"},{"title":"结果3","url":"https://c.com"}]}',
        '',
        'data:{"type":"token","content":"根据搜索结果"}',
        '',
        'data:{"type":"token","content":"，答案是..."}',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\n');

      const result = parseSSE(sseText);

      expect(result.hasAssistantMessage).toBe(true);
      expect(result.searchCount).toBe(3);
      expect(result.searchSources).toHaveLength(3);
      expect(result.searchSources[0].title).toBe('结果1');
      expect(result.searchSources[0].url).toBe('https://a.com');
      expect(result.content).toBe('根据搜索结果，答案是...');
    });

    it('should handle search + deepThink + token combined scenario', () => {
      const sseText = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"thinking","content":"让我想想天气"}',
        '',
        'data:{"type":"search","count":2,"sources":[{"title":"北京天气","url":"https://w.com/bj"}]}',
        '',
        'data:{"type":"token","content":"北京今天29°C"}',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\n');

      const result = parseSSE(sseText);

      expect(result.hasAssistantMessage).toBe(true);
      expect(result.thinkingContent).toBe('让我想想天气');
      expect(result.searchCount).toBe(2);
      expect(result.content).toBe('北京今天29°C');
    });
  });

  describe('错误处理', () => {
    it('should show error message instead of empty text on Ollama failure', () => {
      const sseText = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"error","content":"\\n\\n[请求失败，请检查 Ollama 服务是否运行]"}',
        '',
      ].join('\n');

      const result = parseSSE(sseText);

      expect(result.hasAssistantMessage).toBe(true);
      expect(result.content).toContain('请求失败');
    });
  });

  describe('边界情况', () => {
    it('should handle empty lines between events gracefully', () => {
      const sseText = '\n\n\ndata:{"type":"start"}\n\n\n\ndata:{"type":"token","content":"hi"}\n\n\ndata:{"type":"done"}\n\n';

      const result = parseSSE(sseText);

      expect(result.hasAssistantMessage).toBe(true);
      expect(result.content).toBe('hi');
    });

    it('should ignore non-JSON lines silently', () => {
      const sseText = [
        'data:{"type":"start"}',
        '',
        ': some comment line',
        '',
        'data:{"type":"token","content":"ok"}',
        '',
        'data:invalid json here',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\n');

      // 不应抛异常
      const result = parseSSE(sseText);

      expect(result.hasAssistantMessage).toBe(true);
      expect(result.content).toBe('ok');
    });

    it('should handle special characters in JSON content', () => {
      // 测试中文、引号、换行等特殊字符
      const specialContent = JSON.stringify({
        type: 'token',
        content: '他说："你好世界！"\n这是新的一行'
      });

      const sseText = `data:${specialContent}\n\ndata:{"type":"done"}\n`;

      const result = parseSSE(sseText);

      expect(result.hasAssistantMessage).toBe(true);
      expect(result.content).toContain('你好世界');
      expect(result.content).toContain('新的一行');
    });

    it('should not crash on malformed JSON', () => {
      const sseText = 'data:{"type":broken}\ndata:{"type":"token","content":"recovered"}\ndata:{"type":"done"}\n';

      // 不应抛异常
      expect(() => parseSSE(sseText)).not.toThrow();

      const result = parseSSE(sseText);
      expect(result.content).toBe('recovered');
    });

    it('should handle single-character tokens correctly', () => {
      const sseText = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"token","content":"A"}',
        '',
        'data:{"type":"token","content":"B"}',
        '',
        'data:{"type":"token","content":"C"}',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\n');

      const result = parseSSE(sseText);

      expect(result.content).toBe('ABC');
    });

    it('should handle whitespace-only content as valid token', () => {
      // 注意：Ollama 可能输出空白字符（如换行、空格）
      const sseText = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"token","content":" "}',
        '',
        'data:{"type":"token","content":"hello"}',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\n');

      const result = parseSSE(sseText);

      expect(result.hasAssistantMessage).toBe(true);
      // 空白字符也会被追加到 content 中
      expect(result.content).toBe(' hello');
    });
  });

  describe('真实场景模拟', () => {
    /**
     * 模拟真实的后端 SSE 输出格式
     * 这是直接从后端测试得到的实际数据格式
     */
    it('should correctly parse real backend SSE output format', () => {
      // 模拟后端 SseEmitter 输出的真实格式（每个事件用 \n\n 分隔）
      const realBackendOutput = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"token","content":"Hello"}',
        '',
        'data:{"type":"token","content":"!"}',
        '',
        'data:{"type":"token","content":" How can I help you today?"}',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\r\n'); // Windows 风格换行

      const result = parseSSE(realBackendOutput);

      expect(result.hasAssistantMessage).toBe(true);
      expect(result.content).toBe('Hello! How can I help you today?');
    });

    it('should handle Chinese content from backend correctly', () => {
      const chineseOutput = [
        'data:{"type":"start"}',
        '',
        'data:{"type":"token","content":"你好"}',
        '',
        'data:{"type":"token","content":"！很高兴见到你。"}',
        '',
        'data:{"type":"token","content":"有什么我可以帮你的吗？"}',
        '',
        'data:{"type":"done"}',
        '',
      ].join('\n');

      const result = parseSSE(chineseOutput);

      expect(result.hasAssistantMessage).toBe(true);
      expect(result.content).toBe('你好！很高兴见到你。有什么我可以帮你的吗？');
    });
  });
});
