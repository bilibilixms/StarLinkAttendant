package com.starlink.member.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值请求。
 * <p>
 * 金额语义（本轮统一，与 member_recharge 列注释一致）：
 * <ul>
 *   <li>{@code amount} —— <b>实付充值金额</b>；</li>
 *   <li>赠送金额由<b>服务端</b>按充值活动阶梯规则计算，客户端<b>不可</b>传入；</li>
 *   <li>实际到账 = amount + 服务端计算的赠送金额。</li>
 * </ul>
 */
@Data
public class RechargeRequest {

    /** 单笔充值金额上限（元）。充值与充值试算共用同一约束，避免两者口径漂移。 */
    public static final long MAX_AMOUNT = 1000000L;

    @NotNull(message = "会员ID不能为空")
    private Long memberId;

    @NotNull(message = "充值金额不能为空")
    @Min(value = 1, message = "充值金额最小为1元")
    @Max(value = MAX_AMOUNT, message = "单笔充值金额上限为1000000元")
    private BigDecimal amount;

    @NotNull(message = "支付方式不能为空")
    private Byte paymentMethod;

    private Long operatorId;

    private Long campaignId;

    /**
     * 客户端幂等键（可选，建议必传）。
     * <p>
     * 同一个逻辑充值请求重复提交（重复点击 / 超时重试）时必须携带同一个值：
     * 服务端据此生成确定性的充值单号，由 {@code member_recharge.uk_recharge_no}
     * 唯一索引保证「同一请求只入账一次」，不依赖前端按钮防重复点击。
     * <p>
     * 为空时退化为「每次调用都是新充值」（无幂等保护）。
     */
    @Size(max = 64, message = "幂等键长度不能超过64")
    private String idempotentKey;
}
