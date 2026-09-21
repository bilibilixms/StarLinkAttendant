<script setup lang="ts">
/**
 * 个人资料：昵称 / 性别 / 生日 / 实名认证。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon.vue'
import AppButton from '@/components/AppButton.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import { useUserStore } from '@/stores/user'
import { navBack, goLogin } from '@/utils/nav'
import { eventValue } from '@/utils/event'
import { toast, toastSuccess } from '@/utils/ui'
import { maskPhone } from '@/utils/format'
import type { Gender } from '@/types/member'

const user = useUserStore()

const nickName = ref('')
const gender = ref<Gender>(0)
const birthday = ref('')
const saving = ref(false)

const GENDERS: Array<{ value: Gender; text: string }> = [
  { value: 0, text: '保密' },
  { value: 1, text: '男' },
  { value: 2, text: '女' },
]

const genderText = computed(() => GENDERS.find((g) => g.value === gender.value)?.text ?? '保密')
const canSave = computed(() => nickName.value.trim().length > 0)

onLoad(() => {
  if (!user.isLogin) {
    goLogin('/pages/profile/index')
    return
  }
  nickName.value = user.member?.nickName || user.member?.realName || ''
  gender.value = user.member?.gender ?? 0
  birthday.value = user.member?.birthday ?? ''
})

async function onSave(): Promise<void> {
  if (!canSave.value) {
    toast('昵称不能为空')
    return
  }
  if (saving.value) return
  saving.value = true
  try {
    // 后端未提供资料更新接口，仅更新本地缓存
    if (user.member) {
      user.member.nickName = nickName.value.trim()
      user.member.gender = gender.value
      user.member.birthday = birthday.value || undefined
    }
    toastSuccess('资料已保存')
  } finally {
    saving.value = false
  }
}

/** 实名认证 */
function onVerify(): void {
  // 后端未提供实名认证接口，仅占位提示
  toast('实名认证功能开发中')
}

/** 生日变更 */
function onBirthdayChange(e: Event): void {
  birthday.value = eventValue(e)
}

function onAvatarTap(): void {
  uni.showActionSheet({
    itemList: ['从相册选择', '拍照'],
    success: () => toast('Demo 阶段暂不支持上传头像'),
  })
}
</script>

<template>
  <view class="pf page-root">
    <AppNavBar title="个人资料" show-back @back="navBack" />

    <!-- ==================== 头像 ==================== -->
    <view class="card sec">
      <view class="row row--tap" @tap="onAvatarTap">
        <text class="row__label">头像</text>
        <view class="row__right">
          <image
            v-if="user.avatar"
            class="row__avatar"
            :src="user.avatar"
            mode="aspectFill"
          />
          <view v-else class="row__avatar row__avatar--placeholder">
            <AppIcon name="tab-mine" :size="48" color="#B8BCCB" :stroke-width="1.6" />
          </view>
          <view class="row__arrow" />
        </view>
      </view>

      <view class="row">
        <text class="row__label">会员编号</text>
        <text class="row__value">{{ user.member?.memberNo || '--' }}</text>
      </view>

      <view class="row">
        <text class="row__label">手机号</text>
        <text class="row__value">{{ maskPhone(user.member?.phone) || '--' }}</text>
      </view>

      <view class="row">
        <text class="row__label">会员等级</text>
        <view class="row__right">
          <view class="level">
            <text class="level__text">{{ user.levelName }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ==================== 可编辑 ==================== -->
    <view class="card sec">
      <view class="row">
        <text class="row__label">昵称</text>
        <input
          v-model="nickName"
          class="row__input"
          maxlength="16"
          placeholder="请输入昵称"
          placeholder-class="row__ph"
        />
      </view>

      <view class="row">
        <text class="row__label">性别</text>
        <view class="genders">
          <view
            v-for="g in GENDERS"
            :key="g.value"
            class="gender"
            :class="{ 'is-active': gender === g.value }"
            @tap="gender = g.value"
          >
            <text class="gender__text">{{ g.text }}</text>
          </view>
        </view>
      </view>

      <view class="row">
        <text class="row__label">生日</text>
        <picker mode="date" :value="birthday" start="1940-01-01" @change="onBirthdayChange">
          <view class="row__right">
            <text class="row__value">{{ birthday || '请选择' }}</text>
            <view class="row__arrow" />
          </view>
        </picker>
      </view>

      <view class="row row--tap" @tap="onVerify">
        <text class="row__label">实名认证</text>
        <view class="row__right">
          <text class="row__value" :class="{ 'is-done': user.verified }">
            {{ user.verified ? '已认证' : '未认证' }}
          </text>
          <view v-if="!user.verified" class="row__arrow" />
        </view>
      </view>
    </view>

    <!-- ==================== 资产 ==================== -->
    <view class="card sec">
      <view class="row">
        <text class="row__label">账户余额</text>
        <text class="row__value row__value--money">¥{{ user.balance.toFixed(2) }}</text>
      </view>
      <view class="row">
        <text class="row__label">可用积分</text>
        <text class="row__value">{{ user.points }}</text>
      </view>
      <view class="row">
        <text class="row__label">性别</text>
        <text class="row__value">{{ genderText }}</text>
      </view>
    </view>

    <view class="pf__actions">
      <AppButton
        type="primary"
        size="lg"
        block
        :disabled="!canSave"
        :loading="saving"
        @tap="onSave"
      >
        保存资料
      </AppButton>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.pf {
  padding-bottom: 60rpx;
  @include safe-area-bottom(60rpx);
}

.sec {
  margin: 20rpx $gap-page 0;
  padding: 8rpx 24rpx;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 96rpx;
  border-bottom: 1rpx solid $divider;

  &:last-child {
    border-bottom: none;
  }
}

.row--tap:active {
  opacity: 0.7;
}

.row__label {
  font-size: $fs-md;
  color: $text-regular;
  flex-shrink: 0;
}

.row__right {
  display: flex;
  align-items: center;

  &:active {
    opacity: 0.7;
  }
}

.row__value {
  font-size: $fs-md;
  color: $text-primary;
  font-weight: 500;
}

.row__value--money {
  color: $danger;
  font-weight: 700;
}

.row__value.is-done {
  color: $success;
}

.row__input {
  flex: 1;
  min-width: 0;
  height: 80rpx;
  text-align: right;
  font-size: $fs-md;
  color: $text-primary;
}

.row__ph {
  color: $text-placeholder;
  font-size: $fs-md;
}

.row__arrow {
  width: 14rpx;
  height: 14rpx;
  margin-left: 12rpx;
  border-top: 3rpx solid $text-placeholder;
  border-right: 3rpx solid $text-placeholder;
  transform: rotate(45deg);
  border-radius: 2rpx;
}

.row__avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #e4e6ee;
}

.row__avatar--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.level {
  padding: 4rpx 16rpx;
  border-radius: $radius-xs;
  background: linear-gradient(135deg, #8b6bff 0%, #5b5bd6 100%);
}

.level__text {
  font-size: $fs-sm;
  font-weight: 700;
  color: #ffffff;
}

/* ==================== 性别 ==================== */
.genders {
  display: flex;
  align-items: center;
}

.gender {
  padding: 8rpx 24rpx;
  margin-left: 14rpx;
  border-radius: $radius-pill;
  background: $card-bg-soft;

  &.is-active {
    background: $brand-primary;
  }

  &:active {
    opacity: 0.8;
  }
}

.gender__text {
  font-size: $fs-base;
  color: $text-regular;
}

.gender.is-active .gender__text {
  color: #ffffff;
  font-weight: 600;
}

.pf__actions {
  padding: 44rpx $gap-page 0;
}
</style>
