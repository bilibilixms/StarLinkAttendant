<script setup lang="ts">
/**
 * 订单详情。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { orderApi } from '@/api'
import { navBack, navTo } from '@/utils/nav'
import { toast, toastSuccess, confirm } from '@/utils/ui'
import { formatMoney, formatDateTime } from '@/utils/format'
import type { Order } from '@/types/order'

const order = ref<Order | null>(null)
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const orderId = ref(0)
/** 从确认页跳过来时带 paid=1，展示一次成功提示 */
const justPaid = ref(false)
const acting = ref(false)

const statusTitle = computed(() => {
  const o = order.value
  if (!o) return ''
  if (o.statusText) return o.statusText
  return { 0: '待支付', 1: '已完成', 2: '部分退款', 3: '已退款', 4: '已取消' }[o.status] ?? ''
})

const statusDesc = computed(() => {
  const o = order.value
  if (!o) return ''
  if (o.status === 0) return '请在 15 分钟内完成支付，超时订单将自动取消'
  if (o.status === 1) return o.orderType === 1 ? '吧台正在备餐，出餐后送达您的机位' : '订单已完成，感谢惠顾'
  if (o.status === 3) return '退款已原路退回，到账时间以支付渠道为准'
  if (o.status === 4) return '订单已取消'
  return ''
})

async function load(id: number): Promise<void> {
  state.value = 'loading'
  try {
    order.value = await orderApi.getOrderDetail(id)
    state.value = 'success'
  } catch {
    state.value = 'error'
  }
}

onLoad((query) => {
  orderId.value = Number(query?.id ?? 0)
  justPaid.value = String(query?.paid ?? '') === '1'
  if (!orderId.value) {
    state.value = 'error'
    return
  }
  void load(orderId.value)
  if (justPaid.value) toastSuccess('支付成功')
})

/* ==================== 操作 ==================== */

async function onPay(): Promise<void> {
  if (!order.value || acting.value) return
  const ok = await confirm(`确认支付 ¥${formatMoney(order.value.payableAmount)}？`, '支付订单', '确认支付')
  if (!ok) return
  acting.value = true
  try {
    await orderApi.payOrder(order.value.id, 'wechat')
    toastSuccess('支付成功')
    void load(orderId.value)
  } catch {
    /* request 层已提示 */
  } finally {
    acting.value = false
  }
}

async function onCancel(): Promise<void> {
  if (!order.value) return
  const ok = await confirm('确定要取消这笔订单吗？', '取消订单', '确定取消')
  if (!ok) return
  try {
    await orderApi.cancelOrder(order.value.id)
    toast('订单已取消')
    void load(orderId.value)
  } catch {
    /* request 层已提示 */
  }
}

async function onRefund(): Promise<void> {
  if (!order.value) return
  const ok = await confirm('确定要申请退款吗？', '申请退款', '申请退款')
  if (!ok) return
  try {
    await orderApi.refundOrder(order.value.id, '用户申请退款')
    toast('退款申请已提交')
    void load(orderId.value)
  } catch {
    /* request 层已提示 */
  }
}

function onRebuy(): void {
  navTo('/pages/product/index')
}

function copyOrderNo(orderNo: string): void {
  uni.setClipboardData({ data: orderNo, success: () => toast('订单号已复制') })
}
</script>

