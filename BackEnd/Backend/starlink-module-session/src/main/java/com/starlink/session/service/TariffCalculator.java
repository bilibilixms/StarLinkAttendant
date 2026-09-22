package com.starlink.session.service;

import com.starlink.session.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 上机费用统一计费器。
 * <p>
 * 会员自助下机、管理端下机/强制下机、每分钟计费调度、会员端当前会话实时估价
 * 全部走本类，保证「页面看到的费用」与「下机实际扣款」永远同一口径。
 * <p>
 * 计费规则：
 * <ul>
 *   <li>不足 1 分钟按 1 分钟起计；</li>
 *   <li>按时方案：首个计费时段（first_minutes，通常为 60 分钟）固定收 first_price，
 *       即不足 1 小时按 1 小时计费；超出首时段的部分不足 1 小时也按 1 小时计，
 *       金额 = 向上取整小时数 × 续价小时单价（renewal_price 元/分钟 × 60）；</li>
 *   <li>包时方案（rate_type=3）：固定收 first_price；</li>
 *   <li>费率缺失 / 首时价缺失时返回 0。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TariffCalculator {

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final SessionMapper sessionMapper;

    /**
     * 按累计计费分钟数计算会话总费用。
     *
     * @param totalMinutes 累计计费分钟（暂停/临时下机时间不计），0 也按 1 分钟起计
     * @param tariffPlanId 费率方案 ID
     * @return 应付金额（两位小数）
     */
    public BigDecimal calculate(int totalMinutes, Long tariffPlanId) {
        if (tariffPlanId == null) {
            return ZERO;
        }
        // 不足 1 分钟按 1 分钟起计：开机后立刻下机也要收首小时费用
        int minutes = Math.max(totalMinutes, 1);

        List<Map<String, Object>> rates = sessionMapper.selectTariffRates(tariffPlanId);
        if (rates.isEmpty()) {
            return ZERO;
        }

        // 包时价：固定金额
        for (Map<String, Object> rate : rates) {
            if (rateType(rate) == 3) {
                return scale(toBigDecimal(rate.get("firstPrice")));
            }
        }

        // 按时方案：首时价 + 续价小时单价
        BigDecimal firstPrice = BigDecimal.ZERO;
        int firstMinutes = 0;
        BigDecimal renewalPricePerMinute = BigDecimal.ZERO;
        for (Map<String, Object> rate : rates) {
            int rt = rateType(rate);
            if (rt == 1) {
                firstPrice = toBigDecimal(rate.get("firstPrice"));
                firstMinutes = ((Number) rate.get("firstMinutes")).intValue();
            } else if (rt == 2) {
                renewalPricePerMinute = toBigDecimal(rate.get("renewalPrice"));
            }
        }

        if (firstPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        // 首时段内（含不足 1 小时）：固定首时价
        if (firstMinutes <= 0 || minutes <= firstMinutes) {
            return scale(firstPrice);
        }

        // 超出首时段：不足 1 小时按 1 小时，按续价小时单价计
        int extraMinutes = minutes - firstMinutes;
        if (renewalPricePerMinute.compareTo(BigDecimal.ZERO) > 0) {
            int extraHours = (int) Math.ceil(extraMinutes / 60.0);
            BigDecimal extraAmount = renewalPricePerMinute.multiply(SIXTY)
                    .multiply(BigDecimal.valueOf(extraHours));
            return scale(firstPrice.add(extraAmount));
        }

        // 无续价配置的兜底：超出后每个「首时段长度」按一个首时价、向上取整
        int periods = (int) Math.ceil(minutes / (double) firstMinutes);
        return scale(firstPrice.multiply(BigDecimal.valueOf(periods)));
    }

    /**
     * 续价折算的每小时单价（元/小时）；无续价时回退为首时价折算；包时返回 0。
     * 供小程序展示「续费 X 元/小时」与估算剩余时长。
     */
    public BigDecimal renewalHourlyRate(Long tariffPlanId) {
        if (tariffPlanId == null) {
            return ZERO;
        }
        List<Map<String, Object>> rates = sessionMapper.selectTariffRates(tariffPlanId);
        BigDecimal renewalPerMinute = null;
        BigDecimal firstPrice = null;
        int firstMinutes = 0;
        for (Map<String, Object> rate : rates) {
            int rt = rateType(rate);
            if (rt == 3) {
                return ZERO;
            }
            if (rt == 2) {
                renewalPerMinute = toBigDecimal(rate.get("renewalPrice"));
            } else if (rt == 1) {
                firstPrice = toBigDecimal(rate.get("firstPrice"));
                firstMinutes = ((Number) rate.get("firstMinutes")).intValue();
            }
        }
        if (renewalPerMinute != null && renewalPerMinute.compareTo(BigDecimal.ZERO) > 0) {
            return scale(renewalPerMinute.multiply(SIXTY));
        }
        if (firstPrice != null && firstPrice.compareTo(BigDecimal.ZERO) > 0 && firstMinutes > 0) {
            return scale(firstPrice.multiply(SIXTY).divide(BigDecimal.valueOf(firstMinutes),
                    2, RoundingMode.HALF_UP));
        }
        return ZERO;
    }

    /**
     * 首时段价格（元）；包时方案即包时价；缺失返回 0。
     */
    public BigDecimal firstPeriodPrice(Long tariffPlanId) {
        if (tariffPlanId == null) {
            return ZERO;
        }
        List<Map<String, Object>> rates = sessionMapper.selectTariffRates(tariffPlanId);
        BigDecimal firstPrice = BigDecimal.ZERO;
        for (Map<String, Object> rate : rates) {
            int rt = rateType(rate);
            if (rt == 1 || rt == 3) {
                firstPrice = toBigDecimal(rate.get("firstPrice"));
            }
        }
        return scale(firstPrice);
    }

    private int rateType(Map<String, Object> rate) {
        return ((Number) rate.get("rateType")).intValue();
    }

    private BigDecimal scale(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }
}
