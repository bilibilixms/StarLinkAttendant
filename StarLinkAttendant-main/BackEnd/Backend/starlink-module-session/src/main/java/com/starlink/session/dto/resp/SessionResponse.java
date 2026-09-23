package com.starlink.session.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 上机会话响应。
 *
 */
@Data
public class SessionResponse {

    private Long id;

    /** 会话编号 */
    private String sessionNo;

    /** 机位 ID */
    private Long computerId;

    /** 机位编号 */
    private String computerNo;

    /** 机位名称 */
    private String computerName;

    /** 会员 ID */
    private Long memberId;

    /** 会员姓名 */
    private String memberName;

    /** 会员手机号 */
    private String memberPhone;

    /** 认证方式：1-刷卡 2-扫码 3-人脸 4-临时密码 */
    private Byte authMethod;

    /** 状态标签 */
    private String statusLabel;

    /** 费率方案 ID */
    private Long tariffPlanId;

    /** 费率方案名称 */
    private String tariffPlanName;

    /** 上机时间 */
    private LocalDateTime startTime;

    /** 下机时间 */
    private LocalDateTime endTime;

    /** 预计时长（分钟） */
    private Integer expectedMinutes;

    /** 已计费时长（分钟） */
    private Integer billedMinutes;

    /** 赠送/优惠时长（分钟） */
    private Integer freeMinutes;

    /** 总费用 */
    private BigDecimal totalAmount;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 实付金额 */
    private BigDecimal paidAmount;

    /** 状态：0-上机中 1-临时下机 2-已下机 3-强制下机 4-异常中断 */
    private Byte status;

    /** 临时下机次数 */
    private Integer pauseCount;

    /** 临时下机总时长（分钟） */
    private Integer pauseDuration;

    /** 操作员工 ID */
    private Long operatorId;

    /** 备注 */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 计时段列表（详情接口使用） */
    private List<SessionTimingResponse> timings;
}
