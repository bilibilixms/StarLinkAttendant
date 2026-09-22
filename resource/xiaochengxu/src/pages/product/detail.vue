<script setup lang="ts">
/**
 * 商品详情。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { productApi } from '@/api'
import { useCartStore } from '@/stores/cart'
import { navBack, navTo } from '@/utils/nav'
import { toast } from '@/utils/ui'
import { formatMoney } from '@/utils/format'
import type { Product } from '@/types/product'

const cart = useCartStore()

const product = ref<Product | null>(null)
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const quantity = ref(1)
const productId = ref(0)
/** 来源：'hot' 表示首页热门商品（真实后端）；缺省走自助点餐（mock） */
const source = ref<'hot' | ''>('')

const price = computed(() => product.value?.memberPrice ?? product.value?.price ?? 0)
const images = computed(() => {
  const p = product.value
  if (!p) return []
  return p.images?.length ? p.images : [p.cover]
})
const soldOut = computed(() => (product.value?.stock ?? 0) <= 0)

async function load(id: number): Promise<void> {
  state.value = 'loading'
  try {
    product.value =
      source.value === 'hot'
        ? await productApi.getHotProductDetail(id)
        : await productApi.getProductDetail(id)
    state.value = 'success'
  } catch {
    state.value = 'error'
  }
}

onLoad((query) => {
  productId.value = Number(query?.id ?? 0)
  source.value = query?.source === 'hot' ? 'hot' : ''
  if (!productId.value) {
    state.value = 'error'
    return
  }
  void load(productId.value)
})

function onPreview(index: number): void {
  uni.previewImage({ urls: images.value, current: index })
}

function step(delta: number): void {
  const max = product.value?.stock ?? 1
  quantity.value = Math.min(max, Math.max(1, quantity.value + delta))
}

/** 防重复快速点击：限定窗口内不重复加购，避免一次点按被识别成两次 */
let lastAddAt = 0
function onAddCart(): void {
  if (!product.value || soldOut.value) return
  const now = Date.now()
  if (now - lastAddAt < 400) return
  lastAddAt = now
  const ok = cart.add(product.value, quantity.value)
  toast(ok ? `已加入购物车 ×${quantity.value}` : '库存不足')
}

function onBuyNow(): void {
  if (!product.value || soldOut.value) return
  const now = Date.now()
  if (now - lastAddAt < 400) return
  lastAddAt = now
  const ok = cart.add(product.value, quantity.value)
  if (!ok) {
    toast('库存不足')
    return
  }
  navTo('/pages/order/confirm')
}

function goCart(): void {
  navTo('/pages/cart/index')
}
</script>

<template>
  <view class="detail page-root">
    <AppNavBar title="商品详情" show-back @back="navBack" />

    <StateView v-if="state !== 'success'" :state="state" @retry="load(productId)" />

    <template v-else-if="product">
      <!-- ==================== 图片 ==================== -->
      <swiper class="detail__swiper" :indicator-dots="images.length > 1" indicator-active-color="#5B5BD6" circular>
        <swiper-item v-for="(img, i) in images" :key="i" @tap="onPreview(i)">
          <image class="detail__img" :src="img" mode="aspectFill" />
        </swiper-item>
      </swiper>

      <!-- ==================== 基本信息 ==================== -->
      <view class="card detail__card">
        <view class="detail__price-row">
          <text class="detail__price">¥{{ formatMoney(price) }}</text>
          <text v-if="product.memberPrice && product.memberPrice < product.price" class="detail__origin">
            ¥{{ formatMoney(product.price) }}
          </text>
          <view v-if="product.memberPrice" class="detail__member-tag">
            <text class="detail__member-text">会员价</text>
          </view>
        </view>

        <text class="detail__name">{{ product.name }}</text>

        <view class="detail__meta">
          <text v-if="product.spec" class="detail__meta-item">规格：{{ product.spec }}</text>
          <text class="detail__meta-item">库存：{{ product.stock }}{{ product.unit || '件' }}</text>
          <text class="detail__meta-item">已售：{{ product.sales }}</text>
        </view>

        <view v-if="product.tags?.length" class="detail__tags">
          <text v-for="t in product.tags" :key="t" class="detail__tag">{{ t }}</text>
        </view>
      </view>

      <!-- ==================== 数量 ==================== -->
      <view class="card detail__card">
        <view class="flex-between">
          <text class="detail__label">购买数量</text>
          <view class="stepper">
            <view class="stepper__btn" :class="{ 'is-disabled': quantity <= 1 }" @tap="step(-1)">
              <AppIcon name="minus" :size="28" color="#5B5BD6" :stroke-width="2.4" />
            </view>
            <text class="stepper__num">{{ quantity }}</text>
            <view class="stepper__btn stepper__btn--add" @tap="step(1)">
              <AppIcon name="plus" :size="28" color="#FFFFFF" :stroke-width="2.4" />
            </view>
          </view>
        </view>
      </view>

      <!-- ==================== 描述 ==================== -->
      <view class="card detail__card">
        <text class="detail__section">商品介绍</text>
        <text class="detail__desc">
          {{ product.description || `${product.name}${product.spec ? `（${product.spec}）` : ''}，下单后由吧台配送至您的机位，也可到吧台自取。` }}
        </text>
        <view class="detail__notice">
          <AppIcon name="warning" :size="30" color="#FF9F2E" :stroke-width="1.7" />
          <text class="detail__notice-text">虚拟商品（网费 / 时长卡）下单后即时到账，不支持退款</text>
        </view>
      </view>

      <!-- ==================== 底部操作 ==================== -->
      <view class="detail__bar">
        <view class="detail__bar-cart" @tap="goCart">
          <AppIcon name="cart" :size="46" color="#4A4A52" :stroke-width="1.7" />
          <text class="detail__bar-cart-text">购物车</text>
          <view v-if="cart.count > 0" class="detail__bar-badge">
            <text class="detail__bar-badge-text">{{ cart.count }}</text>
          </view>
        </view>

        <AppButton
          type="soft"
          size="lg"
          class="detail__bar-btn"
          :disabled="soldOut"
          @tap="onAddCart"
        >
          加入购物车
        </AppButton>
        <AppButton
          type="primary"
          size="lg"
          class="detail__bar-btn"
          :disabled="soldOut"
          @tap="onBuyNow"
        >
          {{ soldOut ? '已售罄' : '立即购买' }}
        </AppButton>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.detail {
  padding-bottom: 160rpx;
  @include safe-area-bottom(0rpx);
}