<template>
  <view class="od page-root">
    <AppNavBar title="订单详情" show-back @back="navBack" />

    <StateView v-if="state !== 'success'" :state="state" @retry="load(orderId)" />

    <template v-else-if="order">
      <!-- ==================== 状态 ==================== -->
      <view class="card od__status">
        <view class="od__status-row">
          <text class="od__status-title">{{ statusTitle }}</text>
          <AppIcon
            :name="order.status === 0 ? 'clock' : order.status === 1 ? 'check' : 'warning'"
            :size="52"
            :color="order.status === 0 ? '#FF9F2E' : order.status === 1 ? '#22C55E' : '#8A8A99'"
            :stroke-width="1.8"
          />
        </view>
        <text v-if="statusDesc" class="od__status-desc">{{ statusDesc }}</text>
      </view>

      <!-- ==================== 配送 ==================== -->
      <view class="card od__sec">
        <view class="od__row">
          <AppIcon name="location" :size="32" color="#5B5BD6" />
          <text class="od__row-text">
            {{ order.storeName }}{{ order.seatNo ? ` · ${order.seatNo} 机位` : '' }}
          </text>
        </view>
      </view>

      <!-- ==================== 商品 ==================== -->
      <view class="card od__sec">
        <text class="od__sec-title">商品清单</text>
        <view v-for="item in order.items" :key="item.id" class="oi">
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
      </view>

      <!-- ==================== 金额 ==================== -->
      <view class="card od__sec">
        <view class="amt">
          <text class="amt__label">商品金额</text>
          <text class="amt__value">¥{{ formatMoney(order.totalAmount) }}</text>
        </view>
        <view v-if="order.discountAmount > 0" class="amt">
          <text class="amt__label">优惠</text>
          <text class="amt__value amt__value--cut">-¥{{ formatMoney(order.discountAmount) }}</text>
        </view>
        <view class="amt amt--total">
          <text class="amt__label">实付金额</text>
          <text class="amt__value amt__value--total">
            ¥{{ formatMoney(order.paidAmount || order.payableAmount) }}
          </text>
        </view>
      </view>

      <!-- ==================== 订单信息 ==================== -->
      <view class="card od__sec">
        <view class="od__row od__row--between">
          <text class="od__row-label">订单编号</text>
          <view class="od__row-value-wrap" @tap="copyOrderNo(order.orderNo)">
            <text class="od__row-value">{{ order.orderNo }}</text>
            <text class="od__copy">复制</text>
          </view>
        </view>
        <view class="od__row od__row--between">
          <text class="od__row-label">下单时间</text>
          <text class="od__row-value">{{ formatDateTime(order.createdAt, true) }}</text>
        </view>
        <view v-if="order.paidAt" class="od__row od__row--between">
          <text class="od__row-label">支付时间</text>
          <text class="od__row-value">{{ formatDateTime(order.paidAt, true) }}</text>
        </view>
        <view v-if="order.remark" class="od__row od__row--between">
          <text class="od__row-label">备注</text>
          <text class="od__row-value od__row-value--ellipsis">{{ order.remark }}</text>
        </view>
      </view>

      <!-- ==================== 操作 ==================== -->
      <view class="od__actions">
        <template v-if="order.status === 0">
          <AppButton type="ghost" size="lg" class="od__action" @tap="onCancel">取消订单</AppButton>
          <AppButton
            type="primary"
            size="lg"
            class="od__action"
            :loading="acting"
            @tap="onPay"
          >
            立即支付
          </AppButton>
        </template>
        <template v-else-if="order.status === 1">
          <AppButton type="ghost" size="lg" class="od__action" @tap="onRefund">申请退款</AppButton>
          <AppButton type="primary" size="lg" class="od__action" @tap="onRebuy">再次购买</AppButton>
        </template>
        <template v-else>
          <AppButton type="soft" size="lg" block @tap="onRebuy">再来一单</AppButton>
        </template>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.od {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

/* ==================== 状态 ==================== */
.od__status {
  margin: 20rpx $gap-page 0;
  padding: 34rpx 28rpx;
  background: linear-gradient(135deg, #f3f2ff 0%, #ffffff 60%);
}

.od__status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.od__status-title {
  font-size: 44rpx;
  font-weight: 800;
  color: $text-primary;
}

.od__status-desc {
  display: block;
  margin-top: 16rpx;
  font-size: $fs-md;
  color: $text-secondary;
  line-height: 1.6;
}

/* ==================== 区块 ==================== */
.od__sec {
  margin: 20rpx $gap-page 0;
  padding: 26rpx 24rpx;
}

.od__sec-title {
  display: block;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.od__row {
  display: flex;
  align-items: center;
  min-height: 68rpx;
}

.od__row--between {
  justify-content: space-between;
}

.od__row-text {
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
  font-size: $fs-md;
  color: $text-primary;
  @include ellipsis;
}

.od__row-label {
  font-size: $fs-md;
  color: $text-secondary;
  flex-shrink: 0;
}

.od__row-value-wrap {
  display: flex;
  align-items: center;
}

.od__row-value {
  font-size: $fs-md;
  color: $text-primary;
}

.od__row-value--ellipsis {
  max-width: 420rpx;
  @include ellipsis;
}

.od__copy {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: $radius-xs;
  border: 2rpx solid $brand-primary;
  font-size: $fs-xs;
  color: $brand-primary;
}

/* ==================== 商品 ==================== */
.oi {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
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

/* ==================== 金额 ==================== */
.amt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10rpx 0;
}

.amt--total {
  margin-top: 8rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid $divider;
}

.amt__label {
  font-size: $fs-md;
  color: $text-secondary;
}

.amt__value {
  font-size: $fs-md;
  color: $text-primary;
}

.amt__value--cut {
  color: $danger;
}

.amt__value--total {
  font-size: 40rpx;
  font-weight: 800;
  color: $danger;
}

/* ==================== 操作 ==================== */
.od__actions {
  display: flex;
  align-items: center;
  padding: 40rpx $gap-page 0;
}

.od__action {
  flex: 1;
  min-width: 0;
  margin-right: 24rpx;

  &:last-child {
    margin-right: 0;
  }
}
</style>
