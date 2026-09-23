<script setup lang="ts">
/**
 * 自助点餐（商品列表）。
 * 左侧分类 + 右侧商品，底部购物车吸底栏；下单后由吧台送到机位。
 */
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { productApi } from '@/api'
import { useCartStore } from '@/stores/cart'
import { useAppStore } from '@/stores/app'
import { navTo, navBack, goLogin } from '@/utils/nav'
import { toast } from '@/utils/ui'
import { formatMoney } from '@/utils/format'
import type { Product, ProductCategory } from '@/types/product'

const cart = useCartStore()
const app = useAppStore()

const categories = ref<ProductCategory[]>([])
const products = ref<Product[]>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const activeCatId = ref(0)
const scrollTo = ref('')
const listScrollTop = ref(0)

/** 按分类分组，右侧一次渲染全部，滚动定位 */
const groups = computed(() =>
  categories.value
    .map((c) => ({ category: c, items: products.value.filter((p) => p.categoryId === c.id) }))
    .filter((g) => g.items.length > 0)
)

/** 各分类区块距顶部的偏移，用于滚动时反查当前分类 */
const sectionTops = ref<Array<{ id: number; top: number }>>([])

async function load(): Promise<void> {
  state.value = 'loading'
  try {
    const [cats, list] = await Promise.all([productApi.getCategories(), productApi.getProducts()])
    categories.value = cats
    products.value = list
    if (cats.length && !activeCatId.value) activeCatId.value = cats[0].id
    state.value = list.length ? 'success' : 'empty'
    setTimeout(measureSections, 120)
  } catch {
    state.value = 'error'
  }
}

/** 测量每个分类区块的位置，供滚动联动使用 */
function measureSections(): void {
  const query = uni.createSelectorQuery()
  groups.value.forEach((g) => {
    query.select(`#cat-${g.category.id}`).boundingClientRect()
  })
  // scrollOffset 的类型定义要求传回调（即使这里不需要单独处理结果）
  query.select('.menu__list').scrollOffset(() => {})
  query.exec((res) => {
    const list = res.slice(0, groups.value.length)
    const offset = res[res.length - 1] as { scrollTop?: number } | undefined
    const base = offset?.scrollTop ?? 0
    sectionTops.value = groups.value.map((g, i) => {
      const rect = list[i] as { top?: number } | null
      return { id: g.category.id, top: (rect?.top ?? 0) + base }
    })
  })
}

onLoad(() => {
  void load()
})

onShow(() => {
  cart.restore()
})

function selectCat(id: number): void {
  activeCatId.value = id
  scrollTo.value = `cat-${id}`
}

function onListScroll(e: { detail: { scrollTop: number } }): void {
  listScrollTop.value = e.detail.scrollTop
  const top = e.detail.scrollTop + 20
  let current = sectionTops.value[0]?.id ?? 0
  sectionTops.value.forEach((s) => {
    if (s.top <= top) current = s.id
  })
  if (current && current !== activeCatId.value) activeCatId.value = current
}

/* ==================== 购物车操作 ==================== */

function priceOf(p: Product): number {
  return p.memberPrice ?? p.price
}

function qtyOf(p: Product): number {
  return cart.quantityOf(p.id)
}

function onAdd(p: Product): void {
  if (p.stock <= 0) {
    toast('该商品已售罄')
    return
  }
  const ok = cart.add(p, 1)
  if (!ok) toast(`库存不足，最多可加 ${p.stock} 件`)
}

function onMinus(p: Product): void {
  cart.decrement(p.id)
}

function goDetail(p: Product): void {
  navTo(`/pages/product/detail?id=${p.id}`)
}

function goCart(): void {
  if (cart.isEmpty) return
  if (!app.hasActiveSession) {
    // 没有上机会话时也能点单，但提示送到吧台自取
    toast('当前未上机，下单后请到吧台自取')
  }
  navTo('/pages/cart/index')
}
</script>

