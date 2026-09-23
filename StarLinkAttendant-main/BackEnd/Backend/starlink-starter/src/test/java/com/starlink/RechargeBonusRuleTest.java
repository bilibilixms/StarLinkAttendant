package com.starlink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.starlink.member.mapper.RechargeCampaignMapper;
import com.starlink.member.service.RechargeBonusCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 充值赠送阶梯规则的<b>单元测试</b>（不依赖数据库）。
 * <p>
 * 直接验证 {@link RechargeBonusCalculator#matchTierBonus(String, BigDecimal, Long)}：
 * 阶梯匹配、两种 tier 写法、乱序、非法配置等。
 */
class RechargeBonusRuleTest {

    private final RechargeBonusCalculator calculator =
            new RechargeBonusCalculator(mock(RechargeCampaignMapper.class), new ObjectMapper());

    /** 种子数据使用的比例式 tiers（需求文档「满 X 赠 Y%」） */
    private static final String RATE_RULES =
            "{\"type\":\"recharge_bonus\",\"tiers\":["
                    + "{\"min\":200,\"bonus_rate\":5},"
                    + "{\"min\":500,\"bonus_rate\":10},"
                    + "{\"min\":1000,\"bonus_rate\":15}]}";

    /** 管理端「充值促销」页写入的固定额式 tiers（充 X 送 Y 元） */
    private static final String FIXED_RULES =
            "{\"tiers\":[{\"rechargeAmount\":100,\"bonusAmount\":10},"
                    + "{\"rechargeAmount\":500,\"bonusAmount\":80}]}";

    private BigDecimal bonus(String rules, String amount) {
        return calculator.matchTierBonus(rules, new BigDecimal(amount), 1L);
    }

    /* ==================== 比例式阶梯（种子口径） ==================== */

    @Test
    @DisplayName("比例式：充 100 未满首档 → 赠送 0（这正是「充值100却到账115」的修复点）")
    void belowFirstTierGetsNoBonus() {
        assertEquals(0, bonus(RATE_RULES, "100").compareTo(BigDecimal.ZERO));
        assertEquals(0, bonus(RATE_RULES, "199.99").compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("比例式：命中最高档后按该档比例 × 实付金额计算")
    void matchesHighestApplicableTier() {
        // 比例式语义：命中档位决定「比例」，赠送额 = 实付金额 × 该档比例
        assertEquals(0, bonus(RATE_RULES, "200").compareTo(new BigDecimal("10.00")));    // 5%
        assertEquals(0, bonus(RATE_RULES, "499").compareTo(new BigDecimal("24.95")));    // 仍 5%，499×5%
        assertEquals(0, bonus(RATE_RULES, "500").compareTo(new BigDecimal("50.00")));    // 10%
        assertEquals(0, bonus(RATE_RULES, "1000").compareTo(new BigDecimal("150.00")));  // 15%
        assertEquals(0, bonus(RATE_RULES, "5000").compareTo(new BigDecimal("750.00")));  // 封顶档 15%
    }

    /* ==================== 固定额式阶梯（管理端口径） ==================== */

    @Test
    @DisplayName("固定额式：充 X 送 Y 元，按最高命中档取固定赠送额")
    void fixedAmountTiers() {
        assertEquals(0, bonus(FIXED_RULES, "50").compareTo(BigDecimal.ZERO));
        assertEquals(0, bonus(FIXED_RULES, "100").compareTo(new BigDecimal("10.00")));
        assertEquals(0, bonus(FIXED_RULES, "499").compareTo(new BigDecimal("10.00")));
        assertEquals(0, bonus(FIXED_RULES, "500").compareTo(new BigDecimal("80.00")));
    }

    /* ==================== 健壮性 ==================== */

    @Test
    @DisplayName("乱序 tiers 也能取到门槛最高的命中档")
    void unorderedTiers() {
        String unordered = "{\"tiers\":[{\"min\":1000,\"bonus_rate\":15},"
                + "{\"min\":200,\"bonus_rate\":5},{\"min\":500,\"bonus_rate\":10}]}";
        assertEquals(0, bonus(unordered, "500").compareTo(new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("异常/空配置一律按赠送 0 处理，不抛异常（不阻断充值）")
    void malformedRulesDegradeToZero() {
        assertEquals(0, bonus("not-json", "1000").compareTo(BigDecimal.ZERO));
        assertEquals(0, bonus("{}", "1000").compareTo(BigDecimal.ZERO));
        assertEquals(0, bonus("{\"tiers\":[]}", "1000").compareTo(BigDecimal.ZERO));
        assertEquals(0, bonus("{\"tiers\":[{\"min\":100}]}", "1000").compareTo(BigDecimal.ZERO)); // 档位无赠送字段
    }

    @Test
    @DisplayName("缺少门槛字段的档位被跳过，不影响其他档位")
    void tierWithoutThresholdIsSkipped() {
        String rules = "{\"tiers\":[{\"bonus_rate\":99},{\"min\":100,\"bonus_rate\":5}]}";
        assertEquals(0, bonus(rules, "100").compareTo(new BigDecimal("5.00")));
    }

    @Test
    @DisplayName("金额为 0 或负数时不赠送")
    void nonPositiveAmountGetsNoBonus() {
        assertEquals(0, bonus(RATE_RULES, "0").compareTo(BigDecimal.ZERO));
        assertEquals(0, bonus(RATE_RULES, "-100").compareTo(BigDecimal.ZERO));
    }
}
