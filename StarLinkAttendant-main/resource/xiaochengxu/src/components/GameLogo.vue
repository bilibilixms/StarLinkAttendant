<script setup lang="ts">
/**
 * 游戏图标。
 *
 * 参考截图里是各家厂商的官方 logo（有版权，不能自带）。
 * 本组件做**双模**：
 * - 传了 `src`（真实 logo）→ 直接渲染图片；
 * - 没传 → 回退成「品牌色圆角方块 + 游戏短名」，保证页面不塌。
 *
 * 也就是说：拿到真实 logo 后只要往 src/static/images/game/ 放文件、
 * 在 src/mock/seed.ts 的 seedGames 里补上 logo 字段即可，页面无需改动。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    short: string
    color: string
    /** 真实 logo 路径，如 /static/images/game/logo-lol.png */
    src?: string
    size?: number
    active?: boolean
  }>(),
  { size: 96, active: false, src: '' }
)

const style = computed(() => ({
  width: `${props.size}rpx`,
  height: `${props.size}rpx`,
  backgroundColor: props.color,
}))

/** 短名越长字号越小，保证两三个字都不溢出 */
const fontSize = computed(() => {
  const n = props.short.length
  if (n <= 1) return props.size * 0.46
  if (n <= 2) return props.size * 0.34
  return props.size * 0.24
})
</script>

<template>
  <view class="glogo" :class="{ 'is-active': active }" :style="style">
    <!-- 有真实 logo 用真图 -->
    <image v-if="src" class="glogo__img" :src="src" mode="aspectFill" />

    <!-- 否则用品牌色块 + 短名兜底 -->
    <template v-else>
      <view class="glogo__shine" />
      <text class="glogo__text" :style="{ fontSize: `${fontSize}rpx` }">{{ short }}</text>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.glogo {
  position: relative;
  border-radius: 22rpx;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4rpx 12rpx rgba(20, 20, 60, 0.14);
}

.glogo__img {
  width: 100%;
  height: 100%;
}

.glogo__shine {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(150deg, rgba(255, 255, 255, 0.42) 0%, rgba(0, 0, 0, 0.2) 100%);
}

.glogo__text {
  position: relative;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 1rpx;
  text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.25);
  line-height: 1;
}
</style>
