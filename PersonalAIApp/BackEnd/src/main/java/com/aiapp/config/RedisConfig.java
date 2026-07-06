package com.aiapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 *
 * ## 功能描述
 * 自定义 StringRedisTemplate Bean，确保 Redis 中的 Key 和 Value 都使用字符串序列化。
 * 默认的 RedisTemplate 使用 JDK 序列化，会导致存入 Redis 的数据不可读。
 *
 * ## 序列化策略
 * - Key 和 Value 统一使用 StringRedisSerializer（UTF-8 编码）
 * - 优点：存入 Redis 的数据人类可读，便于调试和跨语言客户端访问
 * - 注意：只能存储字符串类型，复杂对象需先转换为 JSON 字符串再存入
 *
 * ## 使用场景
 * - 缓存 Ollama 模型列表（JSON 字符串）
 * - 缓存会话消息历史（JSON 字符串）
 * - 会话列表缓存
 *
 * ## 依赖关系
 * - 依赖 RedisConnectionFactory（由 spring-boot-starter-data-redis 自动配置）
 * - 连接参数（host/port/password）在 application.yml 中配置
 */
@Configuration
public class RedisConfig {

    /**
     * 创建 StringRedisTemplate Bean
     * 配置字符串序列化器，确保存入 Redis 的数据可读
     *
     * @param connectionFactory Redis 连接工厂（自动注入）
     * @return 配置好的 StringRedisTemplate 实例
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}