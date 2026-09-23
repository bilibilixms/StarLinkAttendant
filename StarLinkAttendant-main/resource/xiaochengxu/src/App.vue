<script setup lang="ts">
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { initApiMode } from '@/api/request'

onLaunch(() => {
  // 打印一次当前数据源，方便排查「为什么走的是 mock」
  initApiMode()
  // 从本地缓存恢复登录态（token / 会员信息）
  useUserStore().restore()
})

onShow(() => {
  /* 预留：切前台时刷新当前上机会话，接入真实后端后启用 */
})
</script>

<template>
  <!-- uni-app 的 App.vue 不渲染任何内容 -->
</template>

<style lang="scss">
/**
 * 全局样式。
 *
 * 注意：uni-app 会把 src/uni.scss 的内容自动注入到每个 <style lang="scss"> 的最前面，
 * 所以这里不能写 @use / @import（否则会报 "@use rules must be written before any other rules"）。
 * 全局样式只能直接写规则 —— 变量已经由 uni.scss 提供。
 */

page {
  background-color: $page-bg;
  color: $text-primary;
  font-size: $fs-md;
  font-family:
    -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', 'Hiragino Sans GB',
    'Microsoft YaHei', sans-serif;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
}

view,
text,
image,
scroll-view,
button,
input,
textarea {
  box-sizing: border-box;
}

image {
  display: block;
}

/* 小程序端 button 自带厚重边框，统一抹掉 */
button {
  padding: 0;
  margin: 0;
  border-radius: 0;
  background: transparent;
  line-height: normal;
  font-size: inherit;

  &::after {
    border: none;
  }
}

/* 页面根容器：底部预留 TabBar 高度，避免内容被遮挡 */
.page-root {
  min-height: 100vh;
  background-color: $page-bg;
}

.page-root--has-tabbar {
  padding-bottom: calc(#{$tabbar-height} + 24rpx + constant(safe-area-inset-bottom));
  padding-bottom: calc(#{$tabbar-height} + 24rpx + env(safe-area-inset-bottom));
}

/* 防止任何页面出现横向滚动条 */
.page-root {
  overflow-x: hidden;
}

.card {
  background: $card-bg;
  border-radius: $radius-lg;
}

.ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.flex-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.flex-1 {
  flex: 1;
  min-width: 0;
}
</style>
