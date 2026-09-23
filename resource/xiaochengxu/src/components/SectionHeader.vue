<script setup lang="ts">
/**
 * 区块标题（截图里的「超级福利·活动专区」「我的订单」「热门任务」）。
 */
import AppIcon from './AppIcon.vue'

withDefaults(
  defineProps<{
    title: string
    /** 标题前的图标名 */
    icon?: string
    /** 图标颜色 */
    iconColor?: string
    /** 右侧说明文字，如「全部订单」 */
    moreText?: string
    /** 是否显示右侧箭头 */
    showArrow?: boolean
    /** 右侧问号说明按钮 */
    showHelp?: boolean
  }>(),
  { icon: '', iconColor: '#5B5BD6', moreText: '', showArrow: true, showHelp: false }
)

const emit = defineEmits<{ (e: 'more'): void; (e: 'help'): void }>()
</script>

<template>
  <view class="sec">
    <view class="sec__left">
      <AppIcon v-if="icon" :name="icon" :size="38" :color="iconColor" class="sec__icon" />
      <text class="sec__title">{{ title }}</text>
      <view v-if="showHelp" class="sec__help" @tap="emit('help')">
        <AppIcon name="question" :size="30" color="#B0B0BE" :stroke-width="1.6" />
      </view>
    </view>

    <view v-if="moreText" class="sec__more" @tap="emit('more')">
      <text class="sec__more-text">{{ moreText }}</text>
      <view v-if="showArrow" class="sec__arrow" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.sec {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48rpx;
}

.sec__left {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.sec__icon {
  margin-right: 12rpx;
}

.sec__title {
  font-size: $fs-xl;
  font-weight: 700;
  color: $text-primary;
  @include ellipsis;
}

.sec__help {
  margin-left: 12rpx;
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sec__more {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  padding-left: 16rpx;

  &:active {
    opacity: 0.6;
  }
}

.sec__more-text {
  font-size: $fs-base;
  color: $text-secondary;
}

/* CSS 画的右箭头，避免图标 data URI 在小尺寸下糊掉 */
.sec__arrow {
  width: 14rpx;
  height: 14rpx;
  margin-left: 8rpx;
  border-top: 3rpx solid $text-placeholder;
  border-right: 3rpx solid $text-placeholder;
  transform: rotate(45deg);
  border-radius: 2rpx;
}
</style>
