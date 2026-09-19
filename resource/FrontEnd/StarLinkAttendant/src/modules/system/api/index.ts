import request from '@/common/api/request'
import type { PageRequest, PageResult } from '@/common/api/types'
import type {
  UserItem, UserQuery, UserCreateRequest, UserUpdateRequest,
  RoleItem, RoleCreateRequest, RoleUpdateRequest,
  MenuItem, MenuCreateRequest, MenuUpdateRequest,
  NoticeItem, NoticeCreateRequest, NoticeUpdateRequest,
  LogItem, LogQuery,
} from '../types'

// ========== 用户管理 ==========
export function getUserList(params: PageRequest & UserQuery) {
  return request.get<any, { data: PageResult<UserItem> }>('/api/system/users', { params })
}
export function getUser(id: number) {
  return request.get<any, { data: UserItem }>(`/api/system/users/${id}`)
}
export function createUser(data: UserCreateRequest) {
  return request.post<any, { data: UserItem }>('/api/system/users', data)
}
export function updateUser(id: number, data: UserUpdateRequest) {
  return request.put<any, { data: UserItem }>(`/api/system/users/${id}`, data)
}
export function deleteUser(id: number) {
  return request.delete(`/api/system/users/${id}`)
}
export function updateUserStatus(id: number, status: number) {
  return request.patch(`/api/system/users/${id}/status`, { status })
}
export function resetUserPassword(id: number) {
  return request.patch(`/api/system/users/${id}/reset-password`)
}

// ========== 角色管理 ==========
export function getRoleList(params: PageRequest) {
  return request.get<any, { data: PageResult<RoleItem> }>('/api/system/roles', { params })
}
export function getAllRoles() {
  return request.get<any, { data: RoleItem[] }>('/api/system/roles/all')
}
export function getRole(id: number) {
  return request.get<any, { data: RoleItem }>(`/api/system/roles/${id}`)
}
export function createRole(data: RoleCreateRequest) {
  return request.post<any, { data: RoleItem }>('/api/system/roles', data)
}
export function updateRole(id: number, data: RoleUpdateRequest) {
  return request.put<any, { data: RoleItem }>(`/api/system/roles/${id}`, data)
}
export function deleteRole(id: number) {
  return request.delete(`/api/system/roles/${id}`)
}
export function assignRolePermissions(id: number, permissionIds: number[]) {
  return request.put(`/api/system/roles/${id}/permissions`, { permissionIds })
}

// ========== 菜单管理 ==========
export function getMenuTree() {
  return request.get<any, { data: MenuItem[] }>('/api/system/menus')
}
export function getMenu(id: number) {
  return request.get<any, { data: MenuItem }>(`/api/system/menus/${id}`)
}
export function createMenu(data: MenuCreateRequest) {
  return request.post<any, { data: MenuItem }>('/api/system/menus', data)
}
export function updateMenu(id: number, data: MenuUpdateRequest) {
  return request.put<any, { data: MenuItem }>(`/api/system/menus/${id}`, data)
}
export function deleteMenu(id: number) {
  return request.delete(`/api/system/menus/${id}`)
}

// ========== 操作日志 ==========
export function getLogList(params: PageRequest & LogQuery) {
  return request.get<any, { data: PageResult<LogItem> }>('/api/system/logs', { params })
}

// ========== 通知公告 ==========
export function getNoticeList(params: PageRequest & { title?: string; notifyType?: number }) {
  return request.get<any, { data: PageResult<NoticeItem> }>('/api/system/notices', { params })
}
export function getNotice(id: number) {
  return request.get<any, { data: NoticeItem }>(`/api/system/notices/${id}`)
}
export function createNotice(data: NoticeCreateRequest) {
  return request.post<any, { data: NoticeItem }>('/api/system/notices', data)
}
export function updateNotice(id: number, data: NoticeUpdateRequest) {
  return request.put<any, { data: NoticeItem }>(`/api/system/notices/${id}`, data)
}
export function deleteNotice(id: number) {
  return request.delete(`/api/system/notices/${id}`)
}
