/**
 * 预约订座 / 预订酒店 类型。
 * 对齐数据库表 reservation。
 */

/** 预约状态：0-待使用 1-已使用 2-已取消 3-已过期 4-已到店 */
export type ReservationStatus = 0 | 1 | 2 | 3 | 4

/** 预约类型：1-网咖机位 2-电竞酒店房间 */
export type ReservationType = 1 | 2

export interface Reservation {
  id: number
  reservationNo: string
  type: ReservationType
  storeId: number
  storeName: string
  areaId?: number
  areaName?: string
  computerId?: number
  computerName?: string
  /** 酒店房型 */
  roomTypeName?: string
  /** 预约开始时间 */
  startTime: string
  /** 预约结束时间 */
  endTime: string
  /** 预计时长（分钟） */
  durationMinutes: number
  /** 到店人数 */
  peopleCount: number
  contactPhone: string
  /** 保证金金额，0 元预约时为 0 */
  depositAmount: number
  status: ReservationStatus
  remark?: string
  createdAt: string
}

export interface ReservationCreateRequest {
  type: ReservationType
  storeId: number
  areaId?: number
  computerIds?: number[]
  roomTypeId?: number
  startTime: string
  endTime: string
  peopleCount: number
  contactPhone: string
  remark?: string
}

export interface ReservationCreateResult {
  reservationId: number
  reservationNo: string
  status: ReservationStatus
  startTime: string
  endTime: string
  depositAmount: number
  /** 0 元预约时提示文案 */
  message: string
}

/** 酒店房型 */
export interface HotelRoomType {
  id: number
  name: string
  /** 可住人数 */
  capacity: number
  /** 价格（元/晚） */
  price: number
  /** 会员价 */
  memberPrice: number
  /** 剩余房量 */
  availableCount: number
  /** 房型图（本地资源 key） */
  cover?: string
  tags: string[]
}
