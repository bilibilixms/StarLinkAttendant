<script setup lang="ts">
/**
 * 门店列表：搜索 + 切换当前门店。
 * 门店用于首页搜索、预约、点单等场景的默认门店。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { storeApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { navBack, navTo } from '@/utils/nav'
import { eventValue } from '@/utils/event'
import { toast, toastSuccess } from '@/utils/ui'
import type { Store } from '@/types/store'

const app = useAppStore()

const keyword = ref('')
const stores = ref<Store[]>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const searching = ref(false)

const currentId = computed(() => app.store.id)

async function load(): Promise<void> {
  state.value = 'loading'
  try {
    stores.value = await storeApi.getStores({ keyword: keyword.value.trim() || undefined })
    state.value = stores.value.length ? 'success' : 'empty'
  } catch {
    state.value = 'error'
  }
}

onLoad(() => {
  void load()
})

function onInput(e: Event): void {
  keyword.value = eventValue(e)
}

async function onSearch(): Promise<void> {
  if (searching.value) return
  searching.value = true
  await load()
  searching.value = false
}

function onClear(): void {
  keyword.value = ''
  void load()
}

function onPick(s: Store): void {
  if (s.id === currentId.value) {
    toast('已经是当前门店')
    return
  }
  if (s.businessStatus !== 1) {
    toast('该门店已打烊，暂不可选')
    return
  }
  app.setStore({
    id: s.id,
    name: s.name,
    shortName: s.name.replace(/^星络灵侍馆\(|\)$/g, ''),
    address: s.address,
  })
  toastSuccess(`已切换到 ${s.name}`)
}

function onCall(s: Store): void {
  if (!s.phone) {
    toast('该门店暂未提供电话')
    return
  }
  uni.makePhoneCall({
    phoneNumber: s.phone.replace(/\s/g, ''),
    fail: () => toast(`门店电话：${s.phone}`),
  })
}

function onNavigate(s: Store): void {
  if (!s.latitude || !s.longitude) {
    uni.showModal({
      title: s.name,
      content: s.address,
      confirmText: '复制地址',
      confirmColor: '#5B5BD6',
      success: (r) => {
        if (r.confirm) {
          uni.setClipboardData({ data: s.address, success: () => toast('地址已复制') })
        }
      },
    })
    return
  }
  uni.openLocation({
    latitude: s.latitude,
    longitude: s.longitude,
    name: s.name,
    address: s.address,
  })
}

function onReserve(s: Store): void {
  onPick(s)
  navTo('/pages/reservation/index')
}
</script>

<template>
  <view class="st page-root">
    <AppNavBar title="选择门店" show-back @back="navBack" />

    <!-- ==================== 搜索 ==================== -->
    <view class="search">
      <view class="search__box">
        <AppIcon name="search" :size="34" color="#8A8A99" />
        <input
          class="search__input"
          :value="keyword"
          placeholder="输入门店名称 / 地址"
          placeholder-class="search__ph"
          confirm-type="search"
          @input="onInput"
          @confirm="onSearch"
        />
        <view v-if="keyword" class="search__clear" @tap="onClear">
          <AppIcon name="close" :size="28" color="#B0B0BE" :stroke-width="2" />
        </view>
      </view>
      <text class="search__btn" @tap="onSearch">搜索</text>
    </view>

    <!-- ==================== 列表 ==================== -->
    <StateView
      v-if="state !== 'success'"
      :state="state"
      empty-text="没有找到匹配的门店"
      empty-desc="换个关键词试试"
      empty-icon="empty-search"
      @retry="load"
    />

    <view v-else class="list">
      <view v-for="s in stores" :key="s.id" class="sc">
        <image class="sc__img" :src="s.cover" mode="aspectFill" />

        <view class="sc__body">
          <view class="sc__head">
            <text class="sc__name">{{ s.name }}</text>
            <view v-if="s.id === currentId" class="sc__current">
              <text class="sc__current-text">当前</text>
            </view>
          </view>

          <text class="sc__addr">{{ s.address }}</text>

          <view class="sc__meta">
            <text class="sc__hours">{{ s.businessHours }}</text>
            <text class="sc__free" :class="{ 'is-closed': s.businessStatus !== 1 }">
              {{ s.businessStatus === 1 ? `空闲 ${s.freeSeats} 台` : '已打烊' }}
            </text>
          </view>

          <view class="sc__tags">
            <text v-for="t in s.tags" :key="t" class="sc__tag">{{ t }}</text>
          </view>

          <view class="sc__actions">
            <view class="sc__icon-btn" @tap="onNavigate(s)">
              <AppIcon name="location" :size="32" color="#5B5BD6" />
              <text class="sc__icon-text">导航</text>
            </view>
            <view class="sc__icon-btn" @tap="onCall(s)">
              <AppIcon name="phone" :size="32" color="#5B5BD6" />
              <text class="sc__icon-text">电话</text>
            </view>
            <AppButton
              :type="s.id === currentId ? 'soft' : 'primary'"
              size="sm"
              class="sc__btn"
              :disabled="s.businessStatus !== 1"
              @tap="onReserve(s)"
            >
              {{ s.id === currentId ? '去预约' : '选择并预约' }}
            </AppButton>
          </view>
        </view>
      </view>

      <view class="list__foot">
        <text class="list__foot-text">共 {{ stores.length }} 家门店</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.st {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

/* ==================== 搜索 ==================== */
.search {
  display: flex;
  align-items: center;
  padding: 8rpx $gap-page 20rpx;
  background: #ffffff;
}

