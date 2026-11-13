import { defineConfig } from 'eslint/config';
import tseslint from 'typescript-eslint';

/**
 * 根级 ESLint 扁平配置 —— 适用于 packages/*（apps/web 使用自身 eslint.config.mjs）。
 */
export default defineConfig([
  {
    ignores: [
      '**/node_modules/**',
      '**/.next/**',
      '**/dist/**',
      '**/target/**',
      '**/coverage/**',
      '**/next-env.d.ts',
    ],
  },
  ...tseslint.configs.recommended,
  {
    files: ['packages/*/src/**/*.{ts,tsx}'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
    },
  },
]);
