package com.aiapp.service;

import com.aiapp.model.Message;
import com.aiapp.model.Session;
import com.aiapp.repository.MessageRepository;
import com.aiapp.repository.SessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SSE 格式验证测试类
 *
 * <p>功能描述：模拟前端 SSE 解析流程，验证后端 ChatService 输出的 SSE 事件格式
 * 是否与前端 ChatArea.tsx 的解析逻辑完全匹配。</p>
 *
 * <p>测试策略：调用 ChatService.chatStream() 获取 Flux&lt;String&gt;，
 * 收集所有事件并模拟前端解析逻辑（去除 data: 前缀、JSON.parse、按 type 分发），
 * 验证每个事件都能被正确解析且内容完整。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>所有事件输出为合法 JSON，前端 JSON.parse 不会静默失败</ul>
 *   <li>事件顺序：start → (search) → (thinking) → token → done</li>
 *   <li>token 事件的 content 字段非空且与 Ollama 原始输出一致</li>
 *   <li>done 事件不含 content 字段</li>
 *   <li>search 事件包含 count 和 sources 字段</li>
 *   <li>模拟前端解析器能从完整事件流还原出正确的消息内容</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SSEFormatVerificationTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private OllamaClientService ollamaClient;

    @Mock
    private WebSearchService webSearchService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ApplicationContext applicationContext;

    private ObjectMapper objectMapper = new ObjectMapper();

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(sessionRepository, messageRepository,
                ollamaClient, webSearchService, redisTemplate, objectMapper, applicationContext);
        ReflectionTestUtils.setField(chatService, "maxContextRounds", 5);
        ReflectionTestUtils.setField(chatService, "maxSessionMessages", 100);
    }

    /**
     * 辅助方法：设置基础 mock（新会话 + Ollama 流式响应）
     */
    private void setupBasicMocks(Flux<String> ollamaResponse) {
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(messageRepository.save(any(Message.class))).thenReturn(new Message());
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(anyLong()))
                .thenReturn(Collections.emptyList());
        when(ollamaClient.chatStream(eq("qwen"), anyList(), anyBoolean()))
                .thenReturn(ollamaResponse);
    }

    // ==================== 1. 基本事件流格式验证 ====================

    /**
     * 测试：正常对话流（无搜索、无深度思考）的事件格式
     *
     * <p>验证点：每个事件都是合法 JSON，前端能正确解析出 start → token → done 序列，
     * 并从 token 事件中还原出完整的 AI 回复内容</p>
     */
    @Test
    void normalChat_AllEventsValidJson_FrontendCanParse() throws Exception {
        // 模拟 Ollama 流式响应
        setupBasicMocks(Flux.just(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"你\"}}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"好\"}}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"！\"}}",
                "{\"done\":true}"
        ));

        List<String> events = chatService.chatStream(null, "qwen", "hello", false, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);
        assertFalse(events.isEmpty(), "应该输出至少一个事件");

        // 模拟前端解析器
        StringBuilder reconstructedContent = new StringBuilder();
        String firstType = null;
        String lastType = null;
        int eventCount = events.size();

        for (String event : events) {
            // 1. 验证每个事件都是合法 JSON
            JsonNode node = assertDoesNotThrow(() -> objectMapper.readTree(event),
                    "事件必须是合法 JSON: " + event);

            // 2. 验证 type 字段存在
            assertTrue(node.has("type"), "事件必须包含 type 字段: " + event);

            String type = node.get("type").asText();
            if (firstType == null) firstType = type;
            lastType = type;

            // 3. 按 type 分发，模拟前端逻辑
            switch (type) {
                case "start":
                    // 前端: break（不做任何事）
                    break;
                case "token":
                    // 前端: if (data.content) → appendToLastMessage(data.content)
                    assertTrue(node.has("content"), "token 事件必须包含 content 字段");
                    String content = node.get("content").asText();
                    assertFalse(content.isEmpty(), "token 事件的 content 不应为空");
                    reconstructedContent.append(content);
                    break;
                case "done":
                    // 前端: break
                    break;
                case "thinking":
                    assertTrue(node.has("content"), "thinking 事件必须包含 content 字段");
                    break;
                case "search":
                    assertTrue(node.has("count"), "search 事件必须包含 count 字段");
                    assertTrue(node.has("sources"), "search 事件必须包含 sources 字段");
                    break;
                case "error":
                    assertTrue(node.has("content"), "error 事件必须包含 content 字段");
                    break;
                default:
                    fail("未知事件类型: " + type);
            }
        }

        // 4. 验证事件序列
        assertEquals("start", firstType, "第一个事件应该是 start");
        assertEquals("done", lastType, "最后一个事件应该是 done");

        // 5. 验证还原的内容
        assertEquals("你好！", reconstructedContent.toString(),
                "前端应该能从 token 事件还原出完整回复内容");
    }

    /**
     * 测试：深度思考模式的事件格式
     *
     * <p>验证点：thinking 和 token 事件都能被正确解析，
     * thinkingContent 和 content 分别累积，不混淆</p>
     */
    @Test
    void deepThinkMode_ThinkingAndTokenBothValid() throws Exception {
        setupBasicMocks(Flux.just(
                "{\"message\":{\"role\":\"assistant\",\"reasoning_content\":\"思考1\",\"content\":\"\"}}",
                "{\"message\":{\"role\":\"assistant\",\"reasoning_content\":\"思考2\",\"content\":\"\"}}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"回答\"}}",
                "{\"done\":true}"
        ));

        List<String> events = chatService.chatStream(null, "qwen", "hello", true, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        StringBuilder thinkingContent = new StringBuilder();
        StringBuilder responseContent = new StringBuilder();

        for (String event : events) {
            JsonNode node = objectMapper.readTree(event);
            String type = node.get("type").asText();

            switch (type) {
                case "thinking":
                    String tc = node.get("content").asText();
                    assertFalse(tc.isEmpty(), "thinking content 不应为空");
                    thinkingContent.append(tc);
                    break;
                case "token":
                    String rc = node.get("content").asText();
                    assertFalse(rc.isEmpty(), "token content 不应为空");
                    responseContent.append(rc);
                    break;
            }
        }

        assertEquals("思考1思考2", thinkingContent.toString(),
                "前端应能还原完整思考内容");
        assertEquals("回答", responseContent.toString(),
                "前端应能还原完整回复内容");
    }

    /**
     * 测试：Ollama 返回空内容时，不应产生 token 事件
     *
     * <p>验证点：空 content 不产生 token 事件，前端不会创建空内容的 assistant 消息</p>
     */
    @Test
    void emptyContent_NoTokenEvents_FrontendNoEmptyMessage() throws Exception {
        setupBasicMocks(Flux.just(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}",
                "{\"done\":true}"
        ));

        List<String> events = chatService.chatStream(null, "qwen", "hello", false, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        // 检查是否只有 start 和 done 事件，没有 token 事件
        boolean hasTokenEvent = false;
        for (String event : events) {
            JsonNode node = objectMapper.readTree(event);
            if ("token".equals(node.get("type").asText())) {
                hasTokenEvent = true;
                // 如果有 token 事件，content 不应为空
                String content = node.get("content").asText();
                assertFalse(content.isEmpty(), "token 事件的 content 不应为空字符串");
            }
        }
        assertFalse(hasTokenEvent, "空 content 不应产生 token 事件");
    }

    /**
     * 测试：Ollama 返回包含特殊字符的内容
     *
     * <p>验证点：换行符、引号、反斜杠等特殊字符在 JSON 序列化后仍能被前端正确解析</p>
     */
    @Test
    void specialCharacters_InContent_JsonStillValid() throws Exception {
        setupBasicMocks(Flux.just(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"第一行\\n第二行\\\"引号\\\"反斜杠\\\\\"}}",
                "{\"done\":true}"
        ));

        List<String> events = chatService.chatStream(null, "qwen", "hello", false, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        for (String event : events) {
            // 验证 JSON 可以被重新解析（不会因为特殊字符导致解析失败）
            assertDoesNotThrow(() -> objectMapper.readTree(event),
                    "包含特殊字符的事件必须是合法 JSON: " + event);
        }

        // 找到 token 事件，验证内容
        for (String event : events) {
            JsonNode node = objectMapper.readTree(event);
            if ("token".equals(node.get("type").asText())) {
                String content = node.get("content").asText();
                assertTrue(content.contains("第一行"), "内容应包含中文");
                assertTrue(content.contains("\n"), "内容应包含换行符");
            }
        }
    }

    // ==================== 2. 搜索场景验证 ====================

    /**
     * 测试：启用搜索时的事件格式
     *
     * <p>验证点：search 事件包含 count 和 sources，且格式与前端 SSEData 接口匹配</p>
     */
    @Test
    void searchEnabled_SearchEventFormat_FrontendCanParse() throws Exception {
        setupBasicMocks(Flux.just(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"根据搜索结果\"}}",
                "{\"done\":true}"
        ));

        // Mock WebSearchService.searchWithContext()
        List<WebSearchService.SearchResultItem> searchResults = List.of(
                new WebSearchService.SearchResultItem("标题1", "https://example.com/1", "摘要1"),
                new WebSearchService.SearchResultItem("标题2", "https://example.com/2", "摘要2")
        );
        WebSearchService.SearchContext searchContext = new WebSearchService.SearchContext(
                searchResults, false, null, null);
        when(webSearchService.searchWithContext("测试搜索")).thenReturn(searchContext);
        when(webSearchService.formatResults(searchResults, false, null, null))
                .thenReturn("搜索结果内容");

        List<String> events = chatService.chatStream(null, "qwen", "测试搜索", false, true)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        // 查找 search 事件
        JsonNode searchEvent = null;
        for (String event : events) {
            JsonNode node = objectMapper.readTree(event);
            if ("search".equals(node.get("type").asText())) {
                searchEvent = node;
                break;
            }
        }

        assertNotNull(searchEvent, "应包含 search 事件");
        assertEquals(2, searchEvent.get("count").asInt(), "搜索结果数量应为 2");
        assertTrue(searchEvent.has("sources"), "search 事件应包含 sources 字段");
        assertTrue(searchEvent.get("sources").isArray(), "sources 应为数组");
        assertEquals(2, searchEvent.get("sources").size(), "sources 数组应有 2 个元素");

        // 验证 sources 数组的每个元素有 title 和 url
        JsonNode sources = searchEvent.get("sources");
        for (int i = 0; i < sources.size(); i++) {
            JsonNode source = sources.get(i);
            assertTrue(source.has("title"), "source 应包含 title 字段");
            assertTrue(source.has("url"), "source 应包含 url 字段");
        }
    }

    /**
     * 测试：天气查询的搜索事件格式
     *
     * <p>验证点：天气查询时 search 事件、token 事件格式均正确</p>
     */
    @Test
    void weatherQuery_SearchAndTokenEventsValid() throws Exception {
        setupBasicMocks(Flux.just(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"北京当前气温29°C\"}}",
                "{\"done\":true}"
        ));

        List<WebSearchService.SearchResultItem> searchResults = List.of(
                new WebSearchService.SearchResultItem("北京天气", "https://weather.com/bj", "北京今日天气")
        );
        WebSearchService.SearchContext weatherContext = new WebSearchService.SearchContext(
                searchResults, true, "北京", "实时天气数据内容");
        when(webSearchService.searchWithContext("北京天气怎么样")).thenReturn(weatherContext);
        when(webSearchService.formatResults(searchResults, true, "北京", "实时天气数据内容"))
                .thenReturn("天气格式化结果");

        List<String> events = chatService.chatStream(null, "qwen", "北京天气怎么样", false, true)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        // 模拟前端完整解析
        StringBuilder reconstructedContent = new StringBuilder();
        boolean foundSearchEvent = false;
        boolean foundTokenEvent = false;

        for (String event : events) {
            JsonNode node = objectMapper.readTree(event);
            String type = node.get("type").asText();

            switch (type) {
                case "search":
                    foundSearchEvent = true;
                    assertTrue(node.has("count"));
                    assertTrue(node.has("sources"));
                    break;
                case "token":
                    foundTokenEvent = true;
                    reconstructedContent.append(node.get("content").asText());
                    break;
            }
        }

        assertTrue(foundSearchEvent, "应包含 search 事件");
        assertTrue(foundTokenEvent, "应包含 token 事件");
        assertEquals("北京当前气温29°C", reconstructedContent.toString());
    }

    // ==================== 3. 模拟前端 SSE 解析器 ====================

    /**
     * 测试：完整模拟前端 SSE 解析器
     *
     * <p>模拟 ChatArea.tsx 的 SSE 解析逻辑：
     * 1. 将 SSE 原始文本按行分割
     * 2. 去除 data: 前缀
     * 3. JSON.parse 解析
     * 4. 按 type 分发处理
     * 5. 还原出最终的 ChatMessage 内容</p>
     *
     * <p>验证点：后端输出经过模拟前端解析后，能得到正确的消息内容，
     * 不会出现空内容或解析失败的情况</p>
     */
    @Test
    void simulateFrontendSSEParser_FullFlow_NoEmptyContent() throws Exception {
        // 模拟 Ollama 返回多段内容
        setupBasicMocks(Flux.just(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"你好\"}}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"，\"}}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"我是AI助手。\"}}",
                "{\"done\":true}"
        ));

        List<String> rawEvents = chatService.chatStream(null, "qwen", "你好", false, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(rawEvents);

        // === 模拟前端 SSE 解析器 ===
        // 步骤1: 构建 SSE 文本（模拟 ChatController 的 SseEmitter 输出）
        StringBuilder sseText = new StringBuilder();
        for (String event : rawEvents) {
            sseText.append("data:").append(event).append("\n\n");
        }

        // 步骤2: 按行分割并解析（模拟 ChatArea.tsx 的 handleSend）
        List<String> lines = Arrays.asList(sseText.toString().split("\n"));
        String buffer = "";

        // 前端状态
        String assistantContent = "";
        String thinkingContent = "";
        boolean hasAssistantMessage = false;
        int searchCount = 0;
        List<String> searchSources = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String jsonStr = trimmed;
            if (trimmed.startsWith("data:")) {
                jsonStr = trimmed.replaceFirst("^data:\\s*", "");
            }

            try {
                JsonNode data = objectMapper.readTree(jsonStr);
                String type = data.get("type").asText();

                switch (type) {
                    case "start":
                        // 前端: break
                        break;
                    case "thinking":
                        if (data.has("content") && !data.get("content").asText().isEmpty()) {
                            if (!hasAssistantMessage) {
                                thinkingContent = data.get("content").asText();
                                hasAssistantMessage = true;
                            } else {
                                thinkingContent += data.get("content").asText();
                            }
                        }
                        break;
                    case "search":
                        if (data.has("count")) {
                            searchCount = data.get("count").asInt();
                            if (data.has("sources")) {
                                for (JsonNode src : data.get("sources")) {
                                    searchSources.add(src.get("title").asText());
                                }
                            }
                            if (!hasAssistantMessage) {
                                hasAssistantMessage = true;
                            }
                        }
                        break;
                    case "token":
                        if (data.has("content") && !data.get("content").asText().isEmpty()) {
                            if (!hasAssistantMessage) {
                                assistantContent = data.get("content").asText();
                                hasAssistantMessage = true;
                            } else {
                                assistantContent += data.get("content").asText();
                            }
                        }
                        break;
                    case "done":
                        break;
                    case "error":
                        if (data.has("content")) {
                            if (!hasAssistantMessage) {
                                assistantContent = data.get("content").asText();
                                hasAssistantMessage = true;
                            } else {
                                assistantContent += data.get("content").asText();
                            }
                        }
                        break;
                }
            } catch (Exception e) {
                fail("前端 JSON.parse 失败: " + jsonStr + " 错误: " + e.getMessage());
            }
        }

        // 验证：前端应该能还原出完整的消息内容
        assertTrue(hasAssistantMessage, "前端应已创建 assistant 消息");
        assertEquals("你好，我是AI助手。", assistantContent,
                "前端应能还原完整回复内容，不应为空文字");
    }

    /**
     * 测试：深度思考 + 搜索 + 正常回复的完整场景
     *
     * <p>验证点：在最复杂场景下，前端仍能正确解析所有事件并还原消息内容</p>
     */
    @Test
    void fullScenario_DeepThinkAndSearch_NoEmptyContent() throws Exception {
        setupBasicMocks(Flux.just(
                "{\"message\":{\"role\":\"assistant\",\"reasoning_content\":\"让我想想\",\"content\":\"\"}}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"根据搜索结果\"}}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"，北京今天29°C\"}}",
                "{\"done\":true}"
        ));

        List<WebSearchService.SearchResultItem> searchResults = List.of(
                new WebSearchService.SearchResultItem("北京天气", "https://weather.com/bj", "晴")
        );
        WebSearchService.SearchContext ctx = new WebSearchService.SearchContext(
                searchResults, true, "北京", "实时天气数据");
        when(webSearchService.searchWithContext("北京天气")).thenReturn(ctx);
        when(webSearchService.formatResults(searchResults, true, "北京", "实时天气数据"))
                .thenReturn("天气结果");

        List<String> rawEvents = chatService.chatStream(null, "qwen", "北京天气", true, true)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(rawEvents);

        // 模拟前端解析
        String assistantContent = "";
        String thinkingContent = "";
        boolean hasAssistantMessage = false;

        for (String event : rawEvents) {
            JsonNode data = objectMapper.readTree(event);
            String type = data.get("type").asText();

            switch (type) {
                case "thinking":
                    if (data.has("content") && !data.get("content").asText().isEmpty()) {
                        if (!hasAssistantMessage) {
                            thinkingContent = data.get("content").asText();
                            hasAssistantMessage = true;
                        } else {
                            thinkingContent += data.get("content").asText();
                        }
                    }
                    break;
                case "token":
                    if (data.has("content") && !data.get("content").asText().isEmpty()) {
                        if (!hasAssistantMessage) {
                            assistantContent = data.get("content").asText();
                            hasAssistantMessage = true;
                        } else {
                            assistantContent += data.get("content").asText();
                        }
                    }
                    break;
            }
        }

        assertTrue(hasAssistantMessage);
        assertEquals("让我想想", thinkingContent);
        assertEquals("根据搜索结果，北京今天29°C", assistantContent,
                "前端应能还原完整内容，不应为空文字");
    }

    // ==================== 4. 边界场景验证 ====================

    /**
     * 测试：Ollama 返回 error 时，前端应收到 error 事件
     *
     * <p>验证点：error 事件包含可读的错误消息，前端不会显示空文字</p>
     */
    @Test
    void ollamaError_FrontendReceivesErrorMessage() throws Exception {
        setupBasicMocks(Flux.error(new RuntimeException("Connection refused")));
        when(applicationContext.getBean(ChatService.class)).thenReturn(chatService);
        when(messageRepository.countBySessionId(anyLong())).thenReturn(1);
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        lenient().when(sessionRepository.findById(1L)).thenReturn(Optional.of(
                Session.builder().id(1L).title("Test").modelName("qwen").build()));
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);

        List<String> events = chatService.chatStream(null, "qwen", "hello", false, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        // 验证有 error 事件
        boolean foundError = false;
        for (String event : events) {
            JsonNode node = objectMapper.readTree(event);
            if ("error".equals(node.get("type").asText())) {
                foundError = true;
                assertTrue(node.has("content"), "error 事件应包含 content 字段");
                String errorContent = node.get("content").asText();
                assertFalse(errorContent.isEmpty(), "error 事件的 content 不应为空");
                assertTrue(errorContent.contains("请求失败"), "错误消息应包含'请求失败'");
            }
        }
        assertTrue(foundError, "应包含 error 事件");
    }

    /**
     * 测试：Ollama 返回同时包含 reasoning_content 和 content 的 chunk
     *
     * <p>验证点：两个事件都应被正确生成，前端不会丢失 content</p>
     */
    @Test
    void chunkWithBothReasoningAndContent_BothEventsGenerated() throws Exception {
        setupBasicMocks(Flux.just(
                "{\"message\":{\"role\":\"assistant\",\"reasoning_content\":\"思考\",\"content\":\"回答\"}}",
                "{\"done\":true}"
        ));

        List<String> events = chatService.chatStream(null, "qwen", "hello", true, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        // 应同时有 thinking 和 token 事件
        boolean foundThinking = false;
        boolean foundToken = false;

        for (String event : events) {
            JsonNode node = objectMapper.readTree(event);
            String type = node.get("type").asText();
            if ("thinking".equals(type)) {
                foundThinking = true;
                assertEquals("思考", node.get("content").asText());
            }
            if ("token".equals(type)) {
                foundToken = true;
                assertEquals("回答", node.get("content").asText());
            }
        }

        assertTrue(foundThinking, "应包含 thinking 事件");
        assertTrue(foundToken, "应包含 token 事件（content 不应丢失）");
    }

    /**
     * 测试：搜索无结果时不会导致空消息
     *
     * <p>验证点：搜索返回空结果但 Ollama 仍正常回复时，前端不会收到空内容的消息</p>
     */
    @Test
    void searchNoResults_OllamaStillResponds_FrontendHasContent() throws Exception {
        setupBasicMocks(Flux.just(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"我没有找到相关信息\"}}",
                "{\"done\":true}"
        ));

        // Mock: 搜索无结果
        WebSearchService.SearchContext emptyContext = new WebSearchService.SearchContext(
                Collections.emptyList(), false, null, null);
        when(webSearchService.searchWithContext("测试")).thenReturn(emptyContext);

        List<String> events = chatService.chatStream(null, "qwen", "测试", false, true)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        // 验证不应有 search 事件（没有搜索结果）
        // 但应该有 token 事件（Ollama 正常回复）
        boolean foundSearchEvent = false;
        boolean foundTokenEvent = false;

        for (String event : events) {
            JsonNode node = objectMapper.readTree(event);
            String type = node.get("type").asText();
            if ("search".equals(type)) foundSearchEvent = true;
            if ("token".equals(type)) foundTokenEvent = true;
        }

        assertFalse(foundSearchEvent, "搜索无结果时不应发送 search 事件");
        assertTrue(foundTokenEvent, "Ollama 应正常回复，前端应有内容");
    }
}
