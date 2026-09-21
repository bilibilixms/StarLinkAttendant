<script setup lang="ts">
/**
 * 登录页（手机号 + 密码）。
 *
 * 后端 starlink-module-member 已提供 POST /api/member/login 接口，
 * 小程序只面向会员，不再提供微信一键登录、Demo 登录与绑定已有会员。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppStatusBar from '@/components/AppStatusBar.vue'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { reLaunch, navBack } from '@/utils/nav'
import { toast, toastSuccess } from '@/utils/ui'

const user = useUserStore()
const cart = useCartStore()

const redirect = ref('/pages/index/index')
const agreed = ref(false)
const phone = ref('')
const password = ref('')
const submitting = ref(false)

const canSubmit = computed(() => /^1[3-9]\d{9}$/.test(phone.value) && password.value.length >= 6)

onLoad((query) => {
  if (query?.redirect) {
    try {
      redirect.value = decodeURIComponent(String(query.redirect))
    } catch {
      redirect.value = String(query.redirect)
    }
  }
})

function ensureAgreed(): boolean {
  if (!agreed.value) {
    toast('请先阅读并同意用户协议与隐私政策')
    return false
  }
  return true
}

function afterLogin(): void {
  cart.restore()
  toastSuccess('登录成功')
  setTimeout(() => reLaunch(redirect.value), 600)
}

async function onLogin(): Promise<void> {
  if (!ensureAgreed()) return
  if (!canSubmit.value) {
    toast('请填写正确的手机号与 6 位以上密码')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    await user.loginByPhonePassword(phone.value, password.value)
    afterLogin()
  } catch {
    /* request 层已提示 */
  } finally {
    submitting.value = false
  }
}

function onAgreement(type: 'user' | 'privacy'): void {
  uni.showModal({
    title: type === 'user' ? '用户协议' : '隐私政策',
    content:
      type === 'user'
        ? '本小程序为网吧管理系统演示项目。登录即表示您同意我们为您创建会员账号，并使用您的手机号进行身份识别。'
        : '我们仅收集完成上机、充值、点单所必需的信息（手机号、消费记录），不会向第三方出售您的个人信息。',
    showCancel: false,
    confirmColor: '#5B5BD6',
  })
}

function onSkip(): void {
  navBack()
}
</script>

<template>
  <view class="login page-root">
    <!-- ==================== 头部品牌区 ==================== -->
    <view class="login__header">
      <AppStatusBar />
      <view class="login__brand">
        <view class="login__logo">
          <text class="login__logo-text">网鱼</text>
        </view>
        <text class="login__title">网鱼电竞</text>
        <text class="login__subtitle">登录网鱼会员享多重权益</text>
      </view>
    </view>

    <!-- ==================== 权益 ==================== -->
    <view class="benefits">
      <view v-for="b in ['余额通网吧通用', '下单送到机位', '会员专属折扣', '积分兑换好礼']" :key="b" class="benefit">
        <AppIcon name="check" :size="32" color="#5B5BD6" :stroke-width="2.2" />
        <text class="benefit__text">{{ b }}</text>
      </view>
    </view>

    <!-- ==================== 登录表单 ==================== -->
    <view class="card login__card">
      <view class="field">
        <text class="field__label">手机号</text>
        <input
          v-model="phone"
          class="field__input"
          type="number"
          maxlength="11"
          placeholder="请输入手机号"
          placeholder-class="field__ph"
        />
      </view>
      <view class="field">
        <text class="field__label">密码</text>
        <input
          v-model="password"
          class="field__input"
          password
          maxlength="20"
          placeholder="请输入密码（6 位以上）"
          placeholder-class="field__ph"
        />
      </view>

      <AppButton
        type="primary"
        size="lg"
        block
        class="login__btn-gap"
        :disabled="!canSubmit"
        :loading="submitting"
        @tap="onLogin"
      >
        登录
      </AppButton>

      <!-- 协议 -->
      <view class="agree" @tap="agreed = !agreed">
        <view class="agree__box" :class="{ 'is-checked': agreed }">
          <AppIcon v-if="agreed" name="check" :size="24" color="#FFFFFF" :stroke-width="3" />
        </view>
        <text class="agree__text">
          我已阅读并同意
          <text class="agree__link" @tap.stop="onAgreement('user')">《用户协议》</text>
          和
          <text class="agree__link" @tap.stop="onAgreement('privacy')">《隐私政策》</text>
        </text>
      </view>
    </view>

    <text class="login__skip" @tap="onSkip">暂不登录，先逛逛</text>
  </view>
</template>

<style lang="scss" scoped>
.login {
  background: $page-bg;
  min-height: 100vh;
  @include safe-area-bottom(60rpx);
}

/* ==================== 头部 ==================== */
.login__header {
  background: $grad-home-header;
  padding-bottom: 60rpx;
}

.login__brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 70rpx $gap-page 0;
}

.login__logo {
  width: 150rpx;
  height: 150rpx;
  border-radius: 40rpx;
  background: $brand-gradient;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 30rpx rgba(91, 91, 214, 0.3);
}

.login__logo-text {
  font-size: 48rpx;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 2rpx;
}

.login__title {
  margin-top: 28rpx;
  font-size: 48rpx;
  font-weight: 800;
  color: $text-primary;
  letter-spacing: 2rpx;
}

.login__subtitle {
  margin-top: 12rpx;
  font-size: $fs-md;
  color: $text-regular;
}

/* ==================== 权益 ==================== */
.benefits {
  display: flex;
  flex-wrap: wrap;
  padding: 32rpx $gap-page 8rpx;
}

.benefit {
  width: 50%;
  display: flex;
  align-items: center;
  padding: 14rpx 0;
}

.benefit__text {
  margin-left: 12rpx;
  font-size: $fs-md;
  color: $text-regular;
}

/* ==================== 登录卡 ==================== */
.login__card {
  margin: 16rpx $gap-page 0;
  padding: 40rpx 32rpx 32rpx;
}

.login__btn-gap {
  margin-top: 24rpx;
}

.login__hint {
  display: block;
  margin-top: 20rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
  line-height: 1.6;
}

/* ---------- 表单 ---------- */
.field {
  display: flex;
  flex-direction: column;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $divider;
}

.field__label {
  font-size: $fs-base;
  color: $text-secondary;
}

.field__input {
  margin-top: 12rpx;
  height: 60rpx;
  font-size: $fs-lg;
  color: $text-primary;
  width: 100%;
}

.field__ph {
  color: $text-placeholder;
  font-size: $fs-md;
}

/* ---------- 协议 ---------- */
.agree {
  display: flex;
  align-items: flex-start;
  margin-top: 36rpx;
}

.agree__box {
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  border: 2rpx solid #c8cad6;
  flex-shrink: 0;
  margin-top: 2rpx;
  display: flex;
  align-items: center;
  justify-content: center;

  &.is-checked {
    background: $brand-primary;
    border-color: $brand-primary;
  }
}

.agree__text {
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
  font-size: $fs-sm;
  color: $text-secondary;
  line-height: 1.6;
}

.agree__link {
  color: $brand-primary;
}

.login__skip {
  display: block;
  margin-top: 48rpx;
  text-align: center;
  font-size: $fs-md;
  color: $text-placeholder;

  &:active {
    opacity: 0.6;
  }
}
</style>
