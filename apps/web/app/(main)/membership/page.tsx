'use client';

/**
 * 会员页（TASK-016，规范 §64/§110 /membership）：
 * 套餐对比（价格与权益来自后端配置化数据）、当前订阅状态、
 * 升级 Pro（mock 环境自动完成支付闭环）与取消订阅。
 */

import Link from 'next/link';
import { useEffect, useState } from 'react';
import type {
  MembershipInfo,
  MembershipPlan,
  PlanPermissions,
  SubscriptionCheckout,
} from '@growthkids/types';
import { apiFetch, errorMessage } from '@/lib/api';
import { loadSession } from '@/lib/session';

type MembershipState =
  | { kind: 'loading' }
  | { kind: 'guest' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; plans: MembershipPlan[]; membership: MembershipInfo };

async function fetchMembershipState(): Promise<MembershipState> {
  if (!loadSession()) {
    return { kind: 'guest' };
  }
  try {
    const [plans, membership] = await Promise.all([
      apiFetch<MembershipPlan[]>('/api/v1/membership/plans'),
      apiFetch<MembershipInfo>('/api/v1/membership'),
    ]);
    return { kind: 'ready', plans, membership };
  } catch (error) {
    return { kind: 'error', message: errorMessage(error) };
  }
}

interface FeatureLine {
  label: string;
  included: boolean;
}

/** 权益展示行（§64）：limits -1 = 不限；布尔项按套餐开关 */
function planFeatures(p: PlanPermissions): FeatureLine[] {
  return [
    {
      label: p.childLimit < 0 ? '不限孩子数量' : `最多 ${p.childLimit} 个孩子`,
      included: true,
    },
    { label: p.unlimitedRecords ? '无限成长记录' : '基础成长记录', included: true },
    {
      label: p.photoLimit < 0 ? '无限照片' : `最多 ${p.photoLimit} 张照片`,
      included: true,
    },
    {
      label: p.timelineLimit < 0 ? '完整时间线' : `最近 ${p.timelineLimit} 条时间线`,
      included: true,
    },
    { label: '高级身高预测', included: p.advancedPrediction },
    { label: '年度报告 + PDF 导出', included: p.annualReport && p.pdfExport },
    { label: '成长海报', included: p.poster },
    { label: '数据导出', included: p.dataExport },
    { label: 'AI 成长总结', included: p.aiSummary },
  ];
}

function priceLabel(plan: MembershipPlan): string {
  const amount =
    plan.priceAmount % 1 === 0
      ? String(plan.priceAmount)
      : plan.priceAmount.toFixed(2);
  const symbol = plan.currency === 'USD' ? '$' : `${plan.currency} `;
  const period = plan.billingPeriod === 'monthly' ? '/月' : '';
  return `${symbol}${amount}${period}`;
}

function formatDate(iso: string): string {
  const [year, month, day] = iso.slice(0, 10).split('-').map(Number);
  return `${year}年${month}月${day}日`;
}

function PlanCard({
  plan,
  current,
  busy,
  onChoose,
}: {
  plan: MembershipPlan;
  current: boolean;
  busy: boolean;
  onChoose: () => void;
}) {
  const isPro = plan.code === 'pro';
  return (
    <div
      className={`space-y-3 rounded-2xl border bg-white p-5 shadow-sm ${
        isPro ? 'border-indigo-200' : 'border-slate-100'
      }`}
    >
      <div className="flex items-baseline justify-between">
        <p className="text-base font-bold text-slate-900">{plan.name}</p>
        {current ? (
          <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-600">
            当前套餐
          </span>
        ) : isPro ? (
          <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-500">
            推荐
          </span>
        ) : null}
      </div>
      <p className="text-2xl font-bold text-slate-900">
        {priceLabel(plan)
          .split('/')[0]
          .trim()}
        {priceLabel(plan).includes('/') && (
          <span className="text-sm font-normal text-slate-400">
            {' '}
            /{priceLabel(plan).split('/')[1]}
          </span>
        )}
      </p>
      <ul className="space-y-1.5">
        {planFeatures(plan.permissions).map((feature) => (
          <li
            key={feature.label}
            className={`flex items-center gap-2 text-sm ${
              feature.included ? 'text-slate-600' : 'text-slate-300'
            }`}
          >
            <span
              className={`text-xs ${feature.included ? 'text-emerald-500' : 'text-slate-300'}`}
            >
              {feature.included ? '✓' : '—'}
            </span>
            {feature.label}
          </li>
        ))}
      </ul>
      {!current && isPro && (
        <button
          type="button"
          disabled={busy}
          onClick={onChoose}
          className="w-full rounded-xl bg-indigo-500 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600 disabled:opacity-50"
        >
          {busy ? '处理中…' : '升级 Pro'}
        </button>
      )}
    </div>
  );
}

