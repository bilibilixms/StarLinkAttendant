<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshRight, Plus, Clock, Money, TrendCharts } from '@element-plus/icons-vue'
import { getShiftList, startShift, endShift } from '../api'
import { SHIFT_STATUS_MAP } from '@/common/constants'
import { formatMoney } from '@/common/utils/money'
import { formatDate } from '@/common/utils/date'
import type { ShiftItem } from '../types'

const loading = ref(false)
const tableData = ref<ShiftItem[]>([])
const total = ref(0)
const startDialogVisible = ref(false)
const endDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const openingBalance = ref(0)
const cashActual = ref(0)
const currentShift = ref<ShiftItem | null>(null)
const detailShift = ref<ShiftItem | null>(null)

const queryParams = reactive({
  page: 1, size: 10, status: null as number | null,
})

const activeShift = computed(() => tableData.value.find(s => s.status === 0) || null)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getShiftList(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { tableData.value = [] }
  finally { loading.value = false }
}

const handleSearch = () => { queryParams.page = 1; fetchData() }
const handleReset = () => { queryParams.status = null; handleSearch() }
const handlePageChange = (page: number) => { queryParams.page = page; fetchData() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchData() }

const handleStartShift = () => {
  openingBalance.value = 0
  startDialogVisible.value = true
}

const confirmStartShift = async () => {
  try {
    await startShift({ openingBalance: openingBalance.value })
    ElMessage.success('开班成功')
    startDialogVisible.value = false
    fetchData()
  } catch { /* handled */ }
}

const handleEndShift = (row: ShiftItem) => {
  currentShift.value = row
  cashActual.value = 0
  endDialogVisible.value = true
}

const confirmEndShift = async () => {
  try {
    await endShift({ cashActual: cashActual.value })
    ElMessage.success('结班成功')
    endDialogVisible.value = false
    fetchData()
  } catch { /* handled */ }
}

const viewShiftDetail = (row: ShiftItem) => {
  detailShift.value = row
  detailDialogVisible.value = true
}

const getStatusType = (status: number) => {
  const map: Record<number, string> = { 0: 'success', 1: '', 2: 'primary' }
  return map[status] || 'info'
}

const getExpectedCash = (shift: ShiftItem) => {
  return shift.openingBalance + shift.cashIncome - shift.cashExpenditure
}

