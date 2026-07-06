package com.aiapp.service;

import com.aiapp.model.Message;
import com.aiapp.model.Session;
import com.aiapp.repository.MessageRepository;
import com.aiapp.repository.SessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 对话服务 — 核心业务逻辑
 *
 * ## 功能描述
 * 处理对话的完整生命周期：创建会话 → 构建上下文 → 调用 Ollama → 保存消息 → 更新缓存。
 * 是连接 Ollama、数据库、Redis 缓存的核心编排层。
 *
 * ## 新增功能
 * - 深度思考（deepThink）：解析 Ollama 返回的 reasoning_content，流式转发给前端
 * - 联网搜索（enableSearch）：在对话前搜索网页，将结果注入上下文
 *
 * ## 核心流程
 * 1. chatStream()：接收用户消息，编排完整对话流程
 *    a. 获取或创建会话
 *    b. 如果启用联网搜索，执行搜索并注入上下文
 *    c. 保存用户消息到数据库
 *    d. 构建对话上下文（最近 N 轮 + 当前消息）
 *    e. 调用 Ollama 流式对话
 *    f. 解析 reasoning_content（思考）和 content（回复），分别转发
 *    g. 流结束后保存 AI 回复到数据库
 *    h. 删除 Redis 缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final OllamaClientService ollamaClient;
    private final WebSearchService webSearchService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    @Value("${app.chat.max-context-rounds}")
    private int maxContextRounds;

    @Value("${app.chat.max-session-messages}")
    private int maxSessionMessages;

    private static final String CACHE_KEY_PREFIX = "session:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /**
     * 流式对话 — 核心入口方法
     *
     * @param sessionId    会话 ID（null 时创建新会话）
     * @param modelName    模型名称
     * @param userMessage  用户消息
     * @param deepThink    是否启用深度思考模式
     * @param enableSearch 是否启用联网搜索
     * @return Flux<String> SSE 流式响应（包含 thinking 和 token 类型事件）
     */
    public Flux<String> chatStream(Long sessionId, String modelName, String userMessage,
                                    boolean deepThink, boolean enableSearch) {
        // 1. 获取或创建会话
        Session session = getOrCreateSession(sessionId, modelName);

        // 2. 如果启用联网搜索，先搜索并将结果注入消息
        String finalMessage = userMessage;
        List<String> prefixEvents = new ArrayList<>();
        prefixEvents.add(buildEvent("start", null));

        if (enableSearch) {
            List<WebSearchService.SearchResultItem> searchResults = webSearchService.searchStructured(userMessage);
            if (!searchResults.isEmpty()) {
                prefixEvents.add(buildSearchEvent(searchResults));
                String formattedResults = webSearchService.formatResults(searchResults);
                finalMessage = "【系统提示】以下是联网搜索结果，请参考：\n\n"
                        + formattedResults + "\n\n"
                        + "【用户问题】" + userMessage;
            }
        }

        // 3. 保存用户消息（压缩后存储）
        saveMessage(session.getId(), Message.MessageRole.user, userMessage, 0);

        // 4. 构建对话上下文
        List<Map<String, String>> context = buildContext(session.getId());

        // 如果启用搜索，修改最后一条消息为搜索增强版本
        if (enableSearch && !context.isEmpty()) {
            Map<String, String> last = context.get(context.size() - 1);
            if ("user".equals(last.get("role"))) {
                last.put("content", finalMessage);
            }
        }

        // 5. 调用 Ollama 流式对话，解析 thinking 和 content
        final Long sid = session.getId();
        StringBuilder assistantContent = new StringBuilder();
        StringBuilder thinkingContent = new StringBuilder();

        return ollamaClient.chatStream(modelName, context, deepThink)
                .concatMap(chunk -> {
                    List<String> events = transformChunk(chunk, assistantContent, thinkingContent, deepThink);
                    return events.isEmpty() ? Mono.empty() : Flux.fromIterable(events);
                })
                .startWith(prefixEvents)
                .doOnComplete(() -> {
                    try {
                        String fullContent = assistantContent.toString();
                        if (!fullContent.isEmpty()) {
                            if (deepThink && thinkingContent.length() > 0) {
                                fullContent = "【思考过程】\n" + thinkingContent + "\n\n【回答】\n" + fullContent;
                            }
                            applicationContext.getBean(ChatService.class)
                                    .saveAssistantResponse(sid, fullContent);
                        }
                    } catch (Exception e) {
                        log.error("保存 AI 回复失败", e);
                    }
                })
                .onErrorResume(e -> {
                    log.error("对话流式响应错误: {}", e.getMessage());
                    String errorMsg = "\n\n[请求失败，请检查 Ollama 服务是否运行]";
                    String fullContent = assistantContent + errorMsg;
                    try {
                        applicationContext.getBean(ChatService.class)
                                .saveAssistantResponse(sid, fullContent);
                    } catch (Exception ex) {
                        log.error("保存错误消息失败: {}", ex.getMessage());
                    }
                    return Flux.just(buildEvent("error", errorMsg));
                });
    }

    /**
     * 转换 Ollama 原始 JSON 为结构化 SSE 事件
     *
     * 解析 Ollama 返回的 JSON，提取 reasoning_content/thinking（思考过程）和 content（回复内容），
     * 转换为前端的结构化 SSE 格式：
     * - {"type":"thinking","content":"思考中..."} → 思考过程（深度思考模式启用时）
     * - {"type":"token","content":"回复"} → 正常回复
     * - {"type":"done"} → 完成
     *
     * 注意：同一个 chunk 可能同时包含 reasoning_content 和 content（如 DeepSeek-R1 等模型），
     * 此时返回两个事件，确保思考内容和回复内容都不会丢失。
     *
     * 兼容性：不同版本的 Ollama 可能使用 "reasoning_content" 或 "thinking" 字段名，
     * 两者均会检测。
     *
     * @param chunk            Ollama 原始 JSON 行
     * @param assistantContent 累计完整回复的 StringBuilder
     * @param thinkingContent  累计思考过程的 StringBuilder
     * @param deepThink        是否启用深度思考
     * @return 结构化的 SSE 事件 JSON 列表，如果无需转发返回空列表
     */
    private List<String> transformChunk(String chunk, StringBuilder assistantContent,
                                        StringBuilder thinkingContent, boolean deepThink) {
        List<String> events = new ArrayList<>();
        if (chunk == null) {
            return events;
        }
        try {
            JsonNode node = objectMapper.readTree(chunk);

            // 检查是否完成
            if (node.has("done") && node.get("done").asBoolean()) {
                events.add(buildEvent("done", null));
                return events;
            }

            if (node.has("message")) {
                JsonNode message = node.get("message");

                // 深度思考模式：解析 reasoning_content（兼容 "thinking" 字段名）
                if (deepThink) {
                    String reasoning = null;
                    if (message.has("reasoning_content")) {
                        reasoning = message.get("reasoning_content").asText();
                    } else if (message.has("thinking")) {
                        reasoning = message.get("thinking").asText();
                    }
                    if (reasoning != null && !reasoning.isEmpty()) {
                        thinkingContent.append(reasoning);
                        events.add(buildEvent("thinking", reasoning));
                    }
                }

                // 解析 content（与 reasoning_content 可共存）
                if (message.has("content")) {
                    String content = message.get("content").asText();
                    if (!content.isEmpty()) {
                        assistantContent.append(content);
                        events.add(buildEvent("token", content));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("解析 Ollama chunk 失败: {}", e.getMessage());
        }
        return events;
    }

    /**
     * 构建联网搜索 SSE 事件
     *
     * 生成格式：{"type":"search","count":N,"sources":[{"title":"...","url":"..."},...]}
     * 前端收到后显示"已搜索了N个网页"并在悬停时展示来源链接列表。
     */
    private String buildSearchEvent(List<WebSearchService.SearchResultItem> results) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "search");
            event.put("count", results.size());
            List<Map<String, String>> sources = new ArrayList<>();
            for (WebSearchService.SearchResultItem item : results) {
                Map<String, String> source = new LinkedHashMap<>();
                source.put("title", item.title);
                source.put("url", item.url);
                sources.add(source);
            }
            event.put("sources", sources);
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("构建搜索事件失败", e);
            return "{\"type\":\"search\",\"count\":0,\"sources\":[]}";
        }
    }

    /**
     * 使用 ObjectMapper 构建结构化 SSE 事件 JSON
     *
     * 替代之前的手动字符串拼接 + escapeJson 方式，确保：
     * 1. 所有 Unicode 字符正确编码
     * 2. 控制字符正确转义
     * 3. 不会产生无效 JSON 导致前端 JSON.parse 静默失败
     *
     * @param type    事件类型（start/thinking/token/done/error）
     * @param content 事件内容（可为 null）
     * @return JSON 字符串
     */
    private String buildEvent(String type, String content) {
        try {
            Map<String, String> event = new LinkedHashMap<>();
            event.put("type", type);
            if (content != null) {
                event.put("content", content);
            }
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("构建 SSE 事件失败", e);
            return "{\"type\":\"" + type + "\"}";
        }
    }

    /**
     * 获取会话消息历史
     * 从数据库查询全部消息，解压后返回
     *
     * @param sessionId 会话 ID
     * @param page      页码（预留，当前未分页）
     * @param size      每页大小（预留，当前未分页）
     * @return 消息列表
     */
    public List<Map<String, Object>> getSessionMessages(Long sessionId, int page, int size) {
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", msg.getId());
            item.put("role", msg.getRole().name());
            item.put("content", decompress(msg.getContentCompressed()));
            item.put("tokenCount", msg.getTokenCount());
            item.put("createdAt", msg.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    /**
     * 删除会话及其所有消息
     *
     * @param sessionId 会话 ID
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
        redisTemplate.delete(CACHE_KEY_PREFIX + sessionId + ":messages");
    }

    // ==================== 私有方法 ====================

    private Session getOrCreateSession(Long sessionId, String modelName) {
        if (sessionId != null) {
            return sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));
        }
        Session session = Session.builder()
                .modelName(modelName)
                .title("新对话")
                .build();
        return sessionRepository.save(session);
    }

    private void saveMessage(Long sessionId, Message.MessageRole role, String content, int tokenCount) {
        Message message = Message.builder()
                .sessionId(sessionId)
                .role(role)
                .contentCompressed(compress(content))
                .contentLength(content.length())
                .tokenCount(tokenCount)
                .build();
        messageRepository.save(message);
    }

    private List<Map<String, String>> buildContext(Long sessionId) {
        List<Map<String, String>> context = new ArrayList<>();
        List<Message> recentMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        int startIdx = Math.max(0, recentMessages.size() - maxContextRounds * 2);
        for (int i = startIdx; i < recentMessages.size(); i++) {
            Message msg = recentMessages.get(i);
            Map<String, String> item = new HashMap<>();
            item.put("role", msg.getRole().name());
            item.put("content", decompress(msg.getContentCompressed()));
            context.add(item);
        }
        return context;
    }

    @Transactional
    public void saveAssistantResponse(Long sessionId, String fullContent) {
        saveMessage(sessionId, Message.MessageRole.assistant, fullContent,
                estimateTokens(fullContent));
        int count = messageRepository.countBySessionId(sessionId);
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setMessageCount(count);
            session.setUpdatedAt(java.time.LocalDateTime.now());
            sessionRepository.save(session);
        });
        redisTemplate.delete(CACHE_KEY_PREFIX + sessionId + ":messages");
    }

    private byte[] compress(String content) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
                gzip.write(content.getBytes(StandardCharsets.UTF_8));
            }
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("压缩失败", e);
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String decompress(byte[] compressed) {
        if (compressed == null) return "";
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
            try (GZIPInputStream gzip = new GZIPInputStream(bis)) {
                return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return new String(compressed, StandardCharsets.UTF_8);
        }
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) (chineseChars / 1.5 + otherChars / 4.0);
    }
}