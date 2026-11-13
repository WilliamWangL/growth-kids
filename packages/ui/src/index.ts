/**
 * @growthkids/ui — 跨应用共享的基础 UI 原语与设计 token。
 *
 * 设计风格（AI_DEVELOPMENT_SPEC §92-94）：
 * 温暖、清爽、儿童成长、家庭、记录感、照片感；不要做成医疗后台。
 */

/** 设计 token（与 tailwind.config / globals.css 保持一致，§93） */
export const designTokens = {
  colors: {
    primary: '#6366F1',
    secondary: '#F59E0B',
    success: '#22C55E',
    background: '#F8FAFC',
    text: '#0F172A',
    muted: '#64748B',
  },
} as const;

/** 轻量 className 合并工具（无第三方依赖） */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ');
}

/** 卡片容器风格（§94：rounded-2xl + shadow-sm，避免过多边框） */
export const cardClassName = 'rounded-2xl bg-white shadow-sm';

export const PACKAGE_NAME = '@growthkids/ui' as const;
