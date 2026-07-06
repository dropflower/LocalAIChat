/**
 * 模型分类映射表
 * 根据模型名称自动识别模型系列并分配颜色标签
 */
const MODEL_CATEGORIES: Record<string, { label: string; color: string }> = {
  codellama: { label: 'Code', color: 'cyan' },
  qwen: { label: '通义千问', color: 'blue' },
  llama: { label: 'Llama', color: 'green' },
  deepseek: { label: 'DeepSeek', color: 'purple' },
  mistral: { label: 'Mistral', color: 'orange' },
  gemma: { label: 'Gemma', color: 'red' },
};

export function getModelCategory(modelName: string) {
  for (const [key, value] of Object.entries(MODEL_CATEGORIES)) {
    if (modelName.toLowerCase().includes(key)) {
      return value;
    }
  }
  return { label: '其他', color: 'default' };
}

export function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(0)} MB`;
  return `${bytes} B`;
}