/**
 * 后端统一响应结构。
 * 与 BackEnd/cafe-common/src/main/java/com/intcaf/common/response/ApiResponse.java 完全一致：
 * code === 0 表示成功。
 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  timestamp?: string
}

/** 分页返回结构（对应后端 PageResponse） */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/** 分页请求参数 */
export interface PageQuery {
  current?: number
  size?: number
}

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

export interface RequestOptions {
  url: string
  method?: HttpMethod
  data?: Record<string, unknown>
  header?: Record<string, string>
  /** 展示全局 loading，默认 false */
  loading?: boolean
  /** 静默模式：失败时不弹 toast，由调用方自行处理 */
  silent?: boolean
  /** 是否需要携带 token，默认 true */
  auth?: boolean
  /** 超时毫秒，默认 15000 */
  timeout?: number
  /** 强制穿透 mock 层打真实后端（即使 USE_MOCK=true），用于「先试后端、失败回退 mock」场景 */
  bypassMock?: boolean
}

/** 三态：加载中 / 空 / 错误，页面统一用它驱动 UI */
export type LoadState = 'loading' | 'empty' | 'error' | 'success'
