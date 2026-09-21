<script setup lang="ts">
/**
 * 天天夺宝活动页（首页 Banner 与「超级福利·活动专区」的落地页）。
 *
 * 说明：真实开奖逻辑属于后端活动模块（campaign / marketing），
 * 当前 `/api/applet/**` 尚未实现，所以这里只做活动展示与任务引流，
 * 「立即参与」跳到游戏任务页去赚取抽奖机会，不伪造开奖结果。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import { gameApi, storeApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { navBack, navTo, goLogin } from '@/utils/nav'
import { toast } from '@/utils/ui'
import type { GameTask } from '@/types/game'

const user = useUserStore()

interface Prize {
  id: number
  name: string
  cover: string
  /** 中奖概率展示用，真实概率由后端下发 */
  weight: string
}

const prizes = ref<Prize[]>([])
const tasks = ref<GameTask[]>([])
const activities = ref<Array<{ key: string; title: string; subtitle: string; action: string; theme: string }>>([])

/** 演示用：抽奖机会由后端下发，这里展示占位值 */
const chances = ref(0)

onLoad(async () => {
  const [t, a] = await Promise.allSettled([gameApi.getGameTasks(), storeApi.getHomeActivities()])
  if (t.status === 'fulfilled') {
    tasks.value = t.value.slice(0, 3)
    prizes.value = t.value.slice(0, 6).map((task, i) => ({
      id: task.id,
      name: task.reward,
      cover: task.thumbs[i % task.thumbs.length],
      weight: '—',
    }))
  }
  if (a.status === 'fulfilled') activities.value = a.value
})

function onJoin(): void {
  if (!user.isLogin) {
    goLogin('/pages/activity/treasure')
    return
  }
  navTo('/pages/game/index')
}

function goTask(id: number): void {
  navTo(`/pages/game/task?id=${id}`)
}

function onRules(): void {
  uni.showModal({
    title: '活动规则',
    content:
      '1. 活动期间在网鱼电竞小程序完成指定游戏任务即可获得夺宝机会\n' +
      '2. 每次夺宝消耗 1 次机会，奖品随机发放\n' +
      '3. 实物奖品请到门店前台凭中奖记录领取\n' +
      '4. 奖品数量有限，先到先得\n' +
      '5. 如发现作弊行为，网鱼有权取消中奖资格',
    showCancel: false,
    confirmColor: '#5B5BD6',
  })
}
</script>

<template>
  <view class="tr page-root">
    <AppNavBar title="天天夺宝" show-back theme="dark" @back="navBack" />

    <!-- ==================== 头部 ==================== -->
    <view class="hero">
      <view class="hero__glow" />
      <text class="hero__title">天天夺宝</text>
      <text class="hero__sub">天天赢超级大奖</text>

      <view class="hero__chance">
        <text class="hero__chance-label">我的夺宝机会</text>
        <view class="hero__chance-row">
          <text class="hero__chance-num">{{ user.isLogin ? chances : '--' }}</text>
          <text class="hero__chance-unit">次</text>
        </view>
        <text class="hero__chance-tip">完成下方游戏任务可获得夺宝机会</text>
      </view>

      <AppButton type="gold" size="lg" block class="hero__btn" @tap="onJoin">
        立即参与
      </AppButton>
    </view>

    <!-- ==================== 奖品池 ==================== -->
    <view class="card sec">
      <view class="flex-between">
        <text class="sec__title sec__title--inline">奖品池</text>
        <text class="sec__more" @tap="onRules">活动规则 ›</text>
      </view>

      <view class="prizes">
        <view v-for="p in prizes" :key="p.id" class="prize">
          <image class="prize__img" :src="p.cover" mode="aspectFill" />
          <text class="prize__name">{{ p.name }}</text>
        </view>
      </view>

      <text class="sec__hint">
        奖品每日限量发放，中奖结果由后端开奖后下发；当前后端活动接口尚未实现，仅展示奖品池。
      </text>
    </view>

    <!-- ==================== 赚取机会 ==================== -->
    <view class="card sec">
      <text class="sec__title">完成任务，赢夺宝机会</text>

      <view v-for="t in tasks" :key="t.id" class="task" @tap="goTask(t.id)">
        <image class="task__thumb" :src="t.thumbs[0]" mode="aspectFill" />
        <view class="task__body">
          <text class="task__title">{{ t.title }}</text>
          <text class="task__meta">{{ t.reward }} · {{ t.progress }}/{{ t.target }}{{ t.unit }}</text>
        </view>
        <AppButton type="soft" size="sm" @tap.stop="goTask(t.id)">去完成</AppButton>
      </view>

      <view class="sec__more-row" @tap="navTo('/pages/game/index')">
        <text class="sec__more-text">查看全部任务</text>
        <view class="sec__arrow" />
      </view>
    </view>

    <!-- ==================== 其他活动 ==================== -->
    <view class="card sec">
      <text class="sec__title">更多活动</text>
      <view class="acts">
        <view
          v-for="a in activities"
          :key="a.key"
          class="act"
          @tap="a.key === 'sign' ? navTo('/pages/mine/index') : navTo('/pages/welfare/index')"
        >
          <view class="act__head">
            <text class="act__title">{{ a.title }}</text>
            <AppIcon name="arrow-right" :size="30" color="#B0B0BE" :stroke-width="2" />
          </view>
          <text class="act__sub">{{ a.subtitle }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.tr {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
  background: $page-bg;
}

/* ==================== 头部 ==================== */
.hero {
  position: relative;
  margin: 0 0 20rpx;
  padding: 60rpx $gap-page 44rpx;
  background: linear-gradient(160deg, #b06bf5 0%, #8a5cf0 45%, #6c4be0 100%);
  overflow: hidden;
}

.hero__glow {
  position: absolute;
  right: -100rpx;
  top: -60rpx;
  width: 460rpx;
  height: 460rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 220, 120, 0.5) 0%, rgba(255, 220, 120, 0) 70%);
  pointer-events: none;
}

.hero__title {
  position: relative;
  display: block;
  font-size: 76rpx;
  font-weight: 900;
  color: #ffffff;
  letter-spacing: 6rpx;
  text-shadow: 0 6rpx 18rpx rgba(50, 10, 110, 0.4);
}

.hero__sub {
  position: relative;
  display: block;
  margin-top: 8rpx;
  font-size: $fs-md;
  color: rgba(255, 255, 255, 0.9);
}

.hero__chance {
  position: relative;
  margin-top: 36rpx;
  padding: 28rpx 24rpx;
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.16);
}

.hero__chance-label {
  font-size: $fs-base;
  color: rgba(255, 255, 255, 0.85);
}

.hero__chance-row {
  display: flex;
  align-items: baseline;
  margin-top: 6rpx;
}

.hero__chance-num {
  font-size: 72rpx;
  font-weight: 900;
  color: #ffe9a8;
  line-height: 1.1;
}

.hero__chance-unit {
  margin-left: 8rpx;
  font-size: $fs-lg;
  color: #ffe9a8;
}

.hero__chance-tip {
  display: block;
  margin-top: 10rpx;
  font-size: $fs-sm;
  color: rgba(255, 255, 255, 0.78);
}

.hero__btn {
  position: relative;
  margin-top: 32rpx;
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

.sec__title--inline {
  margin-bottom: 0;
}

.sec__more {
  font-size: $fs-base;
  color: $brand-primary;

  &:active {
    opacity: 0.6;
  }
}

.sec__hint {
  display: block;
  margin-top: 20rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  line-height: 1.6;
}

/* ==================== 奖品 ==================== */
.prizes {
  display: flex;
  flex-wrap: wrap;
  margin-top: 4rpx;
}

.prize {
  width: 31.5%;
  margin: 0 2% 20rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  &:nth-child(3n) {
    margin-right: 0;
  }
}

.prize__img {
  width: 100%;
  height: 170rpx;
  border-radius: $radius-md;
  background: $card-bg-soft;
}

.prize__name {
  margin-top: 12rpx;
  font-size: $fs-sm;
  color: $text-regular;
  text-align: center;
  width: 100%;
  @include ellipsis;
}

/* ==================== 任务 ==================== */
.task {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $divider;

  &:active {
    opacity: 0.85;
  }
}

.task__thumb {
  width: 96rpx;
  height: 96rpx;
  border-radius: $radius-sm;
  flex-shrink: 0;
  background: $card-bg-soft;
}

.task__body {
  flex: 1;
  min-width: 0;
  margin: 0 18rpx;
}

.task__title {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis-multi(2);
}

.task__meta {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  @include ellipsis;
}

.sec__more-row {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 26rpx 0 6rpx;

  &:active {
    opacity: 0.6;
  }
}

.sec__more-text {
  font-size: $fs-md;
  color: $brand-primary;
}

.sec__arrow {
  width: 14rpx;
  height: 14rpx;
  margin-left: 10rpx;
  border-top: 3rpx solid $brand-primary;
  border-right: 3rpx solid $brand-primary;
  transform: rotate(45deg);
  border-radius: 2rpx;
}

/* ==================== 其他活动 ==================== */
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
</style>
