package com.starlink.cashier.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cashier_shift")
public class CashierShift extends BaseEntity {

    @TableField("employee_id")
    private Long employeeId;

    @TableField("shift_no")
    private String shiftNo;

    @TableField("open_at")
    private LocalDateTime openAt;

    @TableField("close_at")
    private LocalDateTime closeAt;

    @TableField("opening_balance")
    private BigDecimal openingBalance;

    @TableField("cash_income")
    private BigDecimal cashIncome;

    @TableField("cash_expenditure")
    private BigDecimal cashExpenditure;

    @TableField("cash_expected")
    private BigDecimal cashExpected;

    @TableField("cash_actual")
    private BigDecimal cashActual;

    @TableField("cash_diff")
    private BigDecimal cashDiff;

    @TableField("total_income")
    private BigDecimal totalIncome;

    @TableField("order_count")
    private Integer orderCount;

    @TableField("status")
    private Byte status;

    @TableField("audit_by")
    private Long auditBy;
}
