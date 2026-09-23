package com.starlink.cashier.dto.request;

import com.starlink.common.util.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ShiftQueryRequest extends PageQuery {

    /** 员工ID */
    private Long employeeId;

    /** 班次状态 */
    private Integer status;
}
