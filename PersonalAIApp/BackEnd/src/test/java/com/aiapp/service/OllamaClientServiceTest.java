package com.aiapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OllamaClientService 单元测试类
 *
 * <p>功能描述：测试 Ollama 客户端服务（OllamaClientService）与 Ollama API 的交互逻辑，
 * 包括模型列表解析、服务不可用时的降级处理和可用性检测。</p>
 *
 * <p>测试策略：使用 OkHttp MockWebServer 模拟 Ollama HTTP 服务端，
 * 通过 ReflectionTestUtils 注入 baseUrl 和 chatTimeout 配置，
 * 每个测试前后启动和关闭 MockWebServer，实现真实 HTTP 交互的隔离测试。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>listModels：正确解析 Ollama /api/tags 响应的模型列表</li>
 *   <li>listModels：Ollama 服务不可用时返回空列表而非抛异常</li>
 *   <li>isAvailable：Ollama 可用时返回 true</li>
 *   <li>isAvailable：Ollama 不可用时返回 false</li>
 * </ul>
 * </p>
 */
class OllamaClientServiceTest {

    /** MockWebServer：模拟 Ollama HTTP 服务端 */
    private MockWebServer mockWebServer;

    /** 待测试的 OllamaClientService 实例 */
    private OllamaClientService ollamaClientService;

