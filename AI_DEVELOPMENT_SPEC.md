# 儿童成长记录 + 身高预测平台
# AI Development Specification

> 文档版本：v1.0.0  
> 文档类型：产品 PRD + 技术设计 + AI Coding Spec  
> 目标：本文件可直接提供给 Cursor / Claude Code / Qoder / Codex 等 AI 编程工具，作为项目开发主规范。  
> 默认语言：中文  
> 默认技术栈：Next.js + NestJS + MySQL + Redis + Object Storage  
> 产品定位：帮助父母长期记录孩子身高、体重、头围、成长照片和成长故事，并提供基于公开生长标准的百分位分析、趋势分析和身高预测。

---

# 0. 给 AI 开发代理的最高优先级指令

你是本项目的主开发工程师。

必须严格按照本文件开发，不要自行改变核心架构、数据库模型、API 语义和业务规则。

## 0.1 开发原则

1. Mobile First。
2. TypeScript 全项目强类型，禁止 `any`。
3. 前后端分离。
4. REST API。
5. API 前缀统一 `/api/v1`。
6. 数据库使用 MySQL 8。
7. ORM 使用 mybaitsplus。
8. Redis 用于缓存、队列、限流和异步任务。
9. 图片不得直接存 MySQL。
10. 图片使用 S3 兼容对象存储。
11. 原始 WHO 数据不可被业务代码直接修改。
12. 所有儿童数据必须进行用户所有权校验。
13. 所有时间数据库统一 UTC 存储，前端按用户时区展示。
14. 金额使用 Decimal，不允许使用浮点数保存金额。
15. 支付回调必须验证签名。
16. 所有核心业务必须有单元测试或集成测试。
17. Controller 只负责 HTTP 层，业务逻辑放 Service。
18. 百分位和 Z-score 计算必须集中在 `growth-engine`。
19. 身高预测是估算，不得表述为医学诊断或确定结果。
20. UI 必须优先适配 375px、390px、393px、430px 手机宽度。
21. 不允许为了“方便”绕过权限校验。
22. 不允许硬编码用户 ID、child ID、支付状态。
23. 所有数据库修改必须通过 migration。
24. 每完成一个任务必须运行 lint、typecheck 和相关测试。
25. 不要一次性生成整个项目。按照任务列表逐项实现。

---

# 1. 产品概述

## 1.1 产品名称

暂定：

**GrowthKids**

中文定位：

**儿童成长记录与身高预测平台**

名称可以在开发完成后替换。

---

# 2. 产品目标

帮助父母建立孩子从出生到 18 岁的长期成长档案。

记录：

- 身高
- 体重
- 头围
- BMI
- 成长照片
- 成长故事
- 成长备注
- 年龄
- 百分位
- Z-score
- 成长趋势
- 身高预测
- 年度成长报告

最终形成：

> 数据 → 图表 → 预测 → 照片 → 故事 → 成长时间线 → 年度报告 → 成长纪念品

---

# 3. 核心用户

主要用户：

- 0–18 岁儿童父母
- 新生儿父母
- 希望记录孩子成长的家庭

用户核心需求：

1. 快速记录身高。
2. 快速记录体重。
3. 保存成长照片。
4. 保存成长故事。
5. 查看成长曲线。
6. 查看孩子处于哪个百分位。
7. 预测成年身高。
8. 查看每年预计身高。
9. 生成年度成长报告。
10. 长期保存成长记录。

---

# 4. 产品核心闭环

SEO 身高计算器
↓
免费身高预测
↓
保存预测
↓
注册账号
↓
创建孩子
↓
填写父母身高
↓
填写孩子身高
↓
记录成长
↓
上传照片
↓
形成成长时间线
↓
成长曲线
↓
身高预测
↓
年度报告
↓
会员
↓
持续记录

---

# 5. MVP 功能

第一版必须包含：

## P0

- 注册
- 登录
- 创建孩子
- 编辑孩子
- 删除孩子
- 身高记录
- 体重记录
- 头围记录
- BMI
- 年龄计算
- WHO 百分位
- Z-score
- 身高趋势图
- 体重趋势图
- 身高预测
- 父母身高
- 成长照片
- 成长备注
- 成长时间线
- 基础会员
- 免费/会员权限
- 捐赠
- 用户中心

## P1

- 年度报告
- PDF
- 成长海报
- 数据导出
- 多孩子
- 成长故事
- AI 成长总结
- AI 海报文案

## P2

- 成长视频
- 家庭成员协作
- 邀请爷爷奶奶
- 微信登录
- Google 登录
- Apple 登录
- 多语言
- 多币种
- 更多国家生长标准

---

# 6. 技术架构

```text
                         Internet
                            |
                         CDN/WAF
                            |
                     Next.js Web
                            |
                       REST API
                            |
                       NestJS API
              ┌─────────────┼─────────────┐
              |             |             |
            MySQL         Redis       Object Storage
              |             |             |
          Business DB     Cache        Photos
                            |
                         BullMQ
                            |
                ┌───────────┼───────────┐
                |           |           |
             Photo Job   Report Job  Prediction Job
```

---

# 7. 技术栈

## Frontend

- Next.js 16+
- React
- TypeScript
- Tailwind CSS
- shadcn/ui
- Zustand
- React Hook Form
- Zod
- Recharts
- date-fns
- lucide-react

## Backend

- NestJS
- TypeScript
- Prisma
- MySQL 8
- Redis
- BullMQ
- JWT
- Argon2
- class-validator
- Swagger/OpenAPI

## Storage

推荐：

- Cloudflare R2
- AWS S3
- 阿里云 OSS
- 腾讯云 COS

要求：

S3 Compatible API。

## Image

Sharp。

## Deployment

Docker + Nginx。

---

# 8. Monorepo

```text
growthkids/
├── apps/
│   ├── web/
│   └── api/
│
├── packages/
│   ├── ui/
│   ├── types/
│   ├── validation/
│   └── growth-engine/
│
├── data/
│   └── who/
│
├── docs/
│   ├── PRD.md
│   ├── API.md
│   ├── DATABASE.md
│   ├── GROWTH_ENGINE.md
│   └── AI_DEVELOPMENT_SPEC.md
│
├── docker/
│   ├── mysql/
│   ├── redis/
│   └── nginx/
│
├── docker-compose.yml
├── package.json
├── pnpm-workspace.yaml
└── README.md
```

使用 pnpm workspace。

---

# 9. 前端目录

