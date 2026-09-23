package com.starlink.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("member_points_log")
public class MemberPointsLog extends BaseEntity {

    private Long memberId;

    private Integer points;

    private Integer balanceBefore;

    private Integer balanceAfter;

    private Byte bizType;

    private Long bizId;

    private String remark;

    private LocalDateTime expireAt;
}