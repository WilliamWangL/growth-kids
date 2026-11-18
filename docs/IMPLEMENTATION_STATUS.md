# Implementation Status — GrowthKids

> 本文件由 AI Coding Agent 维护，记录仓库迁移方案、架构决策与 TASK 进度。
> 主规范：`AI_DEVELOPMENT_SPEC.md`（v1.0.0）

## 仓库扫描结果（初始化时）

| 项目 | 状态 |
|---|---|
| Node.js | v22.14.0 ✅ |
| pnpm | 未安装（使用 corepack 启用） |
| Java | Temurin 21.0.2 ✅ |
| Maven | 3.9.9 ✅ |
| Docker | 29.8.1 ✅ |
| MySQL | 本机 3306 已被其他项目占用（无凭据）→ 本项目使用 Docker 独立实例 |
| Redis | 本机 6379/6380 已被其他项目占用 → 本项目使用 Docker 独立实例 |
| Nginx | 本机 80 已被其他项目占用 → 本项目使用 Docker 独立实例 |
| 已有代码 | 无（仅规范文档），从零搭建 |

## 架构决策（用户确认）

1. **后端**：Java 21 + Spring Boot 3.5.x + **MyBatis Plus**（替代文档中 NestJS + Prisma），业务规则/API 语义/数据库模型严格遵循规范文档。
2. **迁移工具**：Flyway（替代 Prisma migration），所有 DDL 通过版本化 migration。
3. **前端**：Next.js + TypeScript + Tailwind（按文档）。
4. **growth-engine**：以 Java Maven 模块实现（`packages/growth-engine`），Z-score / percentile / prediction 计算集中于此。
5. **照片存储（第一版）**：本地磁盘存储（`STORAGE_LOCAL_PATH`），API 签发短期签名 URL，开发环境由 API 直接流式返回，生产环境经 Nginx `X-Accel-Redirect` 内部转发；不暴露 storage key。对象存储（S3 兼容）留作后续替换。
6. **端口分配**（避免与其他项目冲突）：
   - MySQL: `3310` → 容器 3306
   - Redis: `6385` → 容器 6379
   - Nginx: `8090` → 容器 80
   - API: `8082`
   - Web dev: `3005`
7. **队列**：第一版用 Spring `@Async` + 数据库状态机替代 BullMQ（保持状态流转语义：pending → processing → ready / failed），Redis 仍用于缓存与限流。
8. **密码哈希**：Argon2id（BouncyCastle 纯 Java 实现）。
9. **支付（TASK-016/017）**：`PaymentProvider` 抽象接口 + `MockPaymentProvider`（开发可用，HMAC 签名验证 webhook，幂等）；PayPal/Stripe 适配器留接口，需真实凭据后启用。

## Current Task

（无）—— TASK-001 ~ TASK-022 全部完成

## Completed

- [x] 仓库扫描、架构决策确认
- [x] git 仓库初始化
- [x] TASK-001 项目初始化：
  - pnpm workspace（apps/web + packages/types、validation、ui）
  - Next.js 16.3.8 + React 19 + Tailwind 4（apps/web，dev 端口 3005）
  - Maven 聚合工程（packages/growth-engine + apps/api）
  - Spring Boot 3.5.16 + MyBatis Plus 3.5.17 骨架（端口 8082，/api/v1/health）
  - ESLint 9 + Prettier + 共享 tsconfig 基座
  - 验证通过：pnpm typecheck / pnpm lint / pnpm build / mvn install -DskipTests
- [x] TASK-002 Docker：
  - docker-compose.yml（MySQL 3310 / Redis 6385 / Nginx 8090，端口错开本机其他项目）
  - MySQL utf8mb4_unicode_ci + UTC 验证通过；growthkids / growthkids_test 双库就绪
  - Redis appendonly + 128MB LRU；Nginx 网关（/api、/、/media 内部目录）验证通过
- [x] TASK-003 数据库：
  - Flyway V1 核心表（13 张：users/children/growth_records/growth_photos/growth_memories/
    who_growth_standards/memberships/membership_plans/payments/growth_reports/
    refresh_tokens/webhook_logs/audit_logs）—— 对齐规范 §12-§20，
    附加字段：users.role（§77）、growth_records.who_version（§124）、growth_photos.memory_id（§114）
  - Flyway V2 会员套餐种子（free / pro，价格配置化 §64）
  - MybatisPlusConfig（分页上限 100 §87 + 防全表更新删除）
  - 验证通过：应用启动自动迁移成功，表结构与种子数据核对无误
- [x] TASK-004 Auth：
  - 注册 / 登录 / 刷新（rotation）/ 登出，API 对齐规范 §24-§27
  - 安全：Argon2id 密码哈希（BouncyCastle，PHC 格式）、JWT access token（HS256，15min）、
    refresh token 仅存 SHA-256 摘要（30d）、泄露检测（已轮换 token 复用 → 独立事务撤销全部 token）、
    登录暴力破解防护（Redis 邮箱 10 次 / IP 30 次 / 15 分钟窗口 → 429）
  - 统一错误响应 §68：{success:false, error:{code,message,details}, requestId}；
    RequestIdFilter 生成 req_xxx（MDC + X-Request-Id 响应头）
  - SecurityConfig：stateless、CORS、401/403 JSON；公开端点=注册/登录/刷新/登出/健康检查/OpenAPI/
    公开预测/媒体签名 URL；admin 路由 hasRole(ADMIN)
  - 验证通过：mvn test 22 个测试全绿（集成 12 + JWT 单测 5 + Argon2 单测 5）；
    真实进程 curl 冒烟：注册 201 / 重复 409 / 登录 200 / 错误密码 401 / 轮换 200 /
    复用旧 token 401（并撤销全部）/ 登出 204 / 登出后刷新 401 / 无 token 401 / Swagger UI 200
- [x] TASK-005 User：
  - GET /api/v1/users/me（资料含 timezone/locale/createdAt UTC）、PATCH /api/v1/users/me
  - PATCH 语义：缺省=不变，空字符串=清空（displayName/avatarUrl）
  - timezone 做 IANA ZoneId 校验；locale 白名单配置化（app.user.supported-locales，§126：en-US/zh-CN）
  - 验证通过：UserControllerIntegrationTest 7 例全绿（含鉴权/默认值/持久化/非法时区/非法语言/空请求）
