import Link from 'next/link';
import type { Deal } from '@growthkids/types';

/** 金额展示：USD 用 $，其他币种保留 code（v1 后端仅支持 USD） */
export function formatDealPrice(amount: number, currency: string): string {
  const text = amount % 1 === 0 ? String(amount) : amount.toFixed(2);
  return currency === 'USD' ? `$${text}` : `${currency} ${text}`;
}

/** 月龄 → 家长易读文案（整年换算为「岁」） */
export function ageMonthsLabel(months: number): string {
  return months >= 12 && months % 12 === 0 ? `${months / 12} 岁` : `${months} 个月`;
}

/** 适用年龄区间：两端都缺失表示全年龄 */
export function dealAgeLabel(deal: Deal): string {
  const min = deal.minAgeMonths ?? null;
  const max = deal.maxAgeMonths ?? null;
  if (min === null || max === null) {
    if (max === null) {
      return min === null ? '全年龄' : `${ageMonthsLabel(min)}以上`;
    }
    return `0-${ageMonthsLabel(max)}`;
  }
  return `${ageMonthsLabel(min)}-${ageMonthsLabel(max)}`;
}

/** 商品缩略图：无图时用分类色块占位，避免外部图片失效导致空白 */
export function DealThumb({ deal, className }: { deal: Deal; className: string }) {
  if (deal.imageUrl) {
    return (
      // eslint-disable-next-line @next/next/no-img-element -- 商家图片来自外部域名，不适合 next/image 逐域配置
      <img
        src={deal.imageUrl}
        alt={deal.title}
        className={`${className} rounded-xl bg-slate-50 object-cover`}
      />
    );
  }
  return (
    <div
      className={`${className} flex items-center justify-center rounded-xl bg-indigo-50 text-sm font-semibold text-indigo-400`}
    >
      {deal.categoryLabel ?? '好物'}
    </div>
  );
}

/** 列表卡片（TASK-022）：公开浏览，点击进入详情页 */
export function DealCard({ deal }: { deal: Deal }) {
  return (
    <Link
      href={`/deals/${deal.id}`}
      className="flex gap-3 rounded-2xl border border-slate-100 bg-white p-3 shadow-sm transition hover:border-indigo-200"
    >
      <DealThumb deal={deal} className="h-20 w-20 shrink-0" />
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <p className="truncate text-sm font-semibold text-slate-900">{deal.title}</p>
          {deal.discountPercent != null && deal.discountPercent > 0 && (
            <span className="shrink-0 rounded-full bg-rose-50 px-2 py-0.5 text-xs font-semibold text-rose-600">
              -{deal.discountPercent}%
            </span>
          )}
        </div>
        <p className="mt-0.5 truncate text-xs text-slate-400">
          {[deal.brand, deal.categoryLabel, dealAgeLabel(deal)].filter(Boolean).join(' · ')}
        </p>
        <div className="mt-1.5 flex items-baseline gap-2">
          <span className="text-base font-bold text-rose-600">
            {formatDealPrice(deal.dealPrice, deal.currency)}
          </span>
          {deal.originalPrice != null && (
            <span className="text-xs text-slate-400 line-through">
              {formatDealPrice(deal.originalPrice, deal.currency)}
            </span>
          )}
          {deal.merchant && (
            <span className="ml-auto shrink-0 text-xs text-slate-400">{deal.merchant}</span>
          )}
        </div>
      </div>
    </Link>
  );
}
