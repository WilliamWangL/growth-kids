'use client';

/**
 * 创建孩子页（§28/§54）：zod 前置校验；父母身高用于后续身高预测（§10）。
 * 创建成功后直接引导到第一条成长记录，完成首次闭环。
 */

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import type { Child, Gender } from '@growthkids/types';
import { createChildSchema } from '@growthkids/validation';
import { Field, FormError, SubmitButton, inputClassName, toFieldErrors } from '@/components/form';
import { apiFetch, errorMessage } from '@/lib/api';

const GENDER_OPTIONS: Array<{ value: Gender; label: string }> = [
  { value: 'male', label: '男孩' },
  { value: 'female', label: '女孩' },
  { value: 'unknown', label: '暂不填写' },
];

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function NewChildPage() {
  const router = useRouter();
  const [name, setName] = useState('');
  const [gender, setGender] = useState<Gender>('male');
  const [birthDate, setBirthDate] = useState('');
  const [birthHeightCm, setBirthHeightCm] = useState('');
  const [birthWeightKg, setBirthWeightKg] = useState('');
  const [fatherHeightCm, setFatherHeightCm] = useState('');
  const [motherHeightCm, setMotherHeightCm] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = createChildSchema.safeParse({
      name,
      gender,
      birthDate,
      birthHeightCm,
      birthWeightKg,
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
      const child = await apiFetch<Child>('/api/v1/children', {
        method: 'POST',
        body: parsed.data,
      });
      router.push(`/children/${child.id}/growth/new`);
    } catch (error) {
      setFormError(errorMessage(error));
      setPending(false);
    }
  }

  return (
    <div className="mx-auto w-full max-w-md space-y-4 px-4 py-8">
      <div className="space-y-1">
        <Link href="/" className="text-sm text-slate-400 hover:text-slate-600">
          ← 返回首页
        </Link>
        <h1 className="text-xl font-bold text-slate-900">添加孩子</h1>
        <p className="text-sm text-slate-500">填写基础信息，开启成长记录</p>
      </div>

      <form
        onSubmit={handleSubmit}
        noValidate
        className="space-y-4 rounded-2xl border border-slate-100 bg-white p-6 shadow-sm"
      >
        <FormError message={formError} />
        <Field label="名字" htmlFor="name" error={fieldErrors.name}>
          <input
            id="name"
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="如：朵朵"
            className={inputClassName}
          />
        </Field>

        <Field label="性别" htmlFor="gender-male" error={fieldErrors.gender}>
          <div className="grid grid-cols-3 gap-2">
            {GENDER_OPTIONS.map((option) => (
              <button
                key={option.value}
                id={`gender-${option.value}`}
                type="button"
                onClick={() => setGender(option.value)}
                className={
                  gender === option.value
                    ? 'rounded-xl border border-indigo-400 bg-indigo-50 py-2.5 text-sm font-medium text-indigo-600'
                    : 'rounded-xl border border-slate-200 bg-white py-2.5 text-sm text-slate-600 hover:border-slate-300'
                }
              >
                {option.label}
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

        <div className="grid grid-cols-2 gap-3">
          <Field label="出生身高" htmlFor="birthHeightCm" error={fieldErrors.birthHeightCm} optional>
            <input
              id="birthHeightCm"
              type="number"
              inputMode="decimal"
              step="0.1"
              min="20"
              max="70"
              value={birthHeightCm}
              onChange={(event) => setBirthHeightCm(event.target.value)}
              placeholder="50"
              className={inputClassName}
            />
          </Field>
          <Field label="出生体重" htmlFor="birthWeightKg" error={fieldErrors.birthWeightKg} optional>
            <input
              id="birthWeightKg"
              type="number"
              inputMode="decimal"
              step="0.1"
              min="0.3"
              max="12"
              value={birthWeightKg}
              onChange={(event) => setBirthWeightKg(event.target.value)}
              placeholder="3.3"
              className={inputClassName}
            />
          </Field>
        </div>

        <div className="grid grid-cols-2 gap-3">
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
              placeholder="175"
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
              placeholder="160"
              className={inputClassName}
            />
          </Field>
        </div>

        <p className="text-xs leading-relaxed text-slate-400">
          填写父母身高可获得更准确的身高预测；出生身高体重有助于对比 WHO 生长标准。
        </p>

        <SubmitButton pending={pending}>保存并记录第一次成长</SubmitButton>
      </form>
    </div>
  );
}

