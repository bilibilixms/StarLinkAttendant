/** 机位区域 */
export interface SeatAreaItem {
  id: number
  areaName: string
  areaColor: string
  sortOrder: number
  isActive: number
  createdAt: string
  updatedAt: string
  computers: ComputerItem[]
}

/** 机位 */
export interface ComputerItem {
  id: number
  areaId: number
  areaName: string
  computerNo: string
  computerName: string
  seatLabel: string
  deviceType: number
  cpu: string
  gpu: string
  memory: string
  screenSize: string
  macAddress: string
  ipAddress: string
  isOnline: number
  status: number
  statusLabel: string
  tariffPlanId: number
  sortOrder: number
  posX: number
  posY: number
  isActive: number
  createdAt: string
  updatedAt: string
}

/** 机位实时状态 */
export interface SeatStatusItem {
  id: number
  status: number
  memberName?: string
  startTime?: string
}

/** 上机会话 */
export interface SessionItem {
  id: number
  sessionNo: string
  computerId: number
  computerNo: string
  computerName: string
  memberId: number
  memberName: string
  memberPhone: string
  authMethod: number
  statusLabel: string
  tariffPlanId: number
  tariffPlanName: string
  startTime: string
  endTime: string
  expectedMinutes: number
  billedMinutes: number
  freeMinutes: number
  totalAmount: number
  discountAmount: number
  paidAmount: number
  status: number
  pauseCount: number
  pauseDuration: number
  operatorId: number
  remark: string
  createdAt: string
  updatedAt: string
  timings?: SessionTimingItem[]
}

/** 计时段 */
export interface SessionTimingItem {
  id: number
  sessionId: number
  timingType: number
  timingTypeLabel: string
  rateType: number
  ratePrice: number
  startTime: string
  endTime: string
  durationMinutes: number
  amount: number
  createdAt: string
  updatedAt: string
}

/** 预约 */
export interface ReservationItem {
  id: number
  reservationNo: string
  memberId: number
  memberName: string
  memberPhone: string
  computerId: number
  computerNo: string
  reservationDate: string
  startTime: string
  endTime: string
  depositAmount: number
  status: number
  statusLabel: string
  cancelReason: string
  checkedInAt: string
  sessionId: number
  createdAt: string
  updatedAt: string
}

// ========== 请求类型 ==========

/** 上机请求 */
export interface SessionStartRequest {
  memberId: number
  computerId: number
  authMethod?: number
  tariffPlanId?: number
  expectedMinutes?: number
}

/** 下机请求 */
export interface SessionEndRequest {
  sessionId: number
  paidAmount?: number
}

/** 暂停请求 */
export interface SessionPauseRequest {
  sessionId: number
}

/** 恢复请求 */
export interface SessionResumeRequest {
  sessionId: number
}

/** 换机请求 */
export interface SessionTransferRequest {
  sessionId: number
  targetComputerId: number
}

/** 强制下机请求 */
export interface ForceEndRequest {
  sessionId: number
  reason?: string
}

/** 创建预约请求 */
export interface ReservationCreateRequest {
  memberId: number
  computerId?: number
  reservationDate: string
  startTime: string
  endTime: string
  depositAmount?: number
}

/** 会话查询参数 */
export interface SessionQuery {
  memberName?: string
  computerNo?: string
  status?: number | null
}

/** 预约查询参数 */
export interface ReservationQuery {
  memberName?: string
  computerNo?: string
  status?: number | null
  reservationDate?: string
}
