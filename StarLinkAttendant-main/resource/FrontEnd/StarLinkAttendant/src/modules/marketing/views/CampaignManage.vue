<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMarketingStore } from '../stores'
import { CAMPAIGN_TYPE_MAP, CAMPAIGN_STATUS_MAP } from '../types'

const store = useMarketingStore()
const loading = ref(false)
const searchForm = ref({ name: '', type: undefined as number | undefined, status: undefined as number | undefined })

const showModal = ref(false)
const isEdit = ref(false)
const formData = ref({
  id: 0, campaignName: '', campaignType: 1,
  startTime: '', endTime: '', rules: '{}',
  budget: undefined as number | undefined, usageLimit: undefined as number | undefined, memberLimit: 1
})
const formRules = {
  campaignName: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  campaignType: [{ required: true, message: '请选择活动类型', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

const openAddModal = () => {
  isEdit.value = false
  formData.value = { id: 0, campaignName: '', campaignType: 1, startTime: '', endTime: '', rules: '{}', budget: undefined, usageLimit: undefined, memberLimit: 1 }
  showModal.value = true
}

const openEditModal = (row: any) => {
  isEdit.value = true
  formData.value = { ...row }
  showModal.value = true
}

const handleSave = async () => {
  if (!formData.value.campaignName.trim()) { ElMessage.warning('请输入活动名称'); return }
  if (!formData.value.startTime || !formData.value.endTime) { ElMessage.warning('请选择活动时间'); return }
  if (new Date(formData.value.endTime) <= new Date(formData.value.startTime)) { ElMessage.warning('结束时间必须晚于开始时间'); return }
  loading.value = true
  try {
    if (isEdit.value) {
      store.updateCampaign(formData.value.id, formData.value)
    } else {
      store.createCampaign(formData.value)
    }
    showModal.value = false
    ElMessage.success(isEdit.value ? '活动已更新' : '活动已创建')
    await loadData()
  } finally { loading.value = false }
}

const handlePublish = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认发布该活动？发布后将无法修改。', '发布确认', { type: 'warning' })
    loading.value = true
    await store.updateCampaignStatus(id, 1)
    ElMessage.success('活动已发布')
    await loadData()
  } catch { /* cancelled */ }
  finally { loading.value = false }
}

const handleEnd = async (row: any) => {
  try {
    const isOffline = await ElMessageBox.confirm('选择操作：确定=手动下架，取消=自然结束', '结束活动', {
      confirmButtonText: '手动下架', cancelButtonText: '自然结束', distinguishCancelAndClose: true, type: 'warning'
    }).then(() => 'offline').catch(() => 'end')
    loading.value = true
    const newStatus = isOffline === 'offline' ? 4 : 3
    await store.updateCampaignStatus(row.id, newStatus)
    ElMessage.success(isOffline === 'offline' ? '活动已下架' : '活动已结束')
    await loadData()
  } catch { /* cancelled */ }
  finally { loading.value = false }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认删除该已结束的活动？删除后不可恢复。', '删除确认', { type: 'warning' })
    loading.value = true
    await store.deleteCampaign(id)
    ElMessage.success('已删除')
    await loadData()
  } catch { /* cancelled */ }
  finally { loading.value = false }
}

const formatStatus = (status: number) => {
  const map: Record<number, { text: string; type: string }> = {
    0: { text: '草稿', type: 'info' },
    1: { text: '已发布', type: 'primary' },
    2: { text: '已生效', type: 'success' },
    3: { text: '已结束', type: 'default' },
    4: { text: '已下架', type: 'danger' }
  }
  return map[status] || { text: '未知', type: 'info' }
}

const loadData = async () => {
  await store.fetchCampaigns({
    campaignName: searchForm.value.name || undefined,
    campaignType: searchForm.value.type,
    status: searchForm.value.status
  })
}

onMounted(() => { loadData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>限时活动</h2>
      <el-button type="primary" @click="openAddModal">创建活动</el-button>
    </div>

    <el-card class="filter-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="活动名称">
          <el-input v-model="searchForm.name" placeholder="搜索名称" clearable />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="searchForm.type" placeholder="全部" clearable style="width:130px">
            <el-option v-for="(v, k) in CAMPAIGN_TYPE_MAP" :key="Number(k)" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width:120px">
            <el-option v-for="(v, k) in CAMPAIGN_STATUS_MAP" :key="Number(k)" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="searchForm = { name: '', type: undefined, status: undefined }; loadData()">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="overflow-x: auto">
      <el-table :data="store.campaigns" v-loading="store.loading" stripe>
        <el-table-column prop="campaignNo" label="活动编号" width="150" />
        <el-table-column prop="campaignName" label="活动名称" min-width="140" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ CAMPAIGN_TYPE_MAP[row.campaignType] }}</template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="endTime" label="结束时间" width="160" />
        <el-table-column label="预算/已用" width="120">
          <template #default="{ row }">¥{{ row.usedBudget || 0 }} / ¥{{ row.budget || 0 }}</template>
        </el-table-column>
        <el-table-column label="已用/上限" width="90">
          <template #default="{ row }">{{ row.usedCount || 0 }} / {{ row.usageLimit || '不限' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="formatStatus(row.status).type" size="small">{{ formatStatus(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" type="primary" size="small" @click="handlePublish(row.id)">发布</el-button>
            <el-button v-if="row.status === 0" type="warning" size="small" @click="openEditModal(row)">编辑</el-button>
            <el-button v-if="row.status === 1 || row.status === 2" type="danger" size="small" @click="handleEnd(row)">结束</el-button>
            <el-button v-if="row.status === 3 || row.status === 4" type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showModal" :title="isEdit ? '编辑活动' : '创建活动'" width="600px">
      <el-form :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="活动名称" prop="campaignName">
          <el-input v-model="formData.campaignName" maxlength="100" placeholder="请输入活动名称" />
        </el-form-item>
        <el-form-item label="活动类型" prop="campaignType">
          <el-select v-model="formData.campaignType" style="width:100%">
            <el-option v-for="(v, k) in CAMPAIGN_TYPE_MAP" :key="Number(k)" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker v-model="formData.startTime" type="datetime" placeholder="选择开始时间" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="formData.endTime" type="datetime" placeholder="选择结束时间" style="width:100%" />
        </el-form-item>
        <el-form-item label="活动规则">
          <el-input v-model="formData.rules" type="textarea" :rows="4" placeholder='JSON 格式，如 {"tiers":[...]} 或 {"discountRate":80}' />
        </el-form-item>
        <el-form-item label="活动预算">
          <el-input-number v-model="formData.budget" :min="0" :precision="2" style="width:100%" placeholder="可选" />
        </el-form-item>
        <el-form-item label="次数上限">
          <el-input-number v-model="formData.usageLimit" :min="0" style="width:100%" placeholder="可选，0=不限" />
        </el-form-item>
        <el-form-item label="每人限次数">
          <el-input-number v-model="formData.memberLimit" :min="1" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showModal = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.filter-card { margin-bottom: 16px; }
</style>
