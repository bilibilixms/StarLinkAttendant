package com.starlink.cashier.dto.request;

import com.starlink.common.util.PageQuery;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class BillQueryRequest extends PageQuery {

    @Pattern(regexp = "ORDER|SESSION|RECHARGE", message = "账单类型无效")
    private String source;

    @Pattern(regexp = "PRODUCT|SESSION|RECHARGE", message = "账单类别无效")
    private String category;

    private String billNo;
    private Long memberId;
    private Integer status;
    private LocalDate startDate;
    private LocalDate endDate;
}
