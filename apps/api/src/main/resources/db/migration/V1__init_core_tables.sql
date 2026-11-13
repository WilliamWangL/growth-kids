-- =====================================================================
-- GrowthKids V1 核心表结构
-- 依据 AI_DEVELOPMENT_SPEC §11-§20 定义；
-- 附加字段均标注规范出处（§64/§71/§114/§124）。
-- 字符集 utf8mb4 / utf8mb4_unicode_ci；时间统一 UTC。
-- =====================================================================

-- ---------------------------------------------------------------------
-- users（§12）
-- role：TASK-019 Admin 需要（§77）
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100),
    avatar_url    VARCHAR(500),
    timezone      VARCHAR(64) NOT NULL DEFAULT 'UTC',
    locale        VARCHAR(20) NOT NULL DEFAULT 'en-US',
    status        VARCHAR(20) NOT NULL DEFAULT 'active',
    role          VARCHAR(20) NOT NULL DEFAULT 'user',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- children（§13）
-- ---------------------------------------------------------------------
CREATE TABLE children (
    id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED NOT NULL,
    name              VARCHAR(100) NOT NULL,
    gender            VARCHAR(20) NOT NULL,
    birth_date        DATE NOT NULL,
    birth_height_cm   DECIMAL(5, 2),
    birth_weight_kg   DECIMAL(5, 2),
    father_height_cm  DECIMAL(5, 2),
    mother_height_cm  DECIMAL(5, 2),
    avatar_url        VARCHAR(500),
    notes             TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    INDEX idx_children_user_id (user_id),
    CONSTRAINT fk_children_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- growth_records（§14）
-- who_version：§124 要求保留 WHO 数据版本以便重算追溯
-- ---------------------------------------------------------------------
CREATE TABLE growth_records (
    id                    BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    child_id              BIGINT UNSIGNED NOT NULL,
    record_date           DATE NOT NULL,

    age_days              INT NOT NULL,

    height_cm             DECIMAL(6, 2),
    weight_kg             DECIMAL(6, 2),
    head_circumference_cm DECIMAL(6, 2),

    bmi                   DECIMAL(6, 3),

    height_z              DECIMAL(8, 4),
    height_percentile     DECIMAL(7, 4),

    weight_z              DECIMAL(8, 4),
    weight_percentile     DECIMAL(7, 4),

    bmi_z                 DECIMAL(8, 4),
    bmi_percentile        DECIMAL(7, 4),

    head_z                DECIMAL(8, 4),
    head_percentile       DECIMAL(7, 4),

    who_version           VARCHAR(50),

    note                  TEXT,

    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    UNIQUE KEY uq_growth_child_date (child_id, record_date),
    INDEX idx_growth_child_date (child_id, record_date),

    CONSTRAINT fk_growth_child FOREIGN KEY (child_id) REFERENCES children (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- growth_photos（§15）
-- memory_id：§114 成长故事支持附带照片
-- 说明：storage_key 为本地存储相对路径（v1），不对外暴露。
-- ---------------------------------------------------------------------
CREATE TABLE growth_photos (
    id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    child_id          BIGINT UNSIGNED NOT NULL,
    growth_record_id  BIGINT UNSIGNED,
    memory_id         BIGINT UNSIGNED,

    storage_key       VARCHAR(1000) NOT NULL,
    thumbnail_key     VARCHAR(1000),

    original_filename VARCHAR(255),
    mime_type         VARCHAR(100),
    width             INT,
    height            INT,
    file_size         BIGINT,

    caption           VARCHAR(500),
    sort_order        INT NOT NULL DEFAULT 0,

    processing_status VARCHAR(30) NOT NULL DEFAULT 'pending',

    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    INDEX idx_photo_child (child_id),
    INDEX idx_photo_growth_record (growth_record_id),
    INDEX idx_photo_memory (memory_id),

    CONSTRAINT fk_photo_child FOREIGN KEY (child_id) REFERENCES children (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- growth_memories（§16）
-- ---------------------------------------------------------------------
CREATE TABLE growth_memories (
    id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    child_id    BIGINT UNSIGNED NOT NULL,

    memory_date DATE NOT NULL,
    title       VARCHAR(200),
    content     TEXT NOT NULL,

    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    INDEX idx_memory_child_date (child_id, memory_date),

    CONSTRAINT fk_memory_child FOREIGN KEY (child_id) REFERENCES children (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- who_growth_standards（§17）
-- 原始 WHO 数据不可被业务代码修改（只读）。
-- ---------------------------------------------------------------------
CREATE TABLE who_growth_standards (
    id        BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,

    source    VARCHAR(50)  NOT NULL,
    version   VARCHAR(50)  NOT NULL,

    sex       VARCHAR(20)  NOT NULL,
    indicator VARCHAR(50)  NOT NULL,

    age_days  INT          NOT NULL,

    l_value   DECIMAL(20, 10) NOT NULL,
    m_value   DECIMAL(20, 10) NOT NULL,
    s_value   DECIMAL(20, 10) NOT NULL,

    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE KEY uq_who (source, version, sex, indicator, age_days)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- memberships（§18）
-- ---------------------------------------------------------------------
CREATE TABLE memberships (
    id                       BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id                  BIGINT UNSIGNED NOT NULL,

    plan                     VARCHAR(30) NOT NULL,
    status                   VARCHAR(30) NOT NULL,

    provider                 VARCHAR(30),
    provider_subscription_id VARCHAR(255),

    started_at               DATETIME(3),
    expires_at               DATETIME(3),

    created_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    INDEX idx_membership_user (user_id),

    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- membership_plans（§64：价格必须配置化，不允许硬编码）
-- ---------------------------------------------------------------------
CREATE TABLE membership_plans (
    id             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,

    code           VARCHAR(30)  NOT NULL UNIQUE,
    name           VARCHAR(100) NOT NULL,

    price_amount   DECIMAL(18, 2) NOT NULL DEFAULT 0,
    currency       VARCHAR(10)  NOT NULL DEFAULT 'USD',
    billing_period VARCHAR(20)  NOT NULL DEFAULT 'none',

    features       JSON,
    is_active      TINYINT(1) NOT NULL DEFAULT 1,
    sort_order     INT NOT NULL DEFAULT 0,

    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- payments（§19）
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,

    user_id        BIGINT UNSIGNED NOT NULL,

    provider       VARCHAR(30) NOT NULL,
    transaction_id VARCHAR(255),

    amount         DECIMAL(18, 2) NOT NULL,
    currency       VARCHAR(10) NOT NULL,

    payment_type   VARCHAR(30) NOT NULL,
    status         VARCHAR(30) NOT NULL,

    raw_payload    JSON,

    paid_at        DATETIME(3),

    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    UNIQUE KEY uq_provider_transaction (provider, transaction_id),
    INDEX idx_payment_user (user_id),

    CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- growth_reports（§20）
-- ---------------------------------------------------------------------
CREATE TABLE growth_reports (
    id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,

    child_id    BIGINT UNSIGNED NOT NULL,
    user_id     BIGINT UNSIGNED NOT NULL,

    report_type VARCHAR(30) NOT NULL,
    year        INT NOT NULL,

    status      VARCHAR(30) NOT NULL DEFAULT 'pending',

    storage_key VARCHAR(1000),

    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    UNIQUE KEY uq_child_report (child_id, report_type, year),

    CONSTRAINT fk_report_child FOREIGN KEY (child_id) REFERENCES children (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- refresh_tokens（§71 refresh token rotation）
-- 仅存 token 的 SHA-256 摘要，不落明文。
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id                     BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id                BIGINT UNSIGNED NOT NULL,

    token_hash             CHAR(64) NOT NULL,
    expires_at             DATETIME(3) NOT NULL,
    revoked_at             DATETIME(3),
    replaced_by_token_hash CHAR(64),

    created_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE KEY uq_refresh_token_hash (token_hash),
    INDEX idx_refresh_user (user_id),

    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- webhook_logs（§67：支付 webhook 必须记录日志、可审计、幂等）
-- ---------------------------------------------------------------------
CREATE TABLE webhook_logs (
    id             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,

    provider       VARCHAR(30) NOT NULL,
    event_type     VARCHAR(100),
    transaction_id VARCHAR(255),

    signature_valid TINYINT(1) NOT NULL DEFAULT 0,
    processed       TINYINT(1) NOT NULL DEFAULT 0,

    payload        JSON,
    error          TEXT,

    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX idx_webhook_tx (provider, transaction_id),
    INDEX idx_webhook_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- audit_logs（§71 审计日志）
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,

    user_id     BIGINT UNSIGNED,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   BIGINT UNSIGNED,

    request_id  VARCHAR(64),
    ip          VARCHAR(64),
    details     JSON,

    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX idx_audit_user (user_id),
    INDEX idx_audit_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
