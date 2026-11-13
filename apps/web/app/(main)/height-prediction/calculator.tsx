'use client';

/**
 * 免费身高预测计算器（TASK-021，规范 §44/§45/§75/§76）：
 * 无需注册即可获得预计成年身高/区间/百分位/逐年预测；
 * “保存到成长档案”需登录（§75），未登录引导注册，形成 SEO 漏斗。
 */

import Link from 'next/link';
import { useState, useSyncExternalStore, type FormEvent } from 'react';
import type { HeightPrediction } from '@growthkids/types';
import { heightPredictionSchema } from '@growthkids/validation';
import { Field, FormError, SubmitButton, inputClassName, toFieldErrors } from '@/components/form';
import { PredictionResult } from '@/components/prediction';
import { apiFetch, errorMessage } from '@/lib/api';
import { loadSession } from '@/lib/session';

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * 会话存在性（localStorage 非 React 状态源）：useSyncExternalStore 提供
 * 服务端快照 false / 客户端快照真实值，避免用水合后的 setState 造成撕裂。
 */
function useHasSession(): boolean {
  return useSyncExternalStore(
    () => () => {},
    () => loadSession() !== null,
    () => false,
  );
}

export function HeightPredictionCalculator() {
  const [gender, setGender] = useState<'male' | 'female'>('male');
  const [birthDate, setBirthDate] = useState('');
  const [heightCm, setHeightCm] = useState('');
  const [fatherHeightCm, setFatherHeightCm] = useState('');
  const [motherHeightCm, setMotherHeightCm] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [prediction, setPrediction] = useState<HeightPrediction | null>(null);
  const loggedIn = useHasSession();

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = heightPredictionSchema.safeParse({
      gender,
      birthDate,
      heightCm,
      fatherHeightCm,
      motherHeightCm,
    });
    if (!parsed.success) {
      setFieldErrors(toFieldErrors(parsed.error.issues));
      return;
    }
    setFieldErrors({});
    setFormError(null);
    setPending(true);
    try {
      // 公开端点（§75）：不带 Authorization，未登录也可得到结果
      const result = await apiFetch<HeightPrediction>('/api/v1/prediction/estimate', {
        method: 'POST',
        body: parsed.data,
        auth: false,
      });
      setPrediction(result);
    } catch (error) {
      setFormError(errorMessage(error));
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="mx-auto w-full max-w-md space-y-4 px-4 py-8">
      <div className="flex items-center justify-between">
        <Link href="/" className="text-sm text-slate-400 hover:text-slate-600">
          ← 返回首页
        </Link>
        {loggedIn ? (
          <Link href="/" className="text-sm font-medium text-indigo-500 hover:text-indigo-600">
            我的成长档案
          </Link>
        ) : (
          <Link href="/login" className="text-sm font-medium text-indigo-500 hover:text-indigo-600">
            登录
          </Link>
        )}
      </div>

      <div className="space-y-1">
        <h1 className="text-2xl font-bold leading-snug text-slate-900">免费预测孩子成年身高</h1>
        <p className="text-sm leading-relaxed text-slate-500">
          输入性别、出生日期、当前身高与父母身高，基于公开生长标准与遗传模型估算。
        </p>
      </div>

      <form
        onSubmit={handleSubmit}
        noValidate
        className="space-y-4 rounded-2xl border border-slate-100 bg-white p-6 shadow-sm"
      >
        <FormError message={formError} />

        <Field label="性别" htmlFor="gender" error={fieldErrors.gender}>
          <div className="grid grid-cols-2 gap-2">
            {(['male', 'female'] as const).map((value) => (
              <button
                key={value}
                type="button"
                onClick={() => setGender(value)}
                className={
                  gender === value
                    ? 'rounded-xl border border-indigo-400 bg-indigo-50 py-2.5 text-sm font-medium text-indigo-600'
                    : 'rounded-xl border border-slate-200 bg-white py-2.5 text-sm text-slate-600 hover:border-slate-300'
                }
              >
                {value === 'male' ? '男孩' : '女孩'}
              </button>
            ))}
          </div>
        </Field>

        <Field label="出生日期" htmlFor="birthDate" error={fieldErrors.birthDate}>
          <input
            id="birthDate"
            type="date"
            max={todayIso()}
            value={birthDate}
            onChange={(event) => setBirthDate(event.target.value)}
            className={inputClassName}
          />
        </Field>

        <Field label="当前身高" htmlFor="heightCm" error={fieldErrors.heightCm}>
          <input
            id="heightCm"
            type="number"
            inputMode="decimal"
            step="0.1"
            min="20"
            max="250"
            value={heightCm}
            onChange={(event) => setHeightCm(event.target.value)}
            placeholder="如 92.5"
            className={inputClassName}
          />
        </Field>

        <Field label="父亲身高" htmlFor="fatherHeightCm" error={fieldErrors.fatherHeightCm} optional>
          <input
            id="fatherHeightCm"
            type="number"
            inputMode="decimal"
            step="0.1"
            min="100"
            max="250"
            value={fatherHeightCm}
            onChange={(event) => setFatherHeightCm(event.target.value)}
            placeholder="如 178"
            className={inputClassName}
          />
        </Field>

        <Field label="母亲身高" htmlFor="motherHeightCm" error={fieldErrors.motherHeightCm} optional>
          <input
            id="motherHeightCm"
            type="number"
            inputMode="decimal"
            step="0.1"
            min="100"
            max="250"
            value={motherHeightCm}
            onChange={(event) => setMotherHeightCm(event.target.value)}
            placeholder="如 165"
            className={inputClassName}
          />
        </Field>

        <p className="text-xs leading-relaxed text-slate-400">
          填写父母身高可引入遗传因素（中靶身高），预测结果会更贴近实际情况。
        </p>

        <SubmitButton pending={pending}>开始预测</SubmitButton>
      </form>

      {prediction ? (
        <section className="space-y-3">
          <h2 className="text-base font-semibold text-slate-900">预测结果</h2>
          <PredictionResult prediction={prediction} />
          <div className="rounded-2xl border border-indigo-100 bg-indigo-50/60 p-4">
            <p className="text-sm font-medium text-indigo-700">想把预测保存成成长档案？</p>
            <p className="mt-1 text-xs leading-relaxed text-indigo-500">
              注册后可持续记录身高体重、查看 WHO 生长曲线、生成年度报告，并在有新记录时重新预测。
            </p>
            <div className="mt-3 grid gap-2">
              <Link
                href={loggedIn ? '/' : '/register'}
                className="block rounded-xl bg-indigo-500 py-2.5 text-center text-sm font-semibold text-white transition hover:bg-indigo-600"
              >
                {loggedIn ? '去记录成长' : '免费注册并保存'}
              </Link>
              {!loggedIn ? (
                <Link
                  href="/login"
                  className="block rounded-xl border border-indigo-200 py-2.5 text-center text-sm font-semibold text-indigo-600 transition hover:bg-indigo-50"
                >
                  已有账号，去登录
                </Link>
              ) : null}
            </div>
          </div>
        </section>
      ) : null}
    </div>
  );
}