```text
apps/web/
├── app/
│   ├── (auth)/
│   │   ├── login/
│   │   └── register/
│   │
│   ├── (main)/
│   │   ├── page.tsx
│   │   ├── children/
│   │   ├── growth/
│   │   ├── timeline/
│   │   ├── reports/
│   │   ├── membership/
│   │   └── me/
│   │
│   ├── prediction/
│   ├── privacy/
│   ├── terms/
│   └── layout.tsx
│
├── components/
│   ├── child/
│   ├── growth/
│   ├── photo/
│   ├── timeline/
│   ├── chart/
│   ├── prediction/
│   ├── membership/
│   ├── report/
│   ├── common/
│   └── mobile/
│
├── hooks/
├── lib/
├── stores/
├── types/
└── styles/
```

---

# 10. 后端目录

```text
apps/api/
├── src/
│   ├── main.ts
│   │
│   ├── common/
│   │   ├── guards/
│   │   ├── decorators/
│   │   ├── filters/
│   │   ├── interceptors/
│   │   ├── pipes/
│   │   └── utils/
│   │
│   ├── auth/
│   ├── users/
│   ├── children/
│   ├── growth-records/
│   ├── growth-photos/
│   ├── memories/
│   ├── timeline/
│   ├── growth/
│   ├── prediction/
│   ├── who/
│   ├── reports/
│   ├── membership/
│   ├── payments/
│   ├── donations/
│   ├── notifications/
│   └── admin/
│
│   ├── database/
│   │   ├── prisma/
│   │   └── seed/
│   │
│   └── jobs/
│       ├── photo-processing/
│       ├── report/
│       └── prediction/
│
└── test/
```

---

# 11. 数据库设计

数据库：

```text
MySQL 8
```

字符集：

```text
utf8mb4
```

排序规则：

```text
utf8mb4_unicode_ci
```

---

# 12. User

```sql
CREATE TABLE users (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    locale VARCHAR(20) NOT NULL DEFAULT 'en-US',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);
```

---

# 13. Child

```sql
CREATE TABLE children (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    gender VARCHAR(20) NOT NULL,
    birth_date DATE NOT NULL,
    birth_height_cm DECIMAL(5,2),
    birth_weight_kg DECIMAL(5,2),
    father_height_cm DECIMAL(5,2),
    mother_height_cm DECIMAL(5,2),
    avatar_url VARCHAR(500),
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,

    INDEX idx_children_user_id(user_id),
    CONSTRAINT fk_children_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);
```

gender：

```text
male
female
unknown
```

---

# 14. GrowthRecord

```sql
CREATE TABLE growth_records (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    child_id BIGINT UNSIGNED NOT NULL,
    record_date DATE NOT NULL,

    age_days INT NOT NULL,

    height_cm DECIMAL(6,2),
    weight_kg DECIMAL(6,2),
    head_circumference_cm DECIMAL(6,2),

    bmi DECIMAL(6,3),

    height_z DECIMAL(8,4),
    height_percentile DECIMAL(7,4),

    weight_z DECIMAL(8,4),
    weight_percentile DECIMAL(7,4),

    bmi_z DECIMAL(8,4),
    bmi_percentile DECIMAL(7,4),

    head_z DECIMAL(8,4),
    head_percentile DECIMAL(7,4),

    note TEXT,

    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,

    INDEX idx_growth_child_date(child_id, record_date),

    CONSTRAINT fk_growth_child
        FOREIGN KEY (child_id) REFERENCES children(id)
);
```

同一个孩子同一天原则上只允许一个主成长记录：

```text
UNIQUE(child_id, record_date)
```

如果未来需要一天多次测量，可改成 measurement_type。

---

# 15. GrowthPhoto

```sql
CREATE TABLE growth_photos (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    child_id BIGINT UNSIGNED NOT NULL,
    growth_record_id BIGINT UNSIGNED,

    storage_key VARCHAR(1000) NOT NULL,
    thumbnail_key VARCHAR(1000),

    original_filename VARCHAR(255),
    mime_type VARCHAR(100),
    width INT,
    height INT,
    file_size BIGINT,

    caption VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,

    processing_status VARCHAR(30) NOT NULL DEFAULT 'pending',

    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,

    INDEX idx_photo_child(child_id),
    INDEX idx_photo_growth_record(growth_record_id)
);
```

---

# 16. GrowthMemory

```sql
CREATE TABLE growth_memories (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    child_id BIGINT UNSIGNED NOT NULL,

    memory_date DATE NOT NULL,
    title VARCHAR(200),
    content TEXT NOT NULL,

    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,

    INDEX idx_memory_child_date(child_id, memory_date)
);
```

用途：

例如：

```text
第一次走路
第一次叫爸爸
第一次去海边
第一次上幼儿园
第一次骑自行车
```

---

# 17. WHO Growth Standards

```sql
CREATE TABLE who_growth_standards (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,

    source VARCHAR(50) NOT NULL,
    version VARCHAR(50) NOT NULL,

    sex VARCHAR(20) NOT NULL,
    indicator VARCHAR(50) NOT NULL,

    age_days INT NOT NULL,

    l_value DECIMAL(20,10) NOT NULL,
    m_value DECIMAL(20,10) NOT NULL,
    s_value DECIMAL(20,10) NOT NULL,

    created_at DATETIME(3) NOT NULL,

    UNIQUE KEY uq_who(
        source,
        version,
        sex,
        indicator,
        age_days
    )
);
```

indicator：

```text
height_for_age
weight_for_age
bmi_for_age
head_circumference_for_age
weight_for_height
```

---

# 18. Membership

```sql
CREATE TABLE memberships (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,

    plan VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,

    provider VARCHAR(30),
    provider_subscription_id VARCHAR(255),

    started_at DATETIME(3),
    expires_at DATETIME(3),

    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,

    INDEX idx_membership_user(user_id)
);
```

---

# 19. Payment

```sql
CREATE TABLE payments (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,

    user_id BIGINT UNSIGNED NOT NULL,

    provider VARCHAR(30) NOT NULL,
    transaction_id VARCHAR(255),

    amount DECIMAL(18,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,

    payment_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,

    raw_payload JSON,

    paid_at DATETIME(3),

    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,

    UNIQUE KEY uq_provider_transaction(
        provider,
        transaction_id
    )
);
```

payment_type：

```text
membership
donation
```

---

# 20. Report

```sql
CREATE TABLE growth_reports (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,

    child_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,

    report_type VARCHAR(30) NOT NULL,
    year INT NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'pending',

    storage_key VARCHAR(1000),

    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,

    UNIQUE KEY uq_child_report(
        child_id,
        report_type,
        year
    )
);
```

---

# 21. 数据关系

```text
User
 |
 ├── Children
 │     |
 │     ├── GrowthRecords
 │     │       |
 │     │       └── GrowthPhotos
 │     │
 │     ├── GrowthMemories
 │     │
 │     └── Reports
 │
 ├── Memberships
 │
 ├── Payments
 │
 └── Donations
```

---

# 22. Prisma 规范

所有数据库操作：

```text
PrismaService
```

禁止：

```text
controller -> prisma
```

必须：

