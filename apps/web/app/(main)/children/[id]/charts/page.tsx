'use client';

/**
 * 成长曲线页（TASK-012，规范 §33/§58/§59/§110）：
 * 身高/体重/BMI/头围四类曲线，自绘 SVG（无图表依赖）；
 * 横向可滚动、点击点 tooltip、1Y/3Y/All 范围切换，桌面最大宽度 1100px。
 */

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import type { Child, GrowthChart, GrowthChartPoint } from '@growthkids/types';
import { ApiError, apiFetch, errorMessage } from '@/lib/api';
import { loadSession } from '@/lib/session';

const METRICS = [
  { key: 'height', label: '身高', unit: 'cm' },
  { key: 'weight', label: '体重', unit: 'kg' },
  { key: 'bmi', label: 'BMI', unit: '' },
  { key: 'headCircumference', label: '头围', unit: 'cm' },
] as const;

type MetricKey = (typeof METRICS)[number]['key'];
type Range = '1Y' | '3Y' | 'ALL';

const RANGES: Array<{ value: Range; label: string }> = [
  { value: '1Y', label: '1年' },
  { value: '3Y', label: '3年' },
  { value: 'ALL', label: '全部' },
];

type ChartsState =
  | { kind: 'loading' }
  | { kind: 'guest' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'ready'; child: Child; chart: GrowthChart };

/** 范围过滤：以最新点月龄为基准向前截取（与 x 轴月龄一致） */
function filterPoints(records: GrowthChartPoint[], range: Range): GrowthChartPoint[] {
  if (range === 'ALL' || records.length === 0) {
    return records;
  }
  const cutoff = records[records.length - 1].ageMonths - (range === '1Y' ? 12 : 36);
  return records.filter((point) => point.ageMonths >= cutoff);
}

function formatAgeLabel(ageMonths: number): string {
  return ageMonths < 24 ? `${ageMonths}个月` : `${Math.floor(ageMonths / 12)}岁${ageMonths % 12}个月`;
}

async function fetchChartsState(childId: string): Promise<ChartsState> {
  if (!loadSession()) {
    return { kind: 'guest' };
  }
  try {
    const child = await apiFetch<Child>(`/api/v1/children/${childId}`);
    const chart = await apiFetch<GrowthChart>(`/api/v1/children/${childId}/growth-chart`);
    return { kind: 'ready', child, chart };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return { kind: 'not-found' };
    }
    return { kind: 'error', message: errorMessage(error) };
  }
}

/** SVG 曲线（§59）：横向滚动、网格、折线、点高亮与点击 tooltip。 */
function MetricChart({ points, unit }: { points: GrowthChartPoint[]; unit: string }) {
  const [selected, setSelected] = useState<number | null>(null);

  if (points.length === 0) {
    return (
      <div className="flex h-48 items-center justify-center rounded-xl border border-dashed border-slate-200 text-sm text-slate-400">
        暂无数据，记录后即可查看曲线
      </div>
    );
  }

  const HEIGHT = 240;
  const PAD_L = 48;
  const PAD_R = 24;
  const PAD_T = 24;
  const PAD_B = 32;
  const PX_PER_MONTH = 28;

  const minM = points[0].ageMonths;
  const maxM = points[points.length - 1].ageMonths;
  const width = Math.max(600, PAD_L + (maxM - minM) * PX_PER_MONTH + PAD_R);

  const values = points.map((point) => point.value);
  let minV = Math.min(...values);
  let maxV = Math.max(...values);
  if (minV === maxV) {
    minV -= 1;
    maxV += 1;
  }
  const valuePad = (maxV - minV) * 0.15;
  minV -= valuePad;
  maxV += valuePad;

  const x = (m: number) => PAD_L + ((m - minM) / Math.max(maxM - minM, 1)) * (width - PAD_L - PAD_R);
  const y = (v: number) => PAD_T + ((maxV - v) / (maxV - minV)) * (HEIGHT - PAD_T - PAD_B);

  const monthStep = maxM - minM > 42 ? 12 : 6;
  const xTicks: number[] = [];
  for (let m = Math.ceil(minM / monthStep) * monthStep; m <= maxM; m += monthStep) {
    xTicks.push(m);
  }
  const yTicks = [0, 1, 2, 3].map((i) => minV + ((maxV - minV) * i) / 3);

  const linePoints = points.map((p) => `${x(p.ageMonths)},${y(p.value)}`).join(' ');
  const selectedPoint = selected != null ? points[selected] : null;
  const tooltipX = selectedPoint ? Math.min(Math.max(x(selectedPoint.ageMonths), 90), width - 90) : 0;
  const tooltipY = selectedPoint ? y(selectedPoint.value) : 0;

  return (
    <div className="overflow-x-auto rounded-xl border border-slate-100 bg-white p-2 shadow-sm">
      <svg width={width} height={HEIGHT} role="img" aria-label="成长曲线图">
        {/* 水平网格 + y 轴刻度 */}
        {yTicks.map((v, i) => (
          <g key={i}>
            <line
              x1={PAD_L}
              x2={width - PAD_R}
              y1={y(v)}
              y2={y(v)}
              stroke="#e2e8f0"
              strokeWidth={1}
            />
            <text x={PAD_L - 8} y={y(v) + 4} textAnchor="end" fontSize={11} fill="#94a3b8">
              {Number(v.toFixed(1))}
            </text>
          </g>
        ))}
        {/* x 轴月龄刻度 */}
        {xTicks.map((m) => (
          <text key={m} x={x(m)} y={HEIGHT - 10} textAnchor="middle" fontSize={11} fill="#94a3b8">
            {m}月
          </text>
        ))}
        {/* 折线：单点时不绘制 */}
        {points.length > 1 ? (
          <polyline points={linePoints} fill="none" stroke="#6366f1" strokeWidth={2} />
        ) : null}
        {/* 数据点；最后一个点高亮 */}
        {points.map((point, index) => {
          const isLast = index === points.length - 1;
          const isSelected = selected === index;
          return (
            <circle
              key={`${point.date}-${index}`}
              cx={x(point.ageMonths)}
              cy={y(point.value)}
              r={isSelected ? 6 : isLast ? 5 : 3.5}
              fill={isSelected ? '#4338ca' : isLast ? '#4338ca' : '#6366f1'}
              stroke="#ffffff"
              strokeWidth={isSelected || isLast ? 2 : 0}
              className="cursor-pointer"
              onClick={() => setSelected(isSelected ? null : index)}
            />
          );
        })}
        {/* 点击点的 tooltip */}
        {selectedPoint ? (
          <g>
            <rect
              x={tooltipX - 88}
              y={tooltipY - 46}
              width={176}
              height={40}
              rx={8}
              fill="#0f172a"
              opacity={0.92}
            />
            <text x={tooltipX} y={tooltipY - 30} textAnchor="middle" fontSize={11} fill="#cbd5e1">
              {selectedPoint.date} · {formatAgeLabel(selectedPoint.ageMonths)}
            </text>
            <text x={tooltipX} y={tooltipY - 14} textAnchor="middle" fontSize={12} fill="#ffffff">
              {selectedPoint.value}
              {unit ? ` ${unit}` : ''}
              {selectedPoint.percentile != null
                ? ` · P${Math.round(selectedPoint.percentile)}`
                : ''}
            </text>
          </g>
        ) : null}
      </svg>
    </div>
  );
}

