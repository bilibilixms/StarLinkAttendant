<script setup lang="ts">
/**
 * 购物车。
 */
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { useCartStore } from '@/stores/cart'
import { useAppStore } from '@/stores/app'
import { navBack, navTo } from '@/utils/nav'
import { confirm } from '@/utils/ui'
import { formatMoney } from '@/utils/format'

const cart = useCartStore()
const app = useAppStore()

const deliveryText = computed(() =>
  app.currentSession
    ? `${app.store.shortName} · ${app.currentSession.seatNo} 机位`
    : `${app.store.shortName} · 吧台自取`
)

onShow(() => {
  cart.restore()
  if (!app.currentSession) void app.fetchCurrentSession()
})

function goMenu(): void {
  navTo('/pages/product/index')
}

async function onClear(): Promise<void> {
  if (cart.isEmpty) return
  const ok = await confirm('确定要清空购物车吗？', '清空购物车', '清空')
  if (ok) cart.clear()
}

function onRemove(productId: number, name: string): void {
  void confirm(`确定要移除「${name}」吗？`, '移除商品', '移除').then((ok) => {
    if (ok) cart.remove(productId)
  })
}

function goConfirm(): void {
  if (cart.isEmpty) return
  navTo('/pages/order/confirm')
}
</script>

<template>
  <view class="cart page-root">
    <AppNavBar title="购物车" show-back @back="navBack">
      <template #right>
        <text v-if="!cart.isEmpty" class="cart__clear" @tap="onClear">清空</text>
      </template>
    </AppNavBar>

    <StateView
      v-if="cart.isEmpty"
      state="empty"
      empty-text="购物车是空的"
      empty-desc="去点单页挑点什么吧"
      empty-icon="empty-box"
    >
      <template #action>
        <AppButton type="primary" size="md" class="cart__empty-btn" @tap="goMenu">去点单</AppButton>
      </template>
    </StateView>

    <template v-else>
      <!-- 配送信息 -->
      <view class="card cart__deliver">
        <AppIcon name="location" :size="34" color="#5B5BD6" />
        <text class="cart__deliver-text">{{ deliveryText }}</text>
      </view>

      <!-- 商品列表 -->
      <view class="card cart__list">
        <view v-for="item in cart.items" :key="item.productId" class="ci">
          <image class="ci__img" :src="item.cover" mode="aspectFill" />

          <view class="ci__info">
            <text class="ci__name">{{ item.name }}</text>
            <text v-if="item.spec" class="ci__spec">{{ item.spec }}</text>
            <view class="ci__price-row">
              <text class="ci__price">¥{{ formatMoney(item.price) }}</text>
              <text class="ci__subtotal">小计 ¥{{ formatMoney(item.price * item.quantity) }}</text>
            </view>
          </view>

          <view class="ci__right">
            <view class="stepper">
              <view class="stepper__btn" @tap="cart.decrement(item.productId)">
                <AppIcon name="minus" :size="26" color="#5B5BD6" :stroke-width="2.4" />
              </view>
              <text class="stepper__num">{{ item.quantity }}</text>
              <view
                class="stepper__btn stepper__btn--add"
                @tap="cart.increment(item.productId)"
              >
                <AppIcon name="plus" :size="26" color="#FFFFFF" :stroke-width="2.4" />
              </view>
            </view>
            <text class="ci__remove" @tap="onRemove(item.productId, item.name)">移除</text>
          </view>
        </view>
      </view>

      <!-- 小计 -->
      <view class="card cart__summary">
        <view class="cart__row">
          <text class="cart__row-label">商品件数</text>
          <text class="cart__row-value">{{ cart.count }} 件</text>
        </view>
        <view class="cart__row">
          <text class="cart__row-label">商品金额</text>
          <text class="cart__row-value">¥{{ formatMoney(cart.totalAmount) }}</text>
        </view>
        <view class="cart__row">
          <text class="cart__row-label">优惠</text>
          <text class="cart__row-value cart__row-value--hint">下单时可选择优惠券</text>
        </view>
      </view>

      <!-- 底部结算 -->
      <view class="cart__bar">
        <view class="cart__bar-total">
          <text class="cart__bar-label">合计</text>
          <text class="cart__bar-money">¥{{ formatMoney(cart.totalAmount) }}</text>
        </view>
        <AppButton type="primary" size="lg" class="cart__bar-btn" @tap="goConfirm">
          去结算({{ cart.count }})
        </AppButton>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.cart {
  padding-bottom: 180rpx;
  @include safe-area-bottom(0rpx);
}

.cart__clear {
  font-size: $fs-md;
  color: $text-secondary;

  &:active {
    opacity: 0.6;
  }
}

.cart__empty-btn {
  margin-top: 8rpx;
}

/* ==================== 配送信息 ==================== */
.cart__deliver {
  margin: 20rpx $gap-page 0;
  padding: 24rpx;
  display: flex;
  align-items: center;
}

.cart__deliver-text {
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
  font-size: $fs-md;
  color: $text-primary;
  @include ellipsis;
}

/* ==================== 商品列表 ==================== */
.cart__list {
  margin: 20rpx $gap-page 0;
  padding: 8rpx 24rpx;
}

.ci {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.ci__img {
  width: 140rpx;
  height: 140rpx;
  border-radius: $radius-md;
  flex-shrink: 0;
  background: $card-bg-soft;
}

.ci__info {
  flex: 1;
  min-width: 0;
  margin: 0 18rpx;
  display: flex;
  flex-direction: column;
}

.ci__name {
  font-size: $fs-lg;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis-multi(2);
}

.ci__spec {
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.ci__price-row {
  display: flex;
  align-items: baseline;
  margin-top: auto;
  padding-top: 12rpx;
}

.ci__price {
  font-size: 32rpx;
  font-weight: 700;
  color: $danger;
}

.ci__subtotal {
  margin-left: 14rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.ci__right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.ci__remove {
  margin-top: 18rpx;
  font-size: $fs-sm;
  color: $text-placeholder;

  &:active {
    opacity: 0.6;
  }
}

/* ==================== 小计 ==================== */
.cart__summary {
  margin: 20rpx $gap-page 0;
  padding: 24rpx;
}

.cart__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10rpx 0;
}

.cart__row-label {
  font-size: $fs-md;
  color: $text-secondary;
}

.cart__row-value {
  font-size: $fs-md;
  color: $text-primary;
}

.cart__row-value--hint {
  color: $brand-primary;
}

/* ==================== 底部 ==================== */
.cart__bar {
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

.cart__bar-total {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
}

.cart__bar-label {
  font-size: $fs-md;
  color: $text-secondary;
}

.cart__bar-money {
  margin-left: 10rpx;
  font-size: 44rpx;
  font-weight: 800;
  color: $danger;
}

.cart__bar-btn {
  flex-shrink: 0;
  min-width: 260rpx;
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

  &:active {
    opacity: 0.7;
  }
}

.stepper__btn--add {
  background: $brand-primary;
  border-color: $brand-primary;
}

.stepper__num {
  min-width: 56rpx;
  text-align: center;
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
}
</style>
