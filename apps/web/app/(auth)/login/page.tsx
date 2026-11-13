'use client';

/**
 * 登录页（§25）：zod 前置校验 + 成功后写入本地会话并跳转首页。
 */

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import type { AuthResponse } from '@growthkids/types';
import { loginSchema } from '@growthkids/validation';
import { Field, FormError, SubmitButton, inputClassName, toFieldErrors } from '@/components/form';
import { apiFetch, errorMessage } from '@/lib/api';
import { saveSession } from '@/lib/session';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = loginSchema.safeParse({ email, password });
    if (!parsed.success) {
      setFieldErrors(toFieldErrors(parsed.error.issues));
      return;
    }
    setFieldErrors({});
    setFormError(null);
    setPending(true);
    try {
      const data = await apiFetch<AuthResponse>('/api/v1/auth/login', {
        method: 'POST',
        auth: false,
        body: parsed.data,
      });
      saveSession({ accessToken: data.accessToken, refreshToken: data.refreshToken });
      router.push('/');
    } catch (error) {
      setFormError(errorMessage(error));
      setPending(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm space-y-6">
        <div className="space-y-1 text-center">
          <h1 className="text-2xl font-bold text-slate-900">登录 GrowthKids</h1>
          <p className="text-sm text-slate-500">记录孩子成长的每一个瞬间</p>
        </div>

        <form
          onSubmit={handleSubmit}
          noValidate
          className="space-y-4 rounded-2xl border border-slate-100 bg-white p-6 shadow-sm"
        >
          <FormError message={formError} />
          <Field label="邮箱" htmlFor="email" error={fieldErrors.email}>
            <input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              className={inputClassName}
            />
          </Field>
          <Field label="密码" htmlFor="password" error={fieldErrors.password}>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="请输入密码"
              className={inputClassName}
            />
          </Field>
          <SubmitButton pending={pending}>登录</SubmitButton>
        </form>

        <p className="text-center text-sm text-slate-500">
          还没有账号？{' '}
          <Link href="/register" className="font-medium text-indigo-500 hover:text-indigo-600">
            注册
          </Link>
        </p>
      </div>
    </div>
  );
}

