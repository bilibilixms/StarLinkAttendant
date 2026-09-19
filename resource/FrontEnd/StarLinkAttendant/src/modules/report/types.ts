export interface DashboardData {
  totalRevenue: number
  onlineRevenue: number
  productRevenue: number
  rechargeRevenue: number
  onlineCount: number
  todayOrderCount: number
  totalSessions: number
  peakConcurrent: number
  avgOccupancyRate: number
  cashAmount: number
  balanceAmount: number
  todayRevenue: number
  avgOrderAmount: number
  occupancyRate: number
}

export interface DailyRevenue {
  date: string
  totalRevenue: number
  onlineRevenue: number
  productRevenue: number
  rechargeRevenue: number
  totalOrders: number
}

export interface MonthlyRevenue {
  month: string
  totalRevenue: number
  onlineRevenue: number
  productRevenue: number
  rechargeRevenue: number
  totalOrders: number
}

export interface RevenueType {
  type: number
  amount: number
  orderCount: number
}

export interface HourlySession {
  hour: number
  sessionCount: number
  activeCount: number
}

export interface ProductRanking {
  productName: string
  totalQuantity: number
  totalAmount: number
  orderCount: number
}

export interface ProfitRanking {
  productName: string
  totalQuantity: number
  totalAmount: number
  totalProfit: number
  orderCount: number
}

export interface MemberTrend {
  date: string
  newCount: number
  activeCount: number
}

export interface RechargeTrend {
  date: string
  count: number
  amount: number
}

export interface MemberLTV {
  memberId: number
  memberNo: string
  memberName: string
  balance: number
  totalConsumption: number
  totalRecharge: number
  totalOnlineHours: number
}

export interface MemberTotals {
  totalMembers: number
  totalRecharge: number
  totalActiveMembers: number
  repurchaseMembers: number
  repurchaseRate: number
}

export interface FinanceData {
  totalIncome: number
  cashIncome: number
  balanceIncome: number
  refundAmount: number
  netIncome: number
  purchaseExpense: number
  netProfit: number
  inventoryValue: number
  cashBalance: number
  totalAssets: number
  memberBalance: number
  netAssets: number
  operatingInflow: number
  rechargeInflow: number
  totalInflow: number
  cashOutflow: number
  netCashFlow: number
}

export interface AvgDurationData {
  date: string
  avgDurationMinutes: number
}

export interface ComputerUtilization {
  totalComputers: number
  hourlyUsage: HourlySession[]
}

export const ORDER_TYPE_MAP: Record<number, string> = {
  1: '商品销售',
  2: '上机结算',
  3: '充值',
  4: '套餐',
}
