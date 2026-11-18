'use client';

/**
 * 优惠商品详情（TASK-022）：公开页面。
 * 只读展示，购买按钮跳商家站外链接；下架/过期商品返回 404 时给出返回列表引导。
 */

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import type { Deal } from '@growthkids/types';
import { DealThumb, dealAgeLabel, formatDealPrice } from '@/components/deal-card';
import { ApiError, apiFetch, errorMessage } from '@/lib/api';

type DetailState =
  | { kind: 'loading' }
  | { kind: 'notfound' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; deal: Deal };

export function DealDetail() {
  const params = useParams<{ id: string }>();
  const dealId = params?.id;
  const [state, setState] = useState<DetailState>({ kind: 'loading' });

  useEffect(() => {
    if (!dealId) {
      return;
    }
    void apiFetch<Deal>(`/api/v1/deals/${dealId}`, { auth: false })
      .then((deal) => setState({ kind: 'ready', deal }))
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.code === 'DEAL_NOT_FOUND') {
          setState({ kind: 'notfound' });
          return;
        }
        setState({ kind: 'error', message: errorMessage(error) });
      });
  }, [dealId]);

  if (state.kind === 'loading') {
    return (
      <div className="flex min-h-[60vh] items-center justify-center text-sm text-slate-400">
        加载中…
      </div>
    );
  }

  if (state.kind === 'notfound') {
    return (
      <div className="mx-auto w-full max-w-md space-y-4 px-4 pt-24 text-center">
        <p className="text-sm text-slate-500">该优惠已下架或已过期</p>
        <Link
          href="/deals"
          className="mx-auto block w-full max-w-xs rounded-xl bg-indigo-500 py-3 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          看看其它优惠
        </Link>
      </div>
    );
  }

  if (state.kind === 'error') {
    return (
      <div className="mx-auto w-full max-w-md space-y-4 px-4 pt-24 text-center">
        <p className="text-sm text-slate-500">{state.message}</p>
        <Link
          href="/deals"
          className="mx-auto block w-full max-w-xs rounded-xl border border-indigo-200 py-3 text-sm font-semibold text-indigo-600 transition hover:bg-indigo-50"
        >
          返回优惠列表
        </Link>
      </div>
    );
  }

  const { deal } = state;
  const meta = [deal.brand, deal.merchant, dealAgeLabel(deal)].filter(Boolean).join(' · ');

  return (
    <div className="mx-auto w-full max-w-md space-y-4 px-4 pb-10">
      <header className="pt-6">
        <Link href="/deals" className="text-xs text-slate-400 hover:text-slate-600">
          ← 优惠列表
        </Link>
      </header>

      <DealThumb deal={deal} className="h-44 w-full text-base" />

      <section className="space-y-2">
        <div className="flex flex-wrap items-center gap-1.5">
          {deal.categoryLabel && (
            <span className="rounded-full bg-indigo-50 px-2.5 py-0.5 text-xs font-medium text-indigo-600">
              {deal.categoryLabel}
            </span>
          )}
          {deal.featured && (
            <span className="rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-600">
              精选
            </span>
          )}
        </div>
        <h1 className="text-xl font-bold leading-snug text-slate-900">{deal.title}</h1>
        <p className="text-xs text-slate-400">{meta}</p>
      </section>

      <section className="flex flex-wrap items-end gap-2 rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
        <span className="text-2xl font-bold text-rose-600">
          {formatDealPrice(deal.dealPrice, deal.currency)}
        </span>
        {deal.originalPrice != null && (
          <span className="pb-1 text-sm text-slate-400 line-through">
            {formatDealPrice(deal.originalPrice, deal.currency)}
          </span>
        )}
        {deal.discountPercent != null && deal.discountPercent > 0 && (
          <span className="ml-auto pb-1 text-xs font-semibold text-rose-600">
            省 {deal.discountPercent}%
          </span>
        )}
      </section>

      {deal.description && (
        <p className="whitespace-pre-line text-sm leading-relaxed text-slate-600">
          {deal.description}
        </p>
      )}

      <a
        href={deal.purchaseUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="block w-full rounded-xl bg-rose-500 py-3 text-center text-sm font-semibold text-white transition hover:bg-rose-600"
      >
        去 {deal.merchant ?? '商家'} 购买
      </a>

      <p className="text-xs leading-relaxed text-slate-400">
        价格、库存与售后以商家页面为准；GrowthKids 仅整理优惠信息，不参与交易。
        {deal.endsAt && ` 优惠截止 ${deal.endsAt.slice(0, 10)}。`}
      </p>
    </div>
  );
}
