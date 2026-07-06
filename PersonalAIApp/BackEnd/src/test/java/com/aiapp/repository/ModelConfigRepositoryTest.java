package com.aiapp.repository;

import com.aiapp.model.ModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ModelConfigRepository 单元测试类
 *
 * <p>功能描述：测试模型配置数据访问层（ModelConfigRepository）的查询方法，验证按模型名称
 * 查询配置和按模型名称+活跃状态查询配置的功能是否正确。</p>
 *
 * <p>测试策略：使用 @DataJpaTest 仅加载 JPA 相关组件，通过内嵌 H2 内存数据库替代 MySQL，
 * 使用 TestEntityManager 进行数据准备和验证。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>findByModelName：按模型名称精确查询配置</li>
 *   <li>findByModelName 不存在时返回 Optional.empty()</li>
 *   <li>findByModelNameAndIsActiveTrue：仅返回 isActive=true 的配置</li>
 *   <li>isActive=false 的配置不会被查询返回</li>
 * </ul>
 * </p>
 *
 * <p>特殊说明：使用 H2 内存数据库（MySQL 兼容模式）替代实际 MySQL，
 * 排除了 Redis 自动配置以避免连接依赖。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.sql.init.mode=never"
})
class ModelConfigRepositoryTest {

    /** JPA 测试实体管理器，用于数据准备和直接查询验证 */
    @Autowired
    private TestEntityManager entityManager;

    /** 待测试的模型配置仓库实例 */
    @Autowired
    private ModelConfigRepository modelConfigRepository;

    /**
     * 测试：按模型名称查询配置，存在时应返回对应配置
     *
     * <p>输入参数：modelName = "qwen"，displayName = "通义千问"，temperature = 0.7，maxTokens = 2048</p>
     * <p>预期结果：findByModelName("qwen") 返回 Optional 包含配置，displayName 为 "通义千问"</p>
     * <p>验证逻辑：确认按模型名称能精确查找到已持久化的配置记录，
     * 且各字段值（displayName 等）正确保存和读取</p>
     */
    @Test
    void findByModelName_ShouldReturnConfig() {
        ModelConfig config = ModelConfig.builder()
                .modelName("qwen")
                .displayName("通义千问")
                .temperature(new BigDecimal("0.7"))
                .maxTokens(2048)
                .isActive(true)
                .build();
        entityManager.persist(config);
        entityManager.flush();

        Optional<ModelConfig> result = modelConfigRepository.findByModelName("qwen");

        assertTrue(result.isPresent());
        assertEquals("通义千问", result.get().getDisplayName());
    }

    /**
     * 测试：按模型名称查询不存在的配置，应返回空 Optional
     *
     * <p>输入参数：modelName = "unknown"（数据库中不存在）</p>
     * <p>预期结果：findByModelName("unknown") 返回 Optional.empty()</p>
     * <p>验证逻辑：确认查询不存在的模型名称时不会抛出异常，
     * 而是返回空的 Optional 供调用方安全处理</p>
     */
    @Test
    void findByModelName_NotFound_ShouldReturnEmpty() {
        Optional<ModelConfig> result = modelConfigRepository.findByModelName("unknown");
        assertTrue(result.isEmpty());
    }

    /**
     * 测试：按模型名称和活跃状态查询，应仅返回 isActive=true 的配置
     *
     * <p>输入参数：活跃配置 modelName="qwen"/isActive=true，
     * 非活跃配置 modelName="llama"/isActive=false</p>
     * <p>预期结果：findByModelNameAndIsActiveTrue("qwen") 返回 Optional 有值，
     * findByModelNameAndIsActiveTrue("llama") 返回 Optional.empty()</p>
     * <p>验证逻辑：确认查询条件中的 isActive 限制生效，
     * 仅活跃状态的模型配置会被返回，非活跃配置被过滤掉。
     * 该方法用于模型列表合并配置时，确保只应用已启用的配置</p>
     */
    @Test
    void findByModelNameAndIsActiveTrue_ShouldReturnOnlyActive() {
        ModelConfig activeConfig = ModelConfig.builder()
                .modelName("qwen")
                .isActive(true)
                .build();
        ModelConfig inactiveConfig = ModelConfig.builder()
                .modelName("llama")
                .isActive(false)
                .build();
        entityManager.persist(activeConfig);
        entityManager.persist(inactiveConfig);
        entityManager.flush();

        Optional<ModelConfig> active = modelConfigRepository
                .findByModelNameAndIsActiveTrue("qwen");
        Optional<ModelConfig> inactive = modelConfigRepository
                .findByModelNameAndIsActiveTrue("llama");

        assertTrue(active.isPresent());
        assertTrue(inactive.isEmpty());
    }
}
