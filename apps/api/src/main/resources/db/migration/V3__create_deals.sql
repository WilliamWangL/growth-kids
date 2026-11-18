-- =====================================================================
-- GrowthKids V3 优惠商品（deals）表
--
-- 业务定位：儿童好物优惠目录（TASK-022）。只做展示与站外跳转，
-- 不含站内下单/库存/支付，因此不建 orders 表，也不接入 payment 模块；
-- 购买行为与售后由商家承担，前端在详情页明示该边界（§139 变现方向）。
--
-- 金额 DECIMAL(10,2) 与 payments 保持一致；时间统一 UTC；
-- 上下架用 status + starts_at/ends_at 双条件控制，避免过期商品被检索到。
-- =====================================================================

CREATE TABLE deals (
    id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    title           VARCHAR(200)   NOT NULL,
    brand           VARCHAR(100),
    category        VARCHAR(32)    NOT NULL,
    min_age_months  INT,
    max_age_months  INT,
    original_price  DECIMAL(10, 2),
    deal_price      DECIMAL(10, 2) NOT NULL,
    currency        CHAR(3)        NOT NULL DEFAULT 'USD',
    merchant        VARCHAR(100),
    image_url       VARCHAR(500),
    purchase_url    VARCHAR(1000)  NOT NULL,
    description     TEXT,
    -- active（上架）/ inactive（下架）；与 users.status 命名保持一致
    status          VARCHAR(20)    NOT NULL DEFAULT 'active',
    -- 首页/列表置顶位（0/1）
    featured        TINYINT(1)     NOT NULL DEFAULT 0,
    starts_at       DATETIME(3),
    ends_at         DATETIME(3),
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_deals_status_window (status, starts_at, ends_at),
    KEY idx_deals_category (category),
    KEY idx_deals_age (min_age_months, max_age_months),
    CONSTRAINT ck_deals_price CHECK (deal_price > 0),
    CONSTRAINT ck_deals_original_price CHECK (original_price IS NULL OR original_price >= 0),
    CONSTRAINT ck_deals_age_range CHECK (
        min_age_months IS NULL OR max_age_months IS NULL OR min_age_months <= max_age_months)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
