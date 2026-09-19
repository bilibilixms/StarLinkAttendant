<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useMarketingStore } from '../stores'
import { COUPON_TYPE_MAP, COUPON_STATUS_MAP } from '../types'
import { getMemberList } from '@/modules/member/api'
import type { MemberItem } from '@/modules/member/types'

const store = useMarketingStore()
const activeTab = ref<'templates' | 'coupons'>('templates')
const loading = ref(false)

const searchForm = ref({ name: '', type: undefined as number | undefined, status: undefined as number | undefined, memberName: '', templateId: undefined as number | undefined })

const showModal = ref(false)
const isEdit = ref(false)
const formData = ref({
  id: 0, templateName: '', couponType: 1, faceValue: 0, minConsume: 0, discountRate: undefined as number | undefined,
  validDays: undefined as number | undefined, validStart: '', validEnd: '', totalQuantity: 0, memberLevelLimit: undefined as number | undefined, isActive: 1
})
const formRules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }, { max: 64, message: '不超过64字符', trigger: 'blur' }],
  faceValue: [{ required: true, message: '请输入面值', trigger: 'blur' }, { type: 'number', min: 0, message: '面值不能为负', trigger: 'blur' }]
}

// Issue coupon modal
const showIssueModal = ref(false)
const issueTemplateId = ref(0)
const memberList = ref<MemberItem[]>([])
const memberTotal = ref(0)
const memberPage = ref(1)
const memberPageSize = ref(10)
const memberSearch = ref('')
const selectedMembers = ref<MemberItem[]>([])
const memberLoading = ref(false)

const validMode = computed({
  get: () => formData.value.validDays != null ? 'days' : 'range',
  set: (val: string) => {
    if (val === 'days') {
      formData.value.validDays = formData.value.validDays || 7
      formData.value.validStart = ''
      formData.value.validEnd = ''
    } else {
      formData.value.validDays = undefined
    }
  }
})

const openAddModal = () => {
  isEdit.value = false
  formData.value = { id: 0, templateName: '', couponType: 1, faceValue: 0, minConsume: 0, discountRate: undefined, validDays: undefined, validStart: '', validEnd: '', totalQuantity: 0, memberLevelLimit: undefined, isActive: 1 }
  showModal.value = true
}

const openEditModal = (row: any) => {
  isEdit.value = true
  formData.value = { ...row }
  showModal.value = true
}

const handleSave = async () => {
  if (!formData.value.templateName.trim()) { ElMessage.warning('请输入模板名称'); return }
  loading.value = true
  try {
    if (isEdit.value) {
      await store.updateCouponTemplate(formData.value.id, formData.value as any)
    } else {
      await store.createCouponTemplate(formData.value as any)
    }
    showModal.value = false
    ElMessage.success(isEdit.value ? '模板已更新' : '模板已创建')
    await loadTemplates()
  } finally { loading.value = false }
}

const handleDelete = async (id: number) => {
  loading.value = true
  try {
    await store.deleteCouponTemplate(id)
    ElMessage.success('模板已删除')
    await loadTemplates()
  } finally { loading.value = false }
}

const openIssueModal = (templateId: number) => {
  issueTemplateId.value = templateId
  memberSearch.value = ''
  memberPage.value = 1
  selectedMembers.value = []
  showIssueModal.value = true
  fetchMembers()
}

const fetchMembers = async () => {
  memberLoading.value = true
  try {
    const res = await getMemberList({
      page: memberPage.value,
      size: memberPageSize.value,
      realName: memberSearch.value || undefined,
    })
    memberList.value = res.data.records
    memberTotal.value = res.data.total
  } finally { memberLoading.value = false }
}

const handleSearchMember = () => {
  memberPage.value = 1
  fetchMembers()
}

const handleMemberPageChange = (page: number) => {
  memberPage.value = page
  fetchMembers()
}

const handleSelectionChange = (selection: MemberItem[]) => {
  selectedMembers.value = selection
}

const handleIssue = async () => {
  if (selectedMembers.value.length === 0) { ElMessage.warning('请选择目标会员'); return }
  loading.value = true
  try {
    let successCount = 0
    for (const member of selectedMembers.value) {
      try {
        await store.issueCoupons(issueTemplateId.value, { memberId: member.id, quantity: 1 })
        successCount++
      } catch { /* skip failed, continue others */ }
    }
    showIssueModal.value = false
    ElMessage.success(`已向 ${successCount} 名会员发放优惠券`)
    await loadCoupons()
  } finally { loading.value = false }
}

const loadTemplates = async () => {
  await store.fetchCouponTemplates({ templateName: searchForm.value.name || undefined, couponType: searchForm.value.type, isActive: searchForm.value.status })
}

const loadCoupons = async () => {
  await store.fetchCoupons({ memberName: searchForm.value.memberName || undefined, templateId: searchForm.value.templateId, status: searchForm.value.status })
}

const handleTabChange = (tab: string) => {
  if (tab === 'templates') loadTemplates()
  else if (tab === 'coupons') loadCoupons()
}

