<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, Refresh, VideoPause, VideoPlay, SwitchButton, CircleClose, CircleCheck } from '@element-plus/icons-vue'
import { useSessionStore } from '../stores'
import { endSession, pauseSession, resumeSession, forceEndSession, updateComputer } from '../api'
import { COMPUTER_STATUS_MAP, COMPUTER_STATUS_COLOR, DEVICE_TYPE_MAP, SESSION_STATUS } from '@/common/constants'
import { formatDate } from '@/common/utils/date'
import { formatMoney } from '@/common/utils/money'
import type { ComputerItem } from '../types'

const router = useRouter()
const sessionStore = useSessionStore()

/** 当前选中的机位 */
const selectedComputer = ref<ComputerItem | null>(null)
/** 操作面板可见性 */
const actionPanelVisible = ref(false)
/** 操作加载中 */
const actionLoading = ref(false)

/** 统计信息 */
const stats = computed(() => {
  const allComputers = sessionStore.seatAreas.flatMap(a => a.computers.filter(c => c.isActive === 1))
  const total = allComputers.length
  let idle = 0, inUse = 0, locked = 0, maintenance = 0
  for (const c of allComputers) {
    const status = sessionStore.seatStatusMap[c.id]?.status ?? c.status
    if (status === 0) idle++
    else if (status === 1) inUse++
    else if (status === 2) locked++
    else if (status === 3) maintenance++
  }
  return { total, idle, inUse, locked, maintenance }
})

/** 获取机位实时状态 */
function getComputerStatus(computer: ComputerItem) {
  return sessionStore.seatStatusMap[computer.id]?.status ?? computer.status
}

/** 获取机位使用者 */
function getComputerMember(computer: ComputerItem) {
  return sessionStore.seatStatusMap[computer.id]?.memberName
}

/** 获取机位上机时间 */
function getComputerStartTime(computer: ComputerItem) {
  return sessionStore.seatStatusMap[computer.id]?.startTime
}

/** 点击机位 */
function handleComputerClick(computer: ComputerItem) {
  selectedComputer.value = computer
  actionPanelVisible.value = true
}

/** 关闭操作面板 */
function closeActionPanel() {
  actionPanelVisible.value = false
  selectedComputer.value = null
}

/** 上机操作 */
function handleStartSession() {
  if (!selectedComputer.value) return
  router.push({ path: '/session/start', query: { computerId: String(selectedComputer.value.id) } })
}

/** 下机操作 */
async function handleEndSession() {
  if (!selectedComputer.value) return
  const status = getComputerStatus(selectedComputer.value)
  if (status !== 1) {
    ElMessage.warning('该机位当前未在使用中')
    return
  }
  try {
    await ElMessageBox.confirm('确定要为该机位执行下机结账操作吗？', '确认下机', { type: 'warning' })
    actionLoading.value = true
    // 需要先获取当前会话 ID，通过活跃会话列表查找
    const { getActiveSessions } = await import('../api')
    const res = await getActiveSessions()
    const session = (res.data || []).find(s => s.computerId === selectedComputer.value!.id)
    if (!session) {
      ElMessage.error('未找到该机位的活跃会话')
      return
    }
    await endSession({ sessionId: session.id })
    ElMessage.success('下机成功')
    closeActionPanel()
    sessionStore.loadSeatStatus()
  } catch { /* cancelled */ }
  finally { actionLoading.value = false }
}

/** 暂停/恢复 */
async function handlePauseResume() {
  if (!selectedComputer.value) return
  const status = getComputerStatus(selectedComputer.value)
  try {
    actionLoading.value = true
    const { getActiveSessions } = await import('../api')
    const res = await getActiveSessions()
    const session = (res.data || []).find(s => s.computerId === selectedComputer.value!.id)
    if (!session) {
      ElMessage.error('未找到该机位的活跃会话')
      return
    }
    if (status === 1 && session.status === SESSION_STATUS.ACTIVE) {
      await ElMessageBox.confirm('确定要暂停该机位的上机吗？', '确认暂停')
      await pauseSession({ sessionId: session.id })
      ElMessage.success('已暂停')
    } else if (status === 1 && session.status === SESSION_STATUS.PAUSED) {
      await resumeSession({ sessionId: session.id })
      ElMessage.success('已恢复')
    }
    closeActionPanel()
    sessionStore.loadSeatStatus()
  } catch { /* cancelled */ }
  finally { actionLoading.value = false }
}

/** 换机 */
function handleTransfer() {
  if (!selectedComputer.value) return
  router.push({ path: '/session/transfer', query: { computerId: String(selectedComputer.value.id) } })
}

