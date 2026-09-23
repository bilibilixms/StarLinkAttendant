<script setup lang="ts">
/**
 * 三态占位：加载中 / 空数据 / 加载失败。
 * 保证任何页面都不会白屏，也不会出现「什么都没有也不说为什么」。
 */
import AppIcon from './AppIcon.vue'

withDefaults(
  defineProps<{
    /** 当前状态 */
    state: 'loading' | 'empty' | 'error'
    /** 空状态文案 */
    emptyText?: string
    /** 空状态补充说明 */
    emptyDesc?: string
    /** 空状态图标名 */
    emptyIcon?: string
    /** 错误文案 */
    errorText?: string
    /** 是否显示重试按钮 */
    retry?: boolean
  }>(),
  {
    emptyText: '这里还是空的',
    emptyDesc: '',
    emptyIcon: 'empty-box',
    errorText: '加载失败，请稍后重试',
    retry: true,
  }
)

const emit = defineEmits<{ (e: 'retry'): void }>()
</script>

<template>
  <!-- 加载中 -->
  <view v-if="state === 'loading'" class="state">
    <view class="state__spinner" />
    <text class="state__text">加载中...</text>
  </view>

  <!-- 加载失败 -->
  <view v-else-if="state === 'error'" class="state">
    <AppIcon name="empty-network" :size="128" color="#C6C9D6" />
    <text class="state__title">{{ errorText }}</text>
    <view v-if="retry" class="state__btn" @tap="emit('retry')">
      <text class="state__btn-text">重新加载</text>
    </view>
  </view>

  <!-- 空数据 -->
  <view v-else class="state">
    <AppIcon :name="emptyIcon" :size="128" color="#C6C9D6" />
    <text class="state__title">{{ emptyText }}</text>
    <text v-if="emptyDesc" class="state__desc">{{ emptyDesc }}</text>
    <slot name="action" />
  </view>
</template>

<style lang="scss" scoped>
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100rpx 60rpx;
  width: 100%;
}

.state__spinner {
  width: 56rpx;
  height: 56rpx;
  border: 5rpx solid rgba(91, 91, 214, 0.15);
  border-top-color: $brand-primary;
  border-radius: 50%;
  animation: st-spin 0.75s linear infinite;
}

@keyframes st-spin {
  to {
    transform: rotate(360deg);
  }
}

.state__text {
  margin-top: 24rpx;
  font-size: $fs-base;
  color: $text-secondary;
}

.state__title {
  margin-top: 24rpx;
  font-size: $fs-md;
  color: $text-secondary;
  text-align: center;
}

.state__desc {
  margin-top: 12rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  text-align: center;
}

.state__btn {
  margin-top: 32rpx;
  padding: 16rpx 48rpx;
  border-radius: $radius-pill;
  border: 2rpx solid $brand-primary;

  &:active {
    opacity: 0.7;
  }
}

.state__btn-text {
  font-size: $fs-base;
  color: $brand-primary;
}
</style>
