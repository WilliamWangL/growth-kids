import type { Metadata } from 'next';
import { DealDetail } from './detail';

export const metadata: Metadata = {
  title: '优惠详情 | GrowthKids',
  description: '查看优惠商品的价格、适用年龄与商家链接。GrowthKids 仅整理优惠信息，交易与售后由商家完成。',
};

/** 优惠商品详情（TASK-022）：公开页面，数据由客户端拉取（与其它详情页一致） */
export default function DealDetailPage() {
  return <DealDetail />;
}
