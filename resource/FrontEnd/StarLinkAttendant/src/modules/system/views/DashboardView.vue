<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/common/api/request'

interface DashboardData {
  onlineCount: number
  todayRevenue: number
  memberCount: number
  todaySalesCount: number
  totalSeats: number
  availableSeats: number
  todayNewMembers: number
  todaySessionCount: number
}

const loading = ref(true)
const stats = ref<DashboardData>({
  onlineCount: 0,
  todayRevenue: 0,
  memberCount: 0,
  todaySalesCount: 0,
  totalSeats: 0,
  availableSeats: 0,
  todayNewMembers: 0,
  todaySessionCount: 0,
})

/** 格式化金额 */
function formatMoney(val: number | null | undefined): string {
  if (val == null) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 机位使用率 */
function seatUsageRate(): string {
  if (!stats.value.totalSeats) return '0'
  const used = stats.value.totalSeats - stats.value.availableSeats
  return ((used / stats.value.totalSeats) * 100).toFixed(1)
}

async function fetchDashboard() {
  loading.value = true
  try {
    const res = await request.get<any, { code: number; data: DashboardData }>('/api/dashboard')
    stats.value = res.data
  } catch (e) {
    // 错误已由 axios 拦截器处理
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h1 class="page-title">首页</h1>
      <p class="page-desc">运营数据总览</p>
    </div>

    <!-- 第一行：核心指标 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon online">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ stats.onlineCount }}</span>
          <span class="stat-label">在线人数</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon revenue">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="12" y1="1" x2="12" y2="23"></line>
            <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">¥{{ formatMoney(stats.todayRevenue) }}</span>
          <span class="stat-label">今日营收</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon member">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="9" cy="7" r="4"></circle>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ stats.memberCount.toLocaleString() }}</span>
          <span class="stat-label">会员总数</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon sales">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="9" cy="21" r="1"></circle>
            <circle cx="20" cy="21" r="1"></circle>
            <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ stats.todaySalesCount }}</span>
          <span class="stat-label">今日商品销量</span>
        </div>
      </div>
    </div>

    <!-- 第二行：运营指标 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon seat">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
            <line x1="8" y1="21" x2="16" y2="21"></line>
            <line x1="12" y1="17" x2="12" y2="21"></line>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ stats.availableSeats }}<span class="stat-unit">/{{ stats.totalSeats }}</span></span>
          <span class="stat-label">空闲机位 / 总机位</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon usage">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ seatUsageRate() }}<span class="stat-unit">%</span></span>
          <span class="stat-label">机位使用率</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon session">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <polyline points="12 6 12 12 16 14"></polyline>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ stats.todaySessionCount }}</span>
          <span class="stat-label">今日上机人次</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon newmember">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="8.5" cy="7" r="4"></circle>
            <line x1="20" y1="8" x2="20" y2="14"></line>
            <line x1="23" y1="11" x2="17" y2="11"></line>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ stats.todayNewMembers }}</span>
          <span class="stat-label">今日新会员</span>
        </div>
      </div>
    </div>

    <!-- 欢迎卡 -->
    <div class="page-card">
      <div class="card-body">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="empty-icon">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
          <circle cx="12" cy="7" r="4"></circle>
        </svg>
        <p class="empty-text">欢迎使用星络灵侍馆</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  width: 100%;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.page-desc {
  font-size: 14px;
  color: #64748b;
  margin-top: 4px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon.online {
  background-color: rgba(34, 197, 94, 0.1);
  color: #22c55e;
}

.stat-icon.revenue {
  background-color: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}

.stat-icon.member {
  background-color: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.stat-icon.sales {
  background-color: rgba(168, 85, 247, 0.1);
  color: #a855f7;
}

.stat-icon.seat {
  background-color: rgba(20, 184, 166, 0.1);
  color: #14b8a6;
}

.stat-icon.usage {
  background-color: rgba(249, 115, 22, 0.1);
  color: #f97316;
}

.stat-icon.session {
  background-color: rgba(99, 102, 241, 0.1);
  color: #6366f1;
}

.stat-icon.newmember {
  background-color: rgba(236, 72, 153, 0.1);
  color: #ec4899;
}

.stat-content {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
}

.stat-unit {
  font-size: 14px;
  font-weight: 500;
  color: #94a3b8;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-top: 2px;
}

.page-card {
  background: white;
  border-radius: 12px;
  padding: 48px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.card-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.empty-icon {
  color: #cbd5e1;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 15px;
  color: #94a3b8;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
