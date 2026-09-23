<script setup lang="ts">
/**
 * 底部 TabBar（对应截图底部 5 个 Tab）。
 *
 * 为什么用自定义组件而不是 pages.json 原生 tabBar：
 * 原生 tabBar 在微信小程序里只接受 PNG 图标，做不到截图里「游戏」Tab 的彩色「玩」字；
 * 自定义组件在 H5 和微信小程序上渲染完全一致，且圆角 / 间距 / 安全区都能精确控制。
 *
 * 「游戏」Tab 特殊处理：参考截图里它是一个加粗的「玩」字（不是线性图标），
 * 所以这里用 <text> 渲染字形，而不是走 AppIcon。
 */
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { TAB_LIST } from '@/config'
import { switchTab } from '@/utils/nav'

const props = defineProps<{
  /** 当前激活的 Tab key：home / service / game / community / mine */
  current: string
}>()

const ACTIVE_COLOR = '#5B5BD6'
/** 未激活色对齐参考截图：偏深的灰，不是浅灰 */
const INACTIVE_COLOR = '#5A5A6B'

const tabs = computed(() =>
  TAB_LIST.map((t) => ({
    ...t,
    active: t.key === props.current,
  }))
)

function colorOf(key: string, active: boolean): string {
  if (!active) return INACTIVE_COLOR
  return ACTIVE_COLOR
}

function go(key: string, path: string): void {
  if (key === props.current) return
  switchTab(path)
}
</script>

<template>
  <view class="tabbar">
    <view class="tabbar__inner">
      <view
        v-for="tab in tabs"
        :key="tab.key"
        class="tabbar__item"
        :class="{ 'is-active': tab.active }"
        @tap="go(tab.key, tab.path)"
      >
        <view class="tabbar__icon">
          <!-- 「游戏」= 加粗「玩」字，与参考截图一致 -->
          <text
            v-if="'glyph' in tab && tab.glyph"
            class="tabbar__glyph"
            :class="{ 'is-active': tab.active }"
          >
            {{ tab.glyph }}
          </text>

          <AppIcon
            v-else
            :name="tab.active ? tab.activeIcon : tab.icon"
            :size="46"
            :color="colorOf(tab.key, tab.active)"
            :stroke-width="tab.active ? 1.9 : 1.7"
          />
        </view>
        <text class="tabbar__text" :style="{ color: colorOf(tab.key, tab.active) }">
          {{ tab.text }}
        </text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 500;
  background: #ffffff;
  box-shadow: 0 -2rpx 16rpx rgba(20, 20, 60, 0.05);

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 1rpx;
    background: $divider;
  }

  @include safe-area-bottom(0rpx);
}

.tabbar__inner {
  display: flex;
  align-items: stretch;
  height: $tabbar-height;
}

.tabbar__item {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-bottom: 6rpx;
  transition: transform 0.15s ease;

  &:active {
    transform: scale(0.94);
  }
}

.tabbar__icon {
  height: 52rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

/**
 * 「游戏」Tab 的「玩」字。
 * 参考截图里这个字比其它图标略大、字重很重。
 * 渐变文字用 background-clip 实现，但只在 H5 开 ——
 * 微信小程序部分基础库不支持，若强行开会导致文字透明不可见，
 * 所以小程序端保留纯色兜底（见下方 #ifdef 说明）。
 */
.tabbar__glyph {
  font-size: 46rpx;
  font-weight: 900;
  line-height: 1;
  color: #5a5a6b;

  &.is-active {
    color: $brand-primary;
  }

  /* #ifdef H5 */
  background-image: linear-gradient(135deg, #ff6bb5 0%, #8b6bff 52%, #5b9bff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;

  &.is-active {
    background-image: linear-gradient(135deg, #ff6bb5 0%, #8b6bff 52%, #5b9bff 100%);
  }

  /* #endif */
}

.tabbar__text {
  margin-top: 4rpx;
  font-size: 21rpx;
  line-height: 1.2;
  font-weight: 400;
}

.tabbar__item.is-active .tabbar__text {
  font-weight: 600;
}
</style>
