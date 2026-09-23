<script setup lang="ts">
/**
 * 在线充值。
 * 选套餐 → 看「充值金额 / 赠送金额 / 实际到账」→ 立即充值。
 * Demo 阶段支付直接标记成功（后端 payment_record 已预留微信支付字段）。
 */
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import StateView from '@/components/StateView.vue'
import { rechargeApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { navBack, navTo, goLogin } from '@/utils/nav'
import { eventValue } from '@/utils/event'
import { toast, toastSuccess, confirm } from '@/utils/ui'
import { formatMoney, genIdempotentKey } from '@/utils/format'
import type { RechargePlan, RechargePreview } from '@/types/store'

const user = useUserStore()

const plans = ref<RechargePlan[]>([])
const state = ref<'loading' | 'empty' | 'error' | 'success'>('loading')
const selected = ref<RechargePlan | null>(null)
const customAmount = ref('')
const payChannel = ref<'wechat' | 'balance'>('wechat')
const submitting = ref(false)
const records = ref<Array<{ id: number; rechargeNo: string; actualAmount: number; createdAt: string }>>([])

/** 实际充值金额：选了套餐用套餐金额，否则用自定义金额 */
const amount = computed(() => selected.value?.amount ?? Number(customAmount.value || 0))

/**
 * 赠送与到账金额一律来自后端「充值试算」接口。
 * 前端不复制活动阶梯规则（否则规则一改，页面就会与真实入账不一致）。
 * 未取到试算结果时回退显示「赠送 0 / 到账=充值金额」，仅为兜底展示，
 * 真实入账始终以提交后后端返回的金额为准。
 */
const preview = ref<RechargePreview | null>(null)
const bonus = computed(() => preview.value?.bonusAmount ?? 0)
const actual = computed(() => preview.value?.totalAmount ?? amount.value)
const canSubmit = computed(() => amount.value > 0 && !submitting.value)

let previewTimer: ReturnType<typeof setTimeout> | undefined

/** 向后端试算当前金额的赠送/到账（防抖，未登录时不请求） */
function schedulePreview(delay = 300): void {
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = setTimeout(() => {
    void fetchPreview()
  }, delay)
}

async function fetchPreview(): Promise<void> {
  const value = amount.value
  if (!user.isLogin || !user.member || value <= 0) {
    preview.value = null
    return
  }
  try {
    preview.value = await rechargeApi.previewRecharge(user.member.id, value)
  } catch {
    // 试算失败只影响展示，不阻断充值：回退为「赠送 0、到账=充值金额」
    preview.value = null
  }
}

/**
 * 幂等键：同一个「金额 + 支付方式」的请求复用同一个键，
 * 用户改金额视为新请求 → 生成新键；提交成功 → 清空，下次充值重新生成。
 * 这样网络重试/重复点击同一笔不会重复入账，而真正的新充值不会因复用旧键被误判为重复。
 */
const pendingKey = ref('')
const pendingKeyFingerprint = ref('')

function idempotentKeyFor(): string {
  const fingerprint = `${amount.value}|${payChannel.value}`
  if (!pendingKey.value || pendingKeyFingerprint.value !== fingerprint) {
    pendingKey.value = genIdempotentKey('RC')
    pendingKeyFingerprint.value = fingerprint
  }
  return pendingKey.value
}

function clearPendingKey(): void {
  pendingKey.value = ''
  pendingKeyFingerprint.value = ''
}

async function load(): Promise<void> {
  state.value = 'loading'
  // 后端未提供充值套餐接口（RechargeController 无 /plans 端点），直接进入自定义金额模式
  plans.value = []
  selected.value = null
  state.value = 'success'
  void loadRecords()
}

async function loadRecords(): Promise<void> {
  if (!user.isLogin || !user.member) return
  try {
    const res = await rechargeApi.getRechargeRecords(user.member.id, { current: 1, size: 3 })
    records.value = res.records
  } catch {
    records.value = []
  }
}

onLoad(() => {
  void load()
})

onUnload(() => {
  if (previewTimer) clearTimeout(previewTimer)
})

/* ==================== 交互 ==================== */

function pickPlan(p: RechargePlan): void {
  selected.value = p
  customAmount.value = ''
  void fetchPreview()
}

function onCustomInput(e: Event): void {
  const raw = eventValue(e).replace(/[^\d.]/g, '')
  customAmount.value = raw
  if (raw) selected.value = null
  schedulePreview()
}

async function onSubmit(): Promise<void> {
  if (!user.isLogin) {
    goLogin('/pages/recharge/index')
    return
  }
  if (!canSubmit.value) {
    toast('请选择充值套餐或输入充值金额')
    return
  }
  const ok = await confirm(
    `确认充值 ¥${formatMoney(amount.value)}？\n实际到账 ¥${formatMoney(actual.value)}`,
    '确认充值',
    '确认充值'
  )
  if (!ok) return

  submitting.value = true
  uni.showLoading({ title: '支付中...', mask: true })
  try {
    const res = await rechargeApi.recharge(user.member!.id, {
      amount: amount.value,
      payChannel: payChannel.value,
      planId: selected.value?.id,
      // 同一笔请求复用同一个幂等键；失败重试仍用它，成功后才清空
      idempotentKey: idempotentKeyFor(),
    })
    user.patchBalance(res.balanceAfter)
    clearPendingKey()
    toastSuccess(`充值成功，到账 ¥${formatMoney(res.actualAmount)}`)
    void loadRecords()
    setTimeout(() => navBack(), 900)
  } catch (e) {
    // 401/403/500/网络错误/业务错误都在此收敛，提示后结束加载
    const msg = (e as Error)?.message
    if (msg) toast(msg)
  } finally {
    // 唯一的关闭点：无论成功、失败还是抛错，都不会永久卡在「支付中...」
    uni.hideLoading()
    submitting.value = false
  }
}
</script>

<template>
  <view class="rc page-root">
    <AppNavBar title="在线充值" show-back @back="navBack" />

    <!-- ==================== 余额 ==================== -->
    <view class="rc__balance">
      <text class="rc__balance-label">当前余额（元）</text>
      <text class="rc__balance-value">{{ user.isLogin ? formatMoney(user.balance) : '--' }}</text>
      <text class="rc__balance-tip">充值后余额全门店通用，可用于上机与点单</text>
    </view>

    <StateView v-if="state !== 'success'" :state="state" @retry="load" />

    <template v-else>
      <!-- ==================== 套餐 ==================== -->
      <view class="card rc__sec">
        <text class="rc__sec-title">选择充值套餐</text>
        <view class="plans">
          <view
            v-for="p in plans"
            :key="p.id"
            class="plan"
            :class="{ 'is-active': selected?.id === p.id }"
            @tap="pickPlan(p)"
          >
            <view v-if="p.tag" class="plan__tag">
              <text class="plan__tag-text">{{ p.tag }}</text>
            </view>
            <view class="plan__amount">
              <text class="plan__symbol">¥</text>
              <text class="plan__num">{{ p.amount }}</text>
            </view>
            <text class="plan__bonus">{{ p.bonus > 0 ? `送${p.bonus}元` : '无赠送' }}</text>
            <text class="plan__actual">到账 ¥{{ p.actualAmount }}</text>
          </view>
        </view>

        <!-- 自定义金额 -->
        <view class="custom">
          <text class="custom__label">其他金额</text>
          <view class="custom__input-wrap">
            <text class="custom__symbol">¥</text>
            <input
              class="custom__input"
              type="digit"
              :value="customAmount"
              placeholder="输入充值金额"
              placeholder-class="custom__ph"
              @input="onCustomInput"
            />
          </view>
        </view>
      </view>

      <!-- ==================== 金额明细 ==================== -->
      <view class="card rc__sec">
        <text class="rc__sec-title">金额明细</text>
        <view class="amt">
          <text class="amt__label">充值金额</text>
          <text class="amt__value">{{ formatMoney(amount) }}</text>
        </view>
        <view class="amt">
          <text class="amt__label">赠送金额</text>
          <text class="amt__value amt__value--bonus">+{{ formatMoney(bonus) }}</text>
        </view>
        <view class="amt amt--total">
          <text class="amt__label">实际到账</text>
          <text class="amt__value amt__value--total">¥{{ formatMoney(actual) }}</text>
        </view>
        <view v-if="selected?.bonusPoints" class="amt">
          <text class="amt__label">额外赠送积分</text>
          <text class="amt__value amt__value--bonus">{{ selected.bonusPoints }} 积分</text>
        </view>
      </view>

      <!-- ==================== 支付方式 ==================== -->
      <view class="card rc__sec">
        <text class="rc__sec-title">支付方式</text>
        <view class="pay" @tap="payChannel = 'wechat'">
          <AppIcon name="pay-wechat" :size="44" color="#22C55E" />
          <view class="pay__info">
            <text class="pay__name">微信支付</text>
            <text class="pay__desc">Demo 阶段模拟支付成功</text>
          </view>
          <view class="pay__radio" :class="{ 'is-checked': payChannel === 'wechat' }">
            <AppIcon
              v-if="payChannel === 'wechat'"
              name="check"
              :size="24"
              color="#FFFFFF"
              :stroke-width="3"
            />
          </view>
        </view>
      </view>

      <!-- ==================== 充值记录 ==================== -->
      <view v-if="records.length" class="card rc__sec">
        <view class="flex-between">
          <text class="rc__sec-title">最近充值</text>
          <text class="rc__more" @tap="navTo('/pages/consume/index')">全部记录 ›</text>
        </view>
        <view v-for="r in records" :key="r.id" class="rec">
          <view class="rec__left">
            <text class="rec__title">余额充值</text>
            <text class="rec__no">{{ r.rechargeNo }}</text>
          </view>
          <text class="rec__amount">+¥{{ formatMoney(r.actualAmount) }}</text>
        </view>
      </view>

      <!-- ==================== 底部 ==================== -->
      <view class="rc__bar">
        <view class="rc__bar-info">
          <text class="rc__bar-label">到账金额</text>
          <text class="rc__bar-money">¥{{ formatMoney(actual) }}</text>
        </view>
        <AppButton
          type="primary"
          size="lg"
          class="rc__bar-btn"
          :disabled="!canSubmit"
          :loading="submitting"
          @tap="onSubmit"
        >
          立即充值
        </AppButton>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.rc {
  padding-bottom: 180rpx;
  @include safe-area-bottom(0rpx);
}

/* ==================== 余额 ==================== */
.rc__balance {
  margin: 20rpx $gap-page 0;
  padding: 40rpx 32rpx;
  border-radius: $radius-lg;
  background: $brand-gradient;
  display: flex;
  flex-direction: column;
  box-shadow: 0 10rpx 26rpx rgba(91, 91, 214, 0.25);
}

.rc__balance-label {
  font-size: $fs-base;
  color: rgba(255, 255, 255, 0.85);
}

.rc__balance-value {
  margin-top: 10rpx;
  font-size: 72rpx;
  font-weight: 800;
  color: #ffffff;
  line-height: 1.1;
}

.rc__balance-tip {
  margin-top: 14rpx;
  font-size: $fs-sm;
  color: rgba(255, 255, 255, 0.78);
}

/* ==================== 区块 ==================== */
.rc__sec {
  margin: 20rpx $gap-page 0;
  padding: 26rpx 24rpx;
}

.rc__sec-title {
  display: block;
  font-size: $fs-lg;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 20rpx;
}

.rc__more {
  font-size: $fs-base;
  color: $brand-primary;
  margin-bottom: 20rpx;
}

/* ==================== 套餐 ==================== */
.plans {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.plan {
  position: relative;
  width: 31.5%;
  margin-bottom: 18rpx;
  padding: 26rpx 8rpx 20rpx;
  border-radius: $radius-md;
  border: 2rpx solid $divider;
  background: $card-bg-soft;
  display: flex;
  flex-direction: column;
  align-items: center;

  &.is-active {
    border-color: $brand-primary;
    background: $brand-primary-soft;
  }

  &:active {
    opacity: 0.85;
  }
}

.plan__tag {
  position: absolute;
  right: -2rpx;
  top: -2rpx;
  padding: 2rpx 12rpx;
  border-radius: 0 $radius-md 0 $radius-md;
  background: linear-gradient(135deg, #ff8a5b 0%, #f5515f 100%);
}

.plan__tag-text {
  font-size: 18rpx;
  color: #ffffff;
  font-weight: 600;
}

.plan__amount {
  display: flex;
  align-items: baseline;
}

.plan__symbol {
  font-size: $fs-md;
  font-weight: 700;
  color: $danger;
}

.plan__num {
  font-size: 48rpx;
  font-weight: 800;
  color: $danger;
  line-height: 1.1;
}

.plan__bonus {
  margin-top: 8rpx;
  font-size: $fs-sm;
  color: #e8465e;
}

.plan__actual {
  margin-top: 4rpx;
  font-size: $fs-xs;
  color: $text-secondary;
}

/* ---------- 自定义 ---------- */
.custom {
  margin-top: 12rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid $divider;
  display: flex;
  align-items: center;
}

.custom__label {
  width: 150rpx;
  flex-shrink: 0;
  font-size: $fs-md;
  color: $text-regular;
}

.custom__input-wrap {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  border-radius: $radius-sm;
  background: $card-bg-soft;
  display: flex;
  align-items: center;
  padding: 0 20rpx;
}

.custom__symbol {
  font-size: $fs-lg;
  font-weight: 700;
  color: $danger;
}

.custom__input {
  flex: 1;
  min-width: 0;
  margin-left: 10rpx;
  height: 100%;
  font-size: $fs-md;
  color: $text-primary;
}

.custom__ph {
  color: $text-placeholder;
  font-size: $fs-md;
}

/* ==================== 金额 ==================== */
.amt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10rpx 0;
}

.amt--total {
  margin-top: 8rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid $divider;
}

.amt__label {
  font-size: $fs-md;
  color: $text-secondary;
}

.amt__value {
  font-size: $fs-md;
  color: $text-primary;
}

.amt__value--bonus {
  color: #e8465e;
}

.amt__value--total {
  font-size: 40rpx;
  font-weight: 800;
  color: $danger;
}

/* ==================== 支付方式 ==================== */
.pay {
  display: flex;
  align-items: center;
  padding: 20rpx 0;

  &:active {
    opacity: 0.8;
  }
}

.pay__info {
  flex: 1;
  min-width: 0;
  margin-left: 18rpx;
}

.pay__name {
  font-size: $fs-md;
  font-weight: 600;
  color: $text-primary;
}

.pay__desc {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.pay__radio {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  border: 2rpx solid #c8cad6;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &.is-checked {
    background: $brand-primary;
    border-color: $brand-primary;
  }
}

/* ==================== 充值记录 ==================== */
.rec {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 0;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.rec__left {
  flex: 1;
  min-width: 0;
}

.rec__title {
  font-size: $fs-md;
  color: $text-primary;
}

.rec__no {
  display: block;
  margin-top: 6rpx;
  font-size: $fs-sm;
  color: $text-placeholder;
}

.rec__amount {
  flex-shrink: 0;
  font-size: $fs-lg;
  font-weight: 700;
  color: $success;
}

/* ==================== 底部 ==================== */
.rc__bar {
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

.rc__bar-info {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
}

.rc__bar-label {
  font-size: $fs-md;
  color: $text-secondary;
}

.rc__bar-money {
  margin-left: 10rpx;
  font-size: 44rpx;
  font-weight: 800;
  color: $danger;
}

.rc__bar-btn {
  flex-shrink: 0;
  min-width: 260rpx;
}
</style>
