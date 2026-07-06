package com.aiapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应体封装
 *
 * ## 功能描述
 * 所有 Controller 接口统一使用此泛型类封装响应数据，
 * 确保前端接收到的 JSON 格式一致，便于统一处理。
 *
 * ## 响应格式
 * {
 *   "code": 200,           // 业务状态码，200 成功，4xx 客户端错误，5xx 服务端错误
 *   "message": "success",  // 提示信息
 *   "data": {...},         // 响应数据（泛型）
 *   "timestamp": 1719561600000  // 响应时间戳（毫秒）
 * }
 *
 * ## 静态工厂方法
 * - success(T data)：快速创建成功响应，code=200, message="success"
 * - success(T data, String message)：成功响应 + 自定义消息
 * - error(int code, String message)：错误响应，无 data 字段
 *
 * ## 使用示例
 * return ApiResponse.success(sessionList);                     // 成功
 * return ApiResponse.error(401, "API Key 无效");               // 错误
 *
 * @param <T> 响应数据的类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 业务状态码，默认 200 */
    @Builder.Default
    private int code = 200;

    /** 提示信息，默认 "success" */
    @Builder.Default
    private String message = "success";

    /** 响应数据，泛型 */
    private T data;

    /** 响应时间戳（毫秒） */
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    /**
     * 创建成功响应
     * @param data 响应数据
     * @return ApiResponse 实例
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder().code(200).message("success").data(data).build();
    }

    /**
     * 创建带自定义消息的成功响应
     * @param data    响应数据
     * @param message 自定义消息
     * @return ApiResponse 实例
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder().code(200).message(message).data(data).build();
    }

    /**
     * 创建错误响应
     * @param code    错误状态码
     * @param message 错误消息
     * @return ApiResponse 实例（data 为 null）
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder().code(code).message(message).build();
    }
}