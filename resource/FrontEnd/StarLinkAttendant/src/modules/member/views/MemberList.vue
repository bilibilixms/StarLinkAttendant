<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, View, Warning, RefreshRight } from '@element-plus/icons-vue'
import { getMemberList, deleteMember, addToBlacklist, removeFromBlacklist, getLevelList } from '../api'
import { MEMBER_STATUS_MAP, GENDER_MAP } from '@/common/constants'
import { formatMoney } from '@/common/utils/money'
import { formatDate } from '@/common/utils/date'
import type { MemberItem, MemberQuery } from '../types'
import type { LevelItem } from '../types'

const router = useRouter()
const loading = ref(false)
const tableData = ref<MemberItem[]>([])
const total = ref(0)
const levelOptions = ref<LevelItem[]>([])
const queryParams = reactive<{ page: number; size: number } & MemberQuery>({
  page: 1, size: 10, memberNo: '', realName: '', phone: '', status: null, levelId: null,
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getMemberList(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { tableData.value = [] }
  finally { loading.value = false }
}

const fetchLevels = async () => {
  try {
    const res = await getLevelList()
    levelOptions.value = res.data || []
  } catch { levelOptions.value = [] }
}

const handleSearch = () => { queryParams.page = 1; fetchData() }
const handleReset = () => {
  queryParams.memberNo = ''; queryParams.realName = ''; queryParams.phone = ''; queryParams.status = null; queryParams.levelId = null
  handleSearch()
}
const handlePageChange = (page: number) => { queryParams.page = page; fetchData() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchData() }

const viewDetail = (row: MemberItem) => { router.push(`/member/detail/${row.id}`) }

const handleAddBlacklist = async (row: MemberItem) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入加入黑名单的原因', '加入黑名单', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入原因',
    })
    await addToBlacklist(row.id, value)
    ElMessage.success('已加入黑名单')
    fetchData()
  } catch { /* cancelled */ }
}

const handleRemoveBlacklist = async (row: MemberItem) => {
  try {
    await ElMessageBox.confirm(`确定要将「${row.realName || row.memberNo}」移出黑名单吗？`, '确认移出', { type: 'warning' })
    await removeFromBlacklist(row.id)
    ElMessage.success('已移出黑名单')
    fetchData()
  } catch { /* cancelled */ }
}

const handleDelete = async (row: MemberItem) => {
  try {
    await ElMessageBox.confirm(`确定要删除会员「${row.realName || row.memberNo}」吗？`, '确认删除', { type: 'warning' })
    await deleteMember(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* cancelled */ }
}

onMounted(() => { fetchData(); fetchLevels() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">会员列表</h1>
      <p class="page-desc">查看和管理所有会员信息</p>
    </div>

    <div class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="会员卡号">
          <el-input v-model="queryParams.memberNo" placeholder="请输入卡号" clearable />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="queryParams.realName" placeholder="请输入姓名" clearable />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="queryParams.phone" placeholder="请输入手机号" clearable />
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="queryParams.levelId" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="level in levelOptions" :key="level.id" :label="level.levelName" :value="level.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100px">
            <el-option v-for="(label, val) in MEMBER_STATUS_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="router.push('/member/register')">新增会员</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="memberNo" label="会员卡号" width="130" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column label="性别" width="60">
          <template #default="{ row }">{{ GENDER_MAP[row.gender] || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="levelName" label="等级" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ row.levelName || '普通' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="余额" width="100">
          <template #default="{ row }">¥{{ formatMoney(row.balance) }}</template>
        </el-table-column>
        <el-table-column label="积分" width="80">
          <template #default="{ row }">{{ row.availablePoints }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'warning' : 'danger'" size="small">{{ MEMBER_STATUS_MAP[row.status] || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :icon="View" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.status === 3" type="success" link size="small" :icon="RefreshRight" @click="handleRemoveBlacklist(row)">移出黑名单</el-button>
            <el-button v-else type="warning" link size="small" :icon="Warning" @click="handleAddBlacklist(row)">黑名单</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
      </div>
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
.table-toolbar { margin-bottom: 16px; display: flex; justify-content: flex-end; }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }
</style>
