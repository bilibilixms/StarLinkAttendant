/**
 * Mock 种子数据。
 *
 * 所有文案 / 数字尽量还原 5 张参考截图（xiaochengxu/reference/*.jpg），
 * 图片统一指向 src/static/images 下的本地占位图，不使用任何网络图片。
 */
import type { Member } from '@/types/member'
import type { Computer, SeatArea, SessionRecord } from '@/types/session'
import type { HotelRoomType, Reservation } from '@/types/reservation'
import type { Product, ProductCategory } from '@/types/product'
import type { ConsumeRecord, Order } from '@/types/order'
import type { Coupon, CouponTemplate } from '@/types/coupon'
import type { Game, GameTask } from '@/types/game'
import type { Post, PostComment, TeamRecruit } from '@/types/community'
import type { PointsGoods, PointsRecord, RechargePlan, RechargeRecord, Store } from '@/types/store'

const IMG = '/static/images'

/* ==================== 会员 ==================== */

export const seedMember: Member = {
  id: 1,
  memberNo: 'M20260101001',
  realName: '陈浩宇',
  nickName: '阿宇',
  gender: 1,
  phone: '13811001001',
  avatar: '',
  birthday: '1995-03-15',
  levelId: 4,
  levelName: '钻石会员',
  availablePoints: 12400,
  totalPoints: 28600,
  balance: 860.0,
  totalRecharge: 5000.0,
  totalConsumption: 4140.0,
  totalOnlineHours: 3600,
  status: 1,
  verified: true,
  createdAt: '2026-01-01 10:00:00',
}

/* ==================== 首页 Banner / 活动 ==================== */

export const seedBanners = [
  {
    id: 1,
    title: '天天夺宝',
    subtitle: '1000网费等你拿',
    buttonText: '立即参与',
    theme: 'treasure',
    image: `${IMG}/banner/treasure.jpg`,
  },
  {
    id: 2,
    title: '新会员礼包',
    subtitle: '注册即送 3 小时免费上网',
    buttonText: '立即领取',
    theme: 'newcomer',
    image: `${IMG}/banner/newcomer.jpg`,
  },
  {
    id: 3,
    title: '电竞酒店特惠',
    subtitle: '会员预订低至 5 折',
    buttonText: '去看看',
    theme: 'hotel',
    image: `${IMG}/banner/hotel.jpg`,
  },
]

export const seedHomeActivities = [
  { key: 'invite', title: '邀新客', subtitle: '3小时免费上网', action: '查看', theme: 'lavender' },
  { key: 'didi', title: '滴滴打车', subtitle: '到网鱼5折起', action: '查看', theme: 'orange' },
  { key: 'treasure', title: '天天夺宝', subtitle: '每天免费抽', action: '免费', theme: 'pink' },
  { key: 'sign', title: '签到领奖', subtitle: '做任务赢奖励', action: '签到', theme: 'blue' },
]

/* ==================== 服务页功能宫格 ==================== */

export const seedServiceGrid = [
  {
    group: '网鱼电竞服务有什么？',
    items: [
      { key: 'reserve', title: '预约订座', desc: '提前锁定位置', icon: 'svc-seat', theme: 'cyan' },
      { key: 'scan', title: '扫码上机', desc: '一键光速开机', icon: 'svc-power', theme: 'blue' },
      { key: 'remote-end', title: '远程下机', desc: '挂机不误事', icon: 'svc-remote', theme: 'sky' },
      { key: 'order-food', title: '自助点餐', desc: '一键下单送到位', icon: 'svc-food', theme: 'mint' },
      { key: 'recharge', title: '在线充值', desc: '在线充值享优惠', icon: 'svc-wallet', theme: 'teal' },
      { key: 'feedback', title: '意见反馈', desc: '倾听您的建议', icon: 'svc-chat', theme: 'green' },
    ],
  },
  {
    group: '电竞酒店服务有什么？',
    items: [
      { key: 'hotel-book', title: '预约订房', desc: '在线预订房间', icon: 'svc-hotel', theme: 'cyan' },
      { key: 'hotel-game', title: '游戏特权', desc: '热门游戏任你选', icon: 'svc-game', theme: 'blue' },
      { key: 'hotel-service', title: '客房服务', desc: '服务员快速上门', icon: 'svc-bed', theme: 'mint' },
      { key: 'hotel-wifi', title: '免费WIFI', desc: '一键连接欢乐玩', icon: 'svc-wifi', theme: 'sky' },
      { key: 'hotel-invoice', title: '开房票', desc: '在线申请开票', icon: 'svc-invoice', theme: 'teal' },
      { key: 'feedback', title: '意见反馈', desc: '倾听您的建议', icon: 'svc-chat', theme: 'green' },
    ],
  },
]

/* ==================== 门店 ==================== */