```text
controller
   ↓
service
   ↓
repository/prisma
```

业务 Service 必须检查：

```text
child.user_id === currentUser.id
```

---

# 23. REST API

统一：

```text
/api/v1
```

---

# 24. Auth API

## 注册

```http
POST /api/v1/auth/register
```

Request：

```json
{
  "email": "user@example.com",
  "password": "StrongPassword123!",
  "displayName": "William"
}
```

Response：

```json
{
  "user": {
    "id": "1",
    "email": "user@example.com",
    "displayName": "William"
  },
  "accessToken": "...",
  "refreshToken": "..."
}
```

---

# 25. Login

```http
POST /api/v1/auth/login
```

---

# 26. Refresh

```http
POST /api/v1/auth/refresh
```

---

# 27. Logout

```http
POST /api/v1/auth/logout
```

---

# 28. Child API

## 创建孩子

```http
POST /api/v1/children
```

Request：

```json
{
  "name": "Tom",
  "gender": "male",
  "birthDate": "2025-01-01",
  "fatherHeightCm": 170,
  "motherHeightCm": 163
}
```

---

## 查询孩子

```http
GET /api/v1/children
```

---

## 查询详情

```http
GET /api/v1/children/:childId
```

---

## 修改

```http
PATCH /api/v1/children/:childId
```

---

## 删除

```http
DELETE /api/v1/children/:childId
```

默认软删除。

---

# 29. Growth Record API

## 创建

```http
POST /api/v1/children/:childId/growth-records
```

Request：

```json
{
  "recordDate": "2026-09-15",
  "heightCm": 82,
  "weightKg": 11.7,
  "headCircumferenceCm": 47.5,
  "note": "最近开始喜欢跑步"
}
```

系统自动：

1. 计算年龄。
2. 计算 BMI。
3. 查询 WHO 数据。
4. 计算 Z-score。
5. 计算 percentile。
6. 保存结果。

---

# 30. 查询成长记录

```http
GET /api/v1/children/:childId/growth-records
```

参数：

```text
page
pageSize
startDate
endDate
```

---

# 31. 修改成长记录

```http
PATCH /api/v1/growth-records/:recordId
```

修改后必须重新计算：

```text
age
BMI
Z-score
percentile
```

---

# 32. 删除成长记录

```http
DELETE /api/v1/growth-records/:recordId
```

---

# 33. Growth Chart API

```http
GET /api/v1/children/:childId/growth-chart
```

Response：

```json
{
  "height": {
    "records": [
      {
        "date": "2026-01-01",
        "ageMonths": 12,
        "value": 75,
        "percentile": 40
      }
    ]
  },
  "weight": {
    "records": []
  }
}
```

---

# 34. WHO Growth Engine

核心模块：

```text
packages/growth-engine
```

API：

```ts
calculateZScore(
  value: number,
  L: number,
  M: number,
  S: number
): number
```

公式：

当 L != 0：

```text
Z = ((X / M)^L - 1) / (L × S)
```

当 L = 0：

```text
Z = ln(X / M) / S
```

Percentile：

```text
P = NormalCDF(Z)
```

转换为：

```text
0 - 100
```

例如：

```text
Z = 0
P = 50
```

---

# 35. WHO 计算注意事项

必须：

1. 精确匹配 sex。
2. 精确匹配 indicator。
3. 根据 age_days 查询。
4. 如果 WHO 数据不是每日，需要按照官方标准允许的方法处理年龄区间。
5. 不允许随意线性插值替代官方标准定义。
6. 所有 WHO 数据记录 source/version。
7. 数据导入后必须执行校验。
8. 关键计算写单元测试。

---

# 36. BMI

```text
BMI = weightKg / (heightM × heightM)
```

例如：

```text
height = 82cm
weight = 11.7kg

BMI = 11.7 / 0.82²
```

---

# 37. 年龄计算

输入：

```text
birthDate
recordDate
```

输出：

```text
ageYears
ageMonths
ageDays
ageTotalDays
```

不要使用：

```text
currentYear - birthYear
```

必须基于实际日期计算。

---

# 38. Height Prediction Engine

模块：

```text
packages/growth-engine/src/prediction
```

---

# 39. 输入

```ts
interface HeightPredictionInput {
  gender: 'male' | 'female';
  birthDate: string;
  currentHeightCm: number;

  measurements: {
    date: string;
    heightCm: number;
  }[];

  fatherHeightCm?: number;
  motherHeightCm?: number;
}
```

---

# 40. 输出

```ts
interface HeightPredictionResult {
  currentAgeMonths: number;

  currentHeightCm: number;
  currentPercentile?: number;

  growthVelocityCmPerYear?: number;

  midParentalHeightCm?: number;

  predictedAdultHeightCm: number;

  lowerRangeCm: number;
  upperRangeCm: number;

  confidence: 'low' | 'medium' | 'high';

  yearlyPredictions: {
    age: number;
    predictedHeightCm: number;
    lowerCm: number;
    upperCm: number;
  }[];
}
```

---

# 41. Mid-Parental Height

男孩：

```text
(fatherHeight + motherHeight + 13) / 2
```

女孩：

```text
(fatherHeight + motherHeight - 13) / 2
```

结果只能作为一个输入因素。

不能直接把中靶身高当成预测结果。

---

# 42. Prediction V1 算法

预测模型采用多因素组合：

```text
Prediction
=
年龄校正
+
当前身高百分位
+
历史身高趋势
+
父母身高
+
增长速度
```

推荐：

```text
score =
    percentile_component * 0.35
  + parental_component  * 0.30
  + velocity_component  * 0.20
  + age_model_component * 0.15
```

权重必须集中在配置文件。

不要散落在业务代码。

---

# 43. 数据不足时

如果：

```text
只有 1 次身高记录
```

则：

```text
confidence = low
```

如果：

```text
>= 2 次记录
且间隔 >= 3个月
```

可以：

```text
confidence = medium
```

如果：

```text
>= 3 次有效记录
且覆盖较长时间
且有父母身高
```

可以：

```text
confidence = high
```

confidence 是算法数据完整度，而不是医学意义上的准确率。

---

# 44. Prediction 页面

页面：

```text
/prediction
```

输入：

- 性别
- 出生日期
- 当前身高
- 父亲身高
- 母亲身高

输出：

```text
预计成年身高

175 cm

范围：

168–182 cm

当前身高百分位：

P62

预计趋势：

...
```

下面：

```text
6岁
7岁
8岁
9岁
...
18岁
```

使用折线图。

---

# 45. Prediction Disclaimer

页面必须显示：

> 身高预测基于用户输入数据和公开生长标准进行估算，仅用于成长趋势参考，不代表医学诊断或确定的成年身高。实际身高会受到遗传、营养、睡眠、运动、青春期发育等多种因素影响。

不要使用：

