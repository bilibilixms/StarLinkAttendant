/**
 * 会员相关类型。
 * 字段对齐数据库表 member / member_level（见 doc/数据库设计文档.md）。
 */

/** 会员状态：1-正常 2-冻结 3-黑名单 */
export type MemberStatus = 1 | 2 | 3

/** 性别：0-未知 1-男 2-女 */
export type Gender = 0 | 1 | 2

export interface MemberLevel {
  id: number
  levelName: string
  /** 折扣率，如 0.85 表示 85 折 */
  discountRate?: number
  /** 升级所需累计积分 */
  upgradePoints?: number
  /** 等级图标（本地资源 key） */
  icon?: string
}

export interface Member {
  id: number
  memberNo: string
  realName?: string
  nickName?: string
  gender: Gender
  phone: string
  avatar?: string
  birthday?: string
  levelId?: number
  levelName?: string
  availablePoints: number
  totalPoints: number
  balance: number
  totalRecharge: number
  totalConsumption: number
  totalOnlineHours: number
  status: MemberStatus
  /** 是否已完成实名认证 */
  verified?: boolean
  createdAt?: string
}

/**
 * 首页聚合数据。
 * 注：后端 starlink-module-member 暂未提供 summary 接口，
 * 前端保留类型以便后续接入，但 stores/user.ts 已停止拉取，统一使用本地缓存的 Member。
 */
export interface MemberSummary {
  login: boolean
  member: Member | null
  couponCount: number
  cardCount: number
  hotelPoints: number
  signedToday: boolean
  continuousSignDays: number
  postStat: {
    published: number
    liked: number
    collected: number
  }
  currentSession: CurrentSessionBrief | null
}

export interface CurrentSessionBrief {
  sessionId: number
  sessionNo: string
  storeName: string
  areaName: string
  computerName: string
  seatNo: string
  startTime: string
  durationMinutes: number
  currentAmount: number
  remainingMinutes: number
  status: SessionStatus
}

/** 上机会话状态：0-上机中 1-临时下机 2-已下机 3-强制下机 4-异常中断 */
export type SessionStatus = 0 | 1 | 2 | 3 | 4

/**
 * 登录响应：对齐后端 MemberLoginResponse（POST /api/member/login）。
 * 后端只返回 token + 部分 Member 字段，未返回字段由 api/auth.ts 的 mapLoginResult 用默认值补齐。
 */
export interface LoginResult {
  token: string
  member: Member
}
