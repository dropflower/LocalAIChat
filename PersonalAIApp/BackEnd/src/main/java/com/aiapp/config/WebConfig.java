package com.aiapp.config;

import com.aiapp.interceptor.ApiKeyInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 全局配置类
 *
 * ## 功能描述
 * 集中管理 Spring MVC 的全局配置，包括：
 * 1. CORS（跨域资源共享）策略 — 允许前端开发服务器跨域访问后端 API
 * 2. 拦截器注册 — 注册 API Key 认证拦截器，保护所有 /api/** 路径
 *
 * ## CORS 配置说明
 * - allowedOrigins：仅允许本地前端开发服务器（Vite 默认端口 5173）
 * - allowedMethods：GET/POST/PUT/DELETE/OPTIONS，覆盖 RESTful 全操作
 * - allowCredentials：允许携带认证信息（如 Cookie）
 * - maxAge：预检请求缓存时间（秒），减少 OPTIONS 请求频率
 *
 * ## 拦截器配置说明
 * - 拦截路径：/api/**（所有 API 接口）
 * - 排除路径：/api/health（健康检查）、/api/auth/login（登录接口）
 *   这两个路径为公开访问，无需认证
 *
 * ## 依赖关系
 * - 依赖 ApiKeyInterceptor 进行 API Key 校验
 * - 使用 Lombok @RequiredArgsConstructor 实现构造器注入
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ApiKeyInterceptor apiKeyInterceptor;

    /**
     * 配置 CORS（跨域资源共享）策略
     * 允许前端 Vite 开发服务器（localhost:5173）跨域访问后端 API
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 注册拦截器
     * 将 ApiKeyInterceptor 应用到所有 /api/** 路径
     * 排除 /api/health 和 /api/auth/login 两个公开端点
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/auth/login");
    }
}