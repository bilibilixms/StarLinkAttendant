<script setup lang="ts">
/**
 * 服务页（严格对照 reference/2-服务.jpg）
 *
 * 结构：淡蓝紫渐变头部（耳机 + 居中「服务」+ 更多）
 * → 预订状态卡（未预约时显示「您还没有预订服务」+ 立即预订）
 * → 「星络灵侍馆服务有什么？」2 列宫格
 * → 「电竞酒店服务有什么？」2 列宫格
 */
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppStatusBar from '@/components/AppStatusBar.vue'
import AppTabBar from '@/components/AppTabBar.vue'
import AiFloatBall from '@/components/AiFloatBall.vue'
import { reservationApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { navTo, goLogin } from '@/utils/nav'
import { toast, confirm, actionSheet } from '@/utils/ui'
import { formatDateTime } from '@/utils/format'
import type { Reservation } from '@/types/reservation'

const user = useUserStore()
const app = useAppStore()

/* ==================== 预订状态 ==================== */

const reservation = ref<Reservation | null>(null)
const loading = ref(true)

async function loadReservation(): Promise<void> {
  if (!user.isLogin) {
    reservation.value = null
    loading.value = false
    return
  }
  loading.value = true
  try {
    const res = await reservationApi.getReservations({ current: 1, size: 1, status: 0 })
    reservation.value = res.records[0] ?? null
  } catch {
    reservation.value = null
  } finally {
    loading.value = false
  }
}

onShow(() => {
  void loadReservation()
})

function goReserve(): void {
  if (!user.isLogin) {
    goLogin('/pages/service/index')
    return
  }
  navTo('/pages/reservation/index')
}

async function cancelReservation(): Promise<void> {
  if (!reservation.value) return
  const ok = await confirm('确定要取消这条预约吗？', '取消预约', '确定取消')
  if (!ok) return
  try {
    await reservationApi.cancelReservation(reservation.value.id)
    toast('预约已取消')
    void loadReservation()
  } catch {
    /* request 层已提示 */
  }
}

/* ==================== 服务宫格 ==================== */

interface SvcItem {
  key: string
  title: string
  desc: string
  icon: string
  color: string
}

const CAFE_SERVICES: SvcItem[] = [
  { key: 'reserve', title: '预约订座', desc: '提前锁定位置', icon: 'svc-seat', color: '#3AC6C6' },
  { key: 'scan', title: '直接上机', desc: '选定机位即开机', icon: 'svc-power', color: '#4A9BFF' },
  { key: 'remote-end', title: '远程下机', desc: '挂机不误事', icon: 'svc-remote', color: '#5B9BFF' },
  { key: 'order-food', title: '自助点餐', desc: '一键下单送到位', icon: 'svc-food', color: '#5FD3B0' },
  { key: 'recharge', title: '在线充值', desc: '在线充值享优惠', icon: 'svc-wallet', color: '#3AC6C6' },
  { key: 'feedback', title: '意见反馈', desc: '倾听您的建议', icon: 'svc-chat', color: '#67C23A' },
]

const HOTEL_SERVICES: SvcItem[] = [
  { key: 'hotel-book', title: '预约订房', desc: '在线预订房间', icon: 'svc-hotel', color: '#3AC6C6' },
  { key: 'hotel-game', title: '游戏特权', desc: '热门游戏任你选', icon: 'svc-game', color: '#4A9BFF' },
  { key: 'hotel-service', title: '客房服务', desc: '服务员快速上门', icon: 'svc-bed', color: '#5FD3B0' },
  { key: 'hotel-wifi', title: '免费WIFI', desc: '一键连接欢乐玩', icon: 'svc-wifi', color: '#5B9BFF' },
  { key: 'hotel-invoice', title: '开房票', desc: '在线申请开票', icon: 'svc-invoice', color: '#3AC6C6' },
  { key: 'feedback', title: '意见反馈', desc: '倾听您的建议', icon: 'svc-chat', color: '#67C23A' },
]

/** 服务项 → 目标动作 */
function onServiceTap(item: SvcItem): void {
  switch (item.key) {
    case 'reserve':
      goReserve()
      break
    case 'scan':
      scanToStart()
      break
    case 'remote-end':
      void onRemoteEnd()
      break
    case 'order-food':
      navTo('/pages/product/index')
      break
    case 'recharge':
      navTo('/pages/recharge/index')
      break
    case 'feedback':
      navTo('/pages/feedback/index')
      break
    case 'hotel-book':
      navTo('/pages/hotel/index')
      break
    case 'hotel-game':
      navTo('/pages/game/index')
      break
    case 'hotel-service':
      navTo('/pages/feedback/index')
      break
    case 'hotel-invoice':
      navTo('/pages/feedback/index')
      break
    case 'hotel-wifi':
      toast('已连接「星络灵侍馆」免费 WIFI')
      break
    default:
      toast('功能开发中')
  }
}

/** 直接上机：跳转到选座页，点击空闲机位即开台 */
function scanToStart(): void {
  if (!user.isLogin) {
    goLogin('/pages/service/index')
    return
  }
  navTo('/pages/session/scan')
}

/** 远程下机：有会话则确认下机，否则去上机 */
async function onRemoteEnd(): Promise<void> {
  if (!user.isLogin) {
    goLogin('/pages/service/index')
    return
  }
  const session = await app.fetchCurrentSession()
  if (!session) {
    toast('当前没有进行中的上机')
    return
  }
  navTo('/pages/session/current')
}

async function onMore(): Promise<void> {
  const idx = await actionSheet(['我的预约', '我的订单', '消费记录', '在线客服'])
  const routes = ['/pages/reservation/index', '/pages/order/index', '/pages/consume/index', '/pages/service/customer']
  if (idx >= 0) navTo(routes[idx])
}

function onHeadset(): void {
  navTo('/pages/service/customer')
}
</script>

<template>
  <view class="page-root page-root--has-tabbar svc">
    <!-- ==================== 顶部 ==================== -->
    <view class="svc__header">
      <AppStatusBar />
      <view class="svc__navbar">
        <view class="svc__nav-btn" @tap="onHeadset">
          <AppIcon name="headset" :size="52" color="#2B2B3A" :stroke-width="1.8" />
        </view>
        <text class="svc__nav-title">服务</text>
        <view class="svc__nav-btn svc__nav-btn--right" @tap="onMore">
          <AppIcon name="menu" :size="46" color="#2B2B3A" :stroke-width="1.8" />
        </view>
        <!-- #ifdef MP-WEIXIN -->
        <view class="svc__capsule" />
        <!-- #endif -->
      </view>
    </view>

    <!-- ==================== 预订状态卡 ==================== -->
    <view class="card reserve-card">
      <!-- 已预约 -->
      <template v-if="reservation">
        <view class="reserve-card__head">
          <view class="reserve-card__badge">
            <text class="reserve-card__badge-text">待使用</text>
          </view>
          <text class="reserve-card__no">{{ reservation.reservationNo }}</text>
        </view>
        <text class="reserve-card__store">{{ reservation.storeName }}</text>
        <view class="reserve-card__row">
          <AppIcon name="clock" :size="34" color="#8A8A99" />
          <text class="reserve-card__time">
            {{ formatDateTime(reservation.startTime, true) }} 起
          </text>
        </view>
        <view class="reserve-card__row">
          <AppIcon name="svc-seat" :size="34" color="#8A8A99" />
          <text class="reserve-card__time">
            {{ reservation.areaName || reservation.roomTypeName }} ·
            {{ reservation.peopleCount }} 人
          </text>
        </view>
        <view class="reserve-card__actions">
          <AppButton type="ghost" size="sm" @tap="cancelReservation">取消预约</AppButton>
          <AppButton type="primary" size="sm" @tap="scanToStart">直接上机</AppButton>
        </view>
      </template>

      <!-- 未预约 -->
      <template v-else>
        <text class="reserve-card__empty">您还没有预订服务</text>
        <AppButton
          type="primary"
          size="lg"
          :pill="false"
          class="reserve-card__btn"
          @tap="goReserve"
        >
          立即预订
        </AppButton>
      </template>
    </view>

    <!-- ==================== 星络灵侍馆服务 ==================== -->
    <view class="card svc-block">
      <text class="svc-block__title">星络灵侍馆服务有什么？</text>
      <view class="svc-grid">
        <view
          v-for="item in CAFE_SERVICES"
          :key="item.key"
          class="svc-item"
          @tap="onServiceTap(item)"
        >
          <view class="svc-item__main">
            <text class="svc-item__title">{{ item.title }}</text>
            <text class="svc-item__desc">{{ item.desc }}</text>
          </view>
          <AppIcon :name="item.icon" :size="62" :color="item.color" :stroke-width="1.5" />
        </view>
      </view>
    </view>

    <!-- ==================== 电竞酒店服务 ==================== -->
    <view class="card svc-block">
      <text class="svc-block__title">电竞酒店服务有什么？</text>
      <view class="svc-grid">
        <view
          v-for="(item, i) in HOTEL_SERVICES"
          :key="`${item.key}-${i}`"
          class="svc-item"
          @tap="onServiceTap(item)"
        >
          <view class="svc-item__main">
            <text class="svc-item__title">{{ item.title }}</text>
            <text class="svc-item__desc">{{ item.desc }}</text>
          </view>
          <AppIcon :name="item.icon" :size="62" :color="item.color" :stroke-width="1.5" />
        </view>
      </view>
    </view>

    <AppTabBar current="service" />
    <AiFloatBall />
  </view>
</template>

<style lang="scss" scoped>
.svc {
  background: $page-bg;
}

/* ==================== 顶部 ==================== */
.svc__header {
  background: $grad-service-header;
  padding-bottom: 8rpx;
}

.svc__navbar {
  position: relative;
  height: 88rpx;
  display: flex;
  align-items: center;
  padding: 0 $gap-page;
}

.svc__nav-title {
  position: absolute;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 36rpx;
  font-weight: 600;
  color: $text-primary;
  pointer-events: none;
}

.svc__nav-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    opacity: 0.6;
  }
}