/** 强制下机 */
async function handleForceEnd() {
  if (!selectedComputer.value) return
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入强制下机原因', '强制下机', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入原因',
    })
    actionLoading.value = true
    const { getActiveSessions } = await import('../api')
    const res = await getActiveSessions()
    const session = (res.data || []).find(s => s.computerId === selectedComputer.value!.id)
    if (!session) {
      ElMessage.error('未找到该机位的活跃会话')
      return
    }
    await forceEndSession({ sessionId: session.id, reason })
    ElMessage.success('已强制下机')
    closeActionPanel()
    sessionStore.loadSeatStatus()
  } catch { /* cancelled */ }
  finally { actionLoading.value = false }
}

/** 恢复使用（维修 → 空闲） */
async function handleRestoreFromMaintenance() {
  if (!selectedComputer.value) return
  try {
    await ElMessageBox.confirm('确定要将该机位恢复为正常使用吗？', '确认恢复', { type: 'info' })
    actionLoading.value = true
    await updateComputer(selectedComputer.value.id, { status: 0 })
    ElMessage.success('机位已恢复为空闲')
    closeActionPanel()
    sessionStore.loadSeatMap()
    sessionStore.loadSeatStatus()
  } catch { /* cancelled */ }
  finally { actionLoading.value = false }
}

/** 手动刷新状态 */
function handleRefresh() {
  sessionStore.loadSeatStatus()
  ElMessage.success('状态已刷新')
}

onMounted(() => {
  sessionStore.loadSeatMap()
  sessionStore.startPolling()
})

