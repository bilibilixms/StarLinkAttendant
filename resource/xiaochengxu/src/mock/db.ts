/**
 * Mock 数据库。
 *
 * 在内存里维护一份可变状态，并持久化到 uni storage，
 * 这样页面跳转 / 刷新后余额、订单、当前会话都会保留，demo 流程才连贯。
 */
import { STORAGE_KEYS } from '@/config'
import { getStorage, setStorage, removeStorage } from '@/utils/storage'
import type { Member } from '@/types/member'
import type { CurrentSession, SessionRecord } from '@/types/session'
import type { Order, ConsumeRecord } from '@/types/order'
import type { Coupon } from '@/types/coupon'
import type { GameTask } from '@/types/game'
import type { Post, TeamRecruit } from '@/types/community'
import type { Reservation } from '@/types/reservation'
import type { PointsRecord, RechargeRecord } from '@/types/store'
import * as seed from './seed'

export interface MockState {
  /** 是否已登录。默认 false —— 与截图里的未登录态一致 */
  loggedIn: boolean
  member: Member
  signedToday: boolean
  continuousSignDays: number
  postStat: { published: number; liked: number; collected: number }

  currentSession: CurrentSession | null
  sessionRecords: SessionRecord[]

  orders: Order[]
  orderSeq: number

  coupons: Coupon[]
  /** 领券中心已领取数量：templateId → count */
  receivedMap: Record<number, number>

  pointsBalance: number
  pointsRecords: PointsRecord[]

  reservations: Reservation[]
  rechargeRecords: RechargeRecord[]
  consumeRecords: ConsumeRecord[]

  posts: Post[]
  teams: TeamRecruit[]
  tasks: GameTask[]
}

function createInitialState(): MockState {
  return {
    loggedIn: false,
    member: JSON.parse(JSON.stringify(seed.seedMember)) as Member,
    signedToday: false,
    continuousSignDays: 3,
    postStat: { published: 0, liked: 0, collected: 0 },

    currentSession: null,
    sessionRecords: JSON.parse(JSON.stringify(seed.seedSessionRecords)) as SessionRecord[],

    orders: JSON.parse(JSON.stringify(seed.seedOrders)) as Order[],
    orderSeq: 100,

    coupons: JSON.parse(JSON.stringify(seed.seedCoupons)) as Coupon[],
    receivedMap: {},

    pointsBalance: seed.seedMember.availablePoints,
    pointsRecords: JSON.parse(JSON.stringify(seed.seedPointsRecords)) as PointsRecord[],

    reservations: JSON.parse(JSON.stringify(seed.seedReservations)) as Reservation[],
    rechargeRecords: JSON.parse(JSON.stringify(seed.seedRechargeRecords)) as RechargeRecord[],
    consumeRecords: JSON.parse(JSON.stringify(seed.seedConsumeRecords)) as ConsumeRecord[],

    posts: JSON.parse(JSON.stringify(seed.seedPosts)) as Post[],
    teams: JSON.parse(JSON.stringify(seed.seedTeams)) as TeamRecruit[],
    tasks: JSON.parse(JSON.stringify(seed.seedGameTasks)) as GameTask[],
  }
}

let state: MockState | null = null

/** 取当前状态（首次调用时从 storage 恢复，恢复失败则用种子数据） */
export function db(): MockState {
  if (state) return state
  const persisted = getStorage<MockState | null>(STORAGE_KEYS.MOCK_DB, null)
  state = persisted && persisted.member ? persisted : createInitialState()
  return state
}

/** 落盘。每次写操作后调用 */
export function save(): void {
  if (!state) return
  setStorage(STORAGE_KEYS.MOCK_DB, state)
}

/** 原子写：改完自动保存 */
export function mutate<T>(fn: (s: MockState) => T): T {
  const s = db()
  const result = fn(s)
  save()
  return result
}

/** 重置 mock 数据（设置页「重置演示数据」用） */
export function resetMockDB(): void {
  state = createInitialState()
  removeStorage(STORAGE_KEYS.MOCK_DB)
  save()
}

/** 退出登录：只清登录标记，保留演示数据 */
export function logoutMock(): void {
  mutate((s) => {
    s.loggedIn = false
  })
}
