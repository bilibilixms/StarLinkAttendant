<script setup lang="ts">
/**
 * 我的订单：全部 / 待支付 / 进行中 / 已完成 / 退款。
 */
import { ref } from 'vue'
import { onLoad, onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { orderApi } from '@/api'
import { ORDER_TABS } from '@/config'
import { useUserStore } from '@/stores/user'
import { navBack, navTo, goLogin } from '@/utils/nav'
import { toast, confirm } from '@/utils/ui'
import { formatMoney, formatDateTime } from '@/utils/format'
import type { Order, OrderTab, OrderStatus } from '@/types/order'

const user = useUserStore()

const tab = ref<OrderTab>('all')
const orders = ref<Order[]>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const page = ref(1)
const hasMore = ref(true)
const loadingMore = ref(false)

const STATUS_STYLE: Record<OrderStatus, string> = {
  0: '#FF9F2E',
  1: '#22C55E',
  2: '#3B9DFF',
  3: '#8A8A99',
  4: '#8A8A99',
}

async function load(reset = false): Promise<void> {
  if (reset) {
    page.value = 1
    hasMore.value = true
  }
  if (state.value !== 'success') state.value = 'loading'
  try {
    const res = await orderApi.getOrders({ tab: tab.value, current: page.value, size: 10 })
    orders.value = page.value === 1 ? res.records : [...orders.value, ...res.records]
    hasMore.value = page.value < res.pages
    state.value = orders.value.length ? 'success' : 'empty'
  } catch {
    if (!orders.value.length) state.value = 'error'
  }
}

function switchTab(next: OrderTab): void {
  if (tab.value === next) return
  tab.value = next
  orders.value = []
  void load(true)
}

onLoad((query) => {
  const t = query?.tab as OrderTab | undefined
  if (t && ORDER_TABS.some((x) => x.key === t)) tab.value = t
  void load(true)
})

onShow(() => {
  if (!user.isLogin) {
    state.value = 'empty'
    return
  }
  if (orders.value.length) void load(true)
})

onPullDownRefresh(async () => {
  await load(true)
  uni.stopPullDownRefresh()
})

onReachBottom(async () => {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  page.value += 1
  await load()
  loadingMore.value = false
})

/* ==================== 操作 ==================== */

function goDetail(o: Order): void {
  navTo(`/pages/order/detail?id=${o.id}`)
}

async function onPay(o: Order): Promise<void> {
  const ok = await confirm(`确认支付 ¥${formatMoney(o.payableAmount)}？`, '支付订单', '确认支付')
  if (!ok) return
  try {
    await orderApi.payOrder(o.id, 'wechat')
    toast('支付成功')
    void load(true)
  } catch {
    /* request 层已提示 */
  }
}

async function onCancel(o: Order): Promise<void> {
  const ok = await confirm('确定要取消这笔订单吗？', '取消订单', '确定取消')
  if (!ok) return
  try {
    await orderApi.cancelOrder(o.id)
    toast('订单已取消')
    void load(true)
  } catch {
    /* request 层已提示 */
  }
}

function onRebuy(o: Order): void {
  navTo('/pages/product/index')
}

async function onRefund(o: Order): Promise<void> {
  const ok = await confirm('确定要申请退款吗？退款将原路退回。', '申请退款', '申请退款')
  if (!ok) return
  try {
    await orderApi.refundOrder(o.id, '用户申请退款')
    toast('退款申请已提交')
    void load(true)
  } catch {
    /* request 层已提示 */
  }
}

function statusText(o: Order): string {
  if (o.statusText) return o.statusText
  return { 0: '待支付', 1: '已完成', 2: '部分退款', 3: '已退款', 4: '已取消' }[o.status] ?? ''
}
</script>

<template>
  <view class="orders page-root">
    <AppNavBar title="我的订单" show-back @back="navBack" />

    <!-- ==================== 状态筛选 ==================== -->
    <view class="tabs">
      <view
        v-for="t in ORDER_TABS"
        :key="t.key"
        class="tab"
        :class="{ 'is-active': tab === t.key }"
        @tap="switchTab(t.key as OrderTab)"
      >
        <text class="tab__text">{{ t.text }}</text>
        <view v-if="tab === t.key" class="tab__bar" />
      </view>
    </view>

    <!-- ==================== 未登录 ==================== -->
    <StateView
      v-if="!user.isLogin"
      state="empty"
      empty-text="登录后查看订单"
      empty-desc="登录网鱼会员即可查看历史订单"
      empty-icon="empty-order"
    >
      <template #action>
        <AppButton type="primary" size="md" @tap="goLogin('/pages/order/index')">立即登录</AppButton>
      </template>
    </StateView>

    <!-- ==================== 列表 ==================== -->
    <template v-else>
      <StateView v-if="state !== 'success'" :state="state" @retry="load(true)" />

      <view v-else class="list">
        <view v-for="o in orders" :key="o.id" class="oc" @tap="goDetail(o)">
          <!-- 头部 -->
          <view class="oc__head">
            <text class="oc__no">订单号 {{ o.orderNo }}</text>
            <text class="oc__status" :style="{ color: STATUS_STYLE[o.status] }">
              {{ statusText(o) }}
            </text>
          </view>

          <!-- 商品 -->
          <view v-for="item in o.items" :key="item.id" class="oi">
            <image class="oi__img" :src="item.cover" mode="aspectFill" />
            <view class="oi__info">
              <text class="oi__name">{{ item.productName }}</text>
              <text v-if="item.spec" class="oi__spec">{{ item.spec }}</text>
            </view>
            <view class="oi__right">
              <text class="oi__price">¥{{ formatMoney(item.price) }}</text>
              <text class="oi__qty">×{{ item.quantity }}</text>
            </view>
          </view>

          <!-- 合计 -->
          <view class="oc__foot">
            <text class="oc__time">{{ formatDateTime(o.createdAt, true) }}</text>
            <view class="oc__amount">
              <text class="oc__amount-label">实付</text>
              <text class="oc__amount-value">¥{{ formatMoney(o.paidAmount || o.payableAmount) }}</text>
            </view>
          </view>

          <!-- 操作 -->
          <view class="oc__actions" @tap.stop>
            <template v-if="o.status === 0">
              <AppButton type="ghost" size="sm" @tap="onCancel(o)">取消订单</AppButton>
              <AppButton type="primary" size="sm" class="oc__btn" @tap="onPay(o)">去支付</AppButton>
            </template>
            <template v-else-if="o.status === 1">
              <AppButton type="ghost" size="sm" @tap="onRefund(o)">申请退款</AppButton>
              <AppButton type="primary" size="sm" class="oc__btn" @tap="onRebuy(o)">再次购买</AppButton>
            </template>
            <template v-else>
              <AppButton type="soft" size="sm" @tap="goDetail(o)">查看详情</AppButton>
            </template>
          </view>
        </view>

        <view class="list__foot">
          <text class="list__foot-text">
            {{ hasMore ? (loadingMore ? '加载中...' : '上拉加载更多') : '没有更多订单了' }}
          </text>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.orders {
  padding-bottom: 40rpx;
  @include safe-area-bottom(40rpx);
}

/* ==================== 筛选 ==================== */
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

.oc {
  margin-bottom: 20rpx;
  padding: 26rpx 24rpx;
  background: #ffffff;
  border-radius: $radius-lg;

  &:active {
    opacity: 0.95;
  }
}

.oc__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 20rpx;
  border-bottom: 1rpx solid $divider;
}