<template>
  <view class="menu page-root">
    <AppNavBar title="自助点餐" show-back @back="navBack" />

    <StateView v-if="state !== 'success'" :state="state" @retry="load" />

    <!-- ==================== 分类 + 商品 ==================== -->
    <view v-else class="menu__body">
      <!-- 左侧分类 -->
      <scroll-view class="menu__cats" scroll-y :show-scrollbar="false">
        <view
          v-for="c in categories"
          :key="c.id"
          class="menu__cat"
          :class="{ 'is-active': activeCatId === c.id }"
          @tap="selectCat(c.id)"
        >
          <view v-if="activeCatId === c.id" class="menu__cat-bar" />
          <text class="menu__cat-text">{{ c.name }}</text>
        </view>
      </scroll-view>

      <!-- 右侧商品 -->
      <scroll-view
        class="menu__list"
        scroll-y
        :scroll-into-view="scrollTo"
        :scroll-top="listScrollTop"
        :scroll-with-animation="true"
        :show-scrollbar="false"
        @scroll="onListScroll"
      >
        <view v-for="g in groups" :id="`cat-${g.category.id}`" :key="g.category.id" class="menu__group">
          <text class="menu__group-title">{{ g.category.name }}</text>

          <view v-for="p in g.items" :key="p.id" class="prod" @tap="goDetail(p)">
            <image class="prod__img" :src="p.cover" mode="aspectFill" />

            <view class="prod__info">
              <text class="prod__name">{{ p.name }}</text>
              <text v-if="p.spec" class="prod__spec">{{ p.spec }}</text>
              <view v-if="p.tags?.length" class="prod__tags">
                <text v-for="t in p.tags" :key="t" class="prod__tag">{{ t }}</text>
              </view>
              <view class="prod__price-row">
                <text class="prod__price">¥{{ formatMoney(priceOf(p)) }}</text>
                <text v-if="p.memberPrice && p.memberPrice < p.price" class="prod__origin">
                  ¥{{ formatMoney(p.price) }}
                </text>
                <text class="prod__sales">已售{{ p.sales }}</text>
              </view>
            </view>

            <!-- 加购 / 步进器 -->
            <view class="prod__action" @tap.stop>
              <view v-if="qtyOf(p) > 0" class="stepper">
                <view class="stepper__btn" @tap="onMinus(p)">
                  <AppIcon name="minus" :size="28" color="#5B5BD6" :stroke-width="2.4" />
                </view>
                <text class="stepper__num">{{ qtyOf(p) }}</text>
                <view class="stepper__btn stepper__btn--add" @tap="onAdd(p)">
                  <AppIcon name="plus" :size="28" color="#FFFFFF" :stroke-width="2.4" />
                </view>
              </view>
              <view v-else class="add-btn" @tap="onAdd(p)">
                <AppIcon name="plus" :size="34" color="#FFFFFF" :stroke-width="2.6" />
              </view>
            </view>
          </view>
        </view>

        <view class="menu__list-foot">
          <text class="menu__list-foot-text">— 已经到底啦 —</text>
        </view>
      </scroll-view>
    </view>

    <!-- ==================== 底部购物车 ==================== -->
    <view class="cartbar">
      <view class="cartbar__icon" :class="{ 'is-empty': cart.isEmpty }" @tap="goCart">
        <AppIcon name="cart" :size="52" :color="cart.isEmpty ? '#B0B0BE' : '#FFFFFF'" :stroke-width="1.8" />
        <view v-if="cart.count > 0" class="cartbar__badge">
          <text class="cartbar__badge-text">{{ cart.count > 99 ? '99+' : cart.count }}</text>
        </view>
      </view>

      <view class="cartbar__info" @tap="goCart">
        <text v-if="cart.isEmpty" class="cartbar__empty">购物车是空的</text>
        <template v-else>
          <text class="cartbar__total">¥{{ formatMoney(cart.totalAmount) }}</text>
          <text class="cartbar__tip">共 {{ cart.count }} 件</text>
        </template>
      </view>

      <AppButton
        type="primary"
        size="lg"
        :disabled="cart.isEmpty"
        class="cartbar__btn"
        @tap="goCart"
      >
        去结算
      </AppButton>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.menu {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #ffffff;
}

/* ==================== 主体 ==================== */
.menu__body {
  flex: 1;
  min-height: 0;
  display: flex;
}

/* ---------- 左侧分类 ---------- */
.menu__cats {
  width: 178rpx;
  height: 100%;
  flex-shrink: 0;
  background: $card-bg-soft;
}

.menu__cat {
  position: relative;
  min-height: 104rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20rpx 12rpx;

  &:active {
    opacity: 0.7;
  }
}

