package com.aiapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 消息实体类 — 映射 sc_message 表
 *
 * ## 功能描述
 * 代表对话中的一条消息（用户消息或 AI 回复）。
 * 消息内容使用 GZIP 压缩后存储为 MEDIUMBLOB，以节省存储空间。
 *
 * ## 字段说明
 * - id：主键，自增
 * - sessionId：所属会话 ID（逻辑外键，关联 sc_session.id）
 * - role：消息角色，枚举值 user/assistant/system
 * - contentCompressed：GZIP 压缩后的消息内容（MEDIUMBLOB）
 * - contentLength：原始内容长度（字节），用于统计和解压校验
 * - tokenCount：估算的 Token 数量，用于统计用量
 * - createdAt：创建时间，由 @PrePersist 自动设置
 *
 * ## 压缩策略
 * 使用 GZIP 压缩原始文本内容后存入数据库：
 * - 文本类的压缩率约 70-80%，显著减少存储占用
 * - 读取时通过 ChatService.decompress() 解压为原始文本
 * - 如果解压失败（如存储时未压缩），直接返回原始字节作为字符串
 *
 * ## 关联关系
 * - 与 Session 为多对一关系（通过 sessionId 关联）
 * - 删除会话时，关联消息由数据库外键 ON DELETE CASCADE 自动删除
 */
@Entity
@Table(name = "sc_message")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('user','assistant','system')")
    private MessageRole role;

    @Lob
    @Column(name = "content_compressed", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] contentCompressed;

    @Column(name = "content_length")
    private Integer contentLength;

    @Column(name = "token_count")
    @Builder.Default
    private Integer tokenCount = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** 首次持久化前自动设置创建时间 */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * 消息角色枚举
     * - user：用户发送的消息
     * - assistant：AI 模型回复的消息
     * - system：系统提示消息（预留，当前未使用）
     */
    public enum MessageRole {
        user, assistant, system
    }
}