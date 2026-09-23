<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshRight, Plus, Calendar, Check, View, Money, Tickets, RefreshLeft } from '@element-plus/icons-vue'
import { getSettlementList, generateSettlement, confirmSettlement } from '../api'
import { SETTLEMENT_STATUS_MAP } from '@/common/constants'
import { formatMoney } from '@/common/utils/money'
import { formatDate, formatDateOnly } from '@/common/utils/date'
import type { SettlementItem } from '../types'

const loading = ref(false)
const tableData = ref<SettlementItem[]>([])
const total = ref(0)
const generateDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const detailItem = ref<SettlementItem | null>(null)
const settlementDate = ref(formatDateOnly(new Date()))

const queryParams = reactive({
  page: 1, size: 10, startTime: '', endTime: '', status: null as number | null,
})
const dateRange = ref<[string, string] | null>(null)

const totalRevenue = computed(() => tableData.value.reduce((s, r) => s + (Number(r.totalRevenue) || 0), 0))
const totalOrders = computed(() => tableData.value.reduce((s, r) => s + (r.totalOrders || 0), 0))
const totalRefunds = computed(() => tableData.value.reduce((s, r) => s + (Number(r.totalRefund) || 0), 0))

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getSettlementList(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { tableData.value = [] }
  finally { loading.value = false }
}

const handleSearch = () => { queryParams.page = 1; fetchData() }
const handleReset = () => {
  queryParams.startTime = ''; queryParams.endTime = ''; queryParams.status = null
  dateRange.value = null
  handleSearch()
}
const handlePageChange = (page: number) => { queryParams.page = page; fetchData() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchData() }

const handleDateChange = (val: [string, string] | null) => {
  queryParams.startTime = val?.[0] || ''
  queryParams.endTime = val?.[1] || ''
}

const handleGenerate = () => {
  settlementDate.value = formatDateOnly(new Date())
  generateDialogVisible.value = true
}

const confirmGenerate = async () => {
  if (!settlementDate.value) { ElMessage.warning('请选择日期'); return }
  try {
    await generateSettlement(settlementDate.value)
    ElMessage.success('日结生成成功')
    generateDialogVisible.value = false
    fetchData()
  } catch { /* handled */ }
}

const handleConfirm = async (row: SettlementItem) => {
  try {
    await ElMessageBox.confirm(`确定确认 ${row.settleDate} 的日结记录？确认后不可修改。`, '确认日结', { type: 'warning' })
    await confirmSettlement(row.id)
    ElMessage.success('日结已确认')
    fetchData()
  } catch { /* cancelled */ }
}

const viewDetail = (row: SettlementItem) => {
  detailItem.value = row
  detailDialogVisible.value = true
}

const getStatusType = (status: number) => status === 1 ? 'success' : 'warning'

