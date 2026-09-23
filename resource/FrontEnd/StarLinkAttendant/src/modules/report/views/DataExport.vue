<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const reportTypes = [
  { label: '营业总览', value: 1, desc: '当日关键经营指标汇总' },
  { label: '每日营收', value: 2, desc: '按日统计的营收趋势数据' },
  { label: '上机分析', value: 3, desc: '小时级上机分布数据' },
  { label: '商品排行', value: 4, desc: '商品销量排行 Top 50' },
  { label: '会员分析', value: 5, desc: '会员 LTV 排行 Top 50' },
  { label: '财务报表', value: 6, desc: '利润表、资产负债表、现金流量表汇总' },
]

const selectedType = ref(1)
const dateRange = ref<[string, string] | null>(null)
const exporting = ref(false)

const dateRangeStr = computed(() => {
  if (!dateRange.value) return { start: '', end: '' }
  return { start: dateRange.value[0], end: dateRange.value[1] }
})

const canExport = computed(() => dateRange.value !== null)

const datePresets = [
  { label: '今天', value: [today(), today()] as [string, string] },
  { label: '昨天', value: [daysAgo(1), daysAgo(1)] as [string, string] },
  { label: '最近 7 天', value: [daysAgo(6), today()] as [string, string] },
  { label: '最近 30 天', value: [daysAgo(29), today()] as [string, string] },
  { label: '本月', value: [monthStart(), today()] as [string, string] },
  { label: '上月', value: [lastMonthStart(), lastMonthEnd()] as [string, string] },
]

function today() {
  return new Date().toISOString().slice(0, 10)
}

function daysAgo(n: number) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d.toISOString().slice(0, 10)
}

function monthStart() {
  const d = new Date()
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10)
}

function lastMonthStart() {
  const d = new Date()
  return new Date(d.getFullYear(), d.getMonth() - 1, 1).toISOString().slice(0, 10)
}

function lastMonthEnd() {
  const d = new Date()
  return new Date(d.getFullYear(), d.getMonth(), 0).toISOString().slice(0, 10)
}

async function handleExport() {
  if (!dateRange.value) {
    ElMessage.warning('请选择导出日期范围')
    return
  }
  exporting.value = true
  try {
    const res = await api.exportReport(
      selectedType.value,
      dateRangeStr.value.start,
      dateRangeStr.value.end
    )
    const blob = res.data as Blob
    const type = reportTypes.find(t => t.value === selectedType.value)
    downloadBlob(blob, (type?.label || '报表') + '.xlsx')
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">数据导出</h1>
      <p class="page-desc">选择报表类型和日期范围，导出 Excel 文件</p>
    </div>

    <div class="page-content">
      <!-- 报表类型 -->
      <div class="form-section">
        <div class="section-label">报表类型</div>
        <div class="type-grid">
          <div
            v-for="t in reportTypes"
            :key="t.value"
            class="type-card"
            :class="{ active: selectedType === t.value }"
            @click="selectedType = t.value"
          >
            <div class="type-card-header">
              <span class="type-name">{{ t.label }}</span>
              <span v-if="selectedType === t.value" class="type-check">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="20 6 9 17 4 12"></polyline>
                </svg>
              </span>
            </div>
            <p class="type-desc">{{ t.desc }}</p>
          </div>
        </div>
      </div>

      <!-- 日期范围 -->
      <div class="form-section">
        <div class="section-label">日期范围</div>
        <div class="date-row">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            :shortcuts="datePresets.map(p => ({ text: p.label, value: p.value }))"
            style="width: 360px"
          />
          <button class="btn-export" :disabled="!canExport || exporting" @click="handleExport">
            <svg v-if="!exporting" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
              <polyline points="7 10 12 15 17 10"></polyline>
              <line x1="12" y1="15" x2="12" y2="3"></line>
            </svg>
            <span v-else class="loading-spinner"></span>
            {{ exporting ? '导出中...' : '导出 Excel' }}
          </button>
        </div>
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

.page-content {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.form-section {
  margin-bottom: 24px;
}

.form-section:last-child {
  margin-bottom: 0;
}

.section-label {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 12px;
}

.type-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.type-card {
  padding: 14px 16px;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.type-card:hover {
  border-color: #93c5fd;
  background: #f8fafc;
}

.type-card.active {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.04);
}

.type-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.type-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.type-card.active .type-name {
  color: #3b82f6;
}

.type-check {
  color: #3b82f6;
  display: flex;
  align-items: center;
}

.type-desc {
  font-size: 12px;
  color: #94a3b8;
  margin: 0;
}

.date-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-export {
  height: 38px;
  padding: 0 20px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  color: white;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s;
}

.btn-export:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.btn-export:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 768px) {
  .type-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
