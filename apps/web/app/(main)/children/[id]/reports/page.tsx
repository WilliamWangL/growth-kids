'use client';

/**
 * 年度报告页（TASK-018，规范 §98/§99/§110）：
 * 选择年份创建报告（Pro 权益）→ 异步生成（pending/generating）→ 轮询至
 * ready → 携 token 下载 PDF（§99 report-generation）。
 */

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import type { Child, GrowthReport, Paginated, ReportStatus } from '@growthkids/types';
import { createReportSchema } from '@growthkids/validation';
import { ApiError, API_BASE_URL, apiFetch, errorMessage } from '@/lib/api';
import { loadSession } from '@/lib/session';

const POLL_INTERVAL_MS = 2000;
const POLL_MAX_TRIES = 60; // 2 分钟上限（首次生成含字体加载，留足余量）

/** 可选年份：当年起最近 5 年（§98 year 1900-2100，前端收敛到常用区间） */
const YEAR_OPTIONS = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() - i);

type ReportsState =
  | { kind: 'loading' }
  | { kind: 'guest' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'ready'; child: Child; reports: GrowthReport[] };

async function fetchReportsState(childId: string): Promise<ReportsState> {
  if (!loadSession()) {
    return { kind: 'guest' };
  }
  try {
    const child = await apiFetch<Child>(`/api/v1/children/${childId}`);
    const page = await apiFetch<Paginated<GrowthReport>>(
      `/api/v1/children/${childId}/reports?page=1&pageSize=20`,
    );
    return { kind: 'ready', child, reports: page.items };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return { kind: 'not-found' };
    }
    return { kind: 'error', message: errorMessage(error) };
  }
}

function isGenerating(status: ReportStatus): boolean {
  return status === 'pending' || status === 'generating';
}

const STATUS_LABEL: Record<ReportStatus, string> = {
  pending: '排队中',
  generating: '生成中',
  ready: '可下载',
  failed: '生成失败',
};

const STATUS_STYLE: Record<ReportStatus, string> = {
  pending: 'bg-slate-100 text-slate-500',
  generating: 'bg-amber-50 text-amber-600',
  ready: 'bg-emerald-50 text-emerald-600',
  failed: 'bg-red-50 text-red-500',
};

