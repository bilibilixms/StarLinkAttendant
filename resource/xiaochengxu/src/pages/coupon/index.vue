<script setup lang="ts">
/**
 * 我的卡券 + 领券中心。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { couponApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { navBack, navTo, goLogin } from '@/utils/nav'
import { toast, toastSuccess } from '@/utils/ui'
import { formatMoney } from '@/utils/format'
import type { Coupon, CouponTemplate } from '@/types/coupon'

const user = useUserStore()

type Tab = 'unused' | 'used' | 'expired' | 'center'
const TAB_LIST: Array<{ key: Tab; text: string }> = [
  { key: 'unused', text: '未使用' },
  { key: 'used', text: '已使用' },
  { key: 'expired', text: '已过期' },
  { key: 'center', text: '领券中心' },
]

const tab = ref<Tab>('unused')
const coupons = ref<Coupon[]>([])
const templates = ref<CouponTemplate[]>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const receivingId = ref(0)

const statusOfTab: Record<Exclude<Tab, 'center'>, number> = {
  unused: 0,
  used: 1,
  expired: 2,
}

const list = computed(() => (tab.value === 'center' ? [] : coupons.value))

async function load(): Promise<void> {
  if (!user.isLogin) {
    state.value = 'empty'
    return
  }
  state.value = 'loading'
  try {
    if (tab.value === 'center') {
      templates.value = await couponApi.getCouponTemplates()
      state.value = templates.value.length ? 'success' : 'empty'
    } else {
      coupons.value = await couponApi.getMyCoupons({ status: statusOfTab[tab.value] })
      state.value = coupons.value.length ? 'success' : 'empty'
    }
  } catch {
    state.value = 'error'
  }
}

function switchTab(next: Tab): void {
  if (tab.value === next) return
  tab.value = next
  void load()
}

onLoad(() => {
  void load()
})

/* ==================== 交互 ==================== */

function couponValue(c: Coupon | CouponTemplate): string {
  return c.type === 1 ? `¥${formatMoney(c.value)}` : `${c.value}折`
}

function couponThreshold(c: Coupon | CouponTemplate): string {
  return c.minAmount > 0 ? `满${c.minAmount}可用` : '无门槛'
}

async function onReceive(t: CouponTemplate): Promise<void> {
  if (receivingId.value) return
  receivingId.value = t.id
  try {
    await couponApi.receiveCoupon(t.id)
    toastSuccess('领取成功')
    void load()
  } catch {
    /* request 层已提示 */
  } finally {
    receivingId.value = 0
  }
}

function onUse(c: Coupon): void {
  if (c.scope === 2) navTo('/pages/recharge/index')
  else if (c.scope === 3) navTo('/pages/product/index')
  else if (c.scope === 4) navTo('/pages/hotel/index')
  else navTo('/pages/product/index')
}

function receivedCount(t: CouponTemplate): number {
  return t.receivedCount ?? 0
}
</script>

