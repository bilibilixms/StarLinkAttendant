/**
 * 接口层统一出口。
 * 页面按需 `import { getProducts } from '@/api'` 即可，
 * 也可以按模块 `import * as productApi from '@/api/product'`。
 */
export * from './request'
export * as authApi from './auth'
export * as memberApi from './member'
export * as sessionApi from './session'
export * as reservationApi from './reservation'
export * as productApi from './product'
export * as orderApi from './order'
export * as rechargeApi from './recharge'
export * as couponApi from './coupon'
export * as pointsApi from './points'
export * as gameApi from './game'
export * as communityApi from './community'
export * as storeApi from './store'
