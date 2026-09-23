/**
 * 积分接口。
 * 后端契约：GET /api/member/{id}/points-records（PointsController.getPointsRecords）。
 * 后端暂未提供积分商品/兑换接口，相关 mock 路由也已移除。
 */
import { get } from './request'
import { API_PREFIX } from '@/config'
import type { PointsRecord } from '@/types/store'
import type { PageResult } from '@/types/api'

/** 后端 PointsRecordResponse 原始结构 */
interface PointsRecordResponse {
  id: number
  memberId: number
  points: number | null
  balanceBefore: number | null
  balanceAfter: number | null
  bizType: number | null
  bizTypeLabel: string | null
  bizId: number | null
  remark: string | null
  expireAt: string | null
  createdAt: string | null
}

function toInt(v: unknown, fallback = 0): number {
  const n = Number(v ?? fallback)
  return Number.isFinite(n) ? Math.trunc(n) : fallback
}

function mapPointsRecord(r: PointsRecordResponse): PointsRecord {
  // 后端 bizType 与前端 PointsChangeType 对齐：1 获得 / 2 活动 / 3 兑换 / 4 过期
  const bizType = toInt(r.bizType, 1) as 1 | 2 | 3 | 4
  const points = toInt(r.points)
  // 后端只返回单次变动值（无正负），按 bizType 决定符号
  const sign = bizType === 3 || bizType === 4 ? -1 : 1
  return {
    id: r.id,
    type: bizType,
    changePoints: sign * Math.abs(points),
    balanceAfter: toInt(r.balanceAfter),
    title: r.bizTypeLabel ?? '积分变动',
    description: r.remark ?? undefined,
    createdAt: r.createdAt ?? '',
  }
}

/** 积分明细分页查询 */
export function getPointsRecords(
  memberId: number,
  params: { current?: number; size?: number; type?: number }
): Promise<PageResult<PointsRecord>> {
  return get<PageResult<PointsRecordResponse>>(
    `${API_PREFIX}/${memberId}/points-records`,
    params as Record<string, unknown>
  ).then((res) => ({
    records: (res.records ?? []).map(mapPointsRecord),
    total: res.total ?? 0,
    current: res.current ?? 1,
    size: res.size ?? 15,
    pages: res.pages ?? 1,
  }))
}
