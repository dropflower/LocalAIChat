package com.aiapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 单元测试类
 *
 * <p>功能描述：测试认证控制器（AuthController）的登录接口，验证 API Key 认证的各种场景，
 * 包括有效 Key 认证成功、无效 Key 认证失败、缺少 Key 以及空 Key 等边界情况。</p>
 *
 * <p>测试策略：使用 @WebMvcTest 仅加载 Web 层，通过 MockMvc 模拟 HTTP 请求，
 * 验证响应状态码和 JSON 返回体中的业务码及消息内容。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>有效 API Key 应返回 200 业务码及认证成功信息</li>
 *   <li>无效、缺失、空 API Key 均应返回 401 业务码</li>
 *   <li>HTTP 状态码始终为 200，业务状态通过 JSON 中的 code 字段区分</li>
 * </ul>
 * </p>
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    /** MockMvc：模拟 HTTP 请求的测试工具，无需启动完整服务器 */
    @Autowired
    private MockMvc mockMvc;

    /** ObjectMapper：用于 JSON 序列化/反序列化，由 Spring 自动注入 */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试：使用有效 API Key 登录，应返回认证成功响应
     *
     * <p>输入参数：POST /api/auth/login，请求体包含正确的 API Key "ai-chat-default-key"</p>
     * <p>预期结果：HTTP 200，业务码 200，data.status 为 "ok"，data.message 为 "认证成功"</p>
     * <p>验证逻辑：确认有效 Key 能够通过认证，返回标准成功响应结构</p>
     */
    @Test
    void login_ValidApiKey_ShouldReturn200() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"ai-chat-default-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.message").value("认证成功"));
    }

    /**
     * 测试：使用无效 API Key 登录，应返回认证失败响应
     *
     * <p>输入参数：POST /api/auth/login，请求体包含错误的 API Key "wrong"</p>
     * <p>预期结果：HTTP 200，业务码 401，message 为 "API Key 无效"</p>
     * <p>验证逻辑：确认错误 Key 被正确识别并返回 401 未授权业务码</p>
     */
    @Test
    void login_InvalidApiKey_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("API Key 无效"));
    }

    /**
     * 测试：请求体中缺少 apiKey 字段，应返回认证失败响应
     *
     * <p>输入参数：POST /api/auth/login，请求体为空 JSON 对象 "{}"</p>
     * <p>预期结果：HTTP 200，业务码 401</p>
     * <p>验证逻辑：确认缺失 apiKey 字段时服务端正确拒绝认证，
     * 该场景模拟前端未传递认证信息的边界情况</p>
     */
    @Test
    void login_MissingApiKey_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    /**
     * 测试：apiKey 字段为空字符串，应返回认证失败响应
     *
     * <p>输入参数：POST /api/auth/login，请求体中 apiKey 为空字符串 ""</p>
     * <p>预期结果：HTTP 200，业务码 401</p>
     * <p>验证逻辑：确认空字符串被视为无效 Key，与缺失字段行为一致，
     * 防止前端仅传递空值绕过认证校验</p>
     */
    @Test
    void login_EmptyApiKey_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
