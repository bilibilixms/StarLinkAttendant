<script setup lang="ts">
/**
 * 会员福利。
 */
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { couponApi, storeApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { navBack, navTo, goLogin } from '@/utils/nav'
import { toast, toastSuccess } from '@/utils/ui'
import { formatMoney, formatThousands } from '@/utils/format'
import type { CouponTemplate } from '@/types/coupon'

const user = useUserStore()

const templates = ref<CouponTemplate[]>([])
const activities = ref<Array<{ key: string; title: string; subtitle: string; action: string; theme: string }>>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const receivingId = ref(0)

const BENEFITS = [
  { key: 'price', title: '会员价', desc: '商品与房型享会员专属价', icon: 'coupon', color: '#FF6B8A' },
  { key: 'points', title: '消费返积分', desc: '消费 1 元累计 1 积分', icon: 'coin', color: '#FF9F43' },
  { key: 'discount', title: '上机折扣', desc: '钻石会员上机 85 折', icon: 'crown', color: '#F5B301' },
  { key: 'birthday', title: '生日礼包', desc: '生日当月赠网费与券', icon: 'gift', color: '#8B6BFF' },
  { key: 'priority', title: '优先订座', desc: '热门时段优先锁定机位', icon: 'svc-seat', color: '#3AC6C6' },
  { key: 'hotel', title: '酒店权益', desc: '电竞酒店房型会员价', icon: 'svc-hotel', color: '#4A9BFF' },
]

async function load(): Promise<void> {
  state.value = 'loading'
  try {
    const [t, a] = await Promise.all([couponApi.getCouponTemplates(), storeApi.getHomeActivities()])
    templates.value = t
    activities.value = a
    state.value = t.length ? 'success' : 'empty'
  } catch {
    state.value = 'error'
  }
}

onLoad(() => {
  void load()
})

onShow(() => {
  // 后端无 summary 接口；福利页内容由 onLoad 触发的 load 拉取
})

function couponValue(t: CouponTemplate): string {
  return t.type === 1 ? `¥${formatMoney(t.value)}` : `${t.value}折`
}

async function onReceive(t: CouponTemplate): Promise<void> {
  if (!user.isLogin) {
    goLogin('/pages/welfare/index')
    return
  }
  if (receivingId.value) return
  receivingId.value = t.id
  try {
    await couponApi.receiveCoupon(t.id)
    toastSuccess('领取成功，已放入卡包')
    void load()
  } catch {
    /* request 层已提示 */
  } finally {
    receivingId.value = 0
  }
}

function onActivity(key: string): void {
  if (key === 'sign') navTo('/pages/mine/index')
  else if (key === 'treasure') navTo('/pages/activity/treasure')
  else if (key === 'invite') toast('分享小程序给好友，双方各得网费')
  else toast('请在微信内打开滴滴出行小程序')
}

function onBenefit(key: string): void {
  if (key === 'price' || key === 'points') navTo('/pages/points/index')
  else if (key === 'discount' || key === 'priority') navTo('/pages/reservation/index')
  else if (key === 'hotel') navTo('/pages/hotel/index')
  else toast('生日当月自动发放，请留意站内通知')
}
</script>

<template>
  <view class="wf page-root">
    <AppNavBar title="会员福利" show-back @back="navBack" />

    <!-- ==================== 等级卡 ==================== -->
    <view class="level-card">
      <view class="level-card__head">
        <view class="level-card__avatar">
          <text class="level-card__avatar-text">{{ user.isLogin ? user.nickname.slice(0, 1) : '网' }}</text>
        </view>
        <view class="level-card__info">
          <text class="level-card__name">{{ user.isLogin ? user.nickname : '未登录' }}</text>
          <view class="level-card__badge">
            <AppIcon name="crown" :size="26" color="#7A4B00" />
            <text class="level-card__level">{{ user.isLogin ? user.levelName : '登录享会员权益' }}</text>
          </view>
        </view>
      </view>

      <view class="level-card__stats">
        <view class="level-card__stat">
          <text class="level-card__stat-value">{{ user.isLogin ? formatMoney(user.balance) : '--' }}</text>
          <text class="level-card__stat-label">余额</text>
        </view>
        <view class="level-card__stat-divider" />
        <view class="level-card__stat">
          <text class="level-card__stat-value">{{ user.isLogin ? formatThousands(user.points) : '--' }}</text>
          <text class="level-card__stat-label">积分</text>
        </view>
        <view class="level-card__stat-divider" />
        <view class="level-card__stat">
          <text class="level-card__stat-value">
            {{ user.isLogin ? (user.summary?.couponCount ?? 0) : '--' }}
          </text>
          <text class="level-card__stat-label">卡券</text>
        </view>
      </view>

      <AppButton
        v-if="!user.isLogin"
        type="gold"
        size="md"
        block
        class="level-card__btn"
        @tap="goLogin('/pages/welfare/index')"
      >
        立即登录
      </AppButton>
    </view>

    <!-- ==================== 会员权益 ==================== -->
    <view class="card sec">
      <text class="sec__title">会员权益</text>
      <view class="benefits">
        <view v-for="b in BENEFITS" :key="b.key" class="benefit" @tap="onBenefit(b.key)">
          <view class="benefit__icon">
            <AppIcon :name="b.icon" :size="52" :color="b.color" :stroke-width="1.6" />
          </view>
          <view class="benefit__body">
            <text class="benefit__title">{{ b.title }}</text>
            <text class="benefit__desc">{{ b.desc }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ==================== 活动 ==================== -->
    <view class="card sec">
      <text class="sec__title">活动专区</text>
      <view class="acts">
        <view
          v-for="a in activities"
          :key="a.key"
          class="act"
          @tap="onActivity(a.key)"
        >
          <view class="act__head">
            <text class="act__title">{{ a.title }}</text>
            <AppIcon name="arrow-right" :size="30" color="#B0B0BE" :stroke-width="2" />
          </view>
          <text class="act__sub">{{ a.subtitle }}</text>
          <text class="act__action">{{ a.action }} ›</text>
        </view>
      </view>
    </view>

    <!-- ==================== 领券 ==================== -->
    <view class="card sec">
      <text class="sec__title">领券中心</text>

      <StateView
        v-if="state !== 'success'"
        :state="state"
        empty-text="暂无可领取的券"
        empty-icon="empty-coupon"
        @retry="load"
      />

      <view v-else class="cps">
        <view v-for="t in templates" :key="t.id" class="cp">
          <view class="cp__left">
            <text class="cp__value">{{ couponValue(t) }}</text>
            <text class="cp__min">{{ t.minAmount > 0 ? `满${t.minAmount}可用` : '无门槛' }}</text>
          </view>
          <view class="cp__mid">
            <text class="cp__name">{{ t.name }}</text>
            <text class="cp__desc">{{ t.description || t.scopeText }}</text>
          </view>
          <AppButton
            :type="t.receivedCount >= t.limitPerMember ? 'soft' : 'primary'"
            size="sm"
            :loading="receivingId === t.id"
            :disabled="t.receivedCount >= t.limitPerMember"
            @tap="onReceive(t)"
          >
            {{ t.receivedCount >= t.limitPerMember ? '已领' : '领取' }}
          </AppButton>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.wf {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

/* ==================== 等级卡 ==================== */
.level-card {
  margin: 20rpx $gap-page 0;
  padding: 36rpx 28rpx 30rpx;
  border-radius: $radius-lg;
  background: linear-gradient(135deg, #3d3d4d 0%, #2a2a38 100%);
  box-shadow: 0 12rpx 30rpx rgba(40, 40, 70, 0.28);
}

.level-card__head {
  display: flex;
  align-items: center;
}

.level-card__avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffe08a 0%, #f7b733 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.level-card__avatar-text {
  font-size: 40rpx;
  font-weight: 800;
  color: #7a4b00;
}

.level-card__info {
  flex: 1;
  min-width: 0;
  margin-left: 22rpx;
}

.level-card__name {
  display: block;
  font-size: 38rpx;
  font-weight: 700;
  color: #ffffff;
  @include ellipsis;
}

.level-card__badge {
  display: inline-flex;
  align-items: center;
  margin-top: 10rpx;
  padding: 4rpx 16rpx 4rpx 10rpx;
  border-radius: $radius-pill;
  background: linear-gradient(135deg, #ffe08a 0%, #f7b733 100%);
}

.level-card__level {
  margin-left: 6rpx;
  font-size: $fs-sm;
  font-weight: 700;
  color: #7a4b00;
}

.level-card__stats {
  display: flex;
  align-items: center;
  margin-top: 32rpx;
  padding-top: 26rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.14);
}

.level-card__stat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.level-card__stat-value {
  font-size: 40rpx;
  font-weight: 800;
  color: #ffe9a8;
}

.level-card__stat-label {
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: rgba(255, 255, 255, 0.72);
}

.level-card__stat-divider {
  width: 1rpx;
  height: 52rpx;
  background: rgba(255, 255, 255, 0.14);
  flex-shrink: 0;
}

.level-card__btn {
  margin-top: 30rpx;
}

/* ==================== 区块 ==================== */
.sec {
  margin: 20rpx $gap-page 0;
  padding: 26rpx 24rpx;
}

.sec__title {
  display: block;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 20rpx;
}

/* ==================== 权益 ==================== */
.benefits {
  display: flex;
  flex-wrap: wrap;
}

.benefit {
  width: 48.5%;
  margin-bottom: 20rpx;
  display: flex;
  align-items: center;

  &:nth-child(odd) {
    margin-right: 3%;
  }

  &:active {
    opacity: 0.7;
  }
}

.benefit__icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: $radius-sm;
  background: $card-bg-soft;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.benefit__body {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
}

.benefit__title {
  display: block;
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis;
}

.benefit__desc {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  @include ellipsis;
}

/* ==================== 活动 ==================== */
.acts {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.act {
  width: 48.5%;
  margin-bottom: 18rpx;
  padding: 22rpx 20rpx;
  border-radius: $radius-md;
  background: $card-bg-soft;

  &:active {
    opacity: 0.85;
  }
}

.act__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.act__title {
  font-size: $fs-md;
  font-weight: 700;
  color: $text-primary;
}

.act__sub {
  display: block;
  margin-top: 10rpx;
  font-size: $fs-sm;
  color: $text-secondary;
  @include ellipsis;
}

.act__action {
  display: block;
  margin-top: 12rpx;
  font-size: $fs-sm;
  font-weight: 600;
  color: $brand-primary;
}

/* ==================== 券 ==================== */
.cps {
  margin-top: 4rpx;
}

.cp {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.cp__left {
  width: 140rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cp__value {
  font-size: 42rpx;
  font-weight: 800;
  color: $danger;
  line-height: 1.1;
}

.cp__min {
  margin-top: 6rpx;
  font-size: $fs-xs;
  color: $text-secondary;
}

.cp__mid {
  flex: 1;
  min-width: 0;
  margin: 0 18rpx;
}

.cp__name {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis;
}

.cp__desc {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  @include ellipsis;
}
</style>
