package com.aiapp.repository;

import com.aiapp.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageRepository 单元测试类
 *
 * <p>功能描述：测试消息数据访问层（MessageRepository）的自定义查询方法，验证消息的
 * 按时间排序查询、最近消息分页查询、消息计数、批量删除以及压缩内容的持久化等功能。</p>
 *
 * <p>测试策略：使用 @DataJpaTest 仅加载 JPA 相关组件，通过内嵌 H2 内存数据库替代 MySQL，
 * 使用 TestEntityManager 进行数据准备和验证。每个测试前通过原生 SQL 插入关联的 Session 记录，
 * 满足外键约束。</p>
 *
 * <p>关键验证点：
 * <ul>
 *   <li>findBySessionIdOrderByCreatedAtAsc：按创建时间升序返回消息</li>
 *   <li>findRecentMessages：按创建时间倒序返回最近 N 条消息</li>
 *   <li>countBySessionId：统计指定会话的消息数量</li>
 *   <li>deleteBySessionId：批量删除指定会话的所有消息</li>
 *   <li>压缩内容（contentCompressed）的二进制数据正确持久化和读取</li>
 * </ul>
 * </p>
 *
 * <p>特殊说明：使用 H2 内存数据库（MySQL 兼容模式）替代实际 MySQL，
 * 排除了 Redis 自动配置以避免连接依赖。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.sql.init.mode=never"
})
class MessageRepositoryTest {

    /** JPA 测试实体管理器，用于数据准备和直接查询验证 */
    @Autowired
    private TestEntityManager entityManager;

    /** 待测试的消息仓库实例 */
    @Autowired
    private MessageRepository messageRepository;

    /** 测试用会话 ID，在每个测试前通过原生 SQL 插入获取 */
    private Long sessionId;

    /**
     * 测试前初始化：通过原生 SQL 向 sc_session 表插入一条测试会话记录，
     * 获取其自增 ID 作为后续消息关联的外键值
     */
    @BeforeEach
    void setUp() {
        entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO sc_session (title, model_name, message_count, is_pinned) VALUES ('Test', 'qwen', 0, false)")
                .executeUpdate();
        Number id = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT id FROM sc_session WHERE title = 'Test'")
                .getSingleResult();
        sessionId = id.longValue();
    }

    /**
     * 测试：按创建时间升序查询消息，应返回正确排序的消息列表
     *
     * <p>输入参数：sessionId 对应的 3 条消息，按 10ms 间隔依次创建</p>
     * <p>预期结果：返回 3 条消息，按 createdAt 升序排列</p>
     * <p>验证逻辑：通过 Thread.sleep(10) 确保消息创建时间有可区分的间隔，
     * 验证排序逻辑的正确性。该排序保证聊天消息按时间顺序展示</p>
     */
    @Test
    void findBySessionIdOrderByCreatedAtAsc_ShouldReturnOrdered() throws InterruptedException {
        Message msg1 = createMessage("first");
        Message msg2 = createMessage("second");
        Message msg3 = createMessage("third");
        entityManager.persist(msg1);
        Thread.sleep(10);
        entityManager.persist(msg2);
        Thread.sleep(10);
        entityManager.persist(msg3);
        entityManager.flush();

        List<Message> result = messageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId);

        assertEquals(3, result.size());
        assertTrue(result.get(0).getCreatedAt().isBefore(result.get(1).getCreatedAt()));
        assertTrue(result.get(1).getCreatedAt().isBefore(result.get(2).getCreatedAt()));
    }

    /**
     * 测试：查询最近消息，应按时间倒序返回指定数量的消息
     *
     * <p>输入参数：sessionId 对应的 5 条消息，分页请求 PageRequest.of(0, 3)</p>
     * <p>预期结果：返回 3 条消息（最近3条），按 createdAt 倒序排列</p>
     * <p>验证逻辑：创建 5 条消息后查询最近 3 条，确认返回数量和倒序排列。
     * 该方法用于滑动窗口上下文构建，获取最近对话历史</p>
     */
    @Test
    void findRecentMessages_ShouldReturnMostRecentFirst() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            entityManager.persist(createMessage("msg" + i));
            Thread.sleep(5);
        }
        entityManager.flush();

        List<Message> result = messageRepository
                .findRecentMessages(sessionId, PageRequest.of(0, 3));

        assertEquals(3, result.size());
        assertTrue(result.get(0).getCreatedAt().isAfter(result.get(1).getCreatedAt()));
    }

    /**
     * 测试：统计指定会话的消息数量，应返回正确计数
     *
     * <p>输入参数：sessionId 对应的 3 条消息</p>
     * <p>预期结果：countBySessionId 返回 3</p>
     * <p>验证逻辑：确认消息计数与实际插入数量一致，
     * 该方法用于滑动窗口判断是否需要截断历史消息</p>
     */
    @Test
    void countBySessionId_ShouldReturnCorrectCount() {
        entityManager.persist(createMessage("a"));
        entityManager.persist(createMessage("b"));
        entityManager.persist(createMessage("c"));
        entityManager.flush();

        int count = messageRepository.countBySessionId(sessionId);
        assertEquals(3, count);
    }

    /**
     * 测试：按会话 ID 批量删除消息，应删除该会话下所有消息
     *
     * <p>输入参数：sessionId 对应的 2 条消息</p>
     * <p>预期结果：deleteBySessionId 执行后，countBySessionId 返回 0</p>
     * <p>验证逻辑：确认删除操作清理了该会话下的全部消息，
     * 该方法在删除会话时被调用，确保级联清理消息数据</p>
     */
    @Test
    void deleteBySessionId_ShouldDeleteAllMessages() {
        entityManager.persist(createMessage("a"));
        entityManager.persist(createMessage("b"));
        entityManager.flush();

        messageRepository.deleteBySessionId(sessionId);
        entityManager.flush();

        int count = messageRepository.countBySessionId(sessionId);
        assertEquals(0, count);
    }

    /**
     * 测试：压缩内容的二进制数据应正确持久化和读取
     *
     * <p>输入参数：contentCompressed = "test content".getBytes(UTF-8)，
     * contentLength = 12，tokenCount = 5</p>
     * <p>预期结果：从数据库读取的 contentCompressed 与写入值一致，
     * tokenCount 也正确持久化</p>
     * <p>验证逻辑：确认消息的压缩内容字段（BLOB 类型）能正确保存和读取二进制数据，
     * 该字段存储 GZIP 压缩后的消息文本，是消息存储的核心字段</p>
     */
    @Test
    void saveAndRetrieve_CompressedContent_ShouldPersist() {
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
        Message msg = Message.builder()
                .sessionId(sessionId)
                .role(Message.MessageRole.user)
                .contentCompressed(content)
                .contentLength(content.length)
                .tokenCount(5)
                .build();
        Message saved = messageRepository.save(msg);

        Message retrieved = entityManager.find(Message.class, saved.getId());
        assertNotNull(retrieved);
        assertArrayEquals(content, retrieved.getContentCompressed());
        assertEquals(5, retrieved.getTokenCount());
    }

    /**
     * 辅助方法：创建测试用 Message 对象
     *
     * @param content 消息文本内容，将被转为 UTF-8 字节存入 contentCompressed
     * @return 构建好的 Message 对象（含 sessionId、role、压缩内容、长度、token 数）
     */
    private Message createMessage(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return Message.builder()
                .sessionId(sessionId)
                .role(Message.MessageRole.user)
                .contentCompressed(bytes)
                .contentLength(bytes.length)
                .tokenCount(1)
                .build();
    }
}
