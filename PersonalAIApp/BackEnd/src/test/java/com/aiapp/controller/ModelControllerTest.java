package com.aiapp.controller;

import com.aiapp.service.ModelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ModelController 单元测试类
 *
 * <p>功能描述：测试模型控制器（ModelController）的两个端点：获取可用模型列表和
 * 检查 Ollama 服务状态，验证接口响应格式和业务逻辑映射是否正确。</p>
 *
 * <p>测试策略：使用 @WebMvcTest 仅加载 Web 层，通过 @MockBean 替换 ModelService，
 * MockMvc 模拟 HTTP 请求。重点验证 Controller 层对 Service 返回结果的封装和转发。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>模型列表接口返回标准 ApiResponse 格式</li>
 *   <li>Ollama 状态检查接口返回可用性标志和模型列表</li>
 *   <li>所有请求均需携带 X-API-Key 请求头</li>
 * </ul>
 * </p>
 */
@WebMvcTest(ModelController.class)
class ModelControllerTest {

    /** 测试用 API Key */
    private static final String API_KEY = "ai-chat-default-key";

    /** MockMvc：模拟 HTTP 请求的测试工具 */
    @Autowired
    private MockMvc mockMvc;

    /** ModelService 的 Mock 替身，隔离 Service 层逻辑 */
    @MockBean
    private ModelService modelService;

    /**
     * 测试：获取可用模型列表，应返回模型数据
     *
     * <p>输入参数：GET /api/models，携带 X-API-Key</p>
     * <p>预期结果：HTTP 200，业务码 200</p>
     * <p>前置条件：Mock modelService.getAvailableModels() 返回空列表</p>
     * <p>验证逻辑：确认 Controller 正确调用 Service 层获取模型列表，
     * 并将结果封装为标准 ApiResponse 格式返回</p>
     */
    @Test
    void listModels_ShouldReturnModels() throws Exception {
        when(modelService.getAvailableModels()).thenReturn(List.of());

        mockMvc.perform(get("/api/models")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 测试：检查 Ollama 服务状态，应返回可用性标志和模型列表
     *
     * <p>输入参数：GET /api/models/status，携带 X-API-Key</p>
     * <p>预期结果：HTTP 200，业务码 200，data.ollamaAvailable=true</p>
     * <p>前置条件：Mock modelService.isOllamaAvailable() 返回 true，
     * modelService.getAvailableModels() 返回空列表</p>
     * <p>验证逻辑：确认 Controller 同时调用可用性检查和模型列表获取，
     * 将 Ollama 可用性标志和模型列表组合后返回给前端，
     * 前端可据此判断是否展示模型选择功能</p>
     */
    @Test
    void checkStatus_ShouldReturnOllamaStatus() throws Exception {
        when(modelService.isOllamaAvailable()).thenReturn(true);
        when(modelService.getAvailableModels()).thenReturn(List.of());

        mockMvc.perform(get("/api/models/status")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ollamaAvailable").value(true));
    }
}