export const seedStores: Store[] = [
  {
    id: 1,
    name: '网鱼电竞(上海虹桥火车站天街店)',
    address: '上海市闵行区申长路 688 号龙湖天街 4F',
    distance: 0.8,
    businessStatus: 1,
    businessHours: '24小时营业',
    phone: '021-6237 8888',
    freeSeats: 46,
    totalSeats: 180,
    cover: `${IMG}/store/store-1.jpg`,
    tags: ['电竞酒店', '包间', '24小时'],
  },
  {
    id: 2,
    name: '网鱼电竞(上海中山公园龙之梦店)',
    address: '上海市长宁区长宁路 1018 号龙之梦 7F',
    distance: 4.2,
    businessStatus: 1,
    businessHours: '24小时营业',
    phone: '021-6237 6666',
    freeSeats: 23,
    totalSeats: 150,
    cover: `${IMG}/store/store-2.jpg`,
    tags: ['赛事中心', '水冷机位'],
  },
  {
    id: 3,
    name: '网鱼电竞(上海静安大悦城店)',
    address: '上海市静安区西藏北路 166 号大悦城 6F',
    distance: 6.7,
    businessStatus: 0,
    businessHours: '10:00 - 次日 02:00',
    phone: '021-6237 5555',
    freeSeats: 0,
    totalSeats: 120,
    cover: `${IMG}/store/store-3.jpg`,
    tags: ['情侣包间'],
  },
]

/* ==================== 区域 / 机位 ==================== */

export const seedSeatAreas: SeatArea[] = [
  { id: 1, areaName: '普通区', areaType: 1, hourlyRate: 4, freeCount: 28, totalCount: 90, description: 'i5-12400F / RTX 3060 / 1080P 144Hz' },
  { id: 2, areaName: '高级区', areaType: 2, hourlyRate: 6, freeCount: 12, totalCount: 48, description: 'i7-13700K / RTX 4070 / 2K 165Hz' },
  { id: 3, areaName: '豪华包间', areaType: 3, hourlyRate: 12, freeCount: 4, totalCount: 12, description: 'i9-14900K / RTX 4080 / 2K 240Hz' },
  { id: 4, areaName: '电竞酒店', areaType: 4, hourlyRate: 20, freeCount: 2, totalCount: 30, description: '双人房 / 四人房，含独立卫浴' },
]

/** 生成座位图数据：每个区域 6 列 × N 行 */
function buildSeatMap(areaId: number, cols: number, rows: number, occupied: number[]): Computer[] {
  const area = seedSeatAreas.find((a) => a.id === areaId)!
  const seats: Computer[] = []
  for (let r = 0; r < rows; r += 1) {
    for (let c = 0; c < cols; c += 1) {
      const index = r * cols + c
      const no = `${String.fromCharCode(65 + r)}${String(c + 1).padStart(2, '0')}`
      seats.push({
        id: areaId * 1000 + index + 1,
        computerNo: no,
        computerName: `${area.areaName}-${no}`,
        areaId,
        areaName: area.areaName,
        status: occupied.includes(index) ? 1 : 0,
        spec: area.description,
        hourlyRate: area.hourlyRate,
        rowIndex: r,
        colIndex: c,
      })
    }
  }
  return seats
}

export const seedComputers: Record<number, Computer[]> = {
  1: buildSeatMap(1, 6, 6, [2, 3, 8, 11, 15, 20, 26, 31]),
  2: buildSeatMap(2, 6, 4, [1, 6, 9, 14, 18]),
  3: buildSeatMap(3, 6, 3, [4, 7]),
  4: buildSeatMap(4, 6, 2, []),
}

/* ==================== 商品 ==================== */

export const seedCategories: ProductCategory[] = [
  { id: 1, name: '饮料', icon: 'cat-drink', sort: 1 },
  { id: 2, name: '零食', icon: 'cat-snack', sort: 2 },
  { id: 3, name: '泡面', icon: 'cat-noodle', sort: 3 },
  { id: 4, name: '套餐', icon: 'cat-combo', sort: 4 },
  { id: 5, name: '虚拟商品', icon: 'cat-virtual', sort: 5 },
]

