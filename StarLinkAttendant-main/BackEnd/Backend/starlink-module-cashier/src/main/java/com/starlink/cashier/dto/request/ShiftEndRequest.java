package com.starlink.cashier.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ShiftEndRequest {

    /** 实际现金金额 */
    @NotNull(message = "实际现金不能为空")
    private BigDecimal cashActual;
}
