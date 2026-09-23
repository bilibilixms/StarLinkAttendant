<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getActiveSessions, getSeatMap, transferSession } from '../api'
import { DEVICE_TYPE_MAP, COMPUTER_STATUS_MAP, COMPUTER_STATUS_COLOR, SESSION_STATUS_MAP } from '@/common/constants'
import { formatDate } from '@/common/utils/date'
import type { SessionItem, SeatAreaItem, ComputerItem } from '../types'

const router = useRouter()
const route = useRoute()

/** 活跃会话列表 */
const sessions = ref<SessionItem[]>([])
const sessionLoading = ref(false)

/** 选中的会话 */
const selectedSession = ref<SessionItem | null>(null)

/** 座位图 */
const seatAreas = ref<SeatAreaItem[]>([])
const seatLoading = ref(false)

/** 选中的目标机位 */
const targetComputerId = ref<number | null>(null)

/** 提交中 */
const submitting = ref(false)

/** 预选参数 */
const preselectedComputerId = computed(() => {
  const id = route.query.computerId
  return id ? Number(id) : null
})
const preselectedSessionId = computed(() => {
  const id = route.query.sessionId
  return id ? Number(id) : null
})

/** 可用目标机位（排除当前机位） */
const availableTargets = computed(() => {
  return seatAreas.value.flatMap(a =>
    a.computers
      .filter(c => c.isActive === 1 && c.status === 0 && c.id !== selectedSession.value?.computerId)
      .map(c => ({ ...c, areaName: a.areaName, areaColor: a.areaColor }))
  )
})

/** 选中的目标机位详情 */
const targetComputer = computed(() => {
  if (!targetComputerId.value) return null
  return availableTargets.value.find(c => c.id === targetComputerId.value) || null
})

/** 加载活跃会话 */
async function loadSessions() {
  sessionLoading.value = true
  try {
    const res = await getActiveSessions()
    sessions.value = res.data || []
    // 自动选中预选的会话
    if (preselectedSessionId.value) {
      const s = sessions.value.find(s => s.id === preselectedSessionId.value)
      if (s) selectedSession.value = s
    }
    // 如果通过 computerId 进入，找到对应会话
    if (preselectedComputerId.value && !selectedSession.value) {
      const s = sessions.value.find(s => s.computerId === preselectedComputerId.value)
      if (s) selectedSession.value = s
    }
  } catch { sessions.value = [] }
  finally { sessionLoading.value = false }
}

/** 加载座位图 */
async function loadSeats() {
  seatLoading.value = true
  try {
    const res = await getSeatMap()
    seatAreas.value = res.data || []
  } catch { seatAreas.value = [] }
  finally { seatLoading.value = false }
}

/** 选择会话 */
function selectSession(session: SessionItem) {
  selectedSession.value = session
  targetComputerId.value = null
}

/** 选择目标机位 */
function selectTarget(computer: ComputerItem & { areaName: string }) {
  targetComputerId.value = computer.id
}

/** 提交换机 */
async function handleTransfer() {
  if (!selectedSession.value) { ElMessage.warning('请选择要换机的会话'); return }
  if (!targetComputerId.value) { ElMessage.warning('请选择目标机位'); return }

  submitting.value = true
  try {
    await transferSession({
      sessionId: selectedSession.value.id,
      targetComputerId: targetComputerId.value,
    })
    ElMessage.success('换机成功')
    router.push('/session/monitor')
  } catch { /* handled by interceptor */ }
  finally { submitting.value = false }
}

