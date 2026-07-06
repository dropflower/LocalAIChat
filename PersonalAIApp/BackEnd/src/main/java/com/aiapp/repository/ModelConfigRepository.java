package com.aiapp.repository;

import com.aiapp.model.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 模型配置数据访问层 — ModelConfig 实体 Repository
 *
 * ## 功能描述
 * 提供 ModelConfig 实体的数据访问操作，用于查询 AI 模型的个性化配置。
 *
 * ## 自定义方法说明
 * - findByModelName：按模型名称精确查询配置
 * - findByModelNameAndIsActiveTrue：查询已激活的模型配置
 *   ModelService 使用此方法，只获取用户启用的配置项
 *
 * ## 使用场景
 * 当 ModelService 从 Ollama 获取模型列表后，会调用此 Repository
 * 查询每个模型对应的自定义配置（温度、最大 Token 等），
 * 并将配置合并到返回的模型信息中
 */
@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {

    /**
     * 按模型名称查询配置
     * @param modelName 模型名称
     * @return 模型配置（可能为空）
     */
    Optional<ModelConfig> findByModelName(String modelName);

    /**
     * 查询已激活的模型配置
     * @param modelName 模型名称
     * @return 已激活的模型配置（可能为空）
     */
    Optional<ModelConfig> findByModelNameAndIsActiveTrue(String modelName);
}