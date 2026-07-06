package com.aiapp.service;

import com.aiapp.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 数据清理服务
 *
 * ## 功能描述
 * 定时清理过期数据，释放存储空间，保持数据库性能。
 *
 * ## 清理策略
 * 1. 过期会话清理：
 *    - 每天凌晨 3:00 执行（业务低峰期）
 *    - 删除 updatedAt 超过 N 天（默认 90 天）的会话
 *    - 删除会话时，关联的消息由外键 ON DELETE CASCADE 自动删除
 *    - 使用 JPQL 的 DELETE 语句，一次 SQL 完成批量删除，避免 N+1 问题
 *
 * ## 定时任务配置
 * - @Scheduled(cron = "0 0 3 * * ?")：每天凌晨 3:00:00 执行
 * - Cron 表达式格式：秒 分 时 日 月 星期
 * - 需要 @EnableScheduling 在主类上启用（已在 AiAppApplication 中配置）
 *
 * ## 注意事项
 * - 清理操作不可逆，删除的会话和消息无法恢复
 * - 清理前会记录日志，包含删除的会话数量
 * - 过期天数通过 app.chat.session-expire-days 配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final SessionRepository sessionRepository;

    @Value("${app.chat.session-expire-days}")
    private int sessionExpireDays;

    /**
     * 每天凌晨 3:00 执行过期会话清理任务
     *
     * 删除超过 sessionExpireDays 天未活动的会话及其所有消息。
     * 使用 @Transactional 确保批量删除的原子性。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredSessions() {
        log.info("开始清理过期会话...");
        LocalDateTime before = LocalDateTime.now().minusDays(sessionExpireDays);
        int deleted = sessionRepository.deleteByUpdatedAtBefore(before);
        log.info("清理完成，删除 {} 个过期会话", deleted);
    }
}