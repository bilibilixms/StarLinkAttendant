/**
 * 门店 / 充值套餐 / 积分 类型。
 */

export interface Store {
  id: number
  name: string
  address: string
  /** 距离（km） */
  distance: number
  /** 营业状态：1-营业中 0-已打烊 */
  businessStatus: number
  /** 营业时间文案，如「24小时营业」 */
  businessHours: string
  phone?: string
  /** 空闲机位 */
  freeSeats: number
  totalSeats: number
  /** 门店封面（本地资源 key） */
  cover: string
  tags: string[]
  longitude?: number
  latitude?: number
}

/* ==================== 充值 ==================== */

export interface RechargePlan {
  id: number
  /** 充值金额（元） */
  amount: number
  /** 赠送金额（元） */
  bonus: number
  /** 赠送积分 */
  bonusPoints?: number
  /** 实际到账 = amount + bonus */
  actualAmount: number
  /** 标签，如「最划算」「热门」 */
  tag?: string
  /** 是否推荐（高亮） */
  recommend?: boolean
  description?: string
}

export interface RechargeRequest {
  planId?: number
  /** 自定义充值金额，与 planId 二选一 */
  amount?: number
  payChannel: 'balance' | 'wechat' | 'alipay'
  /** 幂等键 */
  idempotentKey: string
  /** 使用的充值活动券 id */
  couponId?: number
}

export interface RechargeResult {
  rechargeId: number
  rechargeNo: string
  amount: number
  bonus: number
  actualAmount: number
  /** 到账后余额 */
  balanceAfter: number
  payChannel: string
  paidAt: string
}

export interface RechargeRecord {
  id: number
  rechargeNo: string
  amount: number
  bonus: number
  actualAmount: number
  payChannel: string
  payChannelText: string
  createdAt: string
}

/* ==================== 积分 ==================== */

/** 积分变动类型：1-消费获得 2-活动获得 3-兑换消耗 4-过期扣减 */
export type PointsChangeType = 1 | 2 | 3 | 4

export interface PointsRecord {
  id: number
  type: PointsChangeType
  /** 变动值，正数为获得，负数为消耗 */
  changePoints: number
  /** 变动后余额 */
  balanceAfter: number
  title: string
  description?: string
  createdAt: string
}

/** 积分兑换商品 */
export interface PointsGoods {
  id: number
  name: string
  /** 所需积分 */
  points: number
  cover: string
  /** 库存 */
  stock: number
  description?: string
}
