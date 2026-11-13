'use client';

/**
 * 成长时间线页（TASK-014，规范 §52/§53/§110/§113）：
 * 成长记录 + 成长故事合并展示（date DESC），按年-月分组（§113），
 * 卡片含月龄/照片缩略图/测量值/备注（§53）。
 */

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import type { Child, TimelineItem, TimelinePhoto, TimelineResponse } from '@growthkids/types';
import { ApiError, apiFetch, errorMessage } from '@/lib/api';
import { loadSession } from '@/lib/session';

type TimelineState =
  | { kind: 'loading' }
  | { kind: 'guest' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'ready'; child: Child; items: TimelineItem[] };

async function fetchTimelineState(childId: string): Promise<TimelineState> {
  if (!loadSession()) {
    return { kind: 'guest' };
  }
  try {
    const child = await apiFetch<Child>(`/api/v1/children/${childId}`);
    const timeline = await apiFetch<TimelineResponse>(
      `/api/v1/children/${childId}/timeline`,
    );
    return { kind: 'ready', child, items: timeline.items };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return { kind: 'not-found' };
    }
    return { kind: 'error', message: errorMessage(error) };
  }
}

/** §113 年月分组：保持后端 date DESC 顺序，按年-月归组 */
interface MonthGroup {
  key: string;
  label: string;
  items: TimelineItem[];
}

function groupByMonth(items: TimelineItem[]): MonthGroup[] {
  const groups: MonthGroup[] = [];
  const index = new Map<string, MonthGroup>();
  for (const item of items) {
    const key = item.date.slice(0, 7);
    let group = index.get(key);
    if (!group) {
      const [year, month] = key.split('-');
      group = { key, label: `${year}年${Number(month)}月`, items: [] };
      index.set(key, group);
      groups.push(group);
    }
    group.items.push(item);
  }
  return groups;
}

function formatDate(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  return `${year}年${month}月${day}日`;
}

function formatAge(ageMonths?: number): string | null {
  if (ageMonths == null) {
    return null;
  }
  return ageMonths < 24
    ? `${ageMonths}个月`
    : `${Math.floor(ageMonths / 12)}岁${ageMonths % 12}个月`;
}

function PhotoStrip({ photos }: { photos?: TimelinePhoto[] }) {
  if (!photos || photos.length === 0) {
    return null;
  }
  return (
    <div className="mt-3 flex gap-2">
      {photos.map((photo) => {
        const url = photo.thumbnailUrl ?? photo.viewUrl;
        return url ? (
          // eslint-disable-next-line @next/next/no-img-element -- 签名 URL 为短期凭证且带 query，不适合 next/image
          <img
            key={photo.id}
            src={url}
            alt="成长照片"
            className="h-16 w-16 rounded-lg border border-slate-100 object-cover"
          />
        ) : null;
      })}
    </div>
  );
}

/** §53 卡片：日期 + 月龄 + 照片 + 测量值（记录）/标题内容（故事）+ 备注 */
function TimelineCard({ item }: { item: TimelineItem }) {
  const age = formatAge(item.ageMonths);
  return (
    <div className="rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
      <div className="flex items-baseline justify-between">
        <p className="text-sm font-semibold text-slate-900">{formatDate(item.date)}</p>
        {age && <span className="text-xs text-slate-400">{age}</span>}
      </div>
      {item.type === 'memory' ? (
        <div className="mt-2">
          {item.title && <p className="text-sm font-medium text-slate-800">{item.title}</p>}
          <p className="mt-1 text-sm leading-relaxed text-slate-600">{item.content}</p>
        </div>
      ) : (
        <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-slate-600">
          {item.heightCm != null && <span>身高 {item.heightCm} cm</span>}
          {item.weightKg != null && <span>体重 {item.weightKg} kg</span>}
          {item.headCircumferenceCm != null && (
            <span>头围 {item.headCircumferenceCm} cm</span>
          )}
        </div>
      )}
      {item.note && (
        <p className="mt-2 text-sm leading-relaxed text-slate-500">{item.note}</p>
      )}
      <PhotoStrip photos={item.photos} />
    </div>
  );
}

function TimelineView({ child, items }: { child: Child; items: TimelineItem[] }) {
  const groups = groupByMonth(items);
  return (
    <div className="mx-auto w-full max-w-md space-y-5 px-4 py-6">
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          <Link href="/" className="text-sm text-slate-400 hover:text-slate-600">
            ← 返回首页
          </Link>
          <h1 className="text-xl font-bold text-slate-900">{child.name}的成长时间线</h1>
        </div>
        <div className="flex flex-col items-end gap-1.5">
          <Link
            href={`/children/${child.id}/memories/new`}
            className="rounded-xl bg-indigo-500 px-3 py-2 text-xs font-semibold text-white transition hover:bg-indigo-600"
          >
            + 记录故事
          </Link>
          <Link
            href={`/children/${child.id}/memories`}
            className="text-xs text-slate-400 hover:text-slate-600"
          >
            管理故事
          </Link>
        </div>
      </div>

      {items.length === 0 ? (
        <div className="flex h-40 items-center justify-center rounded-xl border border-dashed border-slate-200 text-sm text-slate-400">
          还没有内容，记录与故事都会出现在这里
        </div>
      ) : (
        groups.map((group) => (
          <section key={group.key} className="space-y-3">
            <h2 className="text-sm font-semibold text-slate-500">{group.label}</h2>
            <div className="space-y-3">
              {group.items.map((item, index) => (
                <TimelineCard
                  key={`${item.type}-${item.date}-${index}`}
                  item={item}
                />
              ))}
            </div>
          </section>
        ))
      )}
    </div>
  );
}

export default function GrowthTimelinePage() {
  const { id } = useParams<{ id: string }>();
  const [state, setState] = useState<TimelineState>({ kind: 'loading' });

  useEffect(() => {
    void fetchTimelineState(id).then((next) => setState(next));
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
        <p className="text-sm text-slate-500">请先登录后查看成长时间线</p>
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
            void fetchTimelineState(id).then((next) => setState(next));
          }}
          className="rounded-xl bg-indigo-500 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          重试
        </button>
      </div>
    );
  }
  return <TimelineView child={state.child} items={state.items} />;
}
