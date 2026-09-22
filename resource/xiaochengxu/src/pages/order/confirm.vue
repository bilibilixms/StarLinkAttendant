<script setup lang="ts">
/**
 * 点单确认页：选优惠券 → 填备注 → 选支付方式 → 提交订单 → 支付。
 *
 * 下单接口必须带 idempotentKey（需求 §9 资金操作幂等），
 * 且提交期间按钮置灰，避免重复点击产生多笔订单。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import { couponApi, orderApi } from '@/api'
import { useCartStore } from '@/stores/cart'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { registerMockProducts } from '@/mock/db'
import { navBack, navTo, goLogin } from '@/utils/nav'
import { eventValue } from '@/utils/event'
import { toast, toastSuccess } from '@/utils/ui'
import { formatMoney, genIdempotentKey } from '@/utils/format'
import type { Coupon } from '@/types/coupon'
import type { CartItem, Product } from '@/types/product'

const cart = useCartStore()
const app = useAppStore()
const user = useUserStore()

const coupons = ref<Coupon[]>([])
const selectedCouponId = ref(0)
const remark = ref('')
const payChannel = ref<'wechat' | 'balance'>('wechat')
const showCouponSheet = ref(false)
const submitting = ref(false)

/** 幂等键在进入页面时生成一次，整个下单过程复用 */
const idempotentKey = ref(genIdempotentKey('ORD'))

const deliveryText = computed(() =>
  app.currentSession
    ? `${app.store.shortName} · ${app.currentSession.seatNo} 机位`
    : `${app.store.shortName} · 吧台自取`
)

const selectedCoupon = computed(() => coupons.value.find((c) => c.id === selectedCouponId.value) ?? null)

/** 当前订单可用的券（未使用、满足门槛、在有效期内） */
const usableCoupons = computed(() => {
  const total = cart.totalAmount
  const now = Date.now()
  return coupons.value.filter((c) => {
    if (c.status !== 0) return false
    if (c.minAmount > total) return false
    const end = new Date(c.endTime.replace(/-/g, '/')).getTime()
    return !end || end > now
  })
})

const discount = computed(() => {
  const c = selectedCoupon.value
  if (!c) return 0
  const total = cart.totalAmount
  const d = c.type === 1 ? c.value : Math.round(total * (1 - c.value / 10) * 100) / 100
  return Math.min(d, total)
})

const payable = computed(() => Math.max(0, Math.round((cart.totalAmount - discount.value) * 100) / 100))

const balanceEnough = computed(() => user.balance >= payable.value)

onLoad(async () => {
  cart.restore()
  if (!user.isLogin) {
    goLogin('/pages/order/confirm')
    return
  }
  if (!app.currentSession) void app.fetchCurrentSession()
  if (cart.isEmpty) {
    toast('购物车是空的')
    setTimeout(() => navBack(), 800)
    return
  }
  try {
    coupons.value = await couponApi.getMyCoupons({ status: 0 })
  } catch {
    coupons.value = []
  }
})

function openCouponSheet(): void {
  if (!coupons.value.length) {
    toast('暂无可用优惠券')
    return
  }
  showCouponSheet.value = true
}

/**
 * 把购物车商品快照注册进 mock 下单池。
 * 避免「购物车是上次会话残留、mock 池为空」时 mock 下单校验报「商品不存在」。
 */
function ensureOrderPool(): void {
  const products: Product[] = cart.items.map((i: CartItem) => ({
    id: i.productId,
    name: i.name,
    categoryId: 0,
    type: 2,
    price: i.price,
    spec: i.spec ?? null,
    unit: '件',
    stock: i.stock,
    sales: 0,
    cover: i.cover,
    status: 1,
  }))
  registerMockProducts(products)
}

function pickCoupon(id: number): void {
  selectedCouponId.value = selectedCouponId.value === id ? 0 : id
  showCouponSheet.value = false
}

function onRemarkInput(e: Event): void {
  remark.value = eventValue(e)
}

function couponLabel(c: Coupon): string {
  return c.type === 1 ? `¥${formatMoney(c.value)}` : `${c.value}折`
}

