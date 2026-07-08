package com.aiapp.service;

import com.aiapp.model.Message;
import com.aiapp.model.Session;
import com.aiapp.repository.MessageRepository;
import com.aiapp.repository.SessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SSE 端到端测试 — 模拟从前端请求到后端响应的完整流程
 *
 * <p>功能描述：验证 ChatService 产生的 SSE 事件流能被前端正确解析为可见文字。
 * 覆盖普通对话、深度思考、联网搜索三种场景，重点检测"输出空文字"问题。</p>
 *
 * <p>核心验证逻辑：
 * 1. 收集 chatStream() 产生的所有 SSE 事件 JSON
 * 2. 用与前端的 ChatArea.tsx 相同的解析逻辑逐行解析
 * 3. 断言最终组装出的 assistant 消息内容非空</p>
 */
@ExtendWith(MockitoExtension.class)
class SSEEndToEndTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private OllamaClientService ollamaClient;
    @Mock private WebSearchService webSearchService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ApplicationContext applicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                sessionRepository, messageRepository, ollamaClient,
                webSearchService, redisTemplate, objectMapper, applicationContext);
        ReflectionTestUtils.setField(chatService, "maxContextRounds", 5);
        ReflectionTestUtils.setField(chatService, "maxSessionMessages", 100);
    }

    // ==================== 模拟前端 SSE 解析器 ====================

    /**
     * 模拟前端 ChatArea.tsx 的 SSE 解析逻辑
     * 输入：后端 SseEmitter 发出的完整 SSE 文本
     * 输出：解析后组装的 assistant 消息内容
     */
    private String simulateFrontendParsing(List<String> backendEvents) {
        // 模拟 SseEmitter.event().data(event) 的格式：每行 "data:<json>\n\n"
        StringBuilder sseText = new StringBuilder();
        for (String event : backendEvents) {
            sseText.append("data:").append(event).append("\n\n");
        }

        // 模拟前端的逐行解析逻辑（与 ChatArea.tsx handleSend 一致）
        String assistantContent = "";
        String thinkingContent = "";
        boolean hasAssistant = false;

        String buffer = sseText.toString();
        String[] lines = buffer.split("\n");

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
                        break;
                    case "thinking":
                        if (data.has("content") && !data.get("content").asText().isEmpty()) {
                            if (!hasAssistant) {
                                thinkingContent = data.get("content").asText();
                                hasAssistant = true;
                            } else {
                                thinkingContent += data.get("content").asText();
                            }
                        }
                        break;
                    case "search":
                        // 搜索事件不包含文本内容
                        if (!hasAssistant) {
                            hasAssistant = true;
                        }
                        break;
                    case "token":
                        if (data.has("content") && !data.get("content").asText().isEmpty()) {
                            if (!hasAssistant) {
                                assistantContent = data.get("content").asText();
                                hasAssistant = true;
                            } else {
                                assistantContent += data.get("content").asText();
                            }
                        }
                        break;
                    case "done":
                        break;
                    case "error":
                        if (data.has("content") && !data.get("content").asText().isEmpty()) {
                            if (!hasAssistant) {
                                assistantContent = data.get("content").asText();
                                hasAssistant = true;
                            } else {
                                assistantContent += data.get("content").asText();
                            }
                        }
                        break;
                }
            } catch (Exception e) {
                // 忽略非 JSON 行（与前端 catch 行为一致）
            }
        }

        return assistantContent;
    }

    // ==================== 基础 Mock 设置 ====================

    private void setupSessionMocks() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(
                Session.builder().id(1L).title("Test").modelName("qwen").build()));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());
        lenient().when(applicationContext.getBean(ChatService.class)).thenReturn(chatService);
        lenient().when(messageRepository.countBySessionId(1L)).thenReturn(1);
        lenient().when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
    }

    private void setupOllamaNormalResponse() {
        // 模拟 Ollama 正常流式返回
        when(ollamaClient.chatStream(eq("qwen"), anyList(), anyBoolean()))
                .thenReturn(Flux.just(
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"你\"}}",
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"好\"}}",
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"！\"}}",
                        "{\"done\":true}"
                ));
    }

    private void setupOllamaDeepThinkResponse() {
        // 模拟 Ollama 深度思考流式返回
        when(ollamaClient.chatStream(eq("deepseek-r1:8b"), anyList(), eq(true)))
                .thenReturn(Flux.just(
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"reasoning_content\":\"让我\"}}",
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"reasoning_content\":\"想想\"}}",
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"你好\"}}",
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"世界\"}}",
                        "{\"done\":true}"
                ));
    }

    private void setupSearchMocks() {
        // 模拟搜索结果
        List<WebSearchService.SearchResultItem> searchResults = List.of(
                new WebSearchService.SearchResultItem("测试标题", "https://example.com", "测试摘要")
        );
        WebSearchService.SearchContext searchContext = new WebSearchService.SearchContext(
                searchResults, false, null, null);
        when(webSearchService.searchWithContext("搜索测试")).thenReturn(searchContext);
        when(webSearchService.formatResults(searchResults, false, null, null))
                .thenReturn("1. **测试标题**\n   测试摘要\n   来源: https://example.com");
    }

    // ==================== 测试用例 ====================

    @Test
    @DisplayName("普通对话：前端应能从 SSE 事件流中正确组装出非空文字")
    void normalChat_FrontendShouldReceiveNonEmptyContent() {
        setupSessionMocks();
        setupOllamaNormalResponse();

        List<String> events = chatService.chatStream(1L, "qwen", "你好", false, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);
        assertFalse(events.isEmpty(), "SSE 事件列表不应为空");

        // 模拟前端解析
        String frontendContent = simulateFrontendParsing(events);

        assertFalse(frontendContent.isEmpty(),
                "前端解析后的内容不应为空！实际事件: " + events);
        assertTrue(frontendContent.contains("你好！"),
                "前端应收到完整内容，实际: " + frontendContent);
    }

    @Test
    @DisplayName("深度思考：前端应同时收到思考过程和回复内容")
    void deepThink_FrontendShouldReceiveBothThinkingAndContent() {
        setupSessionMocks();
        setupOllamaDeepThinkResponse();

        List<String> events = chatService.chatStream(1L, "deepseek-r1:8b", "你好", true, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        // 验证事件类型
        boolean hasThinking = events.stream().anyMatch(e -> e.contains("\"type\":\"thinking\""));
        boolean hasToken = events.stream().anyMatch(e -> e.contains("\"type\":\"token\""));
        assertTrue(hasThinking, "应包含 thinking 事件");
        assertTrue(hasToken, "应包含 token 事件");

        // 模拟前端解析
        String frontendContent = simulateFrontendParsing(events);
        assertFalse(frontendContent.isEmpty(),
                "深度思考模式下前端内容不应为空！实际事件: " + events);
        assertTrue(frontendContent.contains("你好世界"),
                "深度思考模式前端应收到完整回复，实际: " + frontendContent);
    }

    @Test
    @DisplayName("联网搜索：前端应收到搜索事件和回复内容")
    void searchChat_FrontendShouldReceiveSearchAndContent() {
        setupSessionMocks();
        setupOllamaNormalResponse();
        setupSearchMocks();

        List<String> events = chatService.chatStream(1L, "qwen", "搜索测试", false, true)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        // 验证搜索事件
        boolean hasSearch = events.stream().anyMatch(e -> e.contains("\"type\":\"search\""));
        assertTrue(hasSearch, "应包含 search 事件");

        // 验证搜索事件格式
        Optional<String> searchEvent = events.stream()
                .filter(e -> e.contains("\"type\":\"search\""))
                .findFirst();
        assertTrue(searchEvent.isPresent());
        assertDoesNotThrow(() -> {
            JsonNode node = objectMapper.readTree(searchEvent.get());
            assertEquals("search", node.get("type").asText());
            assertTrue(node.has("count"), "search 事件应有 count 字段");
            assertTrue(node.has("sources"), "search 事件应有 sources 字段");
        }, "search 事件应为有效 JSON");

        // 模拟前端解析
        String frontendContent = simulateFrontendParsing(events);
        assertFalse(frontendContent.isEmpty(),
                "搜索模式下前端内容不应为空！实际事件: " + events);
    }

    @Test
    @DisplayName("SSE 事件 JSON 格式验证：每个事件都应是可解析的有效 JSON")
    void allEvents_ShouldBeValidJson() {
        setupSessionMocks();
        setupOllamaNormalResponse();

        List<String> events = chatService.chatStream(1L, "qwen", "你好", false, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);
        for (int i = 0; i < events.size(); i++) {
            final int idx = i;
            assertDoesNotThrow(() -> objectMapper.readTree(events.get(idx)),
                    "第 " + idx + " 个事件不是有效 JSON: " + events.get(idx));
        }
    }

    @Test
    @DisplayName("SSE 事件中的 content 字段不应为 null 或空字符串（token 事件）")
    void tokenEvents_ContentShouldNotBeNullOrEmpty() {
        setupSessionMocks();
        setupOllamaNormalResponse();

        List<String> events = chatService.chatStream(1L, "qwen", "你好", false, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        for (String event : events) {
            if (event.contains("\"type\":\"token\"")) {
                assertDoesNotThrow(() -> {
                    JsonNode node = objectMapper.readTree(event);
                    assertTrue(node.has("content"),
                            "token 事件缺少 content 字段: " + event);
                    String content = node.get("content").asText();
                    assertFalse(content.isEmpty(),
                            "token 事件的 content 不应为空字符串: " + event);
                });
            }
        }
    }

    @Test
    @DisplayName("Ollama 错误：前端应收到错误消息而非空内容")
    void ollamaError_FrontendShouldReceiveErrorMessage() {
        setupSessionMocks();
        when(ollamaClient.chatStream(eq("qwen"), anyList(), anyBoolean()))
                .thenReturn(Flux.error(new RuntimeException("Ollama connection refused")));

        List<String> events = chatService.chatStream(1L, "qwen", "你好", false, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        // 模拟前端解析
        String frontendContent = simulateFrontendParsing(events);
        assertFalse(frontendContent.isEmpty(),
                "Ollama 错误时前端应收到错误消息！实际事件: " + events);
        assertTrue(frontendContent.contains("请求失败"),
                "前端应显示请求失败提示，实际: " + frontendContent);
    }

    @Test
    @DisplayName("Ollama 返回空内容：前端不应显示空字符串")
    void emptyOllamaResponse_FrontendShouldNotShowEmptyString() {
        setupSessionMocks();
        // Ollama 返回只有 done=true 的流（无实际内容）
        when(ollamaClient.chatStream(eq("qwen"), anyList(), anyBoolean()))
                .thenReturn(Flux.just("{\"done\":true}"));

        List<String> events = chatService.chatStream(1L, "qwen", "你好", false, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        // 模拟前端解析
        String frontendContent = simulateFrontendParsing(events);
        // 当 Ollama 无内容时，前端内容应为空（但没有空字符串气泡问题）
        // 关键验证：如果有 token 事件，content 不应为空
        long tokenEvents = events.stream()
                .filter(e -> e.contains("\"type\":\"token\""))
                .count();
        if (tokenEvents > 0) {
            assertFalse(frontendContent.isEmpty(),
                    "有 token 事件但前端内容为空！事件: " + events);
        }
    }

    @Test
    @DisplayName("包含特殊字符的内容应被正确序列化和解析")
    void specialCharacters_ShouldBeSerializedAndParsedCorrectly() {
        setupSessionMocks();
        // 模拟包含换行、引号、中文标点的内容
        when(ollamaClient.chatStream(eq("qwen"), anyList(), anyBoolean()))
                .thenReturn(Flux.just(
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"第一行\\n第二行\"}}",
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"含\\\"引号\\\"和中文标点，。\"}}",
                        "{\"done\":true}"
                ));

        List<String> events = chatService.chatStream(1L, "qwen", "特殊字符测试", false, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        // 验证每个事件都是有效 JSON
        for (String event : events) {
            assertDoesNotThrow(() -> objectMapper.readTree(event),
                    "包含特殊字符的事件不是有效 JSON: " + event);
        }

        // 模拟前端解析
        String frontendContent = simulateFrontendParsing(events);
        assertFalse(frontendContent.isEmpty(),
                "包含特殊字符的内容不应为空！事件: " + events);
        assertTrue(frontendContent.contains("第一行"),
                "前端应正确解析含换行的内容，实际: " + frontendContent);
    }

    @Test
    @DisplayName("搜索事件格式应包含 count 和 sources 字段")
    void searchEvent_ShouldHaveCountAndSourcesFields() {
        setupSessionMocks();
        setupOllamaNormalResponse();
        setupSearchMocks();

        List<String> events = chatService.chatStream(1L, "qwen", "搜索测试", false, true)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);

        Optional<String> searchEventOpt = events.stream()
                .filter(e -> e.contains("\"type\":\"search\""))
                .findFirst();

        assertTrue(searchEventOpt.isPresent(), "应包含 search 事件");

        JsonNode searchNode = assertDoesNotThrow(() -> objectMapper.readTree(searchEventOpt.get()));
        assertEquals("search", searchNode.get("type").asText());
        assertEquals(1, searchNode.get("count").asInt(), "搜索结果数量应为 1");
        assertTrue(searchNode.has("sources"), "应有 sources 字段");
        assertTrue(searchNode.get("sources").isArray(), "sources 应为数组");
        assertEquals(1, searchNode.get("sources").size(), "sources 应有 1 个元素");

        JsonNode source = searchNode.get("sources").get(0);
        assertEquals("测试标题", source.get("title").asText());
        assertEquals("https://example.com", source.get("url").asText());
    }

    @Test
    @DisplayName("StepVerifier: 验证完整事件序列顺序 (start → token* → done)")
    void eventSequence_ShouldBeStartTokenDone() {
        setupSessionMocks();
        setupOllamaNormalResponse();

        StepVerifier.create(chatService.chatStream(1L, "qwen", "你好", false, false))
                .assertNext(event -> {
                    JsonNode node = assertDoesNotThrow(() -> objectMapper.readTree(event));
                    assertEquals("start", node.get("type").asText(),
                            "第一个事件应为 start");
                })
                .assertNext(event -> {
                    JsonNode node = assertDoesNotThrow(() -> objectMapper.readTree(event));
                    assertEquals("token", node.get("type").asText(),
                            "第二个事件应为 token");
                })
                .assertNext(event -> {
                    JsonNode node = assertDoesNotThrow(() -> objectMapper.readTree(event));
                    assertEquals("token", node.get("type").asText());
                })
                .assertNext(event -> {
                    JsonNode node = assertDoesNotThrow(() -> objectMapper.readTree(event));
                    assertEquals("token", node.get("type").asText());
                })
                .assertNext(event -> {
                    JsonNode node = assertDoesNotThrow(() -> objectMapper.readTree(event));
                    assertEquals("done", node.get("type").asText(),
                            "最后一个事件应为 done");
                })
                .verifyComplete();
    }
}
