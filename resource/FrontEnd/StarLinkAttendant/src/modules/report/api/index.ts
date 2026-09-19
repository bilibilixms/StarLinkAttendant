import request from '@/common/api/request'
import axios from 'axios'
import { getToken } from '@/common/auth'
import type {
  DashboardData,
  DailyRevenue,
  MonthlyRevenue,
  RevenueType,
  HourlySession,
  ProductRanking,
  ProfitRanking,
  MemberTrend,
  RechargeTrend,
  MemberLTV,
  MemberTotals,
  FinanceData,
  AvgDurationData,
  ComputerUtilization,
} from '../types'

export const api = {
  // ==================== REP-01 营业总览 ====================
  getDashboard(date?: string) {
    return request.get<any, { data: DashboardData }>('/api/report/dashboard', {
      params: date ? { date } : {},
    })
  },

  // ==================== REP-02 营收分析 ====================
  getDailyRevenue(start: string, end: string) {
    return request.get<any, { data: DailyRevenue[] }>('/api/report/revenue/daily', {
      params: { start, end },
    })
  },
  getMonthlyRevenue(start: string, end: string) {
    return request.get<any, { data: MonthlyRevenue[] }>('/api/report/revenue/monthly', {
      params: { start, end },
    })
  },
  getRevenueByType(start: string, end: string) {
    return request.get<any, { data: RevenueType[] }>('/api/report/revenue/by-type', {
      params: { start, end },
    })
  },
  getYoYMonthlyRevenue(start: string, end: string) {
    return request.get<any, { data: MonthlyRevenue[] }>('/api/report/revenue/yoy', {
      params: { start, end },
    })
  },

  // ==================== REP-03 上机分析 ====================
  getHourlySessions(date: string) {
    return request.get<any, { data: HourlySession[] }>('/api/report/session/hourly', {
      params: { date },
    })
  },
  getAvgSessionDuration(date: string) {
    return request.get<any, { data: AvgDurationData }>('/api/report/session/avg-duration', {
      params: { date },
    })
  },
  getPeakHours(date: string) {
    return request.get<any, { data: HourlySession[] }>('/api/report/session/peak', {
      params: { date },
    })
  },
  getComputerUtilization(date: string) {
    return request.get<any, { data: ComputerUtilization }>('/api/report/session/computer-utilization', {
      params: { date },
    })
  },

  // ==================== REP-04 商品排行 ====================
  getProductRanking(start: string, end: string, limit: number = 10) {
    return request.get<any, { data: ProductRanking[] }>('/api/report/product-ranking', {
      params: { start, end, limit },
    })
  },
  getProfitRanking(start: string, end: string, limit: number = 10) {
    return request.get<any, { data: ProfitRanking[] }>('/api/report/product-ranking/profit', {
      params: { start, end, limit },
    })
  },

  // ==================== REP-05 会员分析 ====================
  getNewMembers(start: string, end: string) {
    return request.get<any, { data: MemberTrend[] }>('/api/report/member/new', {
      params: { start, end },
    })
  },
  getActiveMembers(start: string, end: string) {
    return request.get<any, { data: MemberTrend[] }>('/api/report/member/active', {
      params: { start, end },
    })
  },
  getMemberTotals(start: string, end: string) {
    return request.get<any, { data: MemberTotals }>('/api/report/member/totals', {
      params: { start, end },
    })
  },
  getRechargeTrend(start: string, end: string) {
    return request.get<any, { data: RechargeTrend[] }>('/api/report/member/recharge-trend', {
      params: { start, end },
    })
  },
  getMemberLTV(limit: number = 20) {
    return request.get<any, { data: MemberLTV[] }>('/api/report/member/ltv', {
      params: { limit },
    })
  },

  // ==================== REP-06 财务报表 ====================
  getFinance(start: string, end: string) {
    return request.get<any, { data: FinanceData }>('/api/report/finance', {
      params: { start, end },
    })
  },

  // ==================== REP-07 数据导出 ====================
  exportReport(reportType: number, start: string, end: string) {
    const baseURL = import.meta.env.VITE_API_BASE_URL || ''
    const token = getToken()
    return axios.get(baseURL + '/api/report/export', {
      params: { reportType, start, end },
      responseType: 'blob',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
  },
}

export default api
