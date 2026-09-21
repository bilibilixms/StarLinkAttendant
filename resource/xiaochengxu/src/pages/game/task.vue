<script setup lang="ts">
/**
 * 游戏任务详情。
 */
import { computed, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import GameLogo from '@/components/GameLogo.vue'
import StateView from '@/components/StateView.vue'
import { gameApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { navBack, goLogin } from '@/utils/nav'
import { toast, toastSuccess, confirm } from '@/utils/ui'
import { formatCountdown } from '@/utils/format'
import type { GameTaskDetail } from '@/types/game'

const user = useUserStore()

const task = ref<GameTaskDetail | null>(null)
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const taskId = ref(0)
const claiming = ref(false)
const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | null = null

const progressPercent = computed(() => {
  const t = task.value
  if (!t || !t.target) return 0
  return Math.min(100, Math.round((t.progress / t.target) * 100))
})

const endText = computed(() => {
  const t = task.value
  if (!t) return ''
  if (t.endTimestamp) {
    const rest = Math.floor((t.endTimestamp - now.value) / 1000)
    return rest <= 0 ? '活动已结束' : `${formatCountdown(rest)} 后结束`
  }
  return t.endText
})

const claimed = computed(() => task.value?.status === 1)

async function load(id: number): Promise<void> {
  state.value = 'loading'
  try {
    task.value = await gameApi.getGameTaskDetail(id)
    state.value = 'success'
  } catch {
    state.value = 'error'
  }
}

onLoad((query) => {
  taskId.value = Number(query?.id ?? 0)
  if (!taskId.value) {
    state.value = 'error'
    return
  }
  void load(taskId.value)
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

/** 预览奖励图（模板里不能直接调 uni.*，必须包一层） */
function previewReward(index: number): void {
  if (!task.value) return
  uni.previewImage({ urls: task.value.thumbs, current: index })
}

async function onClaim(): Promise<void> {
  if (!task.value || claiming.value) return
  if (!user.isLogin) {
    goLogin(`/pages/game/task?id=${taskId.value}`)
    return
  }
  if (claimed.value) {
    toast('奖励已领取')
    return
  }
  const ok = await confirm(
    `确认领取「${task.value.reward}」？奖励将在 24 小时内发放到游戏账号。`,
    '领取奖励',
    '确认领取'
  )
  if (!ok) return

  claiming.value = true
  try {
    const res = await gameApi.joinGameTask(task.value.id)
    toastSuccess(res.message)
    void load(taskId.value)
  } catch {
    /* request 层已提示 */
  } finally {
    claiming.value = false
  }
}
</script>

<template>
  <view class="gt page-root">
    <AppNavBar title="任务详情" show-back @back="navBack" />

    <StateView v-if="state !== 'success'" :state="state" @retry="load(taskId)" />

    <template v-else-if="task">
      <!-- ==================== 头部 ==================== -->
      <view class="hero">
        <view class="hero__head">
          <GameLogo :short="task.gameName.slice(0, 2)" color="#5B5BD6" :size="92" />
          <view class="hero__info">
            <text class="hero__game">{{ task.gameName }}</text>
            <text class="hero__reward">奖励：{{ task.reward }}</text>
          </view>
        </view>

        <text class="hero__title">{{ task.title }}</text>

        <!-- 进度 -->
        <view class="progress">
          <view class="progress__bar">
            <view class="progress__fill" :style="{ width: `${progressPercent}%` }" />
          </view>
          <text class="progress__text">{{ task.progress }} / {{ task.target }} {{ task.unit }}</text>
        </view>

        <view class="hero__meta">
          <text class="hero__end">{{ endText }}</text>
          <text v-if="task.remainCount > 0" class="hero__remain">剩余 {{ task.remainCount }} 份</text>
        </view>
      </view>

      <!-- ==================== 缩略图 ==================== -->
      <view class="card sec">
        <text class="sec__title">奖励预览</text>
        <view class="thumbs">
          <image
            v-for="(t, i) in task.thumbs"
            :key="i"
            class="thumbs__item"
            :src="t"
            mode="aspectFill"
            @tap="previewReward(i)"
          />
        </view>
      </view>

      <!-- ==================== 任务说明 ==================== -->
      <view class="card sec">
        <text class="sec__title">任务说明</text>
        <text class="sec__desc">{{ task.description }}</text>
      </view>

      <!-- ==================== 完成步骤 ==================== -->
      <view class="card sec">
        <text class="sec__title">如何完成</text>
        <view v-for="(s, i) in task.steps" :key="i" class="step">
          <view class="step__index">
            <text class="step__index-text">{{ i + 1 }}</text>
          </view>
          <text class="step__text">{{ s }}</text>
        </view>
      </view>

      <!-- ==================== 活动规则 ==================== -->
      <view class="card sec">
        <text class="sec__title">活动规则</text>
        <view v-for="(r, i) in task.rules" :key="i" class="rule">
          <text class="rule__dot">·</text>
          <text class="rule__text">{{ r }}</text>
        </view>
      </view>

      <!-- ==================== 底部 ==================== -->
      <view class="gt__bar">
        <view class="gt__bar-info">
          <text class="gt__bar-label">任务进度</text>
          <text class="gt__bar-value">{{ task.progress }}/{{ task.target }}</text>
        </view>
        <AppButton
          :type="claimed ? 'soft' : 'primary'"
          size="lg"
          class="gt__bar-btn"
          :loading="claiming"
          :disabled="claimed"
          @tap="onClaim"
        >
          {{ claimed ? '已领取' : '去完成' }}
        </AppButton>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.gt {
  padding-bottom: 180rpx;
  @include safe-area-bottom(0rpx);
}

/* ==================== 头部 ==================== */
.hero {
  margin: 20rpx $gap-page 0;
  padding: 32rpx 28rpx;
  border-radius: $radius-lg;
  background: $brand-gradient;
  box-shadow: 0 12rpx 28rpx rgba(91, 91, 214, 0.26);
}

.hero__head {
  display: flex;
  align-items: center;
}

.hero__info {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.hero__game {
  display: block;
  font-size: $fs-lg;
  font-weight: 700;
  color: #ffffff;
}

.hero__reward {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-base;
  color: rgba(255, 255, 255, 0.85);
}

.hero__title {
  display: block;
  margin-top: 26rpx;
  font-size: 38rpx;
  font-weight: 800;
  color: #ffffff;
  line-height: 1.35;
}

.progress {
  margin-top: 28rpx;
}

.progress__bar {
  height: 16rpx;
  border-radius: 8rpx;
  background: rgba(255, 255, 255, 0.28);
  overflow: hidden;
}

.progress__fill {
  height: 100%;
  border-radius: 8rpx;
  background: linear-gradient(90deg, #ffe08a 0%, #ffb347 100%);
  transition: width 0.3s ease;
}

.progress__text {
  display: block;
  margin-top: 12rpx;
  font-size: $fs-base;
  color: rgba(255, 255, 255, 0.9);
}

.hero__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.22);
}

.hero__end {
  font-size: $fs-base;
  color: rgba(255, 255, 255, 0.9);
}

.hero__remain {
  font-size: $fs-base;
  color: #ffe9a8;
  font-weight: 600;
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
  margin-bottom: 18rpx;
}

.sec__desc {
  display: block;
  font-size: $fs-md;
  color: $text-regular;
  line-height: 1.7;
}

/* ==================== 缩略图 ==================== */
.thumbs {
  display: flex;
  justify-content: space-between;
}

.thumbs__item {
  width: 23.5%;
  height: 170rpx;
  border-radius: $radius-md;
  background: $card-bg-soft;
}

/* ==================== 步骤 ==================== */
.step {
  display: flex;
  align-items: flex-start;
  margin-bottom: 18rpx;

  &:last-child {
    margin-bottom: 0;
  }
}

.step__index {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: $brand-primary-soft;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step__index-text {
  font-size: $fs-sm;
  font-weight: 700;
  color: $brand-primary;
}

.step__text {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
  font-size: $fs-md;
  color: $text-regular;
  line-height: 1.6;
}

/* ==================== 规则 ==================== */
.rule {
  display: flex;
  align-items: flex-start;
  margin-bottom: 12rpx;

  &:last-child {
    margin-bottom: 0;
  }
}

.rule__dot {
  width: 20rpx;
  flex-shrink: 0;
  font-size: $fs-md;
  color: $text-placeholder;
  line-height: 1.7;
}

.rule__text {
  flex: 1;
  min-width: 0;
  font-size: $fs-base;
  color: $text-secondary;
  line-height: 1.7;
}

/* ==================== 底部 ==================== */
.gt__bar {
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

.gt__bar-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.gt__bar-label {
  font-size: $fs-sm;
  color: $text-secondary;
}

.gt__bar-value {
  margin-top: 4rpx;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
}

.gt__bar-btn {
  flex-shrink: 0;
  min-width: 260rpx;
}
</style>
