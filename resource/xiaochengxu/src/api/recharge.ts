/**
 * 充值接口。
 * 后端契约：
 * - POST /api/member/recharge（RechargeController.recharge）：body 含 memberId/amount/paymentMethod/operatorId/campaignId
 * - GET  /api/member/{id}/recharge-records（RechargeController.getRechargeRecords）：分页查询
 */
import { get, post } from './request'
import { API_PREFIX } from '@/config'
import type { RechargeRecord, RechargeResult } from '@/types/store'
import type { PageResult } from '@/types/api'

/** 后端 RechargeRecordResponse 原始结构 */
interface RechargeRecordResponse {
  id: number
  rechargeNo: string
  memberId: number
  memberName: string | null
  rechargeAmount: number | string | null
  bonusAmount: number | string | null
  totalAmount: number | string | null
  balanceBefore: number | string | null
  balanceAfter: number | string | null
  paymentMethod: number | null
  paymentMethodLabel: string | null
  status: number | null
  statusLabel: string | null
  paidAt: string | null
  createdAt: string | null
}

/** 前端 PayChannel → 后端 paymentMethod 数值 */
const PAY_CHANNEL_TO_METHOD: Record<'balance' | 'wechat' | 'alipay', number> = {
  wechat: 1,
  alipay: 2,
  balance: 3,
}

function toNum(v: number | string | null | undefined, fallback = 0): number {
  const n = Number(v ?? fallback)
  return Number.isFinite(n) ? n : fallback
}

function mapRechargeRecord(r: RechargeRecordResponse): RechargeRecord {
  const amount = toNum(r.rechargeAmount)
  const bonus = toNum(r.bonusAmount)
  return {
    id: r.id,
    rechargeNo: r.rechargeNo,
    amount,
    bonus,
    actualAmount: toNum(r.totalAmount, amount + bonus),
    payChannel: r.paymentMethodLabel ?? '',
    payChannelText: r.paymentMethodLabel ?? '',
    createdAt: r.createdAt ?? '',
  }
}

/**
 * 充值下单。
 * 后端 RechargeRequest 字段：memberId / amount / paymentMethod / operatorId / campaignId，
 * 没有 idempotentKey，前端调用方仍可传 idempotentKey 但会被忽略。
 * 后端 RechargeController.recharge 返回 RechargeRecordResponse，
 * 这里映射为前端 RechargeResult（balanceAfter 取 balanceAfter 字段，actualAmount 取 totalAmount）。
 */
export function recharge(
  memberId: number,
  payload: {
    amount: number
    payChannel: 'balance' | 'wechat' | 'alipay'
    planId?: number
    couponId?: number
    idempotentKey?: string
  }
): Promise<RechargeResult> {
  return post<RechargeRecordResponse>(`${API_PREFIX}/recharge`, {
    memberId,
    amount: payload.amount,
    paymentMethod: PAY_CHANNEL_TO_METHOD[payload.payChannel] ?? 1,
  }).then((r) => ({
    rechargeId: r.id,
    rechargeNo: r.rechargeNo,
    amount: toNum(r.rechargeAmount),
    bonus: toNum(r.bonusAmount),
    actualAmount: toNum(r.totalAmount),
    balanceAfter: toNum(r.balanceAfter),
    payChannel: r.paymentMethodLabel ?? payload.payChannel,
    paidAt: r.paidAt ?? r.createdAt ?? '',
  }))
}

/** 充值记录分页查询 */
export function getRechargeRecords(
  memberId: number,
  params: { current?: number; size?: number }
): Promise<PageResult<RechargeRecord>> {
  return get<PageResult<RechargeRecordResponse>>(
    `${API_PREFIX}/${memberId}/recharge-records`,
    params as Record<string, unknown>
  ).then((res) => ({
    records: (res.records ?? []).map(mapRechargeRecord),
    total: res.total ?? 0,
    current: res.current ?? 1,
    size: res.size ?? 10,
    pages: res.pages ?? 1,
  }))
}
