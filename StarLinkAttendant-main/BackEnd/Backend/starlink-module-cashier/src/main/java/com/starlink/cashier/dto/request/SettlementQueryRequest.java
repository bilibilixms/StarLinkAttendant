package com.starlink.cashier.dto.request;

import com.starlink.common.util.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SettlementQueryRequest extends PageQuery {

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;

    /** 结算状态 */
    private Integer status;
}
