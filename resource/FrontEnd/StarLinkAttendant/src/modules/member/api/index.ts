import request from '@/common/api/request'
import type { PageRequest, PageResult } from '@/common/api/types'
import type {
  MemberItem, MemberQuery, MemberRegisterRequest, MemberUpdateRequest,
  LevelItem, LevelCreateRequest, LevelUpdateRequest,
  PointsRecordItem,
  RechargeRecordItem, RechargeRequest,
  BlacklistItem,
} from '../types'

// ========== 会员管理 ==========
export function getMemberList(params: PageRequest & MemberQuery) {
  return request.get<any, { data: PageResult<MemberItem> }>('/api/member/list', { params })
}
export function getMember(id: number) {
  return request.get<any, { data: MemberItem }>(`/api/member/${id}`)
}
export function registerMember(data: MemberRegisterRequest) {
  return request.post<any, { data: MemberItem }>('/api/member/register', data)
}
export function updateMember(id: number, data: MemberUpdateRequest) {
  return request.put<any, { data: MemberItem }>(`/api/member/${id}`, data)
}
export function deleteMember(id: number) {
  return request.delete(`/api/member/${id}`)
}

// ========== 等级管理 ==========
export function getLevelList() {
  return request.get<any, { data: LevelItem[] }>('/api/member/levels')
}
export function getLevel(id: number) {
  return request.get<any, { data: LevelItem }>(`/api/member/levels/${id}`)
}
export function createLevel(data: LevelCreateRequest) {
  return request.post<any, { data: LevelItem }>('/api/member/levels', data)
}
export function updateLevel(id: number, data: LevelUpdateRequest) {
  return request.put<any, { data: LevelItem }>(`/api/member/levels/${id}`, data)
}
export function deleteLevel(id: number) {
  return request.delete(`/api/member/levels/${id}`)
}

// ========== 积分管理 ==========
export function getPointsRecords(memberId: number, params: PageRequest & { bizType?: number; startTime?: string; endTime?: string }) {
  return request.get<any, { data: PageResult<PointsRecordItem> }>(`/api/member/${memberId}/points-records`, { params })
}

// ========== 充值管理 ==========
export function createRecharge(data: RechargeRequest) {
  return request.post<any, { data: RechargeRecordItem }>('/api/member/recharge', data)
}
export function getRechargeRecords(memberId: number, params: PageRequest & { startTime?: string; endTime?: string; status?: number }) {
  return request.get<any, { data: PageResult<RechargeRecordItem> }>(`/api/member/${memberId}/recharge-records`, { params })
}
export function getRechargeRecord(id: number) {
  return request.get<any, { data: RechargeRecordItem }>(`/api/member/recharge-records/${id}`)
}

// ========== 黑名单 ==========
export function getBlacklist() {
  return request.get<any, { data: BlacklistItem[] }>('/api/member/blacklist')
}
export function addToBlacklist(memberId: number, reason?: string) {
  return request.post('/api/member/blacklist', { blacklistReason: reason }, { params: { id: memberId } })
}
export function removeFromBlacklist(id: number) {
  return request.delete(`/api/member/blacklist/${id}`)
}
