/**
 * 应用级常量。所有 storage key 集中在这里，避免散落各处写字符串。
 */

/** 小程序接口统一前缀：后端 starlink-module-member 暴露的会员端接口前缀 */
export const API_PREFIX = '/api/member'

/** 本地缓存 key */
export const STORAGE_KEYS = {
  TOKEN: 'intcafe_token',
  MEMBER: 'intcafe_member',
  CART: 'intcafe_cart',
  MOCK_DB: 'intcafe_mock_db',
  CURRENT_STORE: 'intcafe_current_store',
  SEARCH_HISTORY: 'intcafe_search_history',
} as const

/** 默认门店（Demo 阶段单店部署，后端起来后换成门店列表接口） */
export const DEFAULT_STORE = {
  id: 1,
  name: '网鱼电竞(上海虹桥火车站天街店)',
  shortName: '上海虹桥火车站天街店',
  address: '上海市闵行区申长路 688 号龙湖天街 4F',
}

/**
 * TabBar 配置：5 个 Tab，顺序与截图一致。
 *
 * game 用 `glyph` 而不是 icon —— 参考截图里「游戏」Tab 是一个加粗的「玩」字，
 * 不是线性图标，所以单独用文字渲染（详见 AppTabBar.vue）。
 */
export const TAB_LIST = [
  { key: 'home', text: '首页', path: '/pages/index/index', icon: 'tab-home', activeIcon: 'tab-home-active' },
  { key: 'service', text: '服务', path: '/pages/service/index', icon: 'tab-service', activeIcon: 'tab-service-active' },
  { key: 'game', text: '游戏', path: '/pages/game/index', icon: 'tab-game', activeIcon: 'tab-game-active', glyph: '玩' },
  { key: 'community', text: '社区', path: '/pages/community/index', icon: 'tab-community', activeIcon: 'tab-community-active' },
  { key: 'mine', text: '我的', path: '/pages/mine/index', icon: 'tab-mine', activeIcon: 'tab-mine-active' },
] as const

/** 订单状态筛选 Tab */
export const ORDER_TABS = [
  { key: 'all', text: '全部' },
  { key: 'unpaid', text: '待支付' },
  { key: 'processing', text: '进行中' },
  { key: 'finished', text: '已完成' },
  { key: 'refund', text: '退款' },
] as const

/** 充值套餐的支付方式 */
export const PAY_CHANNELS = [
  { key: 'wechat', text: '微信支付', icon: 'pay-wechat' },
  { key: 'balance', text: '会员余额', icon: 'pay-balance' },
] as const
