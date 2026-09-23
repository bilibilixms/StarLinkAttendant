<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useReportStore } from '../stores'

const fmtDate = (d: Date) => d.toISOString().slice(0, 10)

const store = useReportStore()
const queryDate = ref(fmtDate(new Date()))

const fetchData = () => {
  store.fetchDashboard(queryDate.value)
}

const formatMoney = (val: number | undefined | null) => {
  if (val == null) return '--'
  return '¥' + Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">营业总览</h1>
      <div class="header-actions">
        <el-date-picker
          v-model="queryDate"
          type="date"
          placeholder="选择日期"
          value-format="YYYY-MM-DD"
          style="width: 180px"
          @change="fetchData"
        />
      </div>
    </div>

    <!-- KPI卡片 第一行 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon revenue">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="12" y1="1" x2="12" y2="23"></line>
            <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ formatMoney(store.dashboard?.totalRevenue) }}</span>
          <span class="stat-label">今日总营收</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon online">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
            <line x1="8" y1="21" x2="16" y2="21"></line>
            <line x1="12" y1="17" x2="12" y2="21"></line>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ store.dashboard?.totalSessions ?? '--' }}</span>
          <span class="stat-label">当日上机数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon orders">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
            <polyline points="14 2 14 8 20 8"></polyline>
            <line x1="16" y1="13" x2="8" y2="13"></line>
            <line x1="16" y1="17" x2="8" y2="17"></line>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ store.dashboard?.todayOrderCount ?? '--' }}</span>
          <span class="stat-label">今日订单数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon peak">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="23 6 13.5 15.5 8.5 10.5 1 18"></polyline>
            <polyline points="17 6 23 6 23 12"></polyline>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ store.dashboard?.peakConcurrent ?? '--' }}</span>
          <span class="stat-label">峰值并发</span>
        </div>
      </div>
    </div>

    <!-- KPI卡片 第二行 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon avg">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <polyline points="12 6 12 12 16 14"></polyline>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ formatMoney(store.dashboard?.avgOrderAmount) }}</span>
          <span class="stat-label">客单价（均单金额）</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon rate">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="2" y="7" width="20" height="10" rx="2" ry="2"></rect>
            <line x1="12" y1="7" x2="12" y2="17"></line>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ store.dashboard?.occupancyRate != null ? Number(store.dashboard.occupancyRate).toFixed(1) + '%' : '--' }}</span>
          <span class="stat-label">实时上机率</span>
        </div>
      </div>
    </div>

    <!-- 营收构成 -->
    <div class="page-card">
      <h3 class="card-title">营收构成</h3>
      <div class="revenue-breakdown">
        <div class="breakdown-col">
          <div class="breakdown-circle online">
            <span class="circle-value">{{ formatMoney(store.dashboard?.onlineRevenue) }}</span>
            <span class="circle-label">上机营收</span>
          </div>
        </div>
        <div class="breakdown-col">
          <div class="breakdown-circle product">
            <span class="circle-value">{{ formatMoney(store.dashboard?.productRevenue) }}</span>
            <span class="circle-label">商品营收</span>
          </div>
        </div>
        <div class="breakdown-col">
          <div class="breakdown-circle recharge">
            <span class="circle-value">{{ formatMoney(store.dashboard?.rechargeRevenue) }}</span>
            <span class="circle-label">充值营收</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.header-actions { display: flex; gap: 12px; }

.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }

.stat-card { background: white; border-radius: 12px; padding: 20px; display: flex; align-items: center; gap: 16px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05); transition: transform 0.2s, box-shadow 0.2s; }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08); }
.stat-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.stat-icon.revenue { background: rgba(245, 158, 11, 0.1); color: #f59e0b; }
.stat-icon.online { background: rgba(34, 197, 94, 0.1); color: #22c55e; }
.stat-icon.orders { background: rgba(59, 130, 246, 0.1); color: #3b82f6; }
.stat-icon.peak { background: rgba(168, 85, 247, 0.1); color: #a855f7; }
.stat-icon.avg { background: rgba(14, 165, 233, 0.1); color: #0ea5e9; }
.stat-icon.rate { background: rgba(236, 72, 153, 0.1); color: #ec4899; }
.stat-content { display: flex; flex-direction: column; }
.stat-value { font-size: 24px; font-weight: 700; color: #1e293b; }
.stat-label { font-size: 13px; color: #64748b; margin-top: 2px; }

.page-card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05); }
.card-title { font-size: 16px; font-weight: 600; color: #1e293b; margin: 0 0 20px; }

.revenue-breakdown { display: grid; grid-template-columns: repeat(3, 1fr); gap: 24px; }
.breakdown-col { display: flex; justify-content: center; }
.breakdown-circle { width: 120px; height: 120px; border-radius: 50%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; }
.breakdown-circle.online { background: rgba(34, 197, 94, 0.08); }
.breakdown-circle.product { background: rgba(59, 130, 246, 0.08); }
.breakdown-circle.recharge { background: rgba(168, 85, 247, 0.08); }
.circle-value { font-size: 16px; font-weight: 700; color: #1e293b; }
.breakdown-circle.online .circle-value { color: #16a34a; }
.breakdown-circle.product .circle-value { color: #2563eb; }
.breakdown-circle.recharge .circle-value { color: #9333ea; }
.circle-label { font-size: 12px; color: #64748b; }

@media (max-width: 1200px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 768px) { .stats-grid { grid-template-columns: 1fr; } .revenue-breakdown { grid-template-columns: 1fr; } }
</style>
