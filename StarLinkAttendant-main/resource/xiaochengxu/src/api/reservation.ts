/**
 * 预约订座 / 电竞酒店 接口。
 */
import { get, post } from './request'
import { API_PREFIX } from '@/config'
import type {
  HotelRoomType,
  Reservation,
  ReservationCreateRequest,
  ReservationCreateResult,
} from '@/types/reservation'
import type { PageResult } from '@/types/api'

/** 我的预约列表 */
export function getReservations(params: {
  current?: number
  size?: number
  status?: number
}): Promise<PageResult<Reservation>> {
  return get<PageResult<Reservation>>(`${API_PREFIX}/reservations`, params as Record<string, unknown>)
}

/** 创建预约（Demo 阶段 0 元预约） */
export function createReservation(payload: ReservationCreateRequest): Promise<ReservationCreateResult> {
  return post<ReservationCreateResult>(
    `${API_PREFIX}/reservations`,
    payload as unknown as Record<string, unknown>
  )
}

/** 取消预约 */
export function cancelReservation(id: number): Promise<void> {
  return post<void>(`${API_PREFIX}/reservations/${id}/cancel`)
}

/** 电竞酒店房型列表 */
export function getHotelRoomTypes(params?: { storeId?: number }): Promise<HotelRoomType[]> {
  return get<HotelRoomType[]>(`${API_PREFIX}/hotel/room-types`, params as Record<string, unknown>)
}
