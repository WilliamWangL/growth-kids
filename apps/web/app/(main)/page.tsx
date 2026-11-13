'use client';

/**
 * 首页（§54/§111/§112）：登录后展示第一个孩子的摘要 dashboard，
 * 未登录展示营销首屏；数据来自聚合端点 /children/{id}/dashboard（TASK-011）。
 */

import Link from 'next/link';
import { useEffect, useState } from 'react';
import type {
  AgeInfo,
  ChildDashboard,
  Gender,
  Paginated,
  UserProfile,
} from '@growthkids/types';
import { ApiError, apiFetch, errorMessage, logout } from '@/lib/api';
import { loadSession } from '@/lib/session';

type HomeState =
  | { kind: 'loading' }
  | { kind: 'guest' }
  | { kind: 'error'; message: string }
  | { kind: 'no-child'; user: UserProfile }
  | { kind: 'dashboard'; user: UserProfile; dashboard: ChildDashboard };

const GENDER_LABELS: Record<Gender, string> = {
  male: '男孩',
  female: '女孩',
  unknown: '性别未填写',
};

/** 月龄文案（§112）：<2 岁用月、≥2 岁用岁+月 */
function formatAge(age: AgeInfo): string {
  const totalMonths = age.totalMonths ?? age.years * 12 + age.months;
  if (totalMonths < 1) {
    return '未满月';
  }
  if (totalMonths < 24) {
    return `${totalMonths}个月`;
  }
  return `${age.years}岁${age.months}个月`;
}

function formatPercentile(percentile: number | null | undefined): string | undefined {
  return percentile == null ? undefined : `P${Math.round(percentile)}`;
}

function Metric({ label, value, badge }: { label: string; value: string; badge?: string }) {
  return (
    <div className="rounded-2xl border border-slate-100 bg-white p-4 text-center shadow-sm">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 text-lg font-semibold text-slate-900">{value}</p>
      <p className="mt-0.5 h-4 text-xs text-indigo-500">{badge ?? ''}</p>
    </div>
  );
}

function PageHeader({ onLogout }: { onLogout?: () => void }) {
  return (
    <header className="mx-auto flex w-full max-w-md items-center justify-between px-4 pt-6">
      <span className="text-lg font-bold text-indigo-600">GrowthKids</span>
      {onLogout ? (
        <button
          type="button"
          onClick={onLogout}
          className="text-sm text-slate-400 hover:text-slate-600"
        >
          退出
        </button>
      ) : (
        <Link href="/login" className="text-sm font-medium text-indigo-500 hover:text-indigo-600">
          登录
        </Link>
      )}
    </header>
  );
}

function GuestHome() {
  return (
    <div className="mx-auto w-full max-w-md px-4">
      <PageHeader />
      <section className="space-y-5 pt-16 text-center">
        <h1 className="text-3xl font-bold leading-snug text-slate-900">
          记录孩子成长的
          <br />
          每一个瞬间
        </h1>
        <p className="text-sm leading-relaxed text-slate-500">
          身高、体重、头围记录，WHO 生长曲线与身高预测，
          <br />
          为孩子保留完整的成长档案。
        </p>
        <div className="flex flex-wrap justify-center gap-2">
          {['身高体重', '生长曲线', '成长照片', '成长故事', '身高预测'].map((tag) => (
            <span
              key={tag}
              className="rounded-full border border-indigo-100 bg-indigo-50 px-3 py-1 text-xs text-indigo-600"
            >
              {tag}
            </span>
          ))}
        </div>
        <div className="space-y-3 pt-4">
          <Link
            href="/register"
            className="block w-full rounded-xl bg-indigo-500 py-3 text-sm font-semibold text-white transition hover:bg-indigo-600"
          >
            开始记录成长
          </Link>
          <Link
            href="/height-prediction"
            className="block w-full rounded-xl border border-indigo-200 py-3 text-sm font-semibold text-indigo-600 transition hover:bg-indigo-50"
          >
            免费预测身高
          </Link>
        </div>
      </section>
    </div>
  );
}

function DashboardView({
  user,
  dashboard,
  onLogout,
}: {
  user: UserProfile;
  dashboard: ChildDashboard;
  onLogout: () => void;
}) {
  const { child, age, latestRecord } = dashboard;
  return (
    <div className="mx-auto w-full max-w-md space-y-4 px-4 pb-10">
      <PageHeader onLogout={onLogout} />

      <section className="flex items-center gap-4 pt-4">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-indigo-100 text-xl font-bold text-indigo-600">
          {child.name.slice(0, 1).toUpperCase()}
        </div>
        <div className="min-w-0">
          <h1 className="truncate text-xl font-bold text-slate-900">{child.name}</h1>
          <p className="text-sm text-slate-500">
            {GENDER_LABELS[child.gender]} · {formatAge(age)}
          </p>
        </div>
      </section>

      <section className="grid grid-cols-3 gap-3">
        <Metric
          label="身高"
          value={latestRecord?.heightCm != null ? `${latestRecord.heightCm}cm` : '—'}
          badge={latestRecord ? formatPercentile(latestRecord.heightPercentile) : undefined}
        />
        <Metric
          label="体重"
          value={latestRecord?.weightKg != null ? `${latestRecord.weightKg}kg` : '—'}
          badge={latestRecord ? formatPercentile(latestRecord.weightPercentile) : undefined}
        />
        <Metric
          label="头围"
          value={
            latestRecord?.headCircumferenceCm != null
              ? `${latestRecord.headCircumferenceCm}cm`
              : '—'
          }
          badge={latestRecord ? formatPercentile(latestRecord.headPercentile) : undefined}
        />
      </section>

      <p className="text-center text-xs text-slate-400">
        {latestRecord ? `最近一次记录 ${latestRecord.recordDate}` : '还没有成长记录，现在开始吧'}
      </p>

      <Link
        href={`/children/${child.id}/growth/new`}
        className="block w-full rounded-xl bg-indigo-500 py-3 text-center text-sm font-semibold text-white transition hover:bg-indigo-600"
      >
        + 记录今天的成长
      </Link>

      <div className="grid gap-3 pt-2">
        <Link
          href={`/children/${child.id}/charts`}
          className="flex items-center justify-between rounded-2xl border border-slate-100 bg-white px-4 py-3.5 shadow-sm transition hover:border-indigo-200"
        >
          <div>
            <p className="text-sm font-medium text-slate-700">成长趋势</p>
            <p className="text-xs text-slate-400">身高、体重、BMI 与头围曲线</p>
          </div>
          <span className="shrink-0 text-sm text-indigo-500">查看 →</span>
        </Link>
        <Link
          href={`/children/${child.id}/prediction`}
          className="flex items-center justify-between rounded-2xl border border-slate-100 bg-white px-4 py-3.5 shadow-sm transition hover:border-indigo-200"
        >
          <div>
            <p className="text-sm font-medium text-slate-700">身高预测</p>
            <p className="text-xs text-slate-400">估算成年身高、区间与逐年趋势</p>
          </div>
          <span className="shrink-0 text-sm text-indigo-500">查看 →</span>
        </Link>
        <Link
          href={`/children/${child.id}/photos`}
          className="flex items-center justify-between rounded-2xl border border-slate-100 bg-white px-4 py-3.5 shadow-sm transition hover:border-indigo-200"
        >
          <div>
            <p className="text-sm font-medium text-slate-700">最近照片</p>
            <p className="text-xs text-slate-400">珍藏孩子的高光时刻</p>
          </div>
          <span className="shrink-0 text-sm text-indigo-500">查看 →</span>
        </Link>
        <Link
          href={`/children/${child.id}/timeline`}
          className="flex items-center justify-between rounded-2xl border border-slate-100 bg-white px-4 py-3.5 shadow-sm transition hover:border-indigo-200"
        >
          <div>
            <p className="text-sm font-medium text-slate-700">成长时间线</p>
            <p className="text-xs text-slate-400">记录与故事按日期回看</p>
          </div>
          <span className="shrink-0 text-sm text-indigo-500">查看 →</span>
        </Link>
        <Link
          href={`/children/${child.id}/reports`}
          className="flex items-center justify-between rounded-2xl border border-slate-100 bg-white px-4 py-3.5 shadow-sm transition hover:border-indigo-200"
        >
          <div>
            <p className="text-sm font-medium text-slate-700">年度报告</p>
            <p className="text-xs text-slate-400">一键生成可下载的成长总结 PDF</p>
          </div>
          <span className="shrink-0 text-sm text-indigo-500">查看 →</span>
        </Link>
        <Link
          href="/membership"
          className="flex items-center justify-between rounded-2xl border border-slate-100 bg-white px-4 py-3.5 shadow-sm transition hover:border-indigo-200"
        >
          <div>
            <p className="text-sm font-medium text-slate-700">会员</p>
            <p className="text-xs text-slate-400">升级 Pro 解锁更多空间</p>
          </div>
          <span className="shrink-0 text-sm text-indigo-500">查看 →</span>
        </Link>
        <Link
          href="/donate"
          className="flex items-center justify-between rounded-2xl border border-slate-100 bg-white px-4 py-3.5 shadow-sm transition hover:border-indigo-200"
        >
          <div>
            <p className="text-sm font-medium text-slate-700">支持项目</p>
            <p className="text-xs text-slate-400">捐赠帮助 GrowthKids 持续成长</p>
          </div>
          <span className="shrink-0 text-sm text-indigo-500">去捐赠 →</span>
        </Link>
        {user.role === 'admin' && (
          <Link
            href="/admin"
            className="flex items-center justify-between rounded-2xl border border-indigo-100 bg-indigo-50/60 px-4 py-3.5 shadow-sm transition hover:border-indigo-300"
          >
            <div>
              <p className="text-sm font-medium text-indigo-700">后台管理</p>
              <p className="text-xs text-indigo-400">用户/孩子/支付/日志与平台统计</p>
            </div>
            <span className="shrink-0 text-sm text-indigo-500">进入 →</span>
          </Link>
        )}
      </div>

      <p className="pt-2 text-center text-xs text-slate-400">
        你好，{user.displayName ?? user.email}
      </p>
    </div>
  );
}

