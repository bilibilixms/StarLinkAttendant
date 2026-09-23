<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshRight, VideoPause, VideoPlay, SwitchButton, CircleClose, View, Delete } from '@element-plus/icons-vue'
import { getSessionList, getActiveSessions, endSession, pauseSession, resumeSession, forceEndSession, deleteSession } from '../api'
import { SESSION_STATUS_MAP, COMPUTER_STATUS_MAP, AUTH_METHOD_MAP } from '@/common/constants'
import { formatDate } from '@/common/utils/date'
import { formatMoney } from '@/common/utils/money'
import type { SessionItem, SessionQuery } from '../types'

const loading = ref(false)
const tableData = ref<SessionItem[]>([])
const total = ref(0)
const queryParams = reactive<{ page: number; size: number } & SessionQuery>({
  page: 1, size: 10, memberName: '', computerNo: '', status: null,
})

/** 会话详情弹窗 */
const detailVisible = ref(false)
const detailData = ref<SessionItem | null>(null)
const detailLoading = ref(false)

/** 自动刷新定时器 */
let refreshTimer: ReturnType<typeof setInterval> | null = null

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getSessionList(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { tableData.value = [] }
  finally { loading.value = false }
}

/** 刷新活跃会话的时长数据 */
const refreshActiveData = async () => {
  try {
    const res = await getActiveSessions()
    const activeSessions = res.data || []
    // 仅更新活跃会话的计费时长
    for (const active of activeSessions) {
      const idx = tableData.value.findIndex(t => t.id === active.id)
      const row = idx === -1 ? undefined : tableData.value[idx]
      if (row) {
        row.billedMinutes = active.billedMinutes
        row.totalAmount = active.totalAmount
      }
    }
  } catch { /* ignore */ }
}

const handleSearch = () => { queryParams.page = 1; fetchData() }
const handleReset = () => {
  queryParams.memberName = ''; queryParams.computerNo = ''; queryParams.status = null
  handleSearch()
}
const handlePageChange = (page: number) => { queryParams.page = page; fetchData() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchData() }

/** 查看会话详情 */
async function viewDetail(row: SessionItem) {
  detailLoading.value = true
  detailVisible.value = true
  try {
    const { getSessionDetail } = await import('../api')
    const res = await getSessionDetail(row.id)
    detailData.value = res.data
  } catch { detailData.value = null }
  finally { detailLoading.value = false }
}

/** 下机 */
async function handleEnd(row: SessionItem) {
  try {
    await ElMessageBox.confirm(`确定要为会员「${row.memberName || '散客'}」执行下机结账吗？`, '确认下机', { type: 'warning' })
    await endSession({ sessionId: row.id })
    ElMessage.success('下机成功')
    fetchData()
  } catch { /* cancelled */ }
}

/** 暂停/恢复 */
async function handlePauseResume(row: SessionItem) {
  try {
    if (row.status === 0) {
      await ElMessageBox.confirm('确定要暂停该会话吗？暂停后将停止计费。', '确认暂停')
      await pauseSession({ sessionId: row.id })
      ElMessage.success('已暂停')
    } else if (row.status === 1) {
      await resumeSession({ sessionId: row.id })
      ElMessage.success('已恢复')
    }
    fetchData()
  } catch { /* cancelled */ }
}

/** 强制下机 */
async function handleForceEnd(row: SessionItem) {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入强制下机原因', '强制下机', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入原因',
    })
    await forceEndSession({ sessionId: row.id, reason })
    ElMessage.success('已强制下机')
    fetchData()
  } catch { /* cancelled */ }
}

/** 删除会话记录 */
async function handleDelete(row: SessionItem) {
  try {
    await ElMessageBox.confirm(`确定要删除会话「${row.sessionNo}」吗？删除后不可恢复。`, '确认删除', { type: 'warning' })
    await deleteSession(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* cancelled */ }
}

/** 计算已用时长（格式化显示） */
function formatDuration(minutes: number): string {
  if (!minutes) return '0分钟'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h > 0) return `${h}小时${m}分钟`
  return `${m}分钟`
}

