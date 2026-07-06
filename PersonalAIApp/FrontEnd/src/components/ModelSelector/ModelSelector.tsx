import { Select, Tag, Space, Badge, Tooltip } from 'antd';
import { useAppStore } from '../../stores/appStore';
import { getModelCategory, formatSize } from '../../utils/modelUtils';

/**
 * ModelSelector — 模型选择器组件
 *
 * ## 功能描述
 * 在顶部栏显示当前选中的模型，提供下拉切换功能。
 * 每个模型选项显示：模型名称 + 分类标签 + 文件大小。
 *
 * ## 状态处理
 * - Ollama 未连接时：显示红色 Badge "Ollama 未连接"
 * - Ollama 已连接时：显示绿色 Badge + 模型下拉选择器
 * - 无可用模型时：显示"未检测到模型，请先安装"
 *
 * ## 依赖关系
 * - 依赖 useAppStore 的 models/selectedModel/ollamaAvailable/setSelectedModel
 * - 模型切换通过 setSelectedModel 更新全局状态
 */
export default function ModelSelector() {
  const { models, selectedModel, ollamaAvailable, setSelectedModel } = useAppStore();

  // Ollama 未连接状态
  if (!ollamaAvailable) {
    return (
      <Tooltip title="Ollama 服务未启动">
        <span><Badge status="error" text="Ollama 未连接" /></span>
      </Tooltip>
    );
  }

  return (
    <Space>
      <Badge status="success" />
      <Select
        value={selectedModel}
        onChange={setSelectedModel}
        style={{ width: 260 }}
        placeholder="选择模型"
        options={models.map((m) => {
          const category = getModelCategory(m.name);
          return {
            value: m.name,
            label: (
              <Space>
                <span>{m.displayName || m.name}</span>
                <Tag color={category.color} style={{ fontSize: 10, lineHeight: '16px' }}>
                  {category.label}
                </Tag>
                {m.size && (
                  <span style={{ fontSize: 11, color: '#999' }}>
                    {formatSize(m.size)}
                  </span>
                )}
              </Space>
            ),
          };
        })}
        notFoundContent="未检测到模型，请先安装"
      />
    </Space>
  );
}