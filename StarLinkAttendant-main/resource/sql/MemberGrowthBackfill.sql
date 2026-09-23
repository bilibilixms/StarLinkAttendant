-- ============================================================
-- 会员成长值历史回填：按累计充值（实付）回填成长值 + 同步校准等级
-- 规则：
--  1. growth_value = FLOOR(total_recharge)（累计充值实付，1元=1经验向下取整）
--  2. level_id 按新成长值重新判定：只升不降（目标等级高于当前等级才升级）
--  3. 与 GrowthValueService.addGrowth 的自动升级判定保持一致（selectByGrowth 语义）
-- 仅影响 deleted_at IS NULL（未删除）的会员。
-- ============================================================

UPDATE `member` m
SET m.`growth_value` = FLOOR(m.`total_recharge`),
    m.`level_id` = IF(
        (SELECT t2.`level_order`
           FROM `member_level` t2
          WHERE t2.`min_growth` <= FLOOR(m.`total_recharge`)
            AND t2.`max_growth`  > FLOOR(m.`total_recharge`)
            AND t2.`deleted_at` IS NULL
          ORDER BY t2.`level_order` DESC
          LIMIT 1) > COALESCE(
            (SELECT c.`level_order`
               FROM `member_level` c
              WHERE c.`id` = m.`level_id`
                AND c.`deleted_at` IS NULL), 0),
        (SELECT t3.`id`
           FROM `member_level` t3
          WHERE t3.`min_growth` <= FLOOR(m.`total_recharge`)
            AND t3.`max_growth`  > FLOOR(m.`total_recharge`)
            AND t3.`deleted_at` IS NULL
          ORDER BY t3.`level_order` DESC
          LIMIT 1),
        m.`level_id`)
WHERE m.`deleted_at` IS NULL;membermember