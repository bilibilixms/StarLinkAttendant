/**
 * Mock 路由表。
 *
 * 会员端（登录/充值/积分）已对接真实后端 starlink-module-member，
 * 相关 mock 路由已删除，请求会被 api/request.ts 的 isMemberEndpoint() 穿透到 8080。
 * 其余业务（订单/商品/上机/活动等）后端尚未实现，仍走本地 mock。
 */
import { API_PREFIX } from '@/config'
import { db, isLoggedIn, mutate, findOrderProduct } from './db'
import * as seed from './seed'
import type { CurrentSession, SelfEndResult } from '@/types/session'
import type { Order, OrderItem } from '@/types/order'
import type { Coupon } from '@/types/coupon'
import type { Reservation } from '@/types/reservation'

export interface MockContext {
  params: Record<string, string>
  query: Record<string, string>
  body: Record<string, unknown>
  /** 当前是否带 token（模拟后端鉴权） */
  authed: boolean
}

export type MockHandler = (ctx: MockContext) => unknown

export interface MockRoute {
  method: string
  path: string
  /** 是否需要登录，true 时未登录返回 401 */
  auth?: boolean
  handler: MockHandler
}

/* ==================== 工具 ==================== */

const P = API_PREFIX

function now(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function round2(n: number): number {
  return Math.round(n * 100) / 100
}

/** 会员等级折扣：普通 1 / 银卡 0.95 / 金卡 0.9 / 钻石 0.85 */
const LEVEL_DISCOUNT: Record<number, number> = { 1: 1, 2: 0.95, 3: 0.9, 4: 0.85 }

function levelRate(): number {
  return LEVEL_DISCOUNT[db().member.levelId ?? 1] ?? 1
}

function paginate<T>(list: T[], query: Record<string, string>): { records: T[]; total: number; current: number; size: number; pages: number } {
  const current = Math.max(1, Number(query.current ?? 1))
  const size = Math.max(1, Number(query.size ?? 10))
  const start = (current - 1) * size
  return {
    records: list.slice(start, start + size),
    total: list.length,
    current,
    size,
    pages: Math.max(1, Math.ceil(list.length / size)),
  }
}

/** 每次会话费用：费率 × 时长，再按会员等级打折 */
function calcSessionFee(rate: number, minutes: number) {
  const total = round2((rate * Math.max(0, minutes)) / 60)
  const discount = round2(total * (1 - levelRate()))
  return { total, discount, payable: round2(total - discount) }
}

/** 构建 / 刷新当前会话，实时计算时长与费用 */
function buildCurrentSession(s: ReturnType<typeof db>): CurrentSession | null {
  const cs = s.currentSession
  if (!cs) return null
  const start = new Date(cs.startTime.replace(/-/g, '/')).getTime()
  const minutes = Math.max(0, Math.floor((Date.now() - start) / 60000))
  const fee = calcSessionFee(cs.hourlyRate, minutes)
  const balance = s.member.balance
  const remain = cs.hourlyRate > 0 ? Math.floor((balance / cs.hourlyRate) * 60) : -1
  return {
    ...cs,
    durationMinutes: minutes,
    billedMinutes: minutes,
    totalAmount: fee.total,
    discountAmount: fee.discount,
    paidAmount: fee.payable,
    firstHourPrice: cs.firstHourPrice ?? cs.hourlyRate,
    balance,
    balanceAfter: round2(Math.max(0, balance - fee.payable)),
    remainingMinutes: remain,
  }
}

/** 未登录时统一抛 401，由 request.ts 处理跳登录页 */
function needLogin(): never {
  const err = new Error('请先登录后再操作') as Error & { code?: number }
  err.code = 401
  throw err
}

/**
 * 下机结算：按当前会话时长计费、按会员等级打折、扣余额，
 * 并把本次上机写入消费流水与上机记录。
 */
function endSession(): SelfEndResult {
  return mutate((s) => {
    if (!s.currentSession) throw new Error('当前没有进行中的上机会话')
    const cs = buildCurrentSession(s)!
    // 不足 1 分钟按 1 分钟起计费（真实电竞馆也是 1 分钟起计），
    // 否则刚开机就下机会被拒绝，体验很差。
    const minutes = Math.max(1, cs.durationMinutes)

    const fee = calcSessionFee(cs.hourlyRate, minutes)
    if (s.member.balance < fee.payable) {
      throw new Error('账户余额不足，请先充值后再下机')
    }

    s.member.balance = round2(s.member.balance - fee.payable)
    s.member.totalConsumption = round2(s.member.totalConsumption + fee.payable)
    s.member.totalOnlineHours += minutes

    const endedAt = now()
    const record: SessionRecordLike = {
      id: cs.sessionId,
      sessionNo: cs.sessionNo,
      storeName: cs.storeName,
      computerName: cs.computerName,
      areaName: cs.areaName,
      startTime: cs.startTime,
      endTime: endedAt,
      durationMinutes: minutes,
      totalAmount: fee.total,
      paidAmount: fee.payable,
      status: 2,
    }
    s.sessionRecords.unshift(record as never)

    s.consumeRecords.unshift({
      id: Date.now(),
      type: 'session',
      title: '上机消费',
      subtitle: `${cs.computerName} · ${minutes}分钟`,
      amount: -fee.payable,
      createdAt: endedAt,
    })

    // 上机也累计积分：1 元 = 1 分
    const gain = Math.floor(fee.payable)
    if (gain > 0) {
      s.pointsBalance += gain
      s.member.availablePoints = s.pointsBalance
      s.member.totalPoints += gain
      s.pointsRecords.unshift({
        id: Date.now() + 1,
        type: 1,
        changePoints: gain,
        balanceAfter: s.pointsBalance,
        title: '上机获得积分',
        description: `上机消费 ${fee.payable} 元`,
        createdAt: endedAt,
      })
    }

    const result: SelfEndResult = {
      sessionId: cs.sessionId,
      sessionNo: cs.sessionNo,
      durationMinutes: minutes,
      totalAmount: fee.total,
      discountAmount: fee.discount,
      paidAmount: fee.payable,
      balanceAfter: s.member.balance,
      endTime: endedAt,
    }

    s.currentSession = null
    return result
  })
}

/** 仅用于 endSession 内部构造记录，避免引入未使用的类型导入 */
interface SessionRecordLike {
  id: number
  sessionNo: string
  storeName: string
  computerName: string
  areaName: string
  startTime: string
  endTime: string
  durationMinutes: number
  totalAmount: number
  paidAmount: number
  status: number
}

/* ==================== 路由表 ==================== */

export const routes: MockRoute[] = [
  /* ---------- 上机会话 ---------- */
  {
    method: 'GET',
    path: `${P}/session/current`,
    auth: true,
    handler: () => {
      if (!isLoggedIn()) needLogin()
      return buildCurrentSession(db())
    },
  },
  {
    method: 'POST',
    path: `${P}/session/scan-start`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      const qr = String(ctx.body.qrContent ?? '')
      // 支持两种二维码：JSON 串 或 简单串 "storeId-areaId-computerId"
      let computerId = Number(ctx.body.computerId ?? 0)
      let storeId = Number(ctx.body.storeId ?? 1)
      let areaId = Number(ctx.body.areaId ?? 0)
      let computerName = String(ctx.body.computerName ?? '')

      if (qr) {
        try {
          const parsed = JSON.parse(qr)
          computerId = computerId || Number(parsed.computerId ?? 0)
          storeId = Number(parsed.storeId ?? storeId)
          areaId = areaId || Number(parsed.areaId ?? 0)
          computerName = computerName || String(parsed.computerName ?? '')
        } catch {
          if (!computerId) {
            const parts = qr.split(/[-_:]/)
            computerId = Number(parts[parts.length - 1])
            if (Number.isNaN(computerId)) computerId = 0
          }
        }
      }
      if (!computerId) throw new Error('二维码无效，请重新扫描机位二维码')

      // 兜底：按 computerId 反查真实机位，补齐区域与名称。
      // 区域决定费率，绝不能默认成普通区 —— 否则高级区会按普通区价格结算。
      if (!areaId || !computerName) {
        for (const list of Object.values(seed.seedComputers)) {
          const found = list.find((c) => c.id === computerId)
          if (found) {
            areaId = areaId || found.areaId
            computerName = computerName || found.computerName
            break
          }
        }
      }
      if (!areaId) areaId = 1

      const s = db()
      if (s.currentSession) {
        throw new Error('您已有正在进行的上机会话，请先下机')
      }
      if (s.member.balance <= 0) {
        throw new Error('账户余额不足，请先充值')
      }

      const area = seed.seedSeatAreas.find((a) => a.id === areaId) ?? seed.seedSeatAreas[0]
      const seatNo = computerName || `${area.areaName}-A${String(computerId % 100).padStart(2, '0')}`

      return mutate((st) => {
        st.currentSession = {
          sessionId: Date.now(),
          sessionNo: `SES${Date.now()}`,
          storeId,
          storeName: seed.seedStores.find((x) => x.id === storeId)?.name ?? seed.seedStores[0].name,
          computerId,
          computerName: seatNo,
          seatNo: seatNo.split('-').pop() ?? seatNo,
          areaName: area.areaName,
          computerSpec: area.description,
          startTime: now(),
          durationMinutes: 0,
          billedMinutes: 0,
          freeMinutes: 0,
          totalAmount: 0,
          discountAmount: 0,
          paidAmount: 0,
          hourlyRate: area.hourlyRate,
          firstHourPrice: area.hourlyRate,
          balance: st.member.balance,
          balanceAfter: st.member.balance,
          remainingMinutes: Math.floor((st.member.balance / area.hourlyRate) * 60),
          status: 0,
        }
        return { session: buildCurrentSession(st)!, resumed: false }
      })
    },
  },
  {
    method: 'POST',
    path: `${P}/session/self-end`,
    auth: true,
    handler: () => {
      if (!isLoggedIn()) needLogin()
      return endSession()
    },
  },
  {
    method: 'POST',
    path: `${P}/session/remote-end`,
    auth: true,
    handler: () => {
      if (!isLoggedIn()) needLogin()
      return endSession()
    },
  },
  {
    method: 'POST',
    path: `${P}/session/pause`,
    auth: true,
    handler: () => {
      if (!isLoggedIn()) needLogin()
      return mutate((s) => {
        if (!s.currentSession) throw new Error('当前没有进行中的会话')
        s.currentSession.status = 1
        return null
      })
    },
  },
  {
    method: 'POST',
    path: `${P}/session/resume`,
    auth: true,
    handler: () => {
      if (!isLoggedIn()) needLogin()
      return mutate((s) => {
        if (!s.currentSession) throw new Error('当前没有进行中的会话')
        s.currentSession.status = 0
        return null
      })
    },
  },
  {
    method: 'GET',
    path: `${P}/session/records`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      return paginate(db().sessionRecords, ctx.query)
    },
  },

  /* ---------- 预约 ---------- */
  {
    method: 'GET',
    path: `${P}/reservations`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      let list = db().reservations
      if (ctx.query.status !== undefined && ctx.query.status !== '') {
        list = list.filter((r) => r.status === Number(ctx.query.status))
      }
      return paginate(list, ctx.query)
    },
  },
  {
    method: 'POST',
    path: `${P}/reservations`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      const startTime = String(ctx.body.startTime ?? '')
      const endTime = String(ctx.body.endTime ?? '')
      if (!startTime || !endTime) throw new Error('请选择预约时间')
      if (new Date(endTime.replace(/-/g, '/')).getTime() <= new Date(startTime.replace(/-/g, '/')).getTime()) {
        throw new Error('结束时间必须晚于开始时间')
      }

      const type = Number(ctx.body.type ?? 1)
      const storeId = Number(ctx.body.storeId ?? 1)
      const areaId = Number(ctx.body.areaId ?? 0)
      const area = seed.seedSeatAreas.find((a) => a.id === areaId)
      const store = seed.seedStores.find((x) => x.id === storeId) ?? seed.seedStores[0]
      const minutes = Math.round(
        (new Date(endTime.replace(/-/g, '/')).getTime() - new Date(startTime.replace(/-/g, '/')).getTime()) / 60000
      )

      return mutate((s) => {
        const id = Date.now()
        const reservation: Reservation = {
          id,
          reservationNo: `RS${id}`,
          type: type as 1 | 2,
          storeId,
          storeName: store.name,
          areaId: area?.id,
          areaName: area?.areaName,
          computerId: Number(ctx.body.computerIds ? (ctx.body.computerIds as number[])[0] : 0) || undefined,
          computerName: undefined,
          startTime,
          endTime,
          durationMinutes: minutes,
          peopleCount: Number(ctx.body.peopleCount ?? 1),
          contactPhone: String(ctx.body.contactPhone ?? s.member.phone),
          depositAmount: 0,
          status: 0,
          remark: String(ctx.body.remark ?? ''),
          createdAt: now(),
        }
        s.reservations.unshift(reservation)
        return {
          reservationId: id,
          reservationNo: reservation.reservationNo,
          status: 0,
          startTime,
          endTime,
          depositAmount: 0,
          message: '预约成功，请按时到店',
        }
      })
    },
  },
  {
    method: 'POST',
    path: `${P}/reservations/:id/cancel`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      return mutate((s) => {
        const r = s.reservations.find((x) => x.id === Number(ctx.params.id))
        if (!r) throw new Error('预约不存在')
        if (r.status !== 0) throw new Error('该预约不可取消')
        r.status = 2
        return null
      })
    },
  },
  {
    method: 'GET',
    path: `${P}/hotel/room-types`,
    handler: () => seed.seedHotelRoomTypes,
  },

  /* ---------- 商品 ---------- */
  { method: 'GET', path: `${P}/products/categories`, handler: () => seed.seedCategories },
  {
    method: 'GET',
    path: `${P}/products/hot`,
    handler: (ctx) => {
      const limit = Number(ctx.query.limit ?? 6)
      return seed.seedProducts.filter((p) => p.hot).slice(0, limit)
    },
  },
  {
    method: 'GET',
    path: `${P}/products`,
    handler: (ctx) => {
      let list = seed.seedProducts.filter((p) => p.status === 1)
      const categoryId = Number(ctx.query.categoryId ?? 0)
      if (categoryId) list = list.filter((p) => p.categoryId === categoryId)
      const keyword = String(ctx.query.keyword ?? '').trim()
      if (keyword) list = list.filter((p) => p.name.includes(keyword))
      return list
    },
  },
  {
    method: 'GET',
    path: `${P}/products/:id`,
    handler: (ctx) => {
      const p = seed.seedProducts.find((x) => x.id === Number(ctx.params.id))
      if (!p) throw new Error('商品不存在或已下架')
      return p
    },
  },

  /* ---------- 订单 ---------- */
  {
    method: 'GET',
    path: `${P}/orders`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      let list = db().orders
      const tab = String(ctx.query.tab ?? 'all')
      if (tab === 'unpaid') list = list.filter((o) => o.status === 0)
      else if (tab === 'processing') list = list.filter((o) => o.status === 1 && !!o.statusText && o.statusText !== '已完成')
      else if (tab === 'finished') list = list.filter((o) => o.status === 1 && o.statusText === '已完成')
      else if (tab === 'refund') list = list.filter((o) => o.status === 2 || o.status === 3)
      return paginate(list, ctx.query)
    },
  },
  {
    method: 'GET',
    path: `${P}/orders/:id`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      const o = db().orders.find((x) => x.id === Number(ctx.params.id))
      if (!o) throw new Error('订单不存在')
      return o
    },
  },
  {
    method: 'POST',
    path: `${P}/orders`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      const items = (ctx.body.items ?? []) as Array<{
        productId: number
        quantity: number
        /** 快照价（会员价优先），便于 mock 池缺失时兜底结算 */
        price?: number
        name?: string
        cover?: string
        spec?: string
      }>
      if (!items.length) throw new Error('购物车是空的')
      if (!ctx.body.idempotentKey) throw new Error('缺少幂等键，请重试')

      const detailItems: OrderItem[] = []
      let total = 0
      for (const it of items) {
        const p = findOrderProduct(Number(it.productId))
        // 默认商品无限量：mock 池查不到时按购物车快照价格结算、不阻断下单
        if (p && p.stock < it.quantity) {
          throw new Error(`「${p.name}」库存不足，仅剩 ${p.stock} 件`)
        }
        const price = p ? p.memberPrice ?? p.price : Number(it.price) || 0
        const name = p ? p.name : it.name || `商品${it.productId}`
        const cover = p ? p.cover : it.cover ?? ''
        const spec = p ? p.spec : it.spec ?? null
        const amount = round2(price * it.quantity)
        total = round2(total + amount)
        detailItems.push({
          id: it.productId,
          productId: it.productId,
          productName: name,
          cover,
          spec,
          price,
          quantity: it.quantity,
          amount,
        })
      }

      // 优惠券抵扣
      let discount = 0
      const couponId = Number(ctx.body.couponId ?? 0)
      return mutate((s) => {
        if (couponId) {
          const c = s.coupons.find((x) => x.id === couponId && x.status === 0)
          if (!c) throw new Error('优惠券不可用')
          if (total < c.minAmount) throw new Error(`未满 ${c.minAmount} 元，无法使用该券`)
          discount = c.type === 1 ? c.value : round2(total * (1 - c.value / 10))
          discount = Math.min(discount, total)
        }

        const payable = round2(total - discount)
        const id = Date.now()
        const orderNo = `ORD${id}`
        const order: Order = {
          id,
          orderNo,
          orderType: 1,
          memberId: s.member.id,
          storeName: seed.seedStores[0].name,
          seatNo: String(ctx.body.seatNo ?? '') || s.currentSession?.seatNo,
          totalAmount: total,
          discountAmount: discount,
          payableAmount: payable,
          paidAmount: 0,
          status: 0,
          statusText: '待支付',
          items: detailItems,
          remark: String(ctx.body.remark ?? ''),
          createdAt: now(),
        }
        s.orders.unshift(order)

        if (couponId) {
          const c = s.coupons.find((x) => x.id === couponId)
          if (c) c.status = 1
        }

        return { orderId: id, orderNo, payableAmount: payable, status: 0, createdAt: order.createdAt }
      })
    },
  },
  {
    method: 'POST',
    path: `${P}/orders/:id/pay`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      const payChannel = String(ctx.body.payChannel ?? 'balance')
      return mutate((s) => {
        const o = s.orders.find((x) => x.id === Number(ctx.params.id))
        if (!o) throw new Error('订单不存在')
        if (o.status !== 0) throw new Error('该订单无需支付')
        if (payChannel === 'balance' && s.member.balance < o.payableAmount) {
          throw new Error('账户余额不足，请先充值或选择微信支付')
        }

        o.status = 1
        o.paidAmount = o.payableAmount
        o.paidAt = now()
        o.statusText = o.orderType === 1 ? '吧台备餐中' : '已完成'

        if (payChannel === 'balance') {
          s.member.balance = round2(s.member.balance - o.payableAmount)
        }
        s.member.totalConsumption = round2(s.member.totalConsumption + o.payableAmount)

        // 扣库存 + 加销量
        for (const it of o.items) {
          const p = findOrderProduct(it.productId)
          if (p) {
            p.stock = Math.max(0, p.stock - it.quantity)
            p.sales += it.quantity
          }
        }

        // 消费流水
        s.consumeRecords.unshift({
          id: Date.now(),
          type: 'order',
          title: '自助点餐',
          subtitle: `${o.items[0]?.productName ?? '商品'}${o.items.length > 1 ? ` 等 ${o.items.length} 件` : ''}`,
          amount: -o.paidAmount,
          createdAt: o.paidAt,
          orderNo: o.orderNo,
        })

        // 消费获得积分（1 元 = 1 分）
        const gain = Math.floor(o.paidAmount)
        if (gain > 0) {
          s.pointsBalance += gain
          s.member.availablePoints = s.pointsBalance
          s.member.totalPoints += gain
          s.pointsRecords.unshift({
            id: Date.now() + 1,
            type: 1,
            changePoints: gain,
            balanceAfter: s.pointsBalance,
            title: '消费获得积分',
            description: `自助点餐消费 ${o.paidAmount} 元`,
            createdAt: o.paidAt,
          })
        }

        return { orderId: o.id, paidAmount: o.paidAmount, status: o.status }
      })
    },
  },
  {
    method: 'POST',
    path: `${P}/orders/:id/cancel`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      return mutate((s) => {
        const o = s.orders.find((x) => x.id === Number(ctx.params.id))
        if (!o) throw new Error('订单不存在')
        if (o.status !== 0) throw new Error('仅待支付订单可以取消')
        o.status = 4
        o.statusText = '已取消'
        return null
      })
    },
  },
  {
    method: 'POST',
    path: `${P}/orders/:id/refund`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      return mutate((s) => {
        const o = s.orders.find((x) => x.id === Number(ctx.params.id))
        if (!o) throw new Error('订单不存在')
        if (o.status !== 1) throw new Error('仅已完成订单可以申请退款')
        o.status = 3
        o.statusText = '已退款'
        s.member.balance = round2(s.member.balance + o.paidAmount)
        s.consumeRecords.unshift({
          id: Date.now(),
          type: 'order',
          title: '订单退款',
          subtitle: String(ctx.body.reason ?? '用户申请退款'),
          amount: o.paidAmount,
          createdAt: now(),
          orderNo: o.orderNo,
        })
        return null
      })
    },
  },
  {
    method: 'GET',
    path: `${P}/consume-records`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      let list = db().consumeRecords
      const type = String(ctx.query.type ?? '')
      if (type) list = list.filter((r) => r.type === type)
      return paginate(list, ctx.query)
    },
  },

  /* ---------- 优惠券 ---------- */
  {
    method: 'GET',
    path: `${P}/coupons/mine`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      let list = db().coupons
      if (ctx.query.status !== undefined && ctx.query.status !== '') {
        list = list.filter((c) => c.status === Number(ctx.query.status))
      }
      return list
    },
  },
  {
    method: 'GET',
    path: `${P}/coupons/templates`,
    handler: () => {
      const s = db()
      return seed.seedCouponTemplates.map((t) => ({
        ...t,
        receivedCount: s.receivedMap[t.id] ?? t.receivedCount,
      }))
    },
  },
  {
    method: 'GET',
    path: `${P}/coupons/receivable`,
    handler: () => db().coupons.filter((c) => c.status === 0).slice(0, 2),
  },
  {
    method: 'POST',
    path: `${P}/coupons/receive`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      const templateId = Number(ctx.body.templateId ?? 0)
      const tpl = seed.seedCouponTemplates.find((t) => t.id === templateId)
      if (!tpl) throw new Error('优惠券不存在')
      return mutate((s) => {
        const got = s.receivedMap[templateId] ?? tpl.receivedCount
        if (got >= tpl.limitPerMember) throw new Error('该券已达领取上限')
        if (tpl.remainCount <= 0) throw new Error('优惠券已被领完')

        s.receivedMap[templateId] = got + 1
        const coupon: Coupon = {
          id: Date.now(),
          templateId,
          name: tpl.name,
          type: tpl.type,
          value: tpl.value,
          minAmount: tpl.minAmount,
          scope: tpl.scope,
          scopeText: tpl.scopeText,
          status: 0,
          startTime: tpl.startTime,
          endTime: tpl.endTime,
          description: tpl.description,
        }
        s.coupons.unshift(coupon)
        return coupon
      })
    },
  },

  /* ---------- 游戏 ---------- */
  {
    method: 'GET',
    path: `${P}/games`,
    handler: (ctx) => {
      const games = seed.seedGames
      const gameId = Number(ctx.query.gameId ?? 0)
      return gameId ? games.filter((g) => g.id === gameId) : games
    },
  },
  {
    method: 'GET',
    path: `${P}/games/tasks`,
    handler: (ctx) => {
      const gameId = Number(ctx.query.gameId ?? 0)
      const list = db().tasks
      return gameId ? list.filter((t) => t.gameId === gameId) : list
    },
  },
  {
    method: 'GET',
    path: `${P}/games/tasks/:id`,
    handler: (ctx) => {
      const t = db().tasks.find((x) => x.id === Number(ctx.params.id))
      if (!t) throw new Error('任务不存在或已结束')
      return {
        ...t,
        steps: ['登录游戏并绑定星络账号', '完成指定对局/时长要求', '回到小程序点击「领取奖励」'],
        rules: [
          '每个星络账号仅可领取一次奖励',
          '奖励将在领取后 24 小时内发放至游戏账号',
          '如发现作弊行为，星络有权取消奖励资格',
        ],
      }
    },
  },
  {
    method: 'POST',
    path: `${P}/games/tasks/:id/join`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      return mutate((s) => {
        const t = s.tasks.find((x) => x.id === Number(ctx.params.id))
        if (!t) throw new Error('任务不存在或已结束')
        if (t.status === 1) throw new Error('奖励已领取，请勿重复领取')
        t.status = 1
        return { status: 1, message: '奖励领取成功，将尽快发放到您的游戏账号' }
      })
    },
  },

  /* ---------- 社区 ---------- */
  {
    method: 'GET',
    path: `${P}/community/posts`,
    handler: (ctx) => {
      let list = db().posts
      const keyword = String(ctx.query.keyword ?? '').trim()
      if (keyword) list = list.filter((p) => p.title.includes(keyword) || p.content.includes(keyword))
      return paginate(list, ctx.query)
    },
  },
  {
    method: 'GET',
    path: `${P}/community/posts/:id`,
    handler: (ctx) => {
      const p = db().posts.find((x) => x.id === Number(ctx.params.id))
      if (!p) throw new Error('帖子不存在或已删除')
      return p
    },
  },
  {
    method: 'GET',
    path: `${P}/community/posts/:id/comments`,
    handler: (ctx) => {
      const id = Number(ctx.params.id)
      // 第一条帖子用种子评论，其余帖子返回空数组（用于演示空状态）
      return id === 1 ? seed.seedComments : []
    },
  },
  {
    method: 'POST',
    path: `${P}/community/posts/:id/like`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      return mutate((s) => {
        const p = s.posts.find((x) => x.id === Number(ctx.params.id))
        if (!p) throw new Error('帖子不存在')
        p.liked = !p.liked
        p.likeCount = Math.max(0, p.likeCount + (p.liked ? 1 : -1))
        return { liked: p.liked, likeCount: p.likeCount }
      })
    },
  },
  {
    method: 'POST',
    path: `${P}/community/posts/:id/collect`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      return mutate((s) => {
        const p = s.posts.find((x) => x.id === Number(ctx.params.id))
        if (!p) throw new Error('帖子不存在')
        p.collected = !p.collected
        s.postStat.collected = s.posts.filter((x) => x.collected).length
        return { collected: p.collected }
      })
    },
  },
  {
    method: 'POST',
    path: `${P}/community/posts/:id/comments`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      const content = String(ctx.body.content ?? '').trim()
      if (!content) throw new Error('评论内容不能为空')
      if (content.length > 200) throw new Error('评论最多 200 字')
      return mutate((s) => {
        const p = s.posts.find((x) => x.id === Number(ctx.params.id))
        if (p) p.commentCount += 1
        return {
          id: Date.now(),
          author: { id: s.member.id, name: s.member.realName ?? '我', avatar: '', level: 4 },
          content,
          likeCount: 0,
          liked: false,
          createdAt: now(),
          timeText: '刚刚',
        }
      })
    },
  },
  {
    method: 'GET',
    path: `${P}/community/teams`,
    handler: (ctx) => {
      let list = db().teams
      const gameId = Number(ctx.query.gameId ?? 0)
      if (gameId) {
        const game = seed.seedGames.find((g) => g.id === gameId)
        if (game) list = list.filter((t) => t.gameName === game.name)
      }
      return paginate(list, ctx.query)
    },
  },
  {
    method: 'POST',
    path: `${P}/community/teams/:id/join`,
    auth: true,
    handler: (ctx) => {
      if (!isLoggedIn()) needLogin()
      return mutate((s) => {
        const t = s.teams.find((x) => x.id === Number(ctx.params.id))
        if (!t) throw new Error('队伍不存在或已解散')
        if (t.joined) {
          t.joined = false
          t.joinedCount = Math.max(1, t.joinedCount - 1)
        } else {
          if (t.joinedCount >= t.totalCount) throw new Error('队伍已满')
          t.joined = true
          t.joinedCount += 1
          t.needCount = Math.max(0, t.totalCount - t.joinedCount)
        }
        return { joined: t.joined, joinedCount: t.joinedCount }
      })
    },
  },

  /* ---------- 门店 / 其他 ---------- */
  {
    method: 'GET',
    path: `${P}/stores`,
    handler: (ctx) => {
      const keyword = String(ctx.query.keyword ?? '').trim()
      const list = seed.seedStores
      return keyword ? list.filter((s) => s.name.includes(keyword) || s.address.includes(keyword)) : list
    },
  },
  { method: 'GET', path: `${P}/seats/areas`, handler: () => seed.seedSeatAreas },
  {
    method: 'GET',
    path: `${P}/seats/map`,
    handler: (ctx) => {
      const areaId = Number(ctx.query.areaId ?? 1)
      const seats = seed.seedComputers[areaId] ?? seed.seedComputers[1]
      const area = seed.seedSeatAreas.find((a) => a.id === areaId) ?? seed.seedSeatAreas[0]
      return { id: areaId, areaName: area.areaName, freeCount: area.freeCount, totalCount: area.totalCount, computers: seats }
    },
  },
  {
    method: 'POST',
    path: `${P}/feedback`,
    handler: (ctx) => {
      const content = String(ctx.body.content ?? '').trim()
      if (!content) throw new Error('请填写反馈内容')
      if (content.length < 5) throw new Error('反馈内容至少 5 个字')
      return { feedbackId: Date.now() }
    },
  },
  {
    method: 'GET',
    path: `${P}/favorites`,
    handler: () => {
      const s = db()
      return {
        posts: s.posts.filter((p) => p.collected),
        products: seed.seedProducts.filter((p) => p.hot).slice(0, 2),
      }
    },
  },
  { method: 'GET', path: `${P}/home/activities`, handler: () => seed.seedHomeActivities },
  { method: 'GET', path: `${P}/home/banners`, handler: () => seed.seedBanners },
]
