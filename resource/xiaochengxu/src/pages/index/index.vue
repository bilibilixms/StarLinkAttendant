<script setup lang="ts">
/**
 * 首页（严格对照 reference/1-首页01.jpg）
 *
 * 结构自上而下：粉紫渐变头部 → 搜索行 → 大 Banner 轮播 → 会员深色条
 * → 白卡（网咖/电竞酒店 Tab + 5 个功能图标）→ 天天夺宝横幅
 * → 超级福利·活动专区（左大卡 + 右 2×2）→ 当前上机 / 热门商品 / 附近门店
 * → 未登录时的吸底登录条 → TabBar
 */
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppStatusBar from '@/components/AppStatusBar.vue'
import AppTabBar from '@/components/AppTabBar.vue'
import AiFloatBall from '@/components/AiFloatBall.vue'
import BannerSwiper from '@/components/BannerSwiper.vue'
import SectionHeader from '@/components/SectionHeader.vue'
import StateView from '@/components/StateView.vue'
import { storeApi, productApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { navTo, goLogin } from '@/utils/nav'
import { toast, toastSuccess, actionSheet } from '@/utils/ui'
import { formatMoney, formatDuration, formatCompact } from '@/utils/format'
import type { BannerItem } from '@/components/BannerSwiper.vue'
import type { Product } from '@/types/product'
import type { Store } from '@/types/store'

const user = useUserStore()
const app = useAppStore()

/* ==================== 数据 ==================== */

const banners = ref<BannerItem[]>([])
const activities = ref<Array<{ key: string; title: string; subtitle: string; action: string; theme: string }>>([])
const hotProducts = ref<Product[]>([])
const stores = ref<Store[]>([])
const loading = ref(true)

async function loadHome(): Promise<void> {
  loading.value = true
  // 并行拉取，任何一个失败都不影响其他区块渲染
  const [b, a, p, s] = await Promise.allSettled([
    storeApi.getHomeBanners(),
    storeApi.getHomeActivities(),
    productApi.getHotProducts(4),
    storeApi.getStores(),
  ])
  if (b.status === 'fulfilled') banners.value = b.value as BannerItem[]
  if (a.status === 'fulfilled') activities.value = a.value
  if (p.status === 'fulfilled') hotProducts.value = p.value
  if (s.status === 'fulfilled') stores.value = s.value.slice(0, 2)
  loading.value = false
}

onShow(() => {
  void loadHome()
  // 后端无 summary 接口，登录后不主动拉取；余额/积分变更由具体页面 patch 本地缓存
})

/* ==================== 功能宫格 ==================== */

type BizTab = 'cafe' | 'hotel'
const bizTab = ref<BizTab>('cafe')

interface FnItem {
  key: string
  title: string
  icon: string
  color: string
  path?: string
}

const CAFE_FN: FnItem[] = [
  { key: 'seat', title: '去订座', icon: 'fn-seat', color: '#3AC6C6', path: '/pages/reservation/index' },
  { key: 'recharge', title: '网咖充值', icon: 'fn-recharge', color: '#FF6B8A', path: '/pages/recharge/index' },
  { key: 'food', title: '自助点餐', icon: 'fn-food', color: '#FF9F43', path: '/pages/product/index' },
  { key: 'welfare', title: '会员福利', icon: 'fn-welfare', color: '#FF7BA9', path: '/pages/welfare/index' },
  { key: 'card', title: '鱼乐卡', icon: 'fn-card', color: '#4A9BFF', path: '/pages/coupon/index' },
]

const HOTEL_FN: FnItem[] = [
  { key: 'book', title: '预约订房', icon: 'svc-hotel', color: '#3AC6C6', path: '/pages/hotel/index' },
  { key: 'game', title: '游戏特权', icon: 'svc-game', color: '#4A9BFF', path: '/pages/game/index' },
  { key: 'service', title: '客房服务', icon: 'svc-bed', color: '#FF9F43', path: '/pages/feedback/index' },
  { key: 'wifi', title: '免费WIFI', icon: 'svc-wifi', color: '#8B6BFF', path: '' },
  { key: 'invoice', title: '开房票', icon: 'svc-invoice', color: '#67C23A', path: '/pages/feedback/index' },
]

const fnList = computed(() => (bizTab.value === 'cafe' ? CAFE_FN : HOTEL_FN))

function onFnTap(item: FnItem): void {
  if (item.key === 'wifi') {
    toast('已连接「星络灵侍馆」免费 WIFI')
    return
  }
  if (item.path) navTo(item.path)
}

/* ==================== 活动专区配色 ==================== */

const THEME_STYLE: Record<string, { bg: string; title: string; action: string }> = {
  lavender: { bg: '#EEF0FF', title: '#2B2B4A', action: '#5B5BD6' },
  orange: { bg: '#FFF3E6', title: '#7A4413', action: '#FF8A2B' },
  pink: { bg: '#FFEFF3', title: '#7A1F3D', action: '#FF5B8A' },
  blue: { bg: '#E8F4FF', title: '#12405F', action: '#3B9DFF' },
}

function themeOf(key: string) {
  return THEME_STYLE[key] ?? THEME_STYLE.lavender
}

/** 活动小卡右上角的装饰图标 */
const ACTIVITY_ICON: Record<string, string> = {
  invite: 'users',
  didi: 'location',
  treasure: 'trophy',
  sign: 'calendar-check',
}

function iconOfActivity(key: string): string {
  return ACTIVITY_ICON[key] ?? 'gift'
}

/* ==================== 交互 ==================== */

function onSearch(): void {
  navTo('/pages/store/index')
}

/** 直接上机：跳转到选座页，点击空闲机位即开台 */
function onScan(): void {
  navTo('/pages/session/scan')
}

async function onMenu(): Promise<void> {
  const idx = await actionSheet(['我的订单', '我的优惠券', '消费记录', '设置'])
  const routes = ['/pages/order/index', '/pages/coupon/index', '/pages/consume/index', '/pages/settings/index']
  if (idx >= 0) navTo(routes[idx])
}

function onBannerTap(item: BannerItem): void {
  if (item.theme === 'treasure') navTo('/pages/activity/treasure')
  else if (item.theme === 'hotel') navTo('/pages/hotel/index')
  else navTo('/pages/welfare/index')
}

function onMemberBarTap(): void {
  if (!user.isLogin) {
    goLogin('/pages/index/index')
    return
  }
  navTo('/pages/profile/index')
}

async function onSign(): Promise<void> {
  // 后端暂未实现签到接口，仅占位提示
  toast('签到功能开发中')
}

function onActivityTap(key: string): void {
  if (key === 'sign') {
    void onSign()
  } else if (key === 'treasure') {
    navTo('/pages/activity/treasure')
  } else if (key === 'didi') {
    toast('请在微信内打开滴滴出行小程序')
  } else {
    navTo('/pages/welfare/index')
  }
}

function onCurrentSession(): void {
  navTo('/pages/session/current')
}

function goProductList(): void {
  navTo('/pages/product/index')
}

function onProductTap(p: Product): void {
  // 热门商品为真实后端商品，跳转详情页（真实后端详情），详情内可加购/下单（下单走 mock）
  navTo(`/pages/product/detail?id=${p.id}&source=hot`)
}

function onStoreTap(s: Store): void {
  app.setStore({ id: s.id, name: s.name, shortName: s.name.replace(/^星络灵侍馆\(|\)$/g, ''), address: s.address })
  toast(`已切换到 ${s.name}`)
}
</script>

<template>
  <view class="home page-root page-root--has-tabbar">
    <!-- ==================== 顶部渐变区 ==================== -->
    <view class="home__top">
      <AppStatusBar />

      <!-- 搜索行：搜索框 + 扫码 + 更多 -->
      <view class="search-row">
        <view class="search-box" @tap="onSearch">
          <AppIcon name="search" :size="34" color="#8A8A99" />
          <text class="search-box__ph">输入门店名称/地址</text>
        </view>
        <view class="search-row__btn" @tap="onScan">
          <AppIcon name="scan" :size="50" color="#3A3A4A" :stroke-width="1.8" />
        </view>
        <view class="search-row__btn" @tap="onMenu">
          <AppIcon name="menu" :size="46" color="#3A3A4A" :stroke-width="1.8" />
        </view>
        <!-- #ifdef MP-WEIXIN -->
        <view class="search-row__capsule" />
        <!-- #endif -->
      </view>

      <!-- 大 Banner 轮播 -->
      <BannerSwiper :list="banners" @tap="onBannerTap" />

      <!-- 会员深色条 -->
      <view class="member-bar" @tap="onMemberBarTap">
        <view class="member-bar__hi">
          <text class="member-bar__hi-text">Hi</text>
        </view>
        <text class="member-bar__text">
          {{ user.isLogin ? `${user.nickname} · ${user.levelName}` : '登录星络会员享多重权益' }}
        </text>
        <AppButton type="gold" size="sm" icon="hand" @tap.stop="onSign">签到</AppButton>
      </view>
    </view>

    <!-- ==================== 功能宫格白卡 ==================== -->
    <view class="card fn-card">
      <view class="fn-tabs">
        <view class="fn-tab" :class="{ 'is-active': bizTab === 'cafe' }" @tap="bizTab = 'cafe'">
          <text class="fn-tab__text">网咖</text>
          <view v-if="bizTab === 'cafe'" class="fn-tab__bar" />
        </view>
        <view class="fn-tab" :class="{ 'is-active': bizTab === 'hotel' }" @tap="bizTab = 'hotel'">
          <text class="fn-tab__text">电竞酒店</text>
          <view v-if="bizTab === 'hotel'" class="fn-tab__bar" />
        </view>
      </view>

      <view class="fn-grid">
        <view v-for="item in fnList" :key="item.key" class="fn-item" @tap="onFnTap(item)">
          <view class="fn-item__icon">
            <AppIcon :name="item.icon" :size="58" :color="item.color" :stroke-width="1.5" />
          </view>
          <text class="fn-item__text">{{ item.title }}</text>
        </view>
      </view>

      <view class="fn-dots">
        <view class="fn-dot" :class="{ 'is-active': bizTab === 'cafe' }" />
        <view class="fn-dot" :class="{ 'is-active': bizTab === 'hotel' }" />
      </view>
    </view>

    <!-- ==================== 天天夺宝横幅 ==================== -->
    <view class="promo" @tap="navTo('/pages/activity/treasure')">
      <view class="promo__art">
        <view class="promo__kbd">
          <AppIcon name="kbd" :size="104" color="#1E1E32" />
        </view>
        <text class="promo__art-text">Wooting键盘</text>
      </view>
      <view class="promo__content">
        <text class="promo__title">天天夺宝</text>
        <view class="promo__btn">
          <text class="promo__btn-text">天天赢超级大奖</text>
        </view>
      </view>
      <view class="promo__go">
        <text class="promo__go-text">GO</text>
        <text class="promo__go-arrow">»</text>
      </view>
    </view>

    <!-- ==================== 超级福利·活动专区 ==================== -->
    <view class="card welfare">
      <SectionHeader
        title="超级福利·活动专区"
        icon="fire"
        icon-color="#FF7A45"
        :show-arrow="false"
      />

      <view class="welfare__body">
        <!-- 左侧大活动卡 -->
        <view class="welfare__main" @tap="navTo('/pages/activity/treasure')">
          <image class="welfare__main-img" src="/static/images/game/task5-a.jpg" mode="aspectFill" />
          <view class="welfare__main-mask" />
          <view class="welfare__main-body">
            <text class="welfare__main-title">新职业|次元术士登场</text>
            <text class="welfare__main-sub">超多成长福利 登录即领</text>
            <view class="welfare__main-btn">
              <text class="welfare__main-btn-text">立即参与</text>
            </view>
          </view>
        </view>

        <!-- 右侧 2×2 小卡 -->
        <view class="welfare__grid">
          <view
            v-for="act in activities"
            :key="act.key"
            class="welfare__item"
            :style="{ background: themeOf(act.theme).bg }"
            @tap="onActivityTap(act.key)"
          >
            <text class="welfare__item-title" :style="{ color: themeOf(act.theme).title }">
              {{ act.title }}
            </text>
            <text class="welfare__item-sub" :style="{ color: themeOf(act.theme).title }">
              {{ act.subtitle }}
            </text>
            <text class="welfare__item-action" :style="{ color: themeOf(act.theme).action }">
              {{ act.action }} ›
            </text>
            <!-- 图标压在右下角，不占标题宽度（参考图就是这个布局） -->
            <view class="welfare__item-icon">
              <AppIcon :name="iconOfActivity(act.key)" :size="52" :color="themeOf(act.theme).action" />
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- ==================== 当前上机状态 ==================== -->
    <view v-if="user.isLogin && user.summary?.currentSession" class="card session-card" @tap="onCurrentSession">
      <view class="session-card__head">
        <view class="session-card__dot" />
        <text class="session-card__status">上机中</text>
        <text class="session-card__no">{{ user.summary.currentSession.sessionNo }}</text>
      </view>
      <view class="session-card__body">
        <view class="session-card__col">
          <text class="session-card__label">机位</text>
          <text class="session-card__value">{{ user.summary.currentSession.seatNo }}</text>
        </view>
        <view class="session-card__col">
          <text class="session-card__label">时长</text>
          <text class="session-card__value">
            {{ formatDuration(user.summary.currentSession.durationMinutes) }}
          </text>
        </view>
        <view class="session-card__col">
          <text class="session-card__label">当前消费</text>
          <text class="session-card__value session-card__value--money">
            ¥{{ formatMoney(user.summary.currentSession.currentAmount) }}
          </text>
        </view>
      </view>
      <view class="session-card__foot">
        <text class="session-card__tip">
          余额可用约 {{ formatDuration(user.summary.currentSession.remainingMinutes) }}
        </text>
        <AppButton type="primary" size="sm">查看详情</AppButton>
      </view>
    </view>

    <!-- ==================== 热门商品 ==================== -->
    <view class="card block">
      <SectionHeader title="热门商品" more-text="去点单" @more="goProductList" />
      <StateView v-if="loading" state="loading" />
      <StateView v-else-if="!hotProducts.length" state="empty" empty-text="暂无推荐商品" />
      <scroll-view v-else class="prod-scroll" scroll-x :show-scrollbar="false">
        <view class="prod-row">
          <view v-for="p in hotProducts" :key="p.id" class="prod" @tap="onProductTap(p)">
            <image class="prod__img" :src="p.cover" mode="aspectFill" />
            <text class="prod__name">{{ p.name }}</text>
            <view class="prod__price-row">
              <text class="prod__price">¥{{ formatMoney(p.memberPrice ?? p.price) }}</text>
              <text class="prod__sales">已售{{ formatCompact(p.sales) }}</text>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- ==================== 附近门店 ==================== -->
    <view class="card block">
      <SectionHeader title="附近门店" more-text="全部门店" @more="navTo('/pages/store/index')" />
      <view v-for="s in stores" :key="s.id" class="store" @tap="onStoreTap(s)">
        <image class="store__img" :src="s.cover" mode="aspectFill" />
        <view class="store__info">
          <text class="store__name">{{ s.name }}</text>
          <text class="store__addr">{{ s.address }}</text>
          <view class="store__meta">
            <text class="store__tag">{{ s.businessHours }}</text>
            <text class="store__free">空闲 {{ s.freeSeats }} 台</text>
          </view>
        </view>
        <text class="store__distance">{{ s.distance }}km</text>
      </view>
    </view>

    <!-- ==================== 未登录吸底登录条 ==================== -->
    <view v-if="!user.isLogin" class="login-bar">
      <text class="login-bar__text">登录星络会员享多重权益</text>
      <AppButton type="primary" size="sm" @tap="goLogin('/pages/index/index')">立即登录</AppButton>
    </view>

    <AppTabBar current="home" />
    <AiFloatBall />
  </view>
</template>

<style lang="scss" scoped>
/* ==================== 顶部渐变区 ==================== */
.home__top {
  background: $grad-home-header;
  padding-bottom: 28rpx;
}

/* ---------- 搜索行 ---------- */
.search-row {
  display: flex;
  align-items: center;
  padding: 6rpx $gap-page 16rpx;
}

.search-box {
  flex: 1;
  /* flex 子项默认 min-width:auto，不加这行搜索框不会收缩，整页会横向溢出 */
  min-width: 0;
  height: 64rpx;
  border-radius: $radius-pill;
  background: rgba(255, 255, 255, 0.72);
  display: flex;
  align-items: center;
  padding: 0 22rpx;

  &:active {
    opacity: 0.85;
  }
}

.search-box__ph {
  margin-left: 10rpx;
  font-size: $fs-base;
  color: #9a9aae;
  @include ellipsis;
}

/* 扫码 / 更多：参考图里图标带半透明圆角底 */
.search-row__btn {
  width: 64rpx;
  height: 64rpx;
  margin-left: 12rpx;
  border-radius: $radius-md;
  background: rgba(255, 255, 255, 0.26);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &:active {
    opacity: 0.6;
  }
}

.search-row__capsule {
  width: 180rpx;
  flex-shrink: 0;
}

/* ---------- 会员深色条 ---------- */
.member-bar {
  margin: 22rpx $gap-page 0;
  height: 82rpx;
  border-radius: $radius-md;
  background: $member-bar-bg;
  display: flex;
  align-items: center;
  padding: 0 16rpx 0 14rpx;
  box-shadow: 0 8rpx 20rpx rgba(40, 30, 80, 0.18);

  &:active {
    opacity: 0.92;
  }
}

.member-bar__hi {
  width: 46rpx;
  height: 46rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffe08a 0%, #f7b733 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.member-bar__hi-text {
  font-size: 22rpx;
  font-weight: 800;
  color: #7a4b00;
}

.member-bar__text {
  flex: 1;
  min-width: 0;
  margin: 0 14rpx;
  font-size: $fs-base;
  color: #ffe9a8;
  @include ellipsis;
}

/* ==================== 功能宫格白卡 ==================== */
.fn-card {
  margin: -10rpx $gap-page 0;
  padding: 18rpx 10rpx 14rpx;
  border-radius: $radius-lg;
}

.fn-tabs {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-bottom: 4rpx;
}

.fn-tab {
  position: relative;
  padding: 4rpx 36rpx 14rpx;

  &:active {
    opacity: 0.7;
  }
}

.fn-tab__text {
  font-size: $fs-md;
  color: $text-secondary;
}

.fn-tab.is-active .fn-tab__text {
  font-size: 32rpx;
  font-weight: 700;
  color: $text-primary;
}

.fn-tab__bar {
  position: absolute;
  left: 50%;
  bottom: 4rpx;
  width: 36rpx;
  height: 6rpx;
  margin-left: -18rpx;
  border-radius: 3rpx;
  background: $brand-primary;
}

.fn-grid {
  display: flex;
  align-items: flex-start;
  padding: 10rpx 0 2rpx;
}

.fn-item {
  flex: 1;
  /* 关键：flex 子项默认 min-width:auto，会被内容撑开导致 5 个图标挤出屏幕 */
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  &:active {
    opacity: 0.65;
  }
}

/* 图标底衬，让彩色图标在浅底上更有「贴纸感」，接近参考图的立体图标 */
.fn-item__icon {
  width: 76rpx;
  height: 76rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fn-item__text {
  margin-top: 4rpx;
  font-size: $fs-base;
  color: $text-regular;
  text-align: center;
  max-width: 100%;
  @include ellipsis;
}

.fn-dots {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 4rpx;
}

.fn-dot {
  width: 10rpx;
  height: 10rpx;
  margin: 0 5rpx;
  border-radius: 50%;
  background: #d8dae4;

  &.is-active {
    background: $brand-primary;
  }
}

/* ==================== 天天夺宝横幅 ==================== */
.promo {
  position: relative;
  margin: 20rpx $gap-page 0;
  height: 172rpx;
  border-radius: $radius-lg;
  overflow: hidden;
  background: linear-gradient(120deg, #b06bf5 0%, #8a5cf0 48%, #6c4be0 100%);
  display: flex;
  align-items: center;

  &:active {
    opacity: 0.92;
  }
}

.promo__art {
  width: 186rpx;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* 参考图里键盘是「白框商品图」，没有素材时用键盘图形 + 白色相框表达 */
.promo__kbd {
  width: 128rpx;
  height: 76rpx;
  border-radius: 12rpx;
  background: #ffffff;
  border: 3rpx solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 8rpx 18rpx rgba(40, 10, 80, 0.32);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.promo__art-text {
  margin-top: 8rpx;
  font-size: $fs-xs;
  color: rgba(255, 255, 255, 0.92);
}

.promo__content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 参考图是红金渐变立体字 + 白描边，用多层阴影模拟描边 */
.promo__title {
  font-size: 48rpx;
  font-weight: 900;
  color: #ffd166;
  letter-spacing: 3rpx;
  text-shadow:
    -2rpx -2rpx 0 #ffffff,
    2rpx -2rpx 0 #ffffff,
    -2rpx 2rpx 0 #ffffff,
    2rpx 2rpx 0 #ffffff,
    0 6rpx 14rpx rgba(50, 10, 110, 0.45);
}

.promo__btn {
  margin-top: 12rpx;
  padding: 8rpx 26rpx;
  border-radius: $radius-pill;
  background: linear-gradient(135deg, #ff9ec4 0%, #ff6b9d 100%);
  border: 2rpx solid rgba(255, 220, 140, 0.8);
}

.promo__btn-text {
  font-size: $fs-base;
  color: #ffffff;
  font-weight: 600;
}

.promo__go {
  width: 108rpx;
  height: 108rpx;
  margin-right: 20rpx;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #b98bff 0%, #7b4fe0 100%);
  border: 4rpx solid rgba(255, 255, 255, 0.6);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.promo__go-text {
  font-size: 34rpx;
  font-weight: 800;
  color: #ffffff;
  line-height: 1;
}

.promo__go-arrow {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.85);
  line-height: 1;
  margin-top: 2rpx;
}

/* ==================== 超级福利·活动专区 ==================== */
.welfare {
  margin: 20rpx $gap-page 0;
  padding: 22rpx 20rpx 20rpx;
}

.welfare__body {
  display: flex;
  margin-top: 18rpx;
  /* 左侧约 40%，右侧约 60%，与截图一致 */
}

.welfare__main {
  position: relative;
  width: 39%;
  height: 384rpx;
  border-radius: $radius-md;
  overflow: hidden;
  flex-shrink: 0;

  &:active {
    opacity: 0.9;
  }
}

.welfare__main-img {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
}

.welfare__main-mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 62%;
  background: linear-gradient(180deg, rgba(10, 8, 40, 0) 0%, rgba(10, 8, 40, 0.78) 100%);
}

.welfare__main-body {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 14rpx 18rpx;
}

.welfare__main-title {
  display: block;
  font-size: $fs-base;
  font-weight: 700;
  color: #ffffff;
  @include ellipsis-multi(2);
}

.welfare__main-sub {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-xs;
  color: rgba(255, 255, 255, 0.8);
  @include ellipsis;
}

.welfare__main-btn {
  margin-top: 12rpx;
  height: 44rpx;
  border-radius: $radius-pill;
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
}

.welfare__main-btn-text {
  font-size: $fs-sm;
  font-weight: 600;
  color: #6c4be0;
}

.welfare__grid {
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-content: space-between;
}

/**
 * 小活动卡。
 * 图标绝对定位到右下角 —— 早期版本把图标放在标题同一行，
 * 把标题挤到只有 76rpx，出现「滴滴…」「天天…」被截断的问题。
 */
.welfare__item {
  position: relative;
  width: 48.5%;
  height: 184rpx;
  border-radius: $radius-md;
  padding: 16rpx 14rpx;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  &:active {
    opacity: 0.85;
  }
}

.welfare__item-title {
  font-size: $fs-md;
  font-weight: 700;
  line-height: 1.25;
  @include ellipsis;
}

.welfare__item-sub {
  margin-top: 8rpx;
  font-size: 21rpx;
  opacity: 0.72;
  line-height: 1.3;
  @include ellipsis;
}

.welfare__item-action {
  margin-top: auto;
  font-size: $fs-base;
  font-weight: 600;
  max-width: 62%;
  @include ellipsis;
}

.welfare__item-icon {
  position: absolute;
  right: 6rpx;
  bottom: 12rpx;
}

/* ==================== 当前上机 ==================== */
.session-card {
  margin: 24rpx $gap-page 0;
  padding: 26rpx 24rpx;

  &:active {
    opacity: 0.95;
  }
}

.session-card__head {
  display: flex;
  align-items: center;
}

.session-card__dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: $success;
  box-shadow: 0 0 0 6rpx rgba(34, 197, 94, 0.16);
}

.session-card__status {
  margin-left: 14rpx;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
}

.session-card__no {
  margin-left: auto;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.session-card__body {
  display: flex;
  margin-top: 24rpx;
}

.session-card__col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.session-card__label {
  font-size: $fs-sm;
  color: $text-secondary;
}

.session-card__value {
  margin-top: 8rpx;
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis;
}

.session-card__value--money {
  color: $danger;
}

.session-card__foot {
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid $divider;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.session-card__tip {
  font-size: $fs-sm;
  color: $text-secondary;
}

/* ==================== 通用区块 ==================== */
.block {
  margin: 24rpx $gap-page 0;
  padding: 26rpx 24rpx;
}

/* ---------- 热门商品 ---------- */
.prod-scroll {
  margin-top: 22rpx;
  white-space: nowrap;
  width: 100%;
}

.prod-row {
  display: inline-flex;
  align-items: flex-start;
  padding-bottom: 4rpx;
}

.prod {
  width: 200rpx;
  margin-right: 20rpx;
  flex-shrink: 0;

  &:last-child {
    margin-right: 0;
  }

  &:active {
    opacity: 0.8;
  }
}

.prod__img {
  width: 200rpx;
  height: 200rpx;
  border-radius: $radius-md;
  background: $card-bg-soft;
}

.prod__name {
  display: block;
  margin-top: 14rpx;
  font-size: $fs-base;
  color: $text-primary;
  @include ellipsis;
}

.prod__price-row {
  display: flex;
  align-items: baseline;
  margin-top: 6rpx;
}

.prod__price {
  font-size: $fs-lg;
  font-weight: 700;
  color: $danger;
}

.prod__sales {
  margin-left: 10rpx;
  font-size: $fs-xs;
  color: $text-placeholder;
}

/* ---------- 附近门店 ---------- */
.store {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }

  &:active {
    opacity: 0.85;
  }
}

.store__img {
  width: 150rpx;
  height: 116rpx;
  border-radius: $radius-md;
  flex-shrink: 0;
}

.store__info {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.store__name {
  display: block;
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  @include ellipsis;
}

.store__addr {
  display: block;
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: $text-secondary;
  @include ellipsis;
}

.store__meta {
  display: flex;
  align-items: center;
  margin-top: 10rpx;
}

.store__tag {
  padding: 2rpx 12rpx;
  border-radius: $radius-xs;
  background: $brand-primary-soft;
  font-size: $fs-xs;
  color: $brand-primary;
}

.store__free {
  margin-left: 12rpx;
  font-size: $fs-xs;
  color: $success;
}

.store__distance {
  flex-shrink: 0;
  margin-left: 12rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

/* ==================== 吸底登录条 ==================== */
.login-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(#{$tabbar-height} + constant(safe-area-inset-bottom));
  bottom: calc(#{$tabbar-height} + env(safe-area-inset-bottom));
  z-index: 400;
  height: 96rpx;
  padding: 0 $gap-page;
  background: #ffffff;
  border-top: 1rpx solid $divider;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.login-bar__text {
  flex: 1;
  min-width: 0;
  margin-right: 20rpx;
  font-size: $fs-md;
  color: $text-regular;
  @include ellipsis;
}
</style>
