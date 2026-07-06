package com.aiapp.interceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiKeyInterceptor 单元测试类
 *
 * <p>功能描述：测试 API Key 拦截器（ApiKeyInterceptor）的请求拦截逻辑，验证各类请求场景下
 * 拦截器的放行与拒绝行为，包括白名单路径免认证、OPTIONS 预检请求放行、
 * 有效/无效/缺失/空 API Key 的认证校验。</p>
 *
 * <p>测试策略：使用纯单元测试（非 Spring 上下文），手动构造 MockHttpServletRequest 和
 * MockHttpServletResponse，通过 ReflectionTestUtils 注入配置的 API Key 值，
 * 直接调用 preHandle 方法验证返回值和响应状态。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>健康检查端点 /api/health 免认证放行</li>
 *   <li>登录端点 /api/auth/login 免认证放行</li>
 *   <li>CORS 预检请求（OPTIONS 方法）无条件放行</li>
 *   <li>有效 API Key 请求正常放行（preHandle 返回 true）</li>
 *   <li>无效、缺失、空 API Key 请求被拒绝（preHandle 返回 false，HTTP 401）</li>
 * </ul>
 * </p>
 */
class ApiKeyInterceptorTest {

    /** 待测试的拦截器实例 */
    private ApiKeyInterceptor interceptor;

    /** 模拟的 HTTP 请求对象 */
    private MockHttpServletRequest request;

    /** 模拟的 HTTP 响应对象 */
    private MockHttpServletResponse response;

    /**
     * 测试前初始化：创建拦截器实例并通过反射注入配置的 API Key，
     * 创建 Mock 请求和响应对象
     */
    @BeforeEach
    void setUp() {
        interceptor = new ApiKeyInterceptor();
        // 通过反射注入配置的 API Key，模拟 application.yml 中的配置
        ReflectionTestUtils.setField(interceptor, "configuredApiKey", "test-api-key");
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    /**
     * 测试：健康检查端点应免认证放行
     *
     * <p>输入参数：请求 URI 为 /api/health</p>
     * <p>预期结果：preHandle 返回 true，请求继续执行</p>
     * <p>验证逻辑：确认拦截器对健康检查端点不做 API Key 校验，
     * 该端点用于监控系统探测服务可用性，无需认证</p>
     */
    @Test
    void preHandle_HealthEndpoint_ShouldReturnTrue() throws Exception {
        request.setRequestURI("/api/health");
        boolean result = interceptor.preHandle(request, response, null);
        assertTrue(result);
    }

    /**
     * 测试：登录端点应免认证放行
     *
     * <p>输入参数：请求 URI 为 /api/auth/login</p>
     * <p>预期结果：preHandle 返回 true，请求继续执行</p>
     * <p>验证逻辑：确认拦截器对登录端点不做 API Key 校验，
     * 因为登录本身就是获取认证的过程，此时尚无有效 Key</p>
     */
    @Test
    void preHandle_LoginEndpoint_ShouldReturnTrue() throws Exception {
        request.setRequestURI("/api/auth/login");
        boolean result = interceptor.preHandle(request, response, null);
        assertTrue(result);
    }

    /**
     * 测试：CORS 预检请求（OPTIONS 方法）应无条件放行
     *
     * <p>输入参数：请求方法为 OPTIONS，请求 URI 为 /api/sessions（需认证路径）</p>
     * <p>预期结果：preHandle 返回 true，请求继续执行</p>
     * <p>验证逻辑：确认浏览器发送的跨域预检请求不受 API Key 校验限制，
     * 否则前端跨域请求将被拦截导致功能不可用</p>
     */
    @Test
    void preHandle_OptionsMethod_ShouldReturnTrue() throws Exception {
        request.setMethod("OPTIONS");
        request.setRequestURI("/api/sessions");
        boolean result = interceptor.preHandle(request, response, null);
        assertTrue(result);
    }

    /**
     * 测试：携带有效 API Key 的请求应正常放行
     *
     * <p>输入参数：请求 URI 为 /api/sessions，X-API-Key 请求头为 "test-api-key"</p>
     * <p>预期结果：preHandle 返回 true，请求继续执行</p>
     * <p>验证逻辑：确认拦截器正确匹配请求头中的 API Key 与配置值，
     * 有效 Key 的请求应被放行至后续处理链</p>
     */
    @Test
    void preHandle_ValidApiKey_ShouldReturnTrue() throws Exception {
        request.setRequestURI("/api/sessions");
        request.addHeader("X-API-Key", "test-api-key");
        boolean result = interceptor.preHandle(request, response, null);
        assertTrue(result);
    }

    /**
     * 测试：携带无效 API Key 的请求应被拒绝并返回 401
     *
     * <p>输入参数：请求 URI 为 /api/sessions，X-API-Key 请求头为 "wrong-key"</p>
     * <p>预期结果：preHandle 返回 false，HTTP 响应状态码 401，
     * 响应体包含业务码 401 和 "API Key 无效" 提示</p>
     * <p>验证逻辑：确认拦截器识别不匹配的 API Key 并中断请求，
     * 同时返回标准错误响应供前端展示认证失败信息</p>
     */
    @Test
    void preHandle_InvalidApiKey_ShouldReturn401() throws Exception {
        request.setRequestURI("/api/sessions");
        request.addHeader("X-API-Key", "wrong-key");
        boolean result = interceptor.preHandle(request, response, null);
        assertFalse(result);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("401"));
        assertTrue(response.getContentAsString().contains("API Key 无效"));
    }

    /**
     * 测试：未携带 API Key 的请求应被拒绝并返回 401
     *
     * <p>输入参数：请求 URI 为 /api/sessions，无 X-API-Key 请求头</p>
     * <p>预期结果：preHandle 返回 false，HTTP 响应状态码 401</p>
     * <p>验证逻辑：确认拦截器对缺失认证头的请求进行拦截，
     * 该场景模拟前端未配置认证信息或请求头被代理过滤的情况</p>
     */
    @Test
    void preHandle_MissingApiKey_ShouldReturn401() throws Exception {
        request.setRequestURI("/api/sessions");
        boolean result = interceptor.preHandle(request, response, null);
        assertFalse(result);
        assertEquals(401, response.getStatus());
    }

    /**
     * 测试：携带空字符串 API Key 的请求应被拒绝并返回 401
     *
     * <p>输入参数：请求 URI 为 /api/sessions，X-API-Key 请求头为空字符串 ""</p>
     * <p>预期结果：preHandle 返回 false，HTTP 响应状态码 401</p>
     * <p>验证逻辑：确认空字符串被视为无效 Key，与缺失 Key 行为一致，
     * 防止前端传递空值绕过认证校验的边界攻击</p>
     */
    @Test
    void preHandle_EmptyApiKey_ShouldReturn401() throws Exception {
        request.setRequestURI("/api/sessions");
        request.addHeader("X-API-Key", "");
        boolean result = interceptor.preHandle(request, response, null);
        assertFalse(result);
        assertEquals(401, response.getStatus());
    }
}