.search__box {
  flex: 1;
  min-width: 0;
  height: 76rpx;
  border-radius: $radius-pill;
  background: $card-bg-soft;
  display: flex;
  align-items: center;
  padding: 0 24rpx;
}

.search__input {
  flex: 1;
  min-width: 0;
  margin-left: 12rpx;
  height: 100%;
  font-size: $fs-md;
  color: $text-primary;
}

.search__ph {
  color: $text-placeholder;
  font-size: $fs-md;
}

.search__clear {
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.search__btn {
  margin-left: 20rpx;
  flex-shrink: 0;
  font-size: $fs-md;
  color: $brand-primary;
  font-weight: 600;

  &:active {
    opacity: 0.6;
  }
}

/* ==================== 列表 ==================== */
.list {
  padding: 20rpx $gap-page 0;
}

.sc {
  display: flex;
  align-items: flex-start;
  margin-bottom: 20rpx;
  padding: 24rpx 20rpx;
  background: #ffffff;
  border-radius: $radius-lg;
}

.sc__img {
  width: 200rpx;
  height: 200rpx;
  border-radius: $radius-md;
  flex-shrink: 0;
  background: $card-bg-soft;
}

.sc__body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.sc__head {
  display: flex;
  align-items: center;
}

.sc__name {
  flex: 1;
  min-width: 0;
  font-size: $fs-md;
  font-weight: 700;
  color: $text-primary;
  @include ellipsis;
}

.sc__current {
  flex-shrink: 0;
  margin-left: 10rpx;
  padding: 2rpx 12rpx;
  border-radius: $radius-xs;
  background: $brand-primary-soft;
}

.sc__current-text {
  font-size: $fs-xs;
  color: $brand-primary;
  font-weight: 600;
}

.sc__addr {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-secondary;
  @include ellipsis;
}

.sc__meta {
  display: flex;
  align-items: center;
  margin-top: 10rpx;
}

.sc__hours {
  font-size: $fs-xs;
  color: $text-placeholder;
}

.sc__free {
  margin-left: 14rpx;
  font-size: $fs-xs;
  color: $success;

  &.is-closed {
    color: $danger;
  }
}

.sc__tags {
  display: flex;
  flex-wrap: wrap;
  margin-top: 10rpx;
}

.sc__tag {
  margin: 0 10rpx 8rpx 0;
  padding: 2rpx 12rpx;
  border-radius: $radius-xs;
  background: $card-bg-soft;
  font-size: $fs-xs;
  color: $text-secondary;
}

.sc__actions {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}

.sc__icon-btn {
  display: flex;
  align-items: center;
  margin-right: 24rpx;

  &:active {
    opacity: 0.6;
  }
}

.sc__icon-text {
  margin-left: 6rpx;
  font-size: $fs-sm;
  color: $brand-primary;
}

.sc__btn {
  margin-left: auto;
  flex-shrink: 0;
}

.list__foot {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20rpx 0 10rpx;
}

.list__foot-text {
  font-size: $fs-sm;
  color: $text-placeholder;
}
</style>
