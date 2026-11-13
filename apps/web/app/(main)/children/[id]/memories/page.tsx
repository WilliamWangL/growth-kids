'use client';

/**
 * 成长故事管理页（TASK-015，规范 §114）：
 * 列表（date DESC，图表场景一次全量）+ 内联编辑（PATCH 缺省不变语义）+ 删除（照片解关联保留）。
 */

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState, type FormEvent } from 'react';
import type { Child, Memory, Paginated } from '@growthkids/types';
import { createMemorySchema } from '@growthkids/validation';
import { Field, FormError, inputClassName, toFieldErrors } from '@/components/form';
import { ApiError, apiFetch, errorMessage } from '@/lib/api';
import { loadSession } from '@/lib/session';

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

type MemoriesState =
  | { kind: 'loading' }
  | { kind: 'guest' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'ready'; child: Child; memories: Memory[] };

async function fetchMemoriesState(childId: string): Promise<MemoriesState> {
  if (!loadSession()) {
    return { kind: 'guest' };
  }
  try {
    const child = await apiFetch<Child>(`/api/v1/children/${childId}`);
    const page = await apiFetch<Paginated<Memory>>(
      `/api/v1/children/${childId}/memories?page=1&pageSize=100`,
    );
    return { kind: 'ready', child, memories: page.items };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return { kind: 'not-found' };
    }
    return { kind: 'error', message: errorMessage(error) };
  }
}

function formatDate(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  return `${year}年${month}月${day}日`;
}

function MemoryCard({
  memory,
  editing,
  onEdit,
  onDelete,
  onSave,
  onCancel,
}: {
  memory: Memory;
  editing: boolean;
  onEdit: () => void;
  onDelete: () => void;
  onSave: (values: { memoryDate: string; title: string; content: string }) => Promise<string | null>;
  onCancel: () => void;
}) {
  const [editDate, setEditDate] = useState(memory.memoryDate);
  const [editTitle, setEditTitle] = useState(memory.title ?? '');
  const [editContent, setEditContent] = useState(memory.content);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = createMemorySchema.safeParse({
      memoryDate: editDate,
      title: editTitle.trim() === '' ? '' : editTitle,
      content: editContent,
    });
    if (!parsed.success) {
      setFieldErrors(toFieldErrors(parsed.error.issues));
      return;
    }
    setFieldErrors({});
    setFormError(null);
    setSaving(true);
    const error = await onSave({
      memoryDate: parsed.data.memoryDate,
      title: parsed.data.title ?? '',
      content: parsed.data.content,
    });
    setSaving(false);
    if (error !== null) {
      setFormError(error);
    }
  }

  if (!editing) {
    return (
      <div className="rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="text-sm font-semibold text-slate-900">{formatDate(memory.memoryDate)}</p>
            {memory.title && (
              <p className="mt-1 text-sm font-medium text-slate-800">{memory.title}</p>
            )}
          </div>
          <div className="flex shrink-0 gap-3 text-xs">
            <button
              type="button"
              onClick={onEdit}
              className="font-medium text-indigo-500 hover:text-indigo-600"
            >
              编辑
            </button>
            <button
              type="button"
              onClick={onDelete}
              className="font-medium text-rose-500 hover:text-rose-600"
            >
              删除
            </button>
          </div>
        </div>
        <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-slate-600">
          {memory.content}
        </p>
      </div>
    );
  }

  return (
    <form
      onSubmit={handleSave}
      noValidate
      className="space-y-3 rounded-2xl border border-indigo-100 bg-white p-4 shadow-sm"
    >
      <FormError message={formError} />
      <Field label="故事日期" htmlFor={`memory-date-${memory.id}`} error={fieldErrors.memoryDate}>
        <input
          id={`memory-date-${memory.id}`}
          type="date"
          max={todayIso()}
          value={editDate}
          onChange={(event) => setEditDate(event.target.value)}
          className={inputClassName}
        />
      </Field>
      <Field label="标题" htmlFor={`memory-title-${memory.id}`} error={fieldErrors.title} optional>
        <input
          id={`memory-title-${memory.id}`}
          type="text"
          maxLength={200}
          value={editTitle}
          onChange={(event) => setEditTitle(event.target.value)}
          placeholder="清空标题则移除标题"
          className={inputClassName}
        />
      </Field>
      <Field
        label="内容"
        htmlFor={`memory-content-${memory.id}`}
        error={fieldErrors.content}
      >
        <textarea
          id={`memory-content-${memory.id}`}
          rows={4}
          maxLength={5000}
          value={editContent}
          onChange={(event) => setEditContent(event.target.value)}
          className={inputClassName}
        />
      </Field>
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={saving}
          className="flex-1 rounded-xl bg-indigo-500 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600 disabled:opacity-50"
        >
          {saving ? '保存中…' : '保存'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-medium text-slate-500 transition hover:bg-slate-50"
        >
          取消
        </button>
      </div>
    </form>
  );
}

