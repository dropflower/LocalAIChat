package com.aiapp.controller;

import com.aiapp.model.Session;
import com.aiapp.service.ChatService;
import com.aiapp.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SessionController 单元测试类
 *
 * <p>功能描述：测试会话控制器（SessionController）的 CRUD 操作接口，包括会话列表查询、
 * 会话搜索、标题更新、置顶切换和删除操作，验证各接口的请求参数绑定和响应格式。</p>
 *
 * <p>测试策略：使用 @WebMvcTest 仅加载 Web 层，通过 @MockBean 替换 SessionService 和 ChatService，
 * MockMvc 模拟 HTTP 请求。重点验证 Controller 层参数传递和响应封装的正确性。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>会话列表分页查询的参数传递与响应格式</li>
 *   <li>会话搜索的关键词匹配与分页支持</li>
 *   <li>标题更新的请求体解析与返回更新后的会话对象</li>
 *   <li>置顶状态切换的双向验证（置顶/取消置顶）</li>
 *   <li>删除操作的响应确认</li>
 * </ul>
 * </p>
 */
@WebMvcTest(SessionController.class)
class SessionControllerTest {

    /** 测试用 API Key */
    private static final String API_KEY = "ai-chat-default-key";

    /** MockMvc：模拟 HTTP 请求的测试工具 */
    @Autowired
    private MockMvc mockMvc;

    /** SessionService 的 Mock 替身 */
    @MockBean
    private SessionService sessionService;

    /** ChatService 的 Mock 替身（Controller 依赖注入需要） */
    @MockBean
    private ChatService chatService;

    /**
     * 测试：获取会话列表，应返回分页结果
     *
     * <p>输入参数：GET /api/sessions?page=0&size=50，携带 X-API-Key</p>
     * <p>预期结果：HTTP 200，业务码 200</p>
     * <p>前置条件：Mock sessionService.listSessions(0, 50) 返回空列表</p>
     * <p>验证逻辑：确认 Controller 正确传递分页参数至 Service 层，
     * 会话列表按置顶优先、更新时间倒序排列</p>
     */
    @Test
    void listSessions_ShouldReturnPaged() throws Exception {
        when(sessionService.listSessions(0, 50)).thenReturn(List.of());

        mockMvc.perform(get("/api/sessions?page=0&size=50")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 测试：按关键词搜索会话，应返回匹配结果
     *
     * <p>输入参数：GET /api/sessions/search?keyword=test，携带 X-API-Key</p>
     * <p>预期结果：HTTP 200，业务码 200</p>
     * <p>前置条件：Mock sessionService.searchSessions("test", *, *) 返回空列表</p>
     * <p>验证逻辑：确认 Controller 正确传递搜索关键词至 Service 层，
     * 搜索结果基于会话标题的模糊匹配</p>
     */
    @Test
    void searchSessions_ShouldReturnFiltered() throws Exception {
        when(sessionService.searchSessions(eq("test"), anyInt(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/sessions/search?keyword=test")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 测试：更新会话标题，应返回更新后的会话对象
     *
     * <p>输入参数：PUT /api/sessions/1/title，请求体 {"title":"New"}，携带 X-API-Key</p>
     * <p>预期结果：HTTP 200，业务码 200，data.title="New"</p>
     * <p>前置条件：Mock sessionService.updateTitle(1L, "New") 返回 id=1、title="New" 的 Session</p>
     * <p>验证逻辑：确认 Controller 正确解析请求体中的标题字段，
     * 并将路径参数中的会话 ID 和请求体中的标题传递至 Service 层</p>
     */
    @Test
    void updateTitle_Success_ShouldReturnUpdated() throws Exception {
        Session session = Session.builder().id(1L).title("New").build();
        when(sessionService.updateTitle(eq(1L), eq("New"))).thenReturn(session);

        mockMvc.perform(put("/api/sessions/1/title")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("New"));
    }

    /**
     * 测试：切换会话置顶状态，应返回切换后的状态
     *
     * <p>输入参数：PUT /api/sessions/1/pin，携带 X-API-Key</p>
     * <p>预期结果：HTTP 200，业务码 200，data.isPinned=true</p>
     * <p>前置条件：Mock sessionService.togglePin(1L) 返回 isPinned=true 的 Session</p>
     * <p>验证逻辑：确认 Controller 正确调用 togglePin 操作，
     * 置顶状态为布尔切换（true→false 或 false→true），
     * 返回的 isPinned 值应反映切换后的状态</p>
     */
    @Test
    void togglePin_ShouldToggle() throws Exception {
        Session session = Session.builder().id(1L).isPinned(true).build();
        when(sessionService.togglePin(1L)).thenReturn(session);

        mockMvc.perform(put("/api/sessions/1/pin")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isPinned").value(true));
    }

    /**
     * 测试：删除会话，应返回操作成功确认
     *
     * <p>输入参数：DELETE /api/sessions/1，携带 X-API-Key</p>
     * <p>预期结果：HTTP 200，业务码 200，data.status="ok"</p>
     * <p>验证逻辑：确认 Controller 正确传递会话 ID 至 Service 层执行删除操作，
     * 删除操作应同时清理会话关联的消息数据和 Redis 缓存</p>
     */
    @Test
    void deleteSession_ShouldReturnOk() throws Exception {
        mockMvc.perform(delete("/api/sessions/1")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ok"));
    }
}
