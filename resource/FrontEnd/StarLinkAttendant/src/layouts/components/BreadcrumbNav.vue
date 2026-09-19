<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()
</script>

<template>
  <div class="breadcrumb">
    <template v-if="route.path === '/dashboard'">
      <span class="breadcrumb-item active">首页</span>
    </template>
    <template v-else>
      <span class="breadcrumb-item" @click="router.push('/dashboard')">首页</span>
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="breadcrumb-divider">
        <polyline points="9 18 15 12 9 6"></polyline>
      </svg>
      <span class="breadcrumb-item active">{{ route.meta.title || '页面' }}</span>
    </template>
  </div>
</template>

<style scoped>
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
}

.breadcrumb-item {
  font-size: 14px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s ease;
}

.breadcrumb-item:hover {
  color: #3b82f6;
}

.breadcrumb-item.active {
  color: #1e293b;
  font-weight: 600;
  cursor: default;
}

.breadcrumb-divider {
  color: #cbd5e1;
}
</style>