- [x] TASK-006 Child：
  - CRUD 对齐规范 §28：POST 201 / GET 列表（分页 §87）/ GET 详情 / PATCH / DELETE 204（软删除 status=deleted）
  - ownership 校验 §70：非本人 child 一律 CHILD_NOT_FOUND（不泄露存在性）
  - 免费限额配置化（app.membership.free-child-limit=1 → CHILD_LIMIT_REACHED，TASK-016 接入套餐解析）
  - 数据校验：gender 枚举 / birthDate 不可为未来 / 身高体重合理区间；PATCH 语义同 User
  - 通用组件：PageResponse（items+pagination）、PageQuery（page/pageSize 收敛）
  - 验证通过：ChildControllerIntegrationTest 8 例全绿（含限额/隔离/软删除 DB 校验）、全套 37 例全绿
- [x] TASK-007 Growth Record：
  - CRUD 对齐规范 §29-§32：POST 201（嵌套路由）/ GET 列表（分页 + startDate/endDate）/ PATCH（缺省不变）/ DELETE 204（物理删除）
  - 派生计算接入 growth-engine：age_days（基于实际日期 §37，拆分 years/months/days + totalMonths/totalDays）、
    BMI（§36，BigDecimal 3 位小数）；WHO z-score/percentile 于 TASK-009 接入
  - 唯一性 UNIQUE(child_id, record_date) → 409 GROWTH_RECORD_DUPLICATE_DATE（含 PATCH 改日期冲突）
  - 校验：recordDate 不可未来/早于出生；至少一个测量值；height 20-250 / weight 0.3-300 → 领域错误码
  - child.birthDate 变化 → 重算全部记录 age_days；存在早于新生日的记录则拒绝（CHILD_INVALID_DATA）
  - ownership §70：经 child 归属校验，非本人一律 GROWTH_RECORD_NOT_FOUND
  - 验证通过：AgeCalculator 6 例 + BmiCalculator 5 例 + GrowthRecord 集成 14 例；全套 51 例全绿
- [x] TASK-008 WHO 数据：
  - 导入管线：CSV parser（`# source:` / `# version:` 元数据 + 固定表头 + 逐行格式校验，BOM 兼容）
    → Validator（重复行 / 分组内 age_days 严格递增 / M、S 为正 / age 上限 7000 天，聚合报错）
    → Importer（先校验后写入；同 source+version 幂等跳过；事务保证无部分写入）
  - 启动 seed：WhoDataSeeder（ApplicationRunner，app.who.auto-import=true，文件缺失快速失败）
  - 只读查询：WhoGrowthStandardMapper.findLatestUpTo（§35-4 取不晚于 ageDays 的最近锚点，
    禁止随意线性插值——该语义直接服务 TASK-009 的 WHO 匹配）
  - 数据文件：官方数据无法在开发环境完整获取 → 内置合成示例数据（source=growthkids-example-synthetic，
    version=2026.1-example，0-60 月/每 3 月锚点 × 4 指标 × 2 性别 = 168 行，由 scripts/generate-example-who-data.py 确定性生成），
    替换官方数据的流程见 data/who/README.md（详见 Known Issues）
  - 验证通过：WhoCsvParser 10 例 + WhoDataValidator 8 例 + Importer 集成 4 例（seed 168 行 / 锚点回退 / 幂等 / 非法不落库）；全套 85 例全绿
- [x] TASK-009 Growth Engine：
  - growth-engine 新增 `LmsCalculator`（§34：L≠0 用幂公式、L=0 用对数公式；Z 保留 4 位小数）
    与 `NormalDistribution`（A&S 7.1.26 erf 近似，P = Φ(Z)×100）；age/BMI 已于 TASK-007 完成
  - API 新增 `WhoMetricService`：精确匹配 sex/indicator（§35-1/2），按 age_days 取最近锚点（§35-4，
    不插值）；数据集取最近一次导入（findLatestImported，换版本无需配置）；计算成功回填 who_version（§124）
  - GrowthRecordService 三处接入：create 派生全量 WHO 指标；PATCH 在日期/测量值变化时重算；
    child.birthDate 变化时按新 age_days 逐条重算（§31）
  - gender=unknown 不参与 WHO 匹配（z/percentile/whoVersion 为 null，§35-1）
  - 验证通过：LmsCalculator 6 例（标准正态表对照 Φ(±1.96)/Φ(1)/Φ(3)）+ GrowthRecord 集成 15 例
    （锚点 548/457 天确定性 z/percentile 断言 + unknown 跳过）；全套 92 例全绿
- [x] TASK-010 Prediction：
  - growth-engine 新增 prediction 包（§38-§45）：HeightPredictionEngine（多因素加权组合 §42，
    缺失因子权重按比例重分配）、PredictionConfig（权重/成年参照/限幅/置信度阈值全部集中在配置，§42 要求）、
    MidParentalHeightCalculator（§41）、GrowthVelocityCalculator（历史平均速度 cm/年，跨度≥28 天）
  - 百分位因子依赖调用方传入的 WHO z-score（引擎不直接访问 WHO 数据，§34 职责分离）；
    z 限幅 ±3、速度比限幅 [0.8, 1.2] 防止复合放大
  - 置信度 §43 实现：low（<2 条或跨度<90 天）/ medium / high（≥3 条 + 跨度≥180 天 + 父母齐全）；
    预测区间半宽 = 成年 SD × 置信度系数（1.5/1.0/0.75）
  - 年度预测 6-18 岁（§44 展示范围），按成熟度进度在 [当前身高, 预测成年身高] 间插值；
    成熟度表为公开中位身高的近似值（Known Issues 已记录）
  - 注：规范 §44 为前端预测页面（/prediction），无独立后端 API 端点；引擎已就绪供页面后续接入
  - 验证通过：MidParentalHeightCalculator 5 例 + GrowthVelocityCalculator 8 例 + HeightPredictionEngine 7 例
    （期望值经 Python decimal HALF_UP 语义复核 + 链接舍入容差断言）；全套 112 例全绿
