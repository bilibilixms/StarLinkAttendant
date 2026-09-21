<script setup lang="ts">
/**
 * 设置。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import { USE_MOCK } from '@/api/request'
import { resetMockDB } from '@/mock/db'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { navBack, navTo, reLaunch, goLogin } from '@/utils/nav'
import { eventChecked } from '@/utils/event'
import { toastSuccess, confirm } from '@/utils/ui'
import { STORAGE_KEYS } from '@/config'
import { maskPhone } from '@/utils/format'

const user = useUserStore()
const cart = useCartStore()

const notifyEnabled = ref(true)
const cacheSize = ref('计算中...')

const APP_VERSION = '1.0.0'

onLoad(() => {
  void calcCache()
})

/** 估算本地缓存占用 */
function calcCache(): void {
  try {
    const info = uni.getStorageInfoSync()
    const kb = info.currentSize ?? 0
    cacheSize.value = kb > 1024 ? `${(kb / 1024).toFixed(2)} MB` : `${kb} KB`
  } catch {
    cacheSize.value = '未知'
  }
}

async function onClearCache(): Promise<void> {
  const ok = await confirm('清除缓存不会退出登录，但会清空购物车。', '清除缓存', '清除')
  if (!ok) return
  cart.clear()
  try {
    uni.removeStorageSync(STORAGE_KEYS.SEARCH_HISTORY)
  } catch {
    /* ignore */
  }
  void calcCache()
  toastSuccess('缓存已清除')
}

async function onResetDemo(): Promise<void> {
  const ok = await confirm(
    '将把余额、订单、优惠券、上机会话等全部恢复到初始演示数据，确定继续吗？',
    '重置演示数据',
    '重置'
  )
  if (!ok) return
  resetMockDB()
  cart.clear()
  user.clearLocal()
  toastSuccess('演示数据已重置')
  setTimeout(() => reLaunch('/pages/index/index'), 800)
}

async function onLogout(): Promise<void> {
  const ok = await confirm('确定要退出登录吗？', '退出登录', '退出')
  if (!ok) return
  await user.logout()
  cart.clear()
  toastSuccess('已退出登录')
  setTimeout(() => reLaunch('/pages/mine/index'), 700)
}

/** 通知开关变更 */
function onNotifyChange(e: Event): void {
  notifyEnabled.value = eventChecked(e)
}

function onAbout(): void {
  uni.showModal({
    title: '关于星络灵侍馆',
    content: `版本 ${APP_VERSION}\n星络灵侍馆管理系统 · 会员用户端\n\n本小程序为演示项目，用于展示上机、充值、点单、领券等会员自助流程。`,
    showCancel: false,
    confirmColor: '#5B5BD6',
  })
}

function onSecurity(): void {
  uni.showModal({
    title: '账号安全',
    content: user.isLogin
      ? `当前账号：${maskPhone(user.member?.phone)}\n\n修改密码请联系门店前台，或在门店自助机办理。`
      : '请先登录后再查看账号安全设置。',
    showCancel: !user.isLogin,
    confirmText: user.isLogin ? '知道了' : '去登录',
    confirmColor: '#5B5BD6',
    success: (res) => {
      if (res.confirm && !user.isLogin) goLogin('/pages/settings/index')
    },
  })
}
</script>

