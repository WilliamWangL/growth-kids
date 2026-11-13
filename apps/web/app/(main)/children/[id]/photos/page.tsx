'use client';

/**
 * 成长照片页（TASK-013，规范 §46-§49/§57/§110）：
 * 客户端直传（presign → PUT 签名 URL → finalize），上传进度（§56）、
 * 异步缩略图轮询、失败重试（§57）、签名 URL 展示与删除。
 */

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import type { Child, Paginated, Photo } from '@growthkids/types';
import { ApiError, apiFetch, errorMessage } from '@/lib/api';
import {
  PHOTO_ACCEPT,
  PHOTO_MAX_BATCH,
  PHOTO_MAX_SIZE,
  normalizeMime,
  uploadOne,
} from '@/lib/media';
import { loadSession } from '@/lib/session';

const POLL_INTERVAL_MS = 2000;
const POLL_MAX_TRIES = 30;

type PhotosState =
  | { kind: 'loading' }
  | { kind: 'guest' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'ready'; child: Child; photos: Photo[] };

/** 本批上传项（§57：上传中进度 / 失败重试） */
interface UploadItem {
  key: string;
  file: File;
  filename: string;
  percent: number;
  phase: 'uploading' | 'failed';
}

async function fetchPhotosState(childId: string): Promise<PhotosState> {
  if (!loadSession()) {
    return { kind: 'guest' };
  }
  try {
    const child = await apiFetch<Child>(`/api/v1/children/${childId}`);
    const page = await apiFetch<Paginated<Photo>>(
      `/api/v1/children/${childId}/photos?page=1&pageSize=100`,
    );
    return { kind: 'ready', child, photos: page.items };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return { kind: 'not-found' };
    }
    return { kind: 'error', message: errorMessage(error) };
  }
}

function isProcessing(photo: Photo): boolean {
  return photo.processingStatus === 'pending' || photo.processingStatus === 'processing';
}

/** 照片网格项（§57）：ready 显示缩略图（无缩略图回落原图），处理中占位，失败提示 */
function PhotoTile({ photo, onDelete }: { photo: Photo; onDelete: () => void }) {
  const url = photo.thumbnailUrl ?? photo.viewUrl;
  return (
    <div className="group relative aspect-square overflow-hidden rounded-xl border border-slate-100 bg-slate-50">
      {isProcessing(photo) ? (
        <div className="flex h-full flex-col items-center justify-center gap-1 text-xs text-slate-400">
          <span className="h-5 w-5 animate-spin rounded-full border-2 border-slate-200 border-t-indigo-400" />
          处理中…
        </div>
      ) : photo.processingStatus === 'failed' ? (
        <div className="flex h-full flex-col items-center justify-center gap-1 text-xs text-slate-400">
          <span>处理失败</span>
          <button
            type="button"
            onClick={onDelete}
            className="text-indigo-500 hover:text-indigo-600"
          >
            删除
          </button>
        </div>
      ) : url ? (
        // eslint-disable-next-line @next/next/no-img-element -- 签名 URL 为短期凭证且带 query，不适合 next/image
        <img src={url} alt={photo.originalFilename} className="h-full w-full object-cover" />
      ) : (
        <div className="flex h-full items-center justify-center text-xs text-slate-400">无预览</div>
      )}
      {!isProcessing(photo) && (
        <button
          type="button"
          onClick={onDelete}
          aria-label="删除照片"
          className="absolute right-1.5 top-1.5 hidden rounded-full bg-black/50 px-2 py-0.5 text-xs text-white group-hover:block"
        >
          删除
        </button>
      )}
    </div>
  );
}

