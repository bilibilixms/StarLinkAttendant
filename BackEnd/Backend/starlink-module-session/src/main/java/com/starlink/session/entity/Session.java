package com.starlink.session.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 上机会话实体。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("session")
public class Session extends BaseEntity {

    /** 终端 ID */
    private Long computerId;

    /** 会员 ID（NULL 表示散客） */
    private Long memberId;

    /** 会话编号 */
    private String sessionNo;

    /** 认证方式：1-刷卡 2-扫码 3-人脸 4-临时密码 */
    private Byte authMethod;

    /** 适用费率方案 ID */
    private Long tariffPlanId;

    /** 上机时间 */
    private LocalDateTime startTime;

    /** 下机时间 */
    private LocalDateTime endTime;

    /** 预计时长（预约/包时场景，分钟） */
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
}
