<script setup lang="ts">
/**
 * 意见反馈 / 投诉举报。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import { submitFeedback } from '@/api/store'
import { useUserStore } from '@/stores/user'
import { navBack } from '@/utils/nav'
import { eventValue } from '@/utils/event'
import { toast, toastSuccess } from '@/utils/ui'

const user = useUserStore()

const TYPES = [
  { key: 'bug', text: '故障反馈', desc: '上机 / 支付 / 小程序异常' },
  { key: 'advice', text: '功能建议', desc: '希望增加什么功能' },
  { key: 'report', text: '投诉举报', desc: '服务或环境问题' },
  { key: 'other', text: '其他', desc: '其他想说的话' },
]

const type = ref('bug')
const content = ref('')
const contact = ref('')
const submitting = ref(false)
const contentLen = computed(() => content.value.trim().length)
const canSubmit = computed(() => contentLen.value >= 5 && !submitting.value)

onLoad((query) => {
  if (query?.type === 'report') type.value = 'report'
  contact.value = user.member?.phone ?? ''
})

function onContentInput(e: Event): void {
  content.value = eventValue(e)
}

function onContactInput(e: Event): void {
  contact.value = eventValue(e)
}

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) {
    toast('反馈内容至少 5 个字')
    return
  }
  submitting.value = true
  uni.showLoading({ title: '提交中...', mask: true })
  try {
    await submitFeedback({
      type: type.value,
      content: content.value.trim(),
      contact: contact.value.trim() || undefined,
    })
    uni.hideLoading()
    toastSuccess('已提交，感谢您的反馈')
    setTimeout(() => navBack(), 900)
  } catch (e) {
    uni.hideLoading()
    const msg = (e as Error)?.message
    if (msg) toast(msg)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="fb page-root">
    <AppNavBar title="意见反馈" show-back @back="navBack" />

    <!-- ==================== 反馈类型 ==================== -->
    <view class="card sec">
      <text class="sec__title">反馈类型</text>
      <view class="types">
        <view
          v-for="t in TYPES"
          :key="t.key"
          class="type"
          :class="{ 'is-active': type === t.key }"
          @tap="type = t.key"
        >
          <text class="type__text">{{ t.text }}</text>
        </view>
      </view>
      <text class="sec__hint">{{ TYPES.find((t) => t.key === type)?.desc }}</text>
    </view>

    <!-- ==================== 内容 ==================== -->
    <view class="card sec">
      <view class="flex-between">
        <text class="sec__title sec__title--inline">详细描述</text>
        <text class="sec__counter" :class="{ 'is-warn': contentLen > 0 && contentLen < 5 }">
          {{ contentLen }}/200
        </text>
      </view>
      <textarea
        class="fb__textarea"
        :value="content"
        maxlength="200"
        placeholder="请描述遇到的问题或您的建议，越具体越有助于我们改进（至少 5 个字）"
        placeholder-class="fb__ph"
        @input="onContentInput"
      />
    </view>

    <!-- ==================== 联系方式 ==================== -->
    <view class="card sec">
      <view class="frow">
        <text class="frow__label">联系方式</text>
        <input
          class="frow__input"
          :value="contact"
          maxlength="30"
          placeholder="手机号 / 微信号（选填）"
          placeholder-class="frow__ph"
          @input="onContactInput"
        />
      </view>
      <text class="sec__hint">我们会通过该联系方式回复您，不填写则视为无需回复</text>
    </view>

    <!-- ==================== 提示 ==================== -->
    <view class="card notice">
      <AppIcon name="headset" :size="34" color="#4A9BFF" :stroke-width="1.7" />
      <view class="notice__info">
        <text class="notice__title">紧急问题请直接联系门店</text>
        <text class="notice__desc">上机故障、设备问题时，找网管处理最快</text>
      </view>
    </view>

    <view class="fb__actions">
      <AppButton
        type="primary"
        size="lg"
        block
        :disabled="!canSubmit"
        :loading="submitting"
        @tap="onSubmit"
      >
        提交反馈
      </AppButton>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.fb {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

.sec {
  margin: 20rpx $gap-page 0;
  padding: 26rpx 24rpx;
}

.sec__title {
  display: block;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
}

.sec__title--inline {
  margin-bottom: 0;
}

.sec__counter {
  font-size: $fs-sm;
  color: $text-placeholder;
}

.sec__counter.is-warn {
  color: $warning;
}

.sec__hint {
  display: block;
  margin-top: 16rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  line-height: 1.6;
}

/* ==================== 类型 ==================== */
.types {
  display: flex;
  flex-wrap: wrap;
  margin-top: 20rpx;
}

.type {
  padding: 14rpx 32rpx;
  margin: 0 16rpx 16rpx 0;
  border-radius: $radius-pill;
  background: $card-bg-soft;

  &.is-active {
    background: $brand-primary;
  }

  &:active {
    opacity: 0.8;
  }
}

.type__text {
  font-size: $fs-md;
  color: $text-regular;
}

.type.is-active .type__text {
  color: #ffffff;
  font-weight: 600;
}

/* ==================== 输入 ==================== */
.fb__textarea {
  width: 100%;
  height: 280rpx;
  margin-top: 20rpx;
  padding: 20rpx;
  border-radius: $radius-md;
  background: $card-bg-soft;
  font-size: $fs-md;
  color: $text-primary;
  line-height: 1.6;
}

.fb__ph {
  color: $text-placeholder;
  font-size: $fs-md;
}

.frow {
  display: flex;
  align-items: center;
  min-height: 88rpx;
}

.frow__label {
  width: 150rpx;
  flex-shrink: 0;
  font-size: $fs-md;
  color: $text-regular;
}

.frow__input {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  text-align: right;
  font-size: $fs-md;
  color: $text-primary;
}

.frow__ph {
  color: $text-placeholder;
  font-size: $fs-md;
}

/* ==================== 提示 ==================== */
.notice {
  display: flex;
  align-items: center;
  padding: 24rpx;
  background: #f2f8ff;
}

.notice__info {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
}

.notice__title {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
}

.notice__desc {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-secondary;
}

.fb__actions {
  padding: 44rpx $gap-page 0;
}
</style>
