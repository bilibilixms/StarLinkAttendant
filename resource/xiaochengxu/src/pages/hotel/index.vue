<script setup lang="ts">
/**
 * 电竞酒店：房型列表 + 预订入口。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { reservationApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { navBack, navTo, goLogin } from '@/utils/nav'
import { eventValue } from '@/utils/event'
import { toast } from '@/utils/ui'
import { formatMoney, formatDate } from '@/utils/format'
import type { HotelRoomType, Reservation } from '@/types/reservation'

const app = useAppStore()
const user = useUserStore()

const rooms = ref<HotelRoomType[]>([])
const myReservations = ref<Reservation[]>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')

const checkIn = ref(formatDate(new Date()))
const nights = ref(1)

const checkOut = computed(() => {
  const d = new Date(checkIn.value.replace(/-/g, '/'))
  d.setDate(d.getDate() + nights.value)
  return formatDate(d)
})

async function load(): Promise<void> {
  state.value = 'loading'
  try {
    rooms.value = await reservationApi.getHotelRoomTypes({ storeId: app.store.id })
    state.value = rooms.value.length ? 'success' : 'empty'
  } catch {
    state.value = 'error'
  }
  void loadMine()
}

async function loadMine(): Promise<void> {
  if (!user.isLogin) return
  try {
    const res = await reservationApi.getReservations({ current: 1, size: 3, status: 0 })
    myReservations.value = res.records.filter((r) => r.type === 2)
  } catch {
    myReservations.value = []
  }
}

onLoad(() => {
  void load()
})

function stepNights(delta: number): void {
  nights.value = Math.min(7, Math.max(1, nights.value + delta))
}

/** 入住日期变更（模板里不能直接写 e.detail.value，类型对不上） */
function onCheckInChange(e: Event): void {
  checkIn.value = eventValue(e)
}

/** 预订：Demo 阶段复用预约接口，跳转到预约页由用户确认时间与人数 */
function onBook(room: HotelRoomType): void {
  if (!user.isLogin) {
    goLogin('/pages/hotel/index')
    return
  }
  toast(`已选择「${room.name}」，请确认入住时间`)
  navTo('/pages/reservation/index')
}

function onCall(): void {
  uni.makePhoneCall({
    phoneNumber: '02162378888',
    fail: () => toast('酒店前台电话：021-6237 8888'),
  })
}
</script>

