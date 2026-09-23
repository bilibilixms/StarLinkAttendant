import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { CouponTemplate, Coupon, Campaign, CouponUsageStats, IntegrationConfig, IntegrationLog, IDVerificationLog, SmsLog, AuditLog } from '../types'
import api from '../api'

export const useMarketingStore = defineStore('marketing', () => {
  const couponTemplates = ref<CouponTemplate[]>([])
  const couponTemplatesTotal = ref(0)
  const coupons = ref<Coupon[]>([])
  const couponsTotal = ref(0)
  const couponUsageStats = ref<CouponUsageStats[]>([])
  const campaigns = ref<Campaign[]>([])
  const campaignsTotal = ref(0)
  const rechargePromos = ref<Campaign[]>([])
  const rechargePromosTotal = ref(0)
  const integrationConfigs = ref<IntegrationConfig[]>([])
  const integrationLogs = ref<IntegrationLog[]>([])
  const integrationLogsTotal = ref(0)
  const idVerificationLogs = ref<IDVerificationLog[]>([])
  const idVerificationLogsTotal = ref(0)
  const smsLogs = ref<SmsLog[]>([])
  const smsLogsTotal = ref(0)
  const auditLogs = ref<AuditLog[]>([])
  const auditLogsTotal = ref(0)
  const loading = ref(false)

  const activeCouponTemplates = computed(() => couponTemplates.value.filter(t => t.isActive === 1))
  const activeCampaigns = computed(() => campaigns.value.filter(c => c.status === 2))

  // ============ 优惠券模板 ============
  const fetchCouponTemplates = async (params: Record<string, any> = {}) => {
    loading.value = true
    try {
      const res = await api.getCouponTemplates(params)
      couponTemplates.value = res.data.records
      couponTemplatesTotal.value = res.data.total
    } finally { loading.value = false }
  }

  const createCouponTemplate = async (data: Partial<CouponTemplate>) => {
    loading.value = true
    try {
      const res = await api.createCouponTemplate(data)
      await fetchCouponTemplates()
      return res.data
    } finally { loading.value = false }
  }

  const updateCouponTemplate = async (id: number, data: Partial<CouponTemplate>) => {
    loading.value = true
    try {
      const res = await api.updateCouponTemplate(id, data)
      await fetchCouponTemplates()
      return res.data
    } finally { loading.value = false }
  }

  const deleteCouponTemplate = async (id: number) => {
    loading.value = true
    try {
      await api.deleteCouponTemplate(id)
      await fetchCouponTemplates()
      return true
    } finally { loading.value = false }
  }

  // ============ 优惠券记录（核销记录） ============
  const fetchCoupons = async (params: Record<string, any> = {}) => {
    loading.value = true
    try {
      const res = await api.getCouponRecords(params)
      coupons.value = res.data.records
      couponsTotal.value = res.data.total
    } finally { loading.value = false }
  }

  const issueCoupons = async (templateId: number, data: { memberId?: number; campaignId?: number; quantity?: number }) => {
    loading.value = true
    try {
      const res = await api.issueCoupons(templateId, data)
      return res.data
    } finally { loading.value = false }
  }

  const redeemCoupon = async (couponId: number, orderId: number) => {
    loading.value = true
    try {
      const res = await api.redeemCoupon(couponId, orderId)
      return res.data
    } finally { loading.value = false }
  }

  // ============ 营销活动（统一 campaign 表） ============
  const fetchCampaigns = async (params: Record<string, any> = {}) => {
    loading.value = true
    try {
      const res = await api.getCampaigns(params)
      campaigns.value = res.data.records
      campaignsTotal.value = res.data.total
    } finally { loading.value = false }
  }

  const createCampaign = async (data: Partial<Campaign>) => {
    loading.value = true
    try {
      const res = await api.createCampaign(data)
      await fetchCampaigns()
      return res.data
    } finally { loading.value = false }
  }

  const updateCampaign = async (id: number, data: Partial<Campaign>) => {
    loading.value = true
    try {
      const res = await api.updateCampaign(id, data)
      await fetchCampaigns()
      return res.data
    } finally { loading.value = false }
  }

  const deleteCampaign = async (id: number) => {
    loading.value = true
    try {
      await api.deleteCampaign(id)
      await fetchCampaigns()
      return true
    } finally { loading.value = false }
  }

  const updateCampaignStatus = async (id: number, status: number) => {
    loading.value = true
    try {
      const res = await api.updateCampaignStatus(id, status)
      await fetchCampaigns()
      return res.data
    } finally { loading.value = false }
  }

  // ============ 充值活动（campaign_type=1） ============
  const fetchRechargePromos = async (params: Record<string, any> = {}) => {
    loading.value = true
    try {
      const res = await api.getRechargePromos(params)
      rechargePromos.value = res.data.records
      rechargePromosTotal.value = res.data.total
    } finally { loading.value = false }
  }

  const saveRechargePromo = async (data: Record<string, any>, id?: number) => {
    loading.value = true
    try {
      const res = id
        ? await api.updateRechargePromo(id, data)
        : await api.createRechargePromo(data)
      await fetchRechargePromos()
      return res.data
    } finally { loading.value = false }
  }

  const deleteRechargePromo = async (id: number) => {
    loading.value = true
    try {
      await api.deleteRechargePromo(id)
      await fetchRechargePromos()
      return true
    } finally { loading.value = false }
  }

  // ============ 公安审计 ============
  const fetchIntegrationConfigs = async () => {
    loading.value = true
    try {
      const res = await api.getIntegrationConfigs()
      integrationConfigs.value = res.data
    } finally { loading.value = false }
  }

  const saveIntegrationConfig = async (data: Partial<IntegrationConfig>) => {
    loading.value = true
    try {
      const res = await api.saveIntegrationConfig(data)
      await fetchIntegrationConfigs()
      return res.data
    } finally { loading.value = false }
  }

  const fetchIntegrationLogs = async (params: Record<string, any> = {}) => {
    loading.value = true
    try {
      const res = await api.getIntegrationLogs(params)
      integrationLogs.value = res.data.records
      integrationLogsTotal.value = res.data.total
    } finally { loading.value = false }
  }

  const fetchIDVerificationLogs = async (params: Record<string, any> = {}) => {
    loading.value = true
    try {
      const res = await api.getIDVerificationLogs(params)
      idVerificationLogs.value = res.data.records
      idVerificationLogsTotal.value = res.data.total
    } finally { loading.value = false }
  }

  const fetchSmsLogs = async (params: Record<string, any> = {}) => {
    loading.value = true
    try {
      const res = await api.getSmsLogs(params)
      smsLogs.value = res.data.records
      smsLogsTotal.value = res.data.total
    } finally { loading.value = false }
  }

  const fetchAuditLogs = async (params: Record<string, any> = {}) => {
    loading.value = true
    try {
      const res = await api.getAuditLogs(params)
      auditLogs.value = res.data.records
      auditLogsTotal.value = res.data.total
    } finally { loading.value = false }
  }

  const pendingSessions = ref<any[]>([])

  const fetchPendingSessions = async () => {
    loading.value = true
    try {
      const res = await api.getPendingAuditSessions()
      pendingSessions.value = res.data
    } finally { loading.value = false }
  }

  const generateAuditData = async () => {
    loading.value = true
    try {
      const res = await api.generateAuditData()
      await fetchAuditLogs()
      await fetchPendingSessions()
      return res.data.generatedCount
    } finally { loading.value = false }
  }

  const uploadAuditData = async () => {
    loading.value = true
    try {
      const res = await api.uploadAuditData()
      await fetchAuditLogs()
      return res.data.uploadedCount
    } finally { loading.value = false }
  }

  const addAuditLog = async (data: Record<string, any>) => {
    loading.value = true
    try {
      const res = await api.addAuditLog(data)
      await fetchAuditLogs()
      return res.data
    } finally { loading.value = false }
  }

  const testIntegration = async (id: number) => {
    loading.value = true
    try {
      const res = await api.testIntegration(id)
      return res.data
    } finally { loading.value = false }
  }

  return {
    couponTemplates, couponTemplatesTotal, coupons, couponsTotal,
    couponUsageStats,
    campaigns, campaignsTotal, rechargePromos, rechargePromosTotal,
    integrationConfigs, integrationLogs, integrationLogsTotal,
    idVerificationLogs, idVerificationLogsTotal,
    smsLogs, smsLogsTotal, auditLogs, auditLogsTotal,
    loading, activeCouponTemplates, activeCampaigns,
    fetchCouponTemplates, createCouponTemplate, updateCouponTemplate, deleteCouponTemplate,
    fetchCoupons, issueCoupons, redeemCoupon,
    fetchCampaigns, createCampaign, updateCampaign, deleteCampaign, updateCampaignStatus,
    fetchRechargePromos, saveRechargePromo, deleteRechargePromo,
    fetchIntegrationConfigs, saveIntegrationConfig, fetchIntegrationLogs,
    fetchIDVerificationLogs, fetchSmsLogs, fetchAuditLogs,
    pendingSessions, fetchPendingSessions,
    generateAuditData, uploadAuditData, addAuditLog,
    testIntegration,
  }
})
