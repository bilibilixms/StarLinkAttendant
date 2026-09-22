/**
 * 充值接口。
 * 后端契约：
 * - POST /api/member/recharge（RechargeController.recharge）：body 含 memberId/amount/paymentMethod/operatorId/campaignId
 * - GET  /api/member/{id}/recharge-records（RechargeController.getRechargeRecords）：分页查询
 */
import { get, post } from './request'
import { API_PREFIX } from '@/config'
import type { RechargePreview, RechargeRecord, RechargeResult } from '@/types/store'
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
 * 后端 RechargeRequest 字段：memberId / amount / paymentMethod / operatorId / campaignId / idempotentKey。
 * <p>
 * `idempotentKey` <b>原样透传</b>：本层不生成、不覆盖 —— 由调用方（充值页）为「同一个逻辑充值请求」
 * 生成一个键并在重试时复用，服务端据此保证只入账一次。
 * 后端 RechargeController.recharge 返回 RechargeRecordResponse，
 * 这里映射为前端 RechargeResult（bonusAmount 为活动赠送、totalAmount 为实际到账）。
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
    // 幂等键必须透传：后端据此生成确定性单号 + 唯一索引，保证重复提交不重复入账
    ...(payload.idempotentKey ? { idempotentKey: payload.idempotentKey } : {}),
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

/**
 * 充值试算（预览）：由后端按当前生效的充值活动阶梯计算「实付 / 赠送 / 实际到账」。
 * <p>
 * 仅用于提交前展示，前端<b>不</b>自行复制活动规则；最终入账由后端在充值接口重新计算。
 */
export function previewRecharge(memberId: number, amount: number): Promise<RechargePreview> {
  return get<RechargePreview>(`${API_PREFIX}/recharge/preview`, { memberId, amount })
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
