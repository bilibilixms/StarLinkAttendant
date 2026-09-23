/**
 * 上机会话 / 机位 / 区域 类型。
 * 对齐数据库表 session / computer / seat_area。
 */
import type { SessionStatus } from './member'

/** 机位状态：0-空闲 1-使用中 2-已预约 3-维修中 4-离线 */
export type ComputerStatus = 0 | 1 | 2 | 3 | 4

export interface SeatArea {
  id: number
  areaName: string
  /** 区域类型：1-普通区 2-高级区 3-包间 4-电竞酒店 */
  areaType: number
  /** 该区域费率（元/小时） */
  hourlyRate: number
  /** 剩余空位数 */
  freeCount: number
  /** 总机位数 */
  totalCount: number
  description?: string
}

export interface Computer {
  id: number
  computerNo: string
  computerName: string
  areaId: number
  areaName: string
  status: ComputerStatus
  /** 配置描述，如「i7-13700K / RTX 4070 / 2K 165Hz」 */
  spec?: string
  /** 单价（元/小时） */
  hourlyRate: number
  /** 排布坐标，用于座位图 */
  rowIndex?: number
  colIndex?: number
}

/** 座位图：一个区域 + 座位矩阵 */
export interface SeatMap {
  areaId: number
  areaName: string
  cols: number
  seats: Computer[]
}

/** 当前会话详情：GET /api/applet/session/current */
export interface CurrentSession {
  sessionId: number
  sessionNo: string
  storeId: number
  storeName: string
  computerId: number
  computerName: string
  seatNo: string
  areaName: string
  computerSpec?: string
  startTime: string
  endTime?: string
  durationMinutes: number
  billedMinutes: number
  freeMinutes: number
  /** 已产生费用 */
  totalAmount: number
  discountAmount: number
  paidAmount: number
  /** 当前费率（元/小时） */
  hourlyRate: number
  /** 余额 */
  balance: number
  /** 余额可支撑剩余分钟 */
  remainingMinutes: number
  status: SessionStatus
}

/** 扫码得到的机位信息（解析二维码后 / scan-start 的返回） */
export interface ScanStartResult {
  session: CurrentSession
  /** 是否复用了已存在的会话 */
  resumed: boolean
}

export interface ScanStartRequest {
  /** 二维码原始内容 */
  qrContent: string
  computerId?: number
  seatId?: number
  storeId?: number
  /** 机位所在区域，决定计费费率，必须传对 */
  areaId?: number
  computerName?: string
}

export interface SelfEndResult {
  sessionId: number
  sessionNo: string
  durationMinutes: number
  totalAmount: number
  discountAmount: number
  paidAmount: number
  /** 本次结算后余额 */
  balanceAfter: number
  endTime: string
}

/** 上机/消费记录（AP-09 消费记录） */
export interface SessionRecord {
  id: number
  sessionNo: string
  storeName: string
  computerName: string
  areaName: string
  startTime: string
  endTime?: string
  durationMinutes: number
  totalAmount: number
  paidAmount: number
  status: SessionStatus
}
