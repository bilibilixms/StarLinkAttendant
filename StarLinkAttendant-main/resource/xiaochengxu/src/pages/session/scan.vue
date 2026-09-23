<script setup lang="ts">
/**
 * 扫码上机。
 *
 * 二维码内容支持两种格式：
 * 1. JSON：{"storeId":1,"areaId":2,"computerId":2003,"computerName":"高级区-A03"}
 * 2. 简易串："1-2-2003"（storeId-areaId-computerId）
 *
 * H5 环境没有 uni.scanCode，提供「模拟扫码」入口，保证 demo 在浏览器里也能跑通。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import { sessionApi, storeApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { navBack, reLaunch, goLogin } from '@/utils/nav'
import { toast } from '@/utils/ui'
import { formatMoney } from '@/utils/format'
import type { Computer, SeatArea, SeatMap } from '@/types/session'

const app = useAppStore()
const user = useUserStore()

const qrContent = ref('')
const state = ref<'idle' | 'ready' | 'starting' | 'error'>('idle')
const errMsg = ref('')

/** 从二维码解析出的机位信息 */
const parsed = ref<{ storeId: number; areaId: number; computerId: number; computerName: string } | null>(null)

/** 手动选座（扫码不可用时的兜底） */
const areas = ref<SeatArea[]>([])
const seatMap = ref<SeatMap | null>(null)
const activeAreaId = ref(0)
const picking = ref(false)

const area = computed(() => areas.value.find((a) => a.id === (parsed.value?.areaId ?? activeAreaId.value)))
const hourlyRate = computed(() => area.value?.hourlyRate ?? 0)
const canAfford = computed(() => user.balance > 0)

const seatLabel = computed(() => parsed.value?.computerName || (parsed.value ? `机位 #${parsed.value.computerId}` : ''))

onLoad((query) => {
  if (!user.isLogin) {
    goLogin('/pages/session/scan')
    return
  }
  const raw = query?.qr ? decodeURIComponent(String(query.qr)) : ''
  if (raw) {
    handleQr(raw)
  } else {
    state.value = 'idle'
    void loadAreas()
  }
})

/** 解析二维码内容 */
function handleQr(raw: string): void {
  qrContent.value = raw
  let storeId = app.store.id
  let areaId = 1
  let computerId = 0
  let computerName = ''

  try {
    const obj = JSON.parse(raw) as Record<string, unknown>
    storeId = Number(obj.storeId ?? storeId)
    areaId = Number(obj.areaId ?? areaId)
    computerId = Number(obj.computerId ?? 0)
    computerName = String(obj.computerName ?? '')
  } catch {
    const parts = raw.split(/[-_:]/).filter(Boolean)
    const nums = parts.map((p) => Number(p)).filter((n) => !Number.isNaN(n))
    if (nums.length >= 3) {
      storeId = nums[0]
      areaId = nums[1]
      computerId = nums[2]
    } else if (nums.length === 1) {
      computerId = nums[0]
    }
    computerName = raw.length <= 12 ? raw : ''
  }

  if (!computerId) {
    state.value = 'error'
    errMsg.value = '二维码内容无法识别，请确认扫描的是机位二维码'
    void loadAreas()
    return
  }

  parsed.value = { storeId, areaId, computerId, computerName }
  state.value = 'ready'
  void loadAreas()
}

async function loadAreas(): Promise<void> {
  try {
    areas.value = await storeApi.getSeatAreas(app.store.id)
    if (parsed.value?.areaId) {
      activeAreaId.value = parsed.value.areaId
    } else if (areas.value.length) {
      activeAreaId.value = areas.value[0].id
    }
    await loadSeats()
  } catch {
    areas.value = []
  }
}

async function loadSeats(): Promise<void> {
  if (!activeAreaId.value) return
  try {
    seatMap.value = await storeApi.getSeatMap(app.store.id, activeAreaId.value)
  } catch {
    seatMap.value = null
  }
}

