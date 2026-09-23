<script setup lang="ts">
/**
 * 自定义导航栏。
 * 截图里每个页面的顶部都不一样（服务页是「耳机 + 居中标题 + 更多」，
 * 游戏页是左对齐白字），所以这里做成带插槽的通用壳。
 */
import AppStatusBar from './AppStatusBar.vue'

withDefaults(
  defineProps<{
    title?: string
    /** 标题对齐方式 */
    align?: 'center' | 'left'
    /** 文字颜色主题 */
    theme?: 'light' | 'dark'
    /** 是否显示返回按钮 */
    showBack?: boolean
    /** 背景（渐变或纯色），空则透明 */
    background?: string
    /** 是否给微信右上角胶囊预留空间 */
    reserveCapsule?: boolean
  }>(),
  {
    title: '',
    align: 'center',
    theme: 'light',
    showBack: false,
    background: 'transparent',
    reserveCapsule: true,
  }
)

const emit = defineEmits<{ (e: 'back'): void }>()
</script>

<template>
  <view class="nav-root" :style="{ background }">
    <AppStatusBar />
    <view class="nav-row">
      <!-- 左侧 -->
      <view class="nav-side nav-side--left">
        <view v-if="showBack" class="nav-back" @tap="emit('back')">
          <view class="nav-back__icon" />
        </view>
        <slot name="left" />
      </view>

      <!-- 标题 -->
      <view class="nav-center" :class="[`is-${align}`, `is-${theme}`]">
        <text v-if="title" class="nav-title">{{ title }}</text>
        <slot name="center" />
      </view>

      <!-- 右侧 -->
      <view class="nav-side nav-side--right">
        <slot name="right" />
        <!-- #ifdef MP-WEIXIN -->
        <view v-if="reserveCapsule" class="nav-capsule-space" />
        <!-- #endif -->
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.nav-root {
  position: relative;
  width: 100%;
}

.nav-row {
  position: relative;
  height: 88rpx;
  display: flex;
  align-items: center;
  padding: 0 $gap-page;
}

.nav-side {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  min-width: 88rpx;
}

.nav-side--right {
  justify-content: flex-end;
  margin-left: auto;
}

.nav-center {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  pointer-events: none;

  &.is-center {
    justify-content: center;
  }

  &.is-left {
    justify-content: flex-start;
    padding-left: $gap-page;
  }
}

.nav-title {
  font-size: $fs-xl;
  font-weight: 600;
  letter-spacing: 1rpx;
}

.is-light .nav-title {
  color: $text-primary;
}

.is-dark .nav-title {
  color: #ffffff;
}

.nav-back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: -12rpx;
}

/* 用纯 CSS 画返回箭头，避免依赖图标组件的异步 data URI */
.nav-back__icon {
  width: 20rpx;
  height: 20rpx;
  border-left: 4rpx solid $text-primary;
  border-bottom: 4rpx solid $text-primary;
  transform: rotate(45deg);
  border-radius: 2rpx;
}

/* 微信右上角胶囊占位 */
.nav-capsule-space {
  width: 180rpx;
  flex-shrink: 0;
}
</style>