```text
一定长到
保证
准确预测
100%准确
```

---

# 46. 图片系统

## 上传流程

不要：

```text
Browser -> API -> MySQL
```

必须：

```text
Browser
  ↓
API 获取 presigned URL
  ↓
Object Storage
  ↓
API finalize
  ↓
Photo DB
```

---

# 47. Upload API

```http
POST /api/v1/children/:childId/photos/presign
```

Request：

```json
{
  "filename": "IMG_1234.jpg",
  "mimeType": "image/jpeg",
  "size": 3254321
}
```

Response：

```json
{
  "uploadUrl": "...",
  "storageKey": "children/123/2026/09/abc.jpg",
  "expiresIn": 600
}
```

---

# 48. Finalize Photo

```http
POST /api/v1/photos/:photoId/finalize
```

服务器验证：

- storage key
- object exists
- file size
- mime
- ownership

然后进入图片处理队列。

---

# 49. 图片限制

支持：

```text
JPEG
PNG
WEBP
HEIC
```

最大：

```text
10MB
```

单次：

```text
最多 9 张
```

---

# 50. 图片处理

Sharp：

1. 自动 EXIF rotation。
2. 生成 thumbnail。
3. 最大展示尺寸。
4. WebP。
5. 删除敏感 EXIF。
6. 生成缩略图。
7. 更新 processing_status。

---

# 51. Photo Processing 状态

```text
pending
processing
ready
failed
```

---

# 52. Timeline

API：

```http
GET /api/v1/children/:childId/timeline
```

返回：

```json
{
  "items": [
    {
      "type": "growth_record",
      "date": "2026-09-15",
      "heightCm": 82,
      "weightKg": 11.7,
      "photos": [],
      "note": "..."
    },
    {
      "type": "memory",
      "date": "2026-08-01",
      "title": "第一次去海边",
      "content": "..."
    }
  ]
}
```

后端统一排序：

```text
date DESC
```

---

# 53. Growth Timeline UI

卡片：

```text
┌───────────────────────┐
│ 2026年9月15日          │
│ 19个月                 │
│                       │
│ [照片] [照片] [照片]  │
│                       │
│ 身高 82 cm             │
│ 体重 11.7 kg           │
│                       │
│ 最近开始喜欢跑步        │
└───────────────────────┘
```

---

# 54. Home 页面

顶部：

```text
孩子头像
Tom
19个月
```

核心指标：

```text
身高
82 cm
P62

体重
11.7 kg

头围
47.5 cm
```

CTA：

```text
+ 记录今天的成长
```

---

# 55. Bottom Navigation

手机端：

```text
首页
成长
＋记录
时间线
我的
```

中间：

```text
+记录
```

使用突出按钮。

---

# 56. Growth Record UI

表单：

```text
记录日期

身高
[      ] cm

体重
[      ] kg

头围
[      ] cm

成长照片
[ + 添加照片 ]

成长备注
[ 今天有什么特别的？ ]

[ 保存成长记录 ]
```

要求：

- 自动保存草稿。
- 表单错误即时显示。
- 保存成功 toast。
- 上传照片有进度。
- 网络失败可以重试。

---

# 57. Photo UX

点击：

```text
添加照片
```

弹出：

```text
拍照
从相册选择
```

上传后：

```text
[缩略图]
✓ 已上传
```

失败：

```text
上传失败
[重新上传]
```

---

# 58. Growth Chart

必须包含：

## 身高

- 实际身高
- percentile
- 时间
- 年龄

## 体重

- 实际体重
- percentile

## BMI

- BMI
- percentile

## 头围

- 头围
- percentile

---

# 59. Chart 设计

移动端：

- 横向可滚动
- tooltip
- 当前点高亮
- 不显示过多数据
- 支持 1Y / 3Y / All

桌面：

- 最大宽度 1100px

---

# 60. Growth Percentile UI

示例：

```text
身高

82 cm

P62

高于约 62% 的同年龄同性别儿童
```

注意：

必须根据当前 WHO 数据实际计算。

不要写：

```text
比62%的孩子聪明
```

百分位只表示相对生长位置。

---

# 61. AI 功能

AI 不是 MVP 强依赖。

AI 可以用于：

### 成长故事整理

用户输入：

```text
今天第一次自己走了十几步
```

AI 可以整理：

```text
今天是值得记住的一天。你第一次独立走出了十几步。
```

---

# 62. AI 年度总结

输入：

- 身高记录
- 体重记录
- 照片
- 用户故事

输出：

```text
2026 成长回顾

这一年，你从……
身高变化……
体重变化……

今年特别值得记录的瞬间：
1....
2....
3....
```

禁止：

- 医疗诊断
- 疾病判断
- 健康风险诊断
- “你发育异常”
- “你的孩子一定会长到”
- “保证身高”

---

# 63. AI Poster

生成：

```text
2026 Growth Poster

Tom

身高：
75 → 82 cm

体重：
9.8 → 11.7 kg

今年照片：

[Photo]
[Photo]
[Photo]
```

---

# 64. Membership

## Free

```text
1个孩子
基础成长记录
基础图表
基础身高预测
最近20条时间线
最多100张照片
```

## Pro

```text
最多5个孩子
无限成长记录
无限照片
完整时间线
高级预测
年度报告
PDF
成长海报
数据导出
AI成长总结
```

具体价格必须配置化：

```text
membership_plans
```

不要写死代码。

---

# 65. Donation

捐赠独立于会员。

页面：

```text
支持 GrowthKids

$3
$5
$10
$20

自定义金额

[支持项目]
```

数据库必须记录：

```text
user_id
amount
currency
provider
transaction_id
status
```

---

# 66. Payment

支付系统必须抽象：

```ts
interface PaymentProvider {
  createCheckout(): Promise<CheckoutResult>;
  verifyWebhook(): Promise<boolean>;
  getPayment(): Promise<PaymentResult>;
  refund(): Promise<RefundResult>;
}
```

这样未来可以支持：

```text
PayPal
Stripe
其他支付
```

---

# 67. Payment Webhook

必须：

1. 验证签名。
2. 验证 transaction ID。
3. 防重复。
4. 使用数据库事务。
5. 更新 payment。
6. 更新 membership。
7. 写 webhook log。

必须支持幂等。

---

# 68. Error Response

统一：

```json
{
  "success": false,
  "error": {
    "code": "CHILD_NOT_FOUND",
    "message": "Child not found",
    "details": {}
  },
  "requestId": "req_xxx"
}
```

---

# 69. Error Codes

至少：

