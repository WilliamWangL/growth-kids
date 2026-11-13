'use client';

/**
 * 身高预测结果展示（TASK-021，规范 §44/§45）：
 * 预计成年身高 + 区间 + 当前百分位 + 6-18 岁逐年折线（自绘 SVG，无图表依赖）。
 * 免责声明为 §45 强制文案，不得替换为“保证/准确”等确定性表述。
 */

import type { HeightPrediction } from '@growthkids/types';

const CONFIDENCE_LABELS: Record<HeightPrediction['confidence'], string> = {
  low: '数据较少',
  medium: '中等把握',
  high: '数据较充分',
};

const CONFIDENCE_STYLES: Record<HeightPrediction['confidence'], string> = {
  low: 'bg-amber-50 text-amber-600',
  medium: 'bg-indigo-50 text-indigo-600',
  high: 'bg-emerald-50 text-emerald-600',
};

function formatCm(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

/** 逐年预测折线：区间带 + 中位线（§44）。points 可能为空（孩子已接近成年）。 */
function YearlyChart({ points }: { points: HeightPrediction['yearlyPredictions'] }) {
  if (points.length === 0) {
    return (
      <div className="flex h-40 items-center justify-center rounded-xl border border-dashed border-slate-200 text-sm text-slate-400">
        已接近成年身高，暂无逐年预测点
      </div>
    );
  }

  const WIDTH = 680;
  const HEIGHT = 220;
  const PAD_L = 44;
  const PAD_R = 16;
  const PAD_T = 16;
  const PAD_B = 28;

  const minAge = points[0].age;
  const maxAge = points[points.length - 1].age;
  const values = points.flatMap((point) => [point.lowerCm, point.upperCm]);
  let minV = Math.min(...values);
  let maxV = Math.max(...values);
  if (minV === maxV) {
    minV -= 1;
    maxV += 1;
  }
  const pad = (maxV - minV) * 0.15;
  minV -= pad;
  maxV += pad;

  const x = (age: number) =>
    PAD_L + ((age - minAge) / Math.max(maxAge - minAge, 1)) * (WIDTH - PAD_L - PAD_R);
  const y = (value: number) => PAD_T + ((maxV - value) / (maxV - minV)) * (HEIGHT - PAD_T - PAD_B);

  const band = [
    ...points.map((point) => `${x(point.age)},${y(point.upperCm)}`),
    ...[...points].reverse().map((point) => `${x(point.age)},${y(point.lowerCm)}`),
  ].join(' ');
  const line = points.map((point) => `${x(point.age)},${y(point.predictedHeightCm)}`).join(' ');
  const yTicks = [0, 1, 2, 3].map((i) => minV + ((maxV - minV) * i) / 3);
  const adult = points[points.length - 1];

  return (
    <div className="overflow-x-auto rounded-xl border border-slate-100 bg-white p-2 shadow-sm">
      <svg width={WIDTH} height={HEIGHT} role="img" aria-label="逐年身高预测图">
        {yTicks.map((value, index) => (
          <g key={index}>
            <line
              x1={PAD_L}
              x2={WIDTH - PAD_R}
              y1={y(value)}
              y2={y(value)}
              stroke="#e2e8f0"
              strokeWidth={1}
            />
            <text x={PAD_L - 8} y={y(value) + 4} textAnchor="end" fontSize={11} fill="#94a3b8">
              {Math.round(value)}
            </text>
          </g>
        ))}
        {points.map((point) => (
          <text
            key={point.age}
            x={x(point.age)}
            y={HEIGHT - 10}
            textAnchor="middle"
            fontSize={11}
            fill="#94a3b8"
          >
            {point.age}岁
          </text>
        ))}
        {/* 预测区间带 */}
        <polygon points={band} fill="#eef2ff" />
        {/* 中位预测线 */}
        {points.length > 1 ? (
          <polyline points={line} fill="none" stroke="#6366f1" strokeWidth={2} />
        ) : null}
        {points.map((point) => (
          <circle
            key={point.age}
            cx={x(point.age)}
            cy={y(point.predictedHeightCm)}
            r={point.age === adult.age ? 5 : 3}
            fill={point.age === adult.age ? '#4338ca' : '#6366f1'}
            stroke="#ffffff"
            strokeWidth={point.age === adult.age ? 2 : 0}
          />
        ))}
      </svg>
    </div>
  );
}

/** 预测结果卡：主数字 + 区间 + 百分位/速度/中靶 + 逐年折线 + 免责声明（§44/§45）。 */
export function PredictionResult({ prediction }: { prediction: HeightPrediction }) {
  return (
    <div className="space-y-4">
      <section className="rounded-2xl border border-indigo-100 bg-white p-6 text-center shadow-sm">
        <p className="text-xs text-slate-500">预计成年身高</p>
        <p className="mt-1 text-4xl font-bold text-indigo-600">
          {formatCm(prediction.predictedAdultHeightCm)}
          <span className="ml-1 text-base font-medium text-slate-400">cm</span>
        </p>
        <p className="mt-2 text-sm text-slate-500">
          预测区间 {formatCm(prediction.lowerRangeCm)} – {formatCm(prediction.upperRangeCm)} cm
        </p>
        <span
          className={`mt-3 inline-block rounded-full px-3 py-1 text-xs font-medium ${
            CONFIDENCE_STYLES[prediction.confidence]
          }`}
        >
          数据完整度：{CONFIDENCE_LABELS[prediction.confidence]}
        </span>
      </section>

      <section className="grid grid-cols-3 gap-3">
        <div className="rounded-2xl border border-slate-100 bg-white p-4 text-center shadow-sm">
          <p className="text-xs text-slate-500">当前百分位</p>
          <p className="mt-1 text-lg font-semibold text-slate-900">
            {prediction.currentPercentile != null
              ? `P${Math.round(prediction.currentPercentile)}`
              : '—'}
          </p>
        </div>
        <div className="rounded-2xl border border-slate-100 bg-white p-4 text-center shadow-sm">
          <p className="text-xs text-slate-500">生长速度</p>
          <p className="mt-1 text-lg font-semibold text-slate-900">
            {prediction.growthVelocityCmPerYear != null
              ? `${formatCm(prediction.growthVelocityCmPerYear)}`
              : '—'}
            <span className="ml-0.5 text-xs font-normal text-slate-400">
              {prediction.growthVelocityCmPerYear != null ? 'cm/年' : ''}
            </span>
          </p>
        </div>
        <div className="rounded-2xl border border-slate-100 bg-white p-4 text-center shadow-sm">
          <p className="text-xs text-slate-500">中靶身高</p>
          <p className="mt-1 text-lg font-semibold text-slate-900">
            {prediction.midParentalHeightCm != null
              ? `${formatCm(prediction.midParentalHeightCm)}`
              : '—'}
            <span className="ml-0.5 text-xs font-normal text-slate-400">
              {prediction.midParentalHeightCm != null ? 'cm' : ''}
            </span>
          </p>
        </div>
      </section>

      <section className="space-y-2">
        <p className="text-sm font-medium text-slate-700">逐年身高预测</p>
        <YearlyChart points={prediction.yearlyPredictions} />
        <p className="text-xs text-slate-400">阴影为预测区间，折线为估算中位值（6-18 岁）。</p>
      </section>

      <p className="rounded-xl bg-slate-100 px-4 py-3 text-xs leading-relaxed text-slate-500">
        身高预测基于用户输入数据和公开生长标准进行估算，仅用于成长趋势参考，不代表医学诊断或确定的成年身高。实际身高会受到遗传、营养、睡眠、运动、青春期发育等多种因素影响。
      </p>
    </div>
  );
}
