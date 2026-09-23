<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, RefreshRight } from '@element-plus/icons-vue'
import { getRefundList } from '../api'
import { REFUND_STATUS_MAP, REFUND_TYPE_MAP, PAYMENT_METHOD_MAP } from '@/common/constants'
import { formatMoney } from '@/common/utils/money'
import { formatDate } from '@/common/utils/date'
import type { RefundRecordItem } from '../types'

const loading = ref(false)
const tableData = ref<RefundRecordItem[]>([])
const total = ref(0)

const queryParams = reactive({
  page: 1, size: 10, refundNo: '', status: null as number | null,
  startTime: '', endTime: '',
})
const dateRange = ref<[string, string] | null>(null)

const totalRefundAmount = computed(() =>
  tableData.value.reduce((s, r) => s + (Number(r.refundAmount) || 0), 0)
)
const completedCount = computed(() =>
  tableData.value.filter(r => r.status === 1).length
)
const pendingCount = computed(() =>
  tableData.value.filter(r => r.status === 0).length
)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getRefundList(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { tableData.value = [] }
  finally { loading.value = false }
}

const handleSearch = () => { queryParams.page = 1; fetchData() }
const handleReset = () => {
  queryParams.refundNo = ''; queryParams.status = null
  queryParams.startTime = ''; queryParams.endTime = ''
  dateRange.value = null
  handleSearch()
}
const handlePageChange = (page: number) => { queryParams.page = page; fetchData() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchData() }

const handleDateChange = (val: [string, string] | null) => {
  queryParams.startTime = val?.[0] || ''
  queryParams.endTime = val?.[1] || ''
}

const getStatusType = (status: number) => {
  const map: Record<number, string> = { 0: 'warning', 1: 'success', 2: 'danger' }
  return map[status] || 'info'
}

const getStatusLabel = (status: number) => {
  return REFUND_STATUS_MAP[status] || '未知'
}

onMounted(() => { fetchData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">退款管理</h1>
      <p class="page-desc">查看和管理所有退款记录</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-card-icon" style="background: #fef2f2; color: #ef4444">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="1 4 1 10 7 10"></polyline><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"></path></svg>
        </div>
        <div class="stat-card-body">
          <div class="stat-card-label">退款总额</div>
          <div class="stat-card-value" style="color: #ef4444">¥{{ formatMoney(totalRefundAmount) }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-card-icon" style="background: #ecfdf5; color: #10b981">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"></polyline></svg>
        </div>
        <div class="stat-card-body">
          <div class="stat-card-label">已完成</div>
          <div class="stat-card-value" style="color: #10b981">{{ completedCount }} 笔</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-card-icon" style="background: #fef3c7; color: #d97706">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
        </div>
        <div class="stat-card-body">
          <div class="stat-card-label">待处理</div>
          <div class="stat-card-value" style="color: #d97706">{{ pendingCount }} 笔</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-card-icon" style="background: #eff6ff; color: #3b82f6">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg>
        </div>
        <div class="stat-card-body">
          <div class="stat-card-label">当前页记录</div>
          <div class="stat-card-value">{{ tableData.length }} 笔</div>
        </div>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="退款单号">
          <el-input v-model="queryParams.refundNo" placeholder="退款单号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, val) in REFUND_STATUS_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" @change="handleDateChange" style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="refundNo" label="退款单号" width="180">
          <template #default="{ row }">
            <span class="refund-no">{{ row.refundNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="退款金额" width="110" align="right">
          <template #default="{ row }">
            <span class="refund-amount">¥{{ formatMoney(row.refundAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="退款类型" width="95" align="center">
          <template #default="{ row }">
            <span class="type-badge" :class="row.refundType === 1 ? 'full' : 'partial'">
              {{ REFUND_TYPE_MAP[row.refundType] || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="退款方式" width="90">
          <template #default="{ row }">{{ PAYMENT_METHOD_MAP[row.refundMethod] || '-' }}</template>
        </el-table-column>
        <el-table-column label="退款原因" min-width="200">
          <template #default="{ row }">
            <span :class="row.refundReason ? '' : 'text-muted'">{{ row.refundReason || '未填写' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="light">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="退款时间" width="160">
          <template #default="{ row }">
            <span v-if="row.auditAt" class="time-text">{{ formatDate(row.auditAt) }}</span>
            <span v-else class="time-text">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 20px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.page-desc { font-size: 14px; color: #64748b; margin-top: 4px; }

/* Stat cards */
.stat-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.stat-card {
  background: white; border-radius: 12px; padding: 16px; display: flex; align-items: center; gap: 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.stat-card-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.stat-card-label { font-size: 12px; color: #64748b; margin-bottom: 2px; }
.stat-card-value { font-size: 20px; font-weight: 700; color: #1e293b; }

.filter-card { background: white; border-radius: 12px; padding: 16px 20px 4px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.table-card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }

.refund-no { font-family: 'SF Mono', 'Consolas', monospace; font-size: 12px; color: #475569; }
.refund-amount { font-weight: 700; color: #ef4444; }
.text-muted { color: #cbd5e1; }
.time-text { font-size: 12px; color: #64748b; }

.type-badge {
  font-size: 12px; padding: 2px 8px; border-radius: 4px; font-weight: 500;
}
.type-badge.full { background: #fef2f2; color: #dc2626; }
.type-badge.partial { background: #fff7ed; color: #d97706; }

.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }
</style>
