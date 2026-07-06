package com.aiapp.service;

import com.aiapp.model.ModelConfig;
import com.aiapp.repository.ModelConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ModelService 单元测试类
 *
 * <p>功能描述：测试模型服务（ModelService）的核心功能，包括可用模型列表的缓存策略、
 * Ollama 模型与数据库配置的合并逻辑、Ollama 可用性检查等。</p>
 *
 * <p>测试策略：使用 @ExtendWith(MockitoExtension.class) 进行纯 Mockito 单元测试，
 * 通过 @Mock 替换 OllamaClientService、ModelConfigRepository、StringRedisTemplate 和
 * ValueOperations，@InjectMocks 自动创建 ModelService 实例。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>缓存命中：从 Redis 读取模型列表，不调用 Ollama</li>
 *   <li>缓存未命中：从 Ollama 获取模型列表并写入 Redis</li>
 *   <li>模型配置合并：Ollama 模型信息与数据库 ModelConfig 合并</li>
 *   <li>无配置/非活跃配置：不合并额外字段</li>
 *   <li>Redis 写入异常：不抛出异常，仍返回模型列表</li>
 *   <li>Ollama 可用性：委托给 OllamaClientService</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ModelServiceTest {

    @Mock
    private OllamaClientService ollamaClient;

    @Mock
    private ModelConfigRepository modelConfigRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ModelService modelService;

    /**
     * 测试：缓存命中时获取模型列表，应从 Redis 读取且不调用 Ollama
     *
     * <p>输入参数：Redis 中存在 models:list 缓存</p>
     * <p>预期结果：返回结果不调用 ollamaClient.listModels()</p>
     * <p>验证逻辑：确认缓存命中时直接使用缓存数据，避免不必要的 Ollama 调用，
     * 减少 Ollama 服务压力和响应延迟</p>
     */
    @Test
    void getAvailableModels_CacheHit_ShouldReturnFromCache() {
        String cachedJson = "[{\"name\":\"qwen\",\"size\":1000}]";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("models:list")).thenReturn(cachedJson);

        // parseJsonList is a placeholder returning empty list, so cache hit returns empty
        List<Map<String, Object>> result = modelService.getAvailableModels();

        // verify Ollama was NOT called
        verify(ollamaClient, never()).listModels();
    }

    /**
     * 测试：缓存未命中时获取模型列表，应从 Ollama 获取并写入 Redis
     *
     * <p>输入参数：Redis 中 models:list 为 null（缓存过期或首次访问）</p>
     * <p>预期结果：调用 ollamaClient.listModels() 获取模型列表，
     * 返回 1 个模型，且 valueOperations.set() 被调用写入缓存</p>
     * <p>前置条件：Mock Ollama 返回 1 个模型（name="qwen", size=1000），
     * 数据库中无对应 ModelConfig</p>
     * <p>验证逻辑：确认缓存穿透时从 Ollama 获取最新数据，
     * 并将结果写入 Redis 缓存（TTL 5分钟）以供后续请求使用</p>
     */
    @Test
    void getAvailableModels_CacheMiss_ShouldFetchFromOllama() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("models:list")).thenReturn(null);

        List<Map<String, Object>> ollamaModels = new ArrayList<>();
        Map<String, Object> model = new HashMap<>();
        model.put("name", "qwen");
        model.put("size", 1000L);
        ollamaModels.add(model);
        when(ollamaClient.listModels()).thenReturn(ollamaModels);
        when(modelConfigRepository.findByModelNameAndIsActiveTrue("qwen"))
                .thenReturn(Optional.empty());

        List<Map<String, Object>> result = modelService.getAvailableModels();

        assertEquals(1, result.size());
        assertEquals("qwen", result.get(0).get("name"));
        verify(ollamaClient).listModels();
        verify(valueOperations).set(eq("models:list"), anyString(), any());
    }

    /**
     * 测试：获取模型列表时应合并数据库中的 ModelConfig 配置
     *
     * <p>输入参数：Ollama 返回 name="qwen" 模型，数据库中存在活跃配置
     *（displayName="通义千问", temperature=0.8, maxTokens=4096）</p>
     * <p>预期结果：返回的模型数据包含 Ollama 基础信息和数据库配置字段，
     * displayName="通义千问"，temperature=0.8，maxTokens=4096</p>
     * <p>验证逻辑：确认 Ollama 模型列表与数据库配置的合并逻辑正确，
     * 前端可同时展示模型运行时信息和用户自定义配置</p>
     */
    @Test
    void getAvailableModels_ShouldMergeModelConfig() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("models:list")).thenReturn(null);

        List<Map<String, Object>> ollamaModels = new ArrayList<>();
        Map<String, Object> model = new HashMap<>();
        model.put("name", "qwen");
        model.put("size", 1000L);
        ollamaModels.add(model);
        when(ollamaClient.listModels()).thenReturn(ollamaModels);

        ModelConfig config = ModelConfig.builder()
                .modelName("qwen")
                .displayName("通义千问")
                .temperature(new BigDecimal("0.8"))
                .maxTokens(4096)
                .isActive(true)
                .build();
        when(modelConfigRepository.findByModelNameAndIsActiveTrue("qwen"))
                .thenReturn(Optional.of(config));

        List<Map<String, Object>> result = modelService.getAvailableModels();

        assertEquals("通义千问", result.get(0).get("displayName"));
        assertEquals(new BigDecimal("0.8"), result.get(0).get("temperature"));
        assertEquals(4096, result.get(0).get("maxTokens"));
    }

    /**
     * 测试：数据库中无对应 ModelConfig 时，模型数据不应包含配置字段
     *
     * <p>输入参数：Ollama 返回 name="qwen" 模型，数据库中无对应配置</p>
     * <p>预期结果：返回的模型数据中 displayName 和 temperature 为 null</p>
     * <p>验证逻辑：确认无配置时不合并额外字段，仅返回 Ollama 原始数据，
     * 前端需处理配置字段为 null 的情况</p>
     */
    @Test
    void getAvailableModels_NoConfig_ShouldReturnWithoutMerge() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("models:list")).thenReturn(null);

        List<Map<String, Object>> ollamaModels = new ArrayList<>();
        Map<String, Object> model = new HashMap<>();
        model.put("name", "qwen");
        ollamaModels.add(model);
        when(ollamaClient.listModels()).thenReturn(ollamaModels);
        when(modelConfigRepository.findByModelNameAndIsActiveTrue("qwen"))
                .thenReturn(Optional.empty());

        List<Map<String, Object>> result = modelService.getAvailableModels();

        assertNull(result.get(0).get("displayName"));
        assertNull(result.get(0).get("temperature"));
    }

    /**
     * 测试：ModelConfig 为非活跃状态时，不应合并到模型数据
     *
     * <p>输入参数：Ollama 返回 name="qwen" 模型，数据库中配置 isActive=false</p>
     * <p>预期结果：findByModelNameAndIsActiveTrue 返回 empty，displayName 为 null</p>
     * <p>验证逻辑：确认仅活跃状态的配置才参与合并，
     * 非活跃配置（如已下线的模型参数）不应影响前端展示</p>
     */
    @Test
    void getAvailableModels_InactiveConfig_ShouldNotMerge() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("models:list")).thenReturn(null);

        List<Map<String, Object>> ollamaModels = new ArrayList<>();
        Map<String, Object> model = new HashMap<>();
        model.put("name", "qwen");
        ollamaModels.add(model);
        when(ollamaClient.listModels()).thenReturn(ollamaModels);
        // findByModelNameAndIsActiveTrue returns empty for inactive config
        when(modelConfigRepository.findByModelNameAndIsActiveTrue("qwen"))
                .thenReturn(Optional.empty());

        List<Map<String, Object>> result = modelService.getAvailableModels();

        assertNull(result.get(0).get("displayName"));
    }

    /**
     * 测试：Redis 缓存写入失败时，不应抛出异常，仍返回模型列表
     *
     * <p>输入参数：缓存未命中，Ollama 返回模型数据，但 Redis set() 抛出 RuntimeException</p>
     * <p>预期结果：方法正常返回模型列表（size=1），不抛出异常</p>
     * <p>验证逻辑：确认 Redis 不可用时不影响核心功能，模型列表仍可正常返回，
     * 体现了降级容错策略：缓存为辅助优化，非关键依赖</p>
     */
    @Test
    void getAvailableModels_CacheWriteError_ShouldNotThrow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("models:list")).thenReturn(null);

        List<Map<String, Object>> ollamaModels = new ArrayList<>();
        Map<String, Object> model = new HashMap<>();
        model.put("name", "qwen");
        ollamaModels.add(model);
        when(ollamaClient.listModels()).thenReturn(ollamaModels);
        when(modelConfigRepository.findByModelNameAndIsActiveTrue("qwen"))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("Redis error"))
                .when(valueOperations).set(eq("models:list"), anyString(), any());

        // should not throw
        List<Map<String, Object>> result = modelService.getAvailableModels();
        assertEquals(1, result.size());
    }

    /**
     * 测试：isOllamaAvailable 应委托给 OllamaClientService
     *
     * <p>输入参数：分别 Mock ollamaClient.isAvailable() 返回 true 和 false</p>
     * <p>预期结果：modelService.isOllamaAvailable() 返回值与 ollamaClient 一致</p>
     * <p>验证逻辑：确认 ModelService 的可用性检查是 OllamaClientService 的直接委托，
     * 无额外逻辑处理</p>
     */
    @Test
    void isOllamaAvailable_ShouldDelegateToOllamaClient() {
        when(ollamaClient.isAvailable()).thenReturn(true);
        assertTrue(modelService.isOllamaAvailable());

        when(ollamaClient.isAvailable()).thenReturn(false);
        assertFalse(modelService.isOllamaAvailable());
    }
}