/** 携 token 下载 PDF：apiFetch 只处理 JSON，二进制流走原始 fetch → blob */
async function downloadReport(report: GrowthReport) {
  const token = loadSession()?.accessToken;
  const response = await fetch(`${API_BASE_URL}/api/v1/reports/${report.id}/download`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) {
    throw new ApiError(
      response.status,
      'REPORT_DOWNLOAD_FAILED',
      `下载失败（HTTP ${response.status}）`,
    );
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `growthkids-report-${report.year}.pdf`;
  anchor.click();
  URL.revokeObjectURL(url);
}

export default function ReportsPage() {
  const { id } = useParams<{ id: string }>();
  const [state, setState] = useState<ReportsState>({ kind: 'loading' });
  const [year, setYear] = useState<number>(YEAR_OPTIONS[0]);
  const [notice, setNotice] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [downloadingId, setDownloadingId] = useState<number | null>(null);
  const [pollTries, setPollTries] = useState(0);

  useEffect(() => {
    void fetchReportsState(id).then((next) => setState(next));
  }, [id]);

  // 存在生成中的报告时轮询刷新（§99 异步生成，前端轮询状态）
  useEffect(() => {
    if (state.kind !== 'ready' || !state.reports.some((r) => isGenerating(r.status))) {
      return;
    }
    if (pollTries >= POLL_MAX_TRIES) {
      return;
    }
    const timer = setTimeout(() => {
      setPollTries((n) => n + 1);
      void fetchReportsState(id).then((next) => {
        if (next.kind === 'ready') {
          setState(next);
        }
      });
    }, POLL_INTERVAL_MS);
    return () => clearTimeout(timer);
  }, [state, id, pollTries]);

  async function handleCreate() {
    const parsed = createReportSchema.safeParse({ year });
    if (!parsed.success) {
      setNotice(parsed.error.issues[0]?.message ?? '请选择报告年份');
      return;
    }
    setNotice(null);
    setCreating(true);
    try {
      await apiFetch<GrowthReport>(`/api/v1/children/${id}/reports`, {
        method: 'POST',
        body: { year },
      });
      setPollTries(0);
      const next = await fetchReportsState(id);
      if (next.kind === 'ready') {
        setState(next);
      }
    } catch (error) {
      setNotice(errorMessage(error));
    } finally {
      setCreating(false);
    }
  }

  async function handleDownload(report: GrowthReport) {
    if (report.status !== 'ready') {
      return;
    }
    setDownloadingId(report.id);
    setNotice(null);
    try {
      await downloadReport(report);
    } catch (error) {
      setNotice(errorMessage(error));
    } finally {
      setDownloadingId(null);
    }
  }

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
        <p className="text-sm text-slate-500">请先登录后查看年度报告</p>
        <Link
          href="/login"
          className="mx-auto block w-full max-w-xs rounded-xl bg-indigo-500 py-3 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          去登录
        </Link>
      </div>
    );
  }
  if (state.kind === 'not-found') {
    return (
      <div className="mx-auto w-full max-w-md space-y-4 px-4 pt-24 text-center">
        <p className="text-sm text-slate-500">孩子不存在或无权访问</p>
        <Link href="/" className="text-sm font-medium text-indigo-500 hover:text-indigo-600">
          返回首页
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
            void fetchReportsState(id).then((next) => setState(next));
          }}
          className="rounded-xl bg-indigo-500 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          重试
        </button>
      </div>
    );
  }

  const { child, reports } = state;

  return (
    <div className="mx-auto w-full max-w-md space-y-4 px-4 py-6">
      <div className="space-y-1">
        <Link href="/" className="text-sm text-slate-400 hover:text-slate-600">
          ← 返回首页
        </Link>
        <h1 className="text-xl font-bold text-slate-900">{child.name}的年度报告</h1>
        <p className="text-xs text-slate-400">
          汇总一年的身高体重变化、照片与故事，生成可下载的 PDF 纪念册（Pro 权益）
        </p>
      </div>

      {notice && (
        <p className="rounded-xl bg-amber-50 px-3 py-2 text-xs text-amber-700">
          {notice}{' '}
          {notice === '已达免费版上限，升级 Pro 解锁更多空间' && (
            <Link href="/membership" className="font-semibold underline">
              去升级 →
            </Link>
          )}
        </p>
      )}

      <div className="flex items-center gap-2 rounded-2xl border border-slate-100 bg-white px-4 py-3 shadow-sm">
        <label htmlFor="report-year" className="shrink-0 text-sm text-slate-600">
          报告年份
        </label>
        <select
          id="report-year"
          value={year}
          onChange={(event) => setYear(Number(event.target.value))}
          className="flex-1 rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-2 text-sm text-slate-700"
        >
          {YEAR_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option} 年
            </option>
          ))}
        </select>
        <button
          type="button"
          disabled={creating}
          onClick={() => void handleCreate()}
          className="shrink-0 rounded-xl bg-indigo-500 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600 disabled:opacity-50"
        >
          {creating ? '创建中…' : reports.some((r) => r.year === year) ? '重新生成' : '生成报告'}
        </button>
      </div>

      {reports.length === 0 ? (
        <div className="flex h-40 items-center justify-center rounded-xl border border-dashed border-slate-200 text-center text-sm text-slate-400">
          还没有报告，选择年份生成第一份吧
        </div>
      ) : (
        <ul className="space-y-2">
          {reports.map((report) => (
            <li
              key={report.id}
              className="flex items-center justify-between rounded-2xl border border-slate-100 bg-white px-4 py-3.5 shadow-sm"
            >
              <div className="min-w-0">
                <p className="text-sm font-medium text-slate-700">{report.year} 年度报告</p>
                <p className="mt-0.5 text-xs text-slate-400">
                  创建于 {new Date(report.createdAt).toLocaleDateString()}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <span
                  className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[report.status]}`}
                >
                  {STATUS_LABEL[report.status]}
                </span>
                {isGenerating(report.status) && (
                  <span className="h-2 w-2 animate-spin rounded-full border-2 border-amber-300 border-t-amber-500" />
                )}
                {report.status === 'ready' && (
                  <button
                    type="button"
                    onClick={() => void handleDownload(report)}
                    className="rounded-lg bg-indigo-500 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-indigo-600 disabled:opacity-50"
                  >
                    {downloadingId === report.id ? '下载中…' : '下载 PDF'}
                  </button>
                )}
                {report.status === 'failed' && (
                  <span className="text-xs text-slate-400">可重新生成重试</span>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