- [x] TASK-011 Dashboard（首页 + 孩子摘要）：
  - 后端新增聚合端点 GET /api/v1/children/{childId}/dashboard（规范无此端点；
    理由：§112 孩子摘要需组合数据，风格对齐 §52 timeline 聚合；ownership 集中在校验）
  - DashboardService = childService.get（ownership）+ growthRecordService.getLatest（新增，
    orderByDesc record_date,id LIMIT 1）+ AgeCalculator（服务器 UTC 当天基准）
  - 前端首个真实闭环：lib/api.ts（统一 apiFetch + 401 自动刷新/并发去重 + ApiError +
    错误码中文映射）+ lib/session.ts（localStorage token）+ components/form.tsx（表单基件）
  - 页面：/login、/register（zod 前置校验 + 注册即登录）、首页五态（loading/guest 营销首屏 §111/
    no-child/error/孩子摘要 dashboard §54/§112：头像/名字/月龄文案/身高·体重·头围三指标卡含百分位徽标/
    最近记录/CTA）、/children/new（创建孩子 §28）、/children/[id]/growth/new（添加记录 §29）
  - 共享包：types 补全 API 模型（AuthResponse/Child/GrowthRecord/ChildDashboard 等）；
    validation 补全 login/register/createChild/createGrowthRecord schema（与后端规则同步，空串可选测量→undefined）
  - 验证通过：DashboardControllerIntegrationTest 4 例（无记录/latest 排序+WHO/ownership 404/401），
    全套 116 例全绿；pnpm lint/typecheck/build 全绿（6 路由）；真实进程冒烟：注册→创建孩子→添加记录→
    dashboard 聚合数据正确（BMI 17.4 / heightZ -2.3452 / P0.9508 / CORS 头）+ 浏览器 UI 全链路
    （登录/表单提交 201/重复日期 409 中文文案/dashboard 展示/退出）
- [x] TASK-012 Growth Chart（四类曲线）：
  - 后端 GET /api/v1/children/{childId}/growth-chart（§33）：身高/体重/BMI/头围四组序列，
    每点 date/ageMonths（§37 整月龄）/value/percentile；缺测量的记录不产生对应点；
    图表场景一次全量不分页；ownership 经 ChildService（§70）
  - 前端 /children/[id]/charts（§110 路由）：自绘 SVG（无图表依赖），四指标 tab +
    1年/3年/全部范围切换（§59）、横向滚动、点击点 tooltip（日期/月龄/值/P 百分位）、
    末点高亮；百分位说明文案遵循 §60（不作健康结论）；桌面最大宽度 1100px
  - 首页 dashboard“成长趋势”占位卡接入真实入口
  - types 补充 GrowthChart/GrowthChartSeries/GrowthChartPoint
  - 验证通过：GrowthChartControllerIntegrationTest 4 例（四组序列/ageMonths/缺测量跳点/
    percentile 与创建响应一致/空序列/ownership 404/401），API 全套 86 例全绿；
    pnpm lint/typecheck/build 全绿（7 路由）；curl 冒烟四组序列正确 +
    浏览器 UI 冒烟（登录→首页入口→曲线页→tooltip→tab/范围切换）全通过
- [x] TASK-013 Photo（本地存储/签名 URL/缩略图）：
  - 本地存储基座：StorageProperties（对齐 application.yml 预置段：local-path/signing-secret/
    signed-url-ttl-seconds/photo-max-size-bytes）+ LocalStorageService（storage key 相对路径、
    防目录穿越、写/读/存在/大小/删除）+ StorageSigner（HMAC-SHA256 短期签名 URL §120 规则 29，
    subject 约定 upload:{storageKey} / view:{photoId}:{variant}，时序安全比较）
  - 上传流程（§46-§48）：POST /children/{childId}/photos/presign（创建 pending 记录 + 签发上传 URL）
    → PUT /api/v1/media/upload?token=（无 JWT 直传，限长读取 §49）→ POST /photos/{photoId}/finalize
    （验证对象存在/大小后触发异步处理；重复 finalize 幂等）
  - 异步处理（§50/§51）：@Async + DB 状态机 pending→processing→ready/failed（CAS 防并发重复处理，
    架构决策 7）；EXIF orientation 3/6/8 修正（metadata-extractor）、缩略图 ≤400px JPEG
    （重新编码天然去除敏感 EXIF §50.5）、回填 width/height；HEIC 等不可解码 → ready + 原图直出（Known Issues）
  - 访问与删除：GET /api/v1/media/{photoId}/{variant}?token=（签名自校验，original 存储 MIME /
    thumb image/jpeg，无缩略图时回落原图/返回 404）；DELETE /photos/{photoId} 同步删两文件 + 记录；
    列表分页附短期签名 URL；ownership 经 child 归属（§70）
  - 前端 /children/[id]/photos（§57/§110）：多选上传（JPEG/PNG/WEBP/HEIC，≤10MB，≤9 张 §49）、
    XMLHttpRequest 上传进度（§56）、失败重新上传（§57）、网格缩略图（处理中转圈/失败提示）、
    异步轮询 ready（上限 30 次）；首页“最近照片”入口接入
  - 验证通过：StorageSignerTest 6 例（往返/篡改/过期/错误密钥/畸形）+
    PhotoControllerIntegrationTest 10 例（全流程含真实 JPEG 缩略图/未上传 400/格式/超限/
    篡改 token/删除含文件清理/ownership/401），API 全套 98 例全绿；
    pnpm lint/typecheck/build 全绿（8 路由）；curl 冒烟全链路（presign→直传 201→finalize→
    1s 内 ready→签名 URL 200 image/jpeg→篡改 401→删除 204+文件清理）全通过
