-- 会员余额变动流水表
-- 作用：记录每次会员余额扣减/增加，保证账实一致与可审计
-- biz_type：1-消费 2-退款回充 3-充值 4-手动调整
CREATE TABLE IF NOT EXISTS member_balance_log (
  id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  member_id       BIGINT        NOT NULL COMMENT '会员ID',
  amount          DECIMAL(10,2) NOT NULL COMMENT '变动金额(正数增加,负数扣减)',
  balance_before  DECIMAL(10,2) NOT NULL COMMENT '变动前余额',
  balance_after   DECIMAL(10,2) NOT NULL COMMENT '变动后余额',
  biz_type        TINYINT       NOT NULL COMMENT '业务类型 1-消费 2-退款 3-充值 4-手动调整',
  biz_id          BIGINT        NULL COMMENT '业务单据ID',
  remark          VARCHAR(255)  NULL COMMENT '备注',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted_at      DATETIME      NULL COMMENT '删除时间(软删)',
  version         INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (id),
  KEY idx_member_id (member_id),
  KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员余额变动流水';