```text
AUTH_INVALID_CREDENTIALS
AUTH_TOKEN_EXPIRED
AUTH_UNAUTHORIZED

CHILD_NOT_FOUND
CHILD_ACCESS_DENIED
CHILD_INVALID_DATA

GROWTH_RECORD_NOT_FOUND
GROWTH_RECORD_DUPLICATE_DATE
GROWTH_RECORD_INVALID_HEIGHT
GROWTH_RECORD_INVALID_WEIGHT

PHOTO_NOT_FOUND
PHOTO_TOO_LARGE
PHOTO_INVALID_TYPE
PHOTO_UPLOAD_FAILED

WHO_DATA_NOT_FOUND
PREDICTION_INSUFFICIENT_DATA

MEMBERSHIP_REQUIRED
PAYMENT_FAILED
PAYMENT_ALREADY_PROCESSED

INTERNAL_ERROR
```

---

# 70. 权限模型

所有请求：

```text
JWT
 ↓
CurrentUser
 ↓
Service
 ↓
Ownership Check
```

例如：

```ts
const child = await prisma.child.findFirst({
  where: {
    id: childId,
    userId: currentUser.id,
    status: 'active'
  }
});
```

不要：

```ts
findUnique({ where: { id: childId }})
```

然后直接返回。

---

# 71. Security

必须：

- Argon2 password hash
- JWT access token
- refresh token rotation
- HTTPS
- CORS
- Helmet
- Rate Limit
- Login brute force protection
- API validation
- SQL injection protection
- XSS protection
- CSRF strategy
- signed object URLs
- private object bucket
- webhook signature validation
- audit logs

---

# 72. Privacy

儿童照片属于高度私密数据。

默认：

```text
private
```

不得：

- 搜索引擎索引
- 公开访问
- 默认分享
- 暴露 storage key

用户主动分享时：

生成：

```text
share_token
```

例如：

```text
/share/growth/abc123
```

必须可以：

```text
关闭分享
重新生成 token
设置过期时间
```

---

# 73. SEO

SEO 页面：

```text
/
 /height-prediction
 /child-height-calculator
 /growth-percentile
 /who-growth-chart
 /blog/*
```

动态用户数据：

```text
noindex
```

公开工具：

```text
index
```

---

# 74. SEO 首页

Title：

```text
Child Growth Tracker & Height Predictor | GrowthKids
```

Description：

```text
Track your child's height, weight, growth photos and milestones. View growth percentiles and estimate future height with a simple growth tracker.
```

具体 SEO 文案后续根据目标国家和语言调整。

---

# 75. 公开 Height Calculator

无需注册：

输入：

```text
gender
birth date
height
father height
mother height
```

输出：

```text
current percentile
estimated adult height
range
yearly prediction
```

然后：

```text
保存成长记录
```

要求登录。

---

# 76. SEO Funnel

```text
Google Search
      ↓
Height Calculator
      ↓
Free Prediction
      ↓
Save Result
      ↓
Register
      ↓
Create Child
      ↓
Add Growth
      ↓
Upload Photos
      ↓
Timeline
      ↓
Annual Report
      ↓
Membership
```

---

# 77. Admin

后台：

```text
/admin
```

功能：

- 用户
- 孩子数量统计
- 成长记录数量
- 图片数量
- 会员
- 支付
- 捐赠
- WHO 数据版本
- 报告
- 错误日志
- 系统配置

---

# 78. Config

所有可变化参数配置化：

```text
MEMBERSHIP_PRICE
PHOTO_MAX_SIZE
PHOTO_MAX_COUNT
PREDICTION_WEIGHTS
PREDICTION_RANGE
FREE_CHILD_LIMIT
FREE_PHOTO_LIMIT
```

不要硬编码。

---

# 79. 环境变量

```env
NODE_ENV=development

DATABASE_URL=mysql://user:password@mysql:3306/growthkids

REDIS_URL=redis://redis:6379

JWT_ACCESS_SECRET=
JWT_REFRESH_SECRET=

S3_ENDPOINT=
S3_REGION=
S3_BUCKET=
S3_ACCESS_KEY=
S3_SECRET_KEY=

NEXT_PUBLIC_API_URL=

PAYPAL_CLIENT_ID=
PAYPAL_CLIENT_SECRET=
PAYPAL_WEBHOOK_ID=

AI_PROVIDER=
AI_API_KEY=
```

生产环境禁止提交 `.env`。

必须提供：

```text
.env.example
```

---

# 80. Docker Compose

至少：

```text
web
api
mysql
redis
nginx
```

开发环境：

```bash
docker compose up -d
```

---

# 81. Redis

用途：

```text
Cache
Queue
Rate Limit
Session-related temporary state
```

BullMQ：

```text
photo-processing
report-generation
prediction-recalculation
notification
```

---

# 82. Logging

使用结构化 JSON：

```json
{
  "timestamp": "...",
  "level": "info",
  "service": "api",
  "requestId": "req_123",
  "userId": "123",
  "message": "growth record created"
}
```

禁止日志输出：

- password
- access token
- refresh token
- payment secret
- signed upload URL
- private photo URL

---

# 83. Monitoring

MVP：

- application logs
- error logs
- API latency
- HTTP 5xx
- queue failures
- database connection
- Redis connection

后续：

- Sentry
- Prometheus
- Grafana

---

# 84. Testing

必须：

## Unit Test

测试：

```text
年龄计算
BMI
WHO Z-score
percentile
mid-parental height
prediction
```

## Integration Test

测试：

```text
register
login
create child
create growth record
upload photo
timeline
prediction
membership
payment webhook
```

## E2E

至少：

```text
注册
→ 创建孩子
→ 添加成长记录
→ 上传照片
→ 查看时间线
→ 查看预测
```

---

# 85. Growth Engine Test

例如：

```ts
describe('calculateZScore', () => {
  it('should calculate z score', () => {});
});
```

必须覆盖：

```text
L = 0
L != 0
X <= 0
M <= 0
invalid S
```

---

# 86. Prediction Test

至少：

```text
只有1条记录
2条记录
3条记录
无父母身高
有父母身高
男孩
女孩
异常增长速度
不同年龄
```

---

# 87. API Pagination

统一：

```text
page
pageSize
```

Response：

```json
{
  "items": [],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "total": 100,
    "totalPages": 5
  }
}
```

最大：

```text
pageSize <= 100
```

---

# 88. API 日期规范

API 输入：

```text
YYYY-MM-DD
```

时间：

```text
ISO 8601
```

数据库：

```text
UTC
```

---

# 89. API Version

当前：

```text
/api/v1
```

未来：

```text
/api/v2
```

禁止破坏性修改 v1。

---

# 90. Frontend 状态管理

Zustand 管理：

```text
auth
currentChild
ui
```

Server data：

推荐 React Query / TanStack Query。

不要把所有 API 数据放 Zustand。

---

# 91. Form

React Hook Form + Zod。

所有输入：

```text
schema validation
```

前后端都验证。

---

# 92. Design System

主风格：

```text
温暖
清爽
儿童成长
家庭
记录感
照片感
```