function switchArea(id: number): void {
  activeAreaId.value = id
  if (!parsed.value) void loadSeats()
}

/** 手动机位选择 */
function pickSeat(seat: Computer): void {
  if (seat.status !== 0) {
    const label = { 1: '使用中', 2: '已预约', 3: '维修中', 4: '离线' }[seat.status] ?? '不可用'
    toast(`该机位${label}`)
    return
  }
  parsed.value = {
    storeId: app.store.id,
    areaId: seat.areaId,
    computerId: seat.id,
    computerName: seat.computerName,
  }
  qrContent.value = JSON.stringify(parsed.value)
  state.value = 'ready'
}

/** H5 / 开发者工具下的模拟扫码 */
function mockScan(): void {
  picking.value = true
  if (!areas.value.length) void loadAreas()
}

/** 从手动选座返回扫码页 */
function cancelPicking(): void {
  picking.value = false
}

function rescan(): void {
  uni.scanCode({
    scanType: ['qrCode'],
    success: (res) => handleQr(res.result || ''),
    fail: (err) => {
      if (!/cancel/i.test(String(err?.errMsg))) toast('扫码失败，请重试')
    },
  })
}

/* ==================== 上机 ==================== */

async function onStart(): Promise<void> {
  if (!parsed.value || state.value === 'starting') return
  if (!canAfford.value) {
    toast('账户余额不足，请先充值')
    return
  }
  state.value = 'starting'
  uni.showLoading({ title: '开台中...', mask: true })
  try {
    const res = await sessionApi.scanStart({
      qrContent: qrContent.value,
      computerId: parsed.value.computerId,
      storeId: parsed.value.storeId,
      // 区域必须带上：它决定计费费率，漏传会按普通区价格结算
      areaId: parsed.value.areaId,
      computerName: parsed.value.computerName,
    })
    app.setCurrentSession(res.session)
    uni.hideLoading()
    toast(res.resumed ? '已恢复上机会话' : '开机成功，祝您游戏愉快')
    setTimeout(() => reLaunch('/pages/session/current'), 700)
  } catch (e) {
    uni.hideLoading()
    state.value = 'ready'
    const msg = (e as Error)?.message
    if (msg) toast(msg)
  }
}
</script>

<template>
  <view class="scan page-root">
    <AppNavBar title="扫码上机" show-back @back="navBack" />

    <!-- ==================== 待扫码 ====================
         注意：手动选座（picking）时必须让位给下面的选座面板，
         否则 state 仍是 idle，选座界面永远渲染不出来。 -->
    <view v-if="state === 'idle' && !picking" class="scan__hero">
      <view class="scan__frame">
        <AppIcon name="scan" :size="140" color="#B0B0BE" :stroke-width="1.4" />
      </view>
      <text class="scan__hero-title">扫描机位二维码开机</text>
      <text class="scan__hero-desc">机位屏幕或桌贴上有一张二维码，扫码即可一键开机</text>

      <AppButton type="primary" size="lg" class="scan__hero-btn" @tap="rescan">
        扫描二维码
      </AppButton>
      <AppButton type="ghost" size="lg" class="scan__hero-btn" @tap="mockScan">
        手动选择机位
      </AppButton>
    </view>

    <!-- ==================== 手动选座 ==================== -->
    <template v-else-if="picking && !parsed">
      <view class="card pick">
        <view class="flex-between">
          <text class="pick__title">选择区域</text>
          <text class="pick__cancel" @tap="cancelPicking">返回扫码</text>
        </view>
        <view class="pick__areas">
          <view
            v-for="a in areas"
            :key="a.id"
            class="pick__area"
            :class="{ 'is-active': activeAreaId === a.id }"
            @tap="switchArea(a.id)"
          >
            <text class="pick__area-name">{{ a.areaName }}</text>
            <text class="pick__area-meta">{{ a.hourlyRate }}元/时 · 空闲{{ a.freeCount }}</text>
          </view>
        </view>

        <text class="pick__title pick__title--mt">选择机位</text>
        <view v-if="seatMap" class="seats">
          <view
            v-for="s in seatMap.seats"
            :key="s.id"
            class="seat"
            :class="`is-s${s.status}`"
            @tap="pickSeat(s)"
          >
            <text class="seat__text">{{ s.computerNo }}</text>
          </view>
        </view>

        <view class="legend">
          <view class="legend__item"><view class="legend__dot legend__dot--free" /><text class="legend__text">空闲</text></view>
          <view class="legend__item"><view class="legend__dot legend__dot--busy" /><text class="legend__text">使用中</text></view>
          <view class="legend__item"><view class="legend__dot legend__dot--booked" /><text class="legend__text">已预约</text></view>
          <view class="legend__item"><view class="legend__dot legend__dot--fix" /><text class="legend__text">维修中</text></view>
        </view>
      </view>
    </template>

    <!-- ==================== 确认上机 ==================== -->
    <template v-else-if="parsed">
      <view class="card seat-card">
        <view class="seat-card__head">
          <view class="seat-card__badge">
            <text class="seat-card__badge-text">{{ area?.areaName || '机位' }}</text>
          </view>
          <text class="seat-card__store">{{ app.store.shortName }}</text>
        </view>

        <text class="seat-card__seat">{{ seatLabel }}</text>
        <text v-if="area?.description" class="seat-card__spec">{{ area.description }}</text>

        <view class="seat-card__rows">
          <view class="seat-card__row">
            <text class="seat-card__label">计费标准</text>
            <text class="seat-card__value">{{ hourlyRate }} 元/小时</text>
          </view>
          <view class="seat-card__row">
            <text class="seat-card__label">账户余额</text>
            <text class="seat-card__value">¥{{ formatMoney(user.balance) }}</text>
          </view>
          <view class="seat-card__row">
            <text class="seat-card__label">预计可用</text>
            <text class="seat-card__value">
              {{ hourlyRate > 0 ? `约 ${Math.floor((user.balance / hourlyRate) * 60)} 分钟` : '--' }}
            </text>
          </view>
        </view>
      </view>

      <view class="card scan__notice">
        <AppIcon name="warning" :size="32" color="#FF9F2E" :stroke-width="1.7" />
        <text class="scan__notice-text">
          开机后立即开始计费，下机时从余额自动结算。如需更换机位请先下机。
        </text>
      </view>

      <view class="scan__actions">
        <AppButton type="ghost" size="lg" class="scan__action" @tap="rescan">重新扫码</AppButton>
        <AppButton
          type="primary"
          size="lg"
          class="scan__action"
          :loading="state === 'starting'"
          :disabled="!canAfford"
          @tap="onStart"
        >
          {{ canAfford ? '确认开机' : '余额不足' }}
        </AppButton>
      </view>
    </template>

    <!-- ==================== 错误 ==================== -->
    <view v-else-if="state === 'error'" class="scan__hero">
      <AppIcon name="empty-network" :size="130" color="#C6C9D6" />
      <text class="scan__hero-title">{{ errMsg }}</text>
      <AppButton type="primary" size="lg" class="scan__hero-btn" @tap="rescan">重新扫码</AppButton>
      <AppButton type="ghost" size="lg" class="scan__hero-btn" @tap="mockScan">
        手动选择机位
      </AppButton>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.scan {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

/* ==================== 待扫码 ==================== */
.scan__hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 100rpx 60rpx 0;
}

.scan__frame {
  width: 320rpx;
  height: 320rpx;
  border-radius: $radius-xl;
  border: 4rpx dashed #d3d6e2;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
}

.scan__hero-title {
  margin-top: 44rpx;
  font-size: 36rpx;
  font-weight: 700;
  color: $text-primary;
  text-align: center;
}

.scan__hero-desc {
  margin-top: 16rpx;
  font-size: $fs-md;
  color: $text-secondary;
  text-align: center;
  line-height: 1.6;
}

