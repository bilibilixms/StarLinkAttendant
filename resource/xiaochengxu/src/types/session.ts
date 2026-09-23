/**
 * 上机会话 / 机位 / 区域 类型。
 * 对齐数据库表 session / computer / seat_area。
 */
import type { SessionStatus } from './member'

/** 机位状态：0-空闲 1-使用中 2-锁定 3-维修中 4-关机（与后端 SeatService 及 Web 管理端一致） */
export type ComputerStatus = 0 | 1 | 2 | 3 | 4

export interface SeatArea {
  id: number
  areaName: string
  /** 区域标识色（十六进制） */
  areaColor?: string
  /** 区域类型：1-普通区 2-高级区 3-包间 4-电竞酒店（后端暂无，mock 兼容） */
  areaType?: number
  /** 该区域费率（元/小时）；后端按费率方案计价，区域级费率可能为空 */
  hourlyRate?: number
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
  /** 状态标签（空闲/使用中/锁定/维修/关机） */
  statusLabel?: string
  /** 座位标签（靠窗/双人/电竞椅） */
  seatLabel?: string
  /** 设备类型：1-普通 PC 2-电竞 PC 3-包间 4-PS5/主机 */
  deviceType?: number
  /** 配置描述，如「i7-13700K / RTX 4070 / 2K 165Hz」（mock 或由 cpu/gpu/screenSize 拼装） */
  spec?: string
  cpu?: string
  gpu?: string
  memory?: string
  screenSize?: string
  /** 单价（元/小时）；后端按费率方案计价，机位级费率可能为空 */
  hourlyRate?: number
  /** 排布坐标，用于座位图 */
  rowIndex?: number
  colIndex?: number
  posX?: number
  posY?: number
  sortOrder?: number
}

/** 座位图：一个区域 + 座位矩阵 */
export interface SeatMap {
  areaId: number
  areaName: string
  areaColor?: string
  /** 列数（mock 兼容，后端不返回） */
  cols?: number
  freeCount?: number
  totalCount?: number
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
  /** 续费小时单价（元/小时）；包时方案为 0 */
  hourlyRate: number
  /** 首小时价格（不足 1 小时按此价格收取）；包时方案即包时价 */
  firstHourPrice: number
  /** 账户当前余额（未扣本次费用） */
  balance: number
  /** 此刻下机扣费后的预计余额 */
  balanceAfter: number
  /** 扣费后余额可支撑剩余分钟（后端按小时阶梯计算）；-1 为不限时 */
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
