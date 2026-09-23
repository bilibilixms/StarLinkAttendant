<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useReportStore } from '../stores'

const fmtDate = (d: Date) => d.toISOString().slice(0, 10)

const store = useReportStore()
const activeTab = ref<'sales' | 'profit'>('sales')
const dateRange = ref<[string, string]>([
  fmtDate(new Date(Date.now() - 30 * 86400000)),
  fmtDate(new Date()),
])
const topN = ref(10)

const fetchData = () => {
  const [start, end] = dateRange.value
  if (activeTab.value === 'sales') {
    store.fetchProductRanking(start, end, topN.value)
  } else {
    store.fetchProfitRanking(start, end, topN.value)
  }
}

const rankingData = () => {
  return activeTab.value === 'sales' ? store.productRanking : store.profitRanking
}

const formatMoney = (val: number) => {
  return '¥' + val.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">商品排行</h1>
      <div class="header-actions">
        <el-radio-group v-model="activeTab" size="small" @change="fetchData">
          <el-radio-button value="sales">按销售额</el-radio-button>
          <el-radio-button value="profit">按利润</el-radio-button>
        </el-radio-group>
        <span style="font-size: 13px; color: #64748b;">Top</span>
        <el-input-number v-model="topN" :min="5" :max="50" size="small" style="width: 100px" @change="fetchData" />
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

    <div class="page-card">
      <h3 class="card-title">{{ activeTab === 'sales' ? '商品销量排行' : '商品利润排行' }}</h3>
      <el-table :data="rankingData()" stripe style="width: 100%" :default-sort="{ prop: activeTab === 'sales' ? 'totalAmount' : 'totalProfit', order: 'descending' }">
        <el-table-column type="index" label="排名" width="70" />
        <el-table-column prop="productName" label="商品名称" min-width="160" />
        <el-table-column prop="totalQuantity" label="销量" width="100" sortable />
        <el-table-column prop="orderCount" label="单数" width="90" sortable />
        <el-table-column prop="totalAmount" label="销售额" width="140" sortable>
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'profit'" prop="totalProfit" label="利润" width="140" sortable>
          <template #default="{ row }">{{ formatMoney(row.totalProfit) }}</template>
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

.page-card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05); }
.card-title { font-size: 16px; font-weight: 600; color: #1e293b; margin: 0 0 20px; }
</style>