export const seedProducts: Product[] = [
  // 饮料
  { id: 101, name: '可口可乐', categoryId: 1, categoryName: '饮料', type: 1, price: 5, memberPrice: 4.5, spec: '330ml', unit: '罐', stock: 240, sales: 1860, cover: `${IMG}/product/cola.jpg`, tags: ['冰镇'], status: 1, hot: true },
  { id: 102, name: '红牛维生素功能饮料', categoryId: 1, categoryName: '饮料', type: 1, price: 8, memberPrice: 7, spec: '250ml', unit: '罐', stock: 120, sales: 940, cover: `${IMG}/product/redbull.jpg`, tags: ['提神'], status: 1, hot: true },
  { id: 103, name: '农夫山泉', categoryId: 1, categoryName: '饮料', type: 1, price: 3, memberPrice: 2.5, spec: '550ml', unit: '瓶', stock: 300, sales: 2210, cover: `${IMG}/product/water.jpg`, status: 1 },
  { id: 104, name: '冰红茶', categoryId: 1, categoryName: '饮料', type: 1, price: 4, memberPrice: 3.5, spec: '500ml', unit: '瓶', stock: 180, sales: 1230, cover: `${IMG}/product/tea.jpg`, status: 1 },
  { id: 105, name: '东鹏特饮', categoryId: 1, categoryName: '饮料', type: 1, price: 6, memberPrice: 5, spec: '500ml', unit: '瓶', stock: 150, sales: 760, cover: `${IMG}/product/dongpeng.jpg`, tags: ['电竞必备'], status: 1 },
  // 零食
  { id: 201, name: '乐事薯片', categoryId: 2, categoryName: '零食', type: 1, price: 7, memberPrice: 6, spec: '70g', unit: '袋', stock: 96, sales: 620, cover: `${IMG}/product/chips.jpg`, status: 1 },
  { id: 202, name: '奥利奥饼干', categoryId: 2, categoryName: '零食', type: 1, price: 9, memberPrice: 8, spec: '116g', unit: '盒', stock: 78, sales: 410, cover: `${IMG}/product/oreo.jpg`, status: 1 },
  { id: 203, name: '好丽友派', categoryId: 2, categoryName: '零食', type: 1, price: 12, memberPrice: 10.5, spec: '6枚', unit: '盒', stock: 60, sales: 280, cover: `${IMG}/product/pie.jpg`, status: 1 },
  { id: 204, name: '牛肉干', categoryId: 2, categoryName: '零食', type: 1, price: 18, memberPrice: 16, spec: '50g', unit: '袋', stock: 45, sales: 190, cover: `${IMG}/product/beef.jpg`, status: 1 },
  // 泡面
  { id: 301, name: '康师傅红烧牛肉面', categoryId: 3, categoryName: '泡面', type: 1, price: 6, memberPrice: 5.5, spec: '桶装', unit: '桶', stock: 120, sales: 1480, cover: `${IMG}/product/noodle1.jpg`, tags: ['吧台加热'], status: 1, hot: true },
  { id: 302, name: '统一老坛酸菜面', categoryId: 3, categoryName: '泡面', type: 1, price: 6, memberPrice: 5.5, spec: '桶装', unit: '桶', stock: 110, sales: 890, cover: `${IMG}/product/noodle2.jpg`, status: 1 },
  { id: 303, name: '自热米饭', categoryId: 3, categoryName: '泡面', type: 1, price: 16, memberPrice: 14, spec: '260g', unit: '盒', stock: 40, sales: 210, cover: `${IMG}/product/rice.jpg`, status: 1 },
  // 套餐
  { id: 401, name: '双人开黑夜宵套餐', categoryId: 4, categoryName: '套餐', type: 3, price: 68, memberPrice: 58, spec: '2份主食+2杯饮料', unit: '份', stock: 30, sales: 320, cover: `${IMG}/product/combo1.jpg`, tags: ['省15元'], status: 1, hot: true },
  { id: 402, name: '单人能量补给套餐', categoryId: 4, categoryName: '套餐', type: 3, price: 32, memberPrice: 26, spec: '1份主食+1罐红牛', unit: '份', stock: 50, sales: 640, cover: `${IMG}/product/combo2.jpg`, tags: ['省8元'], status: 1 },
  { id: 403, name: '四人开黑套餐', categoryId: 4, categoryName: '套餐', type: 3, price: 138, memberPrice: 118, spec: '4份主食+4杯饮料+2份零食', unit: '份', stock: 20, sales: 150, cover: `${IMG}/product/combo3.jpg`, tags: ['省32元'], status: 1 },
  // 虚拟商品
  { id: 501, name: '10元网费', categoryId: 5, categoryName: '虚拟商品', type: 2, price: 10, memberPrice: 10, stock: 9999, sales: 3200, cover: `${IMG}/product/vnet.jpg`, tags: ['即时到账'], status: 1 },
  { id: 502, name: '5小时畅玩时长卡', categoryId: 5, categoryName: '虚拟商品', type: 2, price: 20, memberPrice: 18, stock: 9999, sales: 1680, cover: `${IMG}/product/vtime.jpg`, tags: ['普通区'], status: 1 },
  { id: 503, name: '通宵包夜卡 (23:00-08:00)', categoryId: 5, categoryName: '虚拟商品', type: 2, price: 35, memberPrice: 30, stock: 9999, sales: 2240, cover: `${IMG}/product/vnight.jpg`, tags: ['超值'], status: 1, hot: true },
]

/* ==================== 充值套餐 ==================== */

export const seedRechargePlans: RechargePlan[] = [
  { id: 1, amount: 50, bonus: 0, actualAmount: 50, description: '适合临时上网' },
  { id: 2, amount: 100, bonus: 10, actualAmount: 110, bonusPoints: 100, tag: '热门', recommend: true, description: '到账 110 元' },
  { id: 3, amount: 200, bonus: 30, actualAmount: 230, bonusPoints: 240, tag: '最划算', description: '到账 230 元' },
  { id: 4, amount: 500, bonus: 100, actualAmount: 600, bonusPoints: 700, tag: '土豪专属', description: '到账 600 元' },
  { id: 5, amount: 1000, bonus: 260, actualAmount: 1260, bonusPoints: 1600, tag: '超值', description: '到账 1260 元' },
]

/* ==================== 优惠券 ==================== */

export const seedCoupons: Coupon[] = [
  { id: 1, templateId: 1, name: '网费满 50 减 10', type: 1, value: 10, minAmount: 50, scope: 2, scopeText: '网费充值可用', status: 0, startTime: '2026-09-01 00:00:00', endTime: '2026-10-31 23:59:59', description: '不可与其他优惠叠加' },
  { id: 2, templateId: 2, name: '点单满 30 减 5', type: 1, value: 5, minAmount: 30, scope: 3, scopeText: '自助点餐可用', status: 0, startTime: '2026-09-01 00:00:00', endTime: '2026-09-30 23:59:59', description: '仅限吧台商品' },
  { id: 3, templateId: 3, name: '全场 8.5 折券', type: 2, value: 8.5, minAmount: 0, scope: 1, scopeText: '全场通用', status: 0, startTime: '2026-09-01 00:00:00', endTime: '2026-09-25 23:59:59', description: '最高抵扣 30 元' },
  { id: 4, templateId: 4, name: '酒店满 200 减 50', type: 1, value: 50, minAmount: 200, scope: 4, scopeText: '电竞酒店可用', status: 1, startTime: '2026-08-01 00:00:00', endTime: '2026-09-10 23:59:59', description: '已使用' },
  { id: 5, templateId: 5, name: '新人 3 小时免费券', type: 3, value: 3, minAmount: 0, scope: 2, scopeText: '普通区可用', status: 2, startTime: '2026-07-01 00:00:00', endTime: '2026-07-31 23:59:59', description: '已过期' },
]

