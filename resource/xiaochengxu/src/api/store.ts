/**
 * 门店 / 意见反馈 / 客服 / 收藏 接口。
 */
import { get, post } from './request'
import { API_PREFIX } from '@/config'
import type { Computer, SeatArea, SeatMap } from '@/types/session'
import type { Product } from '@/types/product'
import type { Coupon } from '@/types/coupon'
import type { Post } from '@/types/community'
import type { Store } from '@/types/store'

/** 门店列表（附近门店 / 搜索门店） */
export function getStores(params?: { keyword?: string }): Promise<Store[]> {
  return get<Store[]>(`${API_PREFIX}/stores`, params as Record<string, unknown>)
}

/* ---------- 后端「会员端机位查询」响应字段（MemberSeatAreaResponse / MemberComputerResponse） ---------- */

interface MemberSeatAreaItem {
  id: number
  areaName: string
  areaColor?: string
  sortOrder?: number
  freeCount?: number
  totalCount?: number
  computers?: MemberComputerItem[]
}

interface MemberComputerItem {
  id: number
  areaId: number
  areaName: string
  computerNo: string
  computerName: string
  seatLabel?: string
  deviceType?: number
  cpu?: string
  gpu?: string
  memory?: string
  screenSize?: string
  status: number
  statusLabel?: string
  sortOrder?: number
  posX?: number
  posY?: number
}

/** 后端机位 → 前端 Computer 形状 */
function toComputer(c: MemberComputerItem): Computer {
  const parts = [c.cpu, c.gpu, c.screenSize].filter(Boolean)
  return {
    id: c.id,
    computerNo: c.computerNo,
    computerName: c.computerName || c.computerNo,
    areaId: c.areaId,
    areaName: c.areaName,
    status: c.status as Computer['status'],
    statusLabel: c.statusLabel,
    seatLabel: c.seatLabel,
    deviceType: c.deviceType,
    cpu: c.cpu,
    gpu: c.gpu,
    memory: c.memory,
    screenSize: c.screenSize,
    spec: parts.length ? parts.join(' / ') : undefined,
    posX: c.posX,
    posY: c.posY,
    sortOrder: c.sortOrder,
  }
}

/** 门店下的区域（普通区 / 高级区 / 包间）— 真实后端 GET /api/member/seats/areas */
export async function getSeatAreas(storeId: number): Promise<SeatArea[]> {
  const list = await get<MemberSeatAreaItem[]>(`${API_PREFIX}/seats/areas`)
  return list.map((a) => ({
    id: a.id,
    areaName: a.areaName,
    areaColor: a.areaColor,
    freeCount: a.freeCount ?? 0,
    totalCount: a.totalCount ?? 0,
  }))
}

/** 机位座位图 — 真实后端 GET /api/member/seats/map?areaId= */
export async function getSeatMap(storeId: number, areaId: number): Promise<SeatMap> {
  const area = await get<MemberSeatAreaItem>(`${API_PREFIX}/seats/map`, { areaId })
  return {
    areaId: area.id,
    areaName: area.areaName,
    areaColor: area.areaColor,
    freeCount: area.freeCount,
    totalCount: area.totalCount,
    seats: (area.computers ?? []).map(toComputer),
  }
}

/** 意见反馈 */
export function submitFeedback(payload: {
  type: string
  content: string
  contact?: string
  images?: string[]
}): Promise<{ feedbackId: number }> {
  return post<{ feedbackId: number }>(`${API_PREFIX}/feedback`, payload as unknown as Record<string, unknown>)
}

/** 我的收藏 */
export function getFavorites(params?: {
  type?: 'post' | 'product' | 'store'
}): Promise<{ posts: Post[]; products: Product[] }> {
  return get<{ posts: Post[]; products: Product[] }>(
    `${API_PREFIX}/favorites`,
    params as Record<string, unknown>
  )
}

/** 首页活动专区（超级福利·活动专区 4 个小卡片） */
export function getHomeActivities(): Promise<
  Array<{ key: string; title: string; subtitle: string; action: string; theme: string }>
> {
  return get<Array<{ key: string; title: string; subtitle: string; action: string; theme: string }>>(
    `${API_PREFIX}/home/activities`
  )
}

/** 首页 Banner（轮播） */
export function getHomeBanners(): Promise<
  Array<{ id: number; title: string; subtitle: string; buttonText: string; theme: string }>
> {
  return get<Array<{ id: number; title: string; subtitle: string; buttonText: string; theme: string }>>(
    `${API_PREFIX}/home/banners`
  )
}

/** 可领取的券（首页/我的页面角标用） */
export function getReceivableCoupons(): Promise<Coupon[]> {
  return get<Coupon[]>(`${API_PREFIX}/coupons/receivable`)
}
