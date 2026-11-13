'use client';

/**
 * 捐赠页（TASK-017，规范 §65/§110 /donate）：
 * 捐赠独立于会员；预设 $3/$5/$10/$20 或自定义金额（USD，$0.50–$10,000）；
 * mock 环境自动完成支付闭环并展示捐赠历史。
 */

import Link from 'next/link';
import { useEffect, useState } from 'react';
import type { Donation, DonationCheckout, Paginated } from '@growthkids/types';
import { donationCheckoutSchema } from '@growthkids/validation';
import { apiFetch, errorMessage } from '@/lib/api';
import { loadSession } from '@/lib/session';

const PRESETS = [3, 5, 10, 20];

type DonateState =
  | { kind: 'loading' }
  | { kind: 'guest' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; history: Donation[] };

async function fetchHistory(): Promise<Donation[]> {
  const page = await apiFetch<Paginated<Donation>>('/api/v1/donations?page=1&pageSize=20');
  return page.items;
}

async function fetchDonateState(): Promise<DonateState> {
  if (!loadSession()) {
    return { kind: 'guest' };
  }
  try {
    return { kind: 'ready', history: await fetchHistory() };
  } catch (error) {
    return { kind: 'error', message: errorMessage(error) };
  }
}

function formatAmount(amount: number): string {
  return amount % 1 === 0 ? `$${amount}` : `$${amount.toFixed(2)}`;
}

function formatDate(iso: string): string {
  const [year, month, day] = iso.slice(0, 10).split('-').map(Number);
  return `${year}年${month}月${day}日`;
}

function statusBadge(status: Donation['status']): { label: string; className: string } {
  switch (status) {
    case 'succeeded':
      return { label: '已完成', className: 'bg-emerald-50 text-emerald-600' };
    case 'pending':
      return { label: '处理中', className: 'bg-slate-100 text-slate-500' };
    default:
      return { label: '失败', className: 'bg-rose-50 text-rose-600' };
  }
}

function DonateView({ initialHistory }: { initialHistory: Donation[] }) {
  const [history, setHistory] = useState(initialHistory);
  const [preset, setPreset] = useState(5);
  const [custom, setCustom] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [noticeOk, setNoticeOk] = useState(true);
  const [amountError, setAmountError] = useState<string | null>(null);

  async function refresh() {
    try {
      setHistory(await fetchHistory());
    } catch {
      // 历史刷新失败不打断捐赠主流程
    }
  }

  async function donate() {
    const raw = custom.trim();
    const amount = raw === '' ? preset : Number(raw);
    const parsed = donationCheckoutSchema.safeParse({ amount });
    if (!parsed.success) {
      setAmountError(parsed.error.issues[0]?.message ?? '请输入有效金额');
      return;
    }
    setAmountError(null);
    setNotice(null);
    setBusy(true);
    try {
      const checkout = await apiFetch<DonationCheckout>('/api/v1/donations/checkout', {
        method: 'POST',
        body: { amount: parsed.data.amount },
      });
      if (checkout.provider === 'mock') {
        // 开发闭环：mock 网关自动完成支付（走真实 webhook 处理路径）
        await apiFetch(`/api/v1/donations/mock-complete/${checkout.transactionId}`, {
          method: 'POST',
        });
        await refresh();
        setNoticeOk(true);
        setNotice(`感谢你的 ${formatAmount(parsed.data.amount)} 捐赠，已支持 GrowthKids 继续成长！`);
      } else {
        // 真实支付网关：跳转 checkout（预留）
        window.location.href = checkout.checkoutUrl;
        return;
      }
    } catch (error) {
      setNoticeOk(false);
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
        <h1 className="text-xl font-bold text-slate-900">支持 GrowthKids</h1>
        <p className="text-xs text-slate-400">捐赠独立于会员，一次性支持项目开发与维护</p>
      </div>

      {notice && (
        <p
          className={`rounded-xl px-3 py-2 text-xs ${
            noticeOk ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'
          }`}
        >
          {notice}
        </p>
      )}

      <div className="space-y-3 rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
        <div className="grid grid-cols-4 gap-2">
          {PRESETS.map((value) => (
            <button
              key={value}
              type="button"
              disabled={busy}
              onClick={() => {
                setPreset(value);
                setCustom('');
                setAmountError(null);
              }}
              className={`rounded-xl border py-3 text-sm font-semibold transition disabled:opacity-50 ${
                custom === '' && preset === value
                  ? 'border-indigo-500 bg-indigo-50 text-indigo-600'
                  : 'border-slate-200 text-slate-600 hover:border-indigo-200'
              }`}
            >
              ${value}
            </button>
          ))}
        </div>

        <div className="space-y-1">
          <label htmlFor="custom-amount" className="text-xs font-medium text-slate-500">
            自定义金额（USD）
          </label>
          <div className="flex items-center rounded-xl border border-slate-200 focus-within:border-indigo-400">
            <span className="pl-3 text-sm text-slate-400">$</span>
            <input
              id="custom-amount"
              type="number"
              inputMode="decimal"
              min={0.5}
              max={10000}
              step={0.01}
              value={custom}
              disabled={busy}
              placeholder="0.00"
              onChange={(event) => setCustom(event.target.value)}
              className="w-full rounded-xl py-2.5 pr-3 text-sm outline-none disabled:opacity-50"
            />
          </div>
          {amountError && <p className="text-xs text-rose-500">{amountError}</p>}
        </div>

        <button
          type="button"
          disabled={busy}
          onClick={() => void donate()}
          className="w-full rounded-xl bg-indigo-500 py-3 text-sm font-semibold text-white transition hover:bg-indigo-600 disabled:opacity-50"
        >
          {busy ? '处理中…' : '支持项目'}
        </button>
        <p className="text-center text-xs text-slate-400">
          开发环境使用 Mock 支付，捐赠即时完成（架构决策 9）
        </p>
      </div>

      <div className="space-y-2">
        <p className="text-sm font-semibold text-slate-900">我的捐赠</p>
        {history.length === 0 ? (
          <p className="rounded-2xl border border-slate-100 bg-white px-4 py-6 text-center text-xs text-slate-400">
            还没有捐赠记录，感谢关注 GrowthKids
          </p>
        ) : (
          <ul className="space-y-2">
            {history.map((donation) => {
              const badge = statusBadge(donation.status);
              return (
                <li
                  key={donation.id}
                  className="flex items-center justify-between rounded-2xl border border-slate-100 bg-white px-4 py-3 shadow-sm"
                >
                  <div>
                    <p className="text-sm font-semibold text-slate-900">
                      {formatAmount(donation.amount)}
                    </p>
                    <p className="text-xs text-slate-400">
                      {formatDate(donation.paidAt ?? donation.createdAt)}
                    </p>
                  </div>
                  <span
                    className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${badge.className}`}
                  >
                    {badge.label}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}

export default function DonatePage() {
  const [state, setState] = useState<DonateState>({ kind: 'loading' });

  useEffect(() => {
    void fetchDonateState().then((next) => setState(next));
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
        <p className="text-sm text-slate-500">请先登录后再支持项目</p>
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
            void fetchDonateState().then((next) => setState(next));
          }}
          className="rounded-xl bg-indigo-500 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          重试
        </button>
      </div>
    );
  }
  return <DonateView initialHistory={state.history} />;
}
