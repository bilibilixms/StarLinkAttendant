<script setup lang="ts">
/**
 * 游戏页（严格对照 reference/3-游戏.jpg）
 *
 * 结构：深色游戏氛围头部（左对齐「游戏福利」）
 * → 游戏分类横向滚动（选中项高亮）
 * → 「热门任务」白卡：任务列表（缩略图 + 标题 + 进度 + 剩余时间 + 去完成）
 * → 查看全部
 */
import { computed, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppStatusBar from '@/components/AppStatusBar.vue'
import AppTabBar from '@/components/AppTabBar.vue'
import AiFloatBall from '@/components/AiFloatBall.vue'
import GameLogo from '@/components/GameLogo.vue'
import StateView from '@/components/StateView.vue'
import { gameApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { navTo, goLogin } from '@/utils/nav'
import { toast, toastSuccess, confirm } from '@/utils/ui'
import { formatCountdown } from '@/utils/format'
import type { Game, GameTask } from '@/types/game'

const user = useUserStore()

const games = ref<Game[]>([])
const tasks = ref<GameTask[]>([])
/** 默认选中「英雄联盟」，与截图一致 */
const activeGameId = ref(4)
const loading = ref(true)
const expanded = ref(false)

/** 实时倒计时：每秒刷新一次，仅对带 endTimestamp 的任务生效 */
const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | null = null

const visibleTasks = computed(() => (expanded.value ? tasks.value : tasks.value.slice(0, 4)))

/** 任务剩余时间文案：有结束时间戳的实时倒计时，否则用后端给的静态文案 */
function endTextOf(task: GameTask): string {
  if (task.endTimestamp) {
    const rest = Math.floor((task.endTimestamp - now.value) / 1000)
    return rest <= 0 ? '已结束' : `${formatCountdown(rest)}后结束`
  }
  return task.endText
}

async function loadGames(): Promise<void> {
  try {
    games.value = await gameApi.getGames()
  } catch {
    games.value = []
  }
}

async function loadTasks(): Promise<void> {
  loading.value = true
  try {
    tasks.value = await gameApi.getGameTasks(
      activeGameId.value ? { gameId: activeGameId.value } : undefined
    )
  } catch {
    tasks.value = []
  } finally {
    loading.value = false
  }
}

/** 切换到「全部」或某个游戏 */
function selectGame(id: number): void {
  activeGameId.value = id
  void loadTasks()
}

onLoad(() => {
  void loadGames()
  void loadTasks()
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

/* ==================== 交互 ==================== */

function onTaskDetail(task: GameTask): void {
  navTo(`/pages/game/task?id=${task.id}`)
}

/** 任务说明弹窗（标题右侧 / 进度右侧的问号） */
function onTaskHelp(task: GameTask): void {
  uni.showModal({
    title: task.title,
    content: task.description,
    showCancel: false,
    confirmText: '知道了',
    confirmColor: '#5B5BD6',
  })
}

async function onJoin(task: GameTask): Promise<void> {
  if (!user.isLogin) {
    goLogin('/pages/game/index')
    return
  }
  if (task.status === 1) {
    toast('奖励已领取')
    return
  }
  const ok = await confirm(
    `确认领取「${task.reward}」奖励？领取后任务进度将重置。`,
    '领取奖励',
    '确认领取'
  )
  if (!ok) return
  try {
    const res = await gameApi.joinGameTask(task.id)
    toastSuccess(res.message)
    void loadTasks()
  } catch {
    /* request 层已提示 */
  }
}

function onViewAll(): void {
  expanded.value = !expanded.value
}
</script>

<template>
  <view class="page-root page-root--has-tabbar game">
    <!-- ==================== 深色游戏头部 ====================
         有真实游戏原画后，在这里放 <image class="game__bg" src="..."/> 并去掉装饰层即可 -->
    <view class="game__header">
      <view class="game__stripes" />
      <view class="game__glow" />
      <AppStatusBar />
      <view class="game__navbar">
        <text class="game__title">游戏福利</text>
        <!-- #ifdef MP-WEIXIN -->
        <view class="game__capsule" />
        <!-- #endif -->
      </view>
    </view>

    <!-- ==================== 游戏分类 ==================== -->
    <scroll-view class="game__cats" scroll-x :show-scrollbar="false">
      <view class="game__cats-row">
        <view
          v-for="g in games"
          :key="g.id"
          class="game__cat"
          :class="{ 'is-active': activeGameId === g.id }"
          @tap="selectGame(g.id)"
        >
          <GameLogo
            :short="g.short"
            :color="g.color"
            :src="g.logo"
            :size="74"
            :active="activeGameId === g.id"
          />
          <text class="game__cat-name" :class="{ 'is-active': activeGameId === g.id }">
            {{ g.name }}
          </text>
        </view>
      </view>
    </scroll-view>

    <!-- ==================== 热门任务 ==================== -->
    <view class="card task-card">
      <view class="task-card__head">
        <text class="task-card__title">热门任务</text>
        <view class="task-card__help" @tap="onTaskHelp(tasks[0] || ({} as GameTask))">
          <AppIcon name="question" :size="34" color="#B0B0BE" :stroke-width="1.6" />
        </view>
      </view>

      <StateView v-if="loading" state="loading" />
      <StateView
        v-else-if="!tasks.length"
        state="empty"
        empty-text="该游戏暂无进行中的任务"
        empty-icon="empty-box"
      />

      <template v-else>
        <view
          v-for="task in visibleTasks"
          :key="task.id"
          class="task"
          @tap="onTaskDetail(task)"
        >
          <!-- 2×2 缩略图 -->
          <view class="task__thumbs">
            <image
              v-for="(t, i) in task.thumbs.slice(0, 4)"
              :key="i"
              class="task__thumb"
              :src="t"
              mode="aspectFill"
            />
          </view>

          <!-- 文案 -->
          <view class="task__body">
            <text class="task__title">{{ task.title }}</text>
            <view class="task__progress-row">
              <text class="task__progress">({{ task.progress }}/{{ task.target }})</text>
              <view class="task__help" @tap.stop="onTaskHelp(task)">
                <AppIcon name="question" :size="28" color="#B0B0BE" :stroke-width="1.6" />
              </view>
            </view>
            <text class="task__meta">
              {{ task.remainCount > 0 ? `剩余${task.remainCount}份 ` : '' }}{{ endTextOf(task) }}
            </text>
          </view>

          <!-- 按钮 -->
          <AppButton
            :type="task.status === 1 ? 'soft' : 'primary'"
            size="sm"
            class="task__btn"
            @tap.stop="onJoin(task)"
          >
            {{ task.status === 1 ? '已领取' : '去完成' }}
          </AppButton>
        </view>

        <!-- 查看全部 -->
        <view class="task-card__all" @tap="onViewAll">
          <text class="task-card__all-text">{{ expanded ? '收起' : '查看全部' }}</text>
          <view class="task-card__all-arrow" :class="{ 'is-up': expanded }" />
        </view>
      </template>
    </view>

    <AppTabBar current="game" />
    <AiFloatBall />
  </view>
</template>

<style lang="scss" scoped>
.game {
  background: $page-bg;
}

/* ==================== 深色头部 ====================
   参考图这里是一张游戏原画海报。
   没有真实素材时用「深色渐变 + 斜向光带 + 光晕」营造电竞氛围，
   等拿到真图后把 .game__bg 换成 <image> 即可（见页面模板注释）。 */
.game__header {
  position: relative;
  background: $grad-game-header;
  padding-bottom: 84rpx;
  overflow: hidden;
}

/* 斜向光带，替代原画里的运动感 */
.game__stripes {
  position: absolute;
  left: -20%;
  top: -30%;
  width: 140%;
  height: 180%;
  background: repeating-linear-gradient(
    108deg,
    rgba(120, 160, 255, 0.14) 0,
    rgba(120, 160, 255, 0.14) 3rpx,
    transparent 3rpx,
    transparent 42rpx
  );
  pointer-events: none;
}

/* 右侧紫色光晕 */
.game__glow {
  position: absolute;
  right: -80rpx;
  top: -60rpx;
  width: 420rpx;
  height: 420rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(123, 97, 255, 0.55) 0%, rgba(123, 97, 255, 0) 70%);
  pointer-events: none;
}

.game__navbar {
  position: relative;
  z-index: 1;
  height: 96rpx;
  display: flex;
  align-items: center;
  padding: 0 $gap-page;
}

.game__title {
  font-size: 46rpx;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 2rpx;
  text-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.35);
}

.game__capsule {
  width: 180rpx;
  margin-left: auto;
  flex-shrink: 0;
}

/* ==================== 游戏分类 ==================== */
.game__cats {
  position: relative;
  z-index: 2;
  margin-top: -60rpx;
  white-space: nowrap;
  width: 100%;
}

.game__cats-row {
  display: inline-flex;
  align-items: flex-start;
  padding: 0 $gap-page 8rpx;
}

/* 参考图实测：图标 ≈70rpx、一列宽 ≈110rpx，一屏能露出 7 个 */
.game__cat {
  width: 112rpx;
  margin-right: 14rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;

  &:last-child {
    margin-right: $gap-page;
  }

  &:active {
    opacity: 0.8;
  }
}

.game__cat-name {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: $text-secondary;
  max-width: 112rpx;
  @include ellipsis;

  &.is-active {
    font-weight: 700;
    color: $text-primary;
  }
}

/* 选中态：白圈 + 柔和投影（参考图不是硬边双圈） */
.game__cat.is-active :deep(.glogo) {
  box-shadow:
    0 0 0 4rpx #ffffff,
    0 6rpx 16rpx rgba(40, 30, 90, 0.28);
}

/* ==================== 热门任务 ==================== */
.task-card {
  margin: 24rpx $gap-page 0;
  padding: 30rpx 24rpx 20rpx;
}

.task-card__head {
  display: flex;
  align-items: center;
}

.task-card__title {
  font-size: 40rpx;
  font-weight: 700;
  color: $text-primary;
}

.task-card__help {
  margin-left: 12rpx;
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ---------- 任务项 ---------- */
.task {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-of-type {
    border-bottom: none;
  }

  &:active {
    opacity: 0.9;
  }
}

.task__thumbs {
  width: 118rpx;
  height: 118rpx;
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-content: space-between;
}

.task__thumb {
  width: 56rpx;
  height: 56rpx;
  border-radius: 10rpx;
  background: $card-bg-soft;
  /* 参考图缩略图带一圈浅描边 */
  border: 1rpx solid rgba(120, 110, 200, 0.16);
}

.task__body {
  flex: 1;
  min-width: 0;
  margin: 0 16rpx;
  display: flex;
  flex-direction: column;
}

/* 参考图任务标题是「单行 + 省略号」，不是两行换行 */
.task__title {
  font-size: 28rpx;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.35;
  @include ellipsis;
}

.task__progress-row {
  display: flex;
  align-items: center;
  margin-top: 10rpx;
}

.task__progress {
  font-size: $fs-md;
  color: $text-regular;
}

.task__help {
  margin-left: 8rpx;
  width: 34rpx;
  height: 34rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.task__meta {
  margin-top: 12rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  @include ellipsis;
}

.task__btn {
  flex-shrink: 0;
  min-width: 124rpx;
}

/* ---------- 查看全部 ---------- */
.task-card__all {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30rpx 0 14rpx;

  &:active {
    opacity: 0.6;
  }
}

.task-card__all-text {
  font-size: $fs-md;
  color: $text-regular;
}

.task-card__all-arrow {
  width: 14rpx;
  height: 14rpx;
  margin-left: 12rpx;
  border-right: 3rpx solid $text-secondary;
  border-bottom: 3rpx solid $text-secondary;
  transform: rotate(45deg) translate(-2rpx, -2rpx);
  border-radius: 2rpx;
  transition: transform 0.2s ease;

  &.is-up {
    transform: rotate(-135deg) translate(-2rpx, -2rpx);
  }
}
</style>
