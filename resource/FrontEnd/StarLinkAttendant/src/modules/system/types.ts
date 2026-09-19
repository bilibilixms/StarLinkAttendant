/** 用户/员工 */
export interface UserItem {
  id: number
  employeeNo: string
  realName: string
  phone: string
  position: string
  employmentType: number
  employmentTypeLabel: string
  hireDate: string
  resignDate: string
  status: number
  statusLabel: string
  isActive: number
  lastLoginAt: string
  createdAt: string
  updatedAt: string
}

export interface UserQuery {
  realName?: string
  phone?: string
  status?: number | null
}

export interface UserCreateRequest {
  employeeNo: string
  realName: string
  phone: string
  password: string
  position?: string
  employmentType?: number
  status?: number
}

export interface UserUpdateRequest {
  realName?: string
  phone?: string
  position?: string
  employmentType?: number
  status?: number
  isActive?: number
}

/** 角色 */
export interface RoleItem {
  id: number
  roleName: string
  roleCode: string
  description: string
  isSystem: number
  sortOrder: number
  isActive: number
  createdAt: string
  updatedAt: string
}

export interface RoleCreateRequest {
  roleName: string
  roleCode: string
  description?: string
  sortOrder?: number
  isActive?: number
}

export interface RoleUpdateRequest {
  roleName?: string
  description?: string
  sortOrder?: number
  isActive?: number
}

/** 菜单/权限 */
export interface MenuItem {
  id: number
  parentId: number | null
  permName: string
  permCode: string
  permType: number
  icon: string
  route: string
  sortOrder: number
  children?: MenuItem[]
}

export interface MenuCreateRequest {
  parentId?: number | null
  permName: string
  permCode: string
  permType?: number
  icon?: string
  route?: string
  sortOrder?: number
}

export interface MenuUpdateRequest {
  parentId?: number | null
  permName?: string
  permCode?: string
  permType?: number
  icon?: string
  route?: string
  sortOrder?: number
}

/** 操作日志 */
export interface LogItem {
  id: number
  operatorName: string
  bizType: string
  bizTypeLabel?: string
  bizId?: number
  action: string
  actionLabel: string
  detail: string
  ipAddress: string
  createdAt: string
}

export interface LogQuery {
  operatorName?: string
  bizType?: string
  action?: string
  startTime?: string
  endTime?: string
}

/** 通知公告 */
export interface NoticeItem {
  id: number
  notifyType: number
  title: string
  content: string
  targetType: number
  targetIds: string
  isRead: number
  publishedAt: string
  expiredAt: string
  createdAt: string
  updatedAt: string
}

export interface NoticeCreateRequest {
  title: string
  content?: string
  notifyType?: number
  targetType?: number
  targetIds?: string
  publishedAt?: string
  expiredAt?: string
}

export interface NoticeUpdateRequest {
  title?: string
  content?: string
  publishedAt?: string
  expiredAt?: string
}