- [x] TASK-014 Timeline（合并时间线）：
  - 后端 GET /api/v1/children/{childId}/timeline（§52）：growth_records + growth_memories 合并，
    单一 item 结构按 type 区分（Jackson non_null 省略空字段，与示例结构一致：
    growth_record 带 heightCm/weightKg/headCircumferenceCm/note/photos；memory 带 title/content/photos）；
    统一 date DESC（同日记录在前）；每项附 ageMonths（§37 AgeCalculator）；照片按
    growth_record_id / memory_id 挂条目（§114）并附短期签名 URL；场景一次全量不分页；
    memory 读取侧用新 entity/mapper（com.growthkids.api.memory），CRUD 于 TASK-015 接入
  - 前端 /children/[id]/timeline（§53/§110/§113）：年-月分组（保持后端排序）、卡片
    （日期/月龄文案/照片行缩略图/三指标/备注；memory 卡标题+内容）；首页第三卡换为时间线入口
  - types 补充 TimelineItem/TimelinePhoto/TimelineResponse
  - 验证通过：TimelineControllerIntegrationTest 5 例（合并排序/同日次序/类型字段结构/
    照片签名 URL 含真实缩略图访问/空/404/401），API 全套 103 例全绿；
    pnpm lint/typecheck/build 全绿（9 路由）；curl 冒烟：两条记录 + memory 合并排序、
    月龄 20/19/14、类型字段省略全部正确

- [x] TASK-015 Memory（成长故事 CRUD）：
  - CRUD（§16/§114）：POST /children/{childId}/memories 201 / GET 列表（date DESC 分页）/
    PATCH /memories/{id}（缺省不变；title 空串清空；content 空串拒绝）/ DELETE 204（物理删除，
    事务内解关联照片：照片保留但 memoryId 置空）
  - 校验：memoryDate 不可未来/早于出生（§29 同语义）；标题 ≤200 可选；内容必填 ≤5000
  - 照片关联打通（§114）：presign 扩展可选 growthRecordId/memoryId，关联目标必须归属同一 child
    （否则 GROWTH_RECORD_NOT_FOUND / MEMORY_NOT_FOUND）；timeline 读取侧已于 TASK-014 就绪
  - 前端：/children/[id]/memories/new（创建故事 + 多选照片 presign 关联 memoryId + 逐张进度）、
    /children/[id]/memories（列表/内联编辑/删除）；timeline 头部加“+ 记录故事”/“管理故事”入口；
    直传逻辑抽取 lib/media.ts 共享（photos 页同步重构引用）
  - 验证通过：MemoryControllerIntegrationTest 8 例（创建/列表排序/PATCH 语义/删除解关联照片保留/
    presign 关联与外孩拒绝/ownership 404/401），API 全套 111 例全绿；pnpm lint/typecheck/build
    全绿（11 路由）；curl 冒烟全链路：创建→未来/早于出生 400→照片关联→timeline 照片 1 张
    月龄 21→PATCH 清标题→非本人 404→删除 204 照片保留→列表归零，全部符合预期
- [x] TASK-016 Membership（套餐/订阅/权限）：
  - 套餐配置化（§64/§78）：GET /api/v1/membership/plans 公开返回 free/pro，
    价格与权益全部来自 membership_plans（V2 种子）features JSON，代码零硬编码；
    PlanPermissions 解析失败回退 app.membership 默认值（§78）
  - 订阅状态：GET /api/v1/membership 返回生效套餐+权益（free 用户 status 省略）；
    PlanResolver：status=active 且未过期→套餐权限，否则回落 Free
  - 支付抽象（§66/架构决策 9）：PaymentProvider 接口（createCheckout/verifyWebhook/
    getPayment/refund 四能力）+ MockPaymentProvider（HMAC-SHA256 签名，时序安全比较，
    @ConditionalOnProperty 按 app.payment.provider 选择）；PayPal/Stripe 待真实凭据接入
  - 订阅闭环：POST /subscribe 创建 pending payment（planCode 存 raw_payload 防篡改）
    → POST /mock-complete/{tx} 构造已签名事件走真实 webhook 路径（幂等）；
    webhook POST /api/v1/payments/webhook/mock（§67：验签→验证交易→防重→事务→
    payment→membership→webhook_logs，失败分支日志随事务提交不因异常回滚）
  - 权限接入：孩子限额（ChildService TODO 兑现，Free 1/Pro 5）、照片限额
    （presign 按账户全部孩子计数，Free 100→MEMBERSHIP_REQUIRED）、时间线条数
    （Free 最近 20 条，两源各取前 N 保证全局前 N 正确）
  - 取消：POST /cancel 置 cancelled，权益立即回落 Free，可重新订阅
  - 前端：/membership 套餐对比页（权益清单/当前状态/升级 mock 自动闭环/取消）、
    首页入口卡、错误映射 MEMBERSHIP_REQUIRED；types 补 PlanPermissions/
    MembershipPlan/MembershipInfo/SubscriptionCheckout（统一 MembershipStatus='cancelled'）
  - 验证通过：MembershipControllerIntegrationTest 9 例（套餐公开/free 默认/订阅校验/
    限额全生命周期/验签拒绝+日志/事件重放幂等/mock 幂等+他人 404/时间线与照片限额），
    API 全套 120 例全绿；pnpm lint/typecheck/build 全绿（12 路由）；
    curl 冒烟：plans 公开→free 默认→孩子 403→订阅 pending→mock-complete 激活
    （到期 +1 月）→pro 限额生效→webhook 拒签 400→取消回落→重订阅，全部符合预期
