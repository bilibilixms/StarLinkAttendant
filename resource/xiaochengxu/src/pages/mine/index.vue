<script setup lang="ts">
/**
 * 我的页（严格对照 reference/5-我的.jpg）
 *
 * 结构：头像 + 昵称 + 签到/扫一扫/设置
 * → 钱包 / 卡券 / 积分 / 酒店积分
 * → 我的订单（全部订单 + 4 个入口）
 * → 「添加好友领至高 328 元大礼包」横幅
 * → 我的帖子（发布/点赞/收藏）
 * → 我的工具（5 个入口）
 */
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppStatusBar from '@/components/AppStatusBar.vue'
import AppTabBar from '@/components/AppTabBar.vue'
import { useUserStore } from '@/stores/user'
import { navTo, goLogin } from '@/utils/nav'
import { toast, toastSuccess } from '@/utils/ui'
import { formatMoney } from '@/utils/format'

const user = useUserStore()

const signing = ref(false)

const walletText = computed(() =>
  user.isLogin ? formatMoney(user.balance) : '--'
)
const couponText = computed(() => (user.isLogin ? '0' : '--'))
const pointsText = computed(() =>
  user.isLogin ? String(user.points) : '--'
)
const hotelPointsText = computed(() => (user.isLogin ? '0' : '--'))

const postStat = computed(() => ({ published: 0, liked: 0, collected: 0 }))

onShow(() => {
  // 后端无 summary 接口，登录后直接读本地缓存；不主动拉取
})

/* ==================== 顶部三动作 ==================== */

async function onSign(): Promise<void> {
  // 后端暂未实现签到接口，仅占位提示
  toast('签到功能开发中')
}

function onScan(): void {
  uni.scanCode({
    scanType: ['qrCode'],
    success: (res) => navTo(`/pages/session/scan?qr=${encodeURIComponent(res.result || '')}`),
    fail: (err) => {
      if (!/cancel/i.test(String(err?.errMsg))) toast('扫码失败，请重试')
    },
  })
}

function onSettings(): void {
  navTo('/pages/settings/index')
}

function onAvatarTap(): void {
  if (!user.isLogin) goLogin('/pages/mine/index')
  else navTo('/pages/profile/index')
}

/* ==================== 订单入口 ==================== */

const ORDER_ENTRIES = [
  { key: 'seat', title: '预约订座', icon: 'mine-order-seat', path: '/pages/reservation/index' },
  { key: 'hotel', title: '预订酒店', icon: 'mine-order-hotel', path: '/pages/hotel/index' },
  { key: 'food', title: '自助点餐', icon: 'mine-order-food', path: '/pages/order/index?tab=all' },
  { key: 'mall', title: '线上商城', icon: 'mine-order-mall', path: '/pages/product/index' },
]

/* ==================== 我的工具 ==================== */

const TOOLS = [
  { key: 'favorite', title: '收藏', icon: 'tool-favorite', path: '/pages/favorite/index' },
  { key: 'service', title: '在线客服', icon: 'tool-service', path: '/pages/service/customer' },
  { key: 'join', title: '加入我们', icon: 'tool-join', path: '' },
  { key: 'coin', title: '网鱼币兑换', icon: 'tool-coin', path: '/pages/points/index' },
  { key: 'report', title: '投诉举报', icon: 'tool-report', path: '/pages/feedback/index?type=report' },
]

function onToolTap(item: { key: string; title: string; path: string }): void {
  if (!item.path) {
    uni.showModal({
      title: '加入我们',
      content: '网鱼电竞长期招聘门店店员与电竞运营，简历请投递至 hr@intcafe.com',
      showCancel: false,
      confirmColor: '#5B5BD6',
    })
    return
  }
  navTo(item.path)
}

function onOrders(): void {
  navTo('/pages/order/index')
}

function onPosts(): void {
  navTo('/pages/community/index')
}

function onGiftBanner(): void {
  uni.showModal({
    title: '添加好友领好礼',
    content: '添加网鱼电竞福利官微信，即可领取最高 328 元优惠券大礼包。',
    confirmText: '复制微信号',
    cancelText: '知道了',
    confirmColor: '#5B5BD6',
    success: (res) => {
      if (res.confirm) {
        uni.setClipboardData({
          data: 'wonyu-fanli',
          success: () => toastSuccess('微信号已复制'),
        })
      }
    },
  })
}
</script>

