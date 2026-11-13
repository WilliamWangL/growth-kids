'use client';

/**
 * 记录成长故事页（TASK-015，规范 §114/§16）:
 * 日期/标题/内容 + 可选照片（presign 关联 memoryId，§46/§49）；
 * 创建成功后回到时间线，故事卡片与照片即时可见（§52）。
 */

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useRef, useState, type FormEvent } from 'react';
import type { Memory } from '@growthkids/types';
import { createMemorySchema } from '@growthkids/validation';
import { Field, FormError, SubmitButton, inputClassName, toFieldErrors } from '@/components/form';
import { apiFetch, errorMessage } from '@/lib/api';
import {
  PHOTO_ACCEPT,
  PHOTO_MAX_BATCH,
  PHOTO_MAX_SIZE,
  normalizeMime,
  uploadOne,
} from '@/lib/media';

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

type Phase =
  | { kind: 'idle' }
  | { kind: 'creating' }
  | { kind: 'uploading'; current: number; total: number; name: string; percent: number };

export default function NewMemoryPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const [memoryDate, setMemoryDate] = useState(todayIso());
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [phase, setPhase] = useState<Phase>({ kind: 'idle' });
  const inputRef = useRef<HTMLInputElement>(null);

  function pickFiles(fileList: FileList | null) {
    if (!fileList || fileList.length === 0) {
      return;
    }
    setNotice(null);
    const next = [...files];
    for (const file of Array.from(fileList)) {
      if (next.length >= PHOTO_MAX_BATCH) {
        setNotice(`一次最多关联 ${PHOTO_MAX_BATCH} 张照片`);
        break;
      }
      const mime = normalizeMime(file);
      if (!PHOTO_ACCEPT.split(',').includes(mime)) {
        setNotice(`不支持的格式：${file.name}`);
      } else if (file.size > PHOTO_MAX_SIZE) {
        setNotice(`文件超过 10MB：${file.name}`);
      } else {
        next.push(file);
      }
    }
    setFiles(next);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = createMemorySchema.safeParse({
      memoryDate,
      title: title.trim() === '' ? undefined : title,
      content,
    });
    if (!parsed.success) {
      setFieldErrors(toFieldErrors(parsed.error.issues));
      return;
    }
    setFieldErrors({});
    setFormError(null);
    setPhase({ kind: 'creating' });
    try {
      const memory = await apiFetch<Memory>(`/api/v1/children/${id}/memories`, {
        method: 'POST',
        body: parsed.data,
      });
      for (const [index, file] of files.entries()) {
        setPhase({
          kind: 'uploading',
          current: index + 1,
          total: files.length,
          name: file.name,
          percent: 0,
        });
        await uploadOne(
          id,
          file,
          (percent) => {
            setPhase({
              kind: 'uploading',
              current: index + 1,
              total: files.length,
              name: file.name,
              percent,
            });
          },
          { memoryId: memory.id },
        );
      }
      router.push(`/children/${id}/timeline`);
    } catch (error) {
      setFormError(errorMessage(error));
      setPhase({ kind: 'idle' });
    }
  }

  const pending = phase.kind !== 'idle';

  return (
    <div className="mx-auto w-full max-w-md space-y-4 px-4 py-8">
      <div className="space-y-1">
        <Link href={`/children/${id}/timeline`} className="text-sm text-slate-400 hover:text-slate-600">
          ← 返回时间线
        </Link>
        <h1 className="text-xl font-bold text-slate-900">记录故事</h1>
        <p className="text-sm text-slate-500">记下这一天关于孩子的小事</p>
      </div>

      <form
        onSubmit={handleSubmit}
        noValidate
        className="space-y-4 rounded-2xl border border-slate-100 bg-white p-6 shadow-sm"
      >
        <FormError message={formError} />
        <Field label="故事日期" htmlFor="memoryDate" error={fieldErrors.memoryDate}>
          <input
            id="memoryDate"
            type="date"
            max={todayIso()}
            value={memoryDate}
            onChange={(event) => setMemoryDate(event.target.value)}
            className={inputClassName}
          />
        </Field>

        <Field label="标题" htmlFor="title" error={fieldErrors.title} optional>
          <input
            id="title"
            type="text"
            maxLength={200}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="如：第一次自己穿鞋"
            className={inputClassName}
          />
        </Field>

        <Field label="内容" htmlFor="content" error={fieldErrors.content}>
          <textarea
            id="content"
            rows={5}
            maxLength={5000}
            value={content}
            onChange={(event) => setContent(event.target.value)}
            placeholder="今天发生了什么值得记住的事？"
            className={inputClassName}
          />
        </Field>

        <Field label="照片" htmlFor="photos" optional>
          <input
            ref={inputRef}
            id="photos"
            type="file"
            accept={PHOTO_ACCEPT}
            multiple
            className="hidden"
            onChange={(event) => {
              pickFiles(event.target.files);
              event.target.value = '';
            }}
          />
          <button
            type="button"
            disabled={pending}
            onClick={() => inputRef.current?.click()}
            className="w-full rounded-xl border border-dashed border-slate-200 py-2.5 text-sm text-slate-500 transition hover:border-indigo-300 hover:text-indigo-500 disabled:opacity-50"
          >
            + 添加照片（最多 {PHOTO_MAX_BATCH} 张）
          </button>
          {files.length > 0 && (
            <div className="mt-2 space-y-1.5">
              {files.map((file, index) => (
                <div
                  key={`${file.name}-${index}`}
                  className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-1.5 text-xs text-slate-500"
                >
                  <span className="truncate">{file.name}</span>
                  {!pending && (
                    <button
                      type="button"
                      onClick={() => setFiles(files.filter((_, i) => i !== index))}
                      aria-label={`移除 ${file.name}`}
                      className="ml-2 shrink-0 text-slate-400 hover:text-slate-600"
                    >
                      ×
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </Field>

        {notice && (
          <p className="rounded-xl bg-amber-50 px-3 py-2 text-xs text-amber-700">{notice}</p>
        )}

        <SubmitButton pending={pending}>
          {phase.kind === 'creating'
            ? '创建中…'
            : phase.kind === 'uploading'
              ? `上传照片 ${phase.current}/${phase.total}（${phase.percent}%）`
              : '保存故事'}
        </SubmitButton>
      </form>
    </div>
  );
}

