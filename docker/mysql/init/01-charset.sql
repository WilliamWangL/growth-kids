-- GrowthKids 初始化兜底脚本
-- 数据库与用户已由官方镜像根据 MYSQL_* 环境变量创建；
-- 此脚本确保字符集/排序规则符合规范（utf8mb4 / utf8mb4_unicode_ci）。
ALTER DATABASE growthkids CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 集成测试专用库（仅开发/测试环境使用）
CREATE DATABASE IF NOT EXISTS growthkids_test
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON growthkids_test.* TO 'growthkids'@'%';
FLUSH PRIVILEGES;
