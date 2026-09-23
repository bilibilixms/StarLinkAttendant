import request from '@/common/api/request'
import type { PageRequest, PageResult } from '@/common/api/types'
import type {
  SeatAreaItem, SeatStatusItem, SessionItem, ReservationItem,
  SessionStartRequest, SessionEndRequest, SessionPauseRequest,
  SessionResumeRequest, SessionTransferRequest, ForceEndRequest,
  ReservationCreateRequest, SessionQuery, ReservationQuery,
  ComputerItem,
} from '../types'

// ========== 座位图 ==========

/** 获取座位图配置（区域 + 机位列表） */
export function getSeatMap() {
  return request.get<any, { data: SeatAreaItem[] }>('/api/session/seat-map')
}

/** 获取所有机位实时状态（轮询接口） */
export function getSeatStatus() {
  return request.get<any, { data: SeatStatusItem[] }>('/api/session/seat-status')
}

// ========== 上机/下机 ==========

/** 上机（开台） */
export function startSession(data: SessionStartRequest) {
  return request.post<any, { data: SessionItem }>('/api/session/start', data)
}

/** 下机（结账） */
export function endSession(data: SessionEndRequest) {
  return request.post<any, { data: SessionItem }>('/api/session/end', data)
}

/** 暂停上机（临时下机） */
export function pauseSession(data: SessionPauseRequest) {
  return request.post<any, { data: SessionItem }>('/api/session/pause', data)
}

/** 恢复上机 */
export function resumeSession(data: SessionResumeRequest) {
  return request.post<any, { data: SessionItem }>('/api/session/resume', data)
}

/** 换机 */
export function transferSession(data: SessionTransferRequest) {
  return request.post<any, { data: SessionItem }>('/api/session/transfer', data)
}

/** 强制下机（管理员操作） */
export function forceEndSession(data: ForceEndRequest) {
  return request.post<any, { data: SessionItem }>('/api/session/force-end', data)
}

// ========== 会话查询 ==========

/** 当前活跃会话列表 */
export function getActiveSessions() {
  return request.get<any, { data: SessionItem[] }>('/api/session/active')
}

/** 会话详情 */
export function getSessionDetail(id: number) {
  return request.get<any, { data: SessionItem }>(`/api/session/${id}`)
}

/** 会话列表（分页） */
export function getSessionList(params: PageRequest & SessionQuery) {
  return request.get<any, { data: PageResult<SessionItem> }>('/api/session/list', { params })
}

/** 删除会话（仅已结束会话） */
export function deleteSession(id: number) {
  return request.delete(`/api/session/${id}`)
}

// ========== 区域/机位管理 ==========

/** 创建区域 */
export function createArea(data: Partial<SeatAreaItem>) {
  return request.post<any, { data: SeatAreaItem }>('/api/session/areas', data)
}

/** 更新区域 */
export function updateArea(id: number, data: Partial<SeatAreaItem>) {
  return request.put<any, { data: SeatAreaItem }>(`/api/session/areas/${id}`, data)
}

/** 删除区域 */
export function deleteArea(id: number) {
  return request.delete(`/api/session/areas/${id}`)
}

/** 创建机位 */
export function createComputer(data: Partial<ComputerItem>) {
  return request.post<any, { data: ComputerItem }>('/api/session/computers', data)
}

/** 更新机位 */
export function updateComputer(id: number, data: Partial<ComputerItem>) {
  return request.put<any, { data: ComputerItem }>(`/api/session/computers/${id}`, data)
}

/** 删除机位 */
export function deleteComputer(id: number) {
  return request.delete(`/api/session/computers/${id}`)
}

// ========== 预约管理 ==========

/** 预约列表（分页） */
export function getReservationList(params: PageRequest & ReservationQuery) {
  return request.get<any, { data: PageResult<ReservationItem> }>('/api/session/reservations', { params })
}

/** 创建预约 */
export function createReservation(data: ReservationCreateRequest) {
  return request.post<any, { data: ReservationItem }>('/api/session/reservations', data)
}

/** 取消预约 */
export function cancelReservation(id: number, cancelReason?: string) {
  return request.delete(`/api/session/reservations/${id}`, { params: { cancelReason } })
}
