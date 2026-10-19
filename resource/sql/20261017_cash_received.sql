-- Existing databases only: run once before deploying the cashier accounting change.
ALTER TABLE payment_record
    ADD COLUMN cash_received DECIMAL(12,2) DEFAULT NULL
        COMMENT '现金实收金额（用于找零，余额支付为空）' AFTER total_amount;
