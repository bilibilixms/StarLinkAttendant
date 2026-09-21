package com.starlink.member.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值试算（预览）结果。
 * <p>
 * 供前端在<b>提交前</b>展示「实付 / 赠送 / 实际到账」，避免前端自行复制一套活动阶梯计算逻辑。
 * <p>
 * <b>仅用于展示</b>：最终入账金额一律由 {@code RechargeService.recharge()} 在服务端
 * 重新计算，前端传入的预览值不参与入账。
 * <p>
 * 字段与 {@code member_recharge} 的金额语义严格一致：
 * <ul>
 *   <li>{@code rechargeAmount} —— 实付充值金额；</li>
 *   <li>{@code bonusAmount} —— 活动赠送金额；</li>
 *   <li>{@code totalAmount} —— 实际到账 = 实付 + 赠送；</li>
 *   <li>{@code campaignId} —— 实际命中赠送的活动 ID；未命中任何档位时为 null。</li>
 * </ul>
 * 未复用 {@code RechargeRecordResponse}：后者是「已发生的充值单据」，
 * 含 id/单号/支付方式/状态等对试算无意义的字段，复用会造成契约歧义。
 */
@Data
public class RechargePreviewResponse {

    private Long memberId;

    private BigDecimal rechargeAmount;

    private BigDecimal bonusAmount;

    private BigDecimal totalAmount;

    private Long campaignId;
}
