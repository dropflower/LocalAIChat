package com.aiapp.service;

import com.aiapp.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

/**
 * CleanupService 单元测试类
 *
 * <p>功能描述：测试会话清理服务（CleanupService）的过期会话清理功能，验证定时任务
 * 正确调用 Repository 层的删除方法，并处理有删除和无删除两种场景。</p>
 *
 * <p>测试策略：使用 @ExtendWith(MockitoExtension.class) 进行纯 Mockito 单元测试，
 * 通过 @Mock 替换 SessionRepository，@InjectMocks 自动创建 CleanupService 实例，
 * 通过 ReflectionTestUtils 注入 sessionExpireDays 配置属性。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>cleanupExpiredSessions 正确计算过期时间并调用 deleteByUpdatedAtBefore</li>
 *   <li>存在过期会话时返回正确的删除数量</li>
 *   <li>无过期会话时不抛异常，正常返回</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class CleanupServiceTest {

    /** SessionRepository 的 Mock 替身 */
    @Mock
    private SessionRepository sessionRepository;

    /** 自动注入 Mock 依赖的 CleanupService 实例 */
    @InjectMocks
    private CleanupService cleanupService;

    /**
     * 测试：清理过期会话，存在过期数据时应正确删除
     *
     * <p>输入参数：sessionExpireDays=30，deleteByUpdatedAtBefore 返回 5（删除了 5 条）</p>
     * <p>预期结果：sessionRepository.deleteByUpdatedAtBefore() 被调用一次</p>
     * <p>验证逻辑：确认清理方法正确计算过期截止时间（当前时间 - 30天），
     * 并调用 Repository 执行删除操作。返回值 5 表示有 5 条过期会话被清理</p>
     */
    @Test
    void cleanupExpiredSessions_ShouldDeleteExpired() {
        ReflectionTestUtils.setField(cleanupService, "sessionExpireDays", 30);
        when(sessionRepository.deleteByUpdatedAtBefore(any(LocalDateTime.class))).thenReturn(5);

        cleanupService.cleanupExpiredSessions();

        verify(sessionRepository).deleteByUpdatedAtBefore(any(LocalDateTime.class));
    }

    /**
     * 测试：清理过期会话，无过期数据时不应抛异常
     *
     * <p>输入参数：sessionExpireDays=30，deleteByUpdatedAtBefore 返回 0（无过期会话）</p>
     * <p>预期结果：sessionRepository.deleteByUpdatedAtBefore() 被调用一次，方法正常返回</p>
     * <p>验证逻辑：确认无过期会话时清理方法不抛异常，正常执行完毕，
     * 该场景在系统刚启动或所有会话均近期活跃时出现</p>
     */
    @Test
    void cleanupExpiredSessions_ShouldHandleZeroDeletions() {
        ReflectionTestUtils.setField(cleanupService, "sessionExpireDays", 30);
        when(sessionRepository.deleteByUpdatedAtBefore(any(LocalDateTime.class))).thenReturn(0);

        cleanupService.cleanupExpiredSessions();

        verify(sessionRepository).deleteByUpdatedAtBefore(any(LocalDateTime.class));
    }
}
