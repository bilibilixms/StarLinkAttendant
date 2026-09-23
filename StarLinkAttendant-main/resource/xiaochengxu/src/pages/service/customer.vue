<script setup lang="ts">
/**
 * 在线客服：常见问题 + 电话 + 意见反馈入口。
 * 真实在线会话属于 P2（订阅消息 / 客服系统），当前先做自助答疑。
 */
import { ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import { useAppStore } from '@/stores/app'
import { navBack, navTo } from '@/utils/nav'
import { toast } from '@/utils/ui'

const app = useAppStore()

interface Faq {
  q: string
  a: string
}

const FAQS: Faq[] = [
  {
    q: '扫码后提示「二维码无效」怎么办？',
    a: '请确认扫描的是机位屏幕或桌贴上的二维码。如果仍然失败，可以让网管在收银台为您手动开机。',
  },
  {
    q: '上机费用是怎么算的？',
    a: '按机位所在区域的费率计费（普通区 4 元/小时、高级区 6 元/小时、豪华包间 12 元/小时），会员等级越高折扣越大，钻石会员享 85 折。下机时从余额自动结算。',
  },
  {
    q: '余额可以退款吗？',
    a: '未消费的充值余额可在门店前台申请退款，赠送金额不予退还。已消费部分不支持退款。',
  },
  {
    q: '点单后多久能送到机位？',
    a: '吧台出餐后由服务员送达，通常 10-15 分钟。高峰期可能稍慢，可在订单详情查看状态。',
  },
  {
    q: '临时下机后机位会保留吗？',
    a: '会。临时下机期间计时暂停，机位为您保留，回到机位点击「恢复上机」即可继续。',
  },
  {
    q: '优惠券为什么用不了？',
    a: '请检查券的适用范围（网费 / 商品 / 酒店）与满减门槛是否满足，以及是否已过期。',
  },
]

const openIndex = ref<number>(-1)

function toggle(i: number): void {
  openIndex.value = openIndex.value === i ? -1 : i
}

function onCall(): void {
  uni.makePhoneCall({
    phoneNumber: '02162378888',
    fail: () => toast('客服电话：021-6237 8888'),
  })
}

function onCopyWx(): void {
  uni.setClipboardData({
    data: 'wonyu-service',
    success: () => toast('客服微信已复制'),
  })
}

function onFeedback(): void {
  navTo('/pages/feedback/index')
}

function onLiveChat(): void {
  uni.showModal({
    title: '人工客服',
    content:
      '在线人工客服需要接入客服消息系统（需求中为 P2 阶段）。\n\n当前可拨打门店电话 021-6237 8888，或提交意见反馈，我们会尽快回复。',
    confirmText: '去反馈',
    cancelText: '打电话',
    confirmColor: '#5B5BD6',
    success: (res) => {
      if (res.confirm) onFeedback()
      else onCall()
    },
  })
}
</script>

<template>
  <view class="cs page-root">
    <AppNavBar title="在线客服" show-back @back="navBack" />

    <!-- ==================== 头部 ==================== -->
    <view class="hero">
      <view class="hero__icon">
        <AppIcon name="headset" :size="76" color="#FFFFFF" :stroke-width="1.6" />
      </view>
      <text class="hero__title">需要什么帮助？</text>
      <text class="hero__sub">{{ app.store.name }}</text>

      <view class="hero__actions">
        <view class="hero__action" @tap="onLiveChat">
          <AppIcon name="comment" :size="44" color="#FFFFFF" :stroke-width="1.7" />
          <text class="hero__action-text">人工客服</text>
        </view>
        <view class="hero__action" @tap="onCall">
          <AppIcon name="phone" :size="44" color="#FFFFFF" :stroke-width="1.7" />
          <text class="hero__action-text">电话咨询</text>
        </view>
        <view class="hero__action" @tap="onFeedback">
          <AppIcon name="edit" :size="44" color="#FFFFFF" :stroke-width="1.7" />
          <text class="hero__action-text">意见反馈</text>
        </view>
      </view>
    </view>

    <!-- ==================== 常见问题 ==================== -->
    <view class="card sec">
      <text class="sec__title">常见问题</text>

      <view v-for="(f, i) in FAQS" :key="i" class="faq">
        <view class="faq__q" @tap="toggle(i)">
          <text class="faq__q-text">{{ f.q }}</text>
          <view class="faq__arrow" :class="{ 'is-open': openIndex === i }" />
        </view>
        <view v-if="openIndex === i" class="faq__a">
          <text class="faq__a-text">{{ f.a }}</text>
        </view>
      </view>
    </view>

    <!-- ==================== 联系方式 ==================== -->
    <view class="card sec">
      <text class="sec__title">联系我们</text>

      <view class="row row--tap" @tap="onCall">
        <AppIcon name="phone" :size="40" color="#5B5BD6" :stroke-width="1.7" />
        <view class="row__body">
          <text class="row__label">门店电话</text>
          <text class="row__value">021-6237 8888</text>
        </view>
        <view class="row__arrow" />
      </view>

      <view class="row row--tap" @tap="onCopyWx">
        <AppIcon name="comment" :size="40" color="#22C55E" :stroke-width="1.7" />
        <view class="row__body">
          <text class="row__label">客服微信</text>
          <text class="row__value">wonyu-service</text>
        </view>
        <text class="row__copy">复制</text>
      </view>

      <view class="row">
        <AppIcon name="location" :size="40" color="#FF9F43" :stroke-width="1.7" />
        <view class="row__body">
          <text class="row__label">门店地址</text>
          <text class="row__value">{{ app.store.address }}</text>
        </view>
      </view>
    </view>

    <view class="cs__footer">
      <text class="cs__footer-text">服务时间：门店营业时间内 · 24 小时营业门店全天在线</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.cs {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

/* ==================== 头部 ==================== */
.hero {
  margin: 20rpx $gap-page 0;
  padding: 44rpx 28rpx 34rpx;
  border-radius: $radius-lg;
  background: linear-gradient(135deg, #6c8cff 0%, #5b5bd6 55%, #7c5cf0 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 12rpx 30rpx rgba(91, 91, 214, 0.26);
}

.hero__icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero__title {
  margin-top: 24rpx;
  font-size: 40rpx;
  font-weight: 800;
  color: #ffffff;
}

.hero__sub {
  margin-top: 10rpx;
  font-size: $fs-base;
  color: rgba(255, 255, 255, 0.82);
  text-align: center;
  @include ellipsis;
  max-width: 100%;
}

.hero__actions {
  display: flex;
  align-items: center;
  width: 100%;
  margin-top: 36rpx;
  padding-top: 30rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.22);
}

.hero__action {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  &:active {
    opacity: 0.7;
  }
}

.hero__action-text {
  margin-top: 12rpx;
  font-size: $fs-base;
  color: #ffffff;
}

/* ==================== 区块 ==================== */
.sec {
  margin: 20rpx $gap-page 0;
  padding: 26rpx 24rpx;
}

.sec__title {
  display: block;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 12rpx;
}

/* ==================== FAQ ==================== */
.faq {
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.faq__q {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 0;

  &:active {
    opacity: 0.7;
  }
}

.faq__q-text {
  flex: 1;
  min-width: 0;
  margin-right: 20rpx;
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
  line-height: 1.5;
}

.faq__arrow {
  width: 14rpx;
  height: 14rpx;
  flex-shrink: 0;
  border-right: 3rpx solid $text-placeholder;
  border-bottom: 3rpx solid $text-placeholder;
  transform: rotate(45deg) translate(-2rpx, -2rpx);
  border-radius: 2rpx;
  transition: transform 0.2s ease;

  &.is-open {
    transform: rotate(-135deg) translate(-2rpx, -2rpx);
  }
}

.faq__a {
  padding: 0 0 26rpx;
}

.faq__a-text {
  font-size: $fs-base;
  color: $text-secondary;
  line-height: 1.7;
}

/* ==================== 联系 ==================== */
.row {
  display: flex;
  align-items: center;
  min-height: 104rpx;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.row--tap:active {
  opacity: 0.7;
}

.row__body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.row__label {
  display: block;
  font-size: $fs-sm;
  color: $text-secondary;
}

.row__value {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-md;
  font-weight: 500;
  color: $text-primary;
  @include ellipsis;
}

.row__arrow {
  width: 14rpx;
  height: 14rpx;
  flex-shrink: 0;
  border-top: 3rpx solid $text-placeholder;
  border-right: 3rpx solid $text-placeholder;
  transform: rotate(45deg);
  border-radius: 2rpx;
}

.row__copy {
  flex-shrink: 0;
  padding: 4rpx 16rpx;
  border-radius: $radius-xs;
  border: 2rpx solid $brand-primary;
  font-size: $fs-xs;
  color: $brand-primary;
}

.cs__footer {
  display: flex;
  justify-content: center;
  padding: 40rpx $gap-page 0;
}

.cs__footer-text {
  font-size: $fs-sm;
  color: $text-placeholder;
  text-align: center;
}
</style>