/** 获取状态标签类型 */
function getStatusType(status: number) {
  switch (status) {
    case 0: return 'success'
    case 1: return 'warning'
    case 2: return 'info'
    case 3: return 'danger'
    case 4: return 'danger'
    default: return 'info'
  }
}

onMounted(() => {
  fetchData()
  // 每 10 秒刷新活跃会话数据
  refreshTimer = setInterval(refreshActiveData, 10000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">会话监控</h1>
      <p class="page-desc">实时监控所有上机会话，支持暂停、恢复、下机等操作</p>
    </div>

    <div class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="会员姓名">
          <el-input v-model="queryParams.memberName" placeholder="请输入会员姓名" clearable />
        </el-form-item>
        <el-form-item label="机位编号">
          <el-input v-model="queryParams.computerNo" placeholder="请输入机位编号" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, val) in SESSION_STATUS_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="sessionNo" label="会话编号" width="150" />
        <el-table-column prop="computerNo" label="机位" width="100" />
        <el-table-column prop="memberName" label="会员" width="100">
          <template #default="{ row }">{{ row.memberName || '散客' }}</template>
        </el-table-column>
        <el-table-column label="认证方式" width="90">
          <template #default="{ row }">{{ AUTH_METHOD_MAP[row.authMethod] || '-' }}</template>
        </el-table-column>
        <el-table-column label="上机时间" width="170">
          <template #default="{ row }">{{ formatDate(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="已计费时长" width="120">
          <template #default="{ row }">
            <span class="duration-text">{{ formatDuration(row.billedMinutes) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="费用" width="100">
          <template #default="{ row }">¥{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ row.statusLabel || SESSION_STATUS_MAP[row.status] || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :icon="View" @click="viewDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 0 || row.status === 1"
              :type="row.status === 1 ? 'success' : 'warning'"
              link size="small"
              :icon="row.status === 1 ? VideoPlay : VideoPause"
              @click="handlePauseResume(row)"
            >{{ row.status === 1 ? '恢复' : '暂停' }}</el-button>
            <el-button v-if="row.status === 0 || row.status === 1" type="primary" link size="small" :icon="SwitchButton" @click="$router.push({ path: '/session/transfer', query: { sessionId: String(row.id) } })">换机</el-button>
            <el-button v-if="row.status === 0 || row.status === 1" type="danger" link size="small" :icon="CircleClose" @click="handleForceEnd(row)">强制</el-button>
            <el-button v-if="row.status === 0 || row.status === 1" type="success" link size="small" @click="handleEnd(row)">下机</el-button>
            <el-button v-if="row.status === 2 || row.status === 3 || row.status === 4" type="danger" link size="small" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
      </div>
    </div>

    <!-- 会话详情抽屉 -->
    <el-drawer v-model="detailVisible" title="会话详情" size="480px" v-loading="detailLoading">
      <template v-if="detailData">
        <div class="detail-section">
          <h3 class="detail-title">基本信息</h3>
          <div class="detail-grid">
            <div class="detail-item"><span class="detail-label">会话编号</span><span class="detail-value">{{ detailData.sessionNo }}</span></div>
            <div class="detail-item"><span class="detail-label">机位</span><span class="detail-value">{{ detailData.computerNo }} {{ detailData.computerName }}</span></div>
            <div class="detail-item"><span class="detail-label">会员</span><span class="detail-value">{{ detailData.memberName || '散客' }}</span></div>
            <div class="detail-item"><span class="detail-label">手机号</span><span class="detail-value">{{ detailData.memberPhone || '-' }}</span></div>
            <div class="detail-item"><span class="detail-label">认证方式</span><span class="detail-value">{{ AUTH_METHOD_MAP[detailData.authMethod] || '-' }}</span></div>
            <div class="detail-item"><span class="detail-label">费率方案</span><span class="detail-value">{{ detailData.tariffPlanName || '-' }}</span></div>
            <div class="detail-item"><span class="detail-label">状态</span>
              <span class="detail-value"><el-tag :type="getStatusType(detailData.status)" size="small">{{ detailData.statusLabel || SESSION_STATUS_MAP[detailData.status] }}</el-tag></span>
            </div>
          </div>
        </div>
        <div class="detail-section">
          <h3 class="detail-title">时间信息</h3>
          <div class="detail-grid">
            <div class="detail-item"><span class="detail-label">上机时间</span><span class="detail-value">{{ formatDate(detailData.startTime) }}</span></div>
            <div class="detail-item"><span class="detail-label">下机时间</span><span class="detail-value">{{ detailData.endTime ? formatDate(detailData.endTime) : '-' }}</span></div>
            <div class="detail-item"><span class="detail-label">已计费时长</span><span class="detail-value">{{ formatDuration(detailData.billedMinutes) }}</span></div>
            <div class="detail-item"><span class="detail-label">赠送时长</span><span class="detail-value">{{ formatDuration(detailData.freeMinutes) }}</span></div>
            <div class="detail-item"><span class="detail-label">暂停次数</span><span class="detail-value">{{ detailData.pauseCount }} 次</span></div>
            <div class="detail-item"><span class="detail-label">暂停总时长</span><span class="detail-value">{{ formatDuration(detailData.pauseDuration) }}</span></div>
          </div>
        </div>
        <div class="detail-section">
          <h3 class="detail-title">费用信息</h3>
          <div class="detail-grid">
            <div class="detail-item"><span class="detail-label">总费用</span><span class="detail-value">¥{{ formatMoney(detailData.totalAmount) }}</span></div>
            <div class="detail-item"><span class="detail-label">优惠金额</span><span class="detail-value">¥{{ formatMoney(detailData.discountAmount) }}</span></div>
            <div class="detail-item"><span class="detail-label">实付金额</span><span class="detail-value">¥{{ formatMoney(detailData.paidAmount) }}</span></div>
          </div>
        </div>
        <div v-if="detailData.timings?.length" class="detail-section">
          <h3 class="detail-title">计时明细</h3>
          <el-table :data="detailData.timings" size="small" stripe>
            <el-table-column prop="timingTypeLabel" label="类型" width="100" />
            <el-table-column label="开始" width="170"><template #default="{ row }">{{ formatDate(row.startTime) }}</template></el-table-column>
            <el-table-column label="结束" width="170"><template #default="{ row }">{{ row.endTime ? formatDate(row.endTime) : '进行中' }}</template></el-table-column>
            <el-table-column label="时长" width="80"><template #default="{ row }">{{ row.durationMinutes }}分</template></el-table-column>
            <el-table-column label="费用"><template #default="{ row }">¥{{ formatMoney(row.amount) }}</template></el-table-column>
          </el-table>
        </div>
        <div v-if="detailData.remark" class="detail-section">
          <h3 class="detail-title">备注</h3>
          <p class="remark-text">{{ detailData.remark }}</p>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.page-desc { font-size: 14px; color: #64748b; margin-top: 4px; }
.search-card { background: white; border-radius: 12px; padding: 20px 20px 4px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.table-card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }
.duration-text { font-weight: 500; color: #409eff; }

.detail-section { margin-bottom: 24px; }
.detail-title { font-size: 15px; font-weight: 600; color: #1e293b; margin: 0 0 12px; border-bottom: 1px solid #f1f5f9; padding-bottom: 8px; }
.detail-grid { display: flex; flex-direction: column; gap: 10px; }
.detail-item { display: flex; justify-content: space-between; align-items: center; }
.detail-label { font-size: 13px; color: #64748b; }
.detail-value { font-size: 13px; color: #1e293b; font-weight: 500; }
.remark-text { font-size: 13px; color: #64748b; line-height: 1.6; }
</style>