/**
 * 门店 / 意见反馈 / 客服 / 收藏 接口。
 */
import { get, post } from './request'
import { API_PREFIX } from '@/config'
import type { SeatArea, SeatMap } from '@/types/session'
import type { Product } from '@/types/product'
import type { Coupon } from '@/types/coupon'
import type { Post } from '@/types/community'
import type { Store } from '@/types/store'

/** 门店列表（附近门店 / 搜索门店） */
export function getStores(params?: { keyword?: string }): Promise<Store[]> {
  return get<Store[]>(`${API_PREFIX}/stores`, params as Record<string, unknown>)
}

/** 门店下的区域（普通区 / 高级区 / 包间） */
export function getSeatAreas(storeId: number): Promise<SeatArea[]> {
  return get<SeatArea[]>(`${API_PREFIX}/computers/areas`, { storeId })
}

/** 机位座位图 */
export function getSeatMap(storeId: number, areaId: number): Promise<SeatMap> {
  return get<SeatMap>(`${API_PREFIX}/computers/available`, { storeId, areaId })
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
