import { useState } from 'react';
import { Button, Input, List, Typography, Space, Dropdown, Modal } from 'antd';
import {
  PlusOutlined,
  SearchOutlined,
  DeleteOutlined,
  EditOutlined,
  PushpinOutlined,
  MoreOutlined,
} from '@ant-design/icons';
import { useAppStore } from '../../stores/appStore';
import { apiService } from '../../services/api';
import type { Session } from '../../types';

const { Text } = Typography;

/**
 * SessionList — 会话列表组件
 *
 * ## 功能描述
 * 左侧边栏的会话列表，提供会话的创建、展示、搜索、重命名、置顶和删除功能。
 *
 * ## 界面结构
 * - 顶部："新对话"按钮（蓝色主按钮）
 * - 搜索框：支持按标题搜索会话
 * - 会话列表：每条会话项显示标题、模型名、消息数、操作菜单
 *
 * ## 会话排序规则
 * 1. 置顶会话优先显示
 * 2. 同级别按最后更新时间倒序
 *
 * ## 交互功能
 * - 点击会话项：选中并加载消息历史
 * - 搜索：输入关键词实时过滤会话标题
 * - 右键/更多按钮：弹出操作菜单（重命名、置顶/取消置顶、删除）
 * - 重命名：点击后内联编辑，Enter 或失焦保存
 * - 删除：弹出确认对话框，确认后删除并清除相关状态
 *
 * ## 当前会话高亮
 * 当前选中的会话项背景色为浅蓝色（#e6f4ff），字体加粗
 *
 * ## 状态管理
 * - searchKeyword：本地搜索关键词
 * - editingId：正在编辑标题的会话 ID
 * - editTitle：编辑中的标题文本
 * - 大部分操作通过 Zustand Store 的方法完成
 */
export default function SessionList() {
  const {
    sessions,
    currentSessionId,
    createSession,
    selectSession,
    updateSessionTitle,
    togglePinSession,
    deleteSession,
    loadSessions,
  } = useAppStore();

  const [searchKeyword, setSearchKeyword] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editTitle, setEditTitle] = useState('');

  /** 创建新会话：清空当前会话状态 */
  const handleNewChat = () => {
    createSession();
  };

  /** 搜索会话：调用后端搜索 API */
  const handleSearch = async (value: string) => {
    setSearchKeyword(value);
    if (value.trim()) {
      try {
        const results = await apiService.searchSessions(value.trim());
        useAppStore.setState({ sessions: results });
      } catch {
        // 搜索失败时静默处理
      }
    } else {
      loadSessions();
    }
  };

  /** 进入编辑模式：设置编辑 ID 和当前标题 */
  const handleEditTitle = (session: Session) => {
    setEditingId(session.id);
    setEditTitle(session.title);
  };

  /** 保存标题：Enter 或失焦时触发 */
  const handleSaveTitle = async () => {
    if (editingId && editTitle.trim()) {
      await updateSessionTitle(editingId, editTitle.trim());
      setEditingId(null);
    }
  };

  /** 删除会话：弹出确认对话框 */
  const handleDelete = (session: Session) => {
    Modal.confirm({
      title: '删除会话',
      content: `确定要删除"${session.title}"吗？此操作不可撤销。`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => deleteSession(session.id),
    });
  };

  /** 右键菜单/更多按钮的操作项 */
  const contextMenuItems = (session: Session) => [
    {
      key: 'edit',
      label: '重命名',
      icon: <EditOutlined />,
      onClick: () => handleEditTitle(session),
    },
    {
      key: 'pin',
      label: session.isPinned ? '取消置顶' : '置顶',
      icon: <PushpinOutlined />,
      onClick: () => togglePinSession(session.id),
    },
    { type: 'divider' as const },
    {
      key: 'delete',
      label: '删除',
      icon: <DeleteOutlined />,
      danger: true,
      onClick: () => handleDelete(session),
    },
  ];

  /** 排序：置顶优先 → 更新时间倒序 */
  const sortedSessions = [...sessions].sort((a, b) => {
    if (a.isPinned !== b.isPinned) return a.isPinned ? -1 : 1;
    return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: 12 }}>
      {/* 新对话按钮 */}
      <Button
        type="primary"
        icon={<PlusOutlined />}
        block
        onClick={handleNewChat}
        style={{ marginBottom: 12 }}
      >
        新对话
      </Button>

      {/* 搜索框 */}
      <Input
        placeholder="搜索会话..."
        prefix={<SearchOutlined />}
        value={searchKeyword}
        onChange={(e) => handleSearch(e.target.value)}
        allowClear
        style={{ marginBottom: 12 }}
      />

      {/* 会话列表 */}
      <div style={{ flex: 1, overflow: 'auto' }}>
        <List
          dataSource={sortedSessions}
          renderItem={(session) => (
            <List.Item
              key={session.id}
              onClick={() => selectSession(session.id)}
              style={{
                cursor: 'pointer',
                padding: '8px 12px',
                borderRadius: 8,
                marginBottom: 4,
                background: currentSessionId === session.id ? '#e6f4ff' : 'transparent',
                border: 'none',
              }}
            >
              <div style={{ width: '100%', overflow: 'hidden' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  {editingId === session.id ? (
                    // 编辑模式：内联输入框
                    <Input
                      size="small"
                      value={editTitle}
                      onChange={(e) => setEditTitle(e.target.value)}
                      onPressEnter={handleSaveTitle}
                      onBlur={handleSaveTitle}
                      autoFocus
                      style={{ width: '80%' }}
                      onClick={(e) => e.stopPropagation()}
                    />
                  ) : (
                    // 显示模式：标题 + 置顶图标
                    <Space>
                      {session.isPinned && <PushpinOutlined style={{ color: '#1677ff', fontSize: 12 }} />}
                      <Text
                        ellipsis
                        style={{
                          maxWidth: 160,
                          fontWeight: currentSessionId === session.id ? 600 : 400,
                        }}
                      >
                        {session.title}
                      </Text>
                    </Space>
                  )}
                  {/* 更多操作按钮 */}
                  <Dropdown menu={{ items: contextMenuItems(session) }} trigger={['click']}>
                    <Button
                      type="text"
                      size="small"
                      icon={<MoreOutlined />}
                      onClick={(e) => e.stopPropagation()}
                    />
                  </Dropdown>
                </div>
                {/* 会话信息：模型名 + 消息数 */}
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {session.modelName} · {session.messageCount} 条消息
                </Text>
              </div>
            </List.Item>
          )}
          locale={{ emptyText: '暂无会话' }}
        />
      </div>
    </div>
  );
}