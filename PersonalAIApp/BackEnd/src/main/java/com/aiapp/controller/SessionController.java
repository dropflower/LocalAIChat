package com.aiapp.controller;

import com.aiapp.model.ApiResponse;
import com.aiapp.model.Session;
import com.aiapp.service.ChatService;
import com.aiapp.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话管理控制器
 *
 * ## 功能描述
 * 提供会话的完整 CRUD 操作，包括列表查询、搜索、重命名、置顶切换、删除。
 *
 * ## 接口说明
 * 1. GET    /api/sessions          — 分页查询会话列表
 * 2. GET    /api/sessions/search   — 按标题模糊搜索会话
 * 3. PUT    /api/sessions/{id}/title — 重命名会话
 * 4. PUT    /api/sessions/{id}/pin  — 切换置顶状态
 * 5. DELETE /api/sessions/{id}     — 删除会话及所有消息
 *
 * ## 删除操作说明
 * 删除会话时会同时删除关联的所有消息（由 ChatService.deleteSession() 处理）
 * 并清除 Redis 缓存。删除操作不可撤销。
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final ChatService chatService;

    /**
     * 分页查询会话列表
     * 排序：置顶优先 → 最后更新时间倒序
     */
    @GetMapping
    public ApiResponse<List<Session>> listSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.info("收到会话列表查询请求");
        return ApiResponse.success(sessionService.listSessions(page, size));
    }

    /**
     * 按标题模糊搜索会话
     */
    @GetMapping("/search")
    public ApiResponse<List<Session>> searchSessions(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("收到会话搜索请求");
        return ApiResponse.success(sessionService.searchSessions(keyword, page, size));
    }

    /**
     * 重命名会话标题
     * @param id   会话 ID
     * @param body 请求体，包含 title 字段
     */
    @PutMapping("/{id}/title")
    public ApiResponse<Session> updateTitle(@PathVariable Long id, @RequestBody Map<String, String> body) {
        log.info("收到会话重命名请求");
        String title = body.get("title");
        return ApiResponse.success(sessionService.updateTitle(id, title));
    }

    /**
     * 切换会话置顶状态
     * @param id 会话 ID
     */
    @PutMapping("/{id}/pin")
    public ApiResponse<Session> togglePin(@PathVariable Long id) {
        log.info("收到会话置顶状态切换请求");
        return ApiResponse.success(sessionService.togglePin(id));
    }

    /**
     * 删除会话及所有消息
     * 该操作不可撤销
     * @param id 会话 ID
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, String>> deleteSession(@PathVariable Long id) {
        log.info("收到会话删除请求");
        chatService.deleteSession(id);
        return ApiResponse.success(Map.of("status", "ok", "message", "会话已删除"));
    }
}