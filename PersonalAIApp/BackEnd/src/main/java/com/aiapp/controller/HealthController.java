package com.aiapp.controller;

import com.aiapp.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查控制器
 *
 * ## 功能描述
 * 提供后端服务的健康检查端点，用于确认服务是否正常运行。
 * 该接口为公开端点，无需认证。
 *
 * ## 接口说明
 * GET /api/health
 * - 响应：{"code": 200, "data": {"status": "ok", "service": "ai-chat-backend"}}
 *
 * ## 使用场景
 * - 前端启动时检查后端是否可用
 * - 运维监控（如 Nagios、Prometheus 等）
 * - Docker 容器健康检查
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        log.info("收到健康检查请求");
        return ApiResponse.success(Map.of("status", "ok", "service", "ai-chat-backend"));
    }
}