onMounted(() => {
  loadSessions()
  loadSeats()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button :icon="ArrowLeft" text @click="router.back()">返回</el-button>
      <div>
        <h1 class="page-title">换机操作</h1>
        <p class="page-desc">选择活跃会话和目标机位，执行换机</p>
      </div>
    </div>

    <div class="transfer-layout">
      <!-- 左侧：选择会话 -->
      <div class="transfer-card">
        <h3 class="card-title">选择会话</h3>
        <div v-loading="sessionLoading" class="session-list">
          <div
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ 'is-selected': selectedSession?.id === session.id }"
            @click="selectSession(session)"
          >
            <div class="session-top">
              <span class="session-no">{{ session.sessionNo }}</span>
              <el-tag size="small" type="success">{{ SESSION_STATUS_MAP[session.status] || '上机中' }}</el-tag>
            </div>
            <div class="session-info">
              <span>会员：{{ session.memberName || '散客' }}</span>
              <span>机位：{{ session.computerNo }}</span>
            </div>
            <div class="session-info">
              <span>上机时间：{{ formatDate(session.startTime, 'HH:mm') }}</span>
              <span>已计费：{{ session.billedMinutes }}分钟</span>
            </div>
          </div>
          <el-empty v-if="!sessionLoading && sessions.length === 0" description="暂无活跃会话" :image-size="60" />
        </div>
      </div>

      <!-- 右侧：选择目标机位 -->
      <div class="transfer-card">
        <h3 class="card-title">选择目标机位</h3>
        <div v-loading="seatLoading" class="target-seats">
          <div v-for="area in seatAreas" :key="area.id" class="area-group">
            <div class="area-label" :style="{ borderLeftColor: area.areaColor || '#409eff' }">{{ area.areaName }}</div>
            <div class="computer-options">
              <div
                v-for="computer in area.computers?.filter(c => c.isActive === 1)"
                :key="computer.id"
                class="computer-option"
                :class="{
                  'is-selected': targetComputerId === computer.id,
                  'is-disabled': computer.status !== 0 || computer.id === selectedSession?.computerId,
                  'is-current': computer.id === selectedSession?.computerId,
                }"
                :style="{ '--opt-color': COMPUTER_STATUS_COLOR[computer.status] || '#909399' }"
                @click="computer.status === 0 && computer.id !== selectedSession?.computerId ? selectTarget(computer) : undefined"
              >
                <div class="opt-no">{{ computer.computerNo }}</div>
                <div class="opt-type">{{ DEVICE_TYPE_MAP[computer.deviceType] || 'PC' }}</div>
                <div class="opt-status" :style="{ color: COMPUTER_STATUS_COLOR[computer.status] }">
                  {{ computer.id === selectedSession?.computerId ? '当前' : COMPUTER_STATUS_MAP[computer.status] }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 确认栏 -->
    <div class="confirm-bar">
      <div class="confirm-info">
        <span v-if="selectedSession">当前：{{ selectedSession.computerNo }}（{{ selectedSession.memberName || '散客' }}）</span>
        <span v-if="targetComputer">→ 目标：{{ targetComputer.computerNo }}（{{ targetComputer.areaName }}）</span>
      </div>
      <el-button type="primary" size="large" :loading="submitting" :disabled="!selectedSession || !targetComputerId" @click="handleTransfer">确认换机</el-button>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.page-desc { font-size: 14px; color: #64748b; margin-top: 4px; }

.transfer-layout { display: grid; grid-template-columns: 1fr 1.5fr; gap: 16px; margin-bottom: 16px; }
.transfer-card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.card-title { font-size: 15px; font-weight: 600; color: #1e293b; margin: 0 0 16px; }

.session-list { max-height: 400px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; }
.session-item { border: 2px solid #e2e8f0; border-radius: 8px; padding: 12px; cursor: pointer; transition: all 0.15s; }
.session-item:hover { border-color: #409eff; background: #f8fafc; }
.session-item.is-selected { border-color: #409eff; background: #ecf5ff; }
.session-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.session-no { font-size: 13px; font-weight: 600; color: #1e293b; }
.session-info { display: flex; justify-content: space-between; font-size: 12px; color: #64748b; }

.target-seats { max-height: 400px; overflow-y: auto; }
.area-group { margin-bottom: 16px; }
.area-label { font-size: 13px; font-weight: 600; color: #475569; padding: 4px 10px; border-left: 3px solid #409eff; margin-bottom: 8px; background: #f8fafc; border-radius: 0 4px 4px 0; }
.computer-options { display: grid; grid-template-columns: repeat(auto-fill, minmax(100px, 1fr)); gap: 8px; }
.computer-option { border: 2px solid #e2e8f0; border-radius: 8px; padding: 10px 8px; text-align: center; cursor: pointer; transition: all 0.15s; }
.computer-option:hover:not(.is-disabled) { border-color: var(--opt-color); background: #f8fafc; }
.computer-option.is-selected { border-color: #409eff; background: #ecf5ff; box-shadow: 0 0 0 1px #409eff; }
.computer-option.is-disabled { opacity: 0.5; cursor: not-allowed; }
.computer-option.is-current { border-color: #e6a23c; background: #fdf6ec; }
.opt-no { font-size: 13px; font-weight: 600; color: #1e293b; }
.opt-type { font-size: 11px; color: #94a3b8; margin: 2px 0; }
.opt-status { font-size: 11px; font-weight: 500; }

.confirm-bar { background: white; border-radius: 12px; padding: 16px 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); display: flex; justify-content: space-between; align-items: center; }
.confirm-info { display: flex; gap: 16px; font-size: 14px; color: #475569; }
</style>
