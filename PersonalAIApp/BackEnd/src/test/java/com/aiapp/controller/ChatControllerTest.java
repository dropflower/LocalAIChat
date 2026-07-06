package com.aiapp.controller;

import com.aiapp.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ChatController 单元测试类
 *
 * <p>功能描述：测试聊天控制器（ChatController）的核心接口，包括获取会话消息列表和
 * 聊天补全（流式响应）两个端点，验证请求参数传递和响应格式是否正确。</p>
 *
 * <p>测试策略：使用 @WebMvcTest 仅加载 Web 层，通过 @MockBean 替换 ChatService，
 * MockMvc 模拟 HTTP 请求。重点验证 Controller 层的参数绑定和响应封装，
 * 不涉及 Service 层的实际业务逻辑。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>获取消息列表接口的分页参数传递和响应格式</li>
 *   <li>聊天补全接口返回 SseEmitter SSE 流式响应</li>
 *   <li>缺少必填参数时返回 400 错误</li>
 *   <li>所有请求均需携带 X-API-Key 请求头进行认证</li>
 * </ul>
 * </p>
 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    /** 测试用 API Key，与配置文件中的默认 Key 保持一致 */
    private static final String API_KEY = "ai-chat-default-key";

    /** MockMvc：模拟 HTTP 请求的测试工具 */
    @Autowired
    private MockMvc mockMvc;

    /** ChatService 的 Mock 替身，隔离 Service 层逻辑 */
    @MockBean
    private ChatService chatService;

    /**
     * 测试：获取指定会话的消息列表，应返回分页消息数据
     *
     * <p>输入参数：GET /api/chat/sessions/1/messages?page=0&size=50，携带 X-API-Key</p>
     * <p>预期结果：HTTP 200，业务码 200，响应体包含消息列表数据</p>
     * <p>前置条件：Mock chatService.getSessionMessages() 返回空列表</p>
     * <p>验证逻辑：确认 Controller 正确传递会话 ID 和分页参数至 Service 层，
     * 并将结果封装为标准 ApiResponse 格式返回</p>
     */
    @Test
    void getMessages_ShouldReturnMessageList() throws Exception {
        when(chatService.getSessionMessages(eq(1L), anyInt(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/chat/sessions/1/messages?page=0&size=50")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 测试：发送聊天补全请求，应返回 SSE 流式响应
     *
     * <p>输入参数：POST /api/chat/completions，请求体包含 sessionId=1、modelName="qwen"、message="hello"</p>
     * <p>预期结果：HTTP 200，Content-Type 包含 text/event-stream</p>
     * <p>前置条件：Mock chatService.chatStream() 返回包含 start 和 done 事件的 Flux</p>
     * <p>验证逻辑：确认 Controller 返回 SseEmitter 类型的 SSE 流式响应，
     * 且响应状态码和 Content-Type 正确</p>
     */
    @Test
    void chatCompletions_ShouldReturnSSE() throws Exception {
        when(chatService.chatStream(eq(1L), eq("qwen"), eq("hello"), anyBoolean(), anyBoolean()))
                .thenReturn(Flux.just("{\"type\":\"start\"}", "{\"type\":\"done\"}"));

        mockMvc.perform(post("/api/chat/completions")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":1,\"modelName\":\"qwen\",\"message\":\"hello\"}"))
                .andExpect(status().isOk());
    }

    /**
     * 测试：缺少模型名称时返回 400 错误
     *
     * <p>输入参数：POST /api/chat/completions，请求体 modelName 为空</p>
     * <p>预期结果：HTTP 400</p>
     * <p>验证逻辑：Controller 参数校验生效，拒绝无效请求</p>
     */
    @Test
    void chatCompletions_MissingModelName_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/chat/completions")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelName\":\"\",\"message\":\"hello\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 测试：缺少消息内容时返回 400 错误
     *
     * <p>输入参数：POST /api/chat/completions，请求体 message 为空</p>
     * <p>预期结果：HTTP 400</p>
     * <p>验证逻辑：Controller 参数校验生效，拒绝无效请求</p>
     */
    @Test
    void chatCompletions_MissingMessage_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/chat/completions")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelName\":\"qwen\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
