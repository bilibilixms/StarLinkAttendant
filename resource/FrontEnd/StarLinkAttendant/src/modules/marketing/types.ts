export interface CouponTemplate {
  id: number
  templateName: string
  campaignId: number | null
  couponType: number
  couponTypeName?: string
  faceValue: number
  minConsume: number
  discountRate: number | null
  validDays: number | null
  validStart: string | null
  validEnd: string | null
  applicableProducts: string | null
  totalQuantity: number
  memberLevelLimit: number | null
  isActive: number
  createdAt: string
  updatedAt: string
}

export interface Coupon {
  id: number
  memberId: number
  memberName?: string
  templateId: number
  templateName?: string
  campaignId: number | null
  campaignName?: string
  couponCode: string
  faceValue: number
  status: number
  statusName?: string
  usedAt: string | null
  usedOrderId: number | null
  expireAt: string
  createdAt: string
  orderNo?: string
}

export interface Campaign {
  id: number
  campaignNo: string
  campaignName: string
  campaignType: number
  campaignTypeName?: string
  startTime: string
  endTime: string
  rules: string
  budget: number | null
  usedBudget: number
  usageLimit: number | null
  usedCount: number
  memberLimit: number
  status: number
  statusName?: string
  createdAt: string
  updatedAt: string
}

export interface CouponIssueRecord {
  id: number
  templateId: number
  campaignId: number | null
  issueType: number
  issueCount: number
  issueTime: string
  operatorId: number | null
  operatorName: string | null
  createdAt: string
  templateName?: string
}

export interface CouponUsageStats {
  templateId: number
  templateName: string
  totalIssued: number
  totalUsed: number
  totalExpired: number
  usageRate: number
  totalAmount: number
}

export interface RechargePromo {
  id: number
  promoName: string
  promoType: number
  tiers: RechargeTier[]
  startTime: string
  endTime: string
  status: number
  createdAt: string
  updatedAt: string
}

export interface RechargeTier {
  id: number
  rechargeAmount: number
  bonusAmount: number
  bonusPoints: number
  sortOrder: number
}

export interface IntegrationConfig {
  id: number
  integrationType: number
  configName: string
  apiUrl: string
  apiKey: string
  secretKey: string
  timeout: number
  isEnabled: number
  lastSyncTime: string | null
  errorCount: number
  createdAt: string
  updatedAt: string
}

export interface IntegrationLog {
  id: number
  integrationType: number
  requestData: string
  responseData: string
  status: number
  errorMessage: string | null
  createdAt: string
}

export interface IDVerificationLog {
  id: number
  memberId: number
  realName: string
  idCard: string
  verificationStatus: number
  verificationTime: string
  errorMessage: string | null
  createdAt: string
}

export interface SmsLog {
  id: number
  phone: string
  smsType: number
  templateCode: string
  content: string
  status: number
  sendTime: string
  errorMessage: string | null
  createdAt: string
}

export interface AuditLog {
  id: number
  memberId: number
  computerId: number
  sessionId: number
  loginTime: string
  logoutTime: string | null
  status: number
  uploaded: number
  uploadTime: string | null
  createdAt: string
}

export const COUPON_TYPE_MAP: Record<number, string> = {
  1: '满减券',
  2: '折扣券',
  3: '现金券',
  4: '时段券'
}

export const COUPON_STATUS_MAP: Record<number, string> = {
  0: '未使用',
  1: '已使用',
  2: '已过期',
  3: '已作废'
}

export const CAMPAIGN_TYPE_MAP: Record<number, string> = {
  1: '充值赠送',
  2: '满减优惠',
  3: '限时折扣',
  4: '新人专享',
  5: '生日活动',
  6: '积分兑换'
}

export const CAMPAIGN_STATUS_MAP: Record<number, string> = {
  0: '草稿',
  1: '已发布',
  2: '已生效',
  3: '已结束',
  4: '已下架'
}

export const INTEGRATION_TYPE_MAP: Record<number, string> = {
  1: '实名认证',
  2: '短信通道',
  3: '公安审计'
}
