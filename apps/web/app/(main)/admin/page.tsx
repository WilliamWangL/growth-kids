'use client';

/**
 * 后台管理页（TASK-019，规范 §77/§110）：仅 admin 可访问。
 * 顶部 tab：总览（平台统计）/ 用户（角色管理）/ 孩子 / 支付 / 优惠商品 / 日志。
 * 非登录与无权限分别给出引导；后端对 /api/v1/admin/** 做 ADMIN 双重校验。
 */

import Link from 'next/link';
import { useEffect, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import type {
  AdminChild,
  AdminDashboard,
  AdminLog,
  AdminPayment,
  AdminUser,
  Deal,
  DealCategoryOption,
  Paginated,
  UserProfile,
} from '@growthkids/types';
import { dealFormSchema } from '@growthkids/validation';
import { apiFetch, errorMessage } from '@/lib/api';
import { loadSession } from '@/lib/session';

const PAGE_SIZE = 20;

type AdminState =
  | { kind: 'loading' }
  | { kind: 'guest' }
  | { kind: 'denied' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; dashboard: AdminDashboard };

type Tab = 'dashboard' | 'users' | 'children' | 'payments' | 'deals' | 'logs';

const TABS: { key: Tab; label: string }[] = [
  { key: 'dashboard', label: '总览' },
  { key: 'users', label: '用户' },
  { key: 'children', label: '孩子' },
  { key: 'payments', label: '支付' },
  { key: 'deals', label: '优惠商品' },
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

// ===== 优惠商品（TASK-022 后台 CRUD） =====

const dealInputClass =
  'w-full rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs text-slate-700 focus:border-indigo-300 focus:outline-none';

/** 分类接口异常时的兜底（与后端 DealCategory 保持同步） */
const FALLBACK_DEAL_CATEGORIES: DealCategoryOption[] = [
  { code: 'toys', label: '玩具' },
  { code: 'books', label: '绘本' },
  { code: 'clothing', label: '童装' },
  { code: 'gear', label: '出行装备' },
  { code: 'nutrition', label: '营养' },
  { code: 'care', label: '洗护' },
  { code: 'other', label: '其他' },
];

interface DealFormState {
  title: string;
  brand: string;
  category: string;
  minAgeMonths: string;
  maxAgeMonths: string;
  originalPrice: string;
  dealPrice: string;
  merchant: string;
  imageUrl: string;
  purchaseUrl: string;
  description: string;
  featured: boolean;
  status: string;
}

function emptyDealForm(): DealFormState {
  return {
    title: '',
    brand: '',
    category: 'toys',
    minAgeMonths: '',
    maxAgeMonths: '',
    originalPrice: '',
    dealPrice: '',
    merchant: '',
    imageUrl: '',
    purchaseUrl: '',
    description: '',
    featured: false,
    status: 'active',
  };
}

function dealToForm(deal: Deal): DealFormState {
  const text = (value: number | undefined) => (value == null ? '' : String(value));
  return {
    title: deal.title,
    brand: deal.brand ?? '',
    category: deal.category,
    minAgeMonths: text(deal.minAgeMonths),
    maxAgeMonths: text(deal.maxAgeMonths),
    originalPrice: text(deal.originalPrice),
    dealPrice: text(deal.dealPrice),
    merchant: deal.merchant ?? '',
    imageUrl: deal.imageUrl ?? '',
    purchaseUrl: deal.purchaseUrl,
    description: deal.description ?? '',
    featured: deal.featured,
    status: deal.status,
  };
}

function DealField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block text-xs text-slate-500">
      <span>{label}</span>
      <div className="mt-1">{children}</div>
    </label>
  );
}

