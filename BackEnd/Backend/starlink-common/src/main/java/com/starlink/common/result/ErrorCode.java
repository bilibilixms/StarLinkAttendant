package com.starlink.common.result;

/**
 * 业务错误码枚举。
 * <p>
 * 编码规则：5 位数字，首位为模块标识。
 * <ul>
 *   <li>0 — 成功</li>
 *   <li>10xxx — 认证/权限</li>
 *   <li>20xxx — 会员模块</li>
 *   <li>30xxx — 上机模块</li>
 *   <li>40xxx — 计费模块</li>
 *   <li>50xxx — 商品模块</li>
 *   <li>60xxx — 收银模块</li>
 *   <li>70xxx — 库存模块</li>
 *   <li>80xxx — 报表模块</li>
 *   <li>90xxx — 营销模块</li>
 * </ul>
 * 各模块可按此规则扩展具体错误码。
 *
 */
public enum ErrorCode {

    // ==================== 全局 ====================
    SUCCESS(0, "操作成功"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未认证或 Token 已过期"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ==================== 认证/权限 (10xxx) ====================
    LOGIN_FAILED(10001, "用户名或密码错误"),
    TOKEN_EXPIRED(10002, "Token 已过期"),
    TOKEN_INVALID(10003, "Token 无效"),
    PERMISSION_DENIED(10004, "权限不足"),
    ACCOUNT_DISABLED(10005, "账号已被禁用"),
    CAPTCHA_ERROR(10006, "验证码错误或已过期"),

    // ==================== 会员模块 (20xxx) ====================
    MEMBER_NO_EXISTS(20001, "会员编号已存在"),
    BALANCE_INSUFFICIENT(20002, "余额不足"),
    MEMBER_BLACKLISTED(20003, "会员已被列入黑名单"),

    // ==================== 上机模块 (30xxx) ====================
    SEAT_OCCUPIED(30001, "该机位已被占用"),
    SESSION_ACTIVE(30002, "该会员已有进行中的会话"),
    RESERVATION_CONFLICT(30003, "预约时段冲突"),
    COMPUTER_NOT_AVAILABLE(30004, "机位当前不可用"),
    SESSION_NOT_FOUND(30005, "上机会话不存在"),
    SESSION_ALREADY_ENDED(30006, "会话已结束，无法重复操作"),
    RESERVATION_NOT_FOUND(30007, "预约记录不存在"),
    RESERVATION_ALREADY_CHECKED_IN(30008, "该预约已签到"),

    // ==================== 计费模块 (40xxx) ====================
    TARIFF_NOT_FOUND(40001, "费率方案不存在"),
    BILLING_CONFLICT(40002, "计费规则冲突"),

    // ==================== 商品模块 (50xxx) ====================
    PRODUCT_NOT_FOUND(50001, "商品不存在"),
    PRODUCT_CODE_DUPLICATE(50002, "商品编码已存在"),
    COMBO_NOT_FOUND(50003, "套餐不存在"),
    COMBO_PRODUCT_EMPTY(50004, "套餐商品不能为空"),
    COMBO_PRODUCT_NOT_FOUND(50005, "套餐内商品不存在"),
    PRODUCT_CATEGORY_NOT_FOUND(50006, "商品分类不存在"),
    PRODUCT_CATEGORY_HAS_CHILDREN(50007, "该分类存在子分类，无法删除"),
    PRODUCT_CATEGORY_HAS_PRODUCTS(50008, "该分类下存在商品，无法删除"),
    PARENT_CATEGORY_NOT_FOUND(50009, "父级分类不存在"),

    // ==================== 收银模块 (60xxx) ====================
    ORDER_NOT_FOUND(60001, "订单不存在"),
    ORDER_REFUND_DENIED(60002, "订单状态不允许退款"),
    ORDER_ALREADY_PAID(60003, "订单已支付，请勿重复支付"),
    ORDER_ITEM_EMPTY(60004, "订单商品项不能为空"),
    ORDER_NOT_CANCELABLE(60005, "当前订单状态不可取消"),
    PAYMENT_DUPLICATE(60006, "支付记录重复"),
    REFUND_AMOUNT_EXCEED(60007, "退款金额超出可退金额"),
    SHIFT_NOT_FOUND(60008, "班次不存在"),
    SHIFT_ACTIVE_EXISTS(60009, "已有进行中的班次"),
    SETTLEMENT_NOT_FOUND(60010, "结算记录不存在"),
    SETTLEMENT_ALREADY_CONFIRMED(60011, "结算已确认，无法重复操作"),

    // ==================== 库存模块 (70xxx) ====================
    STOCK_INSUFFICIENT(70001, "库存不足"),
    PRODUCT_OFF_SHELF(70002, "商品已下架"),

    // ==================== 报表模块 (80xxx) ====================

    // ==================== 营销模块 (90xxx) ====================
    CAMPAIGN_NOT_FOUND(90001, "营销活动不存在"),
    CAMPAIGN_NOT_ACTIVE(90002, "活动未在进行中"),
    CAMPAIGN_END_TIME_ERROR(90003, "活动结束时间设置不合法"),
    CAMPAIGN_MEMBER_LIMIT(90004, "已超出活动参与次数上限"),
    COUPON_NOT_FOUND(90005, "优惠券不存在"),
    COUPON_TEMPLATE_NOT_FOUND(90006, "优惠券模板不存在"),
    COUPON_TEMPLATE_DISABLED(90007, "优惠券模板已停用"),
    COUPON_ALREADY_USED(90008, "优惠券已使用"),
    COUPON_EXPIRED(90009, "优惠券已过期"),
    COUPON_INSUFFICIENT_QUANTITY(90010, "优惠券库存不足"),
    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
