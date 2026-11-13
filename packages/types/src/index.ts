/**
 * @growthkids/types — 前后端共享的 API 与领域类型定义。
 *
 * 约定：
 * - 所有日期字符串使用 `YYYY-MM-DD`（DATE 语义，不转 UTC）。
 * - 所有时间戳字符串使用 ISO 8601 UTC。
 * - 该包只包含类型与常量，不包含运行时逻辑。
 */

export const PACKAGE_NAME = '@growthkids/types' as const;

/** 性别（对应数据库 children.gender） */
export type Gender = 'male' | 'female' | 'unknown';

/** 用户状态 */
export type UserStatus = 'active' | 'deleted' | 'suspended';

/** 孩子状态 */
export type ChildStatus = 'active' | 'deleted';

/** WHO 指标类型 */
export type WhoIndicator =
  | 'height_for_age'
  | 'weight_for_age'
  | 'bmi_for_age'
  | 'head_circumference_for_age'
  | 'weight_for_height';

/** 图片处理状态 */
export type PhotoProcessingStatus = 'pending' | 'processing' | 'ready' | 'failed';

/** 会员套餐 */
export type MembershipPlanCode = 'free' | 'pro';

/** 会员状态（与后端 memberships.status 一致） */
export type MembershipStatus = 'active' | 'expired' | 'cancelled' | 'pending';

/** 支付类型 */
export type PaymentType = 'membership' | 'donation';

/** 支付状态 */
export type PaymentStatus = 'pending' | 'succeeded' | 'failed' | 'refunded';

/** 统一错误响应（AI_DEVELOPMENT_SPEC §68） */
export interface ApiErrorResponse {
  success: false;
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
  };
  requestId: string;
}

/** 统一分页结构（AI_DEVELOPMENT_SPEC §87） */
export interface Pagination {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}

export interface Paginated<T> {
  items: T[];
  pagination: Pagination;
}

// ===== API 模型（TASK-011 前端接入，与 apps/api DTO 对齐）=====

/** 用户摘要（Auth 响应内嵌，§24；role TASK-019：admin 入口判断用） */
export interface UserSummary {
  id: string;
  email: string;
  displayName: string | null;
  role: string;
}

/** 认证响应（§24/§25） */
export interface AuthResponse {
  user: UserSummary;
  accessToken: string;
  refreshToken: string;
}

/** 当前用户资料（§33；role TASK-019：admin 入口判断用） */
export interface UserProfile {
  id: string;
  email: string;
  displayName: string | null;
  avatarUrl: string | null;
  timezone: string;
  locale: string;
  role: string;
  createdAt: string;
}

