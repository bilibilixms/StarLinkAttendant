package com.starlink.common.constant;

/**
 * 全局常量定义。
 * <p>
 * 与 API 规范中的枚举值保持一致。各模块可定义自己的模块级常量类。
 *
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    // ==================== 系统常量 ====================

    /** 应用名称 */
    public static final String APP_NAME = "StarLinkAttendant";

    /** API 基础路径前缀 */
    public static final String API_PREFIX = "/api";

    /** 默认分页大小 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 最大分页大小 */
    public static final int MAX_PAGE_SIZE = 100;

    // ==================== 性别 ====================
    public static final int GENDER_UNKNOWN = 0;
    public static final int GENDER_MALE = 1;
    public static final int GENDER_FEMALE = 2;

    // ==================== 会员状态 ====================
    public static final int MEMBER_STATUS_NORMAL = 0;
    public static final int MEMBER_STATUS_FROZEN = 1;
    public static final int MEMBER_STATUS_CANCELLED = 2;

    // ==================== 机位状态 ====================
    public static final int SEAT_STATUS_IDLE = 0;
    public static final int SEAT_STATUS_IN_USE = 1;
    public static final int SEAT_STATUS_LOCKED = 2;
    public static final int SEAT_STATUS_MAINTENANCE = 3;

    // ==================== 会话状态 ====================
    public static final int SESSION_STATUS_ACTIVE = 0;
    public static final int SESSION_STATUS_PAUSED = 1;
    public static final int SESSION_STATUS_ENDED = 2;

    // ==================== 订单状态（与 orders.status 一致） ====================
    public static final int ORDER_STATUS_PENDING = 0;          // 待支付
    public static final int ORDER_STATUS_PAID = 1;             // 已支付
    public static final int ORDER_STATUS_PARTIAL_REFUND = 2;   // 部分退款
    public static final int ORDER_STATUS_REFUNDED = 3;         // 已退款
    public static final int ORDER_STATUS_CANCELLED = 4;        // 已取消

    // ==================== 订单类型 / 明细类型（orders / order_item） ====================
    /** 订单类型：1-商品销售 2-上机结算 3-充值 4-套餐 */
    public static final int ORDER_TYPE_PRODUCT = 1;
    /** 明细类型：1-商品 2-上机时长 3-包时段 4-套餐 */
    public static final int ITEM_TYPE_PRODUCT = 1;

    // ==================== 支付方式 ====================
    public static final int PAY_TYPE_CASH = 1;
    public static final int PAY_TYPE_WECHAT = 2;
    public static final int PAY_TYPE_ALIPAY = 3;
    public static final int PAY_TYPE_BALANCE = 4;
    /** PAY_METHOD_* 为收银模块语义别名，取值与 PAY_TYPE_* 一致 */
    public static final int PAY_METHOD_CASH = 1;
    public static final int PAY_METHOD_BALANCE = 4;

    // ==================== 支付状态（payment_record.payment_status） ====================
    public static final int PAY_STATUS_PENDING = 0;   // 待支付
    public static final int PAY_STATUS_SUCCESS = 1;   // 支付成功
    public static final int PAY_STATUS_FAILED = 2;    // 支付失败
    public static final int PAY_STATUS_REFUNDED = 3;  // 已退款

    // ==================== 退款（refund_record） ====================
    /** 退款类型：1-全额退款 2-部分退款 */
    public static final int REFUND_TYPE_FULL = 1;
    public static final int REFUND_TYPE_PARTIAL = 2;
    /** 退款方式：复用支付方式编号 1-现金 2-微信 3-支付宝 4-退余额 */
    public static final int REFUND_METHOD_CASH = 1;
    public static final int REFUND_METHOD_BALANCE = 4;
    /** 退款状态：0-待审核 1-已审核 2-已完成 3-已拒绝 */
    public static final int REFUND_STATUS_PENDING = 0;
    public static final int REFUND_STATUS_APPROVED = 1;
    public static final int REFUND_STATUS_COMPLETED = 2;
    public static final int REFUND_STATUS_REJECTED = 3;

    // ==================== 班次状态（cashier_shift.status） ====================
    public static final int SHIFT_STATUS_ACTIVE = 0;   // 进行中
    public static final int SHIFT_STATUS_CLOSED = 1;   // 已结班
    public static final int SHIFT_STATUS_AUDITED = 2;  // 已审核

    // ==================== 日结状态（daily_settlement.status） ====================
    public static final int SETTLEMENT_STATUS_PENDING = 0;   // 待确认
    public static final int SETTLEMENT_STATUS_CONFIRMED = 1; // 已确认
    public static final int SETTLEMENT_STATUS_ARCHIVED = 2;  // 已归档

    // ==================== 余额变动业务类型（member_balance_log.biz_type） ====================
    public static final int BALANCE_BIZ_CONSUME = 1;   // 消费扣减
    public static final int BALANCE_BIZ_REFUND = 2;    // 退款回充
    public static final int BALANCE_BIZ_RECHARGE = 3;  // 充值
    public static final int BALANCE_BIZ_ADJUST = 4;    // 手动调整

    // ==================== 成长值业务类型（member_growth_log.biz_type） ====================
    public static final int GROWTH_BIZ_CONSUME = 1;   // 收银直接消费
    public static final int GROWTH_BIZ_RECHARGE = 2;  // 余额充值（按实付）

    // ==================== 库存操作类型 ====================
    public static final int INVENTORY_IN = 1;
    public static final int INVENTORY_OUT = 2;
    public static final int INVENTORY_SURPLUS = 3;
    public static final int INVENTORY_LOSS = 4;

    // ==================== Redis Key 前缀 ====================
    public static final String REDIS_SEAT_STATUS = "seat:status";
    public static final String REDIS_MEMBER_INFO = "member:info:";
    public static final String REDIS_TARIFF_PLAN = "tariff:plan:";
    public static final String REDIS_CAPTCHA = "captcha:";
    public static final String REDIS_LOGIN_LIMIT = "login:limit:";
    public static final String REDIS_RANKING_CONSUME = "ranking:consume";
    public static final String REDIS_RANKING_PRODUCT = "ranking:product";

    // ==================== 文件上传 ====================
    /** 单文件最大大小：10 MB */
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /** 允许的图片格式 */
    public static final String[] ALLOWED_IMAGE_TYPES = {"jpg", "jpeg", "png", "gif"};

    /** 允许的文档格式 */
    public static final String[] ALLOWED_DOC_TYPES = {"xlsx", "pdf"};
}