<template>
  <view class="ht page-root">
    <AppNavBar title="电竞酒店" show-back @back="navBack" />

    <!-- ==================== 头部 ==================== -->
    <view class="hero">
      <text class="hero__title">电竞酒店</text>
      <text class="hero__sub">开黑到天亮，睡在战场旁</text>

      <view class="hero__stats">
        <view class="hero__stat">
          <text class="hero__stat-value">{{ rooms.length }}</text>
          <text class="hero__stat-label">可选房型</text>
        </view>
        <view class="hero__stat-divider" />
        <view class="hero__stat">
          <text class="hero__stat-value">{{ user.isLogin ? user.points : '--' }}</text>
          <text class="hero__stat-label">酒店积分</text>
        </view>
        <view class="hero__stat-divider" />
        <view class="hero__stat">
          <text class="hero__stat-value">7×24</text>
          <text class="hero__stat-label">前台服务</text>
        </view>
      </view>
    </view>

    <!-- ==================== 入住日期 ==================== -->
    <view class="card sec">
      <view class="dates">
        <picker mode="date" :value="checkIn" :start="formatDate(new Date())" @change="onCheckInChange">
          <view class="date">
            <text class="date__label">入住</text>
            <text class="date__value">{{ checkIn.slice(5) }}</text>
          </view>
        </picker>

        <view class="dates__nights">
          <view class="stepper">
            <view class="stepper__btn" :class="{ 'is-disabled': nights <= 1 }" @tap="stepNights(-1)">
              <AppIcon name="minus" :size="24" color="#5B5BD6" :stroke-width="2.4" />
            </view>
            <text class="stepper__num">{{ nights }}晚</text>
            <view class="stepper__btn stepper__btn--add" @tap="stepNights(1)">
              <AppIcon name="plus" :size="24" color="#FFFFFF" :stroke-width="2.4" />
            </view>
          </view>
        </view>

        <view class="date date--right">
          <text class="date__label">离店</text>
          <text class="date__value">{{ checkOut.slice(5) }}</text>
        </view>
      </view>
    </view>

    <!-- ==================== 我的预订 ==================== -->
    <view v-if="myReservations.length" class="card sec">
      <text class="sec__title">我的预订</text>
      <view v-for="r in myReservations" :key="r.id" class="mine">
        <view class="mine__left">
          <text class="mine__room">{{ r.roomTypeName || '电竞房' }}</text>
          <text class="mine__time">{{ r.startTime.slice(5, 16) }} 入住 · {{ r.peopleCount }}人</text>
        </view>
        <view class="mine__badge">
          <text class="mine__badge-text">待入住</text>
        </view>
      </view>
    </view>

    <!-- ==================== 房型 ==================== -->
    <StateView v-if="state !== 'success'" :state="state" empty-text="暂无可预订房型" @retry="load" />

    <template v-else>
      <text class="ht__section">全部房型</text>
      <view v-for="room in rooms" :key="room.id" class="room">
        <image class="room__img" :src="room.cover" mode="aspectFill" />

        <view class="room__body">
          <text class="room__name">{{ room.name }}</text>
          <text class="room__capacity">可住 {{ room.capacity }} 人 · 剩余 {{ room.availableCount }} 间</text>

          <view class="room__tags">
            <text v-for="t in room.tags" :key="t" class="room__tag">{{ t }}</text>
          </view>

          <view class="room__foot">
            <view class="room__price">
              <text class="room__price-symbol">¥</text>
              <text class="room__price-num">{{ formatMoney(room.memberPrice) }}</text>
              <text class="room__price-unit">/晚</text>
            </view>
            <text class="room__origin">¥{{ formatMoney(room.price) }}</text>
            <AppButton type="primary" size="sm" class="room__btn" @tap="onBook(room)">
              预订
            </AppButton>
          </view>
        </view>
      </view>

      <!-- ==================== 服务 ==================== -->
      <view class="card sec sec--mt">
        <text class="sec__title">酒店服务</text>
        <view class="services">
          <view v-for="s in [
            { icon: 'svc-game', text: '游戏特权', color: '#4A9BFF' },
            { icon: 'svc-wifi', text: '免费WIFI', color: '#5B9BFF' },
            { icon: 'svc-bed', text: '客房服务', color: '#5FD3B0' },
            { icon: 'svc-invoice', text: '开房票', color: '#3AC6C6' },
          ]" :key="s.text" class="service" @tap="s.text === '客房服务' ? onCall() : toast('请到前台办理')">
            <AppIcon :name="s.icon" :size="56" :color="s.color" :stroke-width="1.6" />
            <text class="service__text">{{ s.text }}</text>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.ht {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

/* ==================== 头部 ==================== */
.hero {
  margin: 0 $gap-page;
  padding: 44rpx 32rpx 34rpx;
  border-radius: $radius-lg;
  background: linear-gradient(135deg, #4a6cf7 0%, #6c4be0 55%, #8b5cf6 100%);
  box-shadow: 0 12rpx 30rpx rgba(80, 70, 200, 0.28);
}

.hero__title {
  display: block;
  font-size: 52rpx;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 2rpx;
}

.hero__sub {
  display: block;
  margin-top: 10rpx;
  font-size: $fs-md;
  color: rgba(255, 255, 255, 0.85);
}

.hero__stats {
  display: flex;
  align-items: center;
  margin-top: 34rpx;
  padding-top: 28rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.22);
}

.hero__stat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero__stat-value {
  font-size: 40rpx;
  font-weight: 800;
  color: #ffffff;
}

.hero__stat-label {
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: rgba(255, 255, 255, 0.8);
}

.hero__stat-divider {
  width: 1rpx;
  height: 52rpx;
  background: rgba(255, 255, 255, 0.22);
  flex-shrink: 0;
}

/* ==================== 区块 ==================== */
.sec {
  margin: 20rpx $gap-page 0;
  padding: 26rpx 24rpx;
}

.sec--mt {
  margin-top: 24rpx;
}

.sec__title {
  display: block;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 16rpx;
}

/* ==================== 日期 ==================== */
.dates {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.date {
  display: flex;
  flex-direction: column;
  align-items: flex-start;

  &:active {
    opacity: 0.7;
  }
}

.date--right {
  align-items: flex-end;
}

.date__label {
  font-size: $fs-sm;
  color: $text-placeholder;
}

.date__value {
  margin-top: 8rpx;
  font-size: 38rpx;
  font-weight: 800;
  color: $text-primary;
}

.dates__nights {
  flex-shrink: 0;
}

/* ==================== 我的预订 ==================== */
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

.mine__room {
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

.mine__badge {
  flex-shrink: 0;
  padding: 4rpx 14rpx;
  border-radius: $radius-xs;
  background: $brand-primary-soft;
}

.mine__badge-text {
  font-size: $fs-xs;
  color: $brand-primary;
  font-weight: 600;
}

/* ==================== 房型 ==================== */
.ht__section {
  display: block;
  margin: 30rpx $gap-page 16rpx;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
}

.room {
  display: flex;
  align-items: stretch;
  margin: 0 $gap-page 20rpx;
  padding: 20rpx;
  background: #ffffff;
  border-radius: $radius-lg;
}

.room__img {
  width: 220rpx;
  height: 220rpx;
  border-radius: $radius-md;
  flex-shrink: 0;
  background: $card-bg-soft;
}

.room__body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
}

.room__name {
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
  @include ellipsis;
}

.room__capacity {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-secondary;
}

.room__tags {
  display: flex;
  flex-wrap: wrap;
  margin-top: 12rpx;
}

.room__tag {
  margin: 0 10rpx 8rpx 0;
  padding: 2rpx 12rpx;
  border-radius: $radius-xs;
  background: $card-bg-soft;
  font-size: $fs-xs;
  color: $text-secondary;
}

.room__foot {
  display: flex;
  align-items: baseline;
  margin-top: auto;
  padding-top: 14rpx;
}

.room__price {
  display: flex;
  align-items: baseline;
}

.room__price-symbol {
  font-size: $fs-base;
  font-weight: 700;
  color: $danger;
}

.room__price-num {
  font-size: 40rpx;
  font-weight: 800;
  color: $danger;
  line-height: 1.1;
}

.room__price-unit {
  margin-left: 4rpx;
  font-size: $fs-xs;
  color: $text-placeholder;
}

.room__origin {
  margin-left: 10rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  text-decoration: line-through;
}

.room__btn {
  margin-left: auto;
  flex-shrink: 0;
}

/* ==================== 服务 ==================== */
.services {
  display: flex;
  align-items: center;
}

.service {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  &:active {
    opacity: 0.65;
  }
}

.service__text {
  margin-top: 12rpx;
  font-size: $fs-base;
  color: $text-regular;
}

/* ==================== 步进器 ==================== */
.stepper {
  display: flex;
  align-items: center;
}

.stepper__btn {
  width: 48rpx;
  height: 48rpx;
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
  min-width: 84rpx;
  text-align: center;
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
}
</style>
