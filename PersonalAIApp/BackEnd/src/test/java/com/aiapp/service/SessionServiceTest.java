package com.aiapp.service;

import com.aiapp.model.Session;
import com.aiapp.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SessionService 单元测试类
 *
 * <p>功能描述：测试会话服务（SessionService）的业务逻辑，包括会话列表查询、搜索、
 * 获取单个会话、创建会话、更新标题和置顶状态切换等核心操作。</p>
 *
 * <p>测试策略：使用 @ExtendWith(MockitoExtension.class) 进行纯 Mockito 单元测试，
 * 通过 @Mock 替换 SessionRepository，@InjectMocks 自动创建 SessionService 实例。
 * 重点验证 Service 层的业务逻辑处理和异常抛出行为。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>listSessions：分页查询会话列表</li>
 *   <li>searchSessions：按标题模糊搜索</li>
 *   <li>getSession：存在/不存在的会话查询</li>
 *   <li>createSession：创建并保存新会话</li>
 *   <li>updateTitle：成功更新 / 会话不存在抛异常</li>
 *   <li>togglePin：双向切换（false→true / true→false） / 会话不存在抛异常</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    /** SessionRepository 的 Mock 替身 */
    @Mock
    private SessionRepository sessionRepository;

    /** 自动注入 Mock 依赖的 SessionService 实例 */
    @InjectMocks
    private SessionService sessionService;

    /**
     * 测试：分页查询会话列表，应返回正确的结果和分页参数
     *
     * <p>输入参数：page=0，size=10</p>
     * <p>预期结果：返回 1 条会话，sessionRepository 使用 PageRequest.of(0, 10) 查询</p>
     * <p>验证逻辑：确认分页参数正确传递至 Repository 层，
     * 会话列表按置顶优先、更新时间倒序排列</p>
     */
    @Test
    void listSessions_ShouldReturnPagedResults() {
        List<Session> sessions = List.of(new Session());
        when(sessionRepository.findAllByOrderByIsPinnedDescUpdatedAtDesc(any(Pageable.class)))
                .thenReturn(sessions);

        List<Session> result = sessionService.listSessions(0, 10);

        assertEquals(1, result.size());
        verify(sessionRepository).findAllByOrderByIsPinnedDescUpdatedAtDesc(PageRequest.of(0, 10));
    }

    /**
     * 测试：按标题搜索会话，应返回匹配结果
     *
     * <p>输入参数：keyword="test"，page=0，size=20</p>
     * <p>预期结果：返回 1 条匹配会话，Repository 使用关键词和分页参数查询</p>
     * <p>验证逻辑：确认搜索关键词和分页参数正确传递至 Repository 层</p>
     */
    @Test
    void searchSessions_ShouldReturnMatchingSessions() {
        List<Session> sessions = List.of(new Session());
        when(sessionRepository.searchByTitle(eq("test"), any(Pageable.class))).thenReturn(sessions);

        List<Session> result = sessionService.searchSessions("test", 0, 20);

        assertEquals(1, result.size());
        verify(sessionRepository).searchByTitle("test", PageRequest.of(0, 20));
    }

    /**
     * 测试：获取存在的会话，应返回 Optional 包含该会话
     *
     * <p>输入参数：sessionId=1L</p>
     * <p>预期结果：Optional 有值，会话 ID 为 1</p>
     * <p>验证逻辑：确认存在的会话能被正确查询返回</p>
     */
    @Test
    void getSession_Existing_ShouldReturnSession() {
        Session session = new Session();
        session.setId(1L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        Optional<Session> result = sessionService.getSession(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    /**
     * 测试：获取不存在的会话，应返回空 Optional
     *
     * <p>输入参数：sessionId=999L（不存在）</p>
     * <p>预期结果：Optional.empty()</p>
     * <p>验证逻辑：确认不存在的会话查询返回空值而非抛异常，
     * 调用方可通过 isPresent() 安全判断</p>
     */
    @Test
    void getSession_NotFound_ShouldReturnEmpty() {
        when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Session> result = sessionService.getSession(999L);

        assertTrue(result.isEmpty());
    }

    /**
     * 测试：创建会话，应保存并返回带 ID 的会话对象
     *
     * <p>输入参数：title="Test"，modelName="qwen" 的 Session 对象</p>
     * <p>预期结果：返回 id=1L 的 Session，sessionRepository.save() 被调用</p>
     * <p>验证逻辑：确认新会话被正确保存，返回值包含数据库生成的 ID</p>
     */
    @Test
    void createSession_ShouldSaveAndReturn() {
        Session session = Session.builder().title("Test").modelName("qwen").build();
        Session saved = Session.builder().id(1L).title("Test").modelName("qwen").build();
        when(sessionRepository.save(session)).thenReturn(saved);

        Session result = sessionService.createSession(session);

        assertEquals(1L, result.getId());
        verify(sessionRepository).save(session);
    }

    /**
     * 测试：更新会话标题成功，应修改标题并保存
     *
     * <p>输入参数：sessionId=1L，newTitle="New Title"</p>
     * <p>预期结果：返回的 Session title="New Title"，sessionRepository.save() 被调用</p>
     * <p>前置条件：Mock sessionRepository.findById(1L) 返回已有会话</p>
     * <p>验证逻辑：确认标题更新逻辑正确：查找会话→修改标题→保存</p>
     */
    @Test
    void updateTitle_Success_ShouldUpdateTitle() {
        Session session = Session.builder().id(1L).title("Old").build();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenReturn(session);

        Session result = sessionService.updateTitle(1L, "New Title");

        assertEquals("New Title", result.getTitle());
        verify(sessionRepository).save(session);
    }

    /**
     * 测试：更新不存在会话的标题，应抛出包含"会话不存在"的 RuntimeException
     *
     * <p>输入参数：sessionId=999L（不存在），newTitle="New"</p>
     * <p>预期结果：抛出 RuntimeException，消息包含"会话不存在"和"999"</p>
     * <p>前置条件：Mock sessionRepository.findById(999L) 返回 Optional.empty()</p>
     * <p>验证逻辑：确认对不存在会话的操作正确抛出异常，
     * 异常信息包含会话 ID 以便排查问题</p>
     */
    @Test
    void updateTitle_NotFound_ShouldThrow() {
        when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> sessionService.updateTitle(999L, "New"));

        assertTrue(ex.getMessage().contains("会话不存在"));
        assertTrue(ex.getMessage().contains("999"));
    }

    /**
     * 测试：置顶状态从 false 切换为 true
     *
     * <p>输入参数：sessionId=1L，当前 isPinned=false</p>
     * <p>预期结果：返回的 Session isPinned=true</p>
     * <p>前置条件：Mock sessionRepository.findById(1L) 返回未置顶会话</p>
     * <p>验证逻辑：确认 togglePin 方法正确执行布尔取反操作，
     * 未置顶会话变为已置顶</p>
     */
    @Test
    void togglePin_FromFalse_ShouldBecomeTrue() {
        Session session = Session.builder().id(1L).isPinned(false).build();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenReturn(session);

        Session result = sessionService.togglePin(1L);

        assertTrue(result.getIsPinned());
        verify(sessionRepository).save(session);
    }

    /**
     * 测试：置顶状态从 true 切换为 false
     *
     * <p>输入参数：sessionId=1L，当前 isPinned=true</p>
     * <p>预期结果：返回的 Session isPinned=false</p>
     * <p>前置条件：Mock sessionRepository.findById(1L) 返回已置顶会话</p>
     * <p>验证逻辑：确认 togglePin 方法的双向切换能力，
     * 已置顶会话变为未置顶（取消置顶）</p>
     */
    @Test
    void togglePin_FromTrue_ShouldBecomeFalse() {
        Session session = Session.builder().id(1L).isPinned(true).build();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenReturn(session);

        Session result = sessionService.togglePin(1L);

        assertFalse(result.getIsPinned());
        verify(sessionRepository).save(session);
    }

    /**
     * 测试：对不存在的会话切换置顶状态，应抛出异常
     *
     * <p>输入参数：sessionId=999L（不存在）</p>
     * <p>预期结果：抛出 RuntimeException，消息包含"会话不存在"</p>
     * <p>前置条件：Mock sessionRepository.findById(999L) 返回 Optional.empty()</p>
     * <p>验证逻辑：确认对不存在会话的操作抛出业务异常，
     * 与 updateTitle 行为保持一致</p>
     */
    @Test
    void togglePin_NotFound_ShouldThrow() {
        when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> sessionService.togglePin(999L));

        assertTrue(ex.getMessage().contains("会话不存在"));
    }
}
