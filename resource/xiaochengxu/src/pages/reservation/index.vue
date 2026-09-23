<script setup lang="ts">
/**
 * 预约订座。
 * 选门店 / 区域 / 机位 / 日期时段 / 人数 → 0 元预约（保证金线上支付为 P2，见需求 §7.2 AP-04）。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { reservationApi, storeApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { navBack, navTo, goLogin } from '@/utils/nav'
import { eventValue } from '@/utils/event'
import { toast, toastSuccess } from '@/utils/ui'
import { formatMoney, formatDate } from '@/utils/format'
import type { Computer, SeatArea, SeatMap } from '@/types/session'
import type { Reservation } from '@/types/reservation'

const app = useAppStore()
const user = useUserStore()

const areas = ref<SeatArea[]>([])
const seatMap = ref<SeatMap | null>(null)
const myReservations = ref<Reservation[]>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const submitting = ref(false)

const activeAreaId = ref(0)
const selectedSeatIds = ref<number[]>([])
const date = ref(formatDate(new Date()))
const startTime = ref('19:00')
const durationHours = ref(4)
const peopleCount = ref(2)
const phone = ref('')
const remark = ref('')

const DURATIONS = [1, 2, 3, 4, 6, 8, 12]

const activeArea = computed(() => areas.value.find((a) => a.id === activeAreaId.value))
const endTime = computed(() => {
  const [h, m] = startTime.value.split(':').map(Number)
  const total = h + durationHours.value
  const nextDay = total >= 24
  const hh = total % 24
  return `${nextDay ? '次日 ' : ''}${String(hh).padStart(2, '0')}:${String(m || 0).padStart(2, '0')}`
})

/** 按小时估算的参考费用（仅展示，实际以到店计费为准） */
const estimate = computed(() => {
  const rate = activeArea.value?.hourlyRate ?? 0
  return rate * durationHours.value
})

async function load(): Promise<void> {
  state.value = 'loading'
  try {
    const [areaList] = await Promise.all([storeApi.getSeatAreas(app.store.id)])
    areas.value = areaList
    if (areaList.length) {
      activeAreaId.value = areaList[0].id
      await loadSeats()
    }
    await loadMyReservations()
    state.value = 'success'
  } catch {
    state.value = 'error'
  }
}

async function loadSeats(): Promise<void> {
  try {
    seatMap.value = await storeApi.getSeatMap(app.store.id, activeAreaId.value)
  } catch {
    seatMap.value = null
  }
}

async function loadMyReservations(): Promise<void> {
  if (!user.isLogin) return
  try {
    const res = await reservationApi.getReservations({ current: 1, size: 3, status: 0 })
    myReservations.value = res.records
  } catch {
    myReservations.value = []
  }
}

onLoad(() => {
  phone.value = user.member?.phone ?? ''
  void load()
})

function switchArea(id: number): void {
  activeAreaId.value = id
  selectedSeatIds.value = []
  void loadSeats()
}

function toggleSeat(seat: Computer): void {
  if (seat.status !== 0) {
    toast(`该机位${{ 1: '使用中', 2: '锁定', 3: '维修中', 4: '关机' }[seat.status] ?? '不可用'}`)
    return
  }
  const idx = selectedSeatIds.value.indexOf(seat.id)
  if (idx >= 0) {
    selectedSeatIds.value.splice(idx, 1)
  } else {
    if (selectedSeatIds.value.length >= peopleCount.value) {
      toast(`最多选择 ${peopleCount.value} 个机位（与到店人数一致）`)
      return
    }
    selectedSeatIds.value.push(seat.id)
  }
}

function stepPeople(delta: number): void {
  peopleCount.value = Math.min(10, Math.max(1, peopleCount.value + delta))
  if (selectedSeatIds.value.length > peopleCount.value) {
    selectedSeatIds.value = selectedSeatIds.value.slice(0, peopleCount.value)
  }
}

function onPhoneInput(e: Event): void {
  phone.value = eventValue(e)
}

/** 到店日期变更 */
function onDateChange(e: Event): void {
  date.value = eventValue(e)
}

/** 开始时间变更 */
function onStartTimeChange(e: Event): void {
  startTime.value = eventValue(e)
}

function onRemarkInput(e: Event): void {
  remark.value = eventValue(e)
}

function buildDateTime(time: string, dayOffset = 0): string {
  const d = new Date(date.value.replace(/-/g, '/'))
  d.setDate(d.getDate() + dayOffset)
  return `${formatDate(d)} ${time}:00`
}

/* ==================== 提交 ==================== */

