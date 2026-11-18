import type { ApiErrorResponse } from '@growthkids/types';
import { clearSession, loadSession, saveSession } from './session';

/** API 根地址（媒体直传等非 JSON 请求也使用） */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8082';

/** API 错误（§68 统一错误结构）：status 为 HTTP 状态码，code 为业务错误码 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** 是否附 token 并在 401 时自动刷新（默认 true；登录/注册传 false） */
  auth?: boolean;
}

let refreshInFlight: Promise<string | null> | null = null;

function send(path: string, method: string, body: unknown, token?: string): Promise<Response> {
  return fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

/** refresh token rotation（§26）：并发 401 共享同一次刷新请求 */
function refreshAccessToken(): Promise<string | null> {
  refreshInFlight ??= (async () => {
    const session = loadSession();
    if (!session) {
      return null;
    }
    const response = await send('/api/v1/auth/refresh', 'POST', {
      refreshToken: session.refreshToken,
    });
    if (!response.ok) {
      clearSession();
      return null;
    }
    const data = (await response.json()) as { accessToken: string; refreshToken: string };
    saveSession({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    return data.accessToken;
  })().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

/**
 * 统一请求入口：
 * - 默认附 Authorization；401 时用 refresh token 续期并重试一次（§26）
 * - 204 返回 undefined；失败抛 ApiError（解析 §68 统一错误结构）
 */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true } = options;
  let token = auth ? (loadSession()?.accessToken ?? undefined) : undefined;

  let response = await send(path, method, body, token);
  if (response.status === 401 && auth && loadSession()) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      token = refreshed;
      response = await send(path, method, body, token);
    }
  }

  if (response.status === 204) {
    return undefined as T;
  }
  const data: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const error = (data as ApiErrorResponse | null)?.error;
    throw new ApiError(
      response.status,
      error?.code ?? 'UNKNOWN',
      error?.message ?? `请求失败（HTTP ${response.status}）`,
    );
  }
  return data as T;
}

/** 退出登录（§27）：先撤销 refresh token，再清本地会话；撤销失败不阻塞登出 */
export async function logout(): Promise<void> {
  const session = loadSession();
  try {
    if (session) {
      await apiFetch<void>('/api/v1/auth/logout', {
        method: 'POST',
        body: { refreshToken: session.refreshToken },
      });
    }
  } catch {
    // 忽略：本地会话无论如何清除
  } finally {
    clearSession();
  }
}

/** 业务错误码 → 用户可读文案（后端消息为英文，此处补充高频场景） */
const ERROR_MESSAGES: Record<string, string> = {
  AUTH_EMAIL_EXISTS: '该邮箱已注册，请直接登录',
  AUTH_INVALID_CREDENTIALS: '邮箱或密码不正确',
  AUTH_TOO_MANY_ATTEMPTS: '尝试次数过多，请稍后再试',
  CHILD_LIMIT_REACHED: '当前套餐的孩子数量已达上限',
  MEMBERSHIP_REQUIRED: '已达免费版上限，升级 Pro 解锁更多空间',
  DONATION_INVALID_AMOUNT: '捐赠金额需在 $0.50–$10,000 之间（最多 2 位小数，仅支持 USD）',
  DONATION_DISABLED: '捐赠暂未开放，感谢你的支持',
  REPORT_NO_DATA: '所选年份还没有成长记录，先记录一年的成长再生成报告吧',
  REPORT_NOT_READY: '报告还在生成中，请稍候再下载',
  GROWTH_RECORD_DUPLICATE_DATE: '这一天已有成长记录，请更换日期',
  PREDICTION_INSUFFICIENT_DATA: '身高数据不足，先记录一次身高再试',
  DEAL_NOT_FOUND: '该优惠已下架或不存在',
  DEAL_INVALID_DATA: '商品数据不合法，请检查价格与适用年龄区间',
  USER_NOT_FOUND: '用户不存在或已被删除',
};

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return ERROR_MESSAGES[error.code] ?? error.message;
  }
  return '网络异常，请稍后重试';
}