不要做成医疗后台。

---

# 93. UI 色彩

建议：

```text
Primary: #6366F1
Secondary: #F59E0B
Success: #22C55E
Background: #F8FAFC
Text: #0F172A
Muted: #64748B
```

颜色必须集中：

```text
tailwind.config
```

---

# 94. Card

成长数据采用：

```text
rounded-2xl
shadow-sm
```

避免过多边框。

---

# 95. Mobile Layout

最大内容宽度：

```text
430px mobile
```

桌面：

```text
max-width: 1200px
```

---

# 96. Loading

所有异步页面必须：

```text
Skeleton
```

禁止长时间白屏。

---

# 97. Empty State

例如：

```text
还没有成长记录

记录第一次身高
保存孩子成长的每一个瞬间

[添加成长记录]
```

---

# 98. Annual Report

报告包含：

```text
孩子基本信息
年龄
年初身高
年末身高
身高增长
体重变化
头围变化
百分位变化
成长照片
成长故事
身高预测
年度总结
```

---

# 99. PDF

使用：

```text
Puppeteer
```

HTML → PDF。

任务：

```text
report-generation
```

异步生成。

---

# 100. Data Export

会员支持：

```text
CSV
JSON
PDF
```

CSV：

```text
date
age
height
weight
head_circumference
bmi
height_percentile
weight_percentile
bmi_percentile
note
```

---

# 101. 数据删除

用户可以：

```text
删除孩子
删除成长记录
删除照片
删除成长故事
删除账户
```

账户删除必须：

```text
soft delete + async purge
```

根据隐私政策执行最终物理删除。

---

# 102. Child Delete

删除孩子：

```text
child
growth records
photos
memories
reports
```

必须使用事务或异步删除流程。

Object Storage 文件也必须删除。

---

# 103. Photo Delete

删除数据库记录后：

```text
queue delete object
```

如果 object 删除失败：

记录任务失败。

不能留下无法追踪的孤儿文件。

---

# 104. AI Coding Rules

AI 开发时：

### Rule 1

先理解现有代码。

不要直接重构。

### Rule 2

一次只做一个 TASK。

### Rule 3

修改前先告诉用户：

```text
修改哪些文件
为什么
```

### Rule 4

完成后输出：

```text
Changed files
Tests
Potential issues
Next task
```

### Rule 5

不要修改无关代码。

---

# 105. Git

分支：

```text
main
develop
feature/*
fix/*
```

commit：

```text
feat(child): add child profile
feat(growth): add growth record
feat(prediction): add height prediction
feat(photo): add photo upload
fix(growth): fix percentile calculation
```

---

# 106. AI 开发任务

## TASK-001 Project Init

创建：

```text
monorepo
Next.js
NestJS
packages
pnpm
eslint
prettier
typescript
```

验收：

```text
pnpm install
pnpm dev
```

---

## TASK-002 Docker

创建：

```text
MySQL
Redis
Nginx
```

验收：

```bash
docker compose up -d
```

---

## TASK-003 Prisma

创建：

```text
schema.prisma
migration
seed
```

完成所有核心表。

---

## TASK-004 Auth

实现：

```text
register
login
refresh
logout
JWT
Argon2
```

---

## TASK-005 User

实现：

```text
profile
update profile
timezone
locale
```

---

## TASK-006 Child

实现：

```text
create
list
detail
update
delete
```

---

## TASK-007 Growth Record

实现：

```text
create
list
detail
update
delete
```

自动计算：

```text
age
BMI
percentile
Z-score
```

---

## TASK-008 WHO Data

完成：

```text
WHO 数据导入
CSV parser
validation
seed
version
```

必须使用官方来源数据。

---

## TASK-009 Growth Engine

实现：

```text
age
BMI
Z-score
percentile
```

单元测试。

---

## TASK-010 Prediction

实现：

```text
mid-parental height
growth velocity
percentile
prediction
range
confidence
yearly prediction
```

单元测试。

---

## TASK-011 Dashboard

实现：

```text
Home
Child summary
latest growth
CTA
```

---

## TASK-012 Growth Chart

实现：

```text
height chart
weight chart
BMI chart
head chart
```

---

## TASK-013 Photo

实现：

```text
presigned URL
upload
finalize
processing
thumbnail
delete
```

---

## TASK-014 Timeline

实现：

```text
growth record
memory
photo
merged timeline
```

---

## TASK-015 Memory

实现：

```text
create
edit
delete
timeline
```

---

## TASK-016 Membership

实现：

```text
plans
subscription
permissions
```

---

## TASK-017 Donation

实现：

```text
donation checkout
payment
webhook
history
```

---

## TASK-018 Annual Report

实现：

```text
annual calculation
HTML report
PDF
storage
download
```

---

## TASK-019 Admin

实现：

```text
dashboard
users
children
payments
membership
WHO version
logs
```

---

## TASK-020 Production

实现：

```text
Docker production
Nginx
HTTPS
CI/CD
backup
monitoring
logging
```

---

# 107. AI Agent 执行模板

每个 TASK 开始时：

```text
TASK ID:
TASK NAME:

目标：

需要修改：

数据库：

API：

Frontend：

测试：

验收标准：
```

开发完成后：

```text
TASK COMPLETED

Changed Files:
-

Database:
-

API:
-

Tests:
-

Known Issues:
-

Next Task:
```

---

# 108. AI Agent Prompt

把下面内容作为 AI Coding Agent 的系统提示：

```text
你现在是 GrowthKids 项目的 Senior Full Stack Engineer。

请严格阅读 AI_DEVELOPMENT_SPEC.md。

开发规则：

1. 不得随意改变技术栈。
2. 不得修改核心数据库结构，除非任务明确要求。
3. 不允许 TypeScript any。
4. 所有 API 必须使用 /api/v1。
5. 所有 API 必须进行 DTO validation。
6. 所有 child 数据必须验证当前用户 ownership。
7. 图片必须使用对象存储。
8. 不允许将图片 binary 存数据库。
9. 所有金额使用 Decimal。
10. 所有支付 webhook 必须幂等。
11. WHO 数据必须记录 source/version。
12. WHO 数据不可被业务代码修改。
13. 生长计算必须使用 growth-engine。
14. 身高预测必须明确标记为 estimate。
15. 禁止生成医疗诊断。
16. Controller 不写业务逻辑。
17. Service 负责业务逻辑。
18. Prisma 负责数据库。
19. 所有核心逻辑必须测试。
20. 每次修改后运行：
   - lint
   - typecheck
   - unit test
   - integration test（如果涉及 API）
21. 不要一次实现多个 TASK。
22. 不要修改无关文件。
23. 不要删除已有测试。
24. 不要通过注释关闭 lint。
25. 不要用 any 绕过类型错误。
26. 不要硬编码配置。
27. 所有环境变量必须进入 .env.example。
28. 所有 migration 必须可重复部署。
29. 所有用户私有照片必须使用短期签名 URL。
30. 所有错误必须使用统一 ErrorResponse。

工作方式：

第一步：
检查当前仓库。

第二步：
判断当前 TASK 需要修改哪些文件。

第三步：
实施最小必要修改。

第四步：
运行测试。

第五步：
修复错误。

第六步：
输出：

Changed Files
Implementation
Tests
Potential Issues
Next Task

不要擅自开始下一个 TASK。
```

