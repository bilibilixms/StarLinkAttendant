<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useReportStore } from '../stores'

const fmtDateTime = (d: Date) => {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const store = useReportStore()
const dateRange = ref<[string, string]>([
  fmtDateTime(new Date(Date.now() - 30 * 86400000)),
  fmtDateTime(new Date(new Date().setHours(23, 59, 59, 999))),
])

const fetchAll = () => {
  const [start, end] = dateRange.value
  store.fetchNewMembers(start, end)
  store.fetchActiveMembers(start, end)
  store.fetchMemberTotals(start, end)
  store.fetchRechargeTrend(start, end)
  store.fetchMemberLTV(20)
}

const mergedTrends = computed(() => {
  const map = new Map<string, { date: string; newCount: number; activeCount: number }>()
  for (const n of store.newMembers) {
    map.set(n.date, { date: n.date, newCount: n.newCount, activeCount: 0 })
  }
  for (const a of store.activeMembers) {
    if (map.has(a.date)) {
      map.get(a.date)!.activeCount = a.activeCount
    } else {
      map.set(a.date, { date: a.date, newCount: 0, activeCount: a.activeCount })
    }
  }
  return Array.from(map.values()).sort((a, b) => a.date.localeCompare(b.date))
})

const maxCount = computed(() => {
  if (!mergedTrends.value.length) return 1
  return Math.max(...mergedTrends.value.map(d => Math.max(d.newCount, d.activeCount)))
})

const maxRechargeAmount = computed(() => {
  if (!store.rechargeTrend.length) return 1
  return Math.max(...store.rechargeTrend.map(r => r.amount))
})

const formatMoney = (val: number) => {
  return '¥' + val.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatPercent = (val: number | undefined | null) => {
  if (val == null) return '--'
  return (Number(val) * 100).toFixed(1) + '%'
}

onMounted(fetchAll)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">会员分析</h1>
      <div class="header-actions">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
          @change="fetchAll"
        />
      </div>
    </div>

    <!-- KPI汇总 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon total">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="9" cy="7" r="4"></circle>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ store.memberTotals?.totalMembers ?? '--' }}</span>
          <span class="stat-label">会员总数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon recharge">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="1" y="5" width="22" height="14" rx="2"></rect>
            <line x1="1" y1="10" x2="23" y2="10"></line>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ store.memberTotals ? formatMoney(store.memberTotals.totalRecharge) : '--' }}</span>
          <span class="stat-label">充值总额</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon active">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="9" cy="7" r="4"></circle>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ store.memberTotals?.totalActiveMembers ?? '--' }}</span>
          <span class="stat-label">活跃会员</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon repurchase">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="1 4 1 10 7 10"></polyline>
            <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"></path>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ formatPercent(store.memberTotals?.repurchaseRate) }}</span>
          <span class="stat-label">复购率（>=2次消费）</span>
        </div>
      </div>
    </div>

    <!-- 会员趋势 -->
    <div class="page-card">
      <h3 class="card-title">会员趋势</h3>
      <div class="trend-chart" v-if="mergedTrends.length">
        <div class="trend-bars">
          <div v-for="item in mergedTrends" :key="item.date" class="trend-column">
            <div class="trend-bar-group">
              <div class="trend-bar new-bar" :style="{ height: maxCount > 0 ? ((item.newCount / maxCount) * 100) + '%' : '0%' }"></div>
              <div class="trend-bar active-bar" :style="{ height: maxCount > 0 ? ((item.activeCount / maxCount) * 100) + '%' : '0%' }"></div>
            </div>
            <span class="trend-label">{{ item.date ? item.date.slice(5) : '' }}</span>
          </div>
        </div>
      </div>
      <div class="chart-legend">
        <span class="legend-item"><span class="dot new"></span> 新增会员</span>
        <span class="legend-item"><span class="dot active"></span> 活跃会员</span>
      </div>
    </div>

    <!-- 充值趋势 + LTV 并排 -->
    <div class="detail-grid">
      <div class="page-card">
        <h3 class="card-title">充值趋势</h3>
        <div class="trend-chart" v-if="store.rechargeTrend.length">
          <div class="trend-bars" style="height: 180px">
            <div v-for="item in store.rechargeTrend" :key="item.date" class="trend-column">
              <div class="trend-bar recharge-bar" :style="{ height: maxRechargeAmount > 0 ? ((item.amount / maxRechargeAmount) * 100) + '%' : '0%' }"></div>
              <span class="trend-label">{{ item.date ? item.date.slice(5) : '' }}</span>
            </div>
          </div>
        </div>
        <div v-else class="empty-hint">暂无数据</div>
      </div>

      <div class="page-card">
        <h3 class="card-title">会员 LTV Top 20</h3>
        <el-table :data="store.memberLTV" stripe size="small" max-height="250">
          <el-table-column type="index" label="#" width="40" />
          <el-table-column prop="memberName" label="会员" min-width="80">
            <template #default="{ row }">{{ row.memberName || row.memberNo }}</template>
          </el-table-column>
          <el-table-column label="累计消费" width="120">
            <template #default="{ row }">{{ formatMoney(row.totalConsumption) }}</template>
          </el-table-column>
          <el-table-column label="累计充值" width="120">
            <template #default="{ row }">{{ formatMoney(row.totalRecharge) }}</template>
          </el-table-column>
        </el-table>
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

.stat-card { background: white; border-radius: 12px; padding: 20px; display: flex; align-items: center; gap: 16px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05); }
.stat-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.stat-icon.total { background: rgba(59, 130, 246, 0.1); color: #3b82f6; }
.stat-icon.recharge { background: rgba(34, 197, 94, 0.1); color: #22c55e; }
.stat-icon.active { background: rgba(245, 158, 11, 0.1); color: #f59e0b; }
.stat-icon.repurchase { background: rgba(236, 72, 153, 0.1); color: #ec4899; }
.stat-content { display: flex; flex-direction: column; }
.stat-value { font-size: 24px; font-weight: 700; color: #1e293b; }
.stat-label { font-size: 13px; color: #64748b; margin-top: 2px; }

.page-card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05); margin-bottom: 24px; }
.card-title { font-size: 16px; font-weight: 600; color: #1e293b; margin: 0 0 20px; }

.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }

.trend-chart { padding: 10px 0; }
.trend-bars { display: flex; align-items: flex-end; gap: 6px; height: 220px; }
.trend-column { flex: 1; display: flex; flex-direction: column; align-items: center; min-width: 0; }
.trend-bar-group { display: flex; gap: 2px; align-items: flex-end; height: 100%; width: 100%; justify-content: center; }
.trend-bar { width: 10px; min-width: 6px; border-radius: 3px 3px 0 0; min-height: 2px; transition: height 0.3s; }
.new-bar { background: #3b82f6; }
.active-bar { background: #22c55e; }
.recharge-bar { width: 14px; background: #a855f7; border-radius: 3px 3px 0 0; min-height: 2px; }
.trend-label { font-size: 9px; color: #94a3b8; margin-top: 4px; }

.chart-legend { display: flex; gap: 24px; justify-content: center; margin-top: 12px; }
.legend-item { font-size: 13px; color: #64748b; display: flex; align-items: center; gap: 6px; }
.dot { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
.dot.new { background: #3b82f6; }
.dot.active { background: #22c55e; }
.empty-hint { text-align: center; color: #94a3b8; padding: 30px; font-size: 14px; }

@media (max-width: 1200px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 768px) { .stats-grid { grid-template-columns: 1fr; } .detail-grid { grid-template-columns: 1fr; } }
</style>
