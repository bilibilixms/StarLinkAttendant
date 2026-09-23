import request from '@/common/api/request'
import type { PageResult } from '@/common/api/types'
import type {
  CouponTemplate,
  Coupon,
  Campaign,
} from '../types'

export const api = {
  // ==================== 活动（campaigns） ====================
  getCampaigns(params: { page?: number; size?: number; campaignName?: string; campaignType?: number; status?: number } = {}) {
    return request.get<any, { data: PageResult<Campaign> }>('/api/marketing/campaigns', { params })
  },
  getCampaign(id: number) {
    return request.get<any, { data: Campaign }>(`/api/marketing/campaigns/${id}`)
  },
  createCampaign(data: Record<string, any>) {
    return request.post<any, { data: Campaign }>('/api/marketing/campaigns', data)
  },
  updateCampaign(id: number, data: Record<string, any>) {
    return request.put<any, { data: Campaign }>(`/api/marketing/campaigns/${id}`, data)
  },
  deleteCampaign(id: number) {
    return request.delete(`/api/marketing/campaigns/${id}`)
  },
  updateCampaignStatus(id: number, status: number) {
    return request.patch<any, { data: Campaign }>(`/api/marketing/campaigns/${id}/status`, { status })
  },

  // ==================== 充值促销（campaign_type=1） ====================
  getRechargePromos(params: { page?: number; size?: number } = {}) {
    return request.get<any, { data: PageResult<Campaign> }>('/api/marketing/recharge-promo', { params })
  },
  createRechargePromo(data: Record<string, any>) {
    return request.post<any, { data: Campaign }>('/api/marketing/recharge-promo', data)
  },
  updateRechargePromo(id: number, data: Record<string, any>) {
    return request.put<any, { data: Campaign }>(`/api/marketing/recharge-promo/${id}`, data)
  },
  deleteRechargePromo(id: number) {
    return request.delete(`/api/marketing/recharge-promo/${id}`)
  },

  // ==================== 优惠券模板 ====================
  getCouponTemplates(params: { page?: number; size?: number; templateName?: string; couponType?: number; isActive?: number } = {}) {
    return request.get<any, { data: PageResult<CouponTemplate> }>('/api/marketing/coupons', { params })
  },
  getCouponTemplate(id: number) {
    return request.get<any, { data: CouponTemplate }>(`/api/marketing/coupons/${id}`)
  },
  createCouponTemplate(data: Record<string, any>) {
    return request.post<any, { data: CouponTemplate }>('/api/marketing/coupons', data)
  },
  updateCouponTemplate(id: number, data: Record<string, any>) {
    return request.put<any, { data: CouponTemplate }>(`/api/marketing/coupons/${id}`, data)
  },
  deleteCouponTemplate(id: number) {
    return request.delete(`/api/marketing/coupons/${id}`)
  },

  // ==================== 优惠券发放与记录 ====================
  issueCoupons(templateId: number, data: { memberId?: number; campaignId?: number; quantity?: number }) {
    return request.post<any, { data: number }>(`/api/marketing/coupons/${templateId}/issue`, data)
  },
  redeemCoupon(couponId: number, orderId: number) {
    return request.post<any, { data: Coupon }>(`/api/marketing/coupons/${couponId}/redeem`, null, { params: { orderId } })
  },
  getCouponRecords(params: { page?: number; size?: number; memberId?: number; status?: number; templateId?: number } = {}) {
    return request.get<any, { data: PageResult<Coupon> }>('/api/marketing/coupons/records', { params })
  },

  // ==================== 公安审计 ====================
  getPendingAuditSessions() {
    return request.get<any, { data: any[] }>('/api/marketing/integration/audit/pending-sessions')
  },
  generateAuditData() {
    return request.post<any, { data: { generatedCount: number } }>('/api/marketing/integration/audit/generate')
  },
  uploadAuditData() {
    return request.post<any, { data: { uploadedCount: number } }>('/api/marketing/integration/audit/upload')
  },
  addAuditLog(data: Record<string, any>) {
    return request.post<any, { data: any }>('/api/marketing/integration/audit/add', data)
  },

  // ==================== 公安审计 ====================
  getIntegrationConfigs() {
    return request.get<any, { data: any[] }>('/api/marketing/integration/configs')
  },
  saveIntegrationConfig(data: Record<string, any>) {
    return request.post<any, { data: any }>('/api/marketing/integration/configs', data)
  },
  getIntegrationLogs(params: { page?: number; size?: number; integrationType?: number; status?: number } = {}) {
    return request.get<any, { data: PageResult<any> }>('/api/marketing/integration/logs', { params })
  },
  getIDVerificationLogs(params: { page?: number; size?: number; verificationStatus?: number } = {}) {
    return request.get<any, { data: PageResult<any> }>('/api/marketing/integration/id-verify-logs', { params })
  },
  getSmsLogs(params: { page?: number; size?: number; status?: number; phone?: string } = {}) {
    return request.get<any, { data: PageResult<any> }>('/api/marketing/integration/sms-logs', { params })
  },
  getAuditLogs(params: { page?: number; size?: number; uploaded?: number } = {}) {
    return request.get<any, { data: PageResult<any> }>('/api/marketing/integration/audit-logs', { params })
  },
  testIntegration(id: number) {
    return request.post<any, { data: any }>(`/api/marketing/integration/configs/${id}/test`)
  },
}

export default api