---

# 109. Definition of Done

一个功能只有满足以下条件才能算完成：

```text
[ ] 功能实现
[ ] UI 完成
[ ] API 完成
[ ] DTO 完成
[ ] 权限完成
[ ] 数据库 migration
[ ] 单元测试
[ ] 集成测试
[ ] 错误处理
[ ] Loading
[ ] Empty State
[ ] Mobile UI
[ ] Desktop UI
[ ] TypeScript 无 any
[ ] ESLint 通过
[ ] Typecheck 通过
[ ] Build 通过
```

---

# 110. MVP 页面清单

```text
/
首页

/height-prediction
身高预测

/children/new
创建孩子

/children/:id
孩子主页

/children/:id/growth
成长记录

/children/:id/growth/new
添加成长

/children/:id/charts
成长曲线

/children/:id/timeline
成长时间线

/children/:id/photos
成长照片

/children/:id/reports
成长报告

/membership
会员

/donate
捐赠

/me
我的
```

---

# 111. 首页 UI

首页第一屏：

```text
记录孩子成长的每一个瞬间

身高
体重
照片
故事
成长预测

[开始记录成长]
[免费预测身高]
```

---

# 112. Child Dashboard

```text
Tom
19个月

身高
82 cm
P62

体重
11.7 kg

最近一次记录
2026-09-15

[添加成长记录]

成长趋势
[Chart]

最近照片
[Photos]

最近成长故事
...
```

---

# 113. Timeline

时间线设计：

```text
2026
|
|-- Sep
|    └── 身高 82cm
|         [照片]
|
|-- Aug
|    └── 第一次去海边
|         [照片]
|
|-- Jul
|    └── 第一次自己走路
```

---

# 114. Growth Memory

创建：

```text
日期
标题
内容
照片
```

例如：

```text
第一次叫妈妈

今天早上第一次很清楚地叫了妈妈。
```

---

# 115. 未来视频

后续：

```text
Growth Video
```

自动把：

```text
照片
+
时间
+
身高变化
+
故事
```

生成：

```text
30秒成长视频
```

不进入 MVP。

---

# 116. 分享

用户可以生成：

```text
Growth Share Page
```

例如：

```text
/share/xxxx
```

显示：

```text
孩子昵称
成长时间线
部分照片
身高变化
```

隐私默认：

```text
关闭
```

---

# 117. 数据备份

生产：

```text
MySQL daily backup
Object Storage versioning
```

至少：

```text
每日数据库备份
保留30天
```

---

# 118. 数据恢复

必须有：

```text
backup restore procedure
```

每月至少测试一次恢复。

---

# 119. 性能目标

API：

```text
P95 < 500ms
```

普通页面：

```text
LCP < 2.5s
```

图片：

```text
缩略图优先
lazy loading
```

首页：

```text
不加载所有成长照片
```

---

# 120. 图片优化

列表：

```text
thumbnail
```

详情：

```text
medium
```

原图：

```text
用户点击后加载
```

---

# 121. Cache

Redis：

```text
WHO standards
membership plan
public SEO content
```

不要缓存：

```text
用户私密数据
```

除非明确设计 cache invalidation。

---

# 122. 数据一致性

GrowthRecord 更新：

```text
BEGIN
 ↓
update raw measurement
 ↓
calculate BMI
 ↓
calculate WHO score
 ↓
update derived values
 ↓
COMMIT
```

不能出现：

```text
height changed
percentile unchanged
```

---

# 123. Derived Data

这些字段属于派生数据：

```text
age_days
bmi
height_z
height_percentile
weight_z
weight_percentile
bmi_z
bmi_percentile
head_z
head_percentile
```

原始输入：

```text
record_date
height
weight
head
```

---

# 124. 数据重算

WHO 数据版本变化后：

可以执行：

```text
recalculate growth metrics
```

但必须保留：

```text
who_version
```

避免历史结果无法追踪。

---

# 125. WHO Version Strategy

例如：

```text
WHO_2006
WHO_2007
```

以后增加：

```text
WHO_xxxx
```

业务配置：

```text
default_who_version
```

---

# 126. 国际化

第一版：

```text
zh-CN
en-US
```

后续：

```text
ja-JP
es-MX
pt-BR
```

所有 UI 文案不得硬编码到组件中。

使用：

```text
next-intl
```

---

# 127. Currency

支持：

```text
USD
EUR
GBP
CNY
JPY
```

金额统一：

```text
Decimal
```

---

# 128. Timezone

用户设置：

```text
timezone
```

儿童出生日期：

```text
DATE
```

不要转 UTC。

成长记录日期：

```text
DATE
```

也不要转换成 UTC timestamp。

---

# 129. API Documentation

NestJS 开启：

```text
Swagger
```

开发环境：

```text
/api/docs
```

必须为所有 endpoint 写：

```text
summary
request DTO
response DTO
error
auth
```

---

# 130. OpenAPI

最终生成：

```text
docs/openapi.json
```

用于：

- 前端 API client
- AI coding
- 第三方开发
- 自动生成 SDK

---

# 131. Seed Data

开发环境 seed：

```text
1 demo user
2 demo children
10 growth records
10 memories
sample WHO data
membership plans
```

生产环境禁止自动创建 demo user。

---

# 132. Demo Account

只允许开发环境：

```text
demo@example.com
```

生产：

```text
DISABLED
```

---

# 133. Feature Flags

配置：

```text
ENABLE_AI
ENABLE_REPORT
ENABLE_DONATION
ENABLE_MEMBERSHIP
ENABLE_SHARE
```

---

# 134. AI Provider

不要把 AI SDK 直接写死在业务代码。

抽象：

```ts
interface AIProvider {
  summarizeGrowth(input: GrowthSummaryInput): Promise<string>;
  rewriteMemory(input: MemoryInput): Promise<string>;
  generatePosterText(input: PosterInput): Promise<string>;
}
```

---

# 135. AI 成本控制

AI 请求：

- 异步
- 限流
- 缓存
- 记录 token usage
- 设置用户额度

数据库增加：

```text
ai_usage
```

后续实现。

---

# 136. Analytics

记录：

```text
page_view
prediction_started
prediction_completed
child_created
growth_record_created
photo_uploaded
timeline_viewed
report_generated
membership_started
donation_started
```

