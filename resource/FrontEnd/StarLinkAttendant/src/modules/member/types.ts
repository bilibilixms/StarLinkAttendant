/** 会员 */
export interface MemberItem {
  id: number
  memberNo: string
  realName: string
  gender: number
  genderLabel: string
  phone: string
  birthday: string
  levelId: number
  levelName: string
  totalPoints: number
  availablePoints: number
  growthValue: number
  totalRecharge: number
  balance: number
  totalConsumption: number
  lastLoginTime: string
  lastOnlineTime: string
  totalOnlineHours: number
  registerSource: number
  registerSourceLabel: string
  status: number
  statusLabel: string
  blacklistReason: string
  tag: string
  createdAt: string
  updatedAt: string
}

export interface MemberQuery {
  memberNo?: string
  realName?: string
  phone?: string
  status?: number | null
  levelId?: number | null
}

export interface MemberRegisterRequest {
  phone: string
  password: string
  realName?: string
  gender?: number
  idCard?: string
  birthday?: string
}

export interface MemberUpdateRequest {
  realName?: string
  gender?: number
  phone?: string
  idCard?: string
  birthday?: string
  levelId?: number
  tag?: string
}

/** 会员等级 */
export interface LevelItem {
  id: number
  levelName: string
  levelOrder: number
  minGrowth: number
  maxGrowth: number
  discountRate: number
  hourlyDiscount: number
  rechargeBonusRate: number
  pointsMultiple: number
  autoUpgrade: number
  iconUrl: string
  createdAt: string
  updatedAt: string
}

export interface LevelCreateRequest {
  levelName: string
  levelOrder: number
  minGrowth: number
  maxGrowth: number
  discountRate?: number
  hourlyDiscount?: number
  rechargeBonusRate?: number
  pointsMultiple?: number
  autoUpgrade?: number
  iconUrl?: string
}

export interface LevelUpdateRequest {
  levelName?: string
  levelOrder?: number
  minGrowth?: number
  maxGrowth?: number
  discountRate?: number
  hourlyDiscount?: number
  rechargeBonusRate?: number
  pointsMultiple?: number
  autoUpgrade?: number
  iconUrl?: string
}

/** 积分记录 */
export interface PointsRecordItem {
  id: number
  memberId: number
  points: number
  balanceBefore: number
  balanceAfter: number
  bizType: number
  bizTypeLabel: string
  bizId: number
  remark: string
  expireAt: string
  createdAt: string
}

/** 充值记录 */
export interface RechargeRecordItem {
  id: number
  rechargeNo: string
  memberId: number
  memberName: string
  rechargeAmount: number
  bonusAmount: number
  totalAmount: number
  balanceBefore: number
  balanceAfter: number
  paymentMethod: number
  paymentMethodLabel: string
  status: number
  statusLabel: string
  paidAt: string
  createdAt: string
}

export interface RechargeRequest {
  memberId: number
  amount: number
  paymentMethod: number
  operatorId?: number
  campaignId?: number
}

/** 黑名单 */
export interface BlacklistItem {
  id: number
  memberNo: string
  realName: string
  phone: string
  blacklistReason: string
  createdAt: string
}
