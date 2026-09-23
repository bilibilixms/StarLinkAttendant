<script setup lang="ts">
/**
 * 社区页（严格对照 reference/4-社区.jpg）
 *
 * 两个 Tab：推荐（信息流） / 找队友（组队招募）
 * 顶部 Tab 选中态为「加粗黑字 + 蓝色下划线」，与截图一致。
 */
import { ref } from 'vue'
import AiFloatBall from '@/components/AiFloatBall.vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppStatusBar from '@/components/AppStatusBar.vue'
import AppTabBar from '@/components/AppTabBar.vue'
import CommunityPost from '@/components/CommunityPost.vue'
import GameLogo from '@/components/GameLogo.vue'
import StateView from '@/components/StateView.vue'
import { communityApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { navTo, goLogin } from '@/utils/nav'
import { toast, toastSuccess } from '@/utils/ui'
import type { Post, TeamRecruit } from '@/types/community'

const user = useUserStore()

type Tab = 'recommend' | 'team'
const tab = ref<Tab>('recommend')

const posts = ref<Post[]>([])
const teams = ref<TeamRecruit[]>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const page = ref(1)
const hasMore = ref(true)
const loadingMore = ref(false)

/* ==================== 数据 ==================== */

async function loadPosts(refresh = false): Promise<void> {
  if (refresh) {
    page.value = 1
    hasMore.value = true
  }
  if (state.value !== 'success') state.value = 'loading'
  try {
    const res = await communityApi.getPosts({ current: page.value, size: 10 })
    posts.value = page.value === 1 ? res.records : [...posts.value, ...res.records]
    hasMore.value = page.value < res.pages
    state.value = posts.value.length ? 'success' : 'empty'
  } catch (e) {
    if (posts.value.length === 0) state.value = 'error'
    else toast((e as Error).message)
  }
}

async function loadTeams(): Promise<void> {
  if (state.value !== 'success') state.value = 'loading'
  try {
    const res = await communityApi.getTeamRecruits({ current: 1, size: 20 })
    teams.value = res.records
    state.value = teams.value.length ? 'success' : 'empty'
  } catch {
    state.value = 'error'
  }
}

function switchTab(next: Tab): void {
  if (tab.value === next) return
  tab.value = next
  page.value = 1
  hasMore.value = true
  state.value = 'loading'
  if (next === 'recommend') void loadPosts(true)
  else void loadTeams()
}

function reload(): void {
  if (tab.value === 'recommend') void loadPosts(true)
  else void loadTeams()
}

onLoad(() => {
  void loadPosts(true)
})

onPullDownRefresh(async () => {
  reload()
  uni.stopPullDownRefresh()
})

onReachBottom(async () => {
  if (tab.value !== 'recommend' || !hasMore.value || loadingMore.value) return
  loadingMore.value = true
  page.value += 1
  await loadPosts()
  loadingMore.value = false
})

/* ==================== 交互 ==================== */

function onPostTap(post: Post): void {
  navTo(`/pages/community/detail?id=${post.id}`)
}

function onPreview(post: Post, index: number): void {
  uni.previewImage({ urls: post.images, current: index })
}

function onShare(post: Post): void {
  toast('点击右上角「···」分享给好友')
}

function onComment(post: Post): void {
  navTo(`/pages/community/detail?id=${post.id}&focus=comment`)
}

async function onLike(post: Post): Promise<void> {
  if (!user.isLogin) {
    goLogin('/pages/community/index')
    return
  }
  try {
    const res = await communityApi.togglePostLike(post.id)
    const target = posts.value.find((p) => p.id === post.id)
    if (target) {
      target.liked = res.liked
      target.likeCount = res.likeCount
    }
  } catch {
    /* request 层已提示 */
  }
}

function onStore(post: Post): void {
  navTo('/pages/store/index')
}

async function onJoinTeam(team: TeamRecruit): Promise<void> {
  if (!user.isLogin) {
    goLogin('/pages/community/index')
    return
  }
  try {
    const res = await communityApi.joinTeam(team.id)
    team.joined = res.joined
    team.joinedCount = res.joinedCount
    team.needCount = Math.max(0, team.totalCount - res.joinedCount)
    toastSuccess(res.joined ? '已加入队伍' : '已退出队伍')
  } catch {
    /* request 层已提示 */
  }
}
</script>

<template>
  <view class="page-root page-root--has-tabbar comm">
    <AiFloatBall />
    <!-- ==================== 顶部 Tab ==================== -->
    <view class="comm__header">
      <AppStatusBar />
      <view class="comm__tabs">
        <view class="comm__tab" :class="{ 'is-active': tab === 'recommend' }" @tap="switchTab('recommend')">
          <text class="comm__tab-text">推荐</text>
          <view v-if="tab === 'recommend'" class="comm__tab-bar" />
        </view>
        <view class="comm__tab" :class="{ 'is-active': tab === 'team' }" @tap="switchTab('team')">
          <text class="comm__tab-text">找队友</text>
          <view v-if="tab === 'team'" class="comm__tab-bar" />
        </view>
        <!-- #ifdef MP-WEIXIN -->
        <view class="comm__capsule" />
        <!-- #endif -->
      </view>
    </view>

    <!-- ==================== 推荐信息流 ==================== -->
    <view v-if="tab === 'recommend'" class="comm__body">
      <StateView v-if="state !== 'success'" :state="state" @retry="reload" />
      <template v-else>
        <CommunityPost
          v-for="post in posts"
          :key="post.id"
          :post="post"
          @tap="onPostTap(post)"
          @share="onShare(post)"
          @comment="onComment(post)"
          @like="onLike(post)"
          @store="onStore(post)"
          @preview="(i) => onPreview(post, i)"
        />
        <view class="comm__more">
          <text class="comm__more-text">
            {{ hasMore ? (loadingMore ? '加载中...' : '上拉加载更多') : '没有更多内容了' }}
          </text>
        </view>
      </template>
    </view>

    <!-- ==================== 找队友 ==================== -->
    <view v-else class="comm__body">
      <StateView v-if="state !== 'success'" :state="state" @retry="reload" />
      <template v-else>
        <view v-for="team in teams" :key="team.id" class="team">
          <GameLogo :short="team.gameIcon" :color="team.gameColor" :size="104" />

          <view class="team__body">
            <view class="team__row">
              <text class="team__game">{{ team.gameName }}</text>
              <view class="team__rank">
                <text class="team__rank-text">{{ team.rank }}</text>
              </view>
              <view v-if="team.online" class="team__online">
                <text class="team__online-text">在线</text>
              </view>
            </view>

            <view class="team__row team__row--meta">
              <AppIcon name="clock" :size="28" color="#8A8A99" />
              <text class="team__meta">{{ team.startTimeText }}</text>
              <text class="team__meta team__meta--need">
                还差 {{ team.needCount }} 人（{{ team.joinedCount }}/{{ team.totalCount }}）
              </text>
            </view>

            <view class="team__row team__row--owner">
              <image class="team__avatar" :src="team.ownerAvatar" mode="aspectFill" />
              <text class="team__owner">{{ team.ownerName }}</text>
            </view>

            <text class="team__remark">{{ team.remark }}</text>
          </view>

          <view class="team__action">
            <AppButton
              :type="team.joined ? 'soft' : 'primary'"
              size="sm"
              @tap="onJoinTeam(team)"
            >
              {{ team.joined ? '已加入' : '组队' }}
            </AppButton>
          </view>
        </view>
        <view class="comm__more">
          <text class="comm__more-text">没有更多队伍了</text>
        </view>
      </template>
    </view>

    <AppTabBar current="community" />
  </view>
</template>

<style lang="scss" scoped>
.comm {
  background: #ffffff;
}

/* ==================== 顶部 Tab ==================== */
.comm__header {
  background: #ffffff;
  position: sticky;
  top: 0;
  z-index: 100;
}

.comm__tabs {
  display: flex;
  align-items: center;
  height: 92rpx;
  padding: 0 $gap-page;
}

.comm__tab {
  position: relative;
  margin-right: 44rpx;
  padding-bottom: 8rpx;

  &:active {
    opacity: 0.7;
  }
}

.comm__tab-text {
  font-size: 32rpx;
  color: $text-secondary;
  font-weight: 400;
}

.comm__tab.is-active .comm__tab-text {
  font-size: 42rpx;
  font-weight: 800;
  color: $text-primary;
  letter-spacing: 1rpx;
}

.comm__tab-bar {
  position: absolute;
  left: 4rpx;
  right: 4rpx;
  bottom: 0;
  height: 8rpx;
  border-radius: 4rpx;
  background: linear-gradient(90deg, #4a6cf7 0%, #6ea8ff 100%);
}

.comm__capsule {
  width: 180rpx;
  margin-left: auto;
  flex-shrink: 0;
}

/* ==================== 内容区 ==================== */
.comm__body {
  padding: 0 $gap-page;
  min-height: 60vh;
}

.comm__more {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx 0 20rpx;
}

.comm__more-text {
  font-size: $fs-base;
  color: $text-placeholder;
}

/* ==================== 找队友卡片 ==================== */
.team {
  display: flex;
  align-items: flex-start;
  padding: 30rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-of-type {
    border-bottom: none;
  }
}

.team__body {
  flex: 1;
  min-width: 0;
  margin: 0 20rpx;
}

.team__row {
  display: flex;
  align-items: center;
}

.team__row--meta {
  margin-top: 12rpx;
}

.team__row--owner {
  margin-top: 14rpx;
}

.team__game {
  font-size: 32rpx;
  font-weight: 700;
  color: $text-primary;
  max-width: 220rpx;
  @include ellipsis;
}

.team__rank {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: $radius-xs;
  background: #fff4e0;
}

.team__rank-text {
  font-size: $fs-xs;
  color: #b06a00;
  font-weight: 600;
}

.team__online {
  margin-left: 10rpx;
  padding: 2rpx 12rpx;
  border-radius: $radius-xs;
  background: #e8f8ee;
}

.team__online-text {
  font-size: $fs-xs;
  color: #1f9c53;
  font-weight: 600;
}

.team__meta {
  margin-left: 8rpx;
  font-size: $fs-base;
  color: $text-secondary;
  @include ellipsis;
}

.team__meta--need {
  margin-left: 16rpx;
  color: $brand-primary;
  font-weight: 600;
  flex-shrink: 0;
}

.team__avatar {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: $card-bg-soft;
}

.team__owner {
  margin-left: 10rpx;
  font-size: $fs-base;
  color: $text-regular;
  @include ellipsis;
}

.team__remark {
  display: block;
  margin-top: 12rpx;
  font-size: $fs-base;
  color: $text-placeholder;
  @include ellipsis;
}

.team__action {
  flex-shrink: 0;
  padding-top: 8rpx;
  min-width: 150rpx;
  display: flex;
  justify-content: flex-end;
}
</style>
