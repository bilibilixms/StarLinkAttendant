<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Search, User, Monitor } from '@element-plus/icons-vue'
import { startSession } from '../api'
import { useSessionStore } from '../stores'
import { DEVICE_TYPE_MAP, COMPUTER_STATUS_MAP, COMPUTER_STATUS_COLOR, AUTH_METHOD_MAP } from '@/common/constants'
import type { SeatAreaItem, ComputerItem } from '../types'

const router = useRouter()
const route = useRoute()
const sessionStore = useSessionStore()

/** 表单数据 */
const form = reactive({
  memberId: null as number | null,
  memberPhone: '',
  computerId: null as number | null,
  authMethod: 1,
  tariffPlanId: null as number | null,
  expectedMinutes: null as number | null,
})

/** 会员搜索结果 */
const memberSearchLoading = ref(false)
const memberFound = ref(false)
const memberInfo = ref<{ id: number; realName: string; phone: string; balance: number; levelName: string; status: number } | null>(null)

/** 座位图 */
const seatAreas = ref<SeatAreaItem[]>([])
const seatLoading = ref(false)

/** 提交中 */
const submitting = ref(false)

/** 预选机位 ID（从座位图跳转过来） */
const preselectedComputerId = computed(() => {
  const id = route.query.computerId
  return id ? Number(id) : null
})

/** 获取机位实时状态（优先使用轮询数据） */
function getComputerStatus(computer: ComputerItem) {
  return sessionStore.seatStatusMap[computer.id]?.status ?? computer.status
}

/** 可用机位列表（基于实时状态判断空闲） */
const availableComputers = computed(() => {
  return seatAreas.value.flatMap(a =>
    a.computers
      .filter(c => c.isActive === 1 && getComputerStatus(c) === 0)
      .map(c => ({ ...c, areaName: a.areaName, areaColor: a.areaColor }))
  )
})

/** 选中的机位信息 */
const selectedComputer = computed(() => {
  if (!form.computerId) return null
  return availableComputers.value.find(c => c.id === form.computerId) || null
})

/** 搜索会员（通过手机号） */
async function searchMember() {
  if (!form.memberPhone || form.memberPhone.length < 11) {
    ElMessage.warning('请输入完整的手机号')
    return
  }
  memberSearchLoading.value = true
  try {
    const { getMemberList } = await import('@/modules/member/api')
    const res = await getMemberList({ page: 1, size: 1, phone: form.memberPhone })
    const records = res.data.records || []
    const m = records[0]
    if (m) {
      memberInfo.value = { id: m.id, realName: m.realName, phone: m.phone, balance: m.balance, levelName: m.levelName, status: m.status }
      form.memberId = m.id
      memberFound.value = true
      if (m.status !== 1) {
        ElMessage.warning('该会员状态异常，无法上机')
      }
    } else {
      memberFound.value = false
      memberInfo.value = null
      form.memberId = null
      ElMessage.warning('未找到该手机号对应的会员')
    }
  } catch {
    memberFound.value = false
    memberInfo.value = null
    form.memberId = null
  } finally {
    memberSearchLoading.value = false
  }
}

/** 选择机位 */
function selectComputer(computer: ComputerItem & { areaName: string }) {
  form.computerId = computer.id
}

/** 加载座位图 */
async function loadSeats() {
  seatLoading.value = true
  try {
    // 同时加载座位图配置和实时状态
    await Promise.all([
      sessionStore.loadSeatMap(),
      sessionStore.loadSeatStatus(),
    ])
    seatAreas.value = sessionStore.seatAreas
  } catch { seatAreas.value = [] }
  finally { seatLoading.value = false }
}

/** 提交上机 */
async function handleSubmit() {
  if (!form.memberId) { ElMessage.warning('请先搜索并选择会员'); return }
  if (!form.computerId) { ElMessage.warning('请选择机位'); return }
  if (memberInfo.value && memberInfo.value.status !== 1) { ElMessage.warning('该会员状态异常，无法上机'); return }

  submitting.value = true
  try {
    await startSession({
      memberId: form.memberId,
      computerId: form.computerId,
      authMethod: form.authMethod,
      tariffPlanId: form.tariffPlanId || undefined,
      expectedMinutes: form.expectedMinutes || undefined,
    })
    ElMessage.success('上机成功')
    router.push('/session/monitor')
  } catch { /* error handled by interceptor */ }
  finally { submitting.value = false }
}

onMounted(() => {
  loadSeats()
  sessionStore.startPolling()
  // 如果从座位图跳转过来，预选机位
  if (preselectedComputerId.value) {
    form.computerId = preselectedComputerId.value
  }
})

