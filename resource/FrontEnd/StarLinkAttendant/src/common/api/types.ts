/** 统一API响应 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp: string
}

/** 分页请求参数 */
export interface PageRequest {
  page?: number
  size?: number
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

/** 分页结果 */
export interface PageResult<T = any> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录响应 */
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userInfo: {
    id: number
    employeeNo: string
    realName: string
    phone: string
    position: string
  }
}

/** 用户信息 */
export interface UserInfoResponse {
  id: number
  employeeNo: string
  realName: string
  phone: string
  position: string
  roles: string[]
  permissions: string[]
}
