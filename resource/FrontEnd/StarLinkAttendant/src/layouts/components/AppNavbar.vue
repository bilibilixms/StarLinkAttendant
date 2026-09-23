<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import BreadcrumbNav from './BreadcrumbNav.vue'
import { SwitchButton } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await userStore.logout()
    router.push('/login')
  } catch {
    // cancelled
  }
}
</script>

<template>
  <header class="navbar">
    <div class="navbar-left">
      <BreadcrumbNav />
    </div>
    <div class="navbar-right">
      <div class="user-info">
        <span class="avatar">{{ userStore.realName?.charAt(0) || userStore.username?.charAt(0) || 'U' }}</span>
        <span class="username">{{ userStore.realName || userStore.username || '用户' }}</span>
        <el-tag size="small" type="info" class="role-tag">{{ userStore.position || '管理员' }}</el-tag>
      </div>
      <el-button :icon="SwitchButton" circle size="small" @click="handleLogout" title="退出登录" />
    </div>
  </header>
</template>

<style scoped>
.navbar {
  height: 60px;
  background-color: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 50;
}

.navbar-left {
  flex: 1;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border-radius: 8px;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.username {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.role-tag {
  margin-left: 4px;
}
</style>
