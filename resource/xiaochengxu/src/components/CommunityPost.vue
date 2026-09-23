<script setup lang="ts">
/**
 * 社区信息流单条帖子（对应截图 4 的信息流卡片）。
 * 支持视频（带播放按钮 + 时长）与多图（3 张一行 + 「共 N 张」）两种媒体形态。
 */
import AppIcon from './AppIcon.vue'
import type { Post } from '@/types/community'

withDefaults(
  defineProps<{
    post: Post
    /** 详情页复用时会关掉底部操作栏 */
    showActions?: boolean
  }>(),
  { showActions: true }
)

const emit = defineEmits<{
  (e: 'tap'): void
  (e: 'share'): void
  (e: 'comment'): void
  (e: 'like'): void
  (e: 'store'): void
  (e: 'preview', index: number): void
}>()
</script>

<template>
  <view class="post" @tap="emit('tap')">
    <!-- ---------- 作者行 ---------- -->
    <view class="post__head">
      <image class="post__avatar" :src="post.author.avatar" mode="aspectFill" />
      <text class="post__author">{{ post.author.name }}</text>
      <view v-if="post.author.badge" class="post__badge">
        <AppIcon name="crown" :size="20" color="#6A4400" />
        <text class="post__badge-text">{{ post.author.badge }}</text>
      </view>
      <view v-if="post.author.level" class="post__level">
        <text class="post__level-text">V{{ post.author.level }}</text>
      </view>
      <text class="post__time">{{ post.timeText }}</text>
    </view>

    <!-- ---------- 标题 / 正文 ---------- -->
    <text class="post__title">{{ post.title }}</text>
    <text class="post__content">{{ post.content }}</text>

    <!-- ---------- 媒体 ---------- -->
    <view class="post__media">
      <!-- 视频 -->
      <view v-if="post.contentType === 'video'" class="post__video" @tap.stop="emit('tap')">
        <image class="post__video-bg" :src="post.images[0]" mode="aspectFill" />
        <view class="post__video-mask" />
        <view class="post__play">
          <view class="post__play-tri" />
        </view>
        <view v-if="post.videoDuration" class="post__duration">
          <text class="post__duration-text">{{ post.videoDuration }}</text>
        </view>
      </view>

      <!-- 单图 -->
      <view
        v-else-if="post.images.length === 1"
        class="post__single"
        @tap.stop="emit('preview', 0)"
      >
        <image class="post__single-img" :src="post.images[0]" mode="aspectFill" />
      </view>

      <!-- 多图 -->
      <view v-else-if="post.images.length > 1" class="post__grid">
        <view
          v-for="(img, i) in post.images.slice(0, 3)"
          :key="i"
          class="post__grid-item"
          @tap.stop="emit('preview', i)"
        >
          <image class="post__grid-img" :src="img" mode="aspectFill" />
          <view v-if="i === 2 && post.images.length > 3" class="post__grid-more">
            <text class="post__grid-more-text">共{{ post.images.length }}张</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ---------- 门店标签 ---------- -->
    <view v-if="post.storeName" class="post__store" @tap.stop="emit('store')">
      <AppIcon name="location" :size="30" color="#3B9DFF" />
      <text class="post__store-name">{{ post.storeName }}</text>
      <view class="post__store-arrow" />
    </view>

    <!-- ---------- 操作栏 ---------- -->
    <view v-if="showActions" class="post__actions">
      <view class="post__action" @tap.stop="emit('share')">
        <AppIcon name="share" :size="40" color="#8A8A99" :stroke-width="1.7" />
        <text class="post__action-text">{{ post.shareCount || '' }}</text>
      </view>
      <view class="post__action" @tap.stop="emit('comment')">
        <AppIcon name="comment" :size="40" color="#8A8A99" :stroke-width="1.7" />
        <text class="post__action-text">{{ post.commentCount || '' }}</text>
      </view>
      <view class="post__action" @tap.stop="emit('like')">
        <AppIcon
          :name="post.liked ? 'like-filled' : 'like'"
          :size="40"
          :color="post.liked ? '#5B5BD6' : '#8A8A99'"
          :stroke-width="1.7"
        />
        <text class="post__action-text" :class="{ 'is-liked': post.liked }">
          {{ post.likeCount || '' }}
        </text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.post {
  padding: 30rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

/* ---------- 作者行 ---------- */
.post__head {
  display: flex;
  align-items: center;
}

.post__avatar {
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: $card-bg-soft;
}

.post__author {
  margin-left: 16rpx;
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  max-width: 300rpx;
  @include ellipsis;
}

.post__badge {
  display: flex;
  align-items: center;
  margin-left: 10rpx;
  padding: 2rpx 10rpx 2rpx 6rpx;
  border-radius: $radius-xs;
  background: linear-gradient(135deg, #ffd98a 0%, #f5b942 100%);
  flex-shrink: 0;
}

.post__badge-text {
  margin-left: 4rpx;
  font-size: 20rpx;
  font-weight: 700;
  color: #6a4400;
}

.post__level {
  margin-left: 10rpx;
  padding: 2rpx 10rpx;
  border-radius: $radius-xs;
  background: linear-gradient(135deg, #8b6bff 0%, #5b5bd6 100%);
  flex-shrink: 0;
}

.post__level-text {
  font-size: 20rpx;
  font-weight: 700;
  color: #ffffff;
  font-style: italic;
}

.post__time {
  margin-left: auto;
  font-size: $fs-base;
  color: $text-placeholder;
  flex-shrink: 0;
}

/* ---------- 文案 ---------- */
.post__title {
  display: block;
  margin-top: 22rpx;
  font-size: 34rpx;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.35;
  @include ellipsis-multi(2);
}

.post__content {
  display: block;
  margin-top: 12rpx;
  font-size: $fs-md;
  color: $text-regular;
  line-height: 1.5;
  @include ellipsis-multi(3);
}

/* ---------- 媒体 ---------- */
.post__media {
  margin-top: 20rpx;
}

.post__video {
  position: relative;
  width: 100%;
  height: 400rpx;
  border-radius: $radius-md;
  overflow: hidden;
}

.post__video-bg {
  width: 100%;
  height: 100%;
}

.post__video-mask {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.18);
}

.post__play {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 128rpx;
  height: 128rpx;
  margin: -64rpx 0 0 -64rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.94);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.25);
}

