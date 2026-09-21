/**
 * 订单接口：下单 / 列表 / 详情 / 取消 / 支付 / 消费记录。
 */
import { get, post } from './request'
import { API_PREFIX } from '@/config'
import type { ConsumeRecord, CreateOrderRequest, CreateOrderResult, Order, OrderTab } from '@/types/order'
import type { PageResult } from '@/types/api'

/** 我的订单（分页 + Tab 筛选） */
export function getOrders(params: {
  tab?: OrderTab
  current?: number
  size?: number
}): Promise<PageResult<Order>> {
  return get<PageResult<Order>>(`${API_PREFIX}/orders`, params as Record<string, unknown>)
}

/** 订单详情 */
export function getOrderDetail(id: number): Promise<Order> {
  return get<Order>(`${API_PREFIX}/orders/${id}`)
}

/**
 * 点单下单。
 * 必须携带 idempotentKey，防止重复点击产生多笔订单（需求 §9 资金操作幂等）。
 */
export function createOrder(payload: CreateOrderRequest): Promise<CreateOrderResult> {
  return post<CreateOrderResult>(`${API_PREFIX}/orders`, payload as unknown as Record<string, unknown>)
}

/** 支付订单（Demo 阶段后端直接标记成功，后续替换为微信支付） */
export function payOrder(id: number, payChannel: string): Promise<{ orderId: number; paidAmount: number; status: number }> {
  return post<{ orderId: number; paidAmount: number; status: number }>(
    `${API_PREFIX}/orders/${id}/pay`,
    { payChannel }
  )
}

/** 取消订单 */
export function cancelOrder(id: number): Promise<void> {
  return post<void>(`${API_PREFIX}/orders/${id}/cancel`)
}

/** 申请退款 */
export function refundOrder(id: number, reason: string): Promise<void> {
  return post<void>(`${API_PREFIX}/orders/${id}/refund`, { reason })
}

/** 消费记录（上机 / 点单 / 充值 三类流水聚合） */
export function getConsumeRecords(params: {
  current?: number
  size?: number
  type?: string
}): Promise<PageResult<ConsumeRecord>> {
  return get<PageResult<ConsumeRecord>>(`${API_PREFIX}/consume-records`, params as Record<string, unknown>)
}
