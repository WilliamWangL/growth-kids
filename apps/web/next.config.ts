import path from "node:path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // TASK-020：Docker 生产镜像使用 standalone 输出（仅拷贝运行所需依赖）
  output: "standalone",
  // monorepo 下追踪根设为仓库根，否则 standalone 缺少 workspace 包
  outputFileTracingRoot: path.join(process.cwd(), "../../"),
  // monorepo 工作区包直接以 TS 源码形式被 Next.js 转译
  transpilePackages: ["@growthkids/types", "@growthkids/validation", "@growthkids/ui"],
};

export default nextConfig;
