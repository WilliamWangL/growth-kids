/**
 * @growthkids/validation — 前后端共享的 Zod 校验 schema。
 *
 * 前端表单使用本包 schema 做提交前校验（AI_DEVELOPMENT_SPEC §91）；
 * 后端以 Jakarta Validation 承担相同约束（Java 侧），规则保持同步：
 * - email ≤255；password 8-72；displayName ≤100
 * - 日期 YYYY-MM-DD 且不晚于今天（UTC 口径，与后端一致）
 * - 测量区间：身高 20-250cm、体重 0.3-300kg、头围 20-70cm
 */

import { z } from 'zod';

export const PACKAGE_NAME = '@growthkids/validation' as const;

const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** 今天（UTC）：与后端 LocalDate.now(ZoneOffset.UTC) 口径一致 */
function isoTodayUtc(): string {
  return new Date().toISOString().slice(0, 10);
}

/** 邮箱（登录/注册共用，§24/§25） */
const emailField = z
  .string()
  .trim()
  .min(1, { message: '请输入邮箱' })
  .max(255, { message: '邮箱过长' })
  .email({ message: '邮箱格式不正确' });

/** 日期字段：格式 YYYY-MM-DD + 不晚于今天 */
function dateField(label: string) {
  return z
    .string()
    .trim()
    .regex(ISO_DATE_PATTERN, { message: `${label}格式应为 YYYY-MM-DD` })
    .refine((value) => value <= isoTodayUtc(), { message: `${label}不能晚于今天` });
}

/** 可选测量值：空串视为未填；填写时必须为数字且在区间内 */
function optionalMeasure(label: string, min: number, max: number, unit: string) {
  return z
    .union([z.literal(''), z.coerce.number()])
    .optional()
    .transform((value) => (typeof value === 'number' ? value : undefined))
    .refine((value) => value === undefined || (value >= min && value <= max), {
      message: `${label}需在 ${min}-${max}${unit} 之间`,
    });
}

/** 登录（§25） */
export const loginSchema = z.object({
  email: emailField,
  password: z.string().min(1, { message: '请输入密码' }).max(72, { message: '密码最长 72 位' }),
});

/** 注册（§24） */
export const registerSchema = z.object({
  email: emailField,
  password: z.string().min(8, { message: '密码至少 8 位' }).max(72, { message: '密码最长 72 位' }),
  displayName: z.string().trim().max(100, { message: '昵称最长 100 字' }).optional(),
});

/** 创建孩子（§28） */
export const createChildSchema = z.object({
  name: z.string().trim().min(1, { message: '请输入名字' }).max(100, { message: '名字最长 100 字' }),
  gender: z.enum(['male', 'female', 'unknown'], { message: '请选择性别' }),
  birthDate: dateField('出生日期'),
  birthHeightCm: optionalMeasure('出生身高', 20, 70, 'cm'),
  birthWeightKg: optionalMeasure('出生体重', 0.3, 12, 'kg'),
  fatherHeightCm: optionalMeasure('父亲身高', 100, 250, 'cm'),
  motherHeightCm: optionalMeasure('母亲身高', 100, 250, 'cm'),
});

/** 创建成长记录（§29）：身高/体重/头围至少一项 */
export const createGrowthRecordSchema = z
  .object({
    recordDate: dateField('记录日期'),
    heightCm: optionalMeasure('身高', 20, 250, 'cm'),
    weightKg: optionalMeasure('体重', 0.3, 300, 'kg'),
    headCircumferenceCm: optionalMeasure('头围', 20, 70, 'cm'),
    note: z.string().trim().max(2000, { message: '备注最长 2000 字' }).optional(),
  })
  .refine(
    (value) =>
      value.heightCm !== undefined ||
      value.weightKg !== undefined ||
      value.headCircumferenceCm !== undefined,
    { message: '身高/体重/头围至少填写一项', path: ['heightCm'] },
  );

export type LoginFormValues = z.infer<typeof loginSchema>;
export type RegisterFormValues = z.infer<typeof registerSchema>;
export type CreateChildFormValues = z.infer<typeof createChildSchema>;
export type CreateGrowthRecordFormValues = z.infer<typeof createGrowthRecordSchema>;

/** 创建成长故事（§114）：标题可选 ≤200，内容必填 */
export const createMemorySchema = z.object({
  memoryDate: dateField('故事日期'),
  title: z.string().trim().max(200, { message: '标题最长 200 字' }).optional(),
  content: z
    .string()
    .trim()
    .min(1, { message: '请输入故事内容' })
    .max(5000, { message: '内容最长 5000 字' }),
});

export type CreateMemoryFormValues = z.infer<typeof createMemorySchema>;

/** 捐赠（TASK-017，§65）：金额 $0.50–$10,000，最多 2 位小数（后端另限 USD） */
export const donationCheckoutSchema = z.object({
  amount: z
    .number({ message: '请输入捐赠金额' })
    .min(0.5, { message: '捐赠金额至少 $0.50' })
    .max(10000, { message: '单笔捐赠最多 $10,000' })
    .refine((value) => Math.round(value * 100) === value * 100, {
      message: '金额最多 2 位小数',
    }),
});

export type DonationCheckoutFormValues = z.infer<typeof donationCheckoutSchema>;

/** 创建年度报告（TASK-018，§98）：year 1900–2100，与后端 @Min/@Max 对齐 */
export const createReportSchema = z.object({
  year: z
    .number({ message: '请选择报告年份' })
    .int()
    .min(1900, { message: '年份不得早于 1900' })
    .max(2100, { message: '年份不得晚于 2100' }),
});

export type CreateReportFormValues = z.infer<typeof createReportSchema>;

/**
 * 免费身高预测（TASK-021，§75）：性别/出生日期/当前身高必填，父母身高可选；
 * 与后端 PredictionRequest 的 @DecimalMin/@DecimalMax 区间保持同步。
 */
export const heightPredictionSchema = z.object({
  gender: z.enum(['male', 'female'], { message: '请选择性别' }),
  birthDate: dateField('出生日期'),
  heightCm: z.coerce
    .number({ message: '请输入当前身高' })
    .min(20, { message: '当前身高需在 20-250cm 之间' })
    .max(250, { message: '当前身高需在 20-250cm 之间' }),
  fatherHeightCm: optionalMeasure('父亲身高', 100, 250, 'cm'),
  motherHeightCm: optionalMeasure('母亲身高', 100, 250, 'cm'),
});

export type HeightPredictionFormValues = z.infer<typeof heightPredictionSchema>;
