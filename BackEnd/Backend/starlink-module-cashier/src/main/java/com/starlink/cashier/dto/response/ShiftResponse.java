package com.starlink.cashier.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ShiftResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String shiftNo;
    private LocalDateTime openAt;
    private LocalDateTime closeAt;
    private BigDecimal openingBalance;
    private BigDecimal cashIncome;
    private BigDecimal cashExpenditure;
    private BigDecimal cashExpected;
    private BigDecimal cashActual;
    private BigDecimal cashDiff;
    private BigDecimal totalIncome;
    private Integer orderCount;
    private Integer status;
    private String statusLabel;
    private Long auditBy;
    private LocalDateTime createdAt;
}
