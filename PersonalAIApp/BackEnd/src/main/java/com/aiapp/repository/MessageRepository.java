package com.aiapp.repository;

import com.aiapp.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息数据访问层 — Message 实体 Repository
 *
 * ## 功能描述
 * 提供 Message 实体的数据访问操作，包括按会话查询消息历史、
 * 统计消息数量、获取最近消息等。
 *
 * ## 自定义方法说明
 * - findBySessionIdOrderByCreatedAtAsc：按会话 ID 查询全部消息
 *   按创建时间升序排列，用于构建对话上下文
 * - findRecentMessages：查询指定会话的最近 N 条消息
 *   按创建时间倒序，配合 Pageable 限制条数
 * - countBySessionId：统计指定会话的消息总数
 *   用于更新 Session 的 messageCount 字段
 * - deleteBySessionId：删除指定会话的全部消息
 *   在删除会话时调用，由外键 CASCADE 自动处理，此方法作为显式调用备份
 *
 * ## 性能考虑
 * - findBySessionIdOrderByCreatedAtAsc 返回全量消息，可能影响性能
 *   对于超长会话（>2000 条消息），应使用分页查询
 * - 索引 idx_session_time 覆盖 (session_id, created_at) 组合查询
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * 按会话 ID 查询全部消息，按创建时间升序
     * 用于构建对话上下文窗口
     *
     * @param sessionId 会话 ID
     * @return 消息列表（按时间升序）
     */
    List<Message> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /**
     * 查询指定会话的最近 N 条消息（按时间倒序）
     * @param sessionId 会话 ID
     * @param pageable  分页参数（控制返回条数）
     * @return 消息列表（按时间倒序）
     */
    @Query("SELECT m FROM Message m WHERE m.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<Message> findRecentMessages(@Param("sessionId") Long sessionId, Pageable pageable);

    /**
     * 统计指定会话的消息总数
     * @param sessionId 会话 ID
     * @return 消息数量
     */
    int countBySessionId(Long sessionId);

    /**
     * 删除指定会话的全部消息
     * @param sessionId 会话 ID
     */
    void deleteBySessionId(Long sessionId);
}