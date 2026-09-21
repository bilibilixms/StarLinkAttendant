<script setup lang="ts">
/**
 * 积分：明细 + 兑换。
 */
import { ref } from 'vue'
import { onLoad, onShow, onReachBottom } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { pointsApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { navBack, goLogin } from '@/utils/nav'
import { toast } from '@/utils/ui'
import { formatThousands, formatDateTime } from '@/utils/format'
import type { PointsGoods, PointsRecord } from '@/types/store'

const user = useUserStore()

type Tab = 'records' | 'exchange'
const tab = ref<Tab>('records')

const records = ref<PointsRecord[]>([])
const goods = ref<PointsGoods[]>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const page = ref(1)
const hasMore = ref(true)
const loadingMore = ref(false)
const exchangingId = ref(0)

const TYPE_TEXT: Record<number, string> = {
  1: '消费获得',
  2: '活动获得',
  3: '积分兑换',
  4: '积分过期',
}

async function load(reset = false): Promise<void> {
  if (!user.isLogin || !user.member) {
    state.value = 'empty'
    return
  }
  if (reset) {
    page.value = 1
    hasMore.value = true
  }
  if (state.value !== 'success') state.value = 'loading'
  try {
    if (tab.value === 'records') {
      const res = await pointsApi.getPointsRecords(user.member.id, {
        current: page.value,
        size: 15,
      })
      records.value = page.value === 1 ? res.records : [...records.value, ...res.records]
      hasMore.value = page.value < res.pages
      state.value = records.value.length ? 'success' : 'empty'
    } else {
      // 后端未提供积分商品接口，直接展示空列表
      goods.value = []
      state.value = 'empty'
    }
  } catch {
    if (!records.value.length) state.value = 'error'
  }
}

function switchTab(next: Tab): void {
  if (tab.value === next) return
  tab.value = next
  void load(true)
}

onLoad(() => {
  void load(true)
})

onShow(() => {
  // 后端无 summary 接口；积分明细由 onLoad 触发的 load 拉取
})

onReachBottom(async () => {
  if (tab.value !== 'records' || !hasMore.value || loadingMore.value) return
  loadingMore.value = true
  page.value += 1
  await load()
  loadingMore.value = false
})

/* ==================== 兑换 ==================== */

async function onExchange(g: PointsGoods): Promise<void> {
  // 后端未提供积分兑换接口，仅占位提示
  toast('积分兑换功能开发中')
}
</script>

<template>
  <view class="pt page-root">
    <AppNavBar title="我的积分" show-back @back="navBack" />

    <!-- ==================== 积分卡 ==================== -->
    <view class="pt__hero">
      <text class="pt__hero-label">可用积分</text>
      <text class="pt__hero-value">{{ user.isLogin ? formatThousands(user.points) : '--' }}</text>
      <view class="pt__hero-foot">
        <text class="pt__hero-tip">消费 1 元累计 1 积分，积分可在兑换专区使用</text>
      </view>
    </view>

    <!-- ==================== Tab ==================== -->
    <view class="tabs">
      <view class="tab" :class="{ 'is-active': tab === 'records' }" @tap="switchTab('records')">
        <text class="tab__text">积分明细</text>
        <view v-if="tab === 'records'" class="tab__bar" />
      </view>
      <view class="tab" :class="{ 'is-active': tab === 'exchange' }" @tap="switchTab('exchange')">
        <text class="tab__text">积分兑换</text>
        <view v-if="tab === 'exchange'" class="tab__bar" />
      </view>
    </view>

    <!-- ==================== 未登录 ==================== -->
    <StateView
      v-if="!user.isLogin"
      state="empty"
      empty-text="登录后查看积分"
      empty-desc="登录网鱼会员即可累计积分"
      empty-icon="empty-box"
    >
      <template #action>
        <AppButton type="primary" size="md" @tap="goLogin('/pages/points/index')">立即登录</AppButton>
      </template>
    </StateView>

    <template v-else>
      <StateView
        v-if="state !== 'success'"
        :state="state"
        :empty-text="tab === 'records' ? '还没有积分记录' : '暂无可兑换商品'"
        @retry="load(true)"
      />

      <!-- ==================== 明细 ==================== -->
      <view v-else-if="tab === 'records'" class="list">
        <view v-for="r in records" :key="r.id" class="pr">
          <view class="pr__icon">
            <AppIcon
              :name="r.changePoints > 0 ? 'trophy' : 'refresh'"
              :size="40"
              :color="r.changePoints > 0 ? '#FF9F2E' : '#8A8A99'"
              :stroke-width="1.6"
            />
          </view>
          <view class="pr__info">
            <text class="pr__title">{{ r.title }}</text>
            <text v-if="r.description" class="pr__desc">{{ r.description }}</text>
            <text class="pr__time">{{ formatDateTime(r.createdAt, true) }}</text>
          </view>
          <view class="pr__right">
            <text class="pr__change" :class="r.changePoints > 0 ? 'is-gain' : 'is-cost'">
              {{ r.changePoints > 0 ? '+' : '' }}{{ formatThousands(r.changePoints) }}
            </text>
            <text class="pr__balance">余额 {{ formatThousands(r.balanceAfter) }}</text>
          </view>
        </view>

        <view class="list__foot">
          <text class="list__foot-text">
            {{ hasMore ? (loadingMore ? '加载中...' : '上拉加载更多') : '没有更多记录了' }}
          </text>
        </view>
      </view>

      <!-- ==================== 兑换 ==================== -->
      <view v-else class="goods">
        <view v-for="g in goods" :key="g.id" class="good">
          <image class="good__img" :src="g.cover" mode="aspectFill" />
          <text class="good__name">{{ g.name }}</text>
          <text v-if="g.description" class="good__desc">{{ g.description }}</text>
          <view class="good__foot">
            <view class="good__points">
              <text class="good__points-num">{{ formatThousands(g.points) }}</text>
              <text class="good__points-unit">积分</text>
            </view>
            <text class="good__stock">剩{{ g.stock }}</text>
          </view>
          <AppButton
            :type="user.points >= g.points ? 'primary' : 'soft'"
            size="sm"
            block
            :loading="exchangingId === g.id"
            :disabled="g.stock <= 0"
            @tap="onExchange(g)"
          >
            {{ g.stock <= 0 ? '已兑完' : user.points >= g.points ? '立即兑换' : '积分不足' }}
          </AppButton>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.pt {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

/* ==================== 积分卡 ==================== */
.pt__hero {
  margin: 20rpx $gap-page 0;
  padding: 40rpx 32rpx;
  border-radius: $radius-lg;
  background: linear-gradient(135deg, #ffb347 0%, #ff8a3d 55%, #ff6b6b 100%);
  display: flex;
  flex-direction: column;
  box-shadow: 0 12rpx 28rpx rgba(255, 138, 61, 0.28);
}

.pt__hero-label {
  font-size: $fs-base;
  color: rgba(255, 255, 255, 0.88);
}

.pt__hero-value {
  margin-top: 10rpx;
  font-size: 76rpx;
  font-weight: 800;
  color: #ffffff;
  line-height: 1.1;
}

.pt__hero-foot {
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.25);
}

.pt__hero-tip {
  font-size: $fs-sm;
  color: rgba(255, 255, 255, 0.85);
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

/* ==================== 明细 ==================== */
.list {
  padding: 8rpx $gap-page 0;
}

.pr {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-of-type {
    border-bottom: none;
  }
}

.pr__icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: $card-bg-soft;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.pr__info {
  flex: 1;
  min-width: 0;
  margin: 0 20rpx;
}

.pr__title {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis;
}

.pr__desc {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-secondary;
  @include ellipsis;
}

.pr__time {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.pr__right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.pr__change {
  font-size: 34rpx;
  font-weight: 700;
}

.pr__change.is-gain {
  color: #ff8a3d;
}

.pr__change.is-cost {
  color: $text-secondary;
}

.pr__balance {
  margin-top: 6rpx;
  font-size: $fs-sm;
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

/* ==================== 兑换 ==================== */
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

.good__desc {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  @include ellipsis;
}

.good__foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 14rpx 0;
}

.good__points {
  display: flex;
  align-items: baseline;
}

.good__points-num {
  font-size: 36rpx;
  font-weight: 800;
  color: #ff8a3d;
}

.good__points-unit {
  margin-left: 4rpx;
  font-size: $fs-xs;
  color: #ff8a3d;
}

.good__stock {
  font-size: $fs-xs;
  color: $text-placeholder;
}
</style>
