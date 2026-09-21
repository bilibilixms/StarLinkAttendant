<script setup lang="ts">
/**
 * 帖子详情 + 评论。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import CommunityPost from '@/components/CommunityPost.vue'
import StateView from '@/components/StateView.vue'
import { communityApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { navBack, navTo, goLogin } from '@/utils/nav'
import { eventValue } from '@/utils/event'
import { toast, toastSuccess } from '@/utils/ui'
import type { Post, PostComment } from '@/types/community'

const user = useUserStore()

const post = ref<Post | null>(null)
const comments = ref<PostComment[]>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const postId = ref(0)
const commentText = ref('')
const sending = ref(false)
const focusInput = ref(false)

async function load(id: number): Promise<void> {
  state.value = 'loading'
  try {
    post.value = await communityApi.getPostDetail(id)
    state.value = 'success'
    void loadComments(id)
  } catch {
    state.value = 'error'
  }
}

async function loadComments(id: number): Promise<void> {
  try {
    comments.value = await communityApi.getPostComments(id)
  } catch {
    comments.value = []
  }
}

onLoad((query) => {
  postId.value = Number(query?.id ?? 0)
  if (!postId.value) {
    state.value = 'error'
    return
  }
  void load(postId.value)
  if (query?.focus === 'comment') {
    setTimeout(() => (focusInput.value = true), 400)
  }
})

/* ==================== 交互 ==================== */

function previewImage(index: number): void {
  if (!post.value) return
  uni.previewImage({ urls: post.value.images, current: index })
}

function onShare(): void {
  toast('点击右上角「···」分享给好友')
}

function onStore(): void {
  navTo('/pages/store/index')
}

async function onLike(): Promise<void> {
  if (!post.value) return
  if (!user.isLogin) {
    goLogin(`/pages/community/detail?id=${postId.value}`)
    return
  }
  try {
    const res = await communityApi.togglePostLike(postId.value)
    post.value.liked = res.liked
    post.value.likeCount = res.likeCount
  } catch {
    /* request 层已提示 */
  }
}

async function onCollect(): Promise<void> {
  if (!post.value) return
  if (!user.isLogin) {
    goLogin(`/pages/community/detail?id=${postId.value}`)
    return
  }
  try {
    const res = await communityApi.togglePostCollect(postId.value)
    post.value.collected = res.collected
    toast(res.collected ? '已收藏' : '已取消收藏')
  } catch {
    /* request 层已提示 */
  }
}

function onCommentInput(e: Event): void {
  commentText.value = eventValue(e)
}

