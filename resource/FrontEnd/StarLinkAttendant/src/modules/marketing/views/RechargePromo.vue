<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMarketingStore } from '../stores'

const store = useMarketingStore()
const loading = ref(false)

const showModal = ref(false)
const isEdit = ref(false)
const formData = ref({
  id: 0, campaignName: '', startTime: '', endTime: '',
  budget: undefined as number | undefined, usageLimit: undefined as number | undefined, memberLimit: 1,
  tiers: [{ rechargeAmount: 0, bonusAmount: 0 }] as { rechargeAmount: number; bonusAmount: number }[]
})

const openAddModal = () => {
  isEdit.value = false
  formData.value = { id: 0, campaignName: '', startTime: '', endTime: '', budget: undefined, usageLimit: undefined, memberLimit: 1, tiers: [{ rechargeAmount: 0, bonusAmount: 0 }] }
  showModal.value = true
}

const openEditModal = (row: any) => {
  isEdit.value = true
  let tiers: { rechargeAmount: number; bonusAmount: number }[] = [{ rechargeAmount: 0, bonusAmount: 0 }]
  try {
    const rules = typeof row.rules === 'string' ? JSON.parse(row.rules) : row.rules
    if (rules?.tiers?.length) tiers = rules.tiers
  } catch { /* use default */ }
  formData.value = { id: row.id, campaignName: row.campaignName, startTime: row.startTime, endTime: row.endTime, budget: row.budget, usageLimit: row.usageLimit, memberLimit: row.memberLimit || 1, tiers }
  showModal.value = true
}

const addTier = () => {
  formData.value.tiers.push({ rechargeAmount: 0, bonusAmount: 0 })
}

const removeTier = (index: number) => {
  if (formData.value.tiers.length > 1) formData.value.tiers.splice(index, 1)
}

const handleSave = async () => {
  if (!formData.value.campaignName.trim()) { ElMessage.warning('请输入活动名称'); return }
  if (!formData.value.startTime || !formData.value.endTime) { ElMessage.warning('请选择活动时间'); return }
  loading.value = true
  try {
    const d = formData.value
    const rules = JSON.stringify({ tiers: d.tiers.filter(t => t.rechargeAmount > 0) })
    const payload = {
      campaignName: d.campaignName, campaignType: 1,
      startTime: d.startTime, endTime: d.endTime,
      rules, budget: d.budget, usageLimit: d.usageLimit, memberLimit: d.memberLimit
    }
    if (isEdit.value) {
      await store.updateCampaign(d.id, payload as any)
    } else {
      await store.createCampaign(payload as any)
    }
    showModal.value = false
    ElMessage.success(isEdit.value ? '活动已更新' : '活动已创建')
    await loadData()
  } finally { loading.value = false }
}

const handlePublish = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认发布该充值活动？', '发布确认', { type: 'warning' })
    loading.value = true
    await store.updateCampaignStatus(id, 1)
    ElMessage.success('已发布')
    await loadData()
  } catch { /* cancelled */ }
  finally { loading.value = false }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认删除该已下架的充值活动？删除后不可恢复。', '删除确认', { type: 'warning' })
    loading.value = true
    await store.deleteRechargePromo(id)
    ElMessage.success('已删除')
    await loadData()
  } catch { /* cancelled */ }
  finally { loading.value = false }
}

const handleRemove = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认下架该充值活动？下架后无法恢复使用。', '下架确认', { type: 'warning' })
    loading.value = true
    await store.updateCampaignStatus(id, 4)
    ElMessage.success('已下架')
    await loadData()
  } catch { /* cancelled */ }
  finally { loading.value = false }
}

const formatStatus = (status: number) => {
  const map: Record<number, { text: string; type: string }> = {
    0: { text: '草稿', type: 'info' }, 1: { text: '已发布', type: 'primary' },
    2: { text: '已生效', type: 'success' }, 3: { text: '已结束', type: 'default' }, 4: { text: '已下架', type: 'danger' }
  }
  return map[status] || { text: '未知', type: 'info' }
}

const parseTiers = (rules: string) => {
  try { return JSON.parse(rules)?.tiers || [] } catch { return [] }
}

const loadData = async () => { await store.fetchRechargePromos() }

onMounted(() => { loadData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>充值活动</h2>
      <el-button type="primary" @click="openAddModal">创建充值活动</el-button>
    </div>

    <el-card>
      <el-table :data="store.rechargePromos" v-loading="store.loading" stripe>
        <el-table-column prop="campaignName" label="活动名称" min-width="150" />
        <el-table-column label="充值阶梯" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="t in parseTiers(row.rules)" :key="t.rechargeAmount" size="small" style="margin:2px">
              充{{ t.rechargeAmount }}送{{ t.bonusAmount }}
            </el-tag>
            <span v-if="parseTiers(row.rules).length === 0" style="color:#999">未配置</span>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column prop="endTime" label="结束时间" width="170" />
        <el-table-column label="预算" width="100">
          <template #default="{ row }">¥{{ row.budget || 0 }}</template>
        </el-table-column>
        <el-table-column label="已用/上限" width="100">
          <template #default="{ row }">{{ row.usedCount || 0 }} / {{ row.usageLimit || '不限' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="formatStatus(row.status).type" size="small">{{ formatStatus(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" type="primary" size="small" @click="handlePublish(row.id)">发布</el-button>
            <el-button v-if="row.status === 0" type="warning" size="small" @click="openEditModal(row)">编辑</el-button>
            <el-button v-if="row.status === 1 || row.status === 2" type="warning" size="small" @click="handleRemove(row.id)">下架</el-button>
            <el-button v-if="row.status === 4" type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
            <el-tag v-if="row.status === 3" type="info" size="small">已结束</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showModal" :title="isEdit ? '编辑充值活动' : '创建充值活动'" width="600px">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="活动名称" required>
          <el-input v-model="formData.campaignName" maxlength="100" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="formData.startTime" type="datetime" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="formData.endTime" type="datetime" style="width:100%" />
        </el-form-item>
        <el-form-item label="充值阶梯">
          <div style="width:100%">
            <div v-for="(tier, idx) in formData.tiers" :key="idx" style="display:flex;gap:8px;margin-bottom:8px;align-items:center">
              <span style="white-space:nowrap">充值</span>
              <el-input-number v-model="tier.rechargeAmount" :min="0" :precision="0" size="small" style="width:100px" />
              <span style="white-space:nowrap">赠送</span>
              <el-input-number v-model="tier.bonusAmount" :min="0" :precision="0" size="small" style="width:100px" />
              <el-button v-if="formData.tiers.length > 1" type="danger" size="small" :icon="'Delete'" circle @click="removeTier(idx)" />
            </div>
            <el-button size="small" @click="addTier">+ 添加阶梯</el-button>
          </div>
        </el-form-item>
        <el-form-item label="活动预算">
          <el-input-number v-model="formData.budget" :min="0" :precision="2" style="width:100%" placeholder="可选" />
        </el-form-item>
        <el-form-item label="次数上限">
          <el-input-number v-model="formData.usageLimit" :min="0" style="width:100%" placeholder="0=不限" />
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
</style>