export const seedCouponTemplates: CouponTemplate[] = [
  { id: 11, name: '网费满 100 减 20', type: 1, value: 20, minAmount: 100, scope: 2, scopeText: '网费充值可用', startTime: '2026-09-01 00:00:00', endTime: '2026-10-31 23:59:59', description: '每人限领 1 张', remainCount: 862, receivedCount: 0, limitPerMember: 1 },
  { id: 12, name: '点单满 50 减 12', type: 1, value: 12, minAmount: 50, scope: 3, scopeText: '自助点餐可用', startTime: '2026-09-01 00:00:00', endTime: '2026-10-15 23:59:59', description: '吧台商品通用', remainCount: 431, receivedCount: 0, limitPerMember: 2 },
  { id: 13, name: '夜宵套餐 7 折券', type: 2, value: 7, minAmount: 0, scope: 3, scopeText: '套餐商品可用', startTime: '2026-09-01 00:00:00', endTime: '2026-09-30 23:59:59', description: '仅限套餐分类', remainCount: 156, receivedCount: 1, limitPerMember: 1 },
  { id: 14, name: '电竞酒店 8 折券', type: 2, value: 8, minAmount: 0, scope: 4, scopeText: '电竞酒店可用', startTime: '2026-09-01 00:00:00', endTime: '2026-11-30 23:59:59', description: '需提前 1 天预订', remainCount: 78, receivedCount: 0, limitPerMember: 1 },
]

/* ==================== 游戏 & 任务 ==================== */

/**
 * 游戏分类。
 *
 * `logo` 留空 → 组件用「品牌色块 + 短名」兜底渲染。
 * 拿到官方 logo 后，把图片放到 src/static/images/game/ 并把路径填进来即可，例如：
 *   logo: `${IMG}/game/logo-lol.png`
 * 页面代码不需要任何改动。
 */
export const seedGames: Game[] = [
  { id: 1, name: '失控进化', short: '失控', icon: 'game-evolution', color: '#E8734A', logo: '', taskCount: 1 },
  { id: 2, name: 'FC Online', short: 'FC', icon: 'game-fc', color: '#3EA76B', logo: '', taskCount: 0 },
  { id: 3, name: '无畏契约', short: '无畏', icon: 'game-valorant', color: '#FF4655', logo: '', taskCount: 1 },
  { id: 4, name: '英雄联盟', short: '英雄', icon: 'game-lol', color: '#C89B3C', logo: '', taskCount: 4 },
  { id: 5, name: '三角洲', short: '三角', icon: 'game-delta', color: '#2FBFA8', logo: '', taskCount: 2 },
  { id: 6, name: '绝地求生', short: '绝地', icon: 'game-pubg', color: '#F2A93B', logo: '', taskCount: 2 },
  { id: 7, name: '穿越火线', short: '穿越', icon: 'game-cf', color: '#D63B3B', logo: '', taskCount: 0 },
]

/** 剩余时间用「结束时间戳」表达，前端实时倒计时（对齐截图的 12:31:43 效果） */
const NOW = Date.now()
const HOUR = 3600 * 1000