export default function GrowthPhotosPage() {
  const { id } = useParams<{ id: string }>();
  const [state, setState] = useState<PhotosState>({ kind: 'loading' });
  const [uploads, setUploads] = useState<UploadItem[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const pollTries = useRef(0);

  useEffect(() => {
    void fetchPhotosState(id).then((next) => setState(next));
  }, [id]);

  // 存在处理中的照片时轮询刷新（上限 POLL_MAX_TRIES，避免无限请求）
  useEffect(() => {
    if (state.kind !== 'ready' || !state.photos.some(isProcessing)) {
      return;
    }
    if (pollTries.current >= POLL_MAX_TRIES) {
      return;
    }
    const timer = setTimeout(() => {
      pollTries.current += 1;
      void fetchPhotosState(id).then((next) => {
        if (next.kind === 'ready') {
          setState(next);
        }
      });
    }, POLL_INTERVAL_MS);
    return () => clearTimeout(timer);
  }, [state, id]);

  async function refresh() {
    const next = await fetchPhotosState(id);
    if (next.kind === 'ready') {
      setState(next);
    }
  }

  async function runUpload(item: UploadItem): Promise<boolean> {
    const ok = await uploadOne(id, item.file, (percent) => {
      setUploads((current) =>
        current.map((u) => (u.key === item.key ? { ...u, percent } : u)),
      );
    });
    if (!ok) {
      setUploads((current) =>
        current.map((u) => (u.key === item.key ? { ...u, phase: 'failed' } : u)),
      );
    }
    return ok;
  }

  async function handleFiles(fileList: FileList | null) {
    if (!fileList || fileList.length === 0) {
      return;
    }
    setNotice(null);
    const files = Array.from(fileList).slice(0, PHOTO_MAX_BATCH);
    const valid: File[] = [];
    for (const file of files) {
      if (!PHOTO_ACCEPT.split(',').includes(normalizeMime(file))) {
        setNotice(`不支持的格式：${file.name}`);
      } else if (file.size > PHOTO_MAX_SIZE) {
        setNotice(`文件超过 10MB：${file.name}`);
      } else {
        valid.push(file);
      }
    }
    if (valid.length === 0) {
      return;
    }

    const items: UploadItem[] = valid.map((file, index) => ({
      key: `${Date.now()}-${index}-${file.name}`,
      file,
      filename: file.name,
      percent: 0,
      phase: 'uploading',
    }));
    setUploads(items);

    for (const item of items) {
      await runUpload(item);
    }
    pollTries.current = 0;
    await refresh();
    // 成功项移除，失败项保留“重新上传”入口
    setUploads((current) => current.filter((u) => u.phase === 'failed'));
  }

  async function retry(item: UploadItem) {
    setUploads((current) =>
      current.map((u) => (u.key === item.key ? { ...u, phase: 'uploading', percent: 0 } : u)),
    );
    const ok = await runUpload(item);
    if (!ok) {
      return;
    }
    pollTries.current = 0;
    await refresh();
    setUploads((current) => current.filter((u) => u.key !== item.key));
  }

  async function handleDelete(photo: Photo) {
    if (!window.confirm('确定删除这张照片吗？删除后不可恢复。')) {
      return;
    }
    try {
      await apiFetch(`/api/v1/photos/${photo.id}`, { method: 'DELETE' });
      await refresh();
    } catch (error) {
      setNotice(errorMessage(error));
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
        <p className="text-sm text-slate-500">请先登录后查看成长照片</p>
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
            void fetchPhotosState(id).then((next) => setState(next));
          }}
          className="rounded-xl bg-indigo-500 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          重试
        </button>
      </div>
    );
  }

  const { child, photos } = state;

  return (
    <div className="mx-auto w-full max-w-md space-y-4 px-4 py-6">
      <div className="space-y-1">
        <Link href="/" className="text-sm text-slate-400 hover:text-slate-600">
          ← 返回首页
        </Link>
        <h1 className="text-xl font-bold text-slate-900">{child.name}的成长照片</h1>
      </div>

      {notice && (
        <p className="rounded-xl bg-amber-50 px-3 py-2 text-xs text-amber-700">{notice}</p>
      )}

      {uploads.length > 0 && (
        <div className="space-y-2">
          {uploads.map((item) => (
            <div key={item.key} className="rounded-xl border border-slate-100 bg-white px-3 py-2">
              <div className="flex items-center justify-between text-xs text-slate-500">
                <span className="truncate">{item.filename}</span>
                {item.phase === 'uploading' ? (
                  <span>{item.percent}%</span>
                ) : (
                  <button
                    type="button"
                    onClick={() => void retry(item)}
                    className="font-medium text-indigo-500 hover:text-indigo-600"
                  >
                    重新上传
                  </button>
                )}
              </div>
              {item.phase === 'uploading' && (
                <div className="mt-1.5 h-1 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className="h-full rounded-full bg-indigo-500 transition-all"
                    style={{ width: `${item.percent}%` }}
                  />
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <input
        ref={inputRef}
        type="file"
        accept={PHOTO_ACCEPT}
        multiple
        className="hidden"
        onChange={(event) => {
          void handleFiles(event.target.files);
          event.target.value = '';
        }}
      />
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        className="w-full rounded-xl bg-indigo-500 py-3 text-sm font-semibold text-white transition hover:bg-indigo-600"
      >
        + 添加照片
      </button>
      <p className="text-center text-xs text-slate-400">
        支持 JPEG / PNG / WEBP / HEIC，单张 ≤ 10MB，每次最多 9 张
      </p>

      {photos.length === 0 ? (
        <div className="flex h-40 items-center justify-center rounded-xl border border-dashed border-slate-200 text-sm text-slate-400">
          还没有照片，记录孩子的高光时刻吧
        </div>
      ) : (
        <div className="grid grid-cols-3 gap-2">
          {photos.map((photo) => (
            <PhotoTile key={photo.id} photo={photo} onDelete={() => void handleDelete(photo)} />
          ))}
        </div>
      )}
    </div>
  );
}
