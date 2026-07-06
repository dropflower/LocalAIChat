package com.aiapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI Chat 应用主启动类
 *
 * ## 功能描述
 * Spring Boot 应用的入口，负责启动整个后端服务。
 * 通过 @EnableScheduling 启用定时任务支持，用于会话过期清理等后台任务。
 *
 * ## 关键注解
 * - @SpringBootApplication：组合注解，包含 @Configuration、@EnableAutoConfiguration、@ComponentScan
 *   自动完成组件扫描、自动配置等 Spring Boot 核心功能
 * - @EnableScheduling：启用 Spring 的定时任务调度，使 @Scheduled 注解生效
 *   用于 CleanupService 的每日过期会话清理任务
 *
 * ## 启动方式
 * - 开发环境：mvnw.cmd spring-boot:run
 * - 打包运行：java -jar target/ai-chat-backend-1.0.0.jar
 *
 * ## 依赖服务
 * 启动前需确保以下服务已运行：
 * - MySQL 8.0 (localhost:3306, 数据库: smart_chat)
 * - Redis 7.x (localhost:6379)
 * - Ollama (localhost:11434)
 */
@SpringBootApplication
@EnableScheduling
public class AiAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAppApplication.class, args);
    }
}