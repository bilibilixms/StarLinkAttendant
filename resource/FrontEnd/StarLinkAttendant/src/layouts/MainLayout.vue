<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import AppSidebar from './components/AppSidebar.vue'
import AppNavbar from './components/AppNavbar.vue'

const appStore = useAppStore()
const marginLeft = computed(() => appStore.collapsed ? '64px' : '200px')
</script>

<template>
  <div class="app-layout">
    <AppSidebar />
    <div class="main-panel" :style="{ marginLeft }">
      <AppNavbar />
      <main class="content-wrapper">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  background-color: #f5f7fa;
}

.main-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  transition: margin-left 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.content-wrapper {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
