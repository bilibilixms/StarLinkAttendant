/**
 * 优惠券接口。
 */
import { get, post } from './request'
import { API_PREFIX } from '@/config'
import type { Coupon, CouponTemplate } from '@/types/coupon'

/** 我的优惠券；status 不传返回全部 */
export function getMyCoupons(params?: { status?: number }): Promise<Coupon[]> {
  return get<Coupon[]>(`${API_PREFIX}/coupons/mine`, params as Record<string, unknown>)
}

/** 领券中心：可领取的券模板 */
export function getCouponTemplates(): Promise<CouponTemplate[]> {
  return get<CouponTemplate[]>(`${API_PREFIX}/coupons/templates`)
}

/** 领券 */
export function receiveCoupon(templateId: number): Promise<Coupon> {
  return post<Coupon>(`${API_PREFIX}/coupons/receive`, { templateId })
}
