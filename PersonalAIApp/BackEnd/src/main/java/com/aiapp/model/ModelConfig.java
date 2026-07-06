package com.aiapp.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型配置实体类 — 映射 sc_model_config 表
 *
 * ## 功能描述
 * 存储 AI 模型的个性化配置参数，如温度、最大 Token 数等。
 * 与 Ollama 本地模型列表配合使用，为每个模型提供自定义的运行参数。
 *
 * ## 字段说明
 * - id：主键，自增
 * - modelName：模型名称（唯一），与 Ollama 的模型名对应
 * - displayName：前端展示名称，可自定义中文名等友好名称
 * - temperature：生成温度（0.0-1.0），控制回复的随机性
 *   - 0.0：确定性输出，适合代码生成
 *   - 0.7：默认值，平衡创造性和准确性
 *   - 1.0：最大随机性，适合创意写作
 * - maxTokens：单次回复最大 Token 数，限制回复长度
 * - isActive：是否启用，禁用后前端不显示该模型
 * - createdAt：创建时间
 *
 * ## 使用方式
 * - ModelService.getAvailableModels() 会从 Ollama 获取模型列表
 * - 然后与 sc_model_config 表的配置合并
 * - 如果表中存在对应模型的配置，则覆盖默认参数
 *
 * ## 注意
 * 该表为可选配置，即使没有任何记录，系统也能正常使用 Ollama 默认参数运行
 */
@Entity
@Table(name = "sc_model_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", nullable = false, unique = true, length = 100)
    private String modelName;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal temperature = new BigDecimal("0.7");

    @Column(name = "max_tokens")
    @Builder.Default
    private Integer maxTokens = 2048;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** 首次持久化前自动设置创建时间 */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}