export const seedGameTasks: GameTask[] = [
  {
    id: 1,
    gameId: 4,
    gameName: '英雄联盟',
    title: '云顶|对局1000分钟|领小小英雄',
    description: '活动期间累计进行云顶之弈对局满 1000 分钟，即可领取随机小小英雄蛋一枚。',
    progress: 0,
    target: 1000,
    unit: '分钟',
    reward: '小小英雄蛋',
    thumbs: [`${IMG}/game/task1-a.jpg`, `${IMG}/game/task1-b.jpg`, `${IMG}/game/task1-c.jpg`, `${IMG}/game/task1-d.jpg`],
    endText: '11天后结束',
    remainCount: 0,
    status: 0,
  },
  {
    id: 2,
    gameId: 4,
    gameName: '英雄联盟',
    title: '回归玩家｜玩1局|赢永久皮肤',
    description: '回归玩家专属：活动期间完成 1 局匹配或排位，即可领取永久武器皮肤。',
    progress: 0,
    target: 1,
    unit: '局',
    reward: '永久皮肤',
    thumbs: [`${IMG}/game/task2-a.jpg`, `${IMG}/game/task2-b.jpg`, `${IMG}/game/task2-c.jpg`, `${IMG}/game/task2-d.jpg`],
    endText: '10天后结束',
    remainCount: 13159,
    status: 0,
  },
  {
    id: 3,
    gameId: 4,
    gameName: '英雄联盟',
    title: '云顶|前4名|赢小小英雄',
    description: '云顶之弈对局进入前 4 名即可参与抽取，每日限量发放。',
    progress: 0,
    target: 1,
    unit: '次',
    reward: '小小英雄',
    thumbs: [`${IMG}/game/task3-a.jpg`, `${IMG}/game/task3-b.jpg`, `${IMG}/game/task3-c.jpg`, `${IMG}/game/task3-d.jpg`],
    endText: '12:31:43后结束',
    endTimestamp: NOW + 12 * HOUR + 31 * 60 * 1000 + 43000,
    remainCount: 1379,
    status: 0,
  },
  {
    id: 4,
    gameId: 4,
    gameName: '英雄联盟',
    title: '5V5|赢1局|赢永久皮肤',
    description: '召唤师峡谷 5V5 匹配或排位获胜 1 局，即可领取永久皮肤宝箱。',
    progress: 0,
    target: 1,
    unit: '局',
    reward: '永久皮肤',
    thumbs: [`${IMG}/game/task4-a.jpg`, `${IMG}/game/task4-b.jpg`, `${IMG}/game/task4-c.jpg`, `${IMG}/game/task4-d.jpg`],
    endText: '12:31:43后结束',
    endTimestamp: NOW + 12 * HOUR + 31 * 60 * 1000 + 43000,
    remainCount: 14034,
    status: 0,
  },
  {
    id: 5,
    gameId: 5,
    gameName: '三角洲',
    title: '洲年空投|累计撤离3次|领取战备箱',
    description: '活动期间在「烽火地带」模式累计成功撤离 3 次，即可领取战备物资箱。',
    progress: 1,
    target: 3,
    unit: '次',
    reward: '战备物资箱',
    thumbs: [`${IMG}/game/task5-a.jpg`, `${IMG}/game/task5-b.jpg`, `${IMG}/game/task5-c.jpg`, `${IMG}/game/task5-d.jpg`],
    endText: '25天后结束',
    remainCount: 8642,
    status: 0,
  },
  {
    id: 6,
    gameId: 6,
    gameName: '绝地求生',
    title: '每日首胜|吃鸡1局|领BP补给包',
    description: '每日首次吃鸡可领取 BP 与补给箱，每日刷新。',
    progress: 0,
    target: 1,
    unit: '局',
    reward: 'BP 补给包',
    thumbs: [`${IMG}/game/task6-a.jpg`, `${IMG}/game/task6-b.jpg`, `${IMG}/game/task6-c.jpg`, `${IMG}/game/task6-d.jpg`],
    endText: '今日 23:59 结束',
    remainCount: 0,
    status: 0,
  },
  {
    id: 7,
    gameId: 3,
    gameName: '无畏契约',
    title: '每日首战|完成1局竞技|领VP奖励',
    description: '每日完成 1 局竞技模式对局即可领取 VP 与战斗通行证经验。',
    progress: 0,
    target: 1,
    unit: '局',
    reward: 'VP 奖励',
    thumbs: [`${IMG}/game/task2-a.jpg`, `${IMG}/game/task2-c.jpg`, `${IMG}/game/task4-b.jpg`, `${IMG}/game/task3-d.jpg`],
    endText: '每日 24:00 刷新',
    remainCount: 0,
    status: 0,
  },
  {
    id: 8,
    gameId: 1,
    gameName: '失控进化',
    title: '生存挑战|存活15分钟|领进化核心',
    description: '在任意模式中单局存活满 15 分钟，即可领取进化核心与限定涂装。',
    progress: 0,
    target: 15,
    unit: '分钟',
    reward: '进化核心',
    thumbs: [`${IMG}/game/task5-b.jpg`, `${IMG}/game/task6-a.jpg`, `${IMG}/game/task1-c.jpg`, `${IMG}/game/task3-a.jpg`],
    endText: '18天后结束',
    remainCount: 2140,
    status: 0,
  },
]

/* ==================== 社区 ==================== */

const officialAuthor = { id: 9001, name: '游戏资讯推荐', avatar: `${IMG}/avatar/official.jpg`, badge: '官方', level: 5 }

export const seedPosts: Post[] = [
  {
    id: 1,
    author: officialAuthor,
    title: 'VCT CN | slowly获第二赛段FMVP',
    content:
      '“保持一颗不服输的心” | 2026 VCT CN 赛季最佳选手 TYL slowly，slowly选用了12位英雄，取得了13次单场…',
    topics: ['VCT CN', '无畏契约'],
    contentType: 'video',
    images: [`${IMG}/community/post1-cover.jpg`],
    videoUrl: '',
    videoDuration: '03:15',
    storeName: '上海虹桥火车站天街店',
    storeId: 1,
    shareCount: 39,
    commentCount: 40,
    likeCount: 102,
    liked: false,
    collected: false,
    createdAt: '2026-09-16 14:20:00',
    timeText: '09-16',
  },
  {
    id: 2,
    author: officialAuthor,
    title: '三角洲9月17日更新公告',
    content: '9月17日更新公告 | 洲年空投活动开启 & 干员平衡性调整！#三角洲行动',
    topics: ['三角洲行动'],
    contentType: 'image',
    images: [
      `${IMG}/community/post2-1.jpg`,
      `${IMG}/community/post2-2.jpg`,
      `${IMG}/community/post2-3.jpg`,
      `${IMG}/community/post2-4.jpg`,
      `${IMG}/community/post2-5.jpg`,
      `${IMG}/community/post2-6.jpg`,
      `${IMG}/community/post2-7.jpg`,
    ],
    storeName: '上海虹桥火车站天街店',
    storeId: 1,
    shareCount: 6,
    commentCount: 11,
    likeCount: 64,
    liked: true,
    collected: false,
    createdAt: '2026-09-17 10:05:00',
    timeText: '09-17',
  },
  {
    id: 3,
    author: { id: 9002, name: '网鱼电竞官方', avatar: `${IMG}/avatar/official2.jpg`, badge: '官方', level: 5 },
    title: '九月会员日｜充值满 200 送 50 网费',
    content: '每月 18 日会员日，充值满 200 元赠送 50 元网费，另有抽奖机会赢取机械键盘。',
    topics: ['会员日', '充值活动'],
    contentType: 'image',
    images: [`${IMG}/community/post3-1.jpg`],
    storeName: '上海中山公园龙之梦店',
    storeId: 2,
    shareCount: 128,
    commentCount: 76,
    likeCount: 386,
    liked: false,
    collected: true,
    createdAt: '2026-09-15 09:00:00',
    timeText: '09-15',
  },
  {
    id: 4,
    author: { id: 9003, name: '打野不上线', avatar: `${IMG}/avatar/u1.jpg`, level: 3 },
    title: '虹桥天街店新上了 4070 机位，实测帧数拉满',
    content: '今天去虹桥天街店，高级区换成了 4070 + 2K 165Hz，三角洲全高画质稳定 140 帧以上，强烈推荐。',
    topics: ['三角洲行动', '开黑体验'],
    contentType: 'image',
    images: [`${IMG}/community/post4-1.jpg`, `${IMG}/community/post4-2.jpg`],
    storeName: '上海虹桥火车站天街店',
    storeId: 1,
    shareCount: 12,
    commentCount: 23,
    likeCount: 89,
    liked: false,
    collected: false,
    createdAt: '2026-09-14 21:40:00',
    timeText: '09-14',
  },
]

