<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useReportStore } from '../stores'

const fmtDate = (d: Date) => d.toISOString().slice(0, 10)

const store = useReportStore()
const activeTab = ref<'daily' | 'monthly'>('daily')
const dateRange = ref<[string, string]>([
  fmtDate(new Date(Date.now() - 7 * 86400000)),
  fmtDate(new Date()),
])

const fetchData = () => {
  const [start, end] = dateRange.value
  if (activeTab.value === 'daily') {
    store.fetchDailyRevenue(start, end)
  } else {
    store.fetchMonthlyRevenue(start, end)
  }
  store.fetchRevenueByType(start, end)
}

const chartData = computed(() => {
  if (activeTab.value === 'daily') {
    return store.dailyRevenue
  }
  return store.monthlyRevenue
})

const maxRevenue = computed(() => {
  if (!chartData.value.length) return 1
  return Math.max(...chartData.value.map(d =>
    activeTab.value === 'daily' ? (d as any).totalRevenue : (d as any).totalRevenue
  ))
})

const totalTypeRevenue = computed(() => {
  return store.revenueByType.reduce((sum, r) => sum + r.amount, 0)
})

const formatMoney = (val: number) => {
  return '¥' + val.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
}

const formatLabel = (item: any) => {
  if (activeTab.value === 'daily') {
    return item.date ? item.date.slice(5) : ''
  }
  return item.month
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">营收分析</h1>
      <div class="header-actions">
        <el-radio-group v-model="activeTab" size="small" @change="fetchData">
          <el-radio-button value="daily">按日</el-radio-button>
          <el-radio-button value="monthly">按月</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD"
          style="width: 260px"
          @change="fetchData"
        />
      </div>
    </div>

    <!-- 趋势图（CSS Bar Chart） -->
    <div class="page-card chart-card">
      <h3 class="card-title">营收趋势</h3>
      <div class="chart-container" v-if="chartData.length">
        <div class="chart-bars">
          <div
            v-for="item in chartData"
            :key="item.date || item.month"
            class="bar-column"
          >
            <div class="bar-value-tooltip">{{ formatMoney(item.totalRevenue) }}</div>
            <div
              class="bar"
              :style="{ height: maxRevenue > 0 ? ((item.totalRevenue / maxRevenue) * 100) + '%' : '0%' }"
              :title="formatMoney(item.totalRevenue)"
            >
              <div class="bar-segment online" :style="{ height: item.totalRevenue > 0 ? ((item.onlineRevenue / item.totalRevenue) * 100) + '%' : '0%' }"></div>
              <div class="bar-segment product" :style="{ height: item.totalRevenue > 0 ? ((item.productRevenue / item.totalRevenue) * 100) + '%' : '0%' }"></div>
              <div class="bar-segment recharge" :style="{ height: item.totalRevenue > 0 ? ((item.rechargeRevenue / item.totalRevenue) * 100) + '%' : '0%' }"></div>
            </div>
            <span class="bar-label">{{ formatLabel(item) }}</span>
          </div>
        </div>
      </div>
      <div class="chart-legend">
        <span class="legend-item"><span class="dot online"></span> 上机营收</span>
        <span class="legend-item"><span class="dot product"></span> 商品营收</span>
        <span class="legend-item"><span class="dot recharge"></span> 充值营收</span>
      </div>
    </div>

    <!-- 数据表 -->
    <div class="page-card">
      <h3 class="card-title">{{ activeTab === 'daily' ? '每日' : '每月' }}明细</h3>
      <el-table :data="chartData" stripe style="width: 100%">
        <el-table-column :prop="activeTab === 'daily' ? 'date' : 'month'" :label="activeTab === 'daily' ? '日期' : '月份'" width="140" />
        <el-table-column prop="totalRevenue" label="总营收" width="140">
          <template #default="{ row }">{{ formatMoney(row.totalRevenue) }}</template>
        </el-table-column>
        <el-table-column prop="onlineRevenue" label="上机营收" width="140">
          <template #default="{ row }">{{ formatMoney(row.onlineRevenue) }}</template>
        </el-table-column>
        <el-table-column prop="productRevenue" label="商品营收" width="140">
          <template #default="{ row }">{{ formatMoney(row.productRevenue) }}</template>
        </el-table-column>
        <el-table-column prop="rechargeRevenue" label="充值营收" width="140">
          <template #default="{ row }">{{ formatMoney(row.rechargeRevenue) }}</template>
        </el-table-column>
        <el-table-column prop="totalOrders" label="订单数" width="100" />
      </el-table>
    </div>

    <!-- 按订单类型营收 -->
    <div class="page-card">
      <h3 class="card-title">订单类型营收分布</h3>
      <el-table :data="store.revenueByType" stripe style="width: 100%">
        <el-table-column label="订单类型" width="140">
          <template #default="{ row }">{{ ({ 1: '商品销售', 2: '上机结算', 3: '充值', 4: '套餐' } as Record<number, string>)[row.type] || '未知' }}</template>
        </el-table-column>
        <el-table-column label="营收金额" width="180">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="orderCount" label="订单数" width="120" />
        <el-table-column label="占比" min-width="160">
          <template #default="{ row }">
            <div class="pct-bar">
              <div class="pct-fill" :style="{ width: totalTypeRevenue > 0 ? ((row.amount / totalTypeRevenue) * 100) + '%' : '0%' }"></div>
              <span class="pct-label">{{ totalTypeRevenue > 0 ? ((row.amount / totalTypeRevenue) * 100).toFixed(1) + '%' : '--' }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.header-actions { display: flex; gap: 12px; align-items: center; }

.page-card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05); margin-bottom: 24px; }
.card-title { font-size: 16px; font-weight: 600; color: #1e293b; margin: 0 0 20px; }

.chart-container { padding: 20px 0; }
.chart-bars { display: flex; align-items: flex-end; gap: 8px; height: 280px; padding: 0 4px; }
.bar-column { flex: 1; display: flex; flex-direction: column; align-items: center; min-width: 0; }
.bar-value-tooltip { font-size: 11px; color: #64748b; margin-bottom: 4px; white-space: nowrap; }
.bar { width: 100%; max-width: 60px; background: #f1f5f9; border-radius: 4px 4px 0 0; position: relative; overflow: hidden; min-height: 2px; transition: height 0.3s; display: flex; flex-direction: column; justify-content: flex-end; }
.bar-segment { width: 100%; transition: height 0.3s; }
.bar-segment.online { background: #22c55e; }
.bar-segment.product { background: #3b82f6; }
.bar-segment.recharge { background: #a855f7; }
.bar-label { font-size: 11px; color: #94a3b8; margin-top: 8px; white-space: nowrap; }

.chart-legend { display: flex; gap: 24px; justify-content: center; margin-top: 12px; }
.legend-item { font-size: 13px; color: #64748b; display: flex; align-items: center; gap: 6px; }
.dot { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
.dot.online { background: #22c55e; }
.dot.product { background: #3b82f6; }
.dot.recharge { background: #a855f7; }

.pct-bar { display: flex; align-items: center; gap: 8px; }
.pct-fill { height: 6px; background: #3b82f6; border-radius: 3px; min-width: 2px; transition: width 0.3s; }
.pct-label { font-size: 12px; color: #64748b; white-space: nowrap; }
</style>