async function onSubmit(): Promise<void> {
  if (!user.isLogin) {
    goLogin('/pages/reservation/index')
    return
  }
  if (!activeAreaId.value) {
    toast('请选择区域')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    toast('请填写正确的联系电话')
    return
  }
  if (submitting.value) return

  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '确认预约',
      content: `${app.store.shortName}\n${activeArea.value?.areaName}\n${date.value} ${startTime.value} - ${endTime.value}\n${peopleCount.value} 人\n0 元预约，到店支付`,
      confirmText: '确认预约',
      confirmColor: '#5B5BD6',
      success: (r) => resolve(!!r.confirm),
      fail: () => resolve(false),
    })
  })
  if (!ok) return

  submitting.value = true
  uni.showLoading({ title: '提交中...', mask: true })
  try {
    const [h, m] = startTime.value.split(':').map(Number)
    const crossDay = h + durationHours.value >= 24
    const res = await reservationApi.createReservation({
      type: 1,
      storeId: app.store.id,
      areaId: activeAreaId.value,
      computerIds: selectedSeatIds.value.length ? selectedSeatIds.value : undefined,
      startTime: buildDateTime(startTime.value),
      endTime: buildDateTime(`${String((h + durationHours.value) % 24).padStart(2, '0')}:${String(m || 0).padStart(2, '0')}`, crossDay ? 1 : 0),
      peopleCount: peopleCount.value,
      contactPhone: phone.value,
      remark: remark.value,
    })
    uni.hideLoading()
    toastSuccess(res.message || '预约成功')
    void loadMyReservations()
    setTimeout(() => navTo('/pages/service/index'), 1000)
  } catch (e) {
    uni.hideLoading()
    const msg = (e as Error)?.message
    if (msg) toast(msg)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="rsv page-root">
    <AppNavBar title="预约订座" show-back @back="navBack" />

    <StateView v-if="state !== 'success'" :state="state" @retry="load" />

    <template v-else>
      <!-- ==================== 已有预约 ==================== -->
      <view v-if="myReservations.length" class="card sec">
        <text class="sec__title">待使用的预约</text>
        <view v-for="r in myReservations" :key="r.id" class="mine">
          <view class="mine__left">
            <text class="mine__area">{{ r.areaName || r.roomTypeName }} · {{ r.peopleCount }}人</text>
            <text class="mine__time">{{ r.startTime.slice(5, 16) }} 起</text>
          </view>
          <text class="mine__no">{{ r.reservationNo }}</text>
        </view>
      </view>

      <!-- ==================== 门店 ==================== -->
      <view class="card sec">
        <text class="sec__title">门店</text>
        <view class="store" @tap="navTo('/pages/store/index')">
          <AppIcon name="location" :size="34" color="#5B5BD6" />
          <view class="store__info">
            <text class="store__name">{{ app.store.name }}</text>
            <text class="store__addr">{{ app.store.address }}</text>
          </view>
          <view class="store__arrow" />
        </view>
      </view>

      <!-- ==================== 区域 ==================== -->
      <view class="card sec">
        <text class="sec__title">选择区域</text>
        <view class="areas">
          <view
            v-for="a in areas"
            :key="a.id"
            class="area"
            :class="{ 'is-active': activeAreaId === a.id }"
            @tap="switchArea(a.id)"
          >
            <text class="area__name">{{ a.areaName }}</text>
            <text class="area__meta">{{ a.hourlyRate ? `${a.hourlyRate}元/时` : '到店计费' }}</text>
            <text class="area__free">空闲 {{ a.freeCount }}</text>
          </view>
        </view>
        <text v-if="activeArea?.description" class="hint">{{ activeArea.description }}</text>
      </view>

      <!-- ==================== 机位 ==================== -->
      <view class="card sec">
        <view class="flex-between">
          <text class="sec__title sec__title--inline">选择机位</text>
          <text class="sec__sub">可选，不选则由门店安排</text>
        </view>

        <view v-if="seatMap" class="seats">
          <view
            v-for="s in seatMap.seats"
            :key="s.id"
            class="seat"
            :class="[`is-s${s.status}`, { 'is-picked': selectedSeatIds.includes(s.id) }]"
            @tap="toggleSeat(s)"
          >
            <text class="seat__text">{{ s.computerNo }}</text>
          </view>
        </view>

        <view class="legend">
          <view class="legend__item"><view class="legend__dot legend__dot--free" /><text class="legend__text">空闲</text></view>
          <view class="legend__item"><view class="legend__dot legend__dot--busy" /><text class="legend__text">使用中</text></view>
          <view class="legend__item"><view class="legend__dot legend__dot--locked" /><text class="legend__text">锁定</text></view>
          <view class="legend__item"><view class="legend__dot legend__dot--fix" /><text class="legend__text">维修中</text></view>
          <view class="legend__item"><view class="legend__dot legend__dot--off" /><text class="legend__text">关机</text></view>
        </view>
      </view>

      <!-- ==================== 时间 ==================== -->
      <view class="card sec">
        <text class="sec__title">预约时间</text>

        <view class="frow">
          <text class="frow__label">到店日期</text>
          <picker mode="date" :value="date" :start="formatDate(new Date())" @change="onDateChange">
            <view class="frow__value">
              <text class="frow__text">{{ date }}</text>
              <view class="frow__arrow" />
            </view>
          </picker>
        </view>

        <view class="frow">
          <text class="frow__label">开始时间</text>
          <picker mode="time" :value="startTime" @change="onStartTimeChange">
            <view class="frow__value">
              <text class="frow__text">{{ startTime }}</text>
              <view class="frow__arrow" />
            </view>
          </picker>
        </view>

        <view class="frow frow--col">
          <text class="frow__label">预计时长</text>
          <view class="durations">
            <view
              v-for="d in DURATIONS"
              :key="d"
              class="dur"
              :class="{ 'is-active': durationHours === d }"
              @tap="durationHours = d"
            >
              <text class="dur__text">{{ d }}小时</text>
            </view>
          </view>
        </view>

        <view class="frow">
          <text class="frow__label">结束时间</text>
          <text class="frow__value-text">{{ endTime }}</text>
        </view>

        <view class="frow">
          <text class="frow__label">到店人数</text>
          <view class="stepper">
            <view class="stepper__btn" :class="{ 'is-disabled': peopleCount <= 1 }" @tap="stepPeople(-1)">
              <AppIcon name="minus" :size="26" color="#5B5BD6" :stroke-width="2.4" />
            </view>
            <text class="stepper__num">{{ peopleCount }}</text>
            <view class="stepper__btn stepper__btn--add" @tap="stepPeople(1)">
              <AppIcon name="plus" :size="26" color="#FFFFFF" :stroke-width="2.4" />
            </view>
          </view>
        </view>
      </view>

      <!-- ==================== 联系方式 ==================== -->
      <view class="card sec">
        <text class="sec__title">联系方式</text>
        <view class="frow">
          <text class="frow__label">手机号</text>
          <input
            class="frow__input"
            type="number"
            maxlength="11"
            :value="phone"
            placeholder="用于到店核销"
            placeholder-class="frow__ph"
            @input="onPhoneInput"
          />
        </view>
        <view class="frow">
          <text class="frow__label">备注</text>
          <input
            class="frow__input"
            maxlength="50"
            :value="remark"
            placeholder="如：靠窗位置优先"
            placeholder-class="frow__ph"
            @input="onRemarkInput"
          />
        </view>
      </view>

      <!-- ==================== 费用说明 ==================== -->
      <view class="card sec">
        <view class="amt">
          <text class="amt__label">保证金</text>
          <text class="amt__value amt__value--free">¥0.00（免押金）</text>
        </view>
        <view class="amt">
          <text class="amt__label">参考费用</text>
          <text class="amt__value">{{ estimate > 0 ? `约 ¥${formatMoney(estimate)}` : '到店按实际计费' }}</text>
        </view>
      </view>

      <!-- ==================== 提交 ==================== -->
      <view class="rsv__bar">
        <view class="rsv__bar-info">
          <text class="rsv__bar-label">应付</text>
          <text class="rsv__bar-money">¥0.00</text>
        </view>
        <AppButton
          type="primary"
          size="lg"
          class="rsv__bar-btn"
          :loading="submitting"
          :disabled="submitting"
          @tap="onSubmit"
        >
          0 元预约
        </AppButton>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.rsv {
  padding-bottom: 180rpx;
  @include safe-area-bottom(0rpx);
}

.sec {
  margin: 20rpx $gap-page 0;
  padding: 26rpx 24rpx;
}

.sec__title {
  display: block;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 20rpx;
}

.sec__title--inline {
  margin-bottom: 0;
}

.sec__sub {
  font-size: $fs-sm;
  color: $text-placeholder;
}

.hint {
  display: block;
  margin-top: 16rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

/* ==================== 已有预约 ==================== */
.mine {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.mine__area {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
}

.mine__time {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-secondary;
}

.mine__no {
  font-size: $fs-sm;
  color: $text-placeholder;
}

/* ==================== 门店 ==================== */
.store {
  display: flex;
  align-items: center;

  &:active {
    opacity: 0.8;
  }
}

.store__info {
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
}

.store__name {
  display: block;
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis;
}

.store__addr {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-secondary;
  @include ellipsis;
}

.store__arrow {
  width: 14rpx;
  height: 14rpx;
  margin-left: 12rpx;
  border-top: 3rpx solid $text-placeholder;
  border-right: 3rpx solid $text-placeholder;
  transform: rotate(45deg);
  border-radius: 2rpx;
  flex-shrink: 0;
}

/* ==================== 区域 ==================== */
.areas {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.area {
  width: 48.5%;
  margin-bottom: 18rpx;
  padding: 22rpx 20rpx;
  border-radius: $radius-md;
  background: $card-bg-soft;
  border: 2rpx solid transparent;

  &.is-active {
    border-color: $brand-primary;
    background: $brand-primary-soft;
  }

  &:active {
    opacity: 0.85;
  }
}

.area__name {
  font-size: $fs-md;
  font-weight: 700;
  color: $text-primary;
}

.area__meta {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-secondary;
}

.area__free {
  display: block;
  margin-top: 4rpx;
  font-size: $fs-sm;
  color: $success;
}

/* ==================== 座位 ==================== */
.seats {
  display: flex;
  flex-wrap: wrap;
  margin-top: 20rpx;
}

.seat {
  width: 84rpx;
  height: 72rpx;
  margin: 0 12rpx 12rpx 0;
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

.seat.is-picked {
  box-shadow: 0 0 0 4rpx #ffffff, 0 0 0 8rpx $brand-primary;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  margin-top: 12rpx;
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

/* ==================== 表单行 ==================== */
.frow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 84rpx;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.frow--col {
  flex-direction: column;
  align-items: stretch;
  padding: 20rpx 0;
}

.frow__label {
  font-size: $fs-md;
  color: $text-regular;
  flex-shrink: 0;
}

.frow__value {
  display: flex;
  align-items: center;

  &:active {
    opacity: 0.6;
  }
}

.frow__text {
  font-size: $fs-md;
  color: $text-primary;
  font-weight: 500;
}

.frow__value-text {
  font-size: $fs-md;
  color: $text-primary;
  font-weight: 600;
}

.frow__arrow {
  width: 14rpx;
  height: 14rpx;
  margin-left: 12rpx;
  border-top: 3rpx solid $text-placeholder;
  border-right: 3rpx solid $text-placeholder;
  transform: rotate(45deg);
  border-radius: 2rpx;
}

.frow__input {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  text-align: right;
  font-size: $fs-md;
  color: $text-primary;
}

.frow__ph {
  color: $text-placeholder;
  font-size: $fs-md;
}

/* ---------- 时长 ---------- */
.durations {
  display: flex;
  flex-wrap: wrap;
  margin-top: 18rpx;
}

.dur {
  padding: 12rpx 26rpx;
  margin: 0 14rpx 14rpx 0;
  border-radius: $radius-pill;
  background: $card-bg-soft;

  &.is-active {
    background: $brand-primary;
  }

  &:active {
    opacity: 0.8;
  }
}

.dur__text {
  font-size: $fs-base;
  color: $text-regular;
}

.dur.is-active .dur__text {
  color: #ffffff;
  font-weight: 600;
}

/* ==================== 金额 ==================== */
.amt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 0;
}

.amt__label {
  font-size: $fs-md;
  color: $text-secondary;
}

.amt__value {
  font-size: $fs-md;
  color: $text-primary;
}

.amt__value--free {
  color: $success;
  font-weight: 700;
}

/* ==================== 步进器 ==================== */
.stepper {
  display: flex;
  align-items: center;
}

.stepper__btn {
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  border: 2rpx solid $brand-primary;
  display: flex;
  align-items: center;
  justify-content: center;

  &.is-disabled {
    opacity: 0.4;
  }

  &:active {
    opacity: 0.7;
  }
}

.stepper__btn--add {
  background: $brand-primary;
  border-color: $brand-primary;
}

.stepper__num {
  min-width: 70rpx;
  text-align: center;
  font-size: $fs-lg;
  font-weight: 600;
  color: $text-primary;
}

/* ==================== 底部 ==================== */
.rsv__bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  padding: 16rpx $gap-page;
  background: #ffffff;
  border-top: 1rpx solid $divider;
  display: flex;
  align-items: center;
  @include safe-area-bottom(16rpx);
}

.rsv__bar-info {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
}

.rsv__bar-label {
  font-size: $fs-md;
  color: $text-secondary;
}

.rsv__bar-money {
  margin-left: 10rpx;
  font-size: 44rpx;
  font-weight: 800;
  color: $success;
}

.rsv__bar-btn {
  flex-shrink: 0;
  min-width: 260rpx;
}
</style>