<template>
  <view class="page-root page-root--has-tabbar mine">
    <!-- ==================== 头部 ==================== -->
    <view class="mine__header">
      <AppStatusBar />
      <view class="mine__profile">
        <view class="mine__user" @tap="onAvatarTap">
          <image
            v-if="user.isLogin && user.avatar"
            class="mine__avatar"
            :src="user.avatar"
            mode="aspectFill"
          />
          <view v-else class="mine__avatar mine__avatar--placeholder">
            <AppIcon name="tab-mine" :size="60" color="#B8BCCB" :stroke-width="1.6" />
          </view>
          <text class="mine__name">{{ user.isLogin ? user.nickname : '微信用户' }}</text>
        </view>

        <view class="mine__actions">
          <view class="mine__action" @tap="onSign">
            <AppIcon
              name="calendar"
              :size="48"
              color="#2B2B3A"
              :stroke-width="1.8"
            />
            <text class="mine__action-text">签到</text>
          </view>
          <view class="mine__action" @tap="onScan">
            <AppIcon name="scan" :size="48" color="#2B2B3A" :stroke-width="1.8" />
            <text class="mine__action-text">扫一扫</text>
          </view>
          <view class="mine__action" @tap="onSettings">
            <AppIcon name="settings" :size="48" color="#2B2B3A" :stroke-width="1.8" />
            <text class="mine__action-text">设置</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ==================== 资产四项 ==================== -->
    <view class="card asset">
      <view class="asset__item" @tap="navTo('/pages/recharge/index')">
        <text class="asset__value">{{ walletText }}</text>
        <text class="asset__label">钱包</text>
      </view>
      <view class="asset__divider" />
      <view class="asset__item" @tap="navTo('/pages/coupon/index')">
        <text class="asset__value">{{ couponText }}</text>
        <text class="asset__label">卡券</text>
      </view>
      <view class="asset__divider" />
      <view class="asset__item" @tap="navTo('/pages/points/index')">
        <text class="asset__value">{{ pointsText }}</text>
        <text class="asset__label">积分</text>
      </view>
      <view class="asset__divider" />
      <view class="asset__item" @tap="navTo('/pages/hotel/index')">
        <text class="asset__value">{{ hotelPointsText }}</text>
        <text class="asset__label">酒店积分</text>
      </view>
    </view>

    <!-- ==================== 我的订单 ==================== -->
    <view class="card block">
      <view class="flex-between" @tap="onOrders">
        <text class="block__title">我的订单</text>
        <view class="block__more">
          <text class="block__more-text">全部订单</text>
          <view class="block__arrow" />
        </view>
      </view>

      <view class="entry-row">
        <view
          v-for="e in ORDER_ENTRIES"
          :key="e.key"
          class="entry"
          @tap="navTo(e.path)"
        >
          <AppIcon :name="e.icon" :size="62" color="#2B2B3A" :stroke-width="1.5" />
          <text class="entry__text">{{ e.title }}</text>
        </view>
      </view>
    </view>

    <!-- ==================== 好友礼包横幅 ==================== -->
    <view class="gift" @tap="onGiftBanner">
      <image class="gift__img" src="/static/images/banner/gift-328.jpg" mode="aspectFill" />
      <view class="gift__content">
        <text class="gift__title">添加好友领</text>
        <view class="gift__row">
          <text class="gift__num">328</text>
          <text class="gift__unit">元大礼包</text>
        </view>
      </view>
    </view>

    <!-- ==================== 我的帖子 ==================== -->
    <view class="card block">
      <view class="flex-between" @tap="onPosts">
        <text class="block__title">我的帖子</text>
      </view>
      <view class="stat-row">
        <view class="stat" @tap="onPosts">
          <text class="stat__value">{{ postStat.published }}</text>
          <text class="stat__label">发布</text>
        </view>
        <view class="stat__divider" />
        <view class="stat" @tap="onPosts">
          <text class="stat__value">{{ postStat.liked }}</text>
          <text class="stat__label">点赞</text>
        </view>
        <view class="stat__divider" />
        <view class="stat" @tap="navTo('/pages/favorite/index')">
          <text class="stat__value">{{ postStat.collected }}</text>
          <text class="stat__label">收藏</text>
        </view>
      </view>
    </view>

    <!-- ==================== 我的工具 ==================== -->
    <view class="card block">
      <text class="block__title">我的工具</text>
      <view class="entry-row entry-row--tools">
        <view v-for="t in TOOLS" :key="t.key" class="entry entry--tool" @tap="onToolTap(t)">
          <AppIcon :name="t.icon" :size="56" color="#2B2B3A" :stroke-width="1.5" />
          <text class="entry__text entry__text--tool">{{ t.title }}</text>
        </view>
      </view>
      <view class="fn-dots">
        <view class="fn-dot is-active" />
        <view class="fn-dot" />
      </view>
    </view>

    <AppTabBar current="mine" />
  </view>
</template>

<style lang="scss" scoped>
.mine {
  background: $page-bg;
}