onMounted(() => { fetchData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">日结管理</h1>
      <p class="page-desc">管理每日营业结算记录</p>
    </div>

    <!-- 汇总卡片 -->
    <div class="summary-cards">
      <div class="summary-card">
        <div class="sc-icon" style="background: #eff6ff; color: #3b82f6">
          <el-icon :size="24"><Calendar /></el-icon>
        </div>
        <div class="sc-info">
          <div class="sc-label">当前页记录</div>
          <div class="sc-value">{{ tableData.length }} 天</div>
        </div>
      </div>
      <div class="summary-card">
        <div class="sc-icon" style="background: #ecfdf5; color: #10b981">
          <el-icon :size="24"><Money /></el-icon>
        </div>
        <div class="sc-info">
          <div class="sc-label">总营收</div>
          <div class="sc-value">¥{{ formatMoney(totalRevenue) }}</div>
        </div>
      </div>
      <div class="summary-card">
        <div class="sc-icon" style="background: #fef3c7; color: #d97706">
          <el-icon :size="24"><Tickets /></el-icon>
        </div>
        <div class="sc-info">
          <div class="sc-label">总订单</div>
          <div class="sc-value">{{ totalOrders }} 笔</div>
        </div>
      </div>
      <div class="summary-card">
        <div class="sc-icon" style="background: #fef2f2; color: #ef4444">
          <el-icon :size="24"><RefreshLeft /></el-icon>
        </div>
        <div class="sc-info">
          <div class="sc-label">总退款</div>
          <div class="sc-value">¥{{ formatMoney(totalRefunds) }}</div>
        </div>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="日期范围">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" @change="handleDateChange" style="width: 260px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, val) in SETTLEMENT_STATUS_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item style="margin-left: auto">
          <el-button type="primary" :icon="Plus" @click="handleGenerate">生成日结</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="settleDate" label="日期" width="120">
          <template #default="{ row }">
            <span style="font-weight: 600; color: #1e293b">{{ row.settleDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总营收" width="110" align="right">
          <template #default="{ row }"><span style="font-weight: 600">¥{{ formatMoney(row.totalRevenue) }}</span></template>
        </el-table-column>
        <el-table-column label="现金收入" width="110" align="right">
          <template #default="{ row }"><span style="color: #10b981">¥{{ formatMoney(row.cashAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="余额收入" width="110" align="right">
          <template #default="{ row }"><span style="color: #3b82f6">¥{{ formatMoney(row.balanceAmount) }}</span></template>
        </el-table-column>
        <el-table-column prop="totalOrders" label="订单" width="70" align="center" />
        <el-table-column label="退款" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.totalRefund > 0" style="color: #ef4444">¥{{ formatMoney(row.totalRefund) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="light">
              <el-icon v-if="row.status === 1" style="margin-right: 2px"><Check /></el-icon>
              {{ SETTLEMENT_STATUS_MAP[row.status] || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认时间" width="160">
          <template #default="{ row }">
            <span v-if="row.confirmedAt" class="time-text">{{ formatDate(row.confirmedAt) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :icon="View" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" type="success" link size="small" :icon="Check" @click="handleConfirm(row)">确认</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
      </div>
    </div>

    <!-- 生成日结弹窗 -->
    <el-dialog v-model="generateDialogVisible" title="生成日结" width="400px" :close-on-click-modal="false">
      <div class="dialog-tip">
        <el-icon :size="16"><Calendar /></el-icon>
        <span>系统将汇总该日所有订单和支付数据生成日结记录</span>
      </div>
      <el-form label-width="80px" style="margin-top: 16px">
        <el-form-item label="结算日期">
          <el-date-picker v-model="settlementDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="日结详情" width="520px">
      <template v-if="detailItem">
        <div class="detail-date-header">
          <span class="detail-date">{{ detailItem.settleDate }}</span>
          <el-tag :type="getStatusType(detailItem.status)" size="small">{{ SETTLEMENT_STATUS_MAP[detailItem.status] }}</el-tag>
        </div>
        <el-descriptions :column="2" border size="small" style="margin-top: 12px">
          <el-descriptions-item label="总营收"><span style="font-weight: 700; color: #1e293b">¥{{ formatMoney(detailItem.totalRevenue) }}</span></el-descriptions-item>
          <el-descriptions-item label="订单数">{{ detailItem.totalOrders }} 笔</el-descriptions-item>
          <el-descriptions-item label="现金收入"><span style="color: #10b981">¥{{ formatMoney(detailItem.cashAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="余额收入"><span style="color: #3b82f6">¥{{ formatMoney(detailItem.balanceAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="退款金额"><span style="color: #ef4444">¥{{ formatMoney(detailItem.totalRefund) }}</span></el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ detailItem.confirmedAt ? formatDate(detailItem.confirmedAt) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailItem.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 20px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.page-desc { font-size: 14px; color: #64748b; margin-top: 4px; }

/* Summary cards */
.summary-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.summary-card {
  background: white; border-radius: 12px; padding: 16px; display: flex; align-items: center; gap: 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.sc-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.sc-label { font-size: 12px; color: #64748b; margin-bottom: 2px; }
.sc-value { font-size: 20px; font-weight: 700; color: #1e293b; }

.filter-card { background: white; border-radius: 12px; padding: 16px 20px 4px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.table-card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.text-muted { color: #cbd5e1; }
.time-text { font-size: 12px; color: #64748b; }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }

.dialog-tip {
  display: flex; align-items: center; gap: 8px; padding: 10px 12px;
  background: #eff6ff; border-radius: 6px; font-size: 13px; color: #1e40af;
}
.detail-date-header {
  display: flex; align-items: center; gap: 12px;
}
.detail-date { font-size: 20px; font-weight: 700; color: #1e293b; }
</style>
