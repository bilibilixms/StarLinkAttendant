<template>
  <view
    class="ai-float-ball"
    :style="{ left: left + 'px', top: top + 'px' }"
    @touchstart="onStart"
    @touchmove.stop.prevent="onMove"
    @touchend="onEnd"
    @mousedown="onStart"
    @click="onTap"
  >
    <image
      class="ai-float-ball__img"
      src="/static/ai_img/ai-avatar.webp"
      mode="aspectFill"
    />
    <view class="ai-float-ball__badge">AI</view>
  </view>
</template>

<script setup lang="ts">
/**
 * AI 可移动悬浮球
 * ----------------------------------------------------------
 * - 固定悬浮在所有 Tab 页右下角（可拖动，位置跨页记忆）
 * - 点击 → 进入 AI 智能助手页
 * - 拖动与点击自动区分（位移超过 6px 视为拖动）
 * - 兼容 H5 鼠标拖拽（window 级监听）与小程序触摸拖拽
 */
import { ref, onMounted } from 'vue'

const POS_KEY = 'ai_float_pos'
const BALL = 56 // 球直径 px（112rpx ≈ 56px）

const left = ref(0)
const top = ref(0)

let sw = 375
let sh = 667
let startX = 0
let startY = 0
let moved = false

function clamp(v: number, min: number, max: number): number {
  return Math.min(Math.max(v, min), max)
}

/** 取指针坐标：兼容 touch（e.touches[0]）与鼠标（e.clientX/clientY） */
function getPos(e: any): { x: number; y: number } | null {
  if (e && e.touches && e.touches[0]) return { x: e.touches[0].clientX, y: e.touches[0].clientY }
  if (e && e.clientX != null && e.clientY != null) return { x: e.clientX, y: e.clientY }
  return null
}

function save(): void {
  try {
    uni.setStorageSync(POS_KEY, { xPct: left.value / sw, yPct: top.value / sh })
  } catch (e) {
    /* ignore */
  }
}

onMounted(() => {
  try {
    const info = uni.getSystemInfoSync()
    sw = info.windowWidth || 375
    sh = info.windowHeight || 667
  } catch (e) {
    /* 默认值兜底 */
  }
  // 恢复上次位置（按比例，跨设备/跨页一致）
  try {
    const raw = uni.getStorageSync(POS_KEY)
    if (raw && typeof raw === 'object' && typeof raw.xPct === 'number') {
      left.value = clamp(raw.xPct * sw, 8, sw - BALL - 8)
      top.value = clamp(raw.yPct * sh, 60, sh - BALL - 8)
      return
    }
  } catch (e) {
    /* ignore */
  }
  // 默认右下角（避开底部 TabBar 约 220px 高度）
  left.value = sw - BALL - 16
  top.value = sh - BALL - 220
})

function onStart(e: any): void {
  const p = getPos(e)
  if (!p) return
  startX = p.x
  startY = p.y
  moved = false
  // H5 鼠标拖拽：指针移出元素后仍能继续拖（window 级监听；小程序无 window，跳过）
  if (e.type === 'mousedown' && typeof window !== 'undefined') {
    window.addEventListener('mousemove', onWindowMove)
    window.addEventListener('mouseup', onWindowUp)
  }
}

function applyMove(p: { x: number; y: number }): void {
  const dx = p.x - startX
  const dy = p.y - startY
  if (!moved && Math.abs(dx) + Math.abs(dy) > 6) moved = true
  if (!moved) return
  left.value = clamp(left.value + dx, 8, sw - BALL - 8)
  top.value = clamp(top.value + dy, 60, sh - BALL - 8)
  startX = p.x
  startY = p.y
}

function onMove(e: any): void {
  const p = getPos(e)
  if (!p) return
  applyMove(p)
}

function onWindowMove(e: any): void {
  e.preventDefault()
  applyMove({ x: e.clientX, y: e.clientY })
}

function onWindowUp(): void {
  if (typeof window !== 'undefined') {
    window.removeEventListener('mousemove', onWindowMove)
    window.removeEventListener('mouseup', onWindowUp)
  }
  onEnd()
}

function onEnd(): void {
  if (moved) save()
}

function onTap(): void {
  // 拖动结束后系统仍会触发 click，靠 moved 标志过滤
  if (moved) {
    moved = false
    return
  }
  uni.navigateTo({ url: '/pages/ai/index' })
}
</script>

<style lang="scss" scoped>
.ai-float-ball {
  position: fixed;
  z-index: 999;
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 8rpx 24rpx rgba(31, 41, 55, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  touch-action: none; /* H5 下禁用触摸默认行为，避免拖动时页面滚动 */
  user-select: none;
}
.ai-float-ball__img {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
}
.ai-float-ball__badge {
  position: absolute;
  right: -6rpx;
  top: -6rpx;
  min-width: 40rpx;
  height: 40rpx;
  line-height: 40rpx;
  text-align: center;
  padding: 0 8rpx;
  box-sizing: border-box;
  background: #4f6ef7;
  color: #ffffff;
  font-size: 20rpx;
  border-radius: 20rpx;
  box-shadow: 0 2rpx 8rpx rgba(79, 110, 247, 0.4);
}
</style>
