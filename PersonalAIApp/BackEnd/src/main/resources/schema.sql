-- ============================================
-- smart_chat 数据库初始化脚本
-- 使用方式：在 MySQL 中执行此脚本
--   mysql -u root -p < schema.sql
-- 或在 MySQL 客户端中：
--   source /path/to/schema.sql;
-- ============================================

-- 先确保使用 smart_chat 数据库
-- CREATE DATABASE IF NOT EXISTS smart_chat DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE smart_chat;

-- 会话表
CREATE TABLE IF NOT EXISTS sc_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL DEFAULT '新对话',
    model_name VARCHAR(100) NOT NULL,
    message_count INT DEFAULT 0,
    is_pinned TINYINT(1) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 消息表（内容 GZIP 压缩后存储）
CREATE TABLE IF NOT EXISTS sc_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role ENUM('user', 'assistant', 'system') NOT NULL,
    content_compressed MEDIUMBLOB NOT NULL COMMENT 'GZIP压缩后的内容',
    content_length INT DEFAULT 0 COMMENT '原始内容长度',
    token_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_time (session_id, created_at),
    FOREIGN KEY (session_id) REFERENCES sc_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=COMPRESSED;

-- 模型配置表
CREATE TABLE IF NOT EXISTS sc_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100),
    temperature DECIMAL(3,2) DEFAULT 0.7,
    max_tokens INT DEFAULT 2048,
    is_active TINYINT(1) DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;