<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import {
  HomeFilled, Monitor, User, UserFilled,
  ShoppingCart, Goods, DataAnalysis, Promotion, Setting,
  Management, Menu as MenuIcon, Document, Bell,
  Plus, Wallet, Trophy, Star, Warning, View, Cpu,
  Calendar, SwitchButton, ChatLineSquare
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()

const activeIndex = computed(() => route.path)

const handleMenuSelect = (index: string) => {
  router.push(index)
}
</script>

<template>
  <aside class="sidebar" :class="{ collapsed: appStore.collapsed }">
    <div class="logo-section">
      <div class="logo">
        <el-icon :size="20" color="#3b82f6"><Monitor /></el-icon>
        <span v-if="!appStore.collapsed" class="logo-text">星络灵侍馆</span>
      </div>
    </div>
    <el-scrollbar class="menu-scrollbar">
      <el-menu
        :default-active="activeIndex"
        :collapse="appStore.collapsed"
        :collapse-transition="true"
        background-color="transparent"
        text-color="#94a3b8"
        active-text-color="#ffffff"
        @select="handleMenuSelect"
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <template #title>首页</template>
        </el-menu-item>

        <el-sub-menu index="session-group">
          <template #title>
            <el-icon><Monitor /></el-icon>
            <span>上机管理</span>
          </template>
          <el-menu-item index="/session/seat-map">
            <el-icon><Monitor /></el-icon>
            <template #title>座位图</template>
          </el-menu-item>
          <el-menu-item index="/session/monitor">
            <el-icon><View /></el-icon>
            <template #title>会话监控</template>
          </el-menu-item>
          <el-menu-item index="/session/start">
            <el-icon><Cpu /></el-icon>
            <template #title>上机操作</template>
          </el-menu-item>
          <el-menu-item index="/session/transfer">
            <el-icon><SwitchButton /></el-icon>
            <template #title>换机操作</template>
          </el-menu-item>
          <el-menu-item index="/session/reservations">
            <el-icon><Calendar /></el-icon>
            <template #title>预约管理</template>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="member-group">
          <template #title>
            <el-icon><UserFilled /></el-icon>
            <span>会员管理</span>
          </template>
          <el-menu-item index="/member/list">
            <el-icon><User /></el-icon>
            <template #title>会员列表</template>
          </el-menu-item>
          <el-menu-item index="/member/register">
            <el-icon><Plus /></el-icon>
            <template #title>会员注册</template>
          </el-menu-item>
          <el-menu-item index="/member/recharge">
            <el-icon><Wallet /></el-icon>
            <template #title>充值管理</template>
          </el-menu-item>
          <el-menu-item index="/member/levels">
            <el-icon><Trophy /></el-icon>
            <template #title>等级规则</template>
          </el-menu-item>
          <el-menu-item index="/member/points">
            <el-icon><Star /></el-icon>
            <template #title>积分管理</template>
          </el-menu-item>
          <el-menu-item index="/member/blacklist">
            <el-icon><Warning /></el-icon>
            <template #title>黑名单管理</template>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="system-group">
          <template #title>
            <el-icon><Management /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/users">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
          <el-menu-item index="/system/menus">
            <el-icon><MenuIcon /></el-icon>
            <template #title>菜单管理</template>
          </el-menu-item>
          <el-menu-item index="/system/logs">
            <el-icon><Document /></el-icon>
            <template #title>操作日志</template>
          </el-menu-item>
          <el-menu-item index="/system/notices">
            <el-icon><Bell /></el-icon>
            <template #title>通知公告</template>
          </el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/cashier">
          <el-icon><Goods /></el-icon>
          <template #title>收银管理</template>
        </el-menu-item>

        <el-menu-item index="/product">
          <el-icon><ShoppingCart /></el-icon>
          <template #title>商品销售</template>
        </el-menu-item>

        <el-menu-item index="/report">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>经营报表</template>
        </el-menu-item>

        <el-menu-item index="/marketing">
          <el-icon><Promotion /></el-icon>
          <template #title>营销活动</template>
        </el-menu-item>

        <el-menu-item index="/integration">
          <el-icon><Setting /></el-icon>
          <template #title>第三方集成</template>
        </el-menu-item>

        <el-menu-item index="/ai">
          <el-icon><ChatLineSquare /></el-icon>
          <template #title>智能助手</template>
        </el-menu-item>
      </el-menu>
    </el-scrollbar>
    <div class="collapse-trigger" @click="appStore.toggleCollapse">
      <el-icon :size="16">
        <component :is="appStore.collapsed ? 'DArrowRight' : 'DArrowLeft'" />
      </el-icon>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 200px;
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
  color: #e2e8f0;
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  transition: width 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
  box-shadow: 4px 0 20px rgba(0, 0, 0, 0.1);
  z-index: 100;
  display: flex;
  flex-direction: column;
}

.sidebar.collapsed {
  width: 64px;
}

.logo-section {
  padding: 12px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 16px;
}

.logo-text {
  font-size: 15px;
  font-weight: 600;
  color: #f1f5f9;
  letter-spacing: 0.3px;
  white-space: nowrap;
  user-select: none;
}

.menu-scrollbar {
  flex: 1;
  overflow: hidden;
}

:deep(.el-menu) {
  border-right: none !important;
  padding: 8px 0;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 46px;
  line-height: 46px;
  margin: 2px 8px;
  border-radius: 8px;
  user-select: none;
  cursor: pointer;
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background-color: rgba(59, 130, 246, 0.12) !important;
  color: #bfdbfe !important;
}

:deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.3) 0%, rgba(30, 64, 175, 0.3) 100%) !important;
  color: #fff !important;
}

:deep(.el-sub-menu .el-menu-item) {
  margin: 2px 8px 2px 16px;
  min-width: auto;
  height: 40px;
  line-height: 40px;
  user-select: none;
  cursor: pointer;
}

:deep(.el-sub-menu.is-opened > .el-sub-menu__title) {
  color: #bfdbfe !important;
}

.collapse-trigger {
  padding: 12px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  cursor: pointer;
  color: #94a3b8;
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.collapse-trigger:hover {
  color: #fff;
  background-color: rgba(59, 130, 246, 0.15);
}
</style>
