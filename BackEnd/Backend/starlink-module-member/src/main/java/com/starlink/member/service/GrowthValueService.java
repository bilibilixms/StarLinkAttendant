package com.starlink.member.service;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.member.entity.Member;
import com.starlink.member.entity.MemberGrowthLog;
import com.starlink.member.entity.MemberLevel;
import com.starlink.member.mapper.MemberGrowthLogMapper;
import com.starlink.member.mapper.MemberLevelMapper;
import com.starlink.member.mapper.MemberMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员成长值（经验）服务。
 * <p>
 * 成长值来源于现金/微信/支付宝直接消费、余额充值（按实付金额，1元=1经验，向下取整）。
 * 余额消费、上机扣费不产生成长值。
 * 成长值达到更高等级区间且等级配置了自动升级时，自动升级，只升不降。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrowthValueService {

    private final MemberMapper memberMapper;
    private final MemberLevelMapper levelMapper;
    private final MemberGrowthLogMapper growthLogMapper;

    /**
     * 增加成长值并处理自动升级（同一事务）。
     *
     * @param memberId 会员ID
     * @param growth   本次成长值（正数）；小于等于0直接跳过
     * @param bizType  业务类型（CommonConstants.GROWTH_BIZ_*）
     * @param bizId    关联业务ID
     * @param remark   备注
     */
    @Transactional
    public void addGrowth(Long memberId, Integer growth, Byte bizType, Long bizId, String remark) {
        if (growth == null || growth <= 0) {
            return;
        }

        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }

        int before = member.getGrowthValue() == null ? 0 : member.getGrowthValue();
        int after = before + growth;

        // 只升不降：仅当目标等级 auto_upgrade=1 且等级高于当前时才升级
        Long oldLevelId = member.getLevelId();
        int oldOrder = safeOrder(oldLevelId == null ? null : levelMapper.selectById(oldLevelId));
        MemberLevel targetLevel = levelMapper.selectByGrowth(after);
        Long newLevelId = oldLevelId;
        boolean upgraded = false;
        if (targetLevel != null
                && targetLevel.getAutoUpgrade() != null
                && targetLevel.getAutoUpgrade() == 1
                && oldOrder < safeOrder(targetLevel)) {
            newLevelId = targetLevel.getId();
            upgraded = true;
        }

        MemberGrowthLog logEntry = new MemberGrowthLog();
        logEntry.setMemberId(memberId);
        logEntry.setGrowth(growth);
        logEntry.setGrowthBefore(before);
        logEntry.setGrowthAfter(after);
        logEntry.setBizType(bizType);
        logEntry.setBizId(bizId);
        logEntry.setRemark(remark);
        logEntry.setLevelBeforeId(oldLevelId);
        logEntry.setLevelAfterId(newLevelId);
        logEntry.setIsUpgraded(upgraded ? (byte) 1 : (byte) 0);

        growthLogMapper.insert(logEntry);

        member.setGrowthValue(after);
        if (upgraded) {
            member.setLevelId(newLevelId);
        }
        memberMapper.updateById(member);

        if (upgraded) {
            log.info("会员自动升级: memberId={}, level={}->{}", memberId, oldLevelId, newLevelId);
        } else {
            log.info("会员成长值增加: memberId={}, growth={}, after={}", memberId, growth, after);
        }
    }

    private int safeOrder(MemberLevel level) {
        if (level == null || level.getLevelOrder() == null) {
            return 0;
        }
        return level.getLevelOrder();
    }
}