<template>
  <view class="cp page-root">
    <AppNavBar title="我的卡券" show-back @back="navBack" />

    <!-- ==================== Tab ==================== -->
    <view class="tabs">
      <view
        v-for="t in TAB_LIST"
        :key="t.key"
        class="tab"
        :class="{ 'is-active': tab === t.key }"
        @tap="switchTab(t.key)"
      >
        <text class="tab__text">{{ t.text }}</text>
        <view v-if="tab === t.key" class="tab__bar" />
      </view>
    </view>

    <!-- ==================== 未登录 ==================== -->
    <StateView
      v-if="!user.isLogin"
      state="empty"
      empty-text="登录后查看卡券"
      empty-desc="登录网鱼会员即可领取优惠券"
      empty-icon="empty-coupon"
    >
      <template #action>
        <AppButton type="primary" size="md" @tap="goLogin('/pages/coupon/index')">立即登录</AppButton>
      </template>
    </StateView>

    <template v-else>
      <StateView
        v-if="state !== 'success'"
        :state="state"
        :empty-text="tab === 'center' ? '暂无可领取的优惠券' : '这里还没有卡券'"
        empty-icon="empty-coupon"
        @retry="load"
      />

      <!-- ==================== 我的券 ==================== -->
      <view v-else-if="tab !== 'center'" class="list">
        <view
          v-for="c in list"
          :key="c.id"
          class="cc"
          :class="{ 'is-dim': tab !== 'unused' }"
        >
          <view class="cc__left">
            <text class="cc__value">{{ couponValue(c) }}</text>
            <text class="cc__threshold">{{ couponThreshold(c) }}</text>
          </view>

          <view class="cc__divider">
            <view class="cc__notch cc__notch--top" />
            <view class="cc__dash" />
            <view class="cc__notch cc__notch--bottom" />
          </view>

          <view class="cc__mid">
            <text class="cc__name">{{ c.name }}</text>
            <text class="cc__scope">{{ c.scopeText }}</text>
            <text class="cc__date">有效期至 {{ c.endTime.slice(0, 10) }}</text>
          </view>

          <view class="cc__right">
            <AppButton v-if="tab === 'unused'" type="primary" size="sm" @tap="onUse(c)">
              去使用
            </AppButton>
            <text v-else class="cc__status">{{ tab === 'used' ? '已使用' : '已过期' }}</text>
          </view>
        </view>

        <view v-if="tab === 'unused'" class="cp__more" @tap="switchTab('center')">
          <text class="cp__more-text">去领券中心看看 ›</text>
        </view>
      </view>

      <!-- ==================== 领券中心 ==================== -->
      <view v-else class="list">
        <view v-for="t in templates" :key="t.id" class="tc">
          <view class="tc__left">
            <text class="tc__value">{{ couponValue(t) }}</text>
            <text class="tc__threshold">{{ couponThreshold(t) }}</text>
          </view>

          <view class="tc__mid">
            <text class="tc__name">{{ t.name }}</text>
            <text class="tc__desc">{{ t.description || t.scopeText }}</text>
            <text class="tc__date">有效期至 {{ t.endTime.slice(0, 10) }}</text>
          </view>

          <view class="tc__right">
            <AppButton
              :type="receivedCount(t) >= t.limitPerMember ? 'soft' : 'primary'"
              size="sm"
              :loading="receivingId === t.id"
              :disabled="receivedCount(t) >= t.limitPerMember"
              @tap="onReceive(t)"
            >
              {{ receivedCount(t) >= t.limitPerMember ? '已领取' : '领取' }}
            </AppButton>
            <text class="tc__remain">剩余 {{ t.remainCount }} 张</text>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.cp {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

/* ==================== Tab ==================== */
.tabs {
  display: flex;
  align-items: center;
  height: 92rpx;
  padding: 0 $gap-page;
  background: #ffffff;
  border-bottom: 1rpx solid $divider;
  position: sticky;
  top: 0;
  z-index: 50;
}

.tab {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: center;
  padding-bottom: 6rpx;

  &:active {
    opacity: 0.7;
  }
}

.tab__text {
  font-size: $fs-md;
  color: $text-secondary;
}

.tab.is-active .tab__text {
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
}

.tab__bar {
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 40rpx;
  height: 6rpx;
  margin-left: -20rpx;
  border-radius: 3rpx;
  background: $brand-primary;
}

/* ==================== 列表 ==================== */
.list {
  padding: 20rpx $gap-page 0;
}

/* ---------- 我的券 ---------- */
.cc {
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
  padding: 26rpx 20rpx;
  background: #ffffff;
  border-radius: $radius-lg;

  &.is-dim {
    opacity: 0.55;
  }
}

.cc__left {
  width: 160rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cc__value {
  font-size: 46rpx;
  font-weight: 800;
  color: $danger;
  line-height: 1.1;
}

.cc__threshold {
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-secondary;
}

.cc__divider {
  position: relative;
  width: 1rpx;
  align-self: stretch;
  margin: 0 20rpx;
  flex-shrink: 0;
}

.cc__dash {
  position: absolute;
  left: 0;
  top: 10rpx;
  bottom: 10rpx;
  width: 2rpx;
  background: repeating-linear-gradient(
    180deg,
    $divider 0,
    $divider 10rpx,
    transparent 10rpx,
    transparent 20rpx
  );
}

.cc__notch {
  position: absolute;
  left: -12rpx;
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
  background: $page-bg;
}

.cc__notch--top {
  top: -38rpx;
}

.cc__notch--bottom {
  bottom: -38rpx;
}

.cc__mid {
  flex: 1;
  min-width: 0;
}

.cc__name {
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
  @include ellipsis;
}

.cc__scope {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-secondary;
  @include ellipsis;
}

.cc__date {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.cc__right {
  flex-shrink: 0;
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cc__status {
  font-size: $fs-base;
  color: $text-placeholder;
}

.cp__more {
  display: flex;
  justify-content: center;
  padding: 30rpx 0 10rpx;

  &:active {
    opacity: 0.6;
  }
}

.cp__more-text {
  font-size: $fs-md;
  color: $brand-primary;
}

/* ---------- 领券中心 ---------- */
.tc {
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
  padding: 26rpx 20rpx;
  background: #ffffff;
  border-radius: $radius-lg;
}

.tc__left {
  width: 150rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.tc__value {
  font-size: 46rpx;
  font-weight: 800;
  color: $danger;
  line-height: 1.1;
}

.tc__threshold {
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-secondary;
}

.tc__mid {
  flex: 1;
  min-width: 0;
  margin: 0 20rpx;
}

.tc__name {
  font-size: $fs-md;
  font-weight: 700;
  color: $text-primary;
  @include ellipsis;
}

.tc__desc {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-secondary;
  @include ellipsis;
}

.tc__date {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.tc__right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.tc__remain {
  margin-top: 10rpx;
  font-size: $fs-xs;
  color: $text-placeholder;
}
</style>