/* 纯 CSS 三角形播放键 */
.post__play-tri {
  width: 0;
  height: 0;
  margin-left: 10rpx;
  border-top: 21rpx solid transparent;
  border-bottom: 21rpx solid transparent;
  border-left: 33rpx solid #1a1a1a;
  border-radius: 4rpx;
}

.post__duration {
  position: absolute;
  right: 16rpx;
  bottom: 16rpx;
  padding: 4rpx 14rpx;
  border-radius: $radius-xs;
  background: rgba(0, 0, 0, 0.55);
}

.post__duration-text {
  font-size: $fs-sm;
  color: #ffffff;
}

.post__single {
  width: 100%;
  height: 400rpx;
  border-radius: $radius-md;
  overflow: hidden;
}

.post__single-img {
  width: 100%;
  height: 100%;
}

.post__grid {
  display: flex;
  justify-content: space-between;
}

.post__grid-item {
  position: relative;
  width: 32%;
  height: 300rpx;
  border-radius: $radius-md;
  overflow: hidden;
}

.post__grid-img {
  width: 100%;
  height: 100%;
}

.post__grid-more {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
}

.post__grid-more-text {
  font-size: $fs-md;
  font-weight: 600;
  color: #ffffff;
}

/* ---------- 门店 ---------- */
.post__store {
  display: inline-flex;
  align-items: center;
  margin-top: 20rpx;
  padding: 8rpx 20rpx 8rpx 14rpx;
  border-radius: $radius-xs;
  background: #eef4ff;

  &:active {
    opacity: 0.7;
  }
}

.post__store-name {
  margin-left: 8rpx;
  font-size: $fs-base;
  color: #2b2b3a;
  max-width: 420rpx;
  @include ellipsis;
}

.post__store-arrow {
  width: 12rpx;
  height: 12rpx;
  margin-left: 10rpx;
  border-top: 3rpx solid #8a8a99;
  border-right: 3rpx solid #8a8a99;
  transform: rotate(45deg);
  border-radius: 2rpx;
}

/* ---------- 操作栏 ---------- */
.post__actions {
  display: flex;
  align-items: center;
  margin-top: 24rpx;
}

.post__action {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    opacity: 0.6;
  }
}

.post__action-text {
  margin-left: 12rpx;
  font-size: $fs-base;
  color: $text-secondary;

  &.is-liked {
    color: $brand-primary;
  }
}
</style>