export const seedComments: PostComment[] = [
  { id: 1, author: { id: 9101, name: '峡谷风清扬', avatar: `${IMG}/avatar/u2.jpg`, level: 3 }, content: 'slowly 这赛季真的顶，FMVP 实至名归', likeCount: 42, liked: false, createdAt: '2026-09-16 15:02:00', timeText: '09-16' },
  { id: 2, author: { id: 9102, name: '枪法如神', avatar: `${IMG}/avatar/u3.jpg`, level: 2 }, content: '什么时候出完整采访视频？', likeCount: 18, liked: false, createdAt: '2026-09-16 15:40:00', timeText: '09-16' },
  { id: 3, author: { id: 9103, name: '夜宵驱动器', avatar: `${IMG}/avatar/u4.jpg`, level: 4 }, content: '天街店网速也快，昨天刚去过', likeCount: 9, liked: false, createdAt: '2026-09-16 18:11:00', timeText: '09-16' },
]

export const seedTeams: TeamRecruit[] = [
  {
    id: 1,
    gameName: '英雄联盟',
    gameIcon: '英雄',
    gameColor: '#C89B3C',
    rank: '王者段位',
    startTimeText: '今晚 20:00',
    needCount: 2,
    joinedCount: 3,
    totalCount: 5,
    ownerName: '打野不上线',
    ownerAvatar: `${IMG}/avatar/u1.jpg`,
    remark: '缺中单和辅助，不开麦勿扰',
    joined: false,
    online: true,
    createdAt: '2026-09-19 10:20:00',
    timeText: '10:20',
  },
  {
    id: 2,
    gameName: '无畏契约',
    gameIcon: '无畏',
    gameColor: '#FF4655',
    rank: '不朽段位',
    startTimeText: '今晚 21:30',
    needCount: 3,
    joinedCount: 2,
    totalCount: 5,
    ownerName: '枪法如神',
    ownerAvatar: `${IMG}/avatar/u3.jpg`,
    remark: '排位上分，要求 KDA 1.0 以上',
    joined: false,
    online: true,
    createdAt: '2026-09-19 09:45:00',
    timeText: '09:45',
  },
  {
    id: 3,
    gameName: '三角洲',
    gameIcon: '三角',
    gameColor: '#2FBFA8',
    rank: '烽火地带',
    startTimeText: '现在',
    needCount: 1,
    joinedCount: 2,
    totalCount: 3,
    ownerName: '夜宵驱动器',
    ownerAvatar: `${IMG}/avatar/u4.jpg`,
    remark: '缺一个老哥，装备自带',
    joined: true,
    online: true,
    createdAt: '2026-09-19 11:05:00',
    timeText: '11:05',
  },
  {
    id: 4,
    gameName: '绝地求生',
    gameIcon: '绝地',
    gameColor: '#F2A93B',
    rank: '钻石段位',
    startTimeText: '明晚 19:00',
    needCount: 2,
    joinedCount: 2,
    totalCount: 4,
    ownerName: '落地成盒王',
    ownerAvatar: `${IMG}/avatar/u5.jpg`,
    remark: '娱乐四排，随意玩',
    joined: false,
    online: false,
    createdAt: '2026-09-19 08:30:00',
    timeText: '08:30',
  },
]

/* ==================== 订单 / 消费记录 ==================== */

