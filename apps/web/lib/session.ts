/**
 * 会话存储（localStorage）：access/refresh token 仅存于浏览器端（TASK-011）。
 *
 * 仅供客户端模块使用（'use client' 组件内或事件回调中）；
 * SSR 环境下 loadSession 返回 null。
 */

const ACCESS_TOKEN_KEY = 'growthkids.accessToken';
const REFRESH_TOKEN_KEY = 'growthkids.refreshToken';

export interface StoredSession {
  accessToken: string;
  refreshToken: string;
}

export function loadSession(): StoredSession | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const accessToken = window.localStorage.getItem(ACCESS_TOKEN_KEY);
  const refreshToken = window.localStorage.getItem(REFRESH_TOKEN_KEY);
  return accessToken && refreshToken ? { accessToken, refreshToken } : null;
}

export function saveSession(session: StoredSession): void {
  window.localStorage.setItem(ACCESS_TOKEN_KEY, session.accessToken);
  window.localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken);
}

export function clearSession(): void {
  window.localStorage.removeItem(ACCESS_TOKEN_KEY);
  window.localStorage.removeItem(REFRESH_TOKEN_KEY);
}