onMounted(() => { loadTemplates() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>优惠券管理</h2>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="优惠券模板" name="templates">
        <div class="tab-actions">
          <el-button type="primary" @click="openAddModal">创建模板</el-button>
          <el-form :inline="true" :model="searchForm" style="display:inline">
            <el-form-item><el-input v-model="searchForm.name" placeholder="搜索名称" clearable @change="loadTemplates" /></el-form-item>
            <el-form-item>
              <el-select v-model="searchForm.type" placeholder="券类型" clearable @change="loadTemplates" style="width:110px">
                <el-option v-for="(v, k) in COUPON_TYPE_MAP" :key="Number(k)" :label="v" :value="Number(k)" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>
        <el-table :data="store.couponTemplates" v-loading="store.loading" stripe>
          <el-table-column prop="templateName" label="模板名称" min-width="140" />
          <el-table-column label="类型" width="90"><template #default="{ row }">{{ COUPON_TYPE_MAP[row.couponType] }}</template></el-table-column>
          <el-table-column label="面值" width="90"><template #default="{ row }">¥{{ row.faceValue.toFixed(2) }}</template></el-table-column>
          <el-table-column label="最低消费" width="100"><template #default="{ row }">¥{{ (row.minConsume || 0).toFixed(2) }}</template></el-table-column>
          <el-table-column label="有效期" width="130">
            <template #default="{ row }">{{ row.validDays ? `${row.validDays}天` : row.validEnd || '--' }}</template>
          </el-table-column>
          <el-table-column label="发行量" width="80"><template #default="{ row }">{{ row.totalQuantity === 0 ? '不限' : row.totalQuantity }}</template></el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }"><el-tag :type="row.isActive ? 'success' : 'danger'" size="small">{{ row.isActive ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="openEditModal(row)">编辑</el-button>
              <el-button type="success" size="small" @click="openIssueModal(row.id)">发放</el-button>
              <el-button type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="优惠券列表" name="coupons">
        <el-form :inline="true" :model="searchForm" style="margin-bottom:12px">
          <el-form-item><el-input v-model="searchForm.memberName" placeholder="会员名称" clearable @change="loadCoupons" /></el-form-item>
          <el-form-item>
            <el-select v-model="searchForm.status" placeholder="状态" clearable @change="loadCoupons" style="width:100px">
              <el-option v-for="(v, k) in COUPON_STATUS_MAP" :key="Number(k)" :label="v" :value="Number(k)" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-table :data="store.coupons" v-loading="store.loading" stripe>
          <el-table-column prop="couponCode" label="券码" width="170" />
          <el-table-column prop="memberName" label="会员" width="80" />
          <el-table-column label="面值" width="80"><template #default="{ row }">¥{{ row.faceValue.toFixed(2) }}</template></el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }"><el-tag :type="row.status === 0 ? 'primary' : row.status === 1 ? 'success' : 'info'" size="small">{{ COUPON_STATUS_MAP[row.status] }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="usedAt" label="使用时间" width="170" />
          <el-table-column prop="expireAt" label="过期时间" width="170" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- Template Form Dialog -->
    <el-dialog v-model="showModal" :title="isEdit ? '编辑模板' : '创建模板'" width="550px">
      <el-form :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="formData.templateName" maxlength="64" />
        </el-form-item>
        <el-form-item label="券类型" prop="couponType">
          <el-select v-model="formData.couponType" style="width:100%">
            <el-option v-for="(v, k) in COUPON_TYPE_MAP" :key="Number(k)" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="面值" prop="faceValue">
          <el-input-number v-model="formData.faceValue" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="最低消费">
          <el-input-number v-model="formData.minConsume" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item v-if="formData.couponType === 2" label="折扣率(%)">
          <el-input-number v-model="formData.discountRate" :min="1" :max="99" style="width:100%" />
        </el-form-item>
        <el-form-item label="有效期方式">
          <el-radio-group v-model="validMode" style="margin-bottom:8px">
            <el-radio value="days">按天数</el-radio>
            <el-radio value="range">按日期范围</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="validMode === 'days'" label="有效天数">
          <el-input-number v-model="formData.validDays" :min="1" style="width:100%" />
        </el-form-item>
        <template v-else>
          <el-form-item label="生效日期">
            <el-date-picker v-model="formData.validStart" type="date" style="width:100%" />
          </el-form-item>
          <el-form-item label="失效日期">
            <el-date-picker v-model="formData.validEnd" type="date" style="width:100%" />
          </el-form-item>
        </template>
        <el-form-item label="发行总量">
          <el-input-number v-model="formData.totalQuantity" :min="0" style="width:100%" /><span style="color:#999;margin-left:8px">0=不限量</span>
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="formData.isActive" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showModal = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- Issue Dialog -->
    <el-dialog v-model="showIssueModal" title="发放优惠券 - 选择会员" width="700px">
      <div style="margin-bottom:12px;display:flex;gap:8px">
        <el-input v-model="memberSearch" placeholder="搜索会员姓名" clearable style="width:200px" @keyup.enter="handleSearchMember" />
        <el-button type="primary" @click="handleSearchMember">搜索</el-button>
      </div>
      <el-table
        :data="memberList"
        v-loading="memberLoading"
        stripe
        max-height="400"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="levelName" label="等级" width="90" />
        <el-table-column label="余额" width="100">
          <template #default="{ row }">¥{{ row.balance.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="statusLabel" label="状态" width="80" />
      </el-table>
      <div style="display:flex;justify-content:center;margin-top:12px">
        <el-pagination
          v-model:current-page="memberPage"
          :page-size="memberPageSize"
          :total="memberTotal"
          layout="prev, pager, next"
          @current-change="handleMemberPageChange"
        />
      </div>
      <template #footer>
        <el-button @click="showIssueModal = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleIssue">
          确认发放 ({{ selectedMembers.length }}人)
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container { padding: 20px; }
.page-header { margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.tab-actions { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
</style>
