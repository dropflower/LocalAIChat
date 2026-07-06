package com.aiapp.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiResponse 单元测试类
 *
 * <p>功能描述：测试统一响应封装类（ApiResponse）的工厂方法和构建器行为，
 * 验证成功响应、错误响应及 Builder 模式的默认值设置是否符合预期。</p>
 *
 * <p>测试策略：纯单元测试，直接调用 ApiResponse 的静态工厂方法和 Builder 构建实例，
 * 通过断言验证各字段的正确性，不依赖 Spring 上下文。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>success() 工厂方法正确设置 code=200、message="success"、data 和 timestamp</li>
 *   <li>success() 带自定义消息方法覆盖默认 message</li>
 *   <li>error() 工厂方法正确设置 code、message，data 为 null</li>
 *   <li>Builder 模式构建时默认值为 code=200、message="success"、自动生成 timestamp</li>
 * </ul>
 * </p>
 */
class ApiResponseTest {

    /**
     * 测试：success() 工厂方法应正确设置所有字段
     *
     * <p>输入参数：data = "data"</p>
     * <p>预期结果：code=200，message="success"，data="data"，timestamp > 0</p>
     * <p>验证逻辑：确认标准成功响应的默认值设置，包括业务码、消息、数据和时间戳</p>
     */
    @Test
    void success_ShouldSetCode200AndMessage() {
        ApiResponse<String> response = ApiResponse.success("data");

        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals("data", response.getData());
        assertTrue(response.getTimestamp() > 0);
    }

    /**
     * 测试：success() 带自定义消息应覆盖默认 message
     *
     * <p>输入参数：data = "data"，自定义消息 = "OK"</p>
     * <p>预期结果：code=200，message="OK"，data="data"</p>
     * <p>验证逻辑：确认自定义消息参数能覆盖默认的 "success" 消息，
     * 适用于需要返回特定成功提示的场景</p>
     */
    @Test
    void success_WithCustomMessage_ShouldSetMessage() {
        ApiResponse<String> response = ApiResponse.success("data", "OK");

        assertEquals(200, response.getCode());
        assertEquals("OK", response.getMessage());
        assertEquals("data", response.getData());
    }

    /**
     * 测试：error() 工厂方法应正确设置错误码和消息，data 为 null
     *
     * <p>输入参数：code = 401，message = "Unauthorized"</p>
     * <p>预期结果：code=401，message="Unauthorized"，data=null</p>
     * <p>验证逻辑：确认错误响应不携带 data 数据，仅通过 code 和 message 传达错误信息，
     * 防止在错误场景下意外泄露敏感数据</p>
     */
    @Test
    void error_ShouldSetCodeAndMessage_AndDataNull() {
        ApiResponse<String> response = ApiResponse.error(401, "Unauthorized");

        assertEquals(401, response.getCode());
        assertEquals("Unauthorized", response.getMessage());
        assertNull(response.getData());
    }

    /**
     * 测试：Builder 模式构建应设置默认值
     *
     * <p>输入参数：无显式参数，使用 Builder 默认值</p>
     * <p>预期结果：code=200，message="success"，timestamp > 0</p>
     * <p>验证逻辑：确认 Builder 的 @Builder.Default 注解生效，
     * 未显式设置的字段自动填充为合理的默认值，
     * 避免构建出字段缺失的不完整响应对象</p>
     */
    @Test
    void builder_ShouldSetDefaults() {
        ApiResponse<String> response = ApiResponse.<String>builder().build();

        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
        assertTrue(response.getTimestamp() > 0);
    }
}