function DealFormPanel({
  deal,
  categories,
  onSaved,
  onCancel,
}: {
  deal: Deal | null;
  categories: DealCategoryOption[];
  onSaved: (message: string) => void;
  onCancel: () => void;
}) {
  const [form, setForm] = useState<DealFormState>(deal ? dealToForm(deal) : emptyDealForm());
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function update<K extends keyof DealFormState>(key: K, value: DealFormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function submit() {
    const parsed = dealFormSchema.safeParse(form);
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? '请检查表单');
      return;
    }
    const values = parsed.data;
    const body = {
      title: values.title,
      brand: values.brand || undefined,
      category: values.category,
      minAgeMonths: values.minAgeMonths,
      maxAgeMonths: values.maxAgeMonths,
      originalPrice: values.originalPrice,
      dealPrice: values.dealPrice,
      merchant: values.merchant || undefined,
      imageUrl: values.imageUrl || undefined,
      purchaseUrl: values.purchaseUrl,
      description: values.description || undefined,
      featured: values.featured ?? false,
      // 新建时后端默认 active；编辑时允许上下架
      ...(deal ? { status: form.status } : {}),
    };
    setBusy(true);
    setError(null);
    try {
      if (deal) {
        await apiFetch<Deal>(`/api/v1/admin/deals/${deal.id}`, { method: 'PATCH', body });
      } else {
        await apiFetch<Deal>('/api/v1/admin/deals', { method: 'POST', body });
      }
      onSaved(deal ? '已更新商品' : '已新建商品');
    } catch (err) {
      setError(errorMessage(err));
    }
    setBusy(false);
  }

  return (
    <form
      onSubmit={(event: FormEvent) => {
        event.preventDefault();
        void submit();
      }}
      className="space-y-3 rounded-2xl border border-indigo-100 bg-white p-4 shadow-sm"
    >
      <p className="text-sm font-semibold text-slate-900">
        {deal ? `编辑：${deal.title}` : '新建优惠商品'}
      </p>
      <DealField label="商品名称">
        <input
          className={dealInputClass}
          value={form.title}
          onChange={(event) => update('title', event.target.value)}
        />
      </DealField>
      <div className="grid grid-cols-2 gap-2">
        <DealField label="品牌">
          <input
            className={dealInputClass}
            value={form.brand}
            onChange={(event) => update('brand', event.target.value)}
          />
        </DealField>
        <DealField label="分类">
          <select
            className={dealInputClass}
            value={form.category}
            onChange={(event) => update('category', event.target.value)}
          >
            {categories.map((item) => (
              <option key={item.code} value={item.code}>
                {item.label}
              </option>
            ))}
          </select>
        </DealField>
        <DealField label="最小月龄">
          <input
            inputMode="numeric"
            className={dealInputClass}
            value={form.minAgeMonths}
            onChange={(event) => update('minAgeMonths', event.target.value)}
          />
        </DealField>
        <DealField label="最大月龄">
          <input
            inputMode="numeric"
            className={dealInputClass}
            value={form.maxAgeMonths}
            onChange={(event) => update('maxAgeMonths', event.target.value)}
          />
        </DealField>
        <DealField label="原价（USD，可空）">
          <input
            inputMode="decimal"
            className={dealInputClass}
            value={form.originalPrice}
            onChange={(event) => update('originalPrice', event.target.value)}
          />
        </DealField>
        <DealField label="优惠价（USD）">
          <input
            inputMode="decimal"
            className={dealInputClass}
            value={form.dealPrice}
            onChange={(event) => update('dealPrice', event.target.value)}
          />
        </DealField>
        <DealField label="商家">
          <input
            className={dealInputClass}
            value={form.merchant}
            onChange={(event) => update('merchant', event.target.value)}
          />
        </DealField>
        <DealField label="图片地址">
          <input
            className={dealInputClass}
            value={form.imageUrl}
            onChange={(event) => update('imageUrl', event.target.value)}
          />
        </DealField>
      </div>
      <DealField label="购买链接（http/https）">
        <input
          className={dealInputClass}
          value={form.purchaseUrl}
          onChange={(event) => update('purchaseUrl', event.target.value)}
        />
      </DealField>
      <DealField label="商品描述">
        <textarea
          rows={3}
          className={dealInputClass}
          value={form.description}
          onChange={(event) => update('description', event.target.value)}
        />
      </DealField>
      <div className="flex items-center gap-4 text-xs text-slate-600">
        <label className="flex items-center gap-1.5">
          <input
            type="checkbox"
            checked={form.featured}
            onChange={(event) => update('featured', event.target.checked)}
          />
          置顶展示
        </label>
        {deal && (
          <label className="flex items-center gap-1.5">
            状态
            <select
              className="rounded-lg border border-slate-200 px-2 py-1 text-xs"
              value={form.status}
              onChange={(event) => update('status', event.target.value)}
            >
              <option value="active">在售</option>
              <option value="inactive">下架</option>
            </select>
          </label>
        )}
      </div>
      {error && <p className="rounded-xl bg-rose-50 px-3 py-2 text-xs text-rose-600">{error}</p>}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={busy}
          className="flex-1 rounded-xl bg-indigo-500 py-2 text-xs font-semibold text-white transition hover:bg-indigo-600 disabled:opacity-50"
        >
          {busy ? '保存中…' : '保存'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="flex-1 rounded-xl border border-slate-200 py-2 text-xs font-semibold text-slate-600 transition hover:border-slate-300"
        >
          取消
        </button>
      </div>
    </form>
  );
}