onUnmounted(() => {
  sessionStore.stopPolling()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div class="page-header-left">
        <h1 class="page-title">座位图</h1>
        <p class="page-desc">实时查看机位状态，点击机位进行操作</p>
      </div>
      <el-button :icon="Refresh" @click="handleRefresh">刷新状态</el-button>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-value">{{ stats.total }}</div>
        <div class="stat-label">总机位</div>
      </div>
      <div class="stat-card stat-idle">
        <div class="stat-value">{{ stats.idle }}</div>
        <div class="stat-label">空闲</div>
      </div>
      <div class="stat-card stat-inuse">
        <div class="stat-value">{{ stats.inUse }}</div>
        <div class="stat-label">使用中</div>
      </div>
      <div class="stat-card stat-locked">
        <div class="stat-value">{{ stats.locked }}</div>
        <div class="stat-label">锁定</div>
      </div>
      <div class="stat-card stat-maint">
        <div class="stat-value">{{ stats.maintenance }}</div>
        <div class="stat-label">维修中</div>
      </div>
    </div>

    <!-- 座位图区域 -->
    <div class="seat-map" v-loading="sessionStore.seatAreas.length === 0">
      <div v-for="area in sessionStore.seatAreas" :key="area.id" class="area-block">
        <div class="area-header" :style="{ borderLeftColor: area.areaColor || '#409eff' }">
          <span class="area-name">{{ area.areaName }}</span>
          <span class="area-count">{{ area.computers?.filter(c => c.isActive === 1).length || 0 }} 台</span>
        </div>
        <div class="computer-grid">
          <div
            v-for="computer in area.computers?.filter(c => c.isActive === 1)"
            :key="computer.id"
            class="computer-seat"
            :class="{ 'is-active': getComputerStatus(computer) === 1, 'is-clickable': true }"
            :style="{ '--seat-color': COMPUTER_STATUS_COLOR[getComputerStatus(computer)] || '#909399' }"
            @click="handleComputerClick(computer)"
          >
            <div class="seat-no">{{ computer.computerNo }}</div>
            <div class="seat-icon">
              <el-icon :size="20"><Monitor /></el-icon>
            </div>
            <div class="seat-status" :style="{ color: COMPUTER_STATUS_COLOR[getComputerStatus(computer)] }">
              {{ COMPUTER_STATUS_MAP[getComputerStatus(computer)] || '未知' }}
            </div>
            <div v-if="getComputerMember(computer)" class="seat-member" :title="getComputerMember(computer)">
              {{ getComputerMember(computer) }}
            </div>
          </div>
        </div>
      </div>

      <el-empty v-if="sessionStore.seatAreas.length > 0 && sessionStore.seatAreas.every(a => !a.computers?.length)" description="暂无机位数据" />
    </div>

    <!-- 机位操作抽屉 -->
    <el-drawer v-model="actionPanelVisible" title="机位操作" size="380px" :close-on-click-modal="true">
      <template v-if="selectedComputer">
        <div class="drawer-content">
          <div class="info-section">
            <h3 class="info-title">机位信息</h3>
            <div class="info-grid">
              <div class="info-item"><span class="info-label">机位编号</span><span class="info-value">{{ selectedComputer.computerNo }}</span></div>
              <div class="info-item"><span class="info-label">设备名称</span><span class="info-value">{{ selectedComputer.computerName || '-' }}</span></div>
              <div class="info-item"><span class="info-label">设备类型</span><span class="info-value">{{ DEVICE_TYPE_MAP[selectedComputer.deviceType] || '-' }}</span></div>
              <div class="info-item"><span class="info-label">座位标签</span><span class="info-value">{{ selectedComputer.seatLabel || '-' }}</span></div>
              <div class="info-item"><span class="info-label">当前状态</span>
                <span class="info-value">
                  <el-tag :color="COMPUTER_STATUS_COLOR[getComputerStatus(selectedComputer)]" effect="dark" size="small" style="color: #fff; border: none;">
                    {{ COMPUTER_STATUS_MAP[getComputerStatus(selectedComputer)] || '未知' }}
                  </el-tag>
                </span>
              </div>
              <div v-if="getComputerMember(selectedComputer)" class="info-item">
                <span class="info-label">使用者</span><span class="info-value">{{ getComputerMember(selectedComputer) }}</span>
              </div>
              <div v-if="getComputerStartTime(selectedComputer)" class="info-item">
                <span class="info-label">上机时间</span><span class="info-value">{{ formatDate(getComputerStartTime(selectedComputer)) }}</span>
              </div>
            </div>
          </div>

          <div class="action-section">
            <h3 class="info-title">快捷操作</h3>
            <div class="action-buttons">
              <el-button type="success" :icon="Monitor" :loading="actionLoading" @click="handleStartSession" :disabled="getComputerStatus(selectedComputer) === 1">上机</el-button>
              <el-button type="primary" :loading="actionLoading" @click="handleEndSession" :disabled="getComputerStatus(selectedComputer) !== 1">下机</el-button>
              <el-button type="warning" :icon="getComputerStatus(selectedComputer) === 1 ? VideoPause : VideoPlay" :loading="actionLoading" @click="handlePauseResume" :disabled="getComputerStatus(selectedComputer) !== 1">
                {{ getComputerStatus(selectedComputer) === 1 ? '暂停' : '恢复' }}
              </el-button>
              <el-button type="info" :icon="SwitchButton" :loading="actionLoading" @click="handleTransfer" :disabled="getComputerStatus(selectedComputer) !== 1">换机</el-button>
              <el-button type="danger" :icon="CircleClose" :loading="actionLoading" @click="handleForceEnd" :disabled="getComputerStatus(selectedComputer) !== 1">强制下机</el-button>
              <el-button v-if="getComputerStatus(selectedComputer) === 3" type="success" :icon="CircleCheck" :loading="actionLoading" @click="handleRestoreFromMaintenance">恢复使用</el-button>
            </div>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.page-desc { font-size: 14px; color: #64748b; margin-top: 4px; }

.stats-row { display: flex; gap: 12px; margin-bottom: 20px; }
.stat-card { flex: 1; background: white; border-radius: 10px; padding: 16px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.05); border-left: 3px solid #e2e8f0; }
.stat-idle { border-left-color: #67c23a; }
.stat-inuse { border-left-color: #409eff; }
.stat-locked { border-left-color: #e6a23c; }
.stat-maint { border-left-color: #f56c6c; }
.stat-value { font-size: 28px; font-weight: 700; color: #1e293b; }
.stat-label { font-size: 13px; color: #64748b; margin-top: 4px; }

.seat-map { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); min-height: 300px; }

.area-block { margin-bottom: 24px; }
.area-block:last-child { margin-bottom: 0; }
.area-header { display: flex; align-items: center; gap: 8px; padding: 8px 12px; margin-bottom: 12px; border-left: 4px solid #409eff; background: #f8fafc; border-radius: 0 6px 6px 0; }
.area-name { font-size: 15px; font-weight: 600; color: #1e293b; }
.area-count { font-size: 12px; color: #94a3b8; }

.computer-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(110px, 1fr)); gap: 12px; }

.computer-seat { position: relative; border: 2px solid var(--seat-color, #e2e8f0); border-radius: 10px; padding: 12px 8px; text-align: center; cursor: pointer; transition: all 0.2s; background: white; }
.computer-seat:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.computer-seat.is-active { background: color-mix(in srgb, var(--seat-color) 8%, white); }
.seat-no { font-size: 13px; font-weight: 600; color: #1e293b; margin-bottom: 4px; }
.seat-icon { color: var(--seat-color); margin: 4px 0; }
.seat-status { font-size: 11px; font-weight: 500; }
.seat-member { font-size: 11px; color: #64748b; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.drawer-content { padding: 0 4px; }
.info-section, .action-section { margin-bottom: 24px; }
.info-title { font-size: 15px; font-weight: 600; color: #1e293b; margin: 0 0 12px; }
.info-grid { display: flex; flex-direction: column; gap: 10px; }
.info-item { display: flex; justify-content: space-between; align-items: center; }
.info-label { font-size: 13px; color: #64748b; }
.info-value { font-size: 13px; color: #1e293b; font-weight: 500; }
.action-buttons { display: flex; flex-wrap: wrap; gap: 8px; }
</style>
