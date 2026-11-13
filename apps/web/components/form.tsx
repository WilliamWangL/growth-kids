'use client';

import type { ReactNode } from 'react';

/** 表单输入统一样式（TASK-011，设计风格 §92-94）。 */
export const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-100';

/** 表单字段：label + 控件 + 错误文案。 */
export function Field({
  label,
  htmlFor,
  error,
  optional,
  children,
}: {
  label: string;
  htmlFor: string;
  error?: string;
  optional?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block text-sm font-medium text-slate-700">
        {label}
        {optional ? (
          <span className="ml-1 text-xs font-normal text-slate-400">（可选）</span>
        ) : null}
      </label>
      {children}
      {error ? <p className="text-xs text-rose-500">{error}</p> : null}
    </div>
  );
}

/** 提交按钮（pending 时禁用并显示提交中）。 */
export function SubmitButton({ pending, children }: { pending: boolean; children: ReactNode }) {
  return (
    <button
      type="submit"
      disabled={pending}
      className="w-full rounded-xl bg-indigo-500 py-3 text-sm font-semibold text-white transition hover:bg-indigo-600 disabled:cursor-not-allowed disabled:opacity-60"
    >
      {pending ? '提交中…' : children}
    </button>
  );
}

/** 表单级错误提示。 */
export function FormError({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return (
    <div className="rounded-xl border border-rose-100 bg-rose-50 px-3.5 py-2.5 text-sm text-rose-600">
      {message}
    </div>
  );
}

/** zod issues → 字段错误映射（每个字段保留第一条，path 首段为字段名）。 */
export function toFieldErrors(
  issues: Array<{ path: PropertyKey[]; message: string }>,
): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const issue of issues) {
    const key = String(issue.path[0] ?? 'form');
    errors[key] ??= issue.message;
  }
  return errors;
}