function DealsTab() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(1);
  const [version, setVersion] = useState(0);
  const [data, setData] = useState<Paginated<Deal> | null>(null);
  const [categories, setCategories] = useState<DealCategoryOption[]>(FALLBACK_DEAL_CATEGORIES);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [editing, setEditing] = useState<Deal | 'new' | null>(null);
  const [confirmId, setConfirmId] = useState<number | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    void apiFetch<DealCategoryOption[]>('/api/v1/deals/categories', { auth: false })
      .then((items) => {
        if (items.length > 0) {
          setCategories(items);
        }
      })
      // 分类接口异常时保留兜底选项，不阻塞表单
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    const query = `page=${page}&pageSize=${PAGE_SIZE}${status ? `&status=${status}` : ''}`;
    void fetchPage<Paginated<Deal>>(`/api/v1/admin/deals?${query}`).then((result) => {
      if (result.data) {
        setData(result.data);
        setError(null);
      } else {
        setError(result.message ?? '加载失败');
      }
    });
  }, [page, status, version]);

  function handleSaved(message: string) {
    setEditing(null);
    setNotice(message);
    setVersion((value) => value + 1);
  }

  async function toggleStatus(deal: Deal) {
    setBusyId(deal.id);
    setNotice(null);
    try {
      const next = deal.status === 'active' ? 'inactive' : 'active';
      await apiFetch<Deal>(`/api/v1/admin/deals/${deal.id}`, {
        method: 'PATCH',
        body: { status: next },
      });
      setNotice(next === 'active' ? '已上架' : '已下架');
      setVersion((value) => value + 1);
    } catch (err) {
      setNotice(errorMessage(err));
    }
    setBusyId(null);
  }

  async function remove(deal: Deal) {
    // 两段式确认：第一次点击变为「确认删除」，避免误删
    if (confirmId !== deal.id) {
      setConfirmId(deal.id);
      return;
    }
    setBusyId(deal.id);
    setConfirmId(null);
    setNotice(null);
    try {
      await apiFetch<void>(`/api/v1/admin/deals/${deal.id}`, { method: 'DELETE' });
      setNotice('已删除商品');
      setVersion((value) => value + 1);
    } catch (err) {
      setNotice(errorMessage(err));
    }
    setBusyId(null);
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(1);
          }}
          className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-600"
        >
          <option value="">全部状态</option>
          <option value="active">在售</option>
          <option value="inactive">已下架</option>
        </select>
        <button
          type="button"
          onClick={() => setEditing('new')}
          className="rounded-xl bg-indigo-500 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-indigo-600"
        >
          + 新建商品
        </button>
      </div>

      {notice && (
        <p className="rounded-xl bg-indigo-50 px-3 py-2 text-xs text-indigo-600">{notice}</p>
      )}
      {error && <LoadError message={error} />}

      {editing && (
        <DealFormPanel
          // 切换编辑对象时重建表单状态
          key={editing === 'new' ? 'new' : editing.id}
          deal={editing === 'new' ? null : editing}
          categories={categories}
          onSaved={handleSaved}
          onCancel={() => setEditing(null)}
        />
      )}

      {data &&
        (data.items.length === 0 ? (
          <p className="rounded-2xl border border-slate-100 bg-white px-4 py-6 text-center text-xs text-slate-400">
            暂无商品，点击「新建商品」添加
          </p>
        ) : (
          <ul className="space-y-2">
            {data.items.map((deal) => (
              <li
                key={deal.id}
                className="space-y-2 rounded-2xl border border-slate-100 bg-white px-4 py-3 shadow-sm"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-slate-900">{deal.title}</p>
                    <p className="truncate text-xs text-slate-400">
                      {deal.categoryLabel} · {formatMoney(deal.dealPrice, deal.currency)}
                      {deal.originalPrice != null &&
                        ` / 原价 ${formatMoney(deal.originalPrice, deal.currency)}`}
                      {deal.merchant ? ` · ${deal.merchant}` : ''}
                    </p>
                  </div>
                  <div className="flex shrink-0 gap-1.5">
                    {deal.featured && <Badge label="置顶" tone="indigo" />}
                    {deal.status === 'active' ? (
                      <Badge label="在售" tone="green" />
                    ) : (
                      <Badge label="已下架" tone="gray" />
                    )}
                  </div>
                </div>
                <div className="flex items-center justify-between gap-2 text-xs text-slate-400">
                  <span>更新 {formatDateTime(deal.updatedAt)}</span>
                  <span className="flex shrink-0 gap-1.5">
                    <button
                      type="button"
                      onClick={() => setEditing(deal)}
                      className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-600 transition hover:border-indigo-300 hover:text-indigo-600"
                    >
                      编辑
                    </button>
                    <button
                      type="button"
                      disabled={busyId === deal.id}
                      onClick={() => void toggleStatus(deal)}
                      className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-600 transition hover:border-indigo-300 hover:text-indigo-600 disabled:opacity-50"
                    >
                      {deal.status === 'active' ? '下架' : '上架'}
                    </button>
                    <button
                      type="button"
                      disabled={busyId === deal.id}
                      onClick={() => void remove(deal)}
                      className={`rounded-lg border px-2.5 py-1 text-xs font-medium transition disabled:opacity-50 ${
                        confirmId === deal.id
                          ? 'border-rose-300 bg-rose-50 text-rose-600'
                          : 'border-slate-200 text-slate-600 hover:border-rose-300 hover:text-rose-600'
                      }`}
                    >
                      {confirmId === deal.id ? '确认删除' : '删除'}
                    </button>
                  </span>
                </div>
              </li>
            ))}
          </ul>
        ))}
      {data && data.pagination.totalPages > 1 && (
        <Pager page={page} totalPages={data.pagination.totalPages} onChange={setPage} />
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
      {tab === 'deals' && <DealsTab />}
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