.detail__swiper {
  width: 100%;
  height: 640rpx;
  background: $card-bg-soft;
}

.detail__img {
  width: 100%;
  height: 100%;
}

.detail__card {
  margin: 20rpx $gap-page 0;
  padding: 28rpx 24rpx;
}

.detail__price-row {
  display: flex;
  align-items: baseline;
}

.detail__price {
  font-size: 52rpx;
  font-weight: 800;
  color: $danger;
}

.detail__origin {
  margin-left: 14rpx;
  font-size: $fs-md;
  color: $text-placeholder;
  text-decoration: line-through;
}

.detail__member-tag {
  margin-left: auto;
  padding: 4rpx 14rpx;
  border-radius: $radius-xs;
  background: $brand-primary-soft;
}

.detail__member-text {
  font-size: $fs-xs;
  color: $brand-primary;
  font-weight: 600;
}

.detail__name {
  display: block;
  margin-top: 16rpx;
  font-size: 38rpx;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.35;
}

.detail__meta {
  display: flex;
  flex-wrap: wrap;
  margin-top: 20rpx;
}

.detail__meta-item {
  margin-right: 28rpx;
  font-size: $fs-base;
  color: $text-secondary;
}

.detail__tags {
  display: flex;
  flex-wrap: wrap;
  margin-top: 18rpx;
}

.detail__tag {
  margin: 0 12rpx 12rpx 0;
  padding: 4rpx 14rpx;
  border-radius: $radius-xs;
  background: #fff2f4;
  font-size: $fs-sm;
  color: #e8465e;
}

.detail__label {
  font-size: $fs-md;
  color: $text-primary;
}

.detail__section {
  display: block;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
}

.detail__desc {
  display: block;
  margin-top: 16rpx;
  font-size: $fs-md;
  color: $text-regular;
  line-height: 1.7;
}

.detail__notice {
  display: flex;
  align-items: flex-start;
  margin-top: 20rpx;
  padding: 16rpx 18rpx;
  border-radius: $radius-sm;
  background: #fff8ec;
}

.detail__notice-text {
  flex: 1;
  min-width: 0;
  margin-left: 10rpx;
  font-size: $fs-sm;
  color: #a8681a;
  line-height: 1.6;
}

/* ==================== 底部操作栏 ==================== */
.detail__bar {
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

.detail__bar-cart {
  position: relative;
  width: 120rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;

  &:active {
    opacity: 0.7;
  }
}

.detail__bar-cart-text {
  margin-top: 4rpx;
  font-size: $fs-xs;
  color: $text-secondary;
}

.detail__bar-badge {
  position: absolute;
  right: 16rpx;
  top: -6rpx;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: 16rpx;
  background: $danger;
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail__bar-badge-text {
  font-size: 18rpx;
  font-weight: 700;
  color: #ffffff;
}

.detail__bar-btn {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
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
  min-width: 64rpx;
  text-align: center;
  font-size: $fs-lg;
  font-weight: 600;
  color: $text-primary;
}
</style>