/* ==================== 提交订单 ==================== */

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  if (cart.isEmpty) {
    toast('购物车是空的')
    return
  }
  if (payChannel.value === 'balance' && !balanceEnough.value) {
    toast('账户余额不足，请选择微信支付或先充值')
    return
  }

  submitting.value = true
  uni.showLoading({ title: '提交中...', mask: true })
  try {
    ensureOrderPool()
    const created = await orderApi.createOrder({
      items: cart.items.map((i) => ({
        productId: i.productId,
        quantity: i.quantity,
        price: i.price,
        name: i.name,
        cover: i.cover,
        spec: i.spec,
      })),
      couponId: selectedCouponId.value || undefined,
      seatNo: app.currentSession?.seatNo,
      remark: remark.value,
      idempotentKey: idempotentKey.value,
    })

    // Demo 阶段后端直接标记支付成功，后续替换为微信支付
    await orderApi.payOrder(created.orderId, payChannel.value)

    // 余额支付：立即扣减本地余额（mock 支付扣的是 mock 会员，前端 store 需同步）
    if (payChannel.value === 'balance') {
      const newBalance = Math.round((user.balance - payable.value) * 100) / 100
      user.patchBalance(newBalance)
    }

    cart.clear()
    uni.hideLoading()
    toastSuccess('下单成功')
    setTimeout(() => {
      uni.redirectTo({ url: `/pages/order/detail?id=${created.orderId}&paid=1` })
    }, 700)
  } catch (e) {
    uni.hideLoading()
    // 失败后换一个幂等键，允许用户重试
    idempotentKey.value = genIdempotentKey('ORD')
    const msg = (e as Error)?.message
    if (msg) toast(msg)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="confirm page-root">
    <AppNavBar title="确认订单" show-back @back="navBack" />

    <!-- ==================== 配送 ==================== -->
    <view class="card sec">
      <view class="sec__row">
        <AppIcon name="location" :size="34" color="#5B5BD6" />
        <text class="sec__deliver">{{ deliveryText }}</text>
      </view>
      <text class="sec__tip">吧台出餐后由服务员送达机位，约 10-15 分钟</text>
    </view>

    <!-- ==================== 商品 ==================== -->
    <view class="card sec">
      <text class="sec__title">商品清单</text>
      <view v-for="item in cart.items" :key="item.productId" class="ci">
        <image class="ci__img" :src="item.cover" mode="aspectFill" />
        <view class="ci__info">
          <text class="ci__name">{{ item.name }}</text>
          <text v-if="item.spec" class="ci__spec">{{ item.spec }}</text>
        </view>
        <view class="ci__right">
          <text class="ci__price">¥{{ formatMoney(item.price) }}</text>
          <text class="ci__qty">×{{ item.quantity }}</text>
        </view>
      </view>
    </view>

    <!-- ==================== 优惠券 / 备注 ==================== -->
    <view class="card sec">
      <view class="sec__row sec__row--tap" @tap="openCouponSheet">
        <text class="sec__label">优惠券</text>
        <view class="sec__value">
          <text v-if="selectedCoupon" class="sec__value--active">
            -¥{{ formatMoney(discount) }}
          </text>
          <text v-else class="sec__value--hint">
            {{ usableCoupons.length ? `${usableCoupons.length} 张可用` : '暂无可用' }}
          </text>
          <view class="sec__arrow" />
        </view>
      </view>

      <view class="sec__row">
        <text class="sec__label">备注</text>
        <input
          class="sec__input"
          :value="remark"
          maxlength="50"
          placeholder="口味 / 忌口 / 送达要求"
          placeholder-class="sec__ph"
          @input="onRemarkInput"
        />
      </view>

      <view class="sec__row">
        <text class="sec__label">支付方式</text>
        <view class="pays">
          <view
            class="pay"
            :class="{ 'is-active': payChannel === 'wechat' }"
            @tap="payChannel = 'wechat'"
          >
            <AppIcon name="pay-wechat" :size="34" color="#22C55E" />
            <text class="pay__text">微信支付</text>
          </view>
          <view
            class="pay"
            :class="{ 'is-active': payChannel === 'balance' }"
            @tap="payChannel = 'balance'"
          >
            <AppIcon name="pay-balance" :size="34" :color="balanceEnough ? '#5B5BD6' : '#B0B0BE'" />
            <text class="pay__text">余额(¥{{ formatMoney(user.balance) }})</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ==================== 金额 ==================== -->
    <view class="card sec">
      <view class="amt">
        <text class="amt__label">商品金额</text>
        <text class="amt__value">¥{{ formatMoney(cart.totalAmount) }}</text>
      </view>
      <view v-if="discount > 0" class="amt">
        <text class="amt__label">优惠券</text>
        <text class="amt__value amt__value--cut">-¥{{ formatMoney(discount) }}</text>
      </view>
      <view class="amt amt--total">
        <text class="amt__label">应付金额</text>
        <text class="amt__value amt__value--total">¥{{ formatMoney(payable) }}</text>
      </view>
    </view>

    <!-- ==================== 底部提交 ==================== -->
    <view class="confirm__bar">
      <view class="confirm__total">
        <text class="confirm__total-label">应付</text>
        <text class="confirm__total-money">¥{{ formatMoney(payable) }}</text>
      </view>
      <AppButton
        type="primary"
        size="lg"
        class="confirm__submit"
        :loading="submitting"
        :disabled="submitting || cart.isEmpty"
        @tap="onSubmit"
      >
        提交订单
      </AppButton>
    </view>

    <!-- ==================== 优惠券选择弹层 ==================== -->
    <view v-if="showCouponSheet" class="sheet-mask" @tap="showCouponSheet = false">
      <view class="sheet" @tap.stop>
        <view class="sheet__head">
          <text class="sheet__title">选择优惠券</text>
          <view class="sheet__close" @tap="showCouponSheet = false">
            <AppIcon name="close" :size="36" color="#8A8A99" :stroke-width="2" />
          </view>
        </view>

        <scroll-view class="sheet__body" scroll-y>
          <view
            v-for="c in usableCoupons"
            :key="c.id"
            class="cp"
            :class="{ 'is-active': selectedCouponId === c.id }"
            @tap="pickCoupon(c.id)"
          >
            <view class="cp__left">
              <text class="cp__value">{{ couponLabel(c) }}</text>
              <text class="cp__min">{{ c.minAmount > 0 ? `满${c.minAmount}可用` : '无门槛' }}</text>
            </view>
            <view class="cp__mid">
              <text class="cp__name">{{ c.name }}</text>
              <text class="cp__scope">{{ c.scopeText }} · 至 {{ c.endTime.slice(0, 10) }}</text>
            </view>
            <view class="cp__check" :class="{ 'is-checked': selectedCouponId === c.id }">
              <AppIcon
                v-if="selectedCouponId === c.id"
                name="check"
                :size="26"
                color="#FFFFFF"
                :stroke-width="3"
              />
            </view>
          </view>

          <view v-if="!usableCoupons.length" class="sheet__empty">
            <text class="sheet__empty-text">当前订单没有可用的优惠券</text>
          </view>
        </scroll-view>

        <view class="sheet__foot" @tap="selectedCouponId = 0; showCouponSheet = false">
          <text class="sheet__foot-text">不使用优惠券</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.confirm {
  padding-bottom: 180rpx;
  @include safe-area-bottom(0rpx);
}

/* ==================== 卡片 ==================== */
.sec {
  margin: 20rpx $gap-page 0;
  padding: 26rpx 24rpx;
}

.sec__title {
  display: block;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.sec__row {
  display: flex;
  align-items: center;
  min-height: 76rpx;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.sec__row--tap:active {
  opacity: 0.7;
}

.sec__deliver {
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
  font-size: $fs-md;
  color: $text-primary;
  @include ellipsis;
}

.sec__tip {
  display: block;
  margin-top: 12rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.sec__label {
  width: 150rpx;
  flex-shrink: 0;
  font-size: $fs-md;
  color: $text-regular;
}

.sec__value {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.sec__value--active {
  font-size: $fs-md;
  font-weight: 600;
  color: $danger;
}

.sec__value--hint {
  font-size: $fs-md;
  color: $text-placeholder;
}

.sec__arrow {
  width: 14rpx;
  height: 14rpx;
  margin-left: 10rpx;
  border-top: 3rpx solid $text-placeholder;
  border-right: 3rpx solid $text-placeholder;
  transform: rotate(45deg);
  border-radius: 2rpx;
}

.sec__input {
  flex: 1;
  min-width: 0;
  height: 60rpx;
  text-align: right;
  font-size: $fs-md;
  color: $text-primary;
}

.sec__ph {
  color: $text-placeholder;
  font-size: $fs-md;
}

/* ==================== 商品项 ==================== */
.ci {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.ci__img {
  width: 110rpx;
  height: 110rpx;
  border-radius: $radius-sm;
  flex-shrink: 0;
  background: $card-bg-soft;
}

.ci__info {
  flex: 1;
  min-width: 0;
  margin: 0 18rpx;
}

.ci__name {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis;
}

.ci__spec {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.ci__right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.ci__price {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
}

.ci__qty {
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

/* ==================== 支付方式 ==================== */
.pays {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: flex-end;
}

.pay {
  display: flex;
  align-items: center;
  margin-left: 16rpx;
  padding: 8rpx 18rpx;
  border-radius: $radius-pill;
  border: 2rpx solid $divider;

  &.is-active {
    border-color: $brand-primary;
    background: $brand-primary-soft;
  }

  &:active {
    opacity: 0.8;
  }
}

.pay__text {
  margin-left: 8rpx;
  font-size: $fs-sm;
  color: $text-primary;
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

/* ==================== 底部栏 ==================== */
.confirm__bar {
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

.confirm__total {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
}

.confirm__total-label {
  font-size: $fs-md;
  color: $text-secondary;
}

.confirm__total-money {
  margin-left: 10rpx;
  font-size: 44rpx;
  font-weight: 800;
  color: $danger;
}

.confirm__submit {
  flex-shrink: 0;
  min-width: 260rpx;
}

/* ==================== 优惠券弹层 ==================== */
.sheet-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 900;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
}

.sheet {
  width: 100%;
  max-height: 70vh;
  background: #ffffff;
  border-radius: $radius-xl $radius-xl 0 0;
  display: flex;
  flex-direction: column;
  @include safe-area-bottom(0rpx);
}

.sheet__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 24rpx 16rpx;
}

.sheet__title {
  font-size: 36rpx;
  font-weight: 700;
  color: $text-primary;
}

.sheet__close {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sheet__body {
  max-height: 52vh;
  padding: 0 24rpx;
}

.cp {
  display: flex;
  align-items: center;
  padding: 22rpx 20rpx;
  margin-bottom: 16rpx;
  border-radius: $radius-md;
  background: $card-bg-soft;
  border: 2rpx solid transparent;

  &.is-active {
    border-color: $brand-primary;
    background: $brand-primary-soft;
  }
}

.cp__left {
  width: 150rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cp__value {
  font-size: 40rpx;
  font-weight: 800;
  color: $danger;
  line-height: 1.1;
}

.cp__min {
  margin-top: 4rpx;
  font-size: $fs-xs;
  color: $text-secondary;
}

.cp__mid {
  flex: 1;
  min-width: 0;
}

.cp__name {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis;
}

.cp__scope {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  @include ellipsis;
}

.cp__check {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  border: 2rpx solid #c8cad6;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &.is-checked {
    background: $brand-primary;
    border-color: $brand-primary;
  }
}

.sheet__empty {
  padding: 60rpx 0;
  display: flex;
  justify-content: center;
}

.sheet__empty-text {
  font-size: $fs-md;
  color: $text-placeholder;
}

.sheet__foot {
  padding: 24rpx;
  margin-top: 8rpx;
  border-top: 1rpx solid $divider;
  display: flex;
  justify-content: center;

  &:active {
    opacity: 0.6;
  }
}

.sheet__foot-text {
  font-size: $fs-md;
  color: $text-secondary;
}
</style>