    /** JSON 序列化工具 */
    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 测试前初始化：启动 MockWebServer，创建 OllamaClientService 实例，
     * 通过反射注入 baseUrl 和 chatTimeout 配置，调用 init() 初始化 HTTP 客户端
     */
    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        ollamaClientService = new OllamaClientService(objectMapper);
        ReflectionTestUtils.setField(ollamaClientService, "baseUrl",
                "http://localhost:" + mockWebServer.getPort());
        ReflectionTestUtils.setField(ollamaClientService, "chatTimeout", 5000L);
        ollamaClientService.init();
    }

    /**
     * 测试后清理：关闭 MockWebServer 释放端口资源
     */
    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    /**
     * 测试：Ollama 返回有效模型列表时，应正确解析模型名称和大小
     *
     * <p>输入参数：MockWebServer 返回包含 2 个模型的 JSON 响应
     *（qwen2.5:7b/4.4GB 和 llama3:8b/4.7GB）</p>
     * <p>预期结果：返回 2 个模型，第一个 name="qwen2.5:7b"，size=4431088896，
     * modified_at="2024-01-01T00:00:00Z"</p>
     * <p>验证逻辑：确认 Ollama API 响应的 JSON 格式能被正确解析，
     * 模型名称、大小和修改时间等关键字段完整提取</p>
     */
    @Test
    void listModels_ValidResponse_ShouldParseModels() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"models\":[" +
                        "{\"name\":\"qwen2.5:7b\",\"size\":4431088896,\"modified_at\":\"2024-01-01T00:00:00Z\"}," +
                        "{\"name\":\"llama3:8b\",\"size\":4666167296,\"modified_at\":\"2024-01-02T00:00:00Z\"}" +
                        "]}")
                .setHeader("Content-Type", "application/json"));

        List<Map<String, Object>> models = ollamaClientService.listModels();

        assertEquals(2, models.size());
        assertEquals("qwen2.5:7b", models.get(0).get("name"));
        assertEquals(4431088896L, models.get(0).get("size"));
        assertEquals("2024-01-01T00:00:00Z", models.get(0).get("modified_at"));
    }

    /**
     * 测试：Ollama 服务不可用时，listModels 应返回空列表
     *
     * <p>输入参数：MockWebServer 未设置响应（模拟服务不可达）</p>
     * <p>预期结果：返回空列表，不抛出异常</p>
     * <p>验证逻辑：确认 Ollama 服务离线时的降级策略——返回空列表而非抛异常，
     * 保证上层服务可继续运行（模型选择功能不可用但系统不崩溃）</p>
     */
    @Test
    void listModels_WhenOllamaUnavailable_ShouldReturnEmptyList() {
        List<Map<String, Object>> models = ollamaClientService.listModels();

        assertTrue(models.isEmpty());
    }

    /**
     * 测试：Ollama 服务不可用时，isAvailable 应返回 false
     *
     * <p>输入参数：MockWebServer 未设置响应（模拟服务不可达）</p>
     * <p>预期结果：返回 false</p>
     * <p>验证逻辑：确认可用性检测在服务不可达时正确返回 false，
     * 前端可据此隐藏模型选择功能或展示离线提示</p>
     */
    @Test
    void isAvailable_WhenOllamaUnavailable_ShouldReturnFalse() {
        assertFalse(ollamaClientService.isAvailable());
    }

    /**
     * 测试：Ollama 服务可用时，isAvailable 应返回 true
     *
     * <p>输入参数：MockWebServer 返回空模型列表的有效 JSON 响应</p>
     * <p>预期结果：返回 true</p>
     * <p>验证逻辑：确认 Ollama 服务正常响应时可用性检测返回 true，
     * 即使没有可用模型，只要服务可达即视为可用</p>
     */
    @Test
    void isAvailable_WhenOllamaAvailable_ShouldReturnTrue() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"models\":[]}")
                .setHeader("Content-Type", "application/json"));

        assertTrue(ollamaClientService.isAvailable());
    }

    // ==================== chatStream 测试 ====================

    /**
     * 测试：正常流式对话，应正确接收 Ollama 返回的 NDJSON 行
     *
     * <p>输入参数：modelName="qwen2.5:7b"，单条用户消息</p>
     * <p>预期结果：返回 3 个元素（2 条内容 + 1 条完成标记），
     * 每条内容为完整的 JSON 行字符串</p>
     * <p>关键验证点：确认 WebClient 能正确逐行解析 Ollama 的 NDJSON 流式响应，
     * 不出现粘包或拆包问题</p>
     */
    @Test
    void chatStream_ValidResponse_ShouldReturnLines() {
        // 模拟 Ollama 的 NDJSON 流式响应（每行一条 JSON）
        String ndjsonResponse = "{\"model\":\"qwen2.5:7b\",\"message\":{\"role\":\"assistant\",\"content\":\"你好\"},\"done\":false}\n"
                + "{\"model\":\"qwen2.5:7b\",\"message\":{\"role\":\"assistant\",\"content\":\"世界\"},\"done\":false}\n"
                + "{\"model\":\"qwen2.5:7b\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true}\n";

        mockWebServer.enqueue(new MockResponse()
                .setBody(ndjsonResponse)
                .setHeader("Content-Type", "application/x-ndjson"));

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "hello")
        );

        StepVerifier.create(ollamaClientService.chatStream("qwen2.5:7b", messages, false))
                .expectNextMatches(line -> line.contains("\"content\":\"你好\""))
                .expectNextMatches(line -> line.contains("\"content\":\"世界\""))
                .expectNextMatches(line -> line.contains("\"done\":true"))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    /**
     * 测试：深度思考模型流式对话，应正确接收 reasoning_content 字段
     *
     * <p>输入参数：modelName="deepseek-r1:7b"，单条用户消息</p>
     * <p>预期结果：返回包含 reasoning_content 的 JSON 行</p>
     * <p>关键验证点：确认支持深度思考的模型（如 DeepSeek-R1）返回的 reasoning_content
     * 字段能被正确接收，不会因字段不存在而解析失败</p>
     */
    @Test
    void chatStream_DeepThinkModel_ShouldReturnReasoningContent() {
        String ndjsonResponse = "{\"model\":\"deepseek-r1:7b\",\"message\":{\"role\":\"assistant\",\"content\":\"\",\"reasoning_content\":\"让我思考一下...\"},\"done\":false}\n"
                + "{\"model\":\"deepseek-r1:7b\",\"message\":{\"role\":\"assistant\",\"content\":\"答案是42\"},\"done\":false}\n"
                + "{\"model\":\"deepseek-r1:7b\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true}\n";

        mockWebServer.enqueue(new MockResponse()
                .setBody(ndjsonResponse)
                .setHeader("Content-Type", "application/x-ndjson"));

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "生命的意义是什么")
        );

        StepVerifier.create(ollamaClientService.chatStream("deepseek-r1:7b", messages, true))
                .expectNextMatches(line -> line.contains("reasoning_content"))
                .expectNextMatches(line -> line.contains("答案是42"))
                .expectNextMatches(line -> line.contains("\"done\":true"))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    /**
     * 测试：空消息列表，Ollama 仍应返回响应
     *
     * <p>输入参数：空消息列表</p>
     * <p>预期结果：返回正常流式响应</p>
     * <p>关键验证点：确认空消息列表（边界情况）不会导致 WebClient 请求构建失败</p>
     */
    @Test
    void chatStream_EmptyMessages_ShouldStillWork() {
        String ndjsonResponse = "{\"model\":\"qwen2.5:7b\",\"message\":{\"role\":\"assistant\",\"content\":\"请提供更多信息\"},\"done\":false}\n"
                + "{\"model\":\"qwen2.5:7b\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true}\n";

        mockWebServer.enqueue(new MockResponse()
                .setBody(ndjsonResponse)
                .setHeader("Content-Type", "application/x-ndjson"));

        StepVerifier.create(ollamaClientService.chatStream("qwen2.5:7b", List.of(), false))
                .expectNextCount(2)
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    /**
     * 测试：Ollama 返回 HTTP 500 错误，应传播错误信号
     *
     * <p>输入参数：正常请求参数</p>
     * <p>预期结果：Flux 发出 error 信号</p>
     * <p>关键验证点：确认 Ollama 服务端错误时，错误信号正确传播到调用方，
     * 由 ChatService.onErrorResume 统一处理</p>
     */
    @Test
    void chatStream_OllamaServerError_ShouldPropagateError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\":\"Internal Server Error\"}"));

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "hello")
        );

        StepVerifier.create(ollamaClientService.chatStream("qwen2.5:7b", messages, false))
                .expectError()
                .verify(Duration.ofSeconds(5));
    }

    /**
     * 测试：Ollama 响应超时，应触发 timeout 错误
     *
     * <p>输入参数：正常请求参数，chatTimeout=100ms</p>
     * <p>预期结果：Flux 发出 timeout 错误信号</p>
     * <p>关键验证点：确认超时机制生效，防止长时间阻塞请求线程</p>
     */
    @Test
    void chatStream_Timeout_ShouldError() {
        ReflectionTestUtils.setField(ollamaClientService, "chatTimeout", 100L);

        // 模拟慢响应（不立即返回）
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"message\":{\"content\":\"slow\"}}")
                .setBodyDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS));

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "hello")
        );

        StepVerifier.create(ollamaClientService.chatStream("qwen2.5:7b", messages, false))
                .expectError()
                .verify(Duration.ofSeconds(5));
    }
}
