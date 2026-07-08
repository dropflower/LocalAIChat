package com.aiapp.controller;

import com.aiapp.model.ApiResponse;
import com.aiapp.model.ChatRequest;
import com.aiapp.service.ChatService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 对话控制器
 *
 * ## 功能描述
 * 处理对话相关的 HTTP 请求，包括流式对话和消息历史查询。
 *
 * ## 接口说明
 * 1. POST /api/chat/completions — 流式对话（SSE）
 *    - Content-Type: text/event-stream
 *    - 请求体：ChatRequest { sessionId, modelName, message, deepThink, enableSearch }
 *    - 响应：SseEmitter SSE 流，每个事件是结构化 JSON
 *    - 前端使用 fetch + ReadableStream 逐行解析 SSE 数据
 *
 * 2. GET /api/chat/sessions/{sessionId}/messages — 查询消息历史
 *    - 返回指定会话的全部消息（已解压）
 *    - 支持分页参数 page 和 size
 *
 * ## SseEmitter vs Flux<String>
 * 之前使用 Flux<String> + TEXT_EVENT_STREAM_VALUE 返回 SSE，依赖 Spring MVC 的
 * ReactiveTypeHandler 隐式处理。存在以下问题：
 * - 超时不可控：默认 30s，无法匹配 Ollama 长时间生成
 * - 缓冲不可控：Spring MVC 可能缓冲事件，无法保证逐个 flush
 * - 错误处理不透明：Flux 管道中的异常传播路径不明确
 *
 * 改用 SseEmitter 后：
 * - 显式设置 5 分钟超时
 * - 每个 send() 后自动 flush，确保前端实时收到事件
 * - 错误处理明确：onError → completeWithError，onComplete → complete
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** SSE 连接超时时间：5 分钟（毫秒） */
    private static final long SSE_TIMEOUT = 300_000L;

    /**
     * 流式对话接口（SSE）
     *
     * 使用 SseEmitter 显式控制 SSE 流式传输：
     * 1. 创建 SseEmitter 并设置 5 分钟超时
     * 2. 在 Schedulers.boundedElastic() 线程上订阅 Flux
     * 3. 逐个将 Flux 事件通过 SseEmitter.send() 推送给客户端
     * 4. 每个 send() 自动 flush，确保实时性
     *
     * @param request 对话请求（sessionId, modelName, message, deepThink, enableSearch）
     * @return SseEmitter SSE 流式响应
     */
    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatCompletions(@RequestBody ChatRequest request, HttpServletResponse response) {
        if (request.getModelName() == null || request.getModelName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型名称不能为空");
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
        }
        log.info("收到对话请求 - sessionId: {}, model: {}, deepThink: {}, search: {}",
                request.getSessionId(), request.getModelName(), request.isDeepThink(), request.isEnableSearch());

        // 禁用 Tomcat 输出缓冲，确保 SSE 事件逐个发送到客户端
        // 不设置此项，Tomcat 会缓冲数据直到缓冲区满才刷新，导致前端收到空文字
        response.setBufferSize(0);
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        chatService.chatStream(
                        request.getSessionId(),
                        request.getModelName(),
                        request.getMessage(),
                        request.isDeepThink(),
                        request.isEnableSearch()
                )
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        event -> {
                            try {
                                emitter.send(SseEmitter.event().data(event));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> emitter.completeWithError(error),
                        () -> emitter.complete()
                );

        emitter.onTimeout(() -> log.warn("SSE 连接超时 - sessionId: {}", request.getSessionId()));
        emitter.onError(ex -> log.error("SSE 连接异常", ex));

        return emitter;
    }

    /**
     * 获取会话消息历史
     *
     * @param sessionId 会话 ID（路径变量）
     * @param page      页码，默认 0
     * @param size      每页大小，默认 50
     * @return 消息列表
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<Map<String, Object>>> getMessages(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.info("收到消息历史查询请求 - sessionId: {}, page: {}, size: {}", sessionId, page, size);
        return ApiResponse.success(chatService.getSessionMessages(sessionId, page, size));
    }
}
