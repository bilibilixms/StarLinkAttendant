/**
 * 优惠券类型。
 * 对齐数据库表 coupon / coupon_template。
 */

/** 券类型：1-满减 2-折扣 3-时长 4-礼品 */
export type CouponType = 1 | 2 | 3 | 4

/** 券状态：0-未使用 1-已使用 2-已过期 */
export type CouponStatus = 0 | 1 | 2

/** 适用范围：1-全部 2-网费 3-商品 4-酒店 */
export type CouponScope = 1 | 2 | 3 | 4

export interface Coupon {
  id: number
  templateId: number
  name: string
  type: CouponType
  /** 面额（元），折扣券时为折扣数（如 8.5 表示 8.5 折） */
  value: number
  /** 使用门槛（元），0 表示无门槛 */
  minAmount: number
  scope: CouponScope
  scopeText: string
  status: CouponStatus
  /** 有效期 */
  startTime: string
  endTime: string
  /** 使用说明 */
  description?: string
  /** 是否可领取（领券中心用） */
  receivable?: boolean
  /** 剩余数量（领券中心用） */
  remainCount?: number
}

/** 领券中心的可领券模板 */
export interface CouponTemplate {
  id: number
  name: string
  type: CouponType
  value: number
  minAmount: number
  scope: CouponScope
  scopeText: string
  startTime: string
  endTime: string
  description?: string
  /** 剩余库存 */
  remainCount: number
  /** 已领取张数（用于「已领取」态） */
  receivedCount: number
  /** 每人限领 */
  limitPerMember: number
}

/** 下单时可用券校验结果 */
export interface CouponUsable {
  coupon: Coupon
  usable: boolean
  /** 不可用原因 */
  reason?: string
  /** 可抵扣金额 */
  discountAmount: number
}