onUnmounted(() => {
  sessionStore.stopPolling()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button :icon="ArrowLeft" text @click="router.back()">返回</el-button>
      <div>
        <h1 class="page-title">上机操作</h1>
        <p class="page-desc">选择会员和机位，确认上机</p>
      </div>
    </div>

    <div class="form-layout">
      <!-- 左侧：会员选择 -->
      <div class="form-card">
        <h3 class="card-title">
          <el-icon><User /></el-icon> 选择会员
        </h3>
        <el-form label-position="top">
          <el-form-item label="会员手机号">
            <div class="member-search-row">
              <el-input v-model="form.memberPhone" placeholder="请输入会员手机号" clearable maxlength="11" @keyup.enter="searchMember" />
              <el-button type="primary" :icon="Search" :loading="memberSearchLoading" @click="searchMember">搜索</el-button>
            </div>
          </el-form-item>
        </el-form>

        <div v-if="memberFound && memberInfo" class="member-card">
          <div class="member-info-row">
            <span class="member-name">{{ memberInfo.realName || '未设置姓名' }}</span>
            <el-tag size="small" type="warning">{{ memberInfo.levelName || '普通' }}</el-tag>
            <el-tag size="small" :type="memberInfo.status === 1 ? 'success' : 'danger'">{{ memberInfo.status === 1 ? '正常' : '异常' }}</el-tag>
          </div>
          <div class="member-detail-row">
            <span>手机号：{{ memberInfo.phone }}</span>
            <span>余额：¥{{ memberInfo.balance?.toFixed(2) || '0.00' }}</span>
          </div>
        </div>
        <div v-else-if="!memberFound && form.memberPhone" class="no-member">
          <p>未找到会员，请先<a @click="router.push('/member/register')" style="color: #409eff; cursor: pointer;">注册会员</a></p>
        </div>
      </div>

      <!-- 右侧：机位选择 -->
      <div class="form-card">
        <h3 class="card-title">
          <el-icon><Monitor /></el-icon> 选择机位
        </h3>
        <div v-loading="seatLoading" class="seat-select-area">
          <div v-for="area in seatAreas" :key="area.id" class="area-group">
            <div class="area-label" :style="{ borderLeftColor: area.areaColor || '#409eff' }">{{ area.areaName }}</div>
            <div class="computer-options">
              <div
                v-for="computer in area.computers?.filter(c => c.isActive === 1)"
                :key="computer.id"
                class="computer-option"
                :class="{
                  'is-selected': form.computerId === computer.id,
                  'is-disabled': getComputerStatus(computer) !== 0,
                }"
                :style="{ '--opt-color': COMPUTER_STATUS_COLOR[getComputerStatus(computer)] || '#909399' }"
                @click="getComputerStatus(computer) === 0 ? selectComputer(computer) : undefined"
              >
                <div class="opt-no">{{ computer.computerNo }}</div>
                <div class="opt-type">{{ DEVICE_TYPE_MAP[computer.deviceType] || 'PC' }}</div>
                <div class="opt-status" :style="{ color: COMPUTER_STATUS_COLOR[getComputerStatus(computer)] }">
                  {{ COMPUTER_STATUS_MAP[getComputerStatus(computer)] || '未知' }}
                </div>
              </div>
            </div>
          </div>
          <el-empty v-if="seatAreas.length > 0 && availableComputers.length === 0" description="暂无可用机位" :image-size="60" />
        </div>
      </div>
    </div>

    <!-- 操作选项 -->
    <div class="form-card options-card">
      <h3 class="card-title">上机设置</h3>
      <el-form :inline="true" label-position="top">
        <el-form-item label="认证方式">
          <el-select v-model="form.authMethod" style="width: 150px">
            <el-option v-for="(label, val) in AUTH_METHOD_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计时长（分钟，可选）">
          <el-input-number v-model="form.expectedMinutes" :min="0" :step="30" placeholder="不限" style="width: 160px" />
        </el-form-item>
      </el-form>
    </div>

    <!-- 确认信息 & 提交 -->
    <div class="confirm-bar">
      <div class="confirm-info">
        <span v-if="memberInfo">会员：{{ memberInfo.realName || memberInfo.phone }}</span>
        <span v-if="selectedComputer">机位：{{ selectedComputer.computerNo }}（{{ selectedComputer.areaName }}）</span>
      </div>
      <el-button type="primary" size="large" :loading="submitting" :disabled="!form.memberId || !form.computerId" @click="handleSubmit">确认上机</el-button>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.page-desc { font-size: 14px; color: #64748b; margin-top: 4px; }

.form-layout { display: grid; grid-template-columns: 1fr 1.5fr; gap: 16px; margin-bottom: 16px; }
.form-card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.card-title { font-size: 15px; font-weight: 600; color: #1e293b; margin: 0 0 16px; display: flex; align-items: center; gap: 6px; }

.member-search-row { display: flex; gap: 8px; width: 100%; }
.member-card { background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 8px; padding: 12px; margin-top: 8px; }
.member-info-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.member-name { font-size: 15px; font-weight: 600; color: #1e293b; }
.member-detail-row { display: flex; gap: 16px; font-size: 13px; color: #64748b; }
.no-member { text-align: center; padding: 16px; color: #94a3b8; font-size: 13px; }

.seat-select-area { max-height: 400px; overflow-y: auto; }
.area-group { margin-bottom: 16px; }
.area-label { font-size: 13px; font-weight: 600; color: #475569; padding: 4px 10px; border-left: 3px solid #409eff; margin-bottom: 8px; background: #f8fafc; border-radius: 0 4px 4px 0; }
.computer-options { display: grid; grid-template-columns: repeat(auto-fill, minmax(100px, 1fr)); gap: 8px; }
.computer-option { border: 2px solid #e2e8f0; border-radius: 8px; padding: 10px 8px; text-align: center; cursor: pointer; transition: all 0.15s; }
.computer-option:hover:not(.is-disabled) { border-color: var(--opt-color); background: #f8fafc; }
.computer-option.is-selected { border-color: #409eff; background: #ecf5ff; box-shadow: 0 0 0 1px #409eff; }
.computer-option.is-disabled { opacity: 0.5; cursor: not-allowed; }
.opt-no { font-size: 13px; font-weight: 600; color: #1e293b; }
.opt-type { font-size: 11px; color: #94a3b8; margin: 2px 0; }
.opt-status { font-size: 11px; font-weight: 500; }

.options-card { margin-bottom: 16px; }

.confirm-bar { background: white; border-radius: 12px; padding: 16px 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); display: flex; justify-content: space-between; align-items: center; }
.confirm-info { display: flex; gap: 16px; font-size: 14px; color: #475569; }
</style>
