package com.starlink.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局枚举定义。
 * <p>
 * 与 API 规范中的枚举值保持一致。每个枚举提供 {@code code}（整数码）和 {@code label}（中文标签）。
 *
 */
public final class CommonEnums {

    private CommonEnums() {
    }

    // ==================== 通用枚举接口 ====================

    /**
     * 统一枚举接口，方便前端下拉框等场景获取 code/label。
     */
    public interface IEnum {
        int getCode();
        String getLabel();
    }

    // ==================== 性别 ====================

    @Getter
    @AllArgsConstructor
    public enum Gender implements IEnum {
        UNKNOWN(0, "未知"),
        MALE(1, "男"),
        FEMALE(2, "女");

        private final int code;
        private final String label;

        public static Gender of(int code) {
            return Arrays.stream(values())
                    .filter(e -> e.code == code)
                    .findFirst()
                    .orElse(UNKNOWN);
        }
    }

    // ==================== 机位状态 ====================

    @Getter
    @AllArgsConstructor
    public enum SeatStatus implements IEnum {
        IDLE(0, "空闲"),
        IN_USE(1, "使用中"),
        LOCKED(2, "锁定"),
        MAINTENANCE(3, "维护中");

        private final int code;
        private final String label;

        public static SeatStatus of(int code) {
            return Arrays.stream(values())
                    .filter(e -> e.code == code)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的机位状态: " + code));
        }

        public static Map<Integer, String> toMap() {
            return Arrays.stream(values()).collect(Collectors.toMap(IEnum::getCode, IEnum::getLabel));
        }
    }

    // ==================== 会话状态 ====================

    @Getter
    @AllArgsConstructor
    public enum SessionStatus implements IEnum {
        ACTIVE(0, "进行中"),
        PAUSED(1, "已暂停"),
        ENDED(2, "已结束");

        private final int code;
        private final String label;

        public static SessionStatus of(int code) {
            return Arrays.stream(values())
                    .filter(e -> e.code == code)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的会话状态: " + code));
        }
    }

    // ==================== 订单状态 ====================

    @Getter
    @AllArgsConstructor
    public enum OrderStatus implements IEnum {
        PENDING(0, "待支付"),
        PAID(1, "已支付"),
        COMPLETED(2, "已完成"),
        CANCELLED(3, "已取消"),
        REFUNDED(4, "已退款");

        private final int code;
        private final String label;

        public static OrderStatus of(int code) {
            return Arrays.stream(values())
                    .filter(e -> e.code == code)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的订单状态: " + code));
        }
    }

    // ==================== 支付方式 ====================

    @Getter
    @AllArgsConstructor
    public enum PayType implements IEnum {
        CASH(1, "现金"),
        WECHAT(2, "微信"),
        ALIPAY(3, "支付宝"),
        BALANCE(4, "会员余额");

        private final int code;
        private final String label;

        public static PayType of(int code) {
            return Arrays.stream(values())
                    .filter(e -> e.code == code)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的支付方式: " + code));
        }
    }

    // ==================== 会员状态 ====================

    @Getter
    @AllArgsConstructor
    public enum MemberStatus implements IEnum {
        NORMAL(0, "正常"),
        FROZEN(1, "冻结"),
        CANCELLED(2, "注销");

        private final int code;
        private final String label;

        public static MemberStatus of(int code) {
            return Arrays.stream(values())
                    .filter(e -> e.code == code)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的会员状态: " + code));
        }
    }

    // ==================== 库存操作类型 ====================

    @Getter
    @AllArgsConstructor
    public enum InventoryChangeType implements IEnum {
        INBOUND(1, "入库"),
        OUTBOUND(2, "出库"),
        SURPLUS(3, "盘盈"),
        LOSS(4, "盘亏");

        private final int code;
        private final String label;

        public static InventoryChangeType of(int code) {
            return Arrays.stream(values())
                    .filter(e -> e.code == code)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的库存操作类型: " + code));
        }
    }
}
