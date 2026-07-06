package com.aiapp.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 会话实体类 — 映射 sc_session 表
 *
 * ## 功能描述
 * 代表一次完整的对话会话。每个会话绑定一个 AI 模型，包含多轮对话消息。
 * 使用 JPA 自动管理创建时间和更新时间。
 *
 * ## 字段说明
 * - id：主键，自增
 * - title：会话标题，默认"新对话"，可由用户修改
 * - modelName：使用的 AI 模型名称（如 qwen2.5:7b）
 * - messageCount：消息总数计数器，每次新增消息后更新
 * - isPinned：是否置顶，置顶会话在列表中优先显示
 * - createdAt：创建时间，由 @PrePersist 自动设置，不可更新
 * - updatedAt：最后更新时间，每次持久化操作自动刷新
 *
 * ## 生命周期回调
 * - @PrePersist：首次保存前自动设置 createdAt 和 updatedAt
 * - @PreUpdate：更新前自动刷新 updatedAt
 *
 * ## 关联关系
 * - 与 Message 为一对多关系（通过 Message.sessionId 关联）
 * - 删除会话时，关联消息由数据库外键 ON DELETE CASCADE 自动删除
 */
@Entity
@Table(name = "sc_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    @Builder.Default
    private String title = "新对话";

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "message_count")
    @Builder.Default
    private Integer messageCount = 0;

    @Column(name = "is_pinned")
    @Builder.Default
    private Boolean isPinned = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 首次持久化前自动设置创建时间和更新时间 */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /** 更新前自动刷新更新时间 */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}