- [x] TASK-017 Donation（捐赠支付/webhook/历史）：
  - 统一 webhook 入口重构：七步骨架（验签→transactionId→防重→查单→分发→
    审计日志）从 MembershipService 抽到 PaymentWebhookService，按 payments.payment_type
    经 WebhookEventHandler 接口分发（membership/donation 各自实现；
    ObjectProvider 延迟解析打破构造循环）；payment.failed 统一置单 failed
  - 防御加固：webhook_logs.payload 为 JSON 列，非 JSON 原文包裹为 {"raw":...}
    落审计（垃圾回调 400 而非 500，集成测试覆盖）
  - 捐赠闭环（§65 独立于会员）：POST /api/v1/donations/checkout 金额自选
    （$0.50–$10,000、≤2 位小数、仅 USD，Bean Validation+service 兑底）→
    pending payment（type=donation）→ POST /mock-complete/{tx} 构造已签名事件
    走真实 webhook 路径（幂等）；GET /api/v1/donations 历史分页（仅本人、全状态）
  - PaymentProvider 扩展 createDonationCheckout（§66 抽象不变，mock 返回
    /donate/checkout/mock/ URL）；新错误码 DONATION_INVALID_AMOUNT/DONATION_DISABLED
    （app.features.enable-donation 开关）
  - 前端：/donate 捐赠页（$3/$5/$10/$20 预设+自定义金额/mock 自动闭环/捐赠历史
    状态徽标）、首页入口卡、types 补 Donation/DonationCheckout、
    validation 补 donationCheckoutSchema
  - 验证通过：DonationControllerIntegrationTest 5 例（金额与币种校验/mock 幂等+
    他人 404/webhook 同端点分发含垃圾回调 400 与 failed 事件/历史隔离分页/未登录 401），
    API 全套 125 例全绿；pnpm lint/typecheck/build 全绿（13 路由）；
    curl 冒烟 28 步全部符合预期
- [x] TASK-018 Annual Report（年度报告/PDF）：
  - PDF 方案：§99 的 Puppeteer 为 NestJS 生态选择，Java 侧等价替换为
    openhtmltopdf-pdfbox 1.0.10；内嵌 Noto Sans SC TrueType variable TTF
    （17.7MB，官方 noto-cjk OTF 为 CFF 轮廓不被 PDFBox 支持，首次踩坑后改用
    google/fonts TrueType 版）；字体 classpath 解包临时文件 + volatile 缓存
  - 报告创建（§98 十二项）：POST /api/v1/children/{childId}/reports（Pro 权益
    §64 annualReport → MEMBERSHIP_REQUIRED；year 1900–2100；当年无记录 →
    REPORT_NO_DATA；uq_child_report 同年幂等，重复创建=重生成重置 pending）
  - 异步生成（§99 report-generation）：@Async Runner 状态机 pending→generating→
    ready/failed（失败不抛出、落库可重试）；TransactionSynchronizationManager
    afterCommit 提交后触发，避免 Runner 新连接读不到未提交行；PDF 写入
    LocalStorageService（reports/{childId}/annual-{year}-{id}.pdf）
  - 数据组装 §98 十二项：基本信息/年末年龄/年初年末快照（身高体重头围百分位）/
    记录表/照片（缩略图 base64 嵌入，读取失败不阻断）/成长故事（近 5 条）/
    身高预测（HeightPredictionEngine）/年度总结（规则模板）；XHTML 纯拼接模板
    （严格闭合标签，openhtmltopdf 要求）
  - 下载：GET /api/v1/reports/{id}/download（仅 ready → REPORT_NOT_READY 409；
    非本人/不存在 → REPORT_NOT_FOUND 404）；流式 InputStreamResource +
    Content-Disposition attachment；新错误码 REPORT_NOT_FOUND/REPORT_NO_DATA/
    REPORT_NOT_READY
  - 前端：/children/[id]/reports（年份选择 1900–2100 常用 5 年/创建→轮询
    pending/generating→ready/携 token blob 下载）、首页入口卡、types 补
    GrowthReport/ReportStatus、validation 补 createReportSchema
  - 验证通过：ReportControllerIntegrationTest 5 例（Free 403/年份与数据校验+
    他人孩子 404/全生命周期生成+下载 %PDF magic bytes/同年重生成幂等单行/
    未就绪 409+越权 404），API 全套 130 例全绿；pnpm lint/typecheck/build
    全绿（14 路由）；curl 冒烟 33 步全部符合预期
- [x] TASK-019 Admin（后台统计/管理）：
  - 权限基础：users.role 列（V1 已有）启用；SecurityConfig 预留
    `/api/v1/admin/** hasRole('ADMIN')` + Controller 类级 @PreAuthorize 双保险；
    JWT role claim 登录时签发（改角色自下次登录生效）；UserSummary/UserProfile
    补 role（前端入口判断）
  - 审计（§71）：audit_logs 表启用，common/audit 包（AuditLog entity +
    mapper/AuditLogMapper + AuditService）；记录 requestId/IP（X-Forwarded-For
    优先）/details JSON；写入失败 log.warn 不阻断业务；mapper 置于
    `common/audit/mapper` 以匹配 @MapperScan("**.mapper")
  - 首个管理员引导：AdminBootstrap（ApplicationRunner）按 app.admin.email/
    password 幂等创建（已存在不动；空配置禁用，测试 profile 禁用）
  - 统计总览（§77）：GET /api/v1/admin/dashboard —— 用户总数/活跃、孩子
    总数/活跃/软删、成长记录/照片/故事/活跃会员、支付（会员与捐赠成功笔数+
    收入、pending/failed）、报告（总数/就绪）、WHO 数据集版本（source/
    version/行数/导入时间，合成数据标注 §124）
  - 管理列表：GET /users（含活跃孩子数/当前套餐）、GET /children（admin 视角
    含软删除，原生 SQL 绕过 @TableLogic）、GET /payments（paymentType/status
    白名单过滤）、GET /logs（kind=webhook 支付回调 / audit 管理操作，统一行
    结构）；PATCH /users/{userId}/role（user↔admin，写审计，幂等重放不重复
    写审计）；新错误码 USER_NOT_FOUND
  - 前端：/admin 页（总览统计卡片 + 用户/孩子/支付/日志 5 tab、支付类型与
    状态过滤、日志 webhook/audit 切换、分页、角色切换按钮）+ 首页
    后台管理入口（仅 role=admin 显示）+ types 补 Admin* 五类
  - 验证通过：AdminControllerIntegrationTest 7 例（匿名 401/非 admin 403/
    统计全维度/用户列表套餐与孩子数/角色变更审计+旧 token 403+重登 200/
    孩子列表含软删/支付过滤/日志双 kind），API 全套 137 例全绿；
    pnpm lint/typecheck/build 全绿（15 路由）；curl 冒烟 28 步全部符合预期
    （注意：本机 8082 被并行项目 fincode 占用，冒烟临时用 --server.port=8092）
- [x] TASK-020 Production（Docker/Nginx/HTTPS/CI/备份/监控）：
  - 监控与日志（§82/§83）：pom 引入 spring-boot-starter-actuator，
    SecurityConfig 放行 `/actuator/health`、`/actuator/info`（对外由 Nginx 返回 404，
    容器健康检查直连 8082）；`application-prod.yml` 开启
    `logging.structured.format.console=ecs`（每行 JSON）、生产 SQL 日志 WARN、
    graceful shutdown、`forward-headers-strategy=framework`、liveness/readiness probes
  - 生产安全（§120）：`application-prod.yml` 中 JWT/存储/支付密钥无默认值
    （缺失即启动失败）；首个管理员默认空（不自动创建默认账号）
  - 部署缺口补齐：`scripts/preflight-prod.sh`（密钥强度/互不复用、三地址同域且 https、
    证书未过期且私钥匹配、WHO 非合成、compose 可解析、`--post` 运行态复核）、
    `scripts/clean-demo-data.sh`（清理 V4 演示商品）；compose 移除空转的
    `ENABLE_DEMO_SEED`（无代码读取），改为 `APP_WHO_DATA_FILE` +
    `./data/who-official` 只读挂载以支持官方 WHO CSV
  - Docker（§80）：`apps/api/Dockerfile`（maven:3.9-eclipse-temurin-21 构建 →
    eclipse-temurin:21-jre-jammy 运行，非 root app 用户，curl healthcheck，
    JAVA_OPTS）；`apps/web/Dockerfile`（node:22-alpine 三阶段，复制 Next standalone
    产物，非 root，wget healthcheck）；`next.config.ts` 开启 `output: "standalone"` +
    `outputFileTracingRoot`（monorepo）
  - 编排：`docker-compose.prod.yml` 六服务（mysql / redis / api / web / nginx /
    backup），仅 Nginx 暴露 HTTP_PORT/HTTPS_PORT；命名卷 mysql-data / redis-data /
    storage-data；api 依赖 mysql/redis healthy、nginx 依赖 api/web healthy；
    backup 服务启动即备份、此后每 24h
  - Nginx/HTTPS：`docker/nginx/nginx.prod.conf`（80 端口 ACME 校验 + 301 跳转；
    443 启用 TLS1.2/1.3 + HSTS/nosniff/DENY/strict-origin；`/actuator/` 对外 404；
    `/api/`、`/media/` → api:8082（X-Forwarded-*）；`/` → web:3000）；
    证书挂载 `docker/nginx/ssl/`，Let's Encrypt webroot 与自签验证流程见 docs/PRODUCTION.md
  - CI/CD：`.github/workflows/ci.yml` 三 job —— backend（MySQL 8 + Redis 7 service，
    `mvn -B -pl apps/api -am test`）、frontend（pnpm lint/typecheck/build）、
    docker（依赖前两者，构建 api/web 镜像验证 Dockerfile）
  - 备份/恢复（§117/§118）：`scripts/backup-mysql.sh`（--single-transaction +
    --routines --triggers --no-tablespaces + gzip -9 + gzip -t/大小校验 + 30 天保留）、
    `scripts/restore-mysql.sh`（支持恢复/演练到其他库，目标库缺失时明确提示）；
    `docs/PRODUCTION.md` 覆盖架构/首次部署/证书三种方式/日志监控/备份恢复/CI/CD/
    安全清单/故障排查
  - 验证通过：`mvn test` 137 例全绿；`pnpm lint/typecheck/build` 全绿（16 路由，
    `.next/standalone/apps/web/server.js` 产出）；`docker compose config` 校验通过
    （6 服务）；`nginx -t` 通过（自签证书 + add-host 模拟上游）；prod profile 实启动
    `/actuator/health` = 200 UP（ECS JSON 日志、liveness/readiness）；
    api/web 镜像均构建成功并实际启动验证（api healthy + 健康检查 200，
    web 首页/登录页 200，两容器均以非 root app 用户运行）；
    备份→恢复到临时库演练 14 表 / users 18 / children 11 与源库完全一致
  - 仓库安全：`.gitignore` 忽略 `.env.production`、`docker/nginx/ssl/*`（保留 .gitkeep）、
    `backups/`；`.dockerignore` 排除 node_modules/.next/target/.git/密钥/备份
- [x] TASK-021 身高预测闭环（补齐规范 §44/§75/§76 未拆入原 TASK 清单的公开预测）：
  - 背景：TASK-010 只实现引擎，`/api/v1/prediction/estimate` 虽在 SecurityConfig 白名单、
    `PREDICTION_INSUFFICIENT_DATA` 错误码也已预留，但无 Controller/页面，首页“免费预测身高”
    按钮直接跳注册页 —— SEO 漏斗首环（§4/§76）缺失
  - 后端：新增 `prediction` 包 —— `POST /api/v1/prediction/estimate`（公开，无需登录，§75）
    与 `GET /api/v1/children/{childId}/prediction`（登录后按孩子档案，§44）；
    `PredictionService` 复用 HeightPredictionEngine + WhoMetricService（当前年龄重算 z，
    未覆盖时回落记录时点存库值）；孩子无身高记录 / 性别 unknown →
    PREDICTION_INSUFFICIENT_DATA；DTO `PredictionRequest/Response` 与引擎解耦
  - 前端：`/height-prediction`（公开 SEO 页，server metadata + 客户端表单，无需登录即可预测，
    未登录引导注册）；`/children/[id]/prediction`（基于真实记录，数据不足引导先记录）；
    首页“免费预测身高”改指 `/height-prediction`，dashboard 新增“身高预测”入口卡
  - 展示：`components/prediction.tsx` 复用结果卡（预计成年身高/区间/百分位/生长速度/中靶身高/
    数据完整度徽标）+ 自绘 SVG 逐年折线（区间带 + 中位线，6-18 岁）+ §45 强制免责声明
  - 共享包：types 补 HeightPrediction/YearlyHeightPrediction；validation 补
    heightPredictionSchema（与后端必填/区间同步）
  - 验证通过：PredictionControllerIntegrationTest 9 例（匿名预测/父母身高缺失降级/
    性别与身高区间校验/未来出生日期/孩子序列预测 medium/无记录与 unknown 性别 400/
    越权 404/未登录 401），API 全套 146 例全绿；`pnpm lint/typecheck/build` 全绿（18 路由）；
    真实进程 curl 冒烟：匿名 estimate 返回 171.2cm（区间 160.7–181.7、13 个年度点）、
    孩子预测返回 180.3cm（区间 173.3–187.3、medium、速度 10.01cm/年）
