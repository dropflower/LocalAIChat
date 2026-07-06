package com.aiapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;

/**
 * Ollama API 客户端服务
 *
 * ## 功能描述
 * 封装与本地 Ollama 服务的 HTTP 通信，提供模型列表查询和流式对话功能。
 * 使用 Spring WebFlux 的 WebClient 实现非阻塞 HTTP 调用。
 *
 * ## 为什么使用 WebClient 而非 RestTemplate？
 * 1. RestTemplate 在 Spring 5+ 已进入维护模式，WebClient 是官方推荐的替代方案
 * 2. WebClient 支持响应式编程（Reactive Streams），天然适合流式 SSE 场景
 * 3. 非阻塞 I/O 不会占用 Tomcat 线程池，单次对话（最长 2 分钟）不阻塞其他请求
 *
 * ## 核心方法
 * - listModels()：调用 Ollama GET /api/tags 获取已安装模型列表
 * - chatStream()：调用 Ollama POST /api/chat 流式对话，返回 Flux<String>
 * - isAvailable()：健康检查，判断 Ollama 服务是否可用
 *
 * ## 错误处理
 * - listModels() 失败时返回空列表，不抛出异常，确保前端能正常显示
 * - chatStream() 的超时由 application.yml 中的 ollama.chat-timeout 控制
 * - 流式调用中的错误通过 Flux.doOnError() 记录日志
 *
 * ## 依赖关系
 * - 依赖 application.yml 中的 ollama.base-url 和 ollama.chat-timeout
 * - 被 ModelService 和 ChatService 调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaClientService {

    private final ObjectMapper objectMapper;

    @Value("${ollama.base-url}")
    private String baseUrl;

    @Value("${ollama.chat-timeout}")
    private long chatTimeout;

    private WebClient webClient;

    /**
     * 初始化 WebClient，设置 Ollama API 的基础 URL
     * 使用 @PostConstruct 确保依赖注入完成后再初始化
     */
    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * 获取本地 Ollama 已安装的模型列表
     *
     * 调用 Ollama GET /api/tags 接口，解析返回的 JSON：
     * {
     *   "models": [
     *     { "name": "qwen2.5:7b", "size": 4431088896, "modified_at": "..." },
     *     ...
     *   ]
     * }
     *
     * @return 模型信息列表，每个元素包含 name/size/modified_at 字段
     *         如果 Ollama 不可用，返回空列表（不抛异常）
     */
    public List<Map<String, Object>> listModels() {
        try {
            JsonNode response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));

            List<Map<String, Object>> models = new ArrayList<>();
            if (response != null && response.has("models")) {
                for (JsonNode model : response.get("models")) {
                    Map<String, Object> modelInfo = new HashMap<>();
                    modelInfo.put("name", model.get("name").asText());
                    modelInfo.put("size", model.has("size") ? model.get("size").asLong() : 0);
                    modelInfo.put("modified_at", model.has("modified_at") ? model.get("modified_at").asText() : "");
                    models.add(modelInfo);
                }
            }
            return models;
        } catch (Exception e) {
            log.error("获取 Ollama 模型列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 流式对话 — 调用 Ollama POST /api/chat
     *
     * 请求体格式：
     * {
     *   "model": "qwen2.5:7b",
     *   "messages": [{"role": "user", "content": "你好"}],
     *   "stream": true,
     *   "think": true   // 仅 deepThink=true 时传递
     * }
     *
     * 响应为 Server-Sent Events 格式的 JSON 流，每行一条 JSON：
     * {"message": {"role": "assistant", "content": "你"}, "done": false}
     * {"message": {"role": "assistant", "reasoning_content": "思考...", "content": "你"}, "done": false}
     * ...
     * {"message": {"role": "assistant", "content": ""}, "done": true}
     *
     * @param modelName 模型名称
     * @param messages  对话历史（包含当前用户消息）
     * @param deepThink 是否启用深度思考模式（传递 "think": true 给 Ollama）
     * @return Flux<String> 流式响应，每个元素是一行 JSON 字符串
     */
    public Flux<String> chatStream(String modelName, List<Map<String, String>> messages, boolean deepThink) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", messages);
        requestBody.put("stream", true);
        if (deepThink) {
            requestBody.put("think", true);
        }

        return webClient.post()
                .uri("/api/chat")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMillis(chatTimeout))
                .doOnError(e -> log.error("Ollama 流式对话错误: {}", e.getMessage()));
    }

    /**
     * 检查 Ollama 服务是否可用
     *
     * 通过调用 GET /api/tags 并设置 5 秒超时来判断服务状态
     * 不解析返回内容，仅关注连接是否成功
     *
     * @return true 可用，false 不可用
     */
    public boolean isAvailable() {
        try {
            webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}