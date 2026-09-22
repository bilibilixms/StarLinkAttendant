<script setup lang="ts">
/**
 * 上机：选择空闲机位直接上机，无需扫码。
 *
 * 入口行为：进入页面 → 加载区域 + 座位图 → 点击空闲机位 → 直接开台 → 计时开始。
 * 已有进行中的会话时，后端会拒绝（提示先下机）。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
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

/** 区域 + 座位图 */
const areas = ref<SeatArea[]>([])
const seatMap = ref<SeatMap | null>(null)
const activeAreaId = ref(0)
const starting = ref(false)

const area = computed(() => areas.value.find((a) => a.id === activeAreaId.value))
const hourlyRate = computed(() => area.value?.hourlyRate ?? 0)
const canAfford = computed(() => user.balance > 0)

onLoad(() => {
  if (!user.isLogin) {
    goLogin('/pages/session/scan')
    return
  }
  void loadAreas()
})

async function loadAreas(): Promise<void> {
  try {
    areas.value = await storeApi.getSeatAreas(app.store.id)
    if (areas.value.length) {
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
  void loadSeats()
}

/**
 * 点击机位：空闲则直接开台，否则提示。
 *
 * 不再做扫码 + 二次确认，避免误触由 toast + 后端校验兜底：
 *  - 余额不足 → 后端 / mock 都会抛错
 *  - 已有进行中的会话 → 后端 / mock 都会抛错
 *  - 机位被占用 → 这里按状态 toast 提示
 */
async function onPickSeat(seat: Computer): Promise<void> {
  if (starting.value) return
  if (seat.status !== 0) {
    const label = { 1: '使用中', 2: '锁定', 3: '维修中', 4: '关机' }[seat.status] ?? '不可用'
    toast(`该机位${label}`)
    return
  }
  if (!canAfford.value) {
    toast('账户余额不足，请先充值')
    return
  }

  starting.value = true
  uni.showLoading({ title: '开台中...', mask: true })
  try {
    // qrContent 与字段同时传：mock 优先用字段兜底；后端真实接口若只看 qrContent 也能解析。
    const res = await sessionApi.scanStart({
      qrContent: JSON.stringify({
        storeId: app.store.id,
        areaId: seat.areaId,
        computerId: seat.id,
        computerName: seat.computerName,
      }),
      computerId: seat.id,
      storeId: app.store.id,
      // 区域必须带上：它决定计费费率，漏传会按普通区价格结算
      areaId: seat.areaId,
      computerName: seat.computerName,
    })
    app.setCurrentSession(res.session)
    uni.hideLoading()
    toast(res.resumed ? '已恢复上机会话' : '开机成功，祝您游戏愉快')
    setTimeout(() => reLaunch('/pages/session/current'), 700)
  } catch (e) {
    uni.hideLoading()
    const msg = (e as Error)?.message
    if (msg) toast(msg)
  } finally {
    starting.value = false
  }
}
</script>

<template>
  <view class="scan page-root">
    <AppNavBar title="选择机位上机" show-back @back="navBack" />

    <view class="card pick">
      <view class="flex-between">
        <text class="pick__title">选择区域</text>
        <text class="pick__balance">余额 ¥{{ formatMoney(user.balance) }}</text>
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
          <text class="pick__area-meta">{{ a.hourlyRate ? `${a.hourlyRate}元/时 · ` : '' }}空闲{{ a.freeCount }}</text>
        </view>
      </view>

      <text class="pick__title pick__title--mt">选择机位</text>
      <view v-if="seatMap && seatMap.seats.length" class="seats">
        <view
          v-for="s in seatMap.seats"
          :key="s.id"
          class="seat"
          :class="[`is-s${s.status}`, { 'is-busy': starting }]"
          @tap="onPickSeat(s)"
        >
          <text class="seat__text">{{ s.computerNo }}</text>
        </view>
      </view>
      <view v-else class="pick__empty">
        <AppIcon name="empty-network" :size="120" color="#C6C9D6" />
        <text class="pick__empty-text">该区域暂无机位</text>
      </view>

      <view class="legend">
        <view class="legend__item"><view class="legend__dot legend__dot--free" /><text class="legend__text">空闲</text></view>
        <view class="legend__item"><view class="legend__dot legend__dot--busy" /><text class="legend__text">使用中</text></view>
        <view class="legend__item"><view class="legend__dot legend__dot--locked" /><text class="legend__text">锁定</text></view>
        <view class="legend__item"><view class="legend__dot legend__dot--fix" /><text class="legend__text">维修中</text></view>
        <view class="legend__item"><view class="legend__dot legend__dot--off" /><text class="legend__text">关机</text></view>
      </view>

      <view class="pick__hint">
        <AppIcon name="warning" :size="28" color="#FF9F2E" :stroke-width="1.7" />
        <text class="pick__hint-text">
          点击空闲机位即开始计时，下机时从余额自动结算。如需更换机位请先下机。
        </text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.scan {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

/* ==================== 选座 ==================== */
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

.pick__balance {
  font-size: $fs-sm;
  color: $text-secondary;
  font-weight: 600;
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

.pick__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 0 20rpx;
}

.pick__empty-text {
  margin-top: 16rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
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

  &.is-busy {
    opacity: 0.5;
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
  background: #409eff;
}

.is-s2 {
  background: #ff9f2e;
}

.is-s3 {
  background: #ff5b5b;
}

.is-s4 {
  background: #909399;
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
  background: #409eff;
}

.legend__dot--locked {
  background: #ff9f2e;
}

.legend__dot--fix {
  background: #ff5b5b;
}

.legend__dot--off {
  background: #909399;
}

.legend__text {
  margin-left: 8rpx;
  font-size: $fs-sm;
  color: $text-secondary;
}

.pick__hint {
  margin-top: 24rpx;
  padding: 22rpx 24rpx;
  display: flex;
  align-items: flex-start;
  background: #fffaf0;
  border-radius: $radius-md;
}

.pick__hint-text {
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
  font-size: $fs-base;
  color: #a8681a;
  line-height: 1.6;
}
</style>
