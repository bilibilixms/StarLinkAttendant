package com.starlink.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("member_growth_log")
public class MemberGrowthLog extends BaseEntity {

    private Long memberId;

    /** 本次获得成长值（正数） */
    private Integer growth;

    /** 变动前累计成长值 */
    private Integer growthBefore;

    /** 变动后累计成长值 */
    private Integer growthAfter;

    /** 业务类型：1-收银直接消费 2-余额充值 */
    private Byte bizType;

    /** 关联业务ID（订单ID/充值记录ID） */
    private Long bizId;

    /** 变动前等级ID */
    private Long levelBeforeId;

    /** 变动后等级ID（含自动升级后） */
    private Long levelAfterId;

    /** 本次是否触发自动升级：0-否 1-是 */
    private Byte isUpgraded;

    private String remark;
}