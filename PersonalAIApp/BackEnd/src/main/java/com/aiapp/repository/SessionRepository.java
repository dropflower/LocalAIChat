package com.aiapp.repository;

import com.aiapp.model.Session;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话数据访问层 — Session 实体 Repository
 *
 * ## 功能描述
 * 继承 Spring Data JPA 的 JpaRepository，提供 Session 实体的数据访问操作。
 * 包含基本的 CRUD 操作以及自定义查询方法。
 *
 * ## 自定义方法说明
 * - findAllByOrderByIsPinnedDescUpdatedAtDesc：分页查询会话列表
 *   排序规则：置顶优先 → 最后更新时间倒序
 * - deleteByUpdatedAtBefore：批量删除过期会话
 *   使用 JPQL 的原生删除，避免先查询再删除的性能开销
 *   需要 @Modifying 注解标识为写操作
 * - searchByTitle：按标题模糊搜索会话
 *   使用 JPQL LIKE 关键字实现模糊匹配
 *
 * ## 事务说明
 * - deleteByUpdatedAtBefore 需要 @Modifying 注解
 * - 调用方需自行添加 @Transactional 确保事务完整性
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    /**
     * 分页查询会话列表
     * 排序：置顶优先 → 最后更新时间倒序
     * @param pageable 分页参数
     * @return 会话列表
     */
    List<Session> findAllByOrderByIsPinnedDescUpdatedAtDesc(Pageable pageable);

    /**
     * 批量删除过期会话
     * 使用 JPQL 避免 N+1 问题，一次 SQL 完成批量删除
     *
     * @param before 截止时间，删除 updatedAt 早于此时间的所有会话
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM Session s WHERE s.updatedAt < :before")
    int deleteByUpdatedAtBefore(@Param("before") LocalDateTime before);

    /**
     * 按标题模糊搜索会话
     * @param keyword  搜索关键词
     * @param pageable 分页参数
     * @return 匹配的会话列表
     */
    @Query("SELECT s FROM Session s WHERE s.title LIKE %:keyword% ORDER BY s.updatedAt DESC")
    List<Session> searchByTitle(@Param("keyword") String keyword, Pageable pageable);
}