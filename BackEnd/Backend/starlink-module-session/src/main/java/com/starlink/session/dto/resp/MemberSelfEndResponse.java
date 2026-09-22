package com.starlink.session.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员端自助下机结算响应（小程序）。
 * <p>
 * paidAmount 为本次实际从余额扣减的金额；余额不足时可能小于 totalAmount。
 *
 */
@Data
public class MemberSelfEndResponse {

    /** 会话 ID */
    private Long sessionId;

    /** 会话编号 */
    private String sessionNo;

    /** 计费时长（分钟，不足 1 分钟按 1 分钟起计） */
    private Integer durationMinutes;

    /** 总费用 */
    private BigDecimal totalAmount;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 实付金额（本次实际扣款） */
    private BigDecimal paidAmount;

    /** 结算后余额 */
    private BigDecimal balanceAfter;

    /** 下机时间 */
    private LocalDateTime endTime;
}
