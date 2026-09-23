/**
 * 上机会话接口：直接上机 / 自助下机 / 当前会话 / 上机记录。
 */
import { get, post } from './request'
import { API_PREFIX } from '@/config'
import type { CurrentSession, ScanStartRequest, ScanStartResult, SelfEndResult, SessionRecord } from '@/types/session'
import type { PageResult } from '@/types/api'

/** 当前上机会话（首页/当前上机页轮询），无会话时后端返回 null */
export function getCurrentSession(): Promise<CurrentSession | null> {
  return get<CurrentSession | null>(`${API_PREFIX}/session/current`, undefined, { silent: true })
}

/** 直接上机（沿用 scan-start 接口，前端不再扫码，点击机位即开台） */
export function scanStart(payload: ScanStartRequest): Promise<ScanStartResult> {
  return post<ScanStartResult>(`${API_PREFIX}/session/scan-start`, payload as unknown as Record<string, unknown>)
}

/** 自助下机结算 */
export function selfEnd(sessionId: number): Promise<SelfEndResult> {
  return post<SelfEndResult>(`${API_PREFIX}/session/self-end`, { sessionId })
}

/** 远程下机（挂机状态下的延迟下机） */
export function remoteEnd(sessionId: number, delayMinutes: number): Promise<SelfEndResult> {
  return post<SelfEndResult>(`${API_PREFIX}/session/remote-end`, { sessionId, delayMinutes })
}

/** 临时下机 / 恢复上机 */
export function pauseSession(sessionId: number): Promise<void> {
  return post<void>(`${API_PREFIX}/session/pause`, { sessionId })
}

export function resumeSession(sessionId: number): Promise<void> {
  return post<void>(`${API_PREFIX}/session/resume`, { sessionId })
}

/** 上机记录（消费记录页） */
export function getSessionRecords(params: {
  current?: number
  size?: number
}): Promise<PageResult<SessionRecord>> {
  return get<PageResult<SessionRecord>>(`${API_PREFIX}/session/records`, params as Record<string, unknown>)
}
