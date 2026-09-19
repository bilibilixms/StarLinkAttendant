/** 订单 */
export interface OrderItem {
  id: number
  orderNo: string
  memberId: number | null
  memberName: string | null
  sessionId: number | null
  orderType: number
  orderTypeLabel: string
  totalAmount: number
  discountAmount: number
  payableAmount: number
  paidAmount: number
  status: number
  statusLabel: string
  remark: string | null
  operatorId: number | null
  operatorName: string | null
  paidAt: string | null
  createdAt: string
  updatedAt: string
}

/** 订单详情 */
export interface OrderDetail extends OrderItem {
  changeAmount: number | null
  items: OrderDetailItem[]
  payment: PaymentRecordItem | null
  refunds: RefundRecordItem[]
}

/** 订单明细行 */
export interface OrderDetailItem {
  id: number
  orderId: number
  productId: number | null
  productName: string
  itemType: number
  itemTypeLabel: string
  unitPrice: number
  quantity: number
  subtotal: number
  discount: number
}

/** 支付记录 */
export interface PaymentRecordItem {
  id: number
  paymentNo: string
  orderId: number
  memberId: number | null
  paymentMethod: number
  paymentMethodLabel: string
  tradeNo: string | null
  totalAmount: number
  refundAmount: number
  paymentStatus: number
  paymentStatusLabel: string
  paidAt: string
  operatorId: number | null
  createdAt: string
}

/** 退款记录 */
export interface RefundRecordItem {
  id: number
  refundNo: string
  orderId: number
  orderNo: string | null
  paymentId: number | null
  memberId: number | null
  refundAmount: number
  refundType: number
  refundTypeLabel: string
  refundReason: string | null
  refundMethod: number
  refundMethodLabel: string
  status: number
  statusLabel: string
  auditBy: number | null
  auditAt: string | null
  operatorId: number | null
  createdAt: string
}

/** 班次 */
export interface ShiftItem {
  id: number
  employeeId: number
  shiftNo: string
  openAt: string
  closeAt: string | null
  openingBalance: number
  cashIncome: number
  cashExpenditure: number
  cashExpected: number | null
  cashActual: number | null
  cashDiff: number | null
  totalIncome: number
  orderCount: number
  status: number
  statusLabel: string
  auditBy: number | null
  createdAt: string
}

/** 日结 */
export interface SettlementItem {
  id: number
  settleDate: string
  settleNo: string
  totalRevenue: number
  onlineRevenue: number
  productRevenue: number
  rechargeRevenue: number
  totalRecharge: number
  totalRefund: number
  totalOrders: number
  totalSessions: number
  peakConcurrent: number
  avgOccupancyRate: number
  cashAmount: number
  balanceAmount: number
  status: number
  statusLabel: string
  confirmBy: number | null
  confirmedAt: string | null
  remark: string | null
  createdAt: string
}

/** 订单查询参数 */
export interface OrderQuery {
  orderNo?: string
  status?: number | null
  statusList?: number[]
  orderType?: number | null
  startTime?: string
  endTime?: string
}

/** 退款查询参数 */
export interface RefundQuery {
  refundNo?: string
  status?: number | null
  startTime?: string
  endTime?: string
}

/** 班次查询参数 */
export interface ShiftQuery {
  employeeId?: number | null
  status?: number | null
}

/** 日结查询参数 */
export interface SettlementQuery {
  startTime?: string
  endTime?: string
  status?: number | null
}

/** 创建订单请求 */
export interface OrderCreateRequest {
  memberId?: number | null
  orderType: number
  remark?: string
  items: OrderItemRequest[]
}

export interface OrderItemRequest {
  productId: number
  quantity: number
}

/** 支付请求 */
export interface PaymentRequest {
  paymentMethod: number
  paidAmount?: number
  idempotentKey?: string
}

/** 退款请求 */
export interface RefundRequest {
  refundAmount: number
  refundReason?: string
  refundMethod?: number
}

/** 开班请求 */
export interface ShiftStartRequest {
  openingBalance: number
}

/** 结班请求 */
export interface ShiftEndRequest {
  cashActual: number
}

/** 购物车商品（前端用） */
export interface CartItem {
  productId: number
  productName: string
  unitPrice: number
  quantity: number
  subtotal: number
}
