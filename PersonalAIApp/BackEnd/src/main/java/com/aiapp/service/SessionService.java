package com.aiapp.service;

import com.aiapp.model.Session;
import com.aiapp.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 会话管理服务
 *
 * ## 功能描述
 * 提供会话的 CRUD 操作，包括列表查询、搜索、创建、重命名、置顶切换等。
 * 会话的业务逻辑较简单，主要是对 SessionRepository 的封装。
 *
 * ## 核心方法
 * - listSessions()：分页查询会话列表，置顶优先 → 更新时间倒序
 * - searchSessions()：按标题模糊搜索会话
 * - getSession()：按 ID 查询单个会话
 * - createSession()：创建新会话
 * - updateTitle()：重命名会话标题
 * - togglePin()：切换会话置顶状态
 *
 * ## 事务说明
 * - updateTitle() 和 togglePin() 使用 @Transactional 确保原子性
 * - 先查询实体，修改属性后由 JPA 自动持久化（脏检查机制）
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

    /**
     * 分页查询会话列表
     * 排序规则：置顶优先 → 最后更新时间倒序
     *
     * @param page 页码（从 0 开始）
     * @param size 每页大小
     * @return 会话列表
     */
    public List<Session> listSessions(int page, int size) {
        return sessionRepository.findAllByOrderByIsPinnedDescUpdatedAtDesc(
                PageRequest.of(page, size));
    }

    /**
     * 按标题模糊搜索会话
     * @param keyword 搜索关键词
     * @param page    页码
     * @param size    每页大小
     * @return 匹配的会话列表
     */
    public List<Session> searchSessions(String keyword, int page, int size) {
        return sessionRepository.searchByTitle(keyword, PageRequest.of(page, size));
    }

    /**
     * 按 ID 查询单个会话
     * @param id 会话 ID
     * @return Optional 包装的会话对象
     */
    public Optional<Session> getSession(Long id) {
        return sessionRepository.findById(id);
    }

    /**
     * 创建新会话
     * @param session 会话对象
     * @return 保存后的会话（含自增 ID）
     */
    public Session createSession(Session session) {
        return sessionRepository.save(session);
    }

    /**
     * 重命名会话标题
     * 先查询确认会话存在，再更新标题
     *
     * @param id    会话 ID
     * @param title 新标题
     * @return 更新后的会话对象
     * @throws RuntimeException 如果会话不存在
     */
    @Transactional
    public Session updateTitle(Long id, String title) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + id));
        session.setTitle(title);
        return sessionRepository.save(session);
    }

    /**
     * 切换会话置顶状态
     * 置顶 → 取消置顶，非置顶 → 置顶
     *
     * @param id 会话 ID
     * @return 更新后的会话对象
     * @throws RuntimeException 如果会话不存在
     */
    @Transactional
    public Session togglePin(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + id));
        session.setIsPinned(!session.getIsPinned());
        return sessionRepository.save(session);
    }
}