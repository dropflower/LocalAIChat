package com.aiapp.controller;

import com.aiapp.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 *
 * ## 功能描述
 * 处理用户登录认证。接收前端提交的 API Key，与配置中的值比对。
 * 认证成功返回确认信息，前端据此设置认证状态。
 *
 * ## 接口说明
 * POST /api/auth/login
 * - 请求体：{"apiKey": "xxx"}
 * - 成功响应：{"code": 200, "message": "success", "data": {"status": "ok", "message": "认证成功"}}
 * - 失败响应：{"code": 401, "message": "API Key 无效"}
 *
 * ## 安全说明
 * - 该接口为公开端点，不经过 ApiKeyInterceptor 拦截（在 WebConfig 中排除）
 * - API Key 值从 application.yml 的 app.auth.api-key 读取
 * - 单用户场景下，API Key 固定不变，登录仅做验证用途
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${app.auth.api-key}")
    private String configuredApiKey;

    /**
     * 登录接口 — 验证 API Key
     * @param body 请求体，包含 apiKey 字段
     * @return 认证结果
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody Map<String, String> body) {
        log.info("收到登录请求，请求体: {}", body);
        String apiKey = body.get("apiKey");
        if (apiKey != null && apiKey.equals(configuredApiKey)) {
            return ApiResponse.success(Map.of("status", "ok", "message", "认证成功"));
        }
        return ApiResponse.error(401, "API Key 无效");
    }
}