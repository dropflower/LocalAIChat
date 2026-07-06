package com.aiapp.controller;

import com.aiapp.model.ApiResponse;
import com.aiapp.service.ModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模型管理控制器
 *
 * ## 功能描述
 * 提供模型列表查询和 Ollama 服务状态检查接口。
 *
 * ## 接口说明
 * 1. GET /api/models — 获取可用模型列表
 *    - 返回从 Ollama 获取的本地模型列表（含数据库配置合并）
 *    - 数据来自 ModelService，经过 Redis 缓存（5 分钟 TTL）
 *
 * 2. GET /api/models/status — 检查 Ollama 服务状态
 *    - 返回 Ollama 服务是否可用，以及当前模型列表
 *    - 前端用于判断是否显示"Ollama 未连接"提示
 */
@Slf4j
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    /**
     * 获取可用模型列表
     * @return 模型信息列表
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listModels() {
        log.info("获取可用模型列表");
        return ApiResponse.success(modelService.getAvailableModels());
    }

    /**
     * 检查 Ollama 服务状态和模型列表
     * @return 包含 ollamaAvailable 和 models 两个字段
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> checkStatus() {
        log.info("检查 Ollama 服务状态和模型列表");
        boolean available = modelService.isOllamaAvailable();
        return ApiResponse.success(Map.of(
                "ollamaAvailable", available,
                "models", modelService.getAvailableModels()
        ));
    }
}