'use client';

/**
 * 孩子身高预测页（TASK-021，规范 §44）：基于已记录的身高序列估算成年身高，
 * 数据不足时引导先记录身高；结果展示复用 PredictionResult（含 §45 免责声明）。
 */

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import type { Child, HeightPrediction } from '@growthkids/types';
import { PredictionResult } from '@/components/prediction';
import { ApiError, apiFetch, errorMessage } from '@/lib/api';
import { loadSession } from '@/lib/session';

type PredictionState =
  | { kind: 'loading' }
  | { kind: 'guest' }
  | { kind: 'not-found' }
  | { kind: 'insufficient' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; child: Child; prediction: HeightPrediction };

async function fetchPredictionState(childId: string): Promise<PredictionState> {
  if (!loadSession()) {
    return { kind: 'guest' };
  }
  try {
    const [child, prediction] = await Promise.all([
      apiFetch<Child>(`/api/v1/children/${childId}`),
      apiFetch<HeightPrediction>(`/api/v1/children/${childId}/prediction`),
    ]);
    return { kind: 'ready', child, prediction };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401 || error.status === 403) {
        return { kind: 'guest' };
      }
      if (error.status === 404) {
        return { kind: 'not-found' };
      }
      if (error.code === 'PREDICTION_INSUFFICIENT_DATA') {
        return { kind: 'insufficient' };
      }
    }
    return { kind: 'error', message: errorMessage(error) };
  }
}

export default function ChildPredictionPage() {
  const { id } = useParams<{ id: string }>();
  const [state, setState] = useState<PredictionState>({ kind: 'loading' });

  useEffect(() => {
    void fetchPredictionState(id).then((next) => setState(next));
  }, [id]);

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
        <p className="text-sm text-slate-500">请先登录后查看身高预测</p>
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
  if (state.kind === 'insufficient') {
    return (
      <div className="mx-auto w-full max-w-md space-y-4 px-4 pt-24 text-center">
        <p className="text-sm text-slate-500">
          还没有可用的身高记录，先记录一次身高即可获得预测。
        </p>
        <Link
          href={`/children/${id}/growth/new`}
          className="mx-auto block w-full max-w-xs rounded-xl bg-indigo-500 py-3 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          + 记录身高
        </Link>
        <Link href="/" className="block text-sm font-medium text-indigo-500 hover:text-indigo-600">
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
            void fetchPredictionState(id).then((next) => setState(next));
          }}
          className="rounded-xl bg-indigo-500 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          重试
        </button>
      </div>
    );
  }

  const { child, prediction } = state;
  return (
    <div className="mx-auto w-full max-w-md space-y-4 px-4 py-8">
      <div className="space-y-1">
        <Link href="/" className="text-sm text-slate-400 hover:text-slate-600">
          ← 返回首页
        </Link>
        <h1 className="text-xl font-bold text-slate-900">{child.name}的身高预测</h1>
        <p className="text-sm leading-relaxed text-slate-500">
          基于已记录的身高序列与父母身高估算；记录越多，数据完整度越高。
        </p>
      </div>

      <PredictionResult prediction={prediction} />

      <div className="grid gap-2">
        <Link
          href={`/children/${child.id}/growth/new`}
          className="block rounded-xl bg-indigo-500 py-3 text-center text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          + 记录新的身高
        </Link>
        <Link
          href={`/children/${child.id}/charts`}
          className="block rounded-xl border border-indigo-200 py-3 text-center text-sm font-semibold text-indigo-600 transition hover:bg-indigo-50"
        >
          查看成长曲线
        </Link>
      </div>
    </div>
  );
}