.scan__hero-btn {
  width: 100%;
  margin-top: 32rpx;
}

/* ==================== 手动选座 ==================== */
.pick {
  margin: 20rpx $gap-page 0;
  padding: 28rpx 24rpx;
}

.pick__title {
  display: block;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
}

.pick__cancel {
  font-size: $fs-base;
  color: $brand-primary;

  &:active {
    opacity: 0.6;
  }
}

.pick__title--mt {
  margin-top: 36rpx;
}

.pick__areas {
  display: flex;
  flex-wrap: wrap;
  margin-top: 20rpx;
}

.pick__area {
  width: 48.5%;
  margin-bottom: 18rpx;
  padding: 20rpx;
  border-radius: $radius-md;
  background: $card-bg-soft;
  border: 2rpx solid transparent;

  &.is-active {
    border-color: $brand-primary;
    background: $brand-primary-soft;
  }
}

.pick__area-name {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
}

.pick__area-meta {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-secondary;
}

/* ---------- 座位图 ---------- */
.seats {
  display: flex;
  flex-wrap: wrap;
  margin-top: 20rpx;
}

.seat {
  width: 88rpx;
  height: 76rpx;
  margin: 0 14rpx 14rpx 0;
  border-radius: $radius-sm;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    opacity: 0.7;
  }
}

.seat__text {
  font-size: $fs-sm;
  color: #ffffff;
  font-weight: 600;
}

.is-s0 {
  background: #22c55e;
}

.is-s1 {
  background: #b8bccb;
}

.is-s2 {
  background: #ff9f2e;
}

.is-s3 {
  background: #ff5b5b;
}

.is-s4 {
  background: #d8dae4;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  margin-top: 16rpx;
}

.legend__item {
  display: flex;
  align-items: center;
  margin-right: 28rpx;
}

.legend__dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 4rpx;
}

.legend__dot--free {
  background: #22c55e;
}

.legend__dot--busy {
  background: #b8bccb;
}

.legend__dot--booked {
  background: #ff9f2e;
}

.legend__dot--fix {
  background: #ff5b5b;
}

.legend__text {
  margin-left: 8rpx;
  font-size: $fs-sm;
  color: $text-secondary;
}

/* ==================== 确认上机 ==================== */
.seat-card {
  margin: 20rpx $gap-page 0;
  padding: 32rpx 28rpx;
}

.seat-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.seat-card__badge {
  padding: 4rpx 16rpx;
  border-radius: $radius-xs;
  background: $brand-primary-soft;
}

.seat-card__badge-text {
  font-size: $fs-sm;
  color: $brand-primary;
  font-weight: 600;
}

.seat-card__store {
  font-size: $fs-sm;
  color: $text-placeholder;
  @include ellipsis;
}

.seat-card__seat {
  display: block;
  margin-top: 24rpx;
  font-size: 56rpx;
  font-weight: 800;
  color: $text-primary;
  letter-spacing: 2rpx;
}

.seat-card__spec {
  display: block;
  margin-top: 12rpx;
  font-size: $fs-base;
  color: $text-secondary;
}

.seat-card__rows {
  margin-top: 30rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid $divider;
}

.seat-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 0;
}

.seat-card__label {
  font-size: $fs-md;
  color: $text-secondary;
}

.seat-card__value {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
}

.scan__notice {
  margin: 20rpx $gap-page 0;
  padding: 22rpx 24rpx;
  display: flex;
  align-items: flex-start;
  background: #fffaf0;
}

.scan__notice-text {
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
  font-size: $fs-base;
  color: #a8681a;
  line-height: 1.6;
}

.scan__actions {
  display: flex;
  align-items: center;
  padding: 40rpx $gap-page 0;
}

.scan__action {
  flex: 1;
  min-width: 0;
  margin-right: 24rpx;

  &:last-child {
    margin-right: 0;
  }
}
</style>
