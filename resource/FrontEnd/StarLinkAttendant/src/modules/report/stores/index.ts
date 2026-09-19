import { defineStore } from 'pinia'
import { ref } from 'vue'
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
import api from '../api'

export const useReportStore = defineStore('report', () => {
  const dashboard = ref<DashboardData | null>(null)
  const dailyRevenue = ref<DailyRevenue[]>([])
  const monthlyRevenue = ref<MonthlyRevenue[]>([])
  const revenueByType = ref<RevenueType[]>([])
  const yoyRevenue = ref<MonthlyRevenue[]>([])
  const hourlySessions = ref<HourlySession[]>([])
  const avgDuration = ref<AvgDurationData | null>(null)
  const peakHours = ref<HourlySession[]>([])
  const computerUtilization = ref<ComputerUtilization | null>(null)
  const productRanking = ref<ProductRanking[]>([])
  const profitRanking = ref<ProfitRanking[]>([])
  const newMembers = ref<MemberTrend[]>([])
  const activeMembers = ref<MemberTrend[]>([])
  const memberTotals = ref<MemberTotals | null>(null)
  const rechargeTrend = ref<RechargeTrend[]>([])
  const memberLTV = ref<MemberLTV[]>([])
  const finance = ref<FinanceData | null>(null)
  const loading = ref(false)

  // ============ REP-01 营业总览 ============
  const fetchDashboard = async (date?: string) => {
    loading.value = true
    try { const res = await api.getDashboard(date); dashboard.value = res.data } finally { loading.value = false }
  }

  // ============ REP-02 营收分析 ============
  const fetchDailyRevenue = async (start: string, end: string) => {
    loading.value = true
    try { const res = await api.getDailyRevenue(start, end); dailyRevenue.value = res.data } finally { loading.value = false }
  }
  const fetchMonthlyRevenue = async (start: string, end: string) => {
    loading.value = true
    try { const res = await api.getMonthlyRevenue(start, end); monthlyRevenue.value = res.data } finally { loading.value = false }
  }
  const fetchRevenueByType = async (start: string, end: string) => {
    loading.value = true
    try { const res = await api.getRevenueByType(start, end); revenueByType.value = res.data } finally { loading.value = false }
  }
  const fetchYoYRevenue = async (start: string, end: string) => {
    loading.value = true
    try { const res = await api.getYoYMonthlyRevenue(start, end); yoyRevenue.value = res.data } finally { loading.value = false }
  }

  // ============ REP-03 上机分析 ============
  const fetchHourlySessions = async (date: string) => {
    loading.value = true
    try { const res = await api.getHourlySessions(date); hourlySessions.value = res.data } finally { loading.value = false }
  }
  const fetchAvgSessionDuration = async (date: string) => {
    loading.value = true
    try { const res = await api.getAvgSessionDuration(date); avgDuration.value = res.data } finally { loading.value = false }
  }
  const fetchPeakHours = async (date: string) => {
    loading.value = true
    try { const res = await api.getPeakHours(date); peakHours.value = res.data } finally { loading.value = false }
  }
  const fetchComputerUtilization = async (date: string) => {
    loading.value = true
    try { const res = await api.getComputerUtilization(date); computerUtilization.value = res.data } finally { loading.value = false }
  }

  // ============ REP-04 商品排行 ============
  const fetchProductRanking = async (start: string, end: string, limit: number = 10) => {
    loading.value = true
    try { const res = await api.getProductRanking(start, end, limit); productRanking.value = res.data } finally { loading.value = false }
  }
  const fetchProfitRanking = async (start: string, end: string, limit: number = 10) => {
    loading.value = true
    try { const res = await api.getProfitRanking(start, end, limit); profitRanking.value = res.data } finally { loading.value = false }
  }

  // ============ REP-05 会员分析 ============
  const fetchNewMembers = async (start: string, end: string) => {
    loading.value = true
    try { const res = await api.getNewMembers(start, end); newMembers.value = res.data } finally { loading.value = false }
  }
  const fetchActiveMembers = async (start: string, end: string) => {
    loading.value = true
    try { const res = await api.getActiveMembers(start, end); activeMembers.value = res.data } finally { loading.value = false }
  }
  const fetchMemberTotals = async (start: string, end: string) => {
    loading.value = true
    try { const res = await api.getMemberTotals(start, end); memberTotals.value = res.data } finally { loading.value = false }
  }
  const fetchRechargeTrend = async (start: string, end: string) => {
    loading.value = true
    try { const res = await api.getRechargeTrend(start, end); rechargeTrend.value = res.data } finally { loading.value = false }
  }
  const fetchMemberLTV = async (limit: number = 20) => {
    loading.value = true
    try { const res = await api.getMemberLTV(limit); memberLTV.value = res.data } finally { loading.value = false }
  }

  // ============ REP-06 财务报表 ============
  const fetchFinance = async (start: string, end: string) => {
    loading.value = true
    try { const res = await api.getFinance(start, end); finance.value = res.data } finally { loading.value = false }
  }

  return {
    dashboard, dailyRevenue, monthlyRevenue, revenueByType, yoyRevenue,
    hourlySessions, avgDuration, peakHours, computerUtilization,
    productRanking, profitRanking,
    newMembers, activeMembers, memberTotals, rechargeTrend, memberLTV,
    finance, loading,
    fetchDashboard,
    fetchDailyRevenue, fetchMonthlyRevenue, fetchRevenueByType, fetchYoYRevenue,
    fetchHourlySessions, fetchAvgSessionDuration, fetchPeakHours, fetchComputerUtilization,
    fetchProductRanking, fetchProfitRanking,
    fetchNewMembers, fetchActiveMembers, fetchMemberTotals, fetchRechargeTrend, fetchMemberLTV,
    fetchFinance,
  }
})
