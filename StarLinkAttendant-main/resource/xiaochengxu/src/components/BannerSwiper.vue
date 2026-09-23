<script setup lang="ts">
/**
 * 首页顶部大 Banner 轮播。
 * 圆角 / 指示点 / 点击事件都按截图实现。
 */
import { ref } from 'vue'

export interface BannerItem {
  id: number
  title: string
  subtitle: string
  buttonText: string
  theme?: string
  image?: string
}

const props = withDefaults(
  defineProps<{
    list: BannerItem[]
    /** rpx。参考截图实测 ≈271rpx（原 336rpx 偏高） */
    height?: number
    /** 自动轮播间隔，0 表示不自动播 */
    interval?: number
  }>(),
  { height: 272, interval: 4000 }
)

const emit = defineEmits<{ (e: 'tap', item: BannerItem, index: number): void }>()

const current = ref(0)

function onChange(e: { detail: { current: number } }): void {
  current.value = e.detail.current
}

function onTap(item: BannerItem, index: number): void {
  emit('tap', item, index)
}
</script>

<template>
  <view class="banner">
    <swiper
      class="banner__swiper"
      :style="{ height: `${height}rpx` }"
      :current="current"
      :autoplay="interval > 0"
      :interval="interval"
      :duration="420"
      circular
      @change="onChange"
    >
      <swiper-item v-for="(item, index) in list" :key="item.id" class="banner__item">
        <view class="banner__inner" @tap="onTap(item, index)">
          <!-- 底图 -->
          <image
            v-if="item.image"
            class="banner__bg"
            :src="item.image"
            mode="aspectFill"
          />
          <view v-else class="banner__bg banner__bg--fallback" />

          <!-- 文案层 -->
          <view class="banner__content">
            <text class="banner__title">{{ item.title }}</text>
            <text class="banner__subtitle">{{ item.subtitle }}</text>
            <view class="banner__btn">
              <text class="banner__btn-text">{{ item.buttonText }}</text>
            </view>
          </view>
        </view>
      </swiper-item>
    </swiper>

    <!-- 指示点 -->
    <view v-if="list.length > 1" class="banner__dots">
      <view
        v-for="(item, index) in list"
        :key="item.id"
        class="banner__dot"
        :class="{ 'is-active': index === current }"
      />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.banner {
  position: relative;
  width: 100%;
  padding: 0 $gap-page;
}

.banner__swiper {
  width: 100%;
  border-radius: $radius-lg;
  overflow: hidden;
}

.banner__item {
  width: 100%;
  height: 100%;
}

.banner__inner {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: $radius-lg;
  overflow: hidden;
  background: $grad-banner;
}

.banner__bg {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
}

.banner__bg--fallback {
  background: $grad-banner;
}

/* 文案压在左侧，右侧留给图片主体 */
.banner__content {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 58%;
  padding: 30rpx 0 30rpx 32rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.banner__title {
  font-size: 56rpx;
  font-weight: 800;
  color: #ffffff;
  line-height: 1.08;
  letter-spacing: 2rpx;
  /* 参考图标题是带描边的立体字，用多层阴影模拟描边 */
  text-shadow:
    0 0 2rpx rgba(255, 255, 255, 0.9),
    0 4rpx 12rpx rgba(60, 20, 120, 0.4);
  @include ellipsis;
}

.banner__subtitle {
  margin-top: 10rpx;
  font-size: $fs-base;
  color: rgba(255, 255, 255, 0.92);
  @include ellipsis;
}

.banner__btn {
  margin-top: 20rpx;
  align-self: flex-start;
  padding: 10rpx 30rpx;
  border-radius: $radius-pill;
  background: linear-gradient(135deg, #ff9ec4 0%, #ff6b9d 100%);
  box-shadow: 0 6rpx 16rpx rgba(180, 40, 100, 0.28);

  &:active {
    opacity: 0.85;
  }
}

.banner__btn-text {
  font-size: $fs-base;
  font-weight: 600;
  color: #ffffff;
}

.banner__dots {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.banner__dot {
  width: 10rpx;
  height: 10rpx;
  margin: 0 6rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
  transition: width 0.25s ease, background 0.25s ease;

  &.is-active {
    width: 24rpx;
    border-radius: 6rpx;
    background: #ffffff;
  }
}
</style>
