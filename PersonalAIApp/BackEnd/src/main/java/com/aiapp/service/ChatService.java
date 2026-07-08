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
            WebSearchService.SearchContext searchContext = webSearchService.searchWithContext(userMessage);
            List<WebSearchService.SearchResultItem> searchResults = searchContext.results;
            boolean hasResults = !searchResults.isEmpty();
            boolean hasWeatherData = searchContext.weatherData != null && !searchContext.weatherData.isEmpty();

            if (hasResults || hasWeatherData) {
                if (hasResults) {
                    prefixEvents.add(buildSearchEvent(searchResults));
                }
                String formattedResults = webSearchService.formatResults(
                        searchResults, searchContext.isWeatherQuery,
                        searchContext.city, searchContext.weatherData);
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

/**
 * 根据会话ID构建上下文消息列表
 * @param sessionId 会话ID
 * @return 包含角色和内容的消息列表，格式为List<Map<String, String>>
 */
    private List<Map<String, String>> buildContext(Long sessionId) {
    // 创建一个新的ArrayList用于存储上下文消息
        List<Map<String, String>> context = new ArrayList<>();
    // 从数据库中获取按创建时间升序排列的最近消息列表
        List<Message> recentMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    // 计算起始索引，保留最近的maxContextRounds*2条消息
        int startIdx = Math.max(0, recentMessages.size() - maxContextRounds * 2);
    // 遍历消息列表，从计算出的起始索引开始
        for (int i = startIdx; i < recentMessages.size(); i++) {
        // 获取当前消息
            Message msg = recentMessages.get(i);
        // 创建一个新的HashMap用于存储单条消息
            Map<String, String> item = new HashMap<>();
        // 将消息角色名称和内容（解压后）添加到map中
            item.put("role", msg.getRole().name());
            item.put("content", decompress(msg.getContentCompressed()));
        // 将map添加到上下文列表中
            context.add(item);
        }
    // 返回构建好的上下文列表
        return context;
    }

/**
 * 保存AI助手的回复内容
 * @param sessionId 会话ID，用于标识当前对话
 * @param fullContent AI助手的完整回复内容
 * @Transactional 注解确保方法内的操作是一个事务，保证数据一致性
 */
    @Transactional
    public void saveAssistantResponse(Long sessionId, String fullContent) {
    // 保存AI助手的消息，包括角色、内容和预估的token数
        saveMessage(sessionId, Message.MessageRole.assistant, fullContent,
                estimateTokens(fullContent));
    // 统计当前会话中的消息总数
        int count = messageRepository.countBySessionId(sessionId);
    // 更新会话信息，包括消息数量和更新时间
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setMessageCount(count);
            session.setUpdatedAt(java.time.LocalDateTime.now());
            sessionRepository.save(session);
        });
    // 清除Redis缓存中该会话的消息缓存，确保数据一致性
        redisTemplate.delete(CACHE_KEY_PREFIX + sessionId + ":messages");
    }

/**
 * 使用GZIP算法压缩字符串内容
 * @param content 需要压缩的字符串内容
 * @return 压缩后的字节数组，如果压缩失败则返回原始内容的字节数组
 */
    private byte[] compress(String content) {
        try {
        // 创建一个字节数组输出流
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
        // 使用try-with-resources语句确保GZIPOutputStream正确关闭
            try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            // 将字符串内容以UTF-8编码写入GZIP输出流
                gzip.write(content.getBytes(StandardCharsets.UTF_8));
            }
        // 返回压缩后的字节数组
            return bos.toByteArray();
        } catch (IOException e) {
        // 记录压缩失败的错误日志
            log.error("压缩失败", e);
        // 如果压缩失败，返回原始内容的字节数组
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }

/**
 * 解压缩字节数组的方法
 * @param compressed 被压缩的字节数组
 * @return 解压后的字符串，如果输入为null则返回空字符串
 */
    private String decompress(byte[] compressed) {
    // 检查输入是否为null，如果是则返回空字符串
        if (compressed == null) return "";
        try {
        // 创建字节数组输入流，用于读取压缩数据
            ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
        // 使用try-with-resources语句创建GZIP输入流，确保资源自动关闭
            try (GZIPInputStream gzip = new GZIPInputStream(bis)) {
            // 读取所有字节并转换为UTF-8编码的字符串返回
                return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
        // 如果发生IO异常，将原始字节数组直接转换为UTF-8字符串返回
            return new String(compressed, StandardCharsets.UTF_8);
        }
    }

/**
 * 估算文本中的token数量
 * @param text 需要估算token的文本字符串
 * @return 计算得到的token数量
 */
    private int estimateTokens(String text) {
        // 如果输入文本为null或空字符串，直接返回0
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0;    // 中文字符计数器
        int otherChars = 0;      // 其他字符计数器
        // 遍历文本中的每个字符
        for (char c : text.toCharArray()) {
            // 判断字符是否属于中日韩统一表意文字区块
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    // 或者属于中日韩统一表意文字扩展A区块
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    // 或者属于中日韩兼容表意文字区块
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                chineseChars++;    // 中文字符计数加1
            } else {
                otherChars++;      // 其他字符计数加1
            }
        }
        // 根据中文字符和其他字符的数量计算token总数
        // 中文字符按1.5个字符算1个token，其他字符按4个字符算1个token
        return (int) (chineseChars / 1.5 + otherChars / 4.0);
    }
}