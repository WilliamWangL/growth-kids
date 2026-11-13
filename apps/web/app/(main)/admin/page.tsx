'use client';

/**
 * 后台管理页（TASK-019，规范 §77/§110）：仅 admin 可访问。
 * 顶部 tab：总览（平台统计）/ 用户（角色管理）/ 孩子 / 支付 / 日志。
 * 非登录与无权限分别给出引导；后端对 /api/v1/admin/** 做 ADMIN 双重校验。
 */

import Link from 'next/link';
import { useEffect, useState } from 'react';
import type {
  AdminChild,
  AdminDashboard,
  AdminLog,
  AdminPayment,
  AdminUser,
  Paginated,
  UserProfile,
} from '@growthkids/types';
import { apiFetch, errorMessage } from '@/lib/api';
import { loadSession } from '@/lib/session';

const PAGE_SIZE = 20;

type AdminState =
  | { kind: 'loading' }
  | { kind: 'guest' }
  | { kind: 'denied' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; dashboard: AdminDashboard };

type Tab = 'dashboard' | 'users' | 'children' | 'payments' | 'logs';

const TABS: { key: Tab; label: string }[] = [
  { key: 'dashboard', label: '总览' },
  { key: 'users', label: '用户' },
  { key: 'children', label: '孩子' },
  { key: 'payments', label: '支付' },
  { key: 'logs', label: '日志' },
];

/** 后端 LocalDateTime 序列化为 `2026-10-09T12:34:56`（无时区后缀） */
function formatDateTime(value: string | null): string {
  return value == null ? '—' : value.slice(0, 16).replace('T', ' ');
}

function formatMoney(amount: number, currency: string): string {
  const text = amount % 1 === 0 ? String(amount) : amount.toFixed(2);
  return `${currency === 'USD' ? '$' : `${currency} `}${text}`;
}

function Badge({ label, tone }: { label: string; tone: 'gray' | 'green' | 'rose' | 'indigo' | 'amber' }) {
  const tones = {
    gray: 'bg-slate-100 text-slate-500',
    green: 'bg-emerald-50 text-emerald-600',
    rose: 'bg-rose-50 text-rose-600',
    indigo: 'bg-indigo-50 text-indigo-600',
    amber: 'bg-amber-50 text-amber-600',
  } as const;
  return (
    <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold ${tones[tone]}`}>
      {label}
    </span>
  );
}

function statusBadge(status: string): { label: string; tone: 'gray' | 'green' | 'rose' | 'amber' } {
  switch (status) {
    case 'succeeded':
    case 'active':
    case 'ready':
      return { label: '成功', tone: 'green' };
    case 'pending':
    case 'processing':
      return { label: '处理中', tone: 'amber' };
    case 'failed':
    case 'deleted':
      return { label: '失败', tone: 'rose' };
    default:
      return { label: status, tone: 'gray' };
  }
}

function genderLabel(gender: string): string {
  switch (gender) {
    case 'male':
      return '男孩';
    case 'female':
      return '女孩';
    default:
      return '未知';
  }
}

/** 分页条（page 从 1 开始；数据为空时隐藏） */
function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (next: number) => void;
}) {
  return (
    <div className="flex items-center justify-between pt-1 text-xs text-slate-500">
      <span>
        第 {page} / {Math.max(totalPages, 1)} 页
      </span>
      <span className="flex gap-2">
        <button
          type="button"
          disabled={page <= 1}
          onClick={() => onChange(page - 1)}
          className="rounded-lg border border-slate-200 px-3 py-1.5 transition hover:border-indigo-300 disabled:opacity-40"
        >
          上一页
        </button>
        <button
          type="button"
          disabled={page >= totalPages}
          onClick={() => onChange(page + 1)}
          className="rounded-lg border border-slate-200 px-3 py-1.5 transition hover:border-indigo-300 disabled:opacity-40"
        >
          下一页
        </button>
      </span>
    </div>
  );
}

function LoadError({ message }: { message: string }) {
  return <p className="rounded-xl bg-rose-50 px-3 py-2 text-xs text-rose-600">{message}</p>;
}

/** 模块级分页拉取（不触碰组件 state；setState 放 effect 的 then 回调，规避 set-state-in-effect 误报） */
async function fetchPage<T>(path: string): Promise<{ data?: T; message?: string }> {
  try {
    return { data: await apiFetch<T>(path) };
  } catch (error) {
    return { message: errorMessage(error) };
  }
}

// ===== 总览（/admin/dashboard 聚合，§77） =====

function StatCard({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-semibold text-slate-900">{value}</p>
      {hint && <p className="mt-0.5 text-xs text-slate-400">{hint}</p>}
    </div>
  );
}

function DashboardTab({ dashboard }: { dashboard: AdminDashboard }) {
  const { children, payments, reports, who } = dashboard;
  const whoText =
    who.version == null
      ? '未导入'
      : `${who.version} · ${who.rows} 行 · ${formatDateTime(who.importedAt)}`;
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <StatCard
          label="注册用户"
          value={String(dashboard.totalUsers)}
          hint={`活跃 ${dashboard.activeUsers}`}
        />
        <StatCard
          label="孩子档案"
          value={String(children.total)}
          hint={`活跃 ${children.active} · 已删 ${children.deleted}`}
        />
        <StatCard label="成长记录" value={String(dashboard.growthRecords)} />
        <StatCard label="成长照片" value={String(dashboard.photos)} />
        <StatCard label="成长故事" value={String(dashboard.memories)} />
        <StatCard
          label="年度报告"
          value={String(reports.total)}
          hint={`已就绪 ${reports.ready}`}
        />
      </div>

      <StatCard
        label="活跃会员（Pro）"
        value={String(dashboard.activeMemberships)}
        hint="memberships.status = active"
      />

      <div className="rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
        <p className="text-sm font-semibold text-slate-900">支付与捐赠</p>
        <ul className="mt-2 space-y-1.5 text-xs text-slate-600">
          <li className="flex justify-between">
            <span>会员收入（{payments.membershipSucceeded} 笔成功）</span>
            <span className="font-semibold text-emerald-600">
              {formatMoney(payments.membershipRevenue, 'USD')}
            </span>
          </li>
          <li className="flex justify-between">
            <span>捐赠收入（{payments.donationSucceeded} 笔成功）</span>
            <span className="font-semibold text-emerald-600">
              {formatMoney(payments.donationRevenue, 'USD')}
            </span>
          </li>
          <li className="flex justify-between text-slate-400">
            <span>处理中 / 失败</span>
            <span>
              {payments.pending} / {payments.failed}
            </span>
          </li>
        </ul>
      </div>

      <div className="rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
        <p className="text-sm font-semibold text-slate-900">WHO 生长标准数据集</p>
        <p className="mt-1 text-xs text-slate-600">{whoText}</p>
        {who.source === 'growthkids-example-synthetic' && (
          <p className="mt-1 text-xs text-amber-600">当前为合成示例数据（§124），生产需替换官方数据</p>
        )}
      </div>
    </div>
  );
}

// ===== 用户（角色管理，§71 审计） =====

function UsersTab() {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paginated<AdminUser> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    void fetchPage<Paginated<AdminUser>>(
      `/api/v1/admin/users?page=${page}&pageSize=${PAGE_SIZE}`,
    ).then((result) => {
      if (result.data) {
        setData(result.data);
        setError(null);
      } else {
        setError(result.message ?? '加载失败');
      }
    });
  }, [page]);

  async function toggleRole(user: AdminUser) {
    setBusyId(user.id);
    setNotice(null);
    try {
      const nextRole = user.role === 'admin' ? 'user' : 'admin';
      const updated = await apiFetch<AdminUser>(`/api/v1/admin/users/${user.id}/role`, {
        method: 'PATCH',
        body: { role: nextRole },
      });
      setData((prev) =>
        prev
          ? { ...prev, items: prev.items.map((row) => (row.id === updated.id ? updated : row)) }
          : prev,
      );
      setNotice(
        `已将 ${updated.email} 切换为${updated.role === 'admin' ? '管理员' : '普通用户'}（自该用户下次登录生效）`,
      );
    } catch (err) {
      setNotice(errorMessage(err));
    }
    setBusyId(null);
  }

  return (
    <div className="space-y-3">
      {notice && (
        <p className="rounded-xl bg-indigo-50 px-3 py-2 text-xs text-indigo-600">{notice}</p>
      )}
      {error && <LoadError message={error} />}
      {data &&
        (data.items.length === 0 ? (
          <p className="rounded-2xl border border-slate-100 bg-white px-4 py-6 text-center text-xs text-slate-400">
            暂无用户
          </p>
        ) : (
          <ul className="space-y-2">
            {data.items.map((user) => (
              <li
                key={user.id}
                className="space-y-2 rounded-2xl border border-slate-100 bg-white px-4 py-3 shadow-sm"
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-slate-900">
                      {user.displayName ?? user.email}
                    </p>
                    <p className="truncate text-xs text-slate-400">{user.email}</p>
                  </div>
                  <div className="flex shrink-0 gap-1.5">
                    {user.plan === 'pro' ? (
                      <Badge label="Pro" tone="indigo" />
                    ) : (
                      <Badge label="Free" tone="gray" />
                    )}
                    {user.role === 'admin' && <Badge label="管理员" tone="amber" />}
                  </div>
                </div>
                <div className="flex items-center justify-between text-xs text-slate-400">
                  <span>
                    孩子 {user.childCount} · 注册 {formatDateTime(user.createdAt)}
                  </span>
                  <button
                    type="button"
                    disabled={busyId === user.id}
                    onClick={() => void toggleRole(user)}
                    className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-600 transition hover:border-indigo-300 hover:text-indigo-600 disabled:opacity-50"
                  >
                    {busyId === user.id
                      ? '处理中…'
                      : user.role === 'admin'
                        ? '降为普通用户'
                        : '设为管理员'}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        ))}
      {data && data.pagination.totalPages > 0 && (
        <Pager
          page={page}
          totalPages={data.pagination.totalPages}
          onChange={(next) => setPage(next)}
        />
      )}
    </div>
  );
}

// ===== 孩子（admin 视角含已软删除） =====

function ChildrenTab() {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paginated<AdminChild> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void fetchPage<Paginated<AdminChild>>(
      `/api/v1/admin/children?page=${page}&pageSize=${PAGE_SIZE}`,
    ).then((result) => {
      if (result.data) {
        setData(result.data);
        setError(null);
      } else {
        setError(result.message ?? '加载失败');
      }
    });
  }, [page]);

  return (
    <div className="space-y-3">
      {error && <LoadError message={error} />}
      {data &&
        (data.items.length === 0 ? (
          <p className="rounded-2xl border border-slate-100 bg-white px-4 py-6 text-center text-xs text-slate-400">
            暂无孩子档案
          </p>
        ) : (
          <ul className="space-y-2">
            {data.items.map((child) => {
              const badge =
                child.status === 'deleted'
                  ? { label: '已删除', tone: 'rose' as const }
                  : { label: '活跃', tone: 'green' as const };
              return (
                <li
                  key={child.id}
                  className="rounded-2xl border border-slate-100 bg-white px-4 py-3 shadow-sm"
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-slate-900">{child.name}</p>
                      <p className="truncate text-xs text-slate-400">
                        {child.ownerEmail} · {genderLabel(child.gender)} · 生日 {child.birthDate}
                      </p>
                    </div>
                    <Badge label={badge.label} tone={badge.tone} />
                  </div>
                  <p className="pt-1 text-xs text-slate-400">
                    创建 {formatDateTime(child.createdAt)} · ID {child.id}
                  </p>
                </li>
              );
            })}
          </ul>
        ))}
      {data && data.pagination.totalPages > 0 && (
        <Pager
          page={page}
          totalPages={data.pagination.totalPages}
          onChange={(next) => setPage(next)}
        />
      )}
    </div>
  );
}

// ===== 支付（membership + donation，可过滤） =====

function PaymentsTab() {
  const [page, setPage] = useState(1);
  const [paymentType, setPaymentType] = useState('');
  const [status, setStatus] = useState('');
  const [data, setData] = useState<Paginated<AdminPayment> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams({ page: String(page), pageSize: String(PAGE_SIZE) });
    if (paymentType) {
      params.set('paymentType', paymentType);
    }
    if (status) {
      params.set('status', status);
    }
    void fetchPage<Paginated<AdminPayment>>(`/api/v1/admin/payments?${params}`).then((result) => {
      if (result.data) {
        setData(result.data);
        setError(null);
      } else {
        setError(result.message ?? '加载失败');
      }
    });
  }, [page, paymentType, status]);

  function onFilterChange(nextType: string, nextStatus: string) {
    setPage(1);
    setPaymentType(nextType);
    setStatus(nextStatus);
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {[
          { label: '全部', value: '' },
          { label: '会员', value: 'membership' },
          { label: '捐赠', value: 'donation' },
        ].map((option) => (
          <button
            key={option.value}
            type="button"
            onClick={() => onFilterChange(option.value, status)}
            className={`rounded-full px-3 py-1 text-xs font-semibold transition ${
              paymentType === option.value
                ? 'bg-indigo-500 text-white'
                : 'border border-slate-200 text-slate-500 hover:border-indigo-300'
            }`}
          >
            {option.label}
          </button>
        ))}
        <span className="mx-1 w-px self-stretch bg-slate-200" />
        {['', 'succeeded', 'pending', 'failed'].map((option) => (
          <button
            key={option || 'all'}
            type="button"
            onClick={() => onFilterChange(paymentType, option)}
            className={`rounded-full px-3 py-1 text-xs font-semibold transition ${
              status === option
                ? 'bg-indigo-500 text-white'
                : 'border border-slate-200 text-slate-500 hover:border-indigo-300'
            }`}
          >
            {option === '' ? '不限状态' : statusBadge(option).label}
          </button>
        ))}
      </div>
      {error && <LoadError message={error} />}
      {data &&
        (data.items.length === 0 ? (
          <p className="rounded-2xl border border-slate-100 bg-white px-4 py-6 text-center text-xs text-slate-400">
            没有符合条件的支付记录
          </p>
        ) : (
          <ul className="space-y-2">
            {data.items.map((payment) => {
              const badge = statusBadge(payment.status);
              return (
                <li
                  key={payment.id}
                  className="rounded-2xl border border-slate-100 bg-white px-4 py-3 shadow-sm"
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-slate-900">
                      {formatMoney(payment.amount, payment.currency)}
                      <span className="ml-2 text-xs font-normal text-slate-400">
                        {payment.paymentType === 'membership' ? '会员订阅' : '捐赠'}
                      </span>
                    </p>
                    <Badge label={badge.label} tone={badge.tone} />
                  </div>
                  <p className="truncate pt-1 text-xs text-slate-400">
                    {payment.userEmail} · {payment.provider} · {payment.transactionId}
                  </p>
                  <p className="text-xs text-slate-400">
                    创建 {formatDateTime(payment.createdAt)}
                    {payment.paidAt ? ` · 支付 ${formatDateTime(payment.paidAt)}` : ''}
                  </p>
                </li>
              );
            })}
          </ul>
        ))}
      {data && data.pagination.totalPages > 0 && (
        <Pager
          page={page}
          totalPages={data.pagination.totalPages}
          onChange={(next) => setPage(next)}
        />
      )}
    </div>
  );
}

// ===== 日志（webhook=支付回调 / audit=管理操作） =====

function LogsTab() {
  const [kind, setKind] = useState<'webhook' | 'audit'>('webhook');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paginated<AdminLog> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void fetchPage<Paginated<AdminLog>>(
      `/api/v1/admin/logs?page=${page}&pageSize=${PAGE_SIZE}&kind=${kind}`,
    ).then((result) => {
      if (result.data) {
        setData(result.data);
        setError(null);
      } else {
        setError(result.message ?? '加载失败');
      }
    });
  }, [page, kind]);

  function onKindChange(next: 'webhook' | 'audit') {
    setPage(1);
    setKind(next);
  }

  return (
    <div className="space-y-3">
      <div className="flex gap-2">
        {(
          [
            { key: 'webhook', label: '支付回调' },
            { key: 'audit', label: '管理操作' },
          ] as const
        ).map((option) => (
          <button
            key={option.key}
            type="button"
            onClick={() => onKindChange(option.key)}
            className={`rounded-full px-3 py-1 text-xs font-semibold transition ${
              kind === option.key
                ? 'bg-indigo-500 text-white'
                : 'border border-slate-200 text-slate-500 hover:border-indigo-300'
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>
      {error && <LoadError message={error} />}
      {data &&
        (data.items.length === 0 ? (
          <p className="rounded-2xl border border-slate-100 bg-white px-4 py-6 text-center text-xs text-slate-400">
            暂无{kind === 'webhook' ? '支付回调' : '管理操作'}日志
          </p>
        ) : (
          <ul className="space-y-2">
            {data.items.map((log) => (
              <li
                key={log.id}
                className="space-y-1 rounded-2xl border border-slate-100 bg-white px-4 py-3 shadow-sm"
              >
                <div className="flex items-center justify-between gap-2">
                  <p className="truncate text-sm font-medium text-slate-800">{log.summary}</p>
                  {log.kind === 'webhook' ? (
                    log.processed ? (
                      <Badge label="已处理" tone="green" />
                    ) : (
                      <Badge label="未通过" tone="rose" />
                    )
                  ) : (
                    <Badge label="审计" tone="amber" />
                  )}
                </div>
                <p className="text-xs text-slate-400">
                  {formatDateTime(log.createdAt)}
                  {log.reference ? ` · ${log.reference}` : ''}
                  {log.actorId != null ? ` · 操作者 #${log.actorId}` : ''}
                </p>
                {log.error && <p className="text-xs text-rose-500">{log.error}</p>}
                {log.details && (
                  <details className="text-xs text-slate-400">
                    <summary className="cursor-pointer select-none">详情</summary>
                    <pre className="mt-1 max-h-40 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-slate-50 p-2">
                      {log.details}
                    </pre>
                  </details>
                )}
              </li>
            ))}
          </ul>
        ))}
      {data && data.pagination.totalPages > 0 && (
        <Pager
          page={page}
          totalPages={data.pagination.totalPages}
          onChange={(next) => setPage(next)}
        />
      )}
    </div>
  );
}

// ===== 页面骨架 =====

function AdminView({ dashboard }: { dashboard: AdminDashboard }) {
  const [tab, setTab] = useState<Tab>('dashboard');
  return (
    <div className="mx-auto w-full max-w-3xl space-y-4 px-4 pb-10">
      <header className="flex items-center justify-between pt-6">
        <div>
          <Link href="/" className="text-xs text-slate-400 hover:text-slate-600">
            ← 返回首页
          </Link>
          <h1 className="text-xl font-bold text-slate-900">后台管理</h1>
        </div>
        <span className="text-sm font-bold text-indigo-600">GrowthKids Admin</span>
      </header>

      <nav className="flex gap-1 overflow-x-auto rounded-full border border-slate-100 bg-white p-1 shadow-sm">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setTab(item.key)}
            className={`flex-1 rounded-full px-4 py-2 text-sm font-medium transition ${
              tab === item.key
                ? 'bg-indigo-500 text-white'
                : 'text-slate-500 hover:bg-indigo-50 hover:text-indigo-600'
            }`}
          >
            {item.label}
          </button>
        ))}
      </nav>

      {tab === 'dashboard' && <DashboardTab dashboard={dashboard} />}
      {tab === 'users' && <UsersTab />}
      {tab === 'children' && <ChildrenTab />}
      {tab === 'payments' && <PaymentsTab />}
      {tab === 'logs' && <LogsTab />}
    </div>
  );
}

/** 拉取后台状态：未登录 guest；非 admin denied（后端 403 亦兜底）。 */
async function fetchAdminState(): Promise<AdminState> {
  if (!loadSession()) {
    return { kind: 'guest' };
  }
  try {
    const user = await apiFetch<UserProfile>('/api/v1/users/me');
    if (user.role !== 'admin') {
      return { kind: 'denied' };
    }
    const dashboard = await apiFetch<AdminDashboard>('/api/v1/admin/dashboard');
    return { kind: 'ready', dashboard };
  } catch (error) {
    return { kind: 'error', message: errorMessage(error) };
  }
}

export default function AdminPage() {
  const [state, setState] = useState<AdminState>({ kind: 'loading' });

  useEffect(() => {
    void fetchAdminState().then((next) => setState(next));
  }, []);

  function handleRetry() {
    setState({ kind: 'loading' });
    void fetchAdminState().then((next) => setState(next));
  }

  if (state.kind === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-slate-400">
        加载中…
      </div>
    );
  }
  if (state.kind === 'guest' || state.kind === 'denied') {
    return (
      <div className="mx-auto w-full max-w-md space-y-4 px-4 pt-24 text-center">
        <p className="text-sm text-slate-500">
          {state.kind === 'guest' ? '请先登录后再访问后台' : '该页面仅管理员可访问'}
        </p>
        <Link
          href={state.kind === 'guest' ? '/login' : '/'}
          className="mx-auto block w-full max-w-xs rounded-xl bg-indigo-500 py-3 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          {state.kind === 'guest' ? '去登录' : '返回首页'}
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
          onClick={handleRetry}
          className="rounded-xl bg-indigo-500 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          重试
        </button>
      </div>
    );
  }
  return <AdminView dashboard={state.dashboard} />;
}
