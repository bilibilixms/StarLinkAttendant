<script setup lang="ts">
/**
 * 消费记录：上机 / 点单 / 充值 三类流水聚合。
 */
import { ref } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { orderApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { navBack, goLogin } from '@/utils/nav'
import { formatMoney, formatDateTime } from '@/utils/format'
import type { ConsumeRecord } from '@/types/order'

const user = useUserStore()

type Tab = 'all' | 'session' | 'order' | 'recharge'
const TABS: Array<{ key: Tab; text: string }> = [
  { key: 'all', text: '全部' },
  { key: 'session', text: '上机' },
  { key: 'order', text: '点单' },
  { key: 'recharge', text: '充值' },
]

const tab = ref<Tab>('all')
const records = ref<ConsumeRecord[]>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const page = ref(1)
const hasMore = ref(true)
const loadingMore = ref(false)

const ICON_OF: Record<string, string> = {
  session: 'svc-power',
  order: 'svc-food',
  recharge: 'svc-wallet',
}

const COLOR_OF: Record<string, string> = {
  session: '#4A9BFF',
  order: '#FF9F43',
  recharge: '#3AC6C6',
}

/** 图标底色（避免用 8 位 hex 做透明度，小程序渲染器支持不稳定） */
const BG_OF: Record<string, string> = {
  session: '#EAF3FF',
  order: '#FFF4E6',
  recharge: '#E6F7F7',
}

async function load(reset = false): Promise<void> {
  if (!user.isLogin) {
    state.value = 'empty'
    return
  }
  if (reset) {
    page.value = 1
    hasMore.value = true
  }
  if (state.value !== 'success') state.value = 'loading'
  try {
    const res = await orderApi.getConsumeRecords({
      current: page.value,
      size: 15,
      type: tab.value === 'all' ? undefined : tab.value,
    })
    records.value = page.value === 1 ? res.records : [...records.value, ...res.records]
    hasMore.value = page.value < res.pages
    state.value = records.value.length ? 'success' : 'empty'
  } catch {
    if (!records.value.length) state.value = 'error'
  }
}

function switchTab(next: Tab): void {
  if (tab.value === next) return
  tab.value = next
  records.value = []
  void load(true)
}

onLoad(() => {
  void load(true)
})

onReachBottom(async () => {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  page.value += 1
  await load()
  loadingMore.value = false
})

/** 月度汇总 */
function monthTotal(): { income: number; expense: number } {
  const now = new Date()
  const prefix = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  let income = 0
  let expense = 0
  records.value.forEach((r) => {
    if (!r.createdAt.startsWith(prefix)) return
    if (r.amount >= 0) income += r.amount
    else expense += -r.amount
  })
  return { income: Math.round(income * 100) / 100, expense: Math.round(expense * 100) / 100 }
}
</script>

<template>
  <view class="cs page-root">
    <AppNavBar title="消费记录" show-back @back="navBack" />

    <!-- ==================== 汇总 ==================== -->
    <view v-if="user.isLogin" class="cs__summary">
      <view class="cs__summary-item">
        <text class="cs__summary-label">本月支出</text>
        <text class="cs__summary-value">¥{{ formatMoney(monthTotal().expense) }}</text>
      </view>
      <view class="cs__summary-divider" />
      <view class="cs__summary-item">
        <text class="cs__summary-label">本月充值</text>
        <text class="cs__summary-value cs__summary-value--income">
          ¥{{ formatMoney(monthTotal().income) }}
        </text>
      </view>
    </view>

    <!-- ==================== Tab ==================== -->
    <view class="tabs">
      <view
        v-for="t in TABS"
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
      empty-text="登录后查看消费记录"
      empty-desc="包括上机、点单与充值流水"
      empty-icon="empty-order"
    >
      <template #action>
        <AppButton type="primary" size="md" @tap="goLogin('/pages/consume/index')">立即登录</AppButton>
      </template>
    </StateView>

    <template v-else>
      <StateView v-if="state !== 'success'" :state="state" empty-text="还没有消费记录" @retry="load(true)" />

      <view v-else class="list">
        <view v-for="r in records" :key="r.id" class="cr">
          <view class="cr__icon" :style="{ backgroundColor: BG_OF[r.type] || '#F5F7FB' }">
            <AppIcon :name="ICON_OF[r.type] || 'empty-box'" :size="42" :color="COLOR_OF[r.type] || '#8A8A99'" />
          </view>

          <view class="cr__info">
            <text class="cr__title">{{ r.title }}</text>
            <text class="cr__subtitle">{{ r.subtitle }}</text>
            <text class="cr__time">{{ formatDateTime(r.createdAt, true) }}</text>
          </view>

          <view class="cr__right">
            <text class="cr__amount" :class="r.amount >= 0 ? 'is-income' : 'is-expense'">
              {{ r.amount >= 0 ? '+' : '-' }}¥{{ formatMoney(Math.abs(r.amount)) }}
            </text>
            <text v-if="r.orderNo" class="cr__no">{{ r.orderNo }}</text>
          </view>
        </view>

        <view class="list__foot">
          <text class="list__foot-text">
            {{ hasMore ? (loadingMore ? '加载中...' : '上拉加载更多') : '没有更多记录了' }}
          </text>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.cs {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

/* ==================== 汇总 ==================== */
.cs__summary {
  display: flex;
  align-items: center;
  margin: 20rpx $gap-page 0;
  padding: 32rpx 24rpx;
  border-radius: $radius-lg;
  background: #ffffff;
}

.cs__summary-item {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cs__summary-label {
  font-size: $fs-base;
  color: $text-secondary;
}

.cs__summary-value {
  margin-top: 10rpx;
  font-size: 42rpx;
  font-weight: 800;
  color: $danger;
}

.cs__summary-value--income {
  color: $success;
}

.cs__summary-divider {
  width: 1rpx;
  height: 60rpx;
  background: $divider;
  flex-shrink: 0;
}

/* ==================== Tab ==================== */
.tabs {
  display: flex;
  align-items: center;
  height: 92rpx;
  padding: 0 $gap-page;
  margin-top: 20rpx;
  background: #ffffff;
  border-bottom: 1rpx solid $divider;
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
  padding: 8rpx $gap-page 0;
}

.cr {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-of-type {
    border-bottom: none;
  }
}

.cr__icon {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cr__info {
  flex: 1;
  min-width: 0;
  margin: 0 20rpx;
}

.cr__title {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis;
}

.cr__subtitle {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-secondary;
  @include ellipsis;
}

.cr__time {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.cr__right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.cr__amount {
  font-size: 34rpx;
  font-weight: 700;
}

.cr__amount.is-income {
  color: $success;
}

.cr__amount.is-expense {
  color: $text-primary;
}

.cr__no {
  margin-top: 6rpx;
  font-size: $fs-xs;
  color: $text-placeholder;
}

.list__foot {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30rpx 0 10rpx;
}

.list__foot-text {
  font-size: $fs-base;
  color: $text-placeholder;
}
</style>
