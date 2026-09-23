<script setup lang="ts">
/**
 * 统一按钮。
 * 所有可点击元素都用它，自带禁用态与防重复点击。
 */
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'

const props = withDefaults(
  defineProps<{
    /** primary=实心品牌色 / ghost=描边 / soft=浅底 / gold=金色（会员条签到） */
    type?: 'primary' | 'ghost' | 'soft' | 'gold' | 'text'
    size?: 'sm' | 'md' | 'lg'
    block?: boolean
    disabled?: boolean
    loading?: boolean
    /** 胶囊圆角，默认 true */
    pill?: boolean
    /** 按钮文字前的图标名（对应 components/icons.ts） */
    icon?: string
    /** 图标颜色，默认跟随按钮类型 */
    iconColor?: string
  }>(),
  {
    type: 'primary',
    size: 'md',
    block: false,
    disabled: false,
    loading: false,
    pill: true,
    icon: '',
    iconColor: '',
  }
)

const emit = defineEmits<{ (e: 'tap'): void }>()

const classes = computed(() => [
  'app-btn',
  `is-${props.type}`,
  `is-${props.size}`,
  { 'is-block': props.block, 'is-disabled': props.disabled || props.loading, 'is-pill': props.pill },
])

/** 默认图标色按按钮类型给，没特殊要求时不用传 */
const DEFAULT_ICON_COLOR: Record<string, string> = {
  primary: '#FFFFFF',
  ghost: '#5B5BD6',
  soft: '#5B5BD6',
  gold: '#7A4B00',
  text: '#5B5BD6',
}

const resolvedIconColor = computed(
  () => props.iconColor || DEFAULT_ICON_COLOR[props.type] || '#FFFFFF'
)

const iconSize = computed(() => (props.size === 'sm' ? 28 : props.size === 'md' ? 32 : 36))

function onClick(): void {
  if (props.disabled || props.loading) return
  emit('tap')
}
</script>

<template>
  <view :class="classes" @tap="onClick">
    <view v-if="loading" class="app-btn__spinner" />
    <AppIcon
      v-else-if="icon"
      :name="icon"
      :size="iconSize"
      :color="resolvedIconColor"
      class="app-btn__icon"
    />
    <text class="app-btn__text">
      <slot>{{ loading ? '处理中...' : '' }}</slot>
    </text>
  </view>
</template>

<style lang="scss" scoped>
.app-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  transition: opacity 0.15s ease, transform 0.15s ease;

  &.is-pill {
    border-radius: $radius-pill;
  }

  &.is-block {
    display: flex;
    width: 100%;
  }

  &:active {
    opacity: 0.82;
  }

  &.is-disabled {
    opacity: 0.5;
  }
}

/* ---------- 尺寸 ---------- */
.is-sm {
  height: 56rpx;
  padding: 0 28rpx;
}

.is-md {
  height: 72rpx;
  padding: 0 40rpx;
}

.is-lg {
  height: 88rpx;
  padding: 0 56rpx;
}

/* ---------- 类型 ---------- */
.is-primary {
  background: $brand-primary;

  .app-btn__text {
    color: #ffffff;
  }
}

.is-ghost {
  background: transparent;
  border: 2rpx solid $brand-primary;

  .app-btn__text {
    color: $brand-primary;
  }
}

.is-soft {
  background: $brand-primary-soft;

  .app-btn__text {
    color: $brand-primary;
  }
}

.is-gold {
  background: linear-gradient(135deg, #ffe08a 0%, #f7c33c 100%);

  .app-btn__text {
    color: #7a4b00;
    font-weight: 600;
  }
}

.is-text {
  background: transparent;
  height: auto;
  padding: 0;

  .app-btn__text {
    color: $brand-primary;
  }
}

/* ---------- 文案 ---------- */
.is-sm .app-btn__text {
  font-size: $fs-base;
}

.is-md .app-btn__text {
  font-size: $fs-md;
}

.is-lg .app-btn__text {
  font-size: $fs-lg;
  font-weight: 600;
}

.app-btn__text {
  line-height: 1;
}

.app-btn__spinner {
  width: 26rpx;
  height: 26rpx;
  margin-right: 12rpx;
  border: 3rpx solid rgba(255, 255, 255, 0.4);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: btn-spin 0.7s linear infinite;
}

.app-btn__icon {
  margin-right: 8rpx;
}

@keyframes btn-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