不要记录敏感儿童内容到第三方 analytics。

---

# 137. 核心业务指标

```text
注册转化率
创建孩子率
首次成长记录率
首次照片上传率
预测完成率
7日留存
30日留存
会员转化率
捐赠转化率
年度报告生成率
```

---

# 138. 核心产品指标

最重要：

```text
首次成长记录完成率
```

因为：

```text
注册
 ↓
创建孩子
 ↓
记录成长
```

才产生长期价值。

---

# 139. Product North Star

建议：

```text
Monthly Active Growth Records
```

即：

```text
每月产生至少一条成长记录的活跃孩子数量
```

---

# 140. 开发优先级

```text
P0:
Auth
Child
Growth
WHO
Prediction
Photo
Timeline

P1:
Memory
Report
Membership
Donation

P2:
AI
Share
Video
Family Collaboration
Internationalization
```

---

# 141. MVP 最终验收流程

完整跑通：

```text
打开网站
 ↓
免费身高预测
 ↓
输入：
性别
出生日期
当前身高
父亲身高
母亲身高
 ↓
得到预测
 ↓
点击保存
 ↓
注册
 ↓
创建孩子
 ↓
进入孩子主页
 ↓
添加成长记录
 ↓
填写身高
 ↓
填写体重
 ↓
填写头围
 ↓
上传照片
 ↓
保存
 ↓
查看百分位
 ↓
查看成长曲线
 ↓
查看时间线
 ↓
查看预测
```

所有步骤必须正常。

---

# 142. 上线前 Checklist

## Infrastructure

```text
[ ] HTTPS
[ ] DNS
[ ] CDN
[ ] WAF
[ ] MySQL backup
[ ] Redis
[ ] Object Storage
[ ] Monitoring
```

## Security

```text
[ ] JWT
[ ] password hashing
[ ] rate limit
[ ] CORS
[ ] CSP
[ ] webhook signature
[ ] private photos
```

## Product

```text
[ ] register
[ ] login
[ ] child
[ ] growth
[ ] chart
[ ] prediction
[ ] photo
[ ] timeline
```

## SEO

```text
[ ] sitemap
[ ] robots.txt
[ ] canonical
[ ] metadata
[ ] structured data
[ ] OG image
```

## Legal

```text
[ ] Privacy Policy
[ ] Terms
[ ] Cookie Policy
[ ] Child data privacy statement
[ ] Medical disclaimer
```

---

# 143. AI 开发顺序

严格执行：

```text
001 Project
 ↓
002 Docker
 ↓
003 Database
 ↓
004 Auth
 ↓
005 User
 ↓
006 Child
 ↓
007 Growth Record
 ↓
008 WHO
 ↓
009 Growth Engine
 ↓
010 Prediction
 ↓
011 Dashboard
 ↓
012 Charts
 ↓
013 Photos
 ↓
014 Timeline
 ↓
015 Memory
 ↓
016 Membership
 ↓
017 Donation
 ↓
018 Report
 ↓
019 Admin
 ↓
020 Production
```

---

# 144. 第一阶段必须达到的状态

完成 TASK-001 ～ TASK-014 后：

用户必须能够：

```text
注册
登录
创建孩子
输入父母身高
记录身高
记录体重
记录头围
看到百分位
看到成长曲线
预测成年身高
上传成长照片
查看成长时间线
```

这就是 MVP Core。

---

# 145. 最终产品形态

产品最终形成：

```text
              GrowthKids
                   |
       ┌───────────┼───────────┐
       |           |           |
     Growth      Memory      Photos
       |           |           |
       └───────────┼───────────┘
                   |
               Timeline
                   |
          ┌────────┴────────┐
          |                 |
       Charts           Prediction
          |                 |
          └────────┬────────┘
                   |
             Annual Report
                   |
        ┌──────────┴──────────┐
        |                     |
     Membership            Donation
```

---

# 146. 最终 AI Coding 指令

当把本文件交给 AI Coding Agent 后，首先执行：

```text
请先不要写业务代码。

第一步：
扫描当前仓库。

第二步：
确认：
- Node.js 版本
- pnpm/npm/yarn
- 是否已有 Next.js
- 是否已有 NestJS
- 是否已有数据库
- 是否已有 Prisma
- 是否已有 Docker
- 是否已有 CI/CD

第三步：
根据当前仓库状态制定迁移方案。

第四步：
创建 docs/IMPLEMENTATION_STATUS.md。

第五步：
列出 TASK-001 的执行计划。

第六步：
等待用户确认后开始 TASK-001。

除非用户明确要求，否则不要跳过 TASK 顺序。
```

---

# 147. IMPLEMENTATION_STATUS.md 模板

```md
# Implementation Status

## Current Task

TASK-001

## Completed

- [ ]

## In Progress

- [ ]

## Blocked

- [ ]

## Tests

- [ ] lint
- [ ] typecheck
- [ ] unit
- [ ] integration
- [ ] build

## Database Migration

- [ ]

## API

- [ ]

## Frontend

- [ ]

## Known Issues

-

## Next Task

TASK-002
```

---

# 148. 产品长期路线图

## Phase 1

```text
成长记录
百分位
预测
照片
时间线
```

## Phase 2

```text
年度报告
会员
捐赠
PDF
海报
```

## Phase 3

```text
AI成长总结
AI故事
AI海报
```

## Phase 4

```text
家庭共享
成长视频
多语言
多地区
```

---

# 149. 重要产品原则

GrowthKids 不是医疗诊断产品。

它的核心价值：

```text
记录
可视化
趋势
预测
纪念
```

不是：

```text
诊断
治疗
疾病判断
医学建议
```

所有预测必须明确：

```text
estimate
```

所有健康相关内容必须避免过度承诺。

---

# 150. 最终交付物

AI 开发完成后必须产生：

```text
README.md
AI_DEVELOPMENT_SPEC.md

apps/web
apps/api

packages/growth-engine
packages/ui
packages/types
packages/validation

prisma/schema.prisma
prisma/migrations

data/who

docker-compose.yml
Dockerfile

docs/API.md
docs/DATABASE.md
docs/GROWTH_ENGINE.md
docs/openapi.json

.env.example
```

并保证：

```bash
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

全部通过。

---

# END

本文件是 GrowthKids 项目的主开发规范。

任何后续需求都应该优先判断：

1. 是否属于已有模块。
2. 是否需要数据库 migration。
3. 是否需要新增 API。
4. 是否需要新增权限。
5. 是否需要新增测试。
6. 是否影响移动端 UI。
7. 是否影响会员权限。
8. 是否影响隐私。
9. 是否影响增长算法。
10. 是否影响已有 API 兼容性。

如果需求与本规范冲突：

优先保护：

```text
数据安全
用户隐私
API 兼容性
数据一致性
增长计算正确性
移动端体验
```