onMounted(() => { fetchData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">班次管理</h1>
      <p class="page-desc">管理收银员班次和交接</p>
    </div>

    <!-- 当前班次卡片 -->
    <div v-if="activeShift" class="active-shift-card">
      <div class="active-shift-header">
        <div class="active-shift-badge">
          <span class="pulse-dot"></span>
          <span>当前进行中</span>
        </div>
        <el-button type="warning" size="small" @click="handleEndShift(activeShift)">结班</el-button>
      </div>
      <div class="active-shift-grid">
        <div class="shift-stat">
          <div class="shift-stat-label">班次编号</div>
          <div class="shift-stat-value">{{ activeShift.shiftNo }}</div>
        </div>
        <div class="shift-stat">
          <div class="shift-stat-label">开班时间</div>
          <div class="shift-stat-value">{{ formatDate(activeShift.openAt) }}</div>
        </div>
        <div class="shift-stat">
          <div class="shift-stat-label">备用金</div>
          <div class="shift-stat-value">¥{{ formatMoney(activeShift.openingBalance) }}</div>
        </div>
        <div class="shift-stat">
          <div class="shift-stat-label">现金收入</div>
          <div class="shift-stat-value" style="color: #10b981">¥{{ formatMoney(activeShift.cashIncome) }}</div>
        </div>
        <div class="shift-stat">
          <div class="shift-stat-label">现金支出</div>
          <div class="shift-stat-value" style="color: #ef4444">¥{{ formatMoney(activeShift.cashExpenditure) }}</div>
        </div>
        <div class="shift-stat">
          <div class="shift-stat-label">订单数</div>
          <div class="shift-stat-value">{{ activeShift.orderCount }} 笔</div>
        </div>
      </div>
    </div>

    <!-- 筛选 + 操作 -->
    <div class="filter-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, val) in SHIFT_STATUS_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item style="margin-left: auto">
          <el-button type="primary" :icon="Plus" @click="handleStartShift" :disabled="!!activeShift">开班</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="shiftNo" label="班次编号" width="170">
          <template #default="{ row }">
            <span class="shift-no">{{ row.shiftNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="开班时间" width="160">
          <template #default="{ row }">{{ formatDate(row.openAt) }}</template>
        </el-table-column>
        <el-table-column label="结班时间" width="160">
          <template #default="{ row }">{{ row.closeAt ? formatDate(row.closeAt) : '-' }}</template>
        </el-table-column>
        <el-table-column label="备用金" width="90" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.openingBalance) }}</template>
        </el-table-column>
        <el-table-column label="现金收入" width="95" align="right">
          <template #default="{ row }"><span style="color: #10b981">¥{{ formatMoney(row.cashIncome) }}</span></template>
        </el-table-column>
        <el-table-column label="现金支出" width="95" align="right">
          <template #default="{ row }"><span style="color: #ef4444">¥{{ formatMoney(row.cashExpenditure) }}</span></template>
        </el-table-column>
        <el-table-column label="差异" width="95" align="right">
          <template #default="{ row }">
            <span v-if="row.cashDiff != null" :class="row.cashDiff === 0 ? 'diff-zero' : 'diff-error'">
              {{ row.cashDiff >= 0 ? '+' : '' }}¥{{ formatMoney(row.cashDiff) }}
            </span>
            <span v-else class="diff-na">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderCount" label="订单" width="70" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="light">{{ SHIFT_STATUS_MAP[row.status] || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewShiftDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" type="warning" link size="small" @click="handleEndShift(row)">结班</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
      </div>
    </div>

    <!-- 开班弹窗 -->
    <el-dialog v-model="startDialogVisible" title="新开班次" width="400px" :close-on-click-modal="false">
      <div class="dialog-tip">
        <el-icon :size="16"><Clock /></el-icon>
        <span>开班后将记录本次班次的所有收银流水</span>
      </div>
      <el-form label-width="80px" style="margin-top: 16px">
        <el-form-item label="备用金">
          <el-input-number v-model="openingBalance" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmStartShift">确认开班</el-button>
      </template>
    </el-dialog>

    <!-- 结班弹窗 -->
    <el-dialog v-model="endDialogVisible" title="结班确认" width="480px" :close-on-click-modal="false">
      <template v-if="currentShift">
        <div class="end-shift-summary">
          <div class="es-row">
            <span class="es-label">班次编号</span>
            <span class="es-value">{{ currentShift.shiftNo }}</span>
          </div>
          <div class="es-row">
            <span class="es-label">开班时间</span>
            <span class="es-value">{{ formatDate(currentShift.openAt) }}</span>
          </div>
          <el-divider />
          <div class="es-row">
            <span class="es-label">备用金</span>
            <span class="es-value">¥{{ formatMoney(currentShift.openingBalance) }}</span>
          </div>
          <div class="es-row">
            <span class="es-label">现金收入</span>
            <span class="es-value" style="color: #10b981">+ ¥{{ formatMoney(currentShift.cashIncome) }}</span>
          </div>
          <div class="es-row">
            <span class="es-label">现金支出</span>
            <span class="es-value" style="color: #ef4444">- ¥{{ formatMoney(currentShift.cashExpenditure) }}</span>
          </div>
          <el-divider />
          <div class="es-row highlight">
            <span class="es-label">应收现金</span>
            <span class="es-value">¥{{ formatMoney(getExpectedCash(currentShift)) }}</span>
          </div>
        </div>
        <el-form label-width="80px" style="margin-top: 16px">
          <el-form-item label="实收现金">
            <el-input-number v-model="cashActual" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <div v-if="cashActual !== getExpectedCash(currentShift)" class="cash-diff-warning">
            差异: ¥{{ formatMoney(cashActual - getExpectedCash(currentShift)) }}
          </div>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="endDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="confirmEndShift">确认结班</el-button>
      </template>
    </el-dialog>

    <!-- 班次详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="班次详情" width="520px">
      <template v-if="detailShift">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="班次编号">{{ detailShift.shiftNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(detailShift.status)" size="small">{{ SHIFT_STATUS_MAP[detailShift.status] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="开班时间">{{ formatDate(detailShift.openAt) }}</el-descriptions-item>
          <el-descriptions-item label="结班时间">{{ detailShift.closeAt ? formatDate(detailShift.closeAt) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="备用金">¥{{ formatMoney(detailShift.openingBalance) }}</el-descriptions-item>
          <el-descriptions-item label="总营收">¥{{ formatMoney(detailShift.totalIncome) }}</el-descriptions-item>
          <el-descriptions-item label="现金收入"><span style="color: #10b981">¥{{ formatMoney(detailShift.cashIncome) }}</span></el-descriptions-item>
          <el-descriptions-item label="现金支出"><span style="color: #ef4444">¥{{ formatMoney(detailShift.cashExpenditure) }}</span></el-descriptions-item>
          <el-descriptions-item label="应收现金">{{ detailShift.cashExpected != null ? '¥' + formatMoney(detailShift.cashExpected) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="实收现金">{{ detailShift.cashActual != null ? '¥' + formatMoney(detailShift.cashActual) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="现金差异" :span="2">
            <span v-if="detailShift.cashDiff != null" :class="detailShift.cashDiff === 0 ? 'diff-zero' : 'diff-error'">
              {{ detailShift.cashDiff >= 0 ? '+' : '' }}¥{{ formatMoney(detailShift.cashDiff) }}
            </span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="订单数">{{ detailShift.orderCount }} 笔</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(detailShift.createdAt) }}</el-descriptions-item>
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

/* Active shift card */
.active-shift-card {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border: 1px solid #bfdbfe; border-radius: 12px; padding: 16px 20px; margin-bottom: 16px;
}
.active-shift-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.active-shift-badge { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; color: #1e40af; }
.pulse-dot {
  width: 8px; height: 8px; border-radius: 50%; background: #22c55e;
  animation: pulse 2s infinite; box-shadow: 0 0 0 0 rgba(34,197,94,0.4);
}
@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgba(34,197,94,0.4); }
  70% { box-shadow: 0 0 0 8px rgba(34,197,94,0); }
  100% { box-shadow: 0 0 0 0 rgba(34,197,94,0); }
}
.active-shift-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; }
.shift-stat { background: white; border-radius: 8px; padding: 10px 12px; }
.shift-stat-label { font-size: 11px; color: #64748b; margin-bottom: 4px; }
.shift-stat-value { font-size: 14px; font-weight: 600; color: #1e293b; }

.filter-card { background: white; border-radius: 12px; padding: 16px 20px 4px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.table-card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.shift-no { font-family: monospace; font-size: 12px; color: #475569; }
.diff-zero { color: #10b981; font-weight: 600; }
.diff-error { color: #ef4444; font-weight: 600; }
.diff-na { color: #cbd5e1; }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }

/* Dialogs */
.dialog-tip {
  display: flex; align-items: center; gap: 8px; padding: 10px 12px;
  background: #eff6ff; border-radius: 6px; font-size: 13px; color: #1e40af;
}
.end-shift-summary { background: #f8fafc; border-radius: 8px; padding: 14px 16px; }
.es-row { display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; }
.es-label { color: #64748b; }
.es-value { font-weight: 500; color: #1e293b; }
.es-row.highlight { font-size: 15px; }
.es-row.highlight .es-label { font-weight: 600; color: #334155; }
.es-row.highlight .es-value { font-weight: 700; color: #1e293b; font-size: 16px; }
.cash-diff-warning {
  text-align: center; padding: 8px; background: #fef2f2; border-radius: 6px;
  color: #dc2626; font-weight: 600; font-size: 14px; margin-top: 8px;
}
</style>