function ChartsView({ child, chart }: { child: Child; chart: GrowthChart }) {
  const [metric, setMetric] = useState<MetricKey>('height');
  const [range, setRange] = useState<Range>('ALL');

  const activeMetric = METRICS.find((item) => item.key === metric) ?? METRICS[0];
  const points = useMemo(() => filterPoints(chart[metric].records, range), [chart, metric, range]);

  return (
    <div className="mx-auto w-full max-w-[1100px] space-y-4 px-4 py-6">
      <div className="space-y-1">
        <Link href="/" className="text-sm text-slate-400 hover:text-slate-600">
          ← 返回首页
        </Link>
        <h1 className="text-xl font-bold text-slate-900">{child.name}的成长曲线</h1>
      </div>

      <div className="grid grid-cols-4 gap-2">
        {METRICS.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setMetric(item.key)}
            className={
              metric === item.key
                ? 'rounded-xl border border-indigo-400 bg-indigo-50 py-2 text-sm font-medium text-indigo-600'
                : 'rounded-xl border border-slate-200 bg-white py-2 text-sm text-slate-600 hover:border-slate-300'
            }
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="flex items-center justify-between">
        <p className="text-sm text-slate-500">
          {activeMetric.label}
          {activeMetric.unit ? `（${activeMetric.unit}）` : ''}
        </p>
        <div className="flex gap-1 rounded-xl bg-slate-100 p-1">
          {RANGES.map((item) => (
            <button
              key={item.value}
              type="button"
              onClick={() => setRange(item.value)}
              className={
                range === item.value
                  ? 'rounded-lg bg-white px-3 py-1 text-xs font-medium text-slate-900 shadow-sm'
                  : 'rounded-lg px-3 py-1 text-xs text-slate-500'
              }
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      <MetricChart key={`${metric}-${range}`} points={points} unit={activeMetric.unit} />

      <p className="text-xs leading-relaxed text-slate-400">
        点击数据点查看详情；百分位（P）表示在同年龄同性别儿童中的相对生长位置，不作为健康结论依据。
      </p>
    </div>
  );
}

export default function GrowthChartsPage() {
  const { id } = useParams<{ id: string }>();
  const [state, setState] = useState<ChartsState>({ kind: 'loading' });

  useEffect(() => {
    void fetchChartsState(id).then((next) => setState(next));
  }, [id]);

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
        <p className="text-sm text-slate-500">请先登录后查看成长曲线</p>
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
            void fetchChartsState(id).then((next) => setState(next));
          }}
          className="rounded-xl bg-indigo-500 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-600"
        >
          重试
        </button>
      </div>
    );
  }
  return <ChartsView child={state.child} chart={state.chart} />;
}

