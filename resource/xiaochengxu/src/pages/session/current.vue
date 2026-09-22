<script setup lang="ts">
/**
 * 当前上机。
 *
 * 计时本地每秒走字，费用按「服务器最近一次返回的每分钟单价」平滑外推，
 * 同时每 30 秒向后端拉一次真实数据兜底，避免显示与结算不一致。
 */
import { computed, onUnmounted, ref } from 'vue'
import { onLoad, onShow, onHide } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { sessionApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { navBack, navTo, reLaunch, goLogin } from '@/utils/nav'
import { toast, confirm, toastSuccess } from '@/utils/ui'
import { formatMoney, formatDateTime, parseTime } from '@/utils/format'
import type { CurrentSession } from '@/types/session'

const app = useAppStore()
const user = useUserStore()

const session = ref<CurrentSession | null>(null)
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const acting = ref(false)
/** 每秒刷新的时间戳，驱动计时与费用走字 */
const tick = ref(Date.now())

let tickTimer: ReturnType<typeof setInterval> | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

/** 已上机秒数（本地推算，比服务器返回的更实时） */
const elapsedSeconds = computed(() => {
  const s = session.value
  if (!s) return 0
  // 后端时间为 ISO 字符串（"2026-09-22T17:04:24"），必须用公共 parseTime 解析，
  // 不能手写 replace(/-/g,'/')，否则会得到 "2026/09/22T..." → Invalid Date → NaN
  const start = parseTime(s.startTime).getTime()
  if (Number.isNaN(start)) return 0
  return Math.max(0, Math.floor((tick.value - start) / 1000))
})

const elapsedText = computed(() => {
  const t = elapsedSeconds.value
  const h = Math.floor(t / 3600)
  const m = Math.floor((t % 3600) / 60)
  const s = t % 60
  return [h, m, s].map((v) => String(v).padStart(2, '0')).join(':')
})

/**
 * 当前应付费用。
 * 规则以后端为准（TariffCalculator）：不足 1 小时按首小时价，超出后每满 1 小时
 * 加一次续费小时价（不满 1 小时也按 1 小时）。页面每秒走字时按同一阶梯本地估算，
 * 每 30 秒轮询再用后端 paidAmount 校正，取两者较大值，避免显示回跳。
 */
const currentAmount = computed(() => {
  const s = session.value
  if (!s) return 0
  // 不足 1 分钟按 1 分钟起计
  const chargedMinutes = Math.max(Math.ceil(elapsedSeconds.value / 60), 1)
  const firstHourPrice = s.firstHourPrice ?? s.paidAmount ?? 0
  let local = firstHourPrice
  if (chargedMinutes > 60 && s.hourlyRate > 0) {
    const extraHours = Math.ceil((chargedMinutes - 60) / 60)
    local = firstHourPrice + extraHours * s.hourlyRate
  }
  // 服务器值兜底（包时方案/费率配置变更等场景），且不允许低于已结算金额
  return Math.max(s.paidAmount ?? 0, Math.round(local * 100) / 100)
})

/** 余额可支撑剩余分钟：以后端按「扣费后余额 + 续费小时价」的计算为准，-1 为不限时 */
const remainingMinutes = computed(() => session.value?.remainingMinutes ?? -1)

/** 账户实时余额（轮询后端拿到的未扣费余额） */
const accountBalance = computed(() => session.value?.balance ?? user.balance)

/** 此刻下机的扣费后预计余额 */
const balanceAfter = computed(() => {
  const s = session.value
  if (!s) return user.balance
  return s.balanceAfter ?? Math.max(0, accountBalance.value - currentAmount.value)
})

/** 计费标准文案：按时方案显示首小时价 + 续费价；包时方案（hourlyRate=0）显示包时价 */
const tariffText = computed(() => {
  const s = session.value
  if (!s) return ''
  const first = formatMoney(s.firstHourPrice ?? 0)
  if (!s.hourlyRate || s.hourlyRate <= 0) return `包时 ¥${first}`
  return `首小时 ¥${first} · 续费 ¥${formatMoney(s.hourlyRate)}/小时`
})

const remainingText = computed(() => {
  const m = remainingMinutes.value
  if (m < 0) return '不限时'
  if (m <= 0) return '余额已用尽'
  if (m < 60) return `约 ${m} 分钟`
  const h = Math.floor(m / 60)
  const rest = m % 60
  return rest ? `约 ${h} 小时 ${rest} 分` : `约 ${h} 小时`
})

const isPaused = computed(() => session.value?.status === 1)

/* ==================== 数据 ==================== */

async function load(showLoading = true): Promise<void> {
  if (showLoading) state.value = 'loading'
  try {
    const res = await sessionApi.getCurrentSession()
    session.value = res
    app.setCurrentSession(res)
    // 以后端真实余额同步全局 store（上机期间余额只在结算时才落库扣减，
    // 此处同步保证与「我的」页 /auth/info 看到的数字一致）
    if (res?.balance != null) user.patchBalance(res.balance)
    state.value = res ? 'success' : 'empty'
  } catch {
    if (!session.value) state.value = 'error'
  }
}

function startTimers(): void {
  stopTimers()
  tickTimer = setInterval(() => {
    tick.value = Date.now()
  }, 1000)
  pollTimer = setInterval(() => {
    void load(false)
  }, 30000)
}

function stopTimers(): void {
  if (tickTimer) clearInterval(tickTimer)
  if (pollTimer) clearInterval(pollTimer)
  tickTimer = null
  pollTimer = null
}

onLoad(() => {
  if (!user.isLogin) {
    goLogin('/pages/session/current')
    return
  }
  void load().then(() => {
    if (state.value === 'success') startTimers()
  })
})

onShow(() => {
  if (user.isLogin && state.value === 'success') startTimers()
})

onHide(() => {
  stopTimers()
})

onUnmounted(() => {
  stopTimers()
})

/* ==================== 交互 ==================== */

async function onPause(): Promise<void> {
  if (!session.value || acting.value) return
  const pause = !isPaused.value
  const ok = await confirm(
    pause ? '临时下机后计时暂停，机位为你保留。' : '恢复上机，计时继续。',
    pause ? '临时下机' : '恢复上机',
    '确定'
  )
  if (!ok) return
  acting.value = true
  try {
    if (pause) await sessionApi.pauseSession(session.value.sessionId)
    else await sessionApi.resumeSession(session.value.sessionId)
    toast(pause ? '已临时下机' : '已恢复上机')
    void load(false)
  } catch {
    /* request 层已提示 */
  } finally {
    acting.value = false
  }
}

async function onEnd(): Promise<void> {
  if (!session.value || acting.value) return
  const amount = currentAmount.value
  const ok = await confirm(
    `本次上机 ${elapsedText.value}，预计消费 ¥${formatMoney(amount)}，将从余额扣除。`,
    '确认下机',
    '确认下机'
  )
  if (!ok) return

  acting.value = true
  uni.showLoading({ title: '结算中...', mask: true })
  try {
    const res = await sessionApi.selfEnd(session.value.sessionId)
    user.patchBalance(res.balanceAfter)
    app.setCurrentSession(null)
    session.value = null
    uni.hideLoading()
    stopTimers()
    toastSuccess(`已下机，本次消费 ¥${formatMoney(res.paidAmount)}`)
    setTimeout(() => reLaunch('/pages/index/index'), 1200)
  } catch (e) {
    uni.hideLoading()
    const msg = (e as Error)?.message
    if (msg) toast(msg)
  } finally {
    acting.value = false
  }
}

function goScan(): void {
  navTo('/pages/session/scan')
}

function goRecharge(): void {
  navTo('/pages/recharge/index')
}

function goOrder(): void {
  navTo('/pages/product/index')
}
</script>

<template>
  <view class="cur page-root">
    <AppNavBar title="当前上机" show-back @back="navBack" />

    <StateView
      v-if="state === 'empty'"
      state="empty"
      empty-text="当前没有进行中的上机"
      empty-desc="选定空闲机位即可一键开机"
      empty-icon="empty-box"
    >
      <template #action>
        <AppButton type="primary" size="md" @tap="goScan">去上机</AppButton>
      </template>
    </StateView>

    <StateView v-else-if="state === 'error'" state="error" @retry="load(true)" />

    <StateView v-else-if="state === 'loading'" state="loading" />

    <template v-else-if="session">
      <!-- ==================== 计时卡 ==================== -->
      <view class="hero">
        <view class="hero__badge" :class="{ 'is-paused': isPaused }">
          <view class="hero__dot" />
          <text class="hero__badge-text">{{ isPaused ? '已临时下机' : '上机中' }}</text>
        </view>

        <text class="hero__time">{{ elapsedText }}</text>
        <text class="hero__time-label">已上机时长</text>

        <view class="hero__amount">
          <text class="hero__amount-label">当前消费</text>
          <text class="hero__amount-value">¥{{ formatMoney(currentAmount) }}</text>
        </view>
      </view>

      <!-- ==================== 机位信息 ==================== -->
      <view class="card sec">
        <view class="row">
          <text class="row__label">机位</text>
          <text class="row__value">{{ session.computerName }}</text>
        </view>
        <view class="row">
          <text class="row__label">区域</text>
          <text class="row__value">{{ session.areaName }}</text>
        </view>
        <view v-if="session.computerSpec" class="row">
          <text class="row__label">配置</text>
          <text class="row__value row__value--ellipsis">{{ session.computerSpec }}</text>
        </view>
        <view class="row">
          <text class="row__label">门店</text>
          <text class="row__value row__value--ellipsis">{{ session.storeName || app.store.name }}</text>
        </view>
        <view class="row">
          <text class="row__label">开机时间</text>
          <text class="row__value">{{ formatDateTime(session.startTime, true) }}</text>
        </view>
        <view class="row">
          <text class="row__label">会话编号</text>
          <text class="row__value row__value--sm">{{ session.sessionNo }}</text>
        </view>
      </view>

      <!-- ==================== 费用 ==================== -->
      <view class="card sec">
        <view class="row">
          <text class="row__label">计费标准</text>
          <text class="row__value">{{ tariffText }}</text>
        </view>
        <view class="row">
          <text class="row__label">账户余额</text>
          <text class="row__value">¥{{ formatMoney(accountBalance) }}</text>
        </view>
        <view class="row">
          <text class="row__label">下机后余额</text>
          <text class="row__value row__value--money">¥{{ formatMoney(balanceAfter) }}</text>
        </view>
        <view class="row">
          <text class="row__label">余额可用</text>
          <text class="row__value" :class="{ 'row__value--warn': remainingMinutes >= 0 && remainingMinutes <= 30 }">
            {{ remainingText }}
          </text>
        </view>
        <view v-if="session.discountAmount > 0" class="row">
          <text class="row__label">会员优惠</text>
          <text class="row__value row__value--cut">-¥{{ formatMoney(session.discountAmount) }}</text>
        </view>
      </view>

      <view v-if="remainingMinutes >= 0 && remainingMinutes <= 30" class="card warn">
        <AppIcon name="warning" :size="32" color="#FF9F2E" :stroke-width="1.7" />
        <text class="warn__text">余额即将用尽，请及时充值以免自动下机</text>
        <text class="warn__action" @tap="goRecharge">去充值</text>
      </view>

      <!-- ==================== 快捷操作 ==================== -->
      <view class="card sec">
        <view class="quick">
          <view class="quick__item" @tap="goOrder">
            <AppIcon name="svc-food" :size="52" color="#FF9F43" :stroke-width="1.6" />
            <text class="quick__text">点单</text>
          </view>
          <view class="quick__item" @tap="goRecharge">
            <AppIcon name="svc-wallet" :size="52" color="#3AC6C6" :stroke-width="1.6" />
            <text class="quick__text">充值</text>
          </view>
          <view class="quick__item" @tap="onPause">
            <AppIcon
              :name="isPaused ? 'svc-power' : 'svc-remote'"
              :size="52"
              color="#4A9BFF"
              :stroke-width="1.6"
            />
            <text class="quick__text">{{ isPaused ? '恢复上机' : '临时下机' }}</text>
          </view>
          <view class="quick__item" @tap="navTo('/pages/feedback/index')">
            <AppIcon name="svc-chat" :size="52" color="#67C23A" :stroke-width="1.6" />
            <text class="quick__text">呼叫网管</text>
          </view>
        </view>
      </view>

      <!-- ==================== 下机 ==================== -->
      <view class="cur__actions">
        <AppButton
          type="ghost"
          size="lg"
          class="cur__action"
          :loading="acting"
          @tap="onPause"
        >
          {{ isPaused ? '恢复上机' : '临时下机' }}
        </AppButton>
        <AppButton
          type="primary"
          size="lg"
          class="cur__action"
          :loading="acting"
          @tap="onEnd"
        >
          自助下机
        </AppButton>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.cur {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

/* ==================== 计时卡 ==================== */
.hero {
  margin: 20rpx $gap-page 0;
  padding: 44rpx 32rpx 36rpx;
  border-radius: $radius-lg;
  background: $brand-gradient;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 12rpx 30rpx rgba(91, 91, 214, 0.28);
}

.hero__badge {
  display: flex;
  align-items: center;
  padding: 6rpx 22rpx;
  border-radius: $radius-pill;
  background: rgba(255, 255, 255, 0.22);

  &.is-paused {
    background: rgba(255, 159, 46, 0.32);
  }
}

.hero__dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #7dffb0;
  box-shadow: 0 0 0 6rpx rgba(125, 255, 176, 0.25);
}

.hero__badge-text {
  margin-left: 12rpx;
  font-size: $fs-base;
  color: #ffffff;
  font-weight: 600;
}

.hero__time {
  margin-top: 30rpx;
  font-size: 96rpx;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 4rpx;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.hero__time-label {
  margin-top: 8rpx;
  font-size: $fs-base;
  color: rgba(255, 255, 255, 0.78);
}

.hero__amount {
  margin-top: 32rpx;
  padding-top: 26rpx;
  width: 100%;
  border-top: 1rpx solid rgba(255, 255, 255, 0.22);
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.hero__amount-label {
  font-size: $fs-md;
  color: rgba(255, 255, 255, 0.85);
}

.hero__amount-value {
  font-size: 52rpx;
  font-weight: 800;
  color: #ffe9a8;
}

/* ==================== 信息行 ==================== */
.sec {
  margin: 20rpx $gap-page 0;
  padding: 20rpx 24rpx;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 72rpx;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.row__label {
  font-size: $fs-md;
  color: $text-secondary;
  flex-shrink: 0;
}

.row__value {
  font-size: $fs-md;
  color: $text-primary;
  font-weight: 500;
  max-width: 440rpx;
  text-align: right;
  @include ellipsis;
}

.row__value--ellipsis {
  max-width: 400rpx;
}

.row__value--sm {
  font-size: $fs-base;
  color: $text-placeholder;
}

.row__value--cut {
  color: $danger;
}

.row__value--money {
  color: $danger;
  font-weight: 700;
}

.row__value--warn {
  color: $warning;
  font-weight: 700;
}

/* ==================== 警告 ==================== */
.warn {
  margin: 20rpx $gap-page 0;
  padding: 22rpx 24rpx;
  display: flex;
  align-items: center;
  background: #fffaf0;
}

.warn__text {
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
  font-size: $fs-base;
  color: #a8681a;
}

.warn__action {
  flex-shrink: 0;
  margin-left: 16rpx;
  font-size: $fs-base;
  font-weight: 600;
  color: #e8465e;

  &:active {
    opacity: 0.6;
  }
}

/* ==================== 快捷操作 ==================== */
.quick {
  display: flex;
  align-items: center;
  padding: 12rpx 0;
}

.quick__item {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  &:active {
    opacity: 0.65;
  }
}

.quick__text {
  margin-top: 12rpx;
  font-size: $fs-base;
  color: $text-regular;
}

/* ==================== 下机 ==================== */
.cur__actions {
  display: flex;
  align-items: center;
  padding: 40rpx $gap-page 0;
}

.cur__action {
  flex: 1;
  min-width: 0;
  margin-right: 24rpx;

  &:last-child {
    margin-right: 0;
  }
}
</style>