/* ==================== 头部 ==================== */
.mine__header {
  background: linear-gradient(180deg, #eef0f8 0%, $page-bg 100%);
  padding-bottom: 20rpx;
}

.mine__profile {
  display: flex;
  align-items: center;
  padding: 24rpx $gap-page 20rpx;
}

.mine__user {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;

  &:active {
    opacity: 0.8;
  }
}

.mine__avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: #e4e6ee;
}

.mine__avatar--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.mine__name {
  margin-left: 24rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: $text-primary;
  @include ellipsis;
}

.mine__actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.mine__action {
  width: 108rpx;
  display: flex;
  flex-direction: column;
  align-items: center;

  &:active {
    opacity: 0.6;
  }
}

.mine__action-text {
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-regular;
}

/* ==================== 资产四项 ==================== */
.asset {
  margin: 0 $gap-page;
  display: flex;
  align-items: center;
  /* 参考图实测该卡高 ≈124rpx */
  padding: 26rpx 0;
}

.asset__item {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  &:active {
    opacity: 0.7;
  }
}

.asset__value {
  font-size: 40rpx;
  font-weight: 700;
  color: $text-primary;
  @include ellipsis;
}

.asset__label {
  margin-top: 8rpx;
  font-size: $fs-base;
  color: $text-secondary;
}

.asset__divider {
  width: 1rpx;
  height: 56rpx;
  background: $divider;
  flex-shrink: 0;
}

/* ==================== 通用区块 ==================== */
.block {
  margin: 24rpx $gap-page 0;
  padding: 30rpx 24rpx;
}

.block__title {
  font-size: 38rpx;
  font-weight: 700;
  color: $text-primary;
}

.block__more {
  display: flex;
  align-items: center;

  &:active {
    opacity: 0.6;
  }
}

.block__more-text {
  font-size: $fs-md;
  color: $text-secondary;
}

.block__arrow {
  width: 14rpx;
  height: 14rpx;
  margin-left: 8rpx;
  border-top: 3rpx solid $text-placeholder;
  border-right: 3rpx solid $text-placeholder;
  transform: rotate(45deg);
  border-radius: 2rpx;
}

/* ==================== 入口行 ==================== */
.entry-row {
  display: flex;
  align-items: flex-start;
  margin-top: 30rpx;
}

.entry {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  &:active {
    opacity: 0.65;
  }
}

.entry__text {
  margin-top: 14rpx;
  font-size: $fs-md;
  color: $text-primary;
  max-width: 100%;
  @include ellipsis;
}

/* 工具行文字比订单行小一号，5 个才排得下且不截断 */
.entry-row--tools {
  flex-wrap: wrap;
}

.entry--tool {
  width: 20%;
  flex: none;
}

.entry__text--tool {
  font-size: $fs-sm;
}

/* ==================== 329 礼包横幅 ==================== */
.gift {
  position: relative;
  margin: 24rpx $gap-page 0;
  height: 200rpx;
  border-radius: $radius-lg;
  overflow: hidden;

  &:active {
    opacity: 0.92;
  }
}

.gift__img {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
}

.gift__content {
  position: relative;
  height: 100%;
  padding-left: 40rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.gift__title {
  font-size: 34rpx;
  font-weight: 800;
  color: #b3200f;
  letter-spacing: 1rpx;
  text-shadow: 0 2rpx 0 rgba(255, 255, 255, 0.6);
}

.gift__row {
  display: flex;
  align-items: flex-end;
  margin-top: 4rpx;
}

.gift__num {
  font-size: 76rpx;
  font-weight: 900;
  color: #d81e06;
  line-height: 1;
  text-shadow: 0 3rpx 0 rgba(255, 255, 255, 0.7);
}

.gift__unit {
  margin-left: 8rpx;
  margin-bottom: 8rpx;
  font-size: 34rpx;
  font-weight: 800;
  color: #d81e06;
  text-shadow: 0 2rpx 0 rgba(255, 255, 255, 0.7);
}

/* ==================== 我的帖子 ==================== */
.stat-row {
  display: flex;
  align-items: center;
  margin-top: 26rpx;
}

.stat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  &:active {
    opacity: 0.7;
  }
}

.stat__value {
  font-size: 52rpx;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.1;
}

.stat__label {
  margin-top: 10rpx;
  font-size: $fs-base;
  color: $text-secondary;
}

.stat__divider {
  width: 1rpx;
  height: 60rpx;
  background: $divider;
  flex-shrink: 0;
}

/* ==================== 页面指示点 ==================== */
.fn-dots {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 24rpx;
}

.fn-dot {
  width: 10rpx;
  height: 10rpx;
  margin: 0 5rpx;
  border-radius: 50%;
  background: #d8dae4;

  &.is-active {
    background: $brand-primary;
  }
}
</style>
