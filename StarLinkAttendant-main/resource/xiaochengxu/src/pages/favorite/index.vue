<script setup lang="ts">
/**
 * 我的收藏：帖子 / 商品。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import CommunityPost from '@/components/CommunityPost.vue'
import StateView from '@/components/StateView.vue'
import { storeApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { navBack, navTo, goLogin } from '@/utils/nav'
import { toast } from '@/utils/ui'
import { formatMoney } from '@/utils/format'
import type { Post } from '@/types/community'
import type { Product } from '@/types/product'

const user = useUserStore()

type Tab = 'post' | 'product'
const tab = ref<Tab>('post')
const posts = ref<Post[]>([])
const products = ref<Product[]>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')

async function load(): Promise<void> {
  if (!user.isLogin) {
    state.value = 'empty'
    return
  }
  state.value = 'loading'
  try {
    const res = await storeApi.getFavorites()
    posts.value = res.posts
    products.value = res.products
    const list = tab.value === 'post' ? posts.value : products.value
    state.value = list.length ? 'success' : 'empty'
  } catch {
    state.value = 'error'
  }
}

function switchTab(next: Tab): void {
  if (tab.value === next) return
  tab.value = next
  const list = next === 'post' ? posts.value : products.value
  state.value = list.length ? 'success' : 'empty'
}

onLoad(() => {
  void load()
})

function onPostTap(p: Post): void {
  navTo(`/pages/community/detail?id=${p.id}`)
}

function previewImage(urls: string[], index: number): void {
  uni.previewImage({ urls, current: index })
}

function onProductTap(p: Product): void {
  navTo(`/pages/product/detail?id=${p.id}`)
}

function goCommunity(): void {
  navTo('/pages/community/index')
}

function goMenu(): void {
  navTo('/pages/product/index')
}
</script>

<template>
  <view class="fav page-root">
    <AppNavBar title="我的收藏" show-back @back="navBack" />

    <!-- ==================== Tab ==================== -->
    <view class="tabs">
      <view class="tab" :class="{ 'is-active': tab === 'post' }" @tap="switchTab('post')">
        <text class="tab__text">帖子（{{ posts.length }}）</text>
        <view v-if="tab === 'post'" class="tab__bar" />
      </view>
      <view class="tab" :class="{ 'is-active': tab === 'product' }" @tap="switchTab('product')">
        <text class="tab__text">商品（{{ products.length }}）</text>
        <view v-if="tab === 'product'" class="tab__bar" />
      </view>
    </view>

    <!-- ==================== 未登录 ==================== -->
    <StateView
      v-if="!user.isLogin"
      state="empty"
      empty-text="登录后查看收藏"
      empty-desc="收藏的帖子和商品都会出现在这里"
      empty-icon="star"
    >
      <template #action>
        <AppButton type="primary" size="md" @tap="goLogin('/pages/favorite/index')">立即登录</AppButton>
      </template>
    </StateView>

    <template v-else>
      <StateView
        v-if="state !== 'success'"
        :state="state"
        :empty-text="tab === 'post' ? '还没有收藏的帖子' : '还没有收藏的商品'"
        empty-desc="看到喜欢的内容点个收藏吧"
        empty-icon="star"
        @retry="load"
      >
        <template #action>
          <AppButton
            type="primary"
            size="md"
            @tap="tab === 'post' ? goCommunity() : goMenu()"
          >
            {{ tab === 'post' ? '去社区逛逛' : '去点单页看看' }}
          </AppButton>
        </template>
      </StateView>

      <!-- ==================== 帖子 ==================== -->
      <view v-else-if="tab === 'post'" class="posts">
        <CommunityPost
          v-for="p in posts"
          :key="p.id"
          :post="p"
          @tap="onPostTap(p)"
          @share="toast('点击右上角「···」分享给好友')"
          @comment="onPostTap(p)"
          @like="toast('请在帖子详情页点赞')"
          @store="navTo('/pages/store/index')"
          @preview="(i) => previewImage(p.images, i)"
        />
      </view>

      <!-- ==================== 商品 ==================== -->
      <view v-else class="goods">
        <view v-for="p in products" :key="p.id" class="good" @tap="onProductTap(p)">
          <image class="good__img" :src="p.cover" mode="aspectFill" />
          <text class="good__name">{{ p.name }}</text>
          <text v-if="p.spec" class="good__spec">{{ p.spec }}</text>
          <view class="good__foot">
            <text class="good__price">¥{{ formatMoney(p.memberPrice ?? p.price) }}</text>
            <text class="good__sales">已售{{ p.sales }}</text>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.fav {
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

/* ==================== 帖子 ==================== */
.posts {
  padding: 0 $gap-page;
  background: #ffffff;
}

/* ==================== 商品 ==================== */
.goods {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  padding: 20rpx $gap-page 0;
}

.good {
  width: 48.5%;
  margin-bottom: 22rpx;
  padding: 20rpx;
  background: #ffffff;
  border-radius: $radius-lg;

  &:active {
    opacity: 0.9;
  }
}

.good__img {
  width: 100%;
  height: 240rpx;
  border-radius: $radius-md;
  background: $card-bg-soft;
}

.good__name {
  display: block;
  margin-top: 16rpx;
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis;
}

.good__spec {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.good__foot {
  display: flex;
  align-items: baseline;
  margin-top: 14rpx;
}

.good__price {
  font-size: 36rpx;
  font-weight: 800;
  color: $danger;
}

.good__sales {
  margin-left: 12rpx;
  font-size: $fs-xs;
  color: $text-placeholder;
}
</style>
