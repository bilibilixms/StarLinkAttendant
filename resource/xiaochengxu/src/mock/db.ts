/**
 * Mock 数据库。
 *
 * 在内存里维护一份可变状态，并持久化到 uni storage，
 * 这样页面跳转 / 刷新后余额、订单、当前会话都会保留，demo 流程才连贯。
 *
 * 注意：登录态**不在**这里维护。登录走真实后端 /api/member/login 拿 JWT，
 * 唯一判据是 storage 里的 token，统一用 isLoggedIn() 读取。
 * （历史遗留的 MockState.loggedIn 字段从未被置为 true，导致登录后被误判
 *  为未登录、反复弹「登录状态已失效」跳登录页 —— 已删除。）
 */
import { STORAGE_KEYS } from '@/config'
import { getStorage, getToken, setStorage, removeStorage } from '@/utils/storage'
import type { Member } from '@/types/member'
import type { CurrentSession, SessionRecord } from '@/types/session'
import type { Order, ConsumeRecord } from '@/types/order'
import type { Coupon } from '@/types/coupon'
import type { GameTask } from '@/types/game'
import type { Post, TeamRecruit } from '@/types/community'
import type { Reservation } from '@/types/reservation'
import type { PointsRecord, RechargeRecord } from '@/types/store'
import type { Product } from '@/types/product'
import * as seed from './seed'

export interface MockState {
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

/**
 * 是否已登录：以真实后端签发的 JWT 为唯一判据。
 *
 * mock 路由的 auth 校验与各 handler 都读这里，不再单独维护登录标记 ——
 * 两处状态一旦不同步就会出现「已登录却被判未登录」的死循环。
 */
export function isLoggedIn(): boolean {
  return !!getToken()
}

/* ==================== 真实热门商品 → mock 下单池 ==================== */
/**
 * 小程序首页「热门商品」来自真实后端 product 表，其 id 不在 mock 种子库
 * （seed.seedProducts，id 为 101~503）。而自助点餐的下单/支付/退款是本地 mock 流程，
 * 直接在 seed.seedProducts 里按 id 查会报「商品不存在」。这里维护一个独立的
 * mock 下单池，把真实热门商品注册进去，下单 mock 流程既能校验到价格/库存，
 * 又不会污染自助点餐的那个 mock 商品菜单。
 */
let orderExtras: Product[] = []

/** 将真实热门商品注册进 mock 下单池（重复注册以最新为准） */
export function registerMockProducts(list: Product[]): void {
  for (const raw of list) {
    // 真实商品无库存字段，注册时给一个充足占位值，避免 mock 下单误判「库存不足」
    const p: Product = { ...raw, stock: Math.max(1, raw.stock || 9999) }
    const idx = orderExtras.findIndex((x) => x.id === p.id)
    if (idx >= 0) orderExtras[idx] = p
    else orderExtras.push(p)
  }
}

/** 下单/支付校验商品：先查 mock 下单池（真实热门商品），查不到再回落 mock 菜单 */
export function findOrderProduct(id: number): Product | undefined {
  return orderExtras.find((x) => x.id === id) ?? seed.seedProducts.find((x) => x.id === id)
}
