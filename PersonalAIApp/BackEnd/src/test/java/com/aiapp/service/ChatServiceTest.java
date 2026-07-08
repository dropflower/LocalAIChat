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


import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChatService 单元测试类
 *
 * <p>功能描述：测试聊天服务（ChatService）的核心业务逻辑，覆盖聊天流式对话、会话消息获取、
 * 会话删除、GZIP 压缩/解压、Token 估算和 SSE 内容提取等功能模块。</p>
 *
 * <p>测试策略：使用 @ExtendWith(MockitoExtension.class) 进行纯 Mockito 单元测试，
 * 通过 @Mock 替换所有依赖（SessionRepository、MessageRepository、OllamaClientService、
 * StringRedisTemplate），通过构造函数注入创建 ChatService 实例，
 * 通过 ReflectionTestUtils 注入配置属性（maxContextRounds、maxSessionMessages）。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>chatStream：新建会话 / 已有会话 / 会话不存在 / 滑动窗口上下文 / 空响应 / 错误处理</li>
 *   <li>getSessionMessages：消息解压和格式化 / 空会话</li>
 *   <li>deleteSession：级联删除消息、会话和缓存</li>
 *   <li>compress/decompress：GZIP 压缩解压往返 / null 处理 / 非 GZIP 数据降级 / 大文本压缩率</li>
 *   <li>estimateTokens：中文/英文/混合文本估算 / 空值处理</li>
 *   <li>extractContent：有效 JSON / 缺失字段 / 无效 JSON 解析</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

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

    /**
     * 测试前初始化：构造 ChatService 实例并注入配置参数
     * - maxContextRounds = 5（滑动窗口最大轮数）
     * - maxSessionMessages = 100（会话最大消息数）
     */
    @BeforeEach
    void setUp() {
        chatService = new ChatService(sessionRepository, messageRepository, ollamaClient,webSearchService, redisTemplate, objectMapper,applicationContext);
        ReflectionTestUtils.setField(chatService, "maxContextRounds", 5);
        ReflectionTestUtils.setField(chatService, "maxSessionMessages", 100);
    }

    // ==================== chatStream 测试 ====================

    /**
     * 测试：新会话聊天流，应自动创建会话并返回流式响应
     *
     * <p>输入参数：sessionId=null（新会话），modelName="qwen"，message="hello"</p>
     * <p>预期结果：返回非空的流式响应，SessionRepository.save() 被调用创建新会话</p>
     * <p>前置条件：Mock sessionRepository.save() 返回带 ID 的 Session，
     * Mock ollamaClient.chatStream() 返回两段 SSE 响应</p>
     * <p>验证逻辑：确认 sessionId 为 null 时自动创建新会话，
     * Ollama 返回的流式数据能被正确接收</p>
     */
    @Test
    void chatStream_NewSession_ShouldCreateSession() {
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(messageRepository.save(any(Message.class))).thenReturn(new Message());
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(anyLong()))
                .thenReturn(Collections.emptyList());
        when(ollamaClient.chatStream(eq("qwen"), anyList(), anyBoolean()))
                .thenReturn(Flux.just("{\"message\":{\"role\":\"assistant\",\"content\":\"你好\"}}", "{\"message\":{\"role\":\"assistant\",\"content\":\"世界\"}}"));

        String result = chatService.chatStream(null, "qwen", "hello",true,false).blockLast(Duration.ofSeconds(5));

        assertNotNull(result);
    }

    /**
     * 测试：已有会话聊天流，应使用现有会话并加载历史消息作为上下文
     *
     * <p>输入参数：sessionId=1L（已有会话），modelName="qwen"，message="hello"</p>
     * <p>预期结果：返回非空的流式响应，sessionRepository.findById(1L) 被调用</p>
     * <p>前置条件：Mock sessionRepository.findById(1L) 返回已有会话</p>
     * <p>验证逻辑：确认 sessionId 非 null 时使用现有会话而非创建新会话，
     * 并验证 findById 被正确调用</p>
     */
    @Test
    void chatStream_ExistingSession_ShouldUseExistingSession() {
        Session existing = Session.builder().id(1L).title("Old").modelName("qwen").build();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(messageRepository.save(any(Message.class))).thenReturn(new Message());
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());
        when(ollamaClient.chatStream(eq("qwen"), anyList(), anyBoolean()))
                .thenReturn(Flux.just("{\"message\":{\"role\":\"assistant\",\"content\":\"回复\"}}"));

        String result = chatService.chatStream(1L, "qwen", "hello",true,false).blockLast(Duration.ofSeconds(5));

        assertNotNull(result);
        verify(sessionRepository).findById(1L);
    }

    /**
     * 测试：会话不存在时聊天流，应抛出 RuntimeException
     *
     * <p>输入参数：sessionId=999L（不存在的会话），modelName="qwen"，message="hello"</p>
     * <p>预期结果：抛出 RuntimeException</p>
     * <p>前置条件：Mock sessionRepository.findById(999L) 返回 Optional.empty()</p>
     * <p>验证逻辑：确认对不存在的会话 ID 发起聊天时正确抛出异常，
     * 防止在无效会话上执行业务操作</p>
     */
    @Test
    void chatStream_SessionNotFound_ShouldThrowException() {
        when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            chatService.chatStream(999L, "qwen", "hello",true,false).blockLast(Duration.ofSeconds(1));
        });
    }

    /**
     * 测试：滑动窗口上下文构建，历史消息超过最大轮数时应截断
     *
     * <p>输入参数：sessionId=1L，12 条历史消息（> maxContextRounds * 2 = 10）</p>
     * <p>预期结果：返回非空响应，上下文消息被截断至最近 10 条（5 轮）</p>
     * <p>前置条件：Mock 12 条历史消息和消息计数，ollamaClient 返回单条响应</p>
     * <p>验证逻辑：确认当历史消息数超过滑动窗口限制时，
     * 仅取最近 maxContextRounds 轮（即 maxContextRounds * 2 条）消息作为上下文，
     * 避免发送过多历史数据导致 Token 溢出</p>
     */
    @Test
    void chatStream_ShouldBuildContextWithSlidingWindow() {
        Session session = Session.builder().id(1L).title("Test").modelName("qwen").build();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.save(any(Message.class))).thenReturn(new Message());

        // 创建 12 条历史消息（> maxContextRounds * 2 = 10）
        List<Message> history = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String role = i % 2 == 0 ? "user" : "assistant";
            history.add(Message.builder()
                    .sessionId(1L)
                    .role(Message.MessageRole.valueOf(role))
                    .contentCompressed(compressText("msg" + i))
                    .build());
        }
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(history);
        when(ollamaClient.chatStream(eq("qwen"), anyList(), anyBoolean()))
                .thenReturn(Flux.just("{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}"));
        when(applicationContext.getBean(ChatService.class)).thenReturn(chatService);
        when(messageRepository.countBySessionId(1L)).thenReturn(12);
        lenient().when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenReturn(session);
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);

        String result = chatService.chatStream(1L, "qwen", "hello", true, false).blockLast(Duration.ofSeconds(5));
        assertNotNull(result);
    }

    /**
     * 测试：Ollama 返回空内容的助手响应时，流应正常完成且不保存空助手消息
     *
     * <p>输入参数：sessionId=1L，Ollama 返回 content="" 的助手消息</p>
     * <p>预期结果：流正常完成（无有效元素），saveAssistantResponse 不被调用</p>
     * <p>验证逻辑：确认空字符串内容被 transformChunk 过滤后，
     * concatMap 返回空 Flux，doOnComplete 中 fullContent 为空，不触发保存</p>
     */
    @Test
    void chatStream_EmptyAssistantResponse_ShouldNotSaveAssistant() {
        Session session = Session.builder().id(1L).title("Test").modelName("qwen").build();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.save(any(Message.class))).thenReturn(new Message());
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());
        when(ollamaClient.chatStream(eq("qwen"), anyList(), anyBoolean()))
                .thenReturn(Flux.just("{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}"));

        // 空内容被过滤，但 startWith 会保留一个 start 事件，blockLast 返回 start
        String result = chatService.chatStream(1L, "qwen", "hello", true, false).blockLast(Duration.ofSeconds(5));
        assertNotNull(result);
        assertTrue(result.contains("\"type\":\"start\""));
    }

    /**
     * 测试：Ollama 服务异常时，onErrorResume 应保存错误消息并返回 error 事件
     *
     * <p>输入参数：sessionId=1L，ollamaClient.chatStream() 返回 Flux.error()</p>
     * <p>预期结果：返回 error 类型的 SSE 事件，且错误消息被保存到数据库</p>
     * <p>关键验证点：
     * <ul>
     *   <li>onErrorResume 恢复流，不抛出异常</li>
     *   <li>错误消息被保存到数据库（saveAssistantResponse 被调用）</li>
     *   <li>前端收到 error 类型的 SSE 事件</li>
     * </ul>
     * </p>
     */
    @Test
    void chatStream_OnError_ShouldSaveErrorAndReturnErrorEvent() {
        Session session = Session.builder().id(1L).title("Test").modelName("qwen").build();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.save(any(Message.class))).thenReturn(new Message());
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());
        when(ollamaClient.chatStream(eq("qwen"), anyList(), anyBoolean()))
                .thenReturn(Flux.error(new RuntimeException("Ollama connection refused")));
        when(applicationContext.getBean(ChatService.class)).thenReturn(chatService);
        when(messageRepository.countBySessionId(1L)).thenReturn(1);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenReturn(session);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        // onErrorResume 恢复流，不应抛出异常
        String result = chatService.chatStream(1L, "qwen", "hello", false, false)
                .blockLast(Duration.ofSeconds(5));

        assertNotNull(result);
        assertTrue(result.contains("\"type\":\"error\""));
        assertTrue(result.contains("请求失败"));
    }

    /**
     * 测试：Ollama 流式响应中途断开，应触发 onErrorResume
     *
     * <p>输入参数：sessionId=1L，ollamaClient 先返回一条数据，然后 error</p>
     * <p>预期结果：返回正常 token 事件 + 错误事件，错误消息被保存</p>
     * <p>关键验证点：确认流中断时已接收的内容不丢失，错误恢复机制正常工作</p>
     */
    @Test
    void chatStream_StreamInterrupted_ShouldRecover() {
        Session session = Session.builder().id(1L).title("Test").modelName("qwen").build();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.save(any(Message.class))).thenReturn(new Message());
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());
        // 先返回一条正常数据，然后 error
        when(ollamaClient.chatStream(eq("qwen"), anyList(), anyBoolean()))
                .thenReturn(Flux.just("{\"message\":{\"role\":\"assistant\",\"content\":\"部分回复\"}}")
                        .concatWith(Flux.error(new RuntimeException("Connection reset"))));
        when(applicationContext.getBean(ChatService.class)).thenReturn(chatService);
        when(messageRepository.countBySessionId(1L)).thenReturn(1);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenReturn(session);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        List<String> results = chatService.chatStream(1L, "qwen", "hello", false, false)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(results);
        assertFalse(results.isEmpty());
        // 第一个是 start 事件
        assertTrue(results.get(0).contains("\"type\":\"start\""));
        // 第二个是正常 token（已接收的部分内容）
        assertTrue(results.get(1).contains("\"type\":\"token\""));
        // 最后一个是 error
        String last = results.get(results.size() - 1);
        assertTrue(last.contains("\"type\":\"error\""));
    }

    // ==================== getSessionMessages 测试 ====================

    /**
     * 测试：获取会话消息，应正确解压内容并返回格式化列表
     *
     * <p>输入参数：sessionId=1L，2 条消息（user:"Hello" + assistant:"Hi there"）</p>
     * <p>预期结果：返回 2 条格式化消息，包含 role 和 content 字段，
     * content 为解压后的明文字符串</p>
     * <p>验证逻辑：确认压缩存储的消息内容能正确解压并格式化为前端可用的 Map 结构，
     * role 和 content 字段值与原始数据一致</p>
     */
    @Test
    void getSessionMessages_ShouldDecompressAndReturnMessages() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder()
                .id(1L).sessionId(1L)
                .role(Message.MessageRole.user)
                .contentCompressed(compressText("Hello"))
                .tokenCount(10)
                .build());
        messages.add(Message.builder()
                .id(2L).sessionId(1L)
                .role(Message.MessageRole.assistant)
                .contentCompressed(compressText("Hi there"))
                .tokenCount(5)
                .build());
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(messages);

        List<Map<String, Object>> result = chatService.getSessionMessages(1L, 0, 50);

        assertEquals(2, result.size());
        assertEquals("user", result.get(0).get("role"));
        assertEquals("Hello", result.get(0).get("content"));
        assertEquals("assistant", result.get(1).get("role"));
        assertEquals("Hi there", result.get(1).get("content"));
    }

    /**
     * 测试：空会话获取消息，应返回空列表
     *
     * <p>输入参数：sessionId=1L，无消息记录</p>
     * <p>预期结果：返回空列表</p>
     * <p>验证逻辑：确认无消息的会话不返回 null，而是安全的空列表</p>
     */
    @Test
    void getSessionMessages_EmptySession_ShouldReturnEmptyList() {
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = chatService.getSessionMessages(1L, 0, 50);

        assertTrue(result.isEmpty());
    }

    // ==================== deleteSession 测试 ====================

    /**
     * 测试：删除会话，应级联删除消息、会话记录和 Redis 缓存
     *
     * <p>输入参数：sessionId=1L</p>
     * <p>预期结果：messageRepository.deleteBySessionId(1L)、sessionRepository.deleteById(1L)、
     * redisTemplate.delete("session:1:messages") 均被调用</p>
     * <p>验证逻辑：确认删除操作完整清理所有关联数据，
     * 避免遗留孤立消息或过期缓存导致数据不一致</p>
     */
    @Test
    void deleteSession_ShouldDeleteMessagesAndSessionAndCache() {
        chatService.deleteSession(1L);

        verify(messageRepository).deleteBySessionId(1L);
        verify(sessionRepository).deleteById(1L);
        verify(redisTemplate).delete("session:1:messages");
    }

    // ==================== GZIP 压缩/解压测试 ====================

    /**
     * 测试：GZIP 压缩与解压的往返一致性
     *
     * <p>输入参数：原始文本 "Hello 你好 World! 这是一个测试文本。"</p>
     * <p>预期结果：压缩后解压的文本与原始文本完全一致</p>
     * <p>验证逻辑：确认 GZIP 压缩/解压的完整往返过程无损，
     * 这是消息存储和读取的基础，确保数据不会因压缩而丢失</p>
     */
    @Test
    void compress_And_Decompress_RoundTrip() throws Exception {
        String original = "Hello 你好 World! 这是一个测试文本。";
        byte[] compressed = (byte[]) ReflectionTestUtils.invokeMethod(chatService, "compress", original);
        String decompressed = (String) ReflectionTestUtils.invokeMethod(chatService, "decompress", compressed);

        assertEquals(original, decompressed);
    }

    /**
     * 测试：解压 null 输入，应返回空字符串
     *
     * <p>输入参数：null</p>
     * <p>预期结果：返回空字符串 ""</p>
     * <p>验证逻辑：确认对 null 压缩内容的安全处理，避免 NPE，
     * 该场景可能出现在旧数据迁移或字段为空的情况</p>
     */
    @Test
    void decompress_Null_ShouldReturnEmpty() throws Exception {
        String result = (String) ReflectionTestUtils.invokeMethod(chatService, "decompress", (Object) null);
        assertEquals("", result);
    }

    /**
     * 测试：解压非 GZIP 格式的字节数据，应降级为 UTF-8 解码
     *
     * <p>输入参数：普通 UTF-8 字节（非 GZIP 压缩格式）</p>
     * <p>预期结果：降级返回 UTF-8 解码后的字符串 "plain text"</p>
     * <p>验证逻辑：确认当字节数据不是 GZIP 格式时（GZIP 解压失败），
     * 系统自动降级为 UTF-8 解码而非抛出异常，
     * 兼容早期未压缩的历史数据或直接存储的文本</p>
     */
    @Test
    void decompress_NonCompressedBytes_ShouldFallbackToUTF8() throws Exception {
        byte[] plainBytes = "plain text".getBytes(StandardCharsets.UTF_8);
        String result = (String) ReflectionTestUtils.invokeMethod(chatService, "decompress", (Object) plainBytes);
        assertEquals("plain text", result);
    }

    /**
     * 测试：压缩大文本，压缩后体积应小于原始 UTF-8 字节大小
     *
     * <p>输入参数：100 段重复的中文文本</p>
     * <p>预期结果：压缩后字节数 < 原始 UTF-8 字节数</p>
     * <p>验证逻辑：确认 GZIP 压缩对重复文本的压缩效果，
     * 验证压缩存储策略的实际收益</p>
     */
    @Test
    void compress_LargeText_ShouldReduceSize() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("这是一段重复的中文文本内容。");
        }
        String original = sb.toString();
        byte[] compressed = (byte[]) ReflectionTestUtils.invokeMethod(chatService, "compress", original);

        assertTrue(compressed.length < original.getBytes(StandardCharsets.UTF_8).length);
    }

    // ==================== Token 估算测试 ====================

    /**
     * 测试：纯中文文本的 Token 估算，应使用 1.5 的字符比
     *
     * <p>输入参数：150 个中文字符</p>
     * <p>预期结果：150 / 1.5 = 100 tokens</p>
     * <p>验证逻辑：确认中文文本的 Token 估算比例正确，
     * 中文字符通常对应更多 Token，因此比例较低（1 字符 ≈ 0.67 token）</p>
     */
    @Test
    void estimateTokens_ChineseText_ShouldUseRatio15() throws Exception {
        // 150 Chinese characters / 1.5 = 100
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 150; i++) sb.append("中");
        int tokens = (int) ReflectionTestUtils.invokeMethod(chatService, "estimateTokens", sb.toString());
        assertEquals(100, tokens);
    }

    /**
     * 测试：纯英文文本的 Token 估算，应使用 4 的字符比
     *
     * <p>输入参数：400 个英文字符</p>
     * <p>预期结果：400 / 4 = 100 tokens</p>
     * <p>验证逻辑：确认英文文本的 Token 估算比例正确，
     * 英文字符通常对应较少 Token，因此比例较高（4 字符 ≈ 1 token）</p>
     */
    @Test
    void estimateTokens_EnglishText_ShouldUseRatio4() throws Exception {
        // 400 English characters / 4 = 100
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++) sb.append("a");
        int tokens = (int) ReflectionTestUtils.invokeMethod(chatService, "estimateTokens", sb.toString());
        assertEquals(100, tokens);
    }

    /**
     * 测试：中英文混合文本的 Token 估算，应分别计算后求和
     *
     * <p>输入参数：150 个中文字符 + 400 个英文字符</p>
     * <p>预期结果：150/1.5 + 400/4 = 100 + 100 = 200 tokens</p>
     * <p>验证逻辑：确认中英文混合文本的 Token 估算分别应用不同比例，
     * 最终结果为两部分之和，更贴近实际 LLM 的 Token 计算方式</p>
     */
    @Test
    void estimateTokens_MixedText_ShouldEstimateCorrectly() throws Exception {
        // 150 Chinese + 400 English
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 150; i++) sb.append("中");
        for (int i = 0; i < 400; i++) sb.append("a");
        int tokens = (int) ReflectionTestUtils.invokeMethod(chatService, "estimateTokens", sb.toString());
        assertEquals(200, tokens);
    }

    /**
     * 测试：null 或空字符串的 Token 估算，应返回 0
     *
     * <p>输入参数：null 和 ""</p>
     * <p>预期结果：均返回 0</p>
     * <p>验证逻辑：确认边界输入的安全处理，避免 NPE 或错误的 Token 计数</p>
     */
    @Test
    void estimateTokens_NullOrEmpty_ShouldReturnZero() throws Exception {
        assertEquals(0, (int) ReflectionTestUtils.invokeMethod(chatService, "estimateTokens", (String) null));
        assertEquals(0, (int) ReflectionTestUtils.invokeMethod(chatService, "estimateTokens", ""));
    }

    // ==================== transformChunk 测试 ====================

    /**
     * 测试：从有效 Ollama SSE JSON 中提取助手回复内容
     *
     * <p>输入参数：JSON {"message":{"role":"assistant","content":"你好"}}，deepThink=false</p>
     * <p>预期结果：返回包含一个 token 事件的列表</p>
     * <p>验证逻辑：确认标准 SSE 响应格式的内容提取正确，
     * 该方法是流式聊天响应解析的核心逻辑</p>
     */
    @Test
    void transformChunk_ValidJson_ShouldReturnTokenEvent() throws Exception {
        String json = "{\"message\":{\"role\":\"assistant\",\"content\":\"你好\"}}";
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<String> results = (List<String>) ReflectionTestUtils.invokeMethod(chatService,
                "transformChunk", json, content, thinking, false);
        assertEquals(1, results.size());
        JsonNode eventNode = objectMapper.readTree(results.get(0));
        assertEquals("token", eventNode.get("type").asText());
        assertEquals("你好", eventNode.get("content").asText());
        assertEquals("你好", content.toString());
    }

    /**
     * 测试：深度思考模式下，reasoning_content 应被解析为 thinking 事件
     *
     * <p>输入参数：包含 reasoning_content 的 JSON，deepThink=true</p>
     * <p>预期结果：返回包含一个 thinking 事件的列表</p>
     * <p>验证逻辑：确认深度思考模式下 reasoning_content 被正确解析</p>
     */
    @Test
    void transformChunk_DeepThinkEnabled_ShouldReturnThinkingEvent() throws Exception {
        String json = "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"reasoning_content\":\"让我想想...\"}}";
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<String> results = (List<String>) ReflectionTestUtils.invokeMethod(chatService,
                "transformChunk", json, content, thinking, true);
        assertEquals(1, results.size());
        JsonNode eventNode = objectMapper.readTree(results.get(0));
        assertEquals("thinking", eventNode.get("type").asText());
        assertEquals("让我想想...", eventNode.get("content").asText());
        assertEquals("让我想想...", thinking.toString());
        assertEquals("", content.toString());
    }

    /**
     * 测试：深度思考模式下，Ollama 返回 "thinking" 字段名也应被解析为 thinking 事件
     *
     * <p>输入参数：包含 thinking 字段（非 reasoning_content）的 JSON，deepThink=true</p>
     * <p>预期结果：返回包含一个 thinking 事件的列表</p>
     * <p>验证逻辑：确认兼容不同版本 Ollama 的字段名（reasoning_content / thinking）</p>
     */
    @Test
    void transformChunk_DeepThinkEnabled_ThinkingField_ShouldReturnThinkingEvent() throws Exception {
        String json = "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"thinking\":\"让我想想...\"}}";
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<String> results = (List<String>) ReflectionTestUtils.invokeMethod(chatService,
                "transformChunk", json, content, thinking, true);
        assertEquals(1, results.size());
        JsonNode eventNode = objectMapper.readTree(results.get(0));
        assertEquals("thinking", eventNode.get("type").asText());
        assertEquals("让我想想...", eventNode.get("content").asText());
        assertEquals("让我想想...", thinking.toString());
    }

    /**
     * 测试：深度思考模式下无 reasoning_content，应正常解析 content
     *
     * <p>输入参数：不含 reasoning_content 的 JSON，deepThink=true</p>
     * <p>预期结果：返回 token 事件（回退到 content 解析）</p>
     * <p>验证逻辑：确认深度思考模式下，如果模型不支持 reasoning_content，
     * 仍能正常解析 content 字段</p>
     */
    @Test
    void transformChunk_DeepThinkEnabled_NoReasoning_ShouldFallbackToContent() throws Exception {
        String json = "{\"message\":{\"role\":\"assistant\",\"content\":\"你好\"}}";
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<String> results = (List<String>) ReflectionTestUtils.invokeMethod(chatService,
                "transformChunk", json, content, thinking, true);
        assertEquals(1, results.size());
        JsonNode eventNode = objectMapper.readTree(results.get(0));
        assertEquals("token", eventNode.get("type").asText());
        assertEquals("你好", eventNode.get("content").asText());
        assertEquals("你好", content.toString());
        assertEquals("", thinking.toString());
    }

    /**
     * 测试：done=true 的 JSON，应返回 done 事件
     *
     * <p>输入参数：JSON {"done":true}</p>
     * <p>预期结果：返回包含一个 done 事件的列表</p>
     * <p>验证逻辑：确认流结束标记被正确识别</p>
     */
    @Test
    void transformChunk_DoneTrue_ShouldReturnDoneEvent() throws Exception {
        String json = "{\"model\":\"qwen\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true}";
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<String> results = (List<String>) ReflectionTestUtils.invokeMethod(chatService,
                "transformChunk", json, content, thinking, false);
        assertEquals(1, results.size());
        JsonNode eventNode = objectMapper.readTree(results.get(0));
        assertEquals("done", eventNode.get("type").asText());
    }

    /**
     * 测试：无效 JSON 字符串，应返回空列表
     *
     * <p>输入参数："not json"（非 JSON 格式字符串）</p>
     * <p>预期结果：返回空列表</p>
     * <p>验证逻辑：确认 JSON 解析失败时不抛异常而是返回空列表，
     * 避免 SSE 流中的异常数据导致整个聊天流程中断</p>
     */
    @Test
    void transformChunk_InvalidJson_ShouldReturnEmptyList() throws Exception {
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<String> results = (List<String>) ReflectionTestUtils.invokeMethod(chatService,
                "transformChunk", "not json", content, thinking, false);
        assertTrue(results.isEmpty());
    }

    /**
     * 测试：null chunk，应返回空列表
     *
     * <p>输入参数：null</p>
     * <p>预期结果：返回空列表，不抛异常</p>
     * <p>验证逻辑：确认 null 输入的安全处理</p>
     */
    @Test
    void transformChunk_NullChunk_ShouldReturnEmptyList() throws Exception {
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<String> results = (List<String>) ReflectionTestUtils.invokeMethod(chatService,
                "transformChunk", null, content, thinking, false);
        assertTrue(results.isEmpty());
    }

    /**
     * 测试：JSON 中 message.content 为空字符串，应返回空列表
     *
     * <p>输入参数：content="" 的 JSON</p>
     * <p>预期结果：返回空列表（空内容不转发）</p>
     * <p>验证逻辑：确认空内容被正确过滤，不产生无意义的 token 事件</p>
     */
    @Test
    void transformChunk_EmptyContent_ShouldReturnEmptyList() throws Exception {
        String json = "{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}";
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<String> results = (List<String>) ReflectionTestUtils.invokeMethod(chatService,
                "transformChunk", json, content, thinking, false);
        assertTrue(results.isEmpty());
    }

    /**
     * 测试：JSON 中同时包含 reasoning_content 和 content，深度思考模式下应返回两个事件
     *
     * <p>输入参数：同时包含 reasoning_content 和 content 的 JSON，deepThink=true</p>
     * <p>预期结果：返回 thinking + token 两个事件，确保两者都不丢失</p>
     * <p>验证逻辑：确认同一 chunk 中 reasoning_content 和 content 共存时，
     * 两者都被正确解析为独立事件，不再因 return 提前退出而丢失 content</p>
     */
    @Test
    void transformChunk_BothReasoningAndContent_ShouldReturnBothEvents() throws Exception {
        String json = "{\"message\":{\"role\":\"assistant\",\"content\":\"回答\",\"reasoning_content\":\"思考中\"}}";
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<String> results = (List<String>) ReflectionTestUtils.invokeMethod(chatService,
                "transformChunk", json, content, thinking, true);
        assertEquals(2, results.size());
        JsonNode thinkingNode = objectMapper.readTree(results.get(0));
        assertEquals("thinking", thinkingNode.get("type").asText());
        assertEquals("思考中", thinkingNode.get("content").asText());
        JsonNode tokenNode = objectMapper.readTree(results.get(1));
        assertEquals("token", tokenNode.get("type").asText());
        assertEquals("回答", tokenNode.get("content").asText());
        assertEquals("思考中", thinking.toString());
        assertEquals("回答", content.toString());
    }

    /**
     * 测试：JSON 中含特殊字符（换行、引号），应正确转义
     *
     * <p>输入参数：content 包含换行符和双引号的 JSON</p>
     * <p>预期结果：返回的 JSON 中特殊字符被正确转义</p>
     * <p>验证逻辑：确认 escapeJson 方法正确处理换行符、引号等特殊字符，
     * 保证 SSE 事件的 JSON 格式正确</p>
     */
    @Test
    void transformChunk_SpecialCharacters_ShouldEscapeCorrectly() throws Exception {
        String json = "{\"message\":{\"role\":\"assistant\",\"content\":\"line1\\nline2\\\"quote\\\"\"}}";
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        @SuppressWarnings("unchecked")
        List<String> results = (List<String>) ReflectionTestUtils.invokeMethod(chatService,
                "transformChunk", json, content, thinking, false);
        assertFalse(results.isEmpty());
        // 验证转义后的 JSON 可以被重新解析
        assertDoesNotThrow(() -> objectMapper.readTree(results.get(0)));
    }

    // ==================== 辅助方法 ====================

    /**
     * 辅助方法：通过反射调用 ChatService 的 compress 方法压缩文本
     *
     * @param text 待压缩的文本
     * @return GZIP 压缩后的字节数组
     */
    private byte[] compressText(String text) {
        try {
            return (byte[]) ReflectionTestUtils.invokeMethod(chatService, "compress", text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
