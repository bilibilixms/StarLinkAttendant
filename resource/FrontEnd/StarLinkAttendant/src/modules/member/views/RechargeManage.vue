<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, RefreshRight } from '@element-plus/icons-vue'
import { getMemberList, createRecharge, getRechargeRecords } from '../api'
import { PAYMENT_METHOD_MAP, RECHARGE_METHOD_MAP, RECHARGE_STATUS_MAP } from '@/common/constants'
import { formatMoney } from '@/common/utils/money'
import { formatDate } from '@/common/utils/date'
import type { MemberItem, RechargeRecordItem, RechargeRequest } from '../types'

const rechargeDialogVisible = ref(false)
const formRef = ref<FormInstance>()
const loading = ref(false)

const rechargeForm = reactive<RechargeRequest>({
  memberId: 0,
  amount: 0,
  paymentMethod: 1,
})

const rechargeRules: FormRules = {
  memberId: [{ required: true, message: '请选择会员', trigger: 'change' }],
  amount: [{ required: true, message: '请输入充值金额', trigger: 'blur' }],
  paymentMethod: [{ required: true, message: '请选择支付方式', trigger: 'change' }],
}

// Member search
const memberOptions = ref<MemberItem[]>([])
const memberLoading = ref(false)
const searchMember = async (query: string) => {
  if (!query) { memberOptions.value = []; return }
  memberLoading.value = true
  try {
    const res = await getMemberList({ page: 1, size: 10, realName: query })
    memberOptions.value = res.data.records || []
  } catch { memberOptions.value = [] }
  finally { memberLoading.value = false }
}

// Records
const records = ref<RechargeRecordItem[]>([])
const recordsTotal = ref(0)
const recordsLoading = ref(false)
const recordQuery = reactive({ memberId: 0, page: 1, size: 10 })
const selectedMemberName = ref('')

const fetchRecords = async () => {
  if (!recordQuery.memberId) return
  recordsLoading.value = true
  try {
    const res = await getRechargeRecords(recordQuery.memberId, { page: recordQuery.page, size: recordQuery.size })
    records.value = res.data.records || []
    recordsTotal.value = res.data.total || 0
  } catch { records.value = [] }
  finally { recordsLoading.value = false }
}

const handleMemberSelect = (member: MemberItem) => {
  recordQuery.memberId = member.id
  selectedMemberName.value = member.realName || member.memberNo
  rechargeForm.memberId = member.id
  recordQuery.page = 1
  fetchRecords()
}

const openRechargeDialog = () => {
  if (!recordQuery.memberId) {
    ElMessage.warning('请先选择会员')
    return
  }
  rechargeForm.memberId = recordQuery.memberId
  rechargeForm.amount = 0
  rechargeForm.paymentMethod = 1
  rechargeDialogVisible.value = true
}

const handleRechargeSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await createRecharge(rechargeForm)
      ElMessage.success('充值成功')
      rechargeDialogVisible.value = false
      fetchRecords()
    } catch { /* interceptor */ }
    finally { loading.value = false }
  })
}

const handleRecordPageChange = (page: number) => {
  recordQuery.page = page
  fetchRecords()
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">充值管理</h1>
      <p class="page-desc">为会员充值并查看充值记录</p>
    </div>

    <!-- 会员选择 -->
    <div class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="选择会员">
          <el-select
            v-model="selectedMemberName"
            filterable
            remote
            reserve-keyword
            :remote-method="searchMember"
            :loading="memberLoading"
            placeholder="输入姓名搜索会员"
            style="width: 280px"
            @change="(val: string) => {
              const m = memberOptions.find(o => (o.realName || o.memberNo) === val)
              if (m) handleMemberSelect(m)
            }"
          >
            <el-option
              v-for="member in memberOptions"
              :key="member.id"
              :label="member.realName || member.memberNo"
              :value="member.realName || member.memberNo"
            >
              <span>{{ member.realName || member.memberNo }}</span>
              <span style="color: #94a3b8; font-size: 12px; margin-left: 8px;">{{ member.phone }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="recordQuery.memberId">
          <el-button type="primary" :icon="Search" @click="openRechargeDialog">充值</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 充值记录 -->
    <div class="table-card" v-if="recordQuery.memberId">
      <div class="table-toolbar">
        <h3 style="margin: 0; font-size: 16px; color: #1e293b;">充值记录 - {{ selectedMemberName }}</h3>
      </div>

      <el-table :data="records" v-loading="recordsLoading" stripe style="width: 100%">
        <el-table-column prop="rechargeNo" label="充值单号" width="180" />
        <el-table-column label="充值金额" width="120">
          <template #default="{ row }">¥{{ formatMoney(row.rechargeAmount) }}</template>
        </el-table-column>
        <el-table-column label="赠送金额" width="120">
          <template #default="{ row }">¥{{ formatMoney(row.bonusAmount) }}</template>
        </el-table-column>
        <el-table-column label="到账金额" width="120">
          <template #default="{ row }">¥{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="充值前余额" width="120">
          <template #default="{ row }">¥{{ formatMoney(row.balanceBefore) }}</template>
        </el-table-column>
        <el-table-column label="充值后余额" width="120">
          <template #default="{ row }">¥{{ formatMoney(row.balanceAfter) }}</template>
        </el-table-column>
        <el-table-column label="支付方式" width="100">
          <template #default="{ row }">{{ PAYMENT_METHOD_MAP[row.paymentMethod] || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ RECHARGE_STATUS_MAP[row.status] || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="充值时间" width="170">
          <template #default="{ row }">{{ formatDate(row.paidAt || row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="recordQuery.page"
          :total="recordsTotal"
          :page-size="recordQuery.size"
          layout="total, prev, pager, next"
          background
          @update:current-page="handleRecordPageChange"
        />
      </div>
    </div>

    <div class="empty-card" v-else>
      <p style="color: #94a3b8; font-size: 15px;">请先选择一个会员以查看充值记录</p>
    </div>

    <!-- 充值对话框 -->
    <el-dialog v-model="rechargeDialogVisible" title="会员充值" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="rechargeForm" :rules="rechargeRules" label-width="100px">
        <el-form-item label="充值金额" prop="amount">
          <el-input-number v-model="rechargeForm.amount" :min="1" :max="99999" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="支付方式" prop="paymentMethod">
          <el-radio-group v-model="rechargeForm.paymentMethod">
            <el-radio v-for="(label, val) in RECHARGE_METHOD_MAP" :key="val" :value="Number(val)">{{ label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleRechargeSubmit">确认充值</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.page-desc { font-size: 14px; color: #64748b; margin-top: 4px; }
.search-card { background: white; border-radius: 12px; padding: 20px 20px 4px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.table-card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.table-toolbar { margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }
.empty-card { background: white; border-radius: 12px; padding: 64px 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); text-align: center; }
</style>
