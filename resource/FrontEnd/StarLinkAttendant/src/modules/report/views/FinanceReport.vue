<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useReportStore } from '../stores'

const fmtDateTime = (d: Date) => {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const store = useReportStore()
const dateRange = ref<[string, string]>([
  fmtDateTime(new Date(new Date().getFullYear(), new Date().getMonth(), 1)),
  fmtDateTime(new Date(new Date().setHours(23, 59, 59, 999))),
])

const fetchData = () => {
  const [start, end] = dateRange.value
  store.fetchFinance(start, end)
}

const formatMoney = (val: number | undefined | null) => {
  if (val == null) return '--'
  const abs = Math.abs(val)
  const sign = val < 0 ? '-' : ''
  return sign + '¥' + abs.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const fm = formatMoney

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">财务报表</h1>
      <div class="header-actions">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
          @change="fetchData"
        />
      </div>
    </div>

    <div class="finance-grid">
      <!-- 利润表 -->
      <div class="page-card">
        <h3 class="card-title">利润表（损益表）</h3>
        <table class="finance-table">
          <tbody>
            <tr><td class="fl">一、营业收入</td><td class="fr">{{ fm(store.finance?.totalIncome) }}</td></tr>
            <tr class="sub"><td class="fl">现金收入</td><td class="fr">{{ fm(store.finance?.cashIncome) }}</td></tr>
            <tr class="sub"><td class="fl">余额收入</td><td class="fr">{{ fm(store.finance?.balanceIncome) }}</td></tr>
            <tr><td class="fl">减：退款</td><td class="fr">{{ fm(store.finance?.refundAmount) }}</td></tr>
            <tr class="total"><td class="fl">二、净收入</td><td class="fr">{{ fm(store.finance?.netIncome) }}</td></tr>
            <tr><td class="fl">减：采购支出</td><td class="fr">{{ fm(store.finance?.purchaseExpense) }}</td></tr>
            <tr class="total highlight"><td class="fl">三、净利润</td><td class="fr">{{ fm(store.finance?.netProfit) }}</td></tr>
          </tbody>
        </table>
        <p class="table-note">统计区间：{{ dateRange[0]?.slice(0, 10) }} ~ {{ dateRange[1]?.slice(0, 10) }}</p>
      </div>

      <!-- 资产负债表 -->
      <div class="page-card">
        <h3 class="card-title">资产负债表</h3>
        <table class="finance-table">
          <thead>
            <tr><th class="fl">资产</th><th class="fr">金额</th></tr>
          </thead>
          <tbody>
            <tr><td class="fl">库存总值</td><td class="fr">{{ fm(store.finance?.inventoryValue) }}</td></tr>
            <tr><td class="fl">现金余额</td><td class="fr">{{ fm(store.finance?.cashBalance) }}</td></tr>
            <tr class="total"><td class="fl">资产总计</td><td class="fr">{{ fm(store.finance?.totalAssets) }}</td></tr>
          </tbody>
          <thead>
            <tr><th class="fl">负债与权益</th><th class="fr">金额</th></tr>
          </thead>
          <tbody>
            <tr><td class="fl">会员储值余额（负债）</td><td class="fr">{{ fm(store.finance?.memberBalance) }}</td></tr>
            <tr class="total"><td class="fl">净资产（所有者权益）</td><td class="fr">{{ fm(store.finance?.netAssets) }}</td></tr>
          </tbody>
        </table>
        <p class="table-note">时点数据（截至 {{ dateRange[1]?.slice(0, 10) }}）</p>
      </div>

      <!-- 现金流量表 -->
      <div class="page-card">
        <h3 class="card-title">现金流量表</h3>
        <table class="finance-table">
          <tbody>
            <tr><td class="fl">一、经营活动现金流入</td><td class="fr">{{ fm(store.finance?.operatingInflow) }}</td></tr>
            <tr><td class="fl">充值现金流入</td><td class="fr">{{ fm(store.finance?.rechargeInflow) }}</td></tr>
            <tr class="total"><td class="fl">现金流入小计</td><td class="fr">{{ fm(store.finance?.totalInflow) }}</td></tr>
            <tr><td class="fl">二、经营活动现金流出</td><td></td></tr>
            <tr class="sub"><td class="fl">采购支出</td><td class="fr">{{ fm(store.finance?.purchaseExpense) }}</td></tr>
            <tr class="sub"><td class="fl">现金退款</td><td class="fr">{{ fm(store.finance?.cashOutflow) }}</td></tr>
            <tr><td class="fl">现金流出小计</td><td class="fr">{{ fm((store.finance?.cashOutflow ?? 0) + (store.finance?.purchaseExpense ?? 0)) }}</td></tr>
            <tr class="total highlight"><td class="fl">三、现金净流量</td><td class="fr">{{ fm(store.finance?.netCashFlow) }}</td></tr>
          </tbody>
        </table>
        <p class="table-note">统计区间：{{ dateRange[0]?.slice(0, 10) }} ~ {{ dateRange[1]?.slice(0, 10) }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.header-actions { display: flex; gap: 12px; }

.finance-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }

.page-card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05); }
.card-title { font-size: 16px; font-weight: 600; color: #1e293b; margin: 0 0 16px; }

.finance-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.finance-table th { text-align: left; padding: 8px 0; border-bottom: 2px solid #e2e8f0; color: #64748b; font-weight: 600; font-size: 13px; }
.finance-table td { padding: 8px 0; border-bottom: 1px solid #f1f5f9; }
.finance-table .fl { text-align: left; color: #334155; }
.finance-table .fr { text-align: right; color: #1e293b; font-variant-numeric: tabular-nums; }
.finance-table .sub td { padding-left: 20px; color: #64748b; font-size: 13px; }
.finance-table .total td { border-top: 2px solid #e2e8f0; font-weight: 700; padding-top: 10px; }
.finance-table .highlight td { color: #3b82f6; }
.finance-table thead th.fr { text-align: right; }

.table-note { font-size: 12px; color: #94a3b8; margin-top: 12px; }

@media (max-width: 1100px) { .finance-grid { grid-template-columns: 1fr; } }
</style>