function NoChildView({ user, onLogout }: { user: UserProfile; onLogout: () => void }) {
  return (
    <div className="mx-auto w-full max-w-md px-4">
      <PageHeader onLogout={onLogout} />
      <section className="space-y-6 pt-24 text-center">
        <h1 className="text-2xl font-bold text-slate-900">
          欢迎，{user.displayName ?? user.email}
        </h1>
        <p className="text-sm text-slate-500">添加第一个孩子，开始记录成长的每一个瞬间。</p>
        <Link
          href="/children/new"
          className="mx-auto block w-full max-w-xs rounded-xl bg-indigo-500 py-3 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          + 添加孩子
        </Link>
        {user.role === 'admin' && (
          <Link
            href="/admin"
            className="mx-auto block w-full max-w-xs rounded-xl border border-indigo-200 py-3 text-sm font-semibold text-indigo-600 transition hover:bg-indigo-50"
          >
            进入后台管理
          </Link>
        )}
      </section>
    </div>
  );
}

/**
 * 拉取首页状态（纯数据获取，不含 setState）。
 * setState 由调用方在 then 回调中执行：react-hooks/set-state-in-effect 无法识别
 * async/await 边界，会把 effect 中调用的异步函数误判为同步 setState。
 */
async function fetchHomeState(): Promise<HomeState> {
  if (!loadSession()) {
    return { kind: 'guest' };
  }
  try {
    const user = await apiFetch<UserProfile>('/api/v1/users/me');
    const children = await apiFetch<Paginated<{ id: string }>>('/api/v1/children?page=1&pageSize=1');
    const first = children.items[0];
    if (!first) {
      return { kind: 'no-child', user };
    }
    const dashboard = await apiFetch<ChildDashboard>(`/api/v1/children/${first.id}/dashboard`);
    return { kind: 'dashboard', user, dashboard };
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      return { kind: 'guest' };
    }
    return { kind: 'error', message: errorMessage(error) };
  }
}

export default function HomePage() {
  const [state, setState] = useState<HomeState>({ kind: 'loading' });

  useEffect(() => {
    void fetchHomeState().then((next) => setState(next));
  }, []);

  function handleRetry() {
    setState({ kind: 'loading' });
    void fetchHomeState().then((next) => setState(next));
  }

  async function handleLogout() {
    await logout();
    setState({ kind: 'guest' });
  }

  if (state.kind === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-slate-400">
        加载中…
      </div>
    );
  }
  if (state.kind === 'guest') {
    return <GuestHome />;
  }
  if (state.kind === 'error') {
    return (
      <div className="mx-auto w-full max-w-md space-y-4 px-4 pt-24 text-center">
        <p className="text-sm text-slate-500">{state.message}</p>
        <button
          type="button"
          onClick={handleRetry}
          className="rounded-xl bg-indigo-500 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          重试
        </button>
      </div>
    );
  }
  if (state.kind === 'no-child') {
    return <NoChildView user={state.user} onLogout={handleLogout} />;
  }
  return (
    <DashboardView user={state.user} dashboard={state.dashboard} onLogout={handleLogout} />
  );
}