async function onSend(): Promise<void> {
  const content = commentText.value.trim()
  if (!content) {
    toast('评论内容不能为空')
    return
  }
  if (!user.isLogin) {
    goLogin(`/pages/community/detail?id=${postId.value}`)
    return
  }
  if (sending.value) return

  sending.value = true
  try {
    const c = await communityApi.addComment(postId.value, content)
    comments.value.unshift(c)
    commentText.value = ''
    if (post.value) post.value.commentCount += 1
    toastSuccess('评论成功')
  } catch {
    /* request 层已提示 */
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <view class="pd page-root">
    <AppNavBar title="帖子详情" show-back @back="navBack">
      <template #right>
        <view class="pd__collect" @tap="onCollect">
          <AppIcon
            :name="post?.collected ? 'star-filled' : 'star'"
            :size="42"
            :color="post?.collected ? '#FFB347' : '#4A4A52'"
            :stroke-width="1.7"
          />
        </view>
      </template>
    </AppNavBar>

    <StateView v-if="state !== 'success'" :state="state" @retry="load(postId)" />

    <template v-else-if="post">
      <view class="pd__post">
        <CommunityPost
          :post="post"
          @tap="() => {}"
          @share="onShare"
          @comment="focusInput = true"
          @like="onLike"
          @store="onStore"
          @preview="previewImage"
        />
      </view>

      <!-- ==================== 评论 ==================== -->
      <view class="card pd__comments">
        <text class="pd__comments-title">全部评论（{{ comments.length }}）</text>

        <view v-if="!comments.length" class="pd__empty">
          <AppIcon name="comment" :size="80" color="#C6C9D6" :stroke-width="1.4" />
          <text class="pd__empty-text">还没有评论，来说两句吧</text>
        </view>

        <view v-for="c in comments" :key="c.id" class="cm">
          <image class="cm__avatar" :src="c.author.avatar" mode="aspectFill" />
          <view class="cm__body">
            <view class="cm__head">
              <text class="cm__name">{{ c.author.name }}</text>
              <view v-if="c.author.level" class="cm__level">
                <text class="cm__level-text">V{{ c.author.level }}</text>
              </view>
              <text class="cm__time">{{ c.timeText }}</text>
            </view>
            <text class="cm__content">{{ c.content }}</text>
            <view class="cm__foot">
              <view class="cm__like">
                <AppIcon name="like" :size="30" color="#8A8A99" :stroke-width="1.6" />
                <text class="cm__like-text">{{ c.likeCount }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </template>

    <!-- ==================== 评论输入 ==================== -->
    <view class="pd__bar">
      <input
        class="pd__input"
        :value="commentText"
        :focus="focusInput"
        maxlength="200"
        placeholder="说点什么..."
        placeholder-class="pd__ph"
        confirm-type="send"
        @input="onCommentInput"
        @confirm="onSend"
      />
      <AppButton
        type="primary"
        size="sm"
        :loading="sending"
        :disabled="!commentText.trim()"
        @tap="onSend"
      >
        发送
      </AppButton>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.pd {
  padding-bottom: 160rpx;
  @include safe-area-bottom(0rpx);
}

.pd__collect {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    opacity: 0.6;
  }
}

/* ==================== 帖子 ==================== */
.pd__post {
  padding: 0 $gap-page;
  background: #ffffff;
}

/* ==================== 评论 ==================== */
.pd__comments {
  margin: 20rpx $gap-page 0;
  padding: 28rpx 24rpx;
}

.pd__comments-title {
  display: block;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
}

.pd__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 0 30rpx;
}

.pd__empty-text {
  margin-top: 20rpx;
  font-size: $fs-base;
  color: $text-placeholder;
}

/* ---------- 评论项 ---------- */
.cm {
  display: flex;
  align-items: flex-start;
  padding: 26rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.cm__avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: $card-bg-soft;
}

.cm__body {
  flex: 1;
  min-width: 0;
  margin-left: 18rpx;
}

.cm__head {
  display: flex;
  align-items: center;
}

.cm__name {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  max-width: 260rpx;
  @include ellipsis;
}

.cm__level {
  margin-left: 10rpx;
  padding: 2rpx 10rpx;
  border-radius: $radius-xs;
  background: linear-gradient(135deg, #8b6bff 0%, #5b5bd6 100%);
  flex-shrink: 0;
}

.cm__level-text {
  font-size: 18rpx;
  font-weight: 700;
  color: #ffffff;
  font-style: italic;
}

.cm__time {
  margin-left: auto;
  font-size: $fs-sm;
  color: $text-placeholder;
  flex-shrink: 0;
}

.cm__content {
  display: block;
  margin-top: 12rpx;
  font-size: $fs-md;
  color: $text-regular;
  line-height: 1.6;
}

.cm__foot {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
}

.cm__like {
  display: flex;
  align-items: center;

  &:active {
    opacity: 0.6;
  }
}

.cm__like-text {
  margin-left: 8rpx;
  font-size: $fs-sm;
  color: $text-secondary;
}

/* ==================== 输入栏 ==================== */
.pd__bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  padding: 16rpx $gap-page;
  background: #ffffff;
  border-top: 1rpx solid $divider;
  display: flex;
  align-items: center;
  @include safe-area-bottom(16rpx);
}

.pd__input {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  margin-right: 20rpx;
  padding: 0 24rpx;
  border-radius: $radius-pill;
  background: $card-bg-soft;
  font-size: $fs-md;
  color: $text-primary;
}

.pd__ph {
  color: $text-placeholder;
  font-size: $fs-md;
}
</style>