function MembershipView({ plans, membership }: { plans: MembershipPlan[]; membership: MembershipInfo }) {
  const [state, setState] = useState<{ plans: MembershipPlan[]; membership: MembershipInfo }>({
    plans,
    membership,
  });
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const isPro = state.membership.plan === 'pro';

  async function refresh() {
    const next = await fetchMembershipState();
    if (next.kind === 'ready') {
      setState({ plans: next.plans, membership: next.membership });
    }
  }

  async function upgrade() {
    setNotice(null);
    setBusy(true);
    try {
      const checkout = await apiFetch<SubscriptionCheckout>('/api/v1/membership/subscribe', {
        method: 'POST',
        body: { planCode: 'pro' },
      });
      if (checkout.provider === 'mock') {
        // 开发闭环：mock 网关自动完成支付（走真实 webhook 处理路径）
        await apiFetch(`/api/v1/membership/mock-complete/${checkout.transactionId}`, {
          method: 'POST',
        });
        await refresh();
        setNotice('升级成功，欢迎加入 Pro！');
      } else {
        // 真实支付网关：跳转 checkout（预留）
        window.location.href = checkout.checkoutUrl;
        return;
      }
    } catch (error) {
      setNotice(errorMessage(error));
    }
    setBusy(false);
  }

  async function cancel() {
    if (!window.confirm('确定取消 Pro 订阅吗？权益将立即回落到免费版。')) {
      return;
    }
    setNotice(null);
    setBusy(true);
    try {
      await apiFetch('/api/v1/membership/cancel', { method: 'POST' });
      await refresh();
      setNotice('已取消订阅');
    } catch (error) {
      setNotice(errorMessage(error));
    }
    setBusy(false);
  }

  return (
    <div className="mx-auto w-full max-w-md space-y-4 px-4 py-6">
      <div className="space-y-1">
        <Link href="/" className="text-sm text-slate-400 hover:text-slate-600">
          ← 返回首页
        </Link>
        <h1 className="text-xl font-bold text-slate-900">会员</h1>
      </div>

      {notice && (
        <p className="rounded-xl bg-indigo-50 px-3 py-2 text-xs text-indigo-600">{notice}</p>
      )}

      <div className="rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between">
          <p className="text-sm font-semibold text-slate-900">当前订阅</p>
          <span
            className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
              isPro ? 'bg-indigo-500 text-white' : 'bg-slate-100 text-slate-500'
            }`}
          >
            {isPro ? 'Pro' : 'Free'}
          </span>
        </div>
        {isPro && state.membership.expiresAt && (
          <p className="mt-1 text-xs text-slate-400">
            {state.membership.status === 'cancelled'
              ? `已于 ${formatDate(state.membership.expiresAt)} 取消`
              : `有效期至 ${formatDate(state.membership.expiresAt)}`}
          </p>
        )}
        {!isPro && (
          <p className="mt-1 text-xs text-slate-400">
            最多 {state.membership.permissions.childLimit} 个孩子 · {state.membership.permissions.photoLimit} 张照片
          </p>
        )}
      </div>

      <div className="grid gap-3">
        {state.plans.map((plan) => (
          <PlanCard
            key={plan.code}
            plan={plan}
            current={plan.code === state.membership.plan}
            busy={busy}
            onChoose={() => void upgrade()}
          />
        ))}
      </div>

      {isPro && (
        <button
          type="button"
          disabled={busy}
          onClick={() => void cancel()}
          className="w-full rounded-xl border border-slate-200 py-2.5 text-sm font-medium text-slate-500 transition hover:bg-slate-50 disabled:opacity-50"
        >
          取消订阅
        </button>
      )}
      <p className="text-center text-xs text-slate-400">
        开发环境使用 Mock 支付，升级即时生效（架构决策 9）
      </p>
    </div>
  );
}

export default function MembershipPage() {
  const [state, setState] = useState<MembershipState>({ kind: 'loading' });

  useEffect(() => {
    void fetchMembershipState().then((next) => setState(next));
  }, []);

  if (state.kind === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-slate-400">
        加载中…
      </div>
    );
  }
  if (state.kind === 'guest') {
    return (
      <div className="mx-auto w-full max-w-md space-y-4 px-4 pt-24 text-center">
        <p className="text-sm text-slate-500">请先登录后查看会员信息</p>
        <Link
          href="/login"
          className="mx-auto block w-full max-w-xs rounded-xl bg-indigo-500 py-3 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          去登录
        </Link>
      </div>
    );
  }
  if (state.kind === 'error') {
    return (
      <div className="mx-auto w-full max-w-md space-y-4 px-4 pt-24 text-center">
        <p className="text-sm text-slate-500">{state.message}</p>
        <button
          type="button"
          onClick={() => {
            setState({ kind: 'loading' });
            void fetchMembershipState().then((next) => setState(next));
          }}
          className="rounded-xl bg-indigo-500 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          重试
        </button>
      </div>
    );
  }
  return <MembershipView plans={state.plans} membership={state.membership} />;
}

