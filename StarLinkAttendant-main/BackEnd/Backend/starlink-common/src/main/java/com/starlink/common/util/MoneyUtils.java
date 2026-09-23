package com.starlink.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额计算工具类。
 * <p>
 * 系统内部所有金额以 <b>分（整数）</b> 存储和传输，前端展示时转换为元。
 * 本工具提供分↔元转换、格式化展示、安全运算等方法，避免浮点精度问题。
 *
 */
public final class MoneyUtils {

    private MoneyUtils() {
    }

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    // ==================== 分 ↔ 元 转换 ====================

    /**
     * 分转元（BigDecimal）。
     *
     * @param cents 金额（分）
     * @return 金额（元），保留 2 位小数
     */
    public static BigDecimal centsToYuan(long cents) {
        return BigDecimal.valueOf(cents).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /**
     * 元转分。
     *
     * @param yuan 金额（元）
     * @return 金额（分），四舍五入
     */
    public static long yuanToCents(BigDecimal yuan) {
        return yuan.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * 元转分（字符串入参，避免浮点精度问题）。
     */
    public static long yuanToCents(String yuanStr) {
        return yuanToCents(new BigDecimal(yuanStr));
    }

    // ==================== 格式化展示 ====================

    /**
     * 格式化为人民币展示（如 ¥100.50）。
     *
     * @param cents 金额（分）
     * @return 格式化字符串
     */
    public static String formatMoney(long cents) {
        return String.format("¥%s", centsToYuan(cents).toPlainString());
    }

    /**
     * 格式化为纯数字展示（如 100.50）。
     */
    public static String formatAmount(long cents) {
        return centsToYuan(cents).toPlainString();
    }

    // ==================== 安全运算 ====================

    /**
     * 金额加法（分）。
     */
    public static long add(long a, long b) {
        return a + b;
    }

    /**
     * 金额减法（分），结果不为负。
     *
     * @throws ArithmeticException 如果 a < b
     */
    public static long subtract(long a, long b) {
        if (a < b) {
            throw new ArithmeticException("金额不足: " + formatMoney(a) + " < " + formatMoney(b));
        }
        return a - b;
    }

    /**
     * 计算折扣后的金额（分）。
     *
     * @param amount       原价（分）
     * @param discountRate 折扣率（百分比，如 85.50 表示 85.50% = 8.55 折）
     * @return 折后金额（分），四舍五入
     */
    public static long applyDiscount(long amount, BigDecimal discountRate) {
        return BigDecimal.valueOf(amount)
                .multiply(discountRate)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * 判断金额是否足够（余额 ≥ 需要支付）。
     */
    public static boolean isSufficient(long balance, long required) {
        return balance >= required;
    }
}
