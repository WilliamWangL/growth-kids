-- =====================================================================
-- V2 种子数据：会员套餐（§64 / §131）
-- 价格与权益项均为可配置数据，业务代码不得硬编码。
-- =====================================================================
INSERT INTO membership_plans (code, name, price_amount, currency, billing_period, features, is_active, sort_order)
VALUES ('free', 'Free', 0.00, 'USD', 'none',
        JSON_OBJECT(
            'childLimit', 1,
            'unlimitedRecords', false,
            'unlimitedPhotos', false,
            'photoLimit', 100,
            'timelineLimit', 20,
            'advancedPrediction', false,
            'annualReport', false,
            'pdfExport', false,
            'poster', false,
            'dataExport', false,
            'aiSummary', false
        ),
        1, 0),
       ('pro', 'Pro', 4.99, 'USD', 'monthly',
        JSON_OBJECT(
            'childLimit', 5,
            'unlimitedRecords', true,
            'unlimitedPhotos', true,
            'photoLimit', -1,
            'timelineLimit', -1,
            'advancedPrediction', true,
            'annualReport', true,
            'pdfExport', true,
            'poster', true,
            'dataExport', true,
            'aiSummary', true
        ),
        1, 1);