function MemoriesView({ child, memories }: { child: Child; memories: Memory[] }) {
  const { id } = useParams<{ id: string }>();
  const [list, setList] = useState(memories);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function handleSave(
    memoryId: number,
    values: { memoryDate: string; title: string; content: string },
  ): Promise<string | null> {
    try {
      const updated = await apiFetch<Memory>(`/api/v1/memories/${memoryId}`, {
        method: 'PATCH',
        body: values,
      });
      setList((current) =>
        current
          .map((memory) => (memory.id === memoryId ? updated : memory))
          .sort((a, b) => (a.memoryDate < b.memoryDate ? 1 : a.memoryDate > b.memoryDate ? -1 : b.id - a.id)),
      );
      setEditingId(null);
      return null;
    } catch (error) {
      return errorMessage(error);
    }
  }

  async function handleDelete(memory: Memory) {
    if (!window.confirm('确定删除这篇故事吗？关联的照片会保留在相册中。')) {
      return;
    }
    try {
      await apiFetch(`/api/v1/memories/${memory.id}`, { method: 'DELETE' });
      setList((current) => current.filter((item) => item.id !== memory.id));
    } catch (error) {
      setNotice(errorMessage(error));
    }
  }

  return (
    <div className="mx-auto w-full max-w-md space-y-4 px-4 py-6">
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          <Link href="/" className="text-sm text-slate-400 hover:text-slate-600">
            ← 返回首页
          </Link>
          <h1 className="text-xl font-bold text-slate-900">{child.name}的成长故事</h1>
        </div>
        <Link
          href={`/children/${id}/memories/new`}
          className="rounded-xl bg-indigo-500 px-3 py-2 text-xs font-semibold text-white transition hover:bg-indigo-600"
        >
          + 新建故事
        </Link>
      </div>

      {notice && (
        <p className="rounded-xl bg-amber-50 px-3 py-2 text-xs text-amber-700">{notice}</p>
      )}

      {list.length === 0 ? (
        <div className="flex h-40 items-center justify-center rounded-xl border border-dashed border-slate-200 text-sm text-slate-400">
          还没有故事，点右上角记录第一篇吧
        </div>
      ) : (
        <div className="space-y-3">
          {list.map((memory) => (
            <MemoryCard
              key={memory.id}
              memory={memory}
              editing={editingId === memory.id}
              onEdit={() => setEditingId(memory.id)}
              onDelete={() => void handleDelete(memory)}
              onSave={(values) => handleSave(memory.id, values)}
              onCancel={() => setEditingId(null)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default function GrowthMemoriesPage() {
  const { id } = useParams<{ id: string }>();
  const [state, setState] = useState<MemoriesState>({ kind: 'loading' });

  useEffect(() => {
    void fetchMemoriesState(id).then((next) => setState(next));
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
        <p className="text-sm text-slate-500">请先登录后查看成长故事</p>
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
            void fetchMemoriesState(id).then((next) => setState(next));
          }}
          className="rounded-xl bg-indigo-500 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          重试
        </button>
      </div>
    );
  }
  return <MemoriesView child={state.child} memories={state.memories} />;
}

