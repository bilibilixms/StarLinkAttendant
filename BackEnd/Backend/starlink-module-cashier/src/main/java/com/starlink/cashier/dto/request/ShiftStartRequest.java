package com.starlink.cashier.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ShiftStartRequest {

    /** 开班备用金 */
    @NotNull(message = "备用金不能为空")
    private BigDecimal openingBalance;
}
