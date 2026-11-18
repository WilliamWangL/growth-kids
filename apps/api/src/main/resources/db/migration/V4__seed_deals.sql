-- =====================================================================
-- V4 种子数据：优惠商品示例（TASK-022）
--
-- 用途：让 /deals 与后台管理在全新环境下有可操作数据（真实商品由 admin 维护）。
-- purchase_url 使用 example.com 占位，接入真实商家（含联盟参数）后由后台覆盖；
-- 生产上线前可直接 DELETE FROM deals 清理。
-- =====================================================================
INSERT INTO deals (title, brand, category, min_age_months, max_age_months,
                   original_price, deal_price, currency, merchant,
                   purchase_url, description, status, featured)
VALUES ('轻便可折叠婴儿推车', 'BabyJourney', 'gear', 0, 36,
        299.00, 219.00, 'USD', 'Amazon',
        'https://example.com/deals/stroller',
        '一键收车，自重 6.2kg，可登机；适合 0-3 岁日常通勤与出行。', 'active', 1),
       ('360° 旋转儿童安全座椅', 'SafeRide', 'gear', 9, 72,
        349.00, 279.00, 'USD', 'Amazon',
        'https://example.com/deals/car-seat',
        'ISOFIX 硬接口 + 支撑腿，正反向安装，侧面防撞；覆盖 9 个月至 6 岁。', 'active', 1),
       ('木质拼搭积木 100 片', 'WoodyKids', 'toys', 12, 60,
        59.00, 39.90, 'USD', 'Etsy',
        'https://example.com/deals/wooden-blocks',
        '榉木原木无漆，边角圆润；锻炼手眼协调与空间想象力。', 'active', 0),
       ('亲子共读绘本套装（20 册）', 'LittleOwl', 'books', 12, 84,
        128.00, 89.00, 'USD', 'BookDepot',
        'https://example.com/deals/picture-books',
        '含情绪管理、生活习惯与自然认知主题，附亲子共读指导手册。', 'active', 0),
       ('有机棉连体衣 3 件装', 'CottonCloud', 'clothing', 0, 18,
        45.00, 29.90, 'USD', 'BrandStore',
        'https://example.com/deals/bodysuit',
        'GOTS 认证有机棉，按扣设计，无荧光剂；0-18 个月四个尺码。', 'active', 0),
       ('儿童钙 + 维生素 D3 咀嚼片', 'NutriGrow', 'nutrition', 36, 144,
        32.00, 24.50, 'USD', 'HealthMart',
        'https://example.com/deals/calcium-d3',
        '每片含钙 200mg 与维 D3 400IU，天然莓果味；3 岁以上每日 1 片。', 'active', 0),
       ('无泪配方洗发沐浴二合一', 'SoftBath', 'care', 0, 72,
        22.00, 16.90, 'USD', 'HealthMart',
        'https://example.com/deals/baby-wash',
        '弱酸性无皂基，含燕麦提取物；敏感肌可用，500ml 家庭装。', 'active', 0);