/** 孩子（§28） */
export interface Child {
  id: string;
  name: string;
  gender: Gender;
  birthDate: string;
  birthHeightCm: number | null;
  birthWeightKg: number | null;
  fatherHeightCm: number | null;
  motherHeightCm: number | null;
  avatarUrl: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 年龄（§37）：totalMonths/totalDays 为总量，dashboard 场景可不含 totalDays */
export interface AgeInfo {
  years: number;
  months: number;
  days: number;
  totalMonths?: number;
  totalDays?: number;
}

/** 成长记录（§29-§32；WHO 字段无匹配数据时缺失，§35-1） */
export interface GrowthRecord {
  id: string;
  childId: string;
  recordDate: string;
  age: AgeInfo;
  heightCm: number | null;
  weightKg: number | null;
  headCircumferenceCm: number | null;
  bmi: number | null;
  heightZ: number | null;
  heightPercentile: number | null;
  weightZ: number | null;
  weightPercentile: number | null;
  bmiZ: number | null;
  bmiPercentile: number | null;
  headZ: number | null;
  headPercentile: number | null;
  whoVersion: string | null;
  note: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 孩子主页摘要（TASK-011，§54/§112）：无记录时 latestRecord 缺失 */
export interface ChildDashboard {
  child: Child;
  age: AgeInfo;
  latestRecord: GrowthRecord | null;
}

/** 成长曲线点（TASK-012，§33）：percentile 缺失（gender=unknown）时省略 */
export interface GrowthChartPoint {
  date: string;
  ageMonths: number;
  value: number;
  percentile?: number;
}

/** 单指标曲线序列（§58） */
export interface GrowthChartSeries {
  records: GrowthChartPoint[];
}

/** 成长曲线响应（§33/§58）：身高/体重/BMI/头围四类 */
export interface GrowthChart {
  height: GrowthChartSeries;
  weight: GrowthChartSeries;
  bmi: GrowthChartSeries;
  headCircumference: GrowthChartSeries;
}

/** 逐年身高预测点（TASK-021，§44）：age 为周岁，页面展示 6-18 岁 */
export interface YearlyHeightPrediction {
  age: number;
  predictedHeightCm: number;
  lowerCm: number;
  upperCm: number;
}

/**
 * 身高预测结果（TASK-021，§40/§43/§75）：
 * 百分位/生长速度/中靶身高在对应输入缺失时省略；confidence 为数据完整度而非准确率
 */
export interface HeightPrediction {
  currentAgeMonths: number;
  currentHeightCm: number;
  currentPercentile?: number;
  growthVelocityCmPerYear?: number;
  midParentalHeightCm?: number;
  predictedAdultHeightCm: number;
  lowerRangeCm: number;
  upperRangeCm: number;
  confidence: 'low' | 'medium' | 'high';
  yearlyPredictions: YearlyHeightPrediction[];
}

/** 成长照片（TASK-013，§15/§47）：缩略图未生成（处理中/HEIC）时 thumbnailUrl 省略 */
export interface Photo {
  id: number;
  childId: number;
  growthRecordId?: number;
  originalFilename: string;
  mimeType: string;
  width?: number;
  height?: number;
  fileSize: number;
  caption?: string;
  processingStatus: 'pending' | 'processing' | 'ready' | 'failed';
  createdAt: string;
  viewUrl?: string;
  thumbnailUrl?: string;
}

/** 上传申请响应（§47）：客户端直传 uploadUrl（短期签名） */
export interface PresignPhotoResponse {
  photoId: number;
  uploadUrl: string;
  storageKey: string;
  expiresIn: number;
}

/** 时间线条目照片（TASK-014，§52/§120）：短期签名 URL，缩略图未生成时 thumbnailUrl 省略 */
export interface TimelinePhoto {
  id: number;
  thumbnailUrl?: string;
  viewUrl?: string;
}

/** 时间线条目（§52）：growth_record 或 memory 共用结构，按 type 区分（空字段省略） */
export interface TimelineItem {
  type: 'growth_record' | 'memory';
  date: string;
  ageMonths?: number;
  heightCm?: number;
  weightKg?: number;
  headCircumferenceCm?: number;
  note?: string;
  photos?: TimelinePhoto[];
  title?: string;
  content?: string;
}

/** 合并时间线响应（§52）：items 按 date DESC */
export interface TimelineResponse {
  items: TimelineItem[];
}

/** 成长故事（TASK-015，§16/§114）：title 缺省（无标题故事） */
export interface Memory {
  id: number;
  childId: number;
  memoryDate: string;
  title?: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

// ===== Membership（TASK-016，§64：价格与权益配置化） =====

/** 套餐权益（-1 = 不限） */
export interface PlanPermissions {
  childLimit: number;
  photoLimit: number;
  timelineLimit: number;
  unlimitedRecords: boolean;
  advancedPrediction: boolean;
  annualReport: boolean;
  pdfExport: boolean;
  poster: boolean;
  dataExport: boolean;
  aiSummary: boolean;
}

/** 会员套餐（membership_plans 配置化数据） */
export interface MembershipPlan {
  code: MembershipPlanCode;
  name: string;
  priceAmount: number;
  currency: string;
  billingPeriod: string;
  permissions: PlanPermissions;
}

/** 当前订阅状态：free 用户 status/expiresAt/provider 缺省 */
export interface MembershipInfo {
  plan: MembershipPlanCode;
  status?: MembershipStatus;
  expiresAt?: string;
  provider?: string;
  permissions: PlanPermissions;
}

/** 订阅结算响应：checkoutUrl 供跳转支付网关（mock 为本地闭环） */
export interface SubscriptionCheckout {
  provider: string;
  transactionId: string;
  checkoutUrl: string;
}

// ===== Donation（TASK-017，§65：捐赠独立于会员） =====

export type DonationStatus = 'pending' | 'succeeded' | 'failed';

/** 捐赠记录（payments 表 type=donation，§65 必录字段） */
export interface Donation {
  id: number;
  transactionId: string;
  amount: number;
  currency: string;
  provider: string;
  status: DonationStatus;
  paidAt?: string;
  createdAt: string;
}

/** 捐赠结算响应：mock 为本地闭环，真实网关跳 checkoutUrl */
export interface DonationCheckout {
  provider: string;
  transactionId: string;
  checkoutUrl: string;
  amount: number;
  currency: string;
}

// ===== Annual Report（TASK-018，§98/§99：年度报告为 Pro 权益） =====

export type ReportStatus = 'pending' | 'generating' | 'ready' | 'failed';

/** 年度报告（growth_reports 表 report_type=annual） */
export interface GrowthReport {
  id: number;
  childId: number;
  reportType: 'annual';
  year: number;
  status: ReportStatus;
  createdAt: string;
  updatedAt: string;
}

// ===== Admin（TASK-019，规范 §77/§110：后台管理，仅 admin 可访问） =====

/** 后台统计总览（/api/v1/admin/dashboard） */
export interface AdminDashboard {
  totalUsers: number;
  activeUsers: number;
  children: { total: number; active: number; deleted: number };
  growthRecords: number;
  photos: number;
  memories: number;
  activeMemberships: number;
  payments: {
    membershipSucceeded: number;
    membershipRevenue: number;
    donationSucceeded: number;
    donationRevenue: number;
    pending: number;
    failed: number;
  };
  reports: { total: number; ready: number };
  who: { source: string | null; version: string | null; rows: number; importedAt: string | null };
}

/** 后台用户列表行（含活跃孩子数与当前套餐） */
export interface AdminUser {
  id: number;
  email: string;
  displayName: string | null;
  role: string;
  status: string;
  plan: string;
  childCount: number;
  createdAt: string;
}

/** 后台孩子列表行（admin 视角含已软删除） */
export interface AdminChild {
  id: number;
  userId: number;
  ownerEmail: string;
  name: string;
  gender: string;
  birthDate: string;
  status: string;
  createdAt: string;
}

/** 后台支付列表行（membership/donation 共用） */
export interface AdminPayment {
  id: number;
  userId: number;
  userEmail: string;
  provider: string;
  transactionId: string;
  amount: number;
  currency: string;
  paymentType: string;
  status: string;
  paidAt: string | null;
  createdAt: string;
}

/** 后台日志列表行（webhook=支付回调 / audit=管理操作审计，按 kind 区分） */
export interface AdminLog {
  id: number;
  kind: string;
  createdAt: string;
  summary: string;
  reference: string | null;
  actorId: number | null;
  processed: boolean | null;
  error: string | null;
  details: string | null;
}

// ===== Admin（TASK-019，规范 §77/§110：后台管理，仅 admin 可访问） =====

/** 后台统计总览（/api/v1/admin/dashboard） */
export interface AdminDashboard {
  totalUsers: number;
  activeUsers: number;
  children: { total: number; active: number; deleted: number };
  growthRecords: number;
  photos: number;
  memories: number;
  activeMemberships: number;
  payments: {
    membershipSucceeded: number;
    membershipRevenue: number;
    donationSucceeded: number;
    donationRevenue: number;
    pending: number;
    failed: number;
  };
  reports: { total: number; ready: number };
  who: { source: string | null; version: string | null; rows: number; importedAt: string | null };
}

/** 后台用户列表行（含活跃孩子数与当前套餐） */
export interface AdminUser {
  id: number;
  email: string;
  displayName: string | null;
  role: string;
  status: string;
  plan: string;
  childCount: number;
  createdAt: string;
}

/** 后台孩子列表行（admin 视角含已软删除） */
export interface AdminChild {
  id: number;
  userId: number;
  ownerEmail: string;
  name: string;
  gender: string;
  birthDate: string;
  status: string;
  createdAt: string;
}

/** 后台支付列表行（membership/donation 共用） */
export interface AdminPayment {
  id: number;
  userId: number;
  userEmail: string;
  provider: string;
  transactionId: string;
  amount: number;
  currency: string;
  paymentType: string;
  status: string;
  paidAt: string | null;
  createdAt: string;
}

/** 后台日志列表行（webhook=支付回调 / audit=管理操作审计，按 kind 区分） */
export interface AdminLog {
  id: number;
  kind: string;
  createdAt: string;
  summary: string;
  reference: string | null;
  actorId: number | null;
  processed: boolean | null;
  error: string | null;
  details: string | null;
}
