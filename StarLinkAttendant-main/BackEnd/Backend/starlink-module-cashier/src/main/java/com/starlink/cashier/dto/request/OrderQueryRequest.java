package com.starlink.cashier.dto.request;

import com.starlink.common.util.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderQueryRequest extends PageQuery {

    /** 订单号 */
    private String orderNo;

    /** 订单类型 */
    private Integer orderType;

    /** 订单状态 */
    private Integer status;

    /** 订单状态列表（多选） */
    private List<Integer> statusList;

    /** 会员ID */
    private Long memberId;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;
}
