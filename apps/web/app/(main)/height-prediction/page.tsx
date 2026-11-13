import type { Metadata } from 'next';
import { HeightPredictionCalculator } from './calculator';

/**
 * 公开身高计算器（TASK-021，规范 §73/§75）：SEO 页面，无需登录即可使用，
 * 作为“搜索 → 预测 → 注册 → 记录”漏斗的首环（§76）。
 */
export const metadata: Metadata = {
  title: '免费身高预测 | GrowthKids',
  description:
    '输入性别、出生日期、当前身高与父母身高，免费估算孩子成年身高、预测区间、当前百分位与逐年身高趋势，并保存到成长档案。',
};

export default function HeightPredictionPage() {
  return <HeightPredictionCalculator />;
}
