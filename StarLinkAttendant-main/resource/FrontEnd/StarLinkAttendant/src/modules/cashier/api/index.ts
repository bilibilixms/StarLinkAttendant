import request from '@/common/api/request'
import type { PageRequest, PageResult } from '@/common/api/types'
import type {
  OrderItem, OrderDetail, PaymentRecordItem, RefundRecordItem,
  ShiftItem, SettlementItem,
  OrderQuery, RefundQuery, ShiftQuery, SettlementQuery,
  OrderCreateRequest, PaymentRequest, RefundRequest,
  ShiftStartRequest, ShiftEndRequest,
} from '../types'

// ========== 订单管理 ==========
export function getOrderList(params: PageRequest & OrderQuery) {
  return request.get<any, { data: PageResult<OrderItem> }>('/api/cashier/orders', { params })
}

export function getOrderDetail(id: number) {
  return request.get<any, { data: OrderDetail }>(`/api/cashier/orders/${id}`)
}

export function createOrder(data: OrderCreateRequest) {
  return request.post<any, { data: OrderItem }>('/api/cashier/orders', data)
}

export function payOrder(id: number, data: PaymentRequest) {
  return request.post<any, { data: PaymentRecordItem }>(`/api/cashier/orders/${id}/pay`, data)
}

export function refundOrder(id: number, data: RefundRequest) {
  return request.post<any, { data: RefundRecordItem }>(`/api/cashier/orders/${id}/refund`, data)
}

export function cancelOrder(id: number) {
  return request.post<any, { data: OrderItem }>(`/api/cashier/orders/${id}/cancel`)
}

// ========== 退款管理 ==========
export function getRefundList(params: PageRequest & RefundQuery) {
  return request.get<any, { data: PageResult<RefundRecordItem> }>('/api/cashier/orders/refunds', { params })
}

// ========== 班次管理 ==========
export function startShift(data: ShiftStartRequest) {
  return request.post<any, { data: ShiftItem }>('/api/cashier/shift/start', data)
}

export function endShift(data: ShiftEndRequest) {
  return request.post<any, { data: ShiftItem }>('/api/cashier/shift/end', data)
}

export function getShiftList(params: PageRequest & ShiftQuery) {
  return request.get<any, { data: PageResult<ShiftItem> }>('/api/cashier/shift/records', { params })
}

export function getShiftDetail(id: number) {
  return request.get<any, { data: ShiftItem }>(`/api/cashier/shift/${id}`)
}

// ========== 日结管理 ==========
export function getSettlementList(params: PageRequest & SettlementQuery) {
  return request.get<any, { data: PageResult<SettlementItem> }>('/api/cashier/settlement/daily', { params })
}

export function getSettlementDetail(id: number) {
  return request.get<any, { data: SettlementItem }>(`/api/cashier/settlement/${id}`)
}

export function generateSettlement(date: string) {
  return request.post<any, { data: SettlementItem }>('/api/cashier/settlement/generate', null, { params: { settleDate: date } })
}

export function confirmSettlement(id: number) {
  return request.post<any, { data: SettlementItem }>(`/api/cashier/settlement/${id}/confirm`)
}
