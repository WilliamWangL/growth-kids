import type { Metadata } from 'next';
import { DealsBrowser } from './browser';

export const metadata: Metadata = {
  title: '育儿好物优惠 | GrowthKids',
  description:
    '精选儿童玩具、绘本、出行装备与营养品的限时优惠，按孩子月龄与分类筛选，查看详情后直达商家购买。',
};

/** 优惠商品列表（TASK-022）：公开页面，SEO + 转化入口 */
export default function DealsPage() {
  return <DealsBrowser />;
}