- [x] TASK-022 优惠商品（deals）模块（儿童好物优惠目录，规范 §139 变现方向）：
  - 定位：只做「优惠信息整理 + 站外跳转」，不做站内下单/库存/支付，不接入 payment 模块；
    购买与售后由商家承担，详情页明示该边界
  - 数据：Flyway V3 建 `deals`（标题/品牌/分类/适用月龄区间/原价/优惠价/币种/商家/图片/
    购买链接/描述/status/featured/投放窗口 starts_at-ends_at，含索引与 CHECK 约束）；
    V4 种子 7 件示例商品（purchase_url 为 example.com 占位，上线前 DELETE 清理）
  - 后端：新增 `deal` 包 —— 公开 `GET /api/v1/deals`（分页 + category/ageMonths/q 过滤，
    只返回 active 且在投放窗口内的商品）、`GET /api/v1/deals/{id}`（不可见统一 404
    DEAL_NOT_FOUND，§70 隐藏存在性）、`GET /api/v1/deals/categories`（分类 code/label
    单一事实源）；后台 `/api/v1/admin/deals` GET/POST/PATCH/DELETE（ADMIN 双保险，
    PATCH 为缺省不变语义）；DealCategory 枚举统一 code/label 与校验正则，
    DealResponse 服务端计算 discountPercent
  - 前端：`/deals` 公开列表（分类 chips + 年龄下拉 + 分页，未登录可看）、`/deals/[id]` 详情
    （价格/折扣/适用年龄/描述 + 站外购买按钮 + 「价格库存以商家为准」声明）；
    首页访客 CTA「育儿好物优惠」与 dashboard「好物优惠」入口卡；`/admin` 新增
    「优惠商品」tab（状态过滤 + 新建/编辑表单 + 上架下架 + 两段式删除）
  - 共享包：types 补 Deal/DealCategoryOption；validation 补 dealFormSchema
    （分类枚举、价格 2 位小数、原价≥优惠价、月龄区间、http(s) 链接）
  - 验证通过：DealControllerIntegrationTest 8 例（公开可见性窗口/分类+月龄+关键词过滤/
    非法分类与月龄 400/不可见详情 404/分类选项/ADMIN 401+403/CRUD 与下架联动/非法载荷）；
    API 全套 154 例全绿；`pnpm lint/typecheck/build` 全绿（19 路由，/deals 静态预渲染）；
    真实进程冒烟：列表 7 件、ageMonths=24 → 5 件、category=books → 1 件、匿名 admin 401；
    `scripts/deal-smoke.sh` 17 项全 PASS（公开读取/权限门禁/CRUD/上下架/非法载荷）

## In Progress

- （无）

## Blocked

- （无）

## Tests

- [x] lint（TASK-001）
- [x] typecheck（TASK-001）
- [x] unit（TASK-004：PasswordHasherTest、JwtServiceTest；TASK-007：AgeCalculatorTest 6 例、BmiCalculatorTest 5 例；TASK-008：WhoCsvParserTest 10 例、WhoDataValidatorTest 8 例；TASK-009：LmsCalculatorTest 6 例；TASK-010：MidParentalHeightCalculatorTest 5 例、GrowthVelocityCalculatorTest 8 例、HeightPredictionEngineTest 7 例；TASK-013：StorageSignerTest 6 例）
- [x] integration（TASK-004 Auth 12 例；TASK-005 User 7 例；TASK-006 Child 8 例；TASK-007/009 Growth Record 15 例；TASK-008 WHO Importer 4 例；TASK-011 Dashboard 4 例；TASK-012 Growth Chart 4 例；TASK-013 Photo 10 例；TASK-014 Timeline 5 例；TASK-015 Memory 8 例；TASK-016 Membership 9 例；TASK-017 Donation 5 例；TASK-018 Annual Report 5 例；TASK-019 Admin 7 例；TASK-022 Deal 8 例）；测试清理统一走 IntegrationTestCleaner（按 FK 顺序 + webhook_logs + audit_logs + deals）；API 全套 154 例全绿
- [x] build（TASK-001：pnpm build / mvn install；TASK-020：Next standalone 产物、
  api/web Docker 镜像、docker compose config、nginx -t）

## Teardown 提示

- 本机其他项目共用宿主机，GrowthKids 专属端口：MySQL 3310 / Redis 6385 / Nginx 8090 / API 8082 / Web 3005。
- 基础设施启动：`docker compose up -d`（已拉起并验证：MySQL 8.0.46 utf8mb4_unicode_ci UTC、Redis PONG、Nginx 网关 502 透传正常）。

## Database Migration

- [x] V1 核心表 / V2 套餐种子（TASK-003）；V3 deals 表 / V4 优惠商品种子（TASK-022）

## API

- [x] /api/v1/auth/*（TASK-004）
- [x] /api/v1/users/*（TASK-005）
- [x] /api/v1/children/*（TASK-006）
- [x] /api/v1/children/:childId/growth-records、/api/v1/growth-records/*（TASK-007；WHO z-score/percentile 于 TASK-009 接入）
- [x] /api/v1/children/:childId/dashboard（TASK-011 聚合端点）
- [x] /api/v1/children/:childId/growth-chart（TASK-012）
- [x] /api/v1/children/:childId/photos（presign/list）、/api/v1/photos/*（finalize/delete）、
  /api/v1/media/*（签名直传/访问）（TASK-013）
- [x] /api/v1/children/:childId/timeline（TASK-014）
- [x] /api/v1/children/:childId/memories（create/list）、/api/v1/memories/:id（PATCH/DELETE）；
  presign 扩展可选 growthRecordId/memoryId 照片关联（TASK-015）
- [x] /api/v1/membership/plans（公开）/membership/subscribe/mock-complete/cancel、
  /api/v1/payments/webhook/mock（HMAC 验签+幂等+审计日志，按 payment_type 分发
  membership/donation）（TASK-016/017）
- [x] /api/v1/donations/checkout、/mock-complete/{tx}、/donations 历史分页（TASK-017）
- [x] /api/v1/children/{childId}/reports（create/list）、/api/v1/reports/{id}/download
  （Pro 权益 + 异步生成 + PDF 流式下载）（TASK-018）
- [x] /api/v1/admin/dashboard、/users（+PATCH /users/{id}/role）、/children、
  /payments（type/status 过滤）、/logs（webhook/audit）（TASK-019，仅 ADMIN）
- [x] /actuator/health（含 liveness/readiness）、/actuator/info（TASK-020，
  仅容器内网；对外由 Nginx 404）
- [x] /api/v1/prediction/estimate（公开，无需登录）、
  /api/v1/children/:childId/prediction（TASK-021，§44/§75）
- [x] /api/v1/deals（公开列表，category/ageMonths/q 过滤）、/api/v1/deals/categories、
  /api/v1/deals/{id}（TASK-022）；后台 /api/v1/admin/deals（GET/POST/PATCH/DELETE，仅 ADMIN）

## Frontend

- [x] apps/web 脚手架（TASK-001）
- [x] 页面：/login、/register、首页（五态）、/children/new、/children/[id]/growth/new + lib/api + lib/session + 表单基件（TASK-011）
- [x] 页面：/children/[id]/charts 四类曲线（自绘 SVG + tooltip + 范围切换）（TASK-012）
- [x] 页面：/children/[id]/photos 照片上传/网格/删除（直传 + 进度 + 轮询）（TASK-013）
- [x] 页面：/children/[id]/timeline 合并时间线（年月分组 + 卡片）（TASK-014）
- [x] 页面：/children/[id]/memories/new 创建故事 + 照片上传、/children/[id]/memories 列表/内联编辑/删除；
  timeline 头部入口；直传共享 lib/media.ts（TASK-015）
- [x] 页面：/membership 套餐对比（权益清单/当前状态/升级 mock 自动闭环/取消）+ 首页入口卡（TASK-016）
- [x] 页面：/donate 捐赠（预设/自定义金额 + mock 自动闭环 + 捐赠历史）+ 首页入口卡（TASK-017）
- [x] 页面：/children/[id]/reports 年度报告（年份选择/创建→轮询→ready/携 token blob
  下载）+ 首页入口卡（TASK-018）
- [x] 页面：/admin 后台管理（总览统计 + 用户/孩子/支付/日志 tab + 角色切换）+
  首页入口（仅 admin 可见）（TASK-019）
- [x] 生产构建：Next.js standalone 输出（`next.config.ts`），由 `apps/web/Dockerfile`
  以 node runner 运行（TASK-020）
- [x] 页面：/height-prediction 公开免费预测（表单 + 结果 + 逐年折线 + §45 免责声明，
  无需登录）、/children/[id]/prediction 孩子档案预测 + 首页 CTA 与 dashboard 入口卡（TASK-021）
- [x] 页面：/deals 公开优惠列表（分类/年龄筛选 + 分页）、/deals/[id] 详情（站外购买）；
  首页访客 CTA 与 dashboard 入口卡；/admin 新增「优惠商品」tab（CRUD + 上下架）（TASK-022）

## Known Issues

- 本机已有多个其他项目服务（fincode / river 等），本项目全部端口错开，互不影响。
- 2026-10-29 发现 fincode（/root/workspace/fincode/apps/api）占用 8082 且长期运行，
  GrowthKids API 无法绑定该端口；TASK-019 冒烟临时以
  `-Dspring-boot.run.arguments=--server.port=8092` 启动（脚本 `API=` 可覆盖）。
  恢复 8082 需先停止 fincode 进程或为其改用其他端口。
  TASK-020 起生产环境由容器网络隔离（仅 Nginx 暴露 80/443，API 容器内网 8082），
  不再受宿主机端口占用影响。
- HEIC 图片纯 Java 无法解码缩略图（无成熟纯 Java 解码器），TASK-013 处理策略：接受上传并保留原图，缩略图生成失败时标记 `processing_status=ready` + 原图直出，作为已知限制记录。
- WHO 官方数据（0-60 月 LMS 表，xlsx 格式）在开发环境无法完整获取（网络受限/WebFetch 大小限制），TASK-008 内置**合成示例数据**（source=growthkids-example-synthetic）跑通全链路。上线前必须按 data/who/README.md 替换为 WHO 官方数据文件（source=bump 为官方版本号），否则 z-score/percentile 不具备医学参考意义。
- 优惠商品 V4 种子为演示数据（无图片、purchase_url 指向 example.com），上线前需在后台替换为真实商品（含联盟追踪参数）并清理示例行：`bash scripts/clean-demo-data.sh --compose --apply`（dry-run 可先预览）；商品图片直出外部域名，未接 next/image 逐域白名单。

## Next Task

（无）—— TASK-001 ~ TASK-022 全部完成。

上线前遗留（均已在 Known Issues / docs/PRODUCTION.md 标注）：

1. 用 WHO 官方 LMS 数据替换合成示例数据（TASK-008 限制，影响医学参考价值）。
2. 接入真实支付网关（PayPal/Stripe）替换 mock（架构决策 9）。
3. 生产部署前置：填写 `.env.production` 密钥、放置 HTTPS 证书、把官方 WHO CSV 放到
   `data/who-official/`，先跑 `bash scripts/preflight-prod.sh --env-file .env.production`
   （0 FAIL 再上线），再按 `docs/PRODUCTION.md` 执行
   `docker compose -f docker-compose.prod.yml up -d --build`；启动后跑 `--post` 复核，
   并用 `scripts/clean-demo-data.sh --apply` 清理 V4 演示商品。
4. 每月执行一次备份恢复演练（§118）。