.svc__nav-btn--right {
  margin-left: auto;
}

.svc__capsule {
  width: 180rpx;
  flex-shrink: 0;
}

/* ==================== 预订卡 ==================== */
.reserve-card {
  margin: 20rpx $gap-page 0;
  padding: 60rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.reserve-card__empty {
  font-size: 36rpx;
  font-weight: 600;
  color: $text-primary;
  letter-spacing: 1rpx;
}

.reserve-card__btn {
  margin-top: 48rpx;
  width: 340rpx;
}

/* 已预约态 */
.reserve-card__head {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.reserve-card__badge {
  padding: 4rpx 16rpx;
  border-radius: $radius-xs;
  background: $brand-primary-soft;
}

.reserve-card__badge-text {
  font-size: $fs-sm;
  color: $brand-primary;
  font-weight: 600;
}

.reserve-card__no {
  font-size: $fs-sm;
  color: $text-placeholder;
}

.reserve-card__store {
  width: 100%;
  margin-top: 20rpx;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
  @include ellipsis;
}

.reserve-card__row {
  width: 100%;
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}

.reserve-card__time {
  margin-left: 12rpx;
  font-size: $fs-base;
  color: $text-secondary;
  @include ellipsis;
}

.reserve-card__actions {
  width: 100%;
  margin-top: 36rpx;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20rpx;
}

/* ==================== 服务宫格 ==================== */
.svc-block {
  margin: 24rpx $gap-page 0;
  padding: 32rpx 24rpx 20rpx;
}

.svc-block__title {
  display: block;
  font-size: 38rpx;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.3;
}

.svc-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  margin-top: 24rpx;
}

/* 参考图实测：卡片高 ≈91rpx、行间距 ≈40rpx —— 卡片矮、留白大 */
.svc-item {
  width: 48.5%;
  height: 128rpx;
  margin-bottom: 22rpx;
  padding: 0 18rpx;
  border-radius: $radius-md;
  background: $card-bg-soft;
  display: flex;
  flex-direction: row;
  align-items: center;

  &:active {
    background: #eaeef7;
  }
}

.svc-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.svc-item__title {
  font-size: 30rpx;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.25;
  @include ellipsis;
}

.svc-item__desc {
  margin-top: 6rpx;
  font-size: $fs-base;
  color: $text-secondary;
  line-height: 1.25;
  @include ellipsis;
}
</style>
