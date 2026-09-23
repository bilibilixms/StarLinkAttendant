package com.starlink.member.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starlink.member.mapper.RechargeCampaignMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 充值赠送金额计算器 —— <b>充值赠送规则的唯一权威实现</b>。
 *
 * <h3>规则来源（本轮确定）</h3>
 * 赠送金额只由「生效中的充值赠送活动」决定，即 {@code campaign}
 * （{@code campaign_type = 1}）的 {@code rules} 阶梯配置：
 * <pre>
 * {"type":"recharge_bonus","tiers":[
 *     {"min":200,  "bonus_rate":5},
 *     {"min":500,  "bonus_rate":10},
 *     {"min":1000, "bonus_rate":15}]}
 * </pre>
 * 取「门槛 ≤ 实付金额」中门槛最高的一档；
 * 未命中任何档位（如充 100 未满 200）→ 赠送 0。
 *
 * <h3>兼容两种阶梯写法</h3>
 * 历史数据与管理端页面存在两种 tier 结构，本类同时支持：
 * <ul>
 *   <li>比例式（种子数据 / 需求文档「满 X 赠 Y%」）：
 *       {@code {"min":500, "bonus_rate":10}} → 按实付金额的百分比赠送；</li>
 *   <li>固定额式（管理端「充值促销」页写入）：
 *       {@code {"rechargeAmount":500, "bonusAmount":50}} → 直接赠送固定金额。</li>
 * </ul>
 * 两种写法中的门槛字段分别接受 {@code min} 与 {@code rechargeAmount}。
 *
 * <h3>已废弃的口径</h3>
 * {@code member_level.recharge_bonus_rate}（等级固定赠送比例，无门槛）<b>不再参与充值赠送计算</b>。
 * 它曾导致「充 100 也送 15%」这类无门槛赠送，与本项目需求文档 MKT-04
 * 「充赠规则配置（满 X 元赠 Y 元）」及 member_recharge.campaign_id 外键的语义不符。
 * 该字段保留为会员等级权益展示用途。
 *
 * <p>容错：活动 rules 解析失败时记录 WARN 并按「赠送 0」处理，
 * 不让一条配置错误的活动阻断全部充值（同时保留日志便于定位）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RechargeBonusCalculator {

    private final RechargeCampaignMapper campaignMapper;
    private final ObjectMapper objectMapper;

    /**
     * 赠送计算结果。
     *
     * @param bonusAmount 赠送金额（未命中任何档位时为 0，不为 null）
     * @param campaignId  实际产生赠送的活动 ID；未命中档位/无活动时为 null
     */
    public record BonusResult(BigDecimal bonusAmount, Long campaignId) {
        static BonusResult none() {
            return new BonusResult(BigDecimal.ZERO, null);
        }
    }

    /**
     * 按当前生效的充值活动阶梯，计算实付 {@code rechargeAmount} 对应的赠送金额。
     *
     * @param rechargeAmount 实付充值金额（正数）
     */
    public BonusResult calculate(BigDecimal rechargeAmount) {
        if (rechargeAmount == null || rechargeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BonusResult.none();
        }

        Map<String, Object> campaign = campaignMapper.selectEffectiveRechargeCampaign(LocalDateTime.now());
        if (campaign == null || campaign.isEmpty()) {
            log.debug("无生效中的充值赠送活动，赠送金额为 0");
            return BonusResult.none();
        }

        Long campaignId = toLong(campaign.get("campaignId"));
        String rulesJson = campaign.get("rulesJson") == null ? null : String.valueOf(campaign.get("rulesJson"));
        if (!StringUtils.hasText(rulesJson)) {
            log.warn("充值活动缺少 rules 配置，按赠送 0 处理: campaignId={}", campaignId);
            return BonusResult.none();
        }

        BigDecimal bonus = matchTierBonus(rulesJson, rechargeAmount, campaignId);
        if (bonus.compareTo(BigDecimal.ZERO) <= 0) {
            // 未命中任何档位：不记录 campaignId，避免把「该活动没送」记成「该活动送的」
            log.debug("实付 {} 未命中任何充值活动档位，赠送 0: campaignId={}", rechargeAmount, campaignId);
            return BonusResult.none();
        }
        return new BonusResult(bonus, campaignId);
    }

    /**
     * 解析 tiers 并取「门槛最高且 ≤ 实付金额」那一档的赠送额；任何异常都退化为 0（记 WARN）。
     * <p>
     * 抽为可测试方法（不依赖数据库），便于对阶梯匹配规则做单元测试。
     *
     * @param rulesJson    活动 rules JSON
     * @param amount       实付充值金额
     * @param campaignId   仅用于日志
     */
    public BigDecimal matchTierBonus(String rulesJson, BigDecimal amount, Long campaignId) {
        try {
            JsonNode tiers = objectMapper.readTree(rulesJson).path("tiers");
            if (!tiers.isArray() || tiers.isEmpty()) {
                log.warn("充值活动 rules 不含 tiers，按赠送 0 处理: campaignId={}", campaignId);
                return BigDecimal.ZERO;
            }

            BigDecimal bestThreshold = null;
            BigDecimal bestBonus = BigDecimal.ZERO;

            for (JsonNode tier : tiers) {
                BigDecimal threshold = firstNumber(tier, "min", "rechargeAmount");
                if (threshold == null) {
                    continue;
                }
                if (threshold.compareTo(amount) > 0) {
                    continue;
                }
                // 命中：门槛 ≤ 实付金额。取门槛最高者
                if (bestThreshold != null && threshold.compareTo(bestThreshold) <= 0) {
                    continue;
                }
                BigDecimal bonus = resolveTierBonus(tier, amount);
                if (bonus == null) {
                    continue;
                }
                bestThreshold = threshold;
                bestBonus = bonus;
            }

            if (bestThreshold == null) {
                return BigDecimal.ZERO;
            }
            return bestBonus.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("充值活动 rules 解析失败，按赠送 0 处理: campaignId={}, reason={}",
                    campaignId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * 单档赠送额：固定额优先（bonusAmount），否则按比例（bonus_rate，百分数）。
     * 两者都没有时返回 null（该档不可用）。
     */
    private BigDecimal resolveTierBonus(JsonNode tier, BigDecimal amount) {
        BigDecimal fixed = firstNumber(tier, "bonusAmount");
        if (fixed != null) {
            return fixed;
        }
        BigDecimal rate = firstNumber(tier, "bonus_rate", "bonusRate");
        if (rate != null) {
            return amount.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        return null;
    }

    /** 依次尝试多个字段名，返回第一个存在的数值字段。 */
    private BigDecimal firstNumber(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode value = node.get(field);
            if (value != null && value.isNumber()) {
                return value.decimalValue();
            }
            if (value != null && value.isTextual()) {
                try {
                    return new BigDecimal(value.asText());
                } catch (NumberFormatException ignored) {
                    // 继续尝试下一个字段名
                }
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
