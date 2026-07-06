package com.aiapp.controller;

import com.aiapp.model.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HealthController 单元测试类
 *
 * <p>功能描述：测试健康检查控制器（HealthController）的 /api/health 端点，
 * 验证服务健康状态探测接口返回正确的服务标识和运行状态信息。</p>
 *
 * <p>测试策略：使用 @WebMvcTest 仅加载 Web 层，通过 MockMvc 发送 GET 请求，
 * 验证响应 JSON 中的业务码、服务状态和服务名称字段。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>健康检查端点无需 API Key 认证即可访问</li>
 *   <li>返回业务码 200 表示服务正常</li>
 *   <li>data.status 为 "ok"，data.service 为 "ai-chat-backend"</li>
 * </ul>
 * </p>
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    /** MockMvc：模拟 HTTP 请求的测试工具 */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 测试：访问健康检查端点，应返回服务正常运行信息
     *
     * <p>输入参数：GET /api/health，无需认证头</p>
     * <p>预期结果：HTTP 200，业务码 200，data.status="ok"，data.service="ai-chat-backend"</p>
     * <p>验证逻辑：确认健康检查端点返回标准响应格式，包含服务名称和运行状态，
     * 该接口通常用于负载均衡器或监控系统探测服务可用性</p>
     */
    @Test
    void health_ShouldReturn200() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.service").value("ai-chat-backend"));
    }
}