export const seedOrders: Order[] = [
  {
    id: 1,
    orderNo: 'OD20260710001',
    orderType: 1,
    memberId: 1,
    storeName: '网鱼电竞(上海虹桥火车站天街店)',
    seatNo: 'A03',
    totalAmount: 19,
    discountAmount: 1,
    payableAmount: 18,
    paidAmount: 18,
    status: 1,
    statusText: '已完成',
    items: [
      { id: 1, productId: 101, productName: '可口可乐', cover: `${IMG}/product/cola.jpg`, spec: '330ml', price: 4.5, quantity: 2, amount: 9 },
      { id: 2, productId: 201, productName: '乐事薯片', cover: `${IMG}/product/chips.jpg`, spec: '70g', price: 6, quantity: 1, amount: 6 },
      { id: 3, productId: 301, productName: '康师傅红烧牛肉面', cover: `${IMG}/product/noodle1.jpg`, spec: '桶装', price: 5.5, quantity: 1, amount: 5.5 },
    ],
    paidAt: '2026-07-10 14:30:00',
    createdAt: '2026-07-10 14:28:00',
  },
  {
    id: 2,
    orderNo: 'OD20260713002',
    orderType: 1,
    memberId: 1,
    storeName: '网鱼电竞(上海虹桥火车站天街店)',
    seatNo: 'B12',
    totalAmount: 68,
    discountAmount: 10,
    payableAmount: 58,
    paidAmount: 58,
    status: 1,
    statusText: '已完成',
    items: [
      { id: 4, productId: 401, productName: '双人开黑夜宵套餐', cover: `${IMG}/product/combo1.jpg`, spec: '2份主食+2杯饮料', price: 58, quantity: 1, amount: 58 },
    ],
    paidAt: '2026-07-13 01:12:00',
    createdAt: '2026-07-13 01:10:00',
  },
  {
    id: 3,
    orderNo: 'OD20260919003',
    orderType: 1,
    memberId: 1,
    storeName: '网鱼电竞(上海虹桥火车站天街店)',
    seatNo: 'A06',
    totalAmount: 32,
    discountAmount: 0,
    payableAmount: 32,
    paidAmount: 0,
    status: 0,
    statusText: '待支付',
    items: [
      { id: 5, productId: 402, productName: '单人能量补给套餐', cover: `${IMG}/product/combo2.jpg`, spec: '1份主食+1罐红牛', price: 26, quantity: 1, amount: 26 },
      { id: 6, productId: 105, productName: '东鹏特饮', cover: `${IMG}/product/dongpeng.jpg`, spec: '500ml', price: 5, quantity: 1, amount: 5 },
    ],
    createdAt: '2026-09-19 09:30:00',
  },
  {
    id: 4,
    orderNo: 'OD20260919004',
    orderType: 1,
    memberId: 1,
    storeName: '网鱼电竞(上海虹桥火车站天街店)',
    seatNo: 'A06',
    totalAmount: 20,
    discountAmount: 2,
    payableAmount: 18,
    paidAmount: 18,
    status: 1,
    statusText: '吧台备餐中',
    items: [
      { id: 7, productId: 502, productName: '5小时畅玩时长卡', cover: `${IMG}/product/vtime.jpg`, spec: '普通区', price: 18, quantity: 1, amount: 18 },
    ],
    paidAt: '2026-09-19 11:20:00',
    createdAt: '2026-09-19 11:18:00',
  },
  {
    id: 5,
    orderNo: 'OD20260705005',
    orderType: 1,
    memberId: 1,
    storeName: '网鱼电竞(上海中山公园龙之梦店)',
    totalAmount: 22,
    discountAmount: 2,
    payableAmount: 20,
    paidAmount: 20,
    status: 3,
    statusText: '已退款',
    items: [
      { id: 8, productId: 202, productName: '奥利奥饼干', cover: `${IMG}/product/oreo.jpg`, spec: '116g', price: 8, quantity: 1, amount: 8 },
      { id: 9, productId: 204, productName: '牛肉干', cover: `${IMG}/product/beef.jpg`, spec: '50g', price: 12, quantity: 1, amount: 12 },
    ],
    paidAt: '2026-07-05 05:15:00',
    createdAt: '2026-07-05 05:10:00',
  },
]

export const seedSessionRecords: SessionRecord[] = [
  { id: 1, sessionNo: 'SES202607141957218486', storeName: '网鱼电竞(上海虹桥火车站天街店)', computerName: '普通区-A01', areaName: '普通区', startTime: '2026-07-14 19:57:21', endTime: '2026-07-14 23:40:00', durationMinutes: 223, totalAmount: 15, paidAmount: 15, status: 2 },
  { id: 2, sessionNo: 'SES202607121400001', storeName: '网鱼电竞(上海虹桥火车站天街店)', computerName: '普通区-B06', areaName: '普通区', startTime: '2026-07-12 14:00:00', endTime: '2026-07-12 18:00:00', durationMinutes: 240, totalAmount: 17, paidAmount: 17, status: 2 },
  { id: 3, sessionNo: 'SES202607112000002', storeName: '网鱼电竞(上海中山公园龙之梦店)', computerName: '高级区-C02', areaName: '高级区', startTime: '2026-07-11 20:00:00', endTime: '2026-07-11 22:15:00', durationMinutes: 135, totalAmount: 26, paidAmount: 22.1, status: 2 },
  { id: 4, sessionNo: 'SES202607100930003', storeName: '网鱼电竞(上海虹桥火车站天街店)', computerName: '普通区-A09', areaName: '普通区', startTime: '2026-07-10 09:30:00', endTime: '2026-07-10 12:00:00', durationMinutes: 150, totalAmount: 10, paidAmount: 10, status: 2 },
]

export const seedRechargeRecords: RechargeRecord[] = [
  { id: 1, rechargeNo: 'RC20260901001', amount: 200, bonus: 30, actualAmount: 230, payChannel: 'wechat', payChannelText: '微信支付', createdAt: '2026-09-01 12:00:00' },
  { id: 2, rechargeNo: 'RC20260815002', amount: 100, bonus: 10, actualAmount: 110, payChannel: 'wechat', payChannelText: '微信支付', createdAt: '2026-08-15 19:20:00' },
  { id: 3, rechargeNo: 'RC20260720003', amount: 500, bonus: 100, actualAmount: 600, payChannel: 'alipay', payChannelText: '支付宝', createdAt: '2026-07-20 21:05:00' },
]

