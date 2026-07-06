package com.aiapp.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API Key 认证拦截器
 *
 * ## 功能描述
 * 在请求到达 Controller 之前拦截并验证请求头中的 X-API-Key。
 * 实现了简化的单用户认证机制，适用于本地单机部署场景。
 *
 * ## 认证流程
 * 1. 检查请求路径是否为公开端点（/api/health、/api/auth/login），是则直接放行
 * 2. 检查请求方法是否为 OPTIONS（CORS 预检请求），是则直接放行
 * 3. 从请求头 X-API-Key 中提取 API Key，与配置中的值比对
 * 4. 匹配成功：放行；匹配失败：返回 401 + JSON 错误响应
 *
 * ## 安全考虑
 * - API Key 通过配置文件管理，默认值仅用于本地开发
 * - 生产环境应通过环境变量 AI_APP_API_KEY 注入
 * - 认证失败会记录日志（含请求路径和来源 IP），便于安全审计
 *
 * ## 依赖关系
 * - 依赖 application.yml 中的 app.auth.api-key 配置项
 * - 在 WebConfig 中注册，拦截 /api/** 路径
 */
@Slf4j
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    @Value("${app.auth.api-key}")
    private String configuredApiKey;

    /**
     * 请求前置处理：验证 API Key
     *
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @param handler  处理器对象
     * @return true 放行，false 拦截并返回 401
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 公开端点放行：健康检查和登录接口无需认证
        String path = request.getRequestURI();
        if (path.startsWith("/api/health") || path.startsWith("/api/auth/login")) {
            return true;
        }

        // OPTIONS 预检请求放行：浏览器 CORS 机制在跨域请求前发送的探测请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 验证 API Key：从请求头 X-API-Key 中获取并比对
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && apiKey.equals(configuredApiKey)) {
            return true;
        }

        // 认证失败：记录日志并返回 401 JSON 响应
        log.warn("认证失败 - 请求路径: {}, IP: {}", path, request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":401,\"message\":\"未授权：API Key 无效\",\"data\":null," +
                "\"timestamp\":" + System.currentTimeMillis() + "}");
        return false;
    }
}