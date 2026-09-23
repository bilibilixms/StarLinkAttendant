<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshRight, Delete } from '@element-plus/icons-vue'
import { getBlacklist, removeFromBlacklist } from '../api'
import { formatDate } from '@/common/utils/date'
import type { BlacklistItem } from '../types'

const loading = ref(false)
const tableData = ref<BlacklistItem[]>([])
const searchKey = ref('')

const filteredData = computed(() => {
  const key = searchKey.value.trim().toLowerCase()
  if (!key) return tableData.value
  return tableData.value.filter(
    (item) =>
      item.memberNo?.toLowerCase().includes(key) ||
      item.realName?.toLowerCase().includes(key) ||
      item.phone?.includes(key) ||
      item.blacklistReason?.toLowerCase().includes(key),
  )
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getBlacklist()
    tableData.value = res.data || []
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  /* computed 自动响应 searchKey，无需额外操作 */
}

const handleReset = () => {
  searchKey.value = ''
}

const handleRemove = async (row: BlacklistItem) => {
  try {
    await ElMessageBox.confirm(
      `确定要将「${row.realName || row.memberNo}」移出黑名单吗？`,
      '确认移出',
      { type: 'warning' },
    )
    await removeFromBlacklist(row.id)
    ElMessage.success('已移出黑名单')
    fetchData()
  } catch {
    /* cancelled */
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">黑名单管理</h1>
      <p class="page-desc">查看和管理被列入黑名单的会员，黑名单会员将无法上机和消费</p>
    </div>

    <div class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="搜索">
          <el-input
            v-model="searchKey"
            placeholder="卡号 / 姓名 / 手机号 / 原因"
            clearable
            style="width: 300px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <el-alert
        title="黑名单说明"
        description="被列入黑名单的会员将无法正常上机和消费。可在会员列表中点击「黑名单」将会员加入。"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <div class="table-toolbar">
        <span class="total-text">共 <b>{{ tableData.length }}</b> 条黑名单记录<template v-if="searchKey && filteredData.length !== tableData.length">，当前筛选 <b>{{ filteredData.length }}</b> 条</template></span>
      </div>

      <el-table :data="filteredData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="memberNo" label="会员卡号" width="150" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="blacklistReason" label="拉黑原因" min-width="250" show-overflow-tooltip />
        <el-table-column label="加入时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link size="small" :icon="Delete" @click="handleRemove(row)">移出黑名单</el-button>
          </template>
        </el-table-column>
        <el-empty v-if="!loading && filteredData.length === 0" description="暂无黑名单记录" />
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.page-desc { font-size: 14px; color: #64748b; margin-top: 4px; }
.search-card { background: white; border-radius: 12px; padding: 20px 20px 4px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.table-card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.table-toolbar { margin-bottom: 12px; display: flex; align-items: center; }
.total-text { font-size: 14px; color: #64748b; }
.total-text b { color: #1e293b; }
</style>
