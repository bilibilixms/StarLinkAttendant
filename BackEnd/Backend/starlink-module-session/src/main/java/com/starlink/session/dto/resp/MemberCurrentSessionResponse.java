package com.starlink.session.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员端当前上机会话响应（小程序「当前上机」页）。
 * <p>
 * 字段命名与小程序 types/session.ts 的 CurrentSession 对齐。
 * 活跃会话的时长/费用为实时计算值，每次请求都会重新代入费率公式。
 *
 */
@Data
public class MemberCurrentSessionResponse {

    /** 会话 ID */
    private Long sessionId;

    /** 会话编号 */
    private String sessionNo;

    /** 门店 ID（单店演示阶段固定为 1） */
    private Long storeId;

    /** 门店名称 */
    private String storeName;

    /** 机位 ID */
    private Long computerId;

    /** 机位名称 */
    private String computerName;

    /** 机位编号 */
    private String seatNo;

    /** 区域名称 */
    private String areaName;

    /** 机位配置描述（CPU/GPU/内存/屏幕 拼装） */
    private String computerSpec;

    /** 上机时间 */
    private LocalDateTime startTime;

    /** 下机时间（活跃会话为空） */
    private LocalDateTime endTime;

    /** 已上机计费时长（分钟，实时，不含临时下机时段） */
    private Integer durationMinutes;

    /** 已计费时长（分钟） */
    private Integer billedMinutes;

    /** 赠送/优惠时长（分钟） */
    private Integer freeMinutes;

    /** 当前已产生总费用 */
    private BigDecimal totalAmount;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 当前应付金额（活跃会话等于已产生费用） */
    private BigDecimal paidAmount;

    /** 续价折算每小时单价（元/小时，供小程序展示与剩余时长估算） */
    private BigDecimal hourlyRate;

    /** 首时段（通常为首小时）价格：不足 1 小时按此价格收取 */
    private BigDecimal firstHourPrice;

    /** 会员当前余额（未扣本次费用） */
    private BigDecimal balance;

    /** 此刻下机扣费后的预计余额 = balance - paidAmount（最低 0） */
    private BigDecimal balanceAfter;

    /** 扣费后余额按续费小时价可支撑的剩余分钟；-1 表示不计费/不限时 */
    private Integer remainingMinutes;

    /** 状态：0-上机中 1-临时下机 2-已下机 3-强制下机 4-异常中断 */
    private Byte status;
}
