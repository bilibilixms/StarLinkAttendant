/**
 * 订单类型。
 * 对齐数据库表 orders / order_item。
 */

/**
 * 订单状态：0-待支付 1-已支付 2-部分退款 3-已退款 4-已取消
 * 前端筛选 Tab 会再做一层「进行中」的聚合（已支付且未完成配送）。
 */
export type OrderStatus = 0 | 1 | 2 | 3 | 4

/** 订单类型：1-商品销售 2-上机结算 3-充值 4-套餐 */
export type OrderType = 1 | 2 | 3 | 4

/** 前端订单筛选 Tab */
export type OrderTab = 'all' | 'unpaid' | 'processing' | 'finished' | 'refund'

export interface OrderItem {
  id: number
  productId: number
  productName: string
  cover: string
  spec?: string
  price: number
  quantity: number
  /** 小计 */
  amount: number
}

export interface Order {
  id: number
  orderNo: string
  orderType: OrderType
  memberId: number | null
  storeName: string
  /** 配送机位，点单场景用 */
  seatNo?: string
  totalAmount: number
  discountAmount: number
  payableAmount: number
  paidAmount: number
  status: OrderStatus
  /** 进行中/已完成 附加状态文案，如「吧台备餐中」 */
  statusText?: string
  items: OrderItem[]
  remark?: string
  paidAt?: string
  createdAt: string
}

export interface CreateOrderItem {
  productId: number
  quantity: number
  /** 下单快照：便于 mock 池缺失时兜底结算（真实后端下单时可不传） */
  price?: number
  name?: string
  cover?: string
  spec?: string
}

export interface CreateOrderRequest {
  items: CreateOrderItem[]
  /** 使用的优惠券 id */
  couponId?: number
  /** 配送机位 */
  seatNo?: string
  remark?: string
  /** 幂等键：防止重复下单 */
  idempotentKey: string
}

export interface CreateOrderResult {
  orderId: number
  orderNo: string
  payableAmount: number
  status: OrderStatus
  createdAt: string
}

/** 消费记录（AP-09）：上机 / 订单 / 充值 三类流水聚合 */
export interface ConsumeRecord {
  id: number
  type: 'session' | 'order' | 'recharge'
  title: string
  subtitle: string
  /** 金额，充值为正、消费为负 */
  amount: number
  createdAt: string
  orderNo?: string
}