.menu__cat-bar {
  position: absolute;
  left: 0;
  top: 50%;
  width: 6rpx;
  height: 36rpx;
  margin-top: -18rpx;
  border-radius: 0 4rpx 4rpx 0;
  background: $brand-primary;
}

.menu__cat-text {
  font-size: $fs-md;
  color: $text-regular;
  text-align: center;
}

.menu__cat.is-active {
  background: #ffffff;
}

.menu__cat.is-active .menu__cat-text {
  font-weight: 700;
  color: $brand-primary;
}

/* ---------- 右侧商品 ---------- */
.menu__list {
  flex: 1;
  min-width: 0;
  height: 100%;
  background: #ffffff;
}

.menu__group {
  padding: 0 20rpx;
}

.menu__group-title {
  display: block;
  padding: 26rpx 0 10rpx;
  font-size: $fs-md;
  font-weight: 700;
  color: $text-primary;
}

.prod {
  display: flex;
  align-items: flex-start;
  padding: 22rpx 0;
  border-bottom: 1rpx solid $divider;

  &:active {
    opacity: 0.9;
  }
}

.prod__img {
  width: 150rpx;
  height: 150rpx;
  border-radius: $radius-md;
  flex-shrink: 0;
  background: $card-bg-soft;
}

.prod__info {
  flex: 1;
  min-width: 0;
  margin: 0 16rpx;
  display: flex;
  flex-direction: column;
}

.prod__name {
  font-size: $fs-lg;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis-multi(2);
}

.prod__spec {
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  @include ellipsis;
}

.prod__tags {
  display: flex;
  margin-top: 8rpx;
}

.prod__tag {
  margin-right: 10rpx;
  padding: 2rpx 10rpx;
  border-radius: $radius-xs;
  background: #fff2f4;
  font-size: $fs-xs;
  color: #e8465e;
}

.prod__price-row {
  display: flex;
  align-items: baseline;
  margin-top: auto;
  padding-top: 12rpx;
}

.prod__price {
  font-size: 34rpx;
  font-weight: 700;
  color: $danger;
}

.prod__origin {
  margin-left: 10rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  text-decoration: line-through;
}

.prod__sales {
  margin-left: 12rpx;
  font-size: $fs-xs;
  color: $text-placeholder;
}

/* ---------- 加购 ---------- */
.prod__action {
  flex-shrink: 0;
  padding-top: 90rpx;
}

.add-btn {
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  background: $brand-primary;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    opacity: 0.8;
  }
}

.stepper {
  display: flex;
  align-items: center;
}

.stepper__btn {
  width: 46rpx;
  height: 46rpx;
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
  min-width: 46rpx;
  text-align: center;
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
}

.menu__list-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx 0 60rpx;
}

.menu__list-foot-text {
  font-size: $fs-sm;
  color: $text-placeholder;
}

/* ==================== 底部购物车 ==================== */
.cartbar {
  flex-shrink: 0;
  height: 108rpx;
  padding: 0 $gap-page;
  background: #ffffff;
  border-top: 1rpx solid $divider;
  display: flex;
  align-items: center;
  @include safe-area-bottom(0rpx);
}

.cartbar__icon {
  position: relative;
  width: 92rpx;
  height: 92rpx;
  margin-top: -36rpx;
  border-radius: 50%;
  background: $brand-gradient;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 8rpx 20rpx rgba(91, 91, 214, 0.32);

  &.is-empty {
    background: #e8eaf0;
    box-shadow: none;
  }

  &:active {
    opacity: 0.85;
  }
}

.cartbar__badge {
  position: absolute;
  right: -4rpx;
  top: -4rpx;
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 8rpx;
  border-radius: 18rpx;
  background: $danger;
  border: 3rpx solid #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cartbar__badge-text {
  font-size: 20rpx;
  font-weight: 700;
  color: #ffffff;
}

.cartbar__info {
  flex: 1;
  min-width: 0;
  margin: 0 24rpx;
  display: flex;
  flex-direction: column;
}

.cartbar__empty {
  font-size: $fs-md;
  color: $text-placeholder;
}

.cartbar__total {
  font-size: 40rpx;
  font-weight: 800;
  color: $danger;
  line-height: 1.1;
}

.cartbar__tip {
  margin-top: 4rpx;
  font-size: $fs-sm;
  color: $text-secondary;
}

.cartbar__btn {
  flex-shrink: 0;
  min-width: 200rpx;
}
</style>
