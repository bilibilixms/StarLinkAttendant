package com.starlink.cashier.dto.request;

import com.starlink.common.util.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RefundQueryRequest extends PageQuery {

    /** 订单ID */
    private Long orderId;

    /** 退款状态 */
    private Integer status;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;
}
