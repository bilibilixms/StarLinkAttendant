<script setup lang="ts">
/**
 * 状态栏占位。所有自定义导航栏页面都必须先垫这一层，
 * 否则内容会被手机状态栏（时间 / 电量）压住。H5 下高度为 0。
 */
import { computed } from 'vue'
import { getStatusBarHeight } from '@/utils/system'

const props = withDefaults(defineProps<{ bg?: string }>(), { bg: 'transparent' })

const style = computed(() => ({
  height: `${getStatusBarHeight()}px`,
  background: props.bg,
}))
</script>

<template>
  <view class="status-bar" :style="style" />
</template>

<style lang="scss" scoped>
.status-bar {
  width: 100%;
  flex-shrink: 0;
}
</style>