export const seedConsumeRecords: ConsumeRecord[] = [
  { id: 1, type: 'order', title: '自助点餐', subtitle: '双人开黑夜宵套餐 等 1 件', amount: -58, createdAt: '2026-07-13 01:12:00', orderNo: 'OD20260713002' },
  { id: 2, type: 'session', title: '上机消费', subtitle: '普通区-G06 · 223分钟', amount: -15, createdAt: '2026-07-14 23:40:00' },
  { id: 3, type: 'order', title: '自助点餐', subtitle: '可口可乐 等 3 件', amount: -18, createdAt: '2026-07-10 14:30:00', orderNo: 'OD20260710001' },
  { id: 4, type: 'recharge', title: '余额充值', subtitle: '微信支付 · 赠送 30 元', amount: 230, createdAt: '2026-09-01 12:00:00', orderNo: 'RC20260901001' },
  { id: 5, type: 'session', title: '上机消费', subtitle: '普通区-B06 · 240分钟', amount: -17, createdAt: '2026-07-12 18:00:00' },
]

/* ==================== 积分 ==================== */

export const seedPointsRecords: PointsRecord[] = [
  { id: 1, type: 2, changePoints: 700, balanceAfter: 12400, title: '充值返积分', description: '充值 500 元赠送', createdAt: '2026-07-20 21:05:00' },
  { id: 2, type: 1, changePoints: 120, balanceAfter: 11700, title: '消费获得积分', description: '自助点餐消费 58 元', createdAt: '2026-07-13 01:12:00' },
  { id: 3, type: 1, changePoints: 36, balanceAfter: 11580, title: '消费获得积分', description: '自助点餐消费 18 元', createdAt: '2026-07-10 14:30:00' },
  { id: 4, type: 3, changePoints: -2000, balanceAfter: 11544, title: '积分兑换', description: '兑换「网费满 60 减 20」券', createdAt: '2026-07-08 16:22:00' },
  { id: 5, type: 1, changePoints: 340, balanceAfter: 13544, title: '上机获得积分', description: '上机消费 17 元', createdAt: '2026-07-12 18:00:00' },
  { id: 6, type: 4, changePoints: -500, balanceAfter: 13204, title: '积分过期', description: '上年度积分到期扣减', createdAt: '2026-06-30 23:59:00' },
]

export const seedPointsGoods: PointsGoods[] = [
  { id: 1, name: '网费满 60 减 20 券', points: 2000, cover: `${IMG}/product/vnet.jpg`, stock: 200, description: '全门店网费可用' },
  { id: 2, name: '自制饮品兑换券', points: 800, cover: `${IMG}/product/tea.jpg`, stock: 500, description: '吧台自制饮品任选一杯' },
  { id: 3, name: '5 小时畅玩时长卡', points: 3500, cover: `${IMG}/product/vtime.jpg`, stock: 120, description: '普通区可用' },
  { id: 4, name: '通宵包夜卡', points: 5000, cover: `${IMG}/product/vnight.jpg`, stock: 60, description: '23:00 - 次日 08:00' },
]

/* ==================== 预约 / 酒店 ==================== */

export const seedReservations: Reservation[] = [
  {
    id: 1,
    reservationNo: 'RS20260918001',
    type: 1,
    storeId: 1,
    storeName: '网鱼电竞(上海虹桥火车站天街店)',
    areaId: 2,
    areaName: '高级区',
    computerId: 2003,
    computerName: '高级区-A03',
    startTime: '2026-09-20 19:00:00',
    endTime: '2026-09-20 23:00:00',
    durationMinutes: 240,
    peopleCount: 2,
    contactPhone: '13811001001',
    depositAmount: 0,
    status: 0,
    remark: '靠窗位置优先',
    createdAt: '2026-09-18 15:20:00',
  },
  {
    id: 2,
    reservationNo: 'RS20260910002',
    type: 2,
    storeId: 1,
    storeName: '网鱼电竞(上海虹桥火车站天街店)',
    roomTypeName: '电竞双人房',
    startTime: '2026-09-10 14:00:00',
    endTime: '2026-09-11 12:00:00',
    durationMinutes: 1320,
    peopleCount: 2,
    contactPhone: '13811001001',
    depositAmount: 0,
    status: 1,
    createdAt: '2026-09-08 10:00:00',
  },
]

export const seedHotelRoomTypes: HotelRoomType[] = [
  { id: 1, name: '电竞双人房', capacity: 2, price: 288, memberPrice: 238, availableCount: 6, cover: `${IMG}/hotel/room1.jpg`, tags: ['2台主机', '独立卫浴', '大床'] },
  { id: 2, name: '电竞四人开黑房', capacity: 4, price: 468, memberPrice: 398, availableCount: 3, cover: `${IMG}/hotel/room2.jpg`, tags: ['4台主机', '上下铺', '独立卫浴'] },
  { id: 3, name: '电竞五人战队房', capacity: 5, price: 588, memberPrice: 498, availableCount: 2, cover: `${IMG}/hotel/room3.jpg`, tags: ['5台主机', '战术桌', '投影仪'] },
  { id: 4, name: '情侣主题房', capacity: 2, price: 328, memberPrice: 268, availableCount: 4, cover: `${IMG}/hotel/room4.jpg`, tags: ['2台主机', '圆床', '氛围灯'] },
]