.oc__no {
  font-size: $fs-sm;
  color: $text-placeholder;
  @include ellipsis;
}

.oc__status {
  font-size: $fs-md;
  font-weight: 600;
  flex-shrink: 0;
  margin-left: 16rpx;
}

/* ---------- 商品行 ---------- */
.oi {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
}

.oi__img {
  width: 110rpx;
  height: 110rpx;
  border-radius: $radius-sm;
  flex-shrink: 0;
  background: $card-bg-soft;
}

.oi__info {
  flex: 1;
  min-width: 0;
  margin: 0 18rpx;
}

.oi__name {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis;
}

.oi__spec {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.oi__right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.oi__price {
  font-size: $fs-md;
  color: $text-primary;
}

.oi__qty {
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

/* ---------- 底部 ---------- */
.oc__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 18rpx;
  border-top: 1rpx solid $divider;
}

.oc__time {
  font-size: $fs-sm;
  color: $text-placeholder;
}

.oc__amount {
  display: flex;
  align-items: baseline;
}

.oc__amount-label {
  font-size: $fs-sm;
  color: $text-secondary;
}

.oc__amount-value {
  margin-left: 8rpx;
  font-size: 34rpx;
  font-weight: 700;
  color: $danger;
}

/* ---------- 操作 ---------- */
.oc__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-top: 22rpx;
}

.oc__btn {
  margin-left: 18rpx;
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
