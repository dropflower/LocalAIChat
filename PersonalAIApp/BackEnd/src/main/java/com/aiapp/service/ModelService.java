package com.aiapp.service;

import com.aiapp.model.ModelConfig;
import com.aiapp.repository.ModelConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * 模型管理服务
 *
 * ## 功能描述
 * 管理 AI 模型的获取、缓存和配置合并。
 * 从 Ollama 获取本地已安装模型，合并数据库中的自定义配置，并通过 Redis 缓存结果。
 *
 * ## 核心流程
 * 1. 先查 Redis 缓存（Key: "models:list"，TTL: 5 分钟）
 * 2. 缓存未命中时，调用 OllamaClientService.listModels() 获取模型列表
 * 3. 遍历模型列表，与 sc_model_config 表合并配置（displayName、temperature、maxTokens）
 * 4. 将合并后的结果写入 Redis 缓存
 * 5. 返回模型列表
 *
 * ## 缓存策略
 * - 使用 Cache-Aside 模式
 * - 缓存 TTL 设为 5 分钟，因为模型列表不会频繁变化
 * - 缓存的 JSON 使用 Jackson 序列化/反序列化，确保特殊字符正确转义
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {

    private final OllamaClientService ollamaClient;
    private final ModelConfigRepository modelConfigRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY = "models:list";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * 获取可用模型列表（带缓存）
     *
     * 1. 尝试从 Redis 读取缓存
     * 2. 缓存未命中则从 Ollama 获取
     * 3. 合并数据库中的模型配置
     * 4. 写入 Redis 缓存
     *
     * @return 模型信息列表，每个元素包含 name/displayName/size/temperature/maxTokens
     */
    public List<Map<String, Object>> getAvailableModels() {
        // 尝试从 Redis 缓存获取
        String cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            try {
                List<Map<String, Object>> parsed = parseJsonList(cached);
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            } catch (Exception e) {
                log.warn("缓存解析失败，重新获取模型列表: {}", e.getMessage());
            }
        }

        // 从 Ollama 获取
        List<Map<String, Object>> models = ollamaClient.listModels();

        // 合并数据库中的配置（displayName、temperature、maxTokens）
        for (Map<String, Object> model : models) {
            String modelName = (String) model.get("name");
            modelConfigRepository.findByModelNameAndIsActiveTrue(modelName)
                    .ifPresent(config -> {
                        model.put("displayName", config.getDisplayName() != null
                                ? config.getDisplayName() : modelName);
                        model.put("temperature", config.getTemperature());
                        model.put("maxTokens", config.getMaxTokens());
                    });
        }

        // 缓存结果
        try {
            String json = objectMapper.writeValueAsString(models);
            redisTemplate.opsForValue().set(CACHE_KEY, json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("模型列表缓存失败: {}", e.getMessage());
        }

        return models;
    }

    /**
     * 检查 Ollama 服务状态
     * 委托给 OllamaClientService.isAvailable()
     *
     * @return true 可用，false 不可用
     */
    public boolean isOllamaAvailable() {
        return ollamaClient.isAvailable();
    }

    /**
     * 解析缓存的 JSON 字符串为模型列表
     * 使用 Jackson TypeReference 处理泛型，正确反序列化 List<Map<String, Object>>
     *
     * @param json 缓存的 JSON 字符串
     * @return 模型信息列表
     * @throws Exception 解析失败时抛出异常，由调用方处理
     */
    private List<Map<String, Object>> parseJsonList(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    }
}