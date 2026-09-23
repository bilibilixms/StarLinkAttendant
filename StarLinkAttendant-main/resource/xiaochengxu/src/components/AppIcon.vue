<script setup lang="ts">
/**
 * 图标组件。
 * 微信小程序不支持内联 <svg>，所以用 background-image + data URI 渲染（见 icons.ts）。
 */
import { computed } from 'vue'
import { iconDataUri } from './icons'

const props = withDefaults(
  defineProps<{
    name: string
    /** 数字按 rpx 处理，字符串按原样作为 CSS 长度 */
    size?: number | string
    color?: string
    strokeWidth?: number
  }>(),
  { size: 40, color: '#4A4A52', strokeWidth: 1.7 }
)

const toCss = (v: number | string): string => (typeof v === 'number' ? `${v}rpx` : v)

const style = computed(() => ({
  width: toCss(props.size),
  height: toCss(props.size),
  backgroundImage: `url("${iconDataUri(props.name, props.color, props.strokeWidth)}")`,
}))
</script>

<template>
  <view class="app-icon" :style="style" />
</template>

<style lang="scss" scoped>
.app-icon {
  display: inline-block;
  flex-shrink: 0;
  background-repeat: no-repeat;
  background-position: center;
  background-size: 100% 100%;
}
</style>
