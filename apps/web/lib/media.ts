/**
 * 媒体直传共享工具（TASK-013/015，规范 §46/§49/§56）：
 * photos 页与 memories/new 页共用的 MIME 判定、带进度的直传与完整上传流程。
 */

import type { PresignPhotoResponse } from '@growthkids/types';
import { API_BASE_URL, apiFetch } from './api';

export const PHOTO_ACCEPT = 'image/jpeg,image/png,image/webp,image/heic';
export const PHOTO_MAX_SIZE = 10 * 1024 * 1024; // §49：单张 ≤ 10MB
export const PHOTO_MAX_BATCH = 9; // §49：单次最多 9 张

/** MIME 兜底：桌面浏览器对 HEIC 常给出空 type，按扩展名推断 */
export function normalizeMime(file: File): string {
  if (file.type) {
    return file.type;
  }
  const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
  const byExt: Record<string, string> = {
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    png: 'image/png',
    webp: 'image/webp',
    heic: 'image/heic',
    heif: 'image/heic',
  };
  return byExt[ext] ?? '';
}

/** 直传签名 URL（§46）；XMLHttpRequest 提供上传进度（§56） */
export function putWithProgress(
  path: string,
  file: File,
  onProgress: (percent: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', `${API_BASE_URL}${path}`);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new Error(`上传失败（${xhr.status}）`));
      }
    };
    xhr.onerror = () => reject(new Error('网络错误，请重试'));
    xhr.send(file);
  });
}

/** 单张完整流程：presign（含可选关联目标）→ 直传 → finalize；任一步失败返回 false */
export async function uploadOne(
  childId: string,
  file: File,
  onProgress: (percent: number) => void,
  target?: { growthRecordId?: number; memoryId?: number },
): Promise<boolean> {
  try {
    const presigned = await apiFetch<PresignPhotoResponse>(
      `/api/v1/children/${childId}/photos/presign`,
      {
        method: 'POST',
        body: {
          filename: file.name,
          mimeType: normalizeMime(file),
          size: file.size,
          ...(target?.growthRecordId != null ? { growthRecordId: target.growthRecordId } : {}),
          ...(target?.memoryId != null ? { memoryId: target.memoryId } : {}),
        },
      },
    );
    await putWithProgress(presigned.uploadUrl, file, onProgress);
    await apiFetch(`/api/v1/photos/${presigned.photoId}/finalize`, { method: 'POST' });
    return true;
  } catch {
    return false;
  }
}
