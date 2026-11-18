'use client';

/**
 * 优惠商品列表（TASK-022）：公开浏览，无需登录。
 * 分类选项来自 /api/v1/deals/categories（后端 DealCategory 单一事实源），
 * 月龄筛选传代表值，由后端做「区间覆盖」判断。
 */

import { useEffect, useState } from 'react';
import Link from 'next/link';
import type { Deal, DealCategoryOption, Paginated } from '@growthkids/types';
import { DealCard } from '@/components/deal-card';
import { apiFetch, errorMessage } from '@/lib/api';

const PAGE_SIZE = 12;

/** 代表月龄：后端按「min ≤ ageMonths ≤ max」匹配，空值表示不限 */
const AGE_OPTIONS = [
  { value: '3', label: '0-6 个月' },
  { value: '9', label: '6-12 个月' },
  { value: '24', label: '1-3 岁' },
  { value: '54', label: '3-6 岁' },
  { value: '84', label: '6 岁以上' },
];

async function fetchDeals(query: string): Promise<{ data?: Paginated<Deal>; message?: string }> {
  try {
    return { data: await apiFetch<Paginated<Deal>>(`/api/v1/deals?${query}`, { auth: false }) };
  } catch (error) {
    return { message: errorMessage(error) };
  }
}

function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (next: number) => void;
}) {
  return (
    <div className="flex items-center justify-between pt-1 text-xs text-slate-500">
      <span>
        第 {page} / {Math.max(totalPages, 1)} 页
      </span>
      <span className="flex gap-2">
        <button
          type="button"
          disabled={page <= 1}
          onClick={() => onChange(page - 1)}
          className="rounded-lg border border-slate-200 px-3 py-1.5 transition hover:border-indigo-300 disabled:opacity-40"
        >
          上一页
        </button>
        <button
          type="button"
          disabled={page >= totalPages}
          onClick={() => onChange(page + 1)}
          className="rounded-lg border border-slate-200 px-3 py-1.5 transition hover:border-indigo-300 disabled:opacity-40"
        >
          下一页
        </button>
      </span>
    </div>
  );
}

export function DealsBrowser() {
  const [categories, setCategories] = useState<DealCategoryOption[]>([]);
  const [category, setCategory] = useState('');
  const [ageMonths, setAgeMonths] = useState('');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paginated<Deal> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void apiFetch<DealCategoryOption[]>('/api/v1/deals/categories', { auth: false })
      .then(setCategories)
      // 分类接口失败不阻塞列表（筛选条退化为「全部」）
      .catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    const params = new URLSearchParams({ page: String(page), pageSize: String(PAGE_SIZE) });
    if (category) {
      params.set('category', category);
    }
    if (ageMonths) {
      params.set('ageMonths', ageMonths);
    }
    void fetchDeals(params.toString()).then((result) => {
      if (result.data) {
        setData(result.data);
        setError(null);
      } else {
        setError(result.message ?? '加载失败');
      }
    });
  }, [category, ageMonths, page]);

  function selectCategory(next: string) {
    setCategory(next);
    setPage(1);
  }

  function selectAge(next: string) {
    setAgeMonths(next);
    setPage(1);
  }

  return (
    <div className="mx-auto w-full max-w-3xl space-y-4 px-4 pb-10">
      <header className="pt-6">
        <Link href="/" className="text-xs text-slate-400 hover:text-slate-600">
          ← 返回首页
        </Link>
        <h1 className="mt-1 text-xl font-bold text-slate-900">育儿好物优惠</h1>
        <p className="mt-1 text-sm text-slate-500">
          精选儿童用品限时价，按孩子年龄挑更省心；购买与售后由商家承担。
        </p>
      </header>

      <nav className="flex gap-1.5 overflow-x-auto pb-1">
        <button
          type="button"
          onClick={() => selectCategory('')}
          className={`shrink-0 rounded-full px-3 py-1.5 text-xs font-medium transition ${
            category === ''
              ? 'bg-indigo-500 text-white'
              : 'border border-slate-200 bg-white text-slate-500 hover:border-indigo-300 hover:text-indigo-600'
          }`}
        >
          全部
        </button>
        {categories.map((item) => (
          <button
            key={item.code}
            type="button"
            onClick={() => selectCategory(item.code)}
            className={`shrink-0 rounded-full px-3 py-1.5 text-xs font-medium transition ${
              category === item.code
                ? 'bg-indigo-500 text-white'
                : 'border border-slate-200 bg-white text-slate-500 hover:border-indigo-300 hover:text-indigo-600'
            }`}
          >
            {item.label}
          </button>
        ))}
      </nav>

      <div className="flex items-center justify-between gap-2 text-xs text-slate-500">
        <label className="flex items-center gap-2">
          <span>适用年龄</span>
          <select
            value={ageMonths}
            onChange={(event) => selectAge(event.target.value)}
            className="rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-600"
          >
            <option value="">不限</option>
            {AGE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        {data && <span>共 {data.pagination.total} 件</span>}
      </div>

      {error && <p className="rounded-xl bg-rose-50 px-3 py-2 text-xs text-rose-600">{error}</p>}

      {!data && !error && (
        <p className="py-8 text-center text-xs text-slate-400">加载中…</p>
      )}

      {data && data.items.length === 0 && (
        <p className="rounded-2xl border border-slate-100 bg-white px-4 py-8 text-center text-xs text-slate-400">
          暂无符合条件的优惠，换个分类或年龄试试
        </p>
      )}

      {data && data.items.length > 0 && (
        <ul className="grid gap-3 sm:grid-cols-2">
          {data.items.map((deal) => (
            <li key={deal.id}>
              <DealCard deal={deal} />
            </li>
          ))}
        </ul>
      )}

      {data && data.pagination.totalPages > 1 && (
        <Pager page={page} totalPages={data.pagination.totalPages} onChange={setPage} />
      )}
    </div>
  );
}
