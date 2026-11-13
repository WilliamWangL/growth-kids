'use client';

/**
 * 添加成长记录页（§29/§54）：身高/体重/头围至少一项，备注可选；
 * 保存成功后回到首页，dashboard 展示最新记录与 WHO 百分位。
 */

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import type { GrowthRecord } from '@growthkids/types';
import { createGrowthRecordSchema } from '@growthkids/validation';
import { Field, FormError, SubmitButton, inputClassName, toFieldErrors } from '@/components/form';
import { apiFetch, errorMessage } from '@/lib/api';

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function NewGrowthRecordPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const [recordDate, setRecordDate] = useState(todayIso());
  const [heightCm, setHeightCm] = useState('');
  const [weightKg, setWeightKg] = useState('');
  const [headCircumferenceCm, setHeadCircumferenceCm] = useState('');
  const [note, setNote] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = createGrowthRecordSchema.safeParse({
      recordDate,
      heightCm,
      weightKg,
      headCircumferenceCm,
      note: note.trim() === '' ? undefined : note,
    });
    if (!parsed.success) {
      setFieldErrors(toFieldErrors(parsed.error.issues));
      return;
    }
    setFieldErrors({});
    setFormError(null);
    setPending(true);
    try {
      await apiFetch<GrowthRecord>(`/api/v1/children/${id}/growth-records`, {
        method: 'POST',
        body: parsed.data,
      });
      router.push('/');
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
        <h1 className="text-xl font-bold text-slate-900">记录成长</h1>
        <p className="text-sm text-slate-500">身高、体重、头围至少填写一项</p>
      </div>

      <form
        onSubmit={handleSubmit}
        noValidate
        className="space-y-4 rounded-2xl border border-slate-100 bg-white p-6 shadow-sm"
      >
        <FormError message={formError} />
        <Field label="记录日期" htmlFor="recordDate" error={fieldErrors.recordDate}>
          <input
            id="recordDate"
            type="date"
            max={todayIso()}
            value={recordDate}
            onChange={(event) => setRecordDate(event.target.value)}
            className={inputClassName}
          />
        </Field>

        <Field label="身高" htmlFor="heightCm" error={fieldErrors.heightCm} optional>
          <input
            id="heightCm"
            type="number"
            inputMode="decimal"
            step="0.1"
            min="20"
            max="250"
            value={heightCm}
            onChange={(event) => setHeightCm(event.target.value)}
            placeholder="如 82.5"
            className={inputClassName}
          />
        </Field>

        <Field label="体重" htmlFor="weightKg" error={fieldErrors.weightKg} optional>
          <input
            id="weightKg"
            type="number"
            inputMode="decimal"
            step="0.1"
            min="0.3"
            max="300"
            value={weightKg}
            onChange={(event) => setWeightKg(event.target.value)}
            placeholder="如 11.7"
            className={inputClassName}
          />
        </Field>

        <Field label="头围" htmlFor="headCircumferenceCm" error={fieldErrors.headCircumferenceCm} optional>
          <input
            id="headCircumferenceCm"
            type="number"
            inputMode="decimal"
            step="0.1"
            min="20"
            max="70"
            value={headCircumferenceCm}
            onChange={(event) => setHeadCircumferenceCm(event.target.value)}
            placeholder="如 47.5"
            className={inputClassName}
          />
        </Field>

        <Field label="备注" htmlFor="note" error={fieldErrors.note} optional>
          <textarea
            id="note"
            rows={3}
            value={note}
            onChange={(event) => setNote(event.target.value)}
            placeholder="今天发生了什么有趣的事？"
            className={inputClassName}
          />
        </Field>

        <SubmitButton pending={pending}>保存记录</SubmitButton>
      </form>
    </div>
  );
}