<template>
  <view class="st page-root">
    <AppNavBar title="设置" show-back @back="navBack" />

    <!-- ==================== 账号 ==================== -->
    <view class="card sec">
      <view class="row row--tap" @tap="onSecurity">
        <AppIcon name="settings" :size="40" color="#5B5BD6" :stroke-width="1.7" />
        <text class="row__label">账号与安全</text>
        <view class="row__right">
          <text class="row__value">{{ user.isLogin ? maskPhone(user.member?.phone) : '未登录' }}</text>
          <view class="row__arrow" />
        </view>
      </view>

      <view v-if="user.isLogin" class="row row--tap" @tap="navTo('/pages/profile/index')">
        <AppIcon name="edit" :size="40" color="#5B5BD6" :stroke-width="1.7" />
        <text class="row__label">个人资料</text>
        <view class="row__right">
          <view class="row__arrow" />
        </view>
      </view>
    </view>

    <!-- ==================== 通用 ==================== -->
    <view class="card sec">
      <view class="row">
        <AppIcon name="comment" :size="40" color="#FF9F43" :stroke-width="1.7" />
        <text class="row__label">接收活动通知</text>
        <view class="row__right">
          <switch
            :checked="notifyEnabled"
            color="#5B5BD6"
            style="transform: scale(0.8)"
            @change="onNotifyChange"
          />
        </view>
      </view>

      <view class="row row--tap" @tap="onClearCache">
        <AppIcon name="refresh" :size="40" color="#3AC6C6" :stroke-width="1.7" />
        <text class="row__label">清除缓存</text>
        <view class="row__right">
          <text class="row__value">{{ cacheSize }}</text>
          <view class="row__arrow" />
        </view>
      </view>

      <view class="row row--tap" @tap="navTo('/pages/feedback/index')">
        <AppIcon name="svc-chat" :size="40" color="#67C23A" :stroke-width="1.7" />
        <text class="row__label">意见反馈</text>
        <view class="row__right">
          <view class="row__arrow" />
        </view>
      </view>

      <view class="row row--tap" @tap="navTo('/pages/service/customer')">
        <AppIcon name="headset" :size="40" color="#4A9BFF" :stroke-width="1.7" />
        <text class="row__label">在线客服</text>
        <view class="row__right">
          <view class="row__arrow" />
        </view>
      </view>
    </view>

    <!-- ==================== 开发者选项 ==================== -->
    <view v-if="USE_MOCK" class="card sec">
      <view class="dev__title">
        <AppIcon name="warning" :size="30" color="#FF9F2E" :stroke-width="1.7" />
        <text class="dev__title-text">开发者选项</text>
      </view>
      <text class="dev__desc">
        当前 VITE_USE_MOCK=true，全部数据来自本地 Mock 层，与真实后端无关。
      </text>
      <AppButton type="ghost" size="md" block class="dev__btn" @tap="onResetDemo">
        重置演示数据
      </AppButton>
      <text class="dev__desc dev__desc--sm">
        重置后余额回到 ¥860.00，订单 / 优惠券 / 上机会话恢复初始状态。
      </text>
    </view>

    <!-- ==================== 关于 ==================== -->
    <view class="card sec">
      <view class="row row--tap" @tap="onAbout">
        <AppIcon name="question" :size="40" color="#8A8A99" :stroke-width="1.7" />
        <text class="row__label">关于我们</text>
        <view class="row__right">
          <text class="row__value">v{{ APP_VERSION }}</text>
          <view class="row__arrow" />
        </view>
      </view>
    </view>

    <!-- ==================== 退出 ==================== -->
    <view v-if="user.isLogin" class="st__actions">
      <AppButton type="ghost" size="lg" block @tap="onLogout">退出登录</AppButton>
    </view>

    <text class="st__footer">星络灵侍馆 · 星络灵侍馆管理系统 v{{ APP_VERSION }}</text>
  </view>
</template>

<style lang="scss" scoped>
.st {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

.sec {
  margin: 20rpx $gap-page 0;
  padding: 8rpx 24rpx 24rpx;
}

.row {
  display: flex;
  align-items: center;
  min-height: 104rpx;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.row--tap:active {
  opacity: 0.7;
}

.row__label {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
  font-size: $fs-md;
  color: $text-primary;
}

.row__right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.row__value {
  font-size: $fs-md;
  color: $text-secondary;
}

.row__arrow {
  width: 14rpx;
  height: 14rpx;
  margin-left: 12rpx;
  border-top: 3rpx solid $text-placeholder;
  border-right: 3rpx solid $text-placeholder;
  transform: rotate(45deg);
  border-radius: 2rpx;
}

/* ==================== 开发者选项 ==================== */
.dev__title {
  display: flex;
  align-items: center;
  padding: 24rpx 0 12rpx;
}

.dev__title-text {
  margin-left: 10rpx;
  font-size: $fs-md;
  font-weight: 700;
  color: #a8681a;
}

.dev__desc {
  display: block;
  font-size: $fs-sm;
  color: $text-secondary;
  line-height: 1.6;
}

.dev__desc--sm {
  margin-top: 12rpx;
  font-size: $fs-xs;
  color: $text-placeholder;
}

.dev__btn {
  margin: 20rpx 0 4rpx;
}

/* ==================== 底部 ==================== */
.st__actions {
  padding: 44rpx $gap-page 0;
}

.st__footer {
  display: block;
  margin-top: 40rpx;
  text-align: center;
  font-size: $fs-sm;
  color: $text-placeholder;
}
</style>
