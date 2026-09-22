<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, RefreshRight, Delete } from '@element-plus/icons-vue'
import { getReservationList, createReservation, cancelReservation } from '../api'
import { RESERVATION_STATUS_MAP } from '@/common/constants'
import { formatDate } from '@/common/utils/date'
import { formatMoney } from '@/common/utils/money'
import type { ReservationItem, ReservationQuery, ReservationCreateRequest } from '../types'

const loading = ref(false)
const tableData = ref<ReservationItem[]>([])
const total = ref(0)
const queryParams = reactive<{ page: number; size: number } & ReservationQuery>({
  page: 1, size: 10, memberName: '', computerNo: '', status: null, reservationDate: '',
})

/** 新建预约弹窗 */
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createForm = reactive<{
  memberPhone: string
  memberId: number | null
  computerId: number | null
  reservationDate: string
  startTime: string
  endTime: string
  depositAmount: number
}>({
  memberPhone: '',
  memberId: null,
  computerId: null,
  reservationDate: '',
  startTime: '',
  endTime: '',
  depositAmount: 0,
})

const fetchData = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (!params.reservationDate) delete params.reservationDate
    const res = await getReservationList(params)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { tableData.value = [] }
  finally { loading.value = false }
}

const handleSearch = () => { queryParams.page = 1; fetchData() }
const handleReset = () => {
  queryParams.memberName = ''; queryParams.computerNo = ''; queryParams.status = null; queryParams.reservationDate = ''
  handleSearch()
}
const handlePageChange = (page: number) => { queryParams.page = page; fetchData() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchData() }

/** 搜索会员（用于创建预约） */
const memberSearchLoading = ref(false)
const memberFound = ref(false)
async function searchMemberForCreate() {
  if (!createForm.memberPhone || createForm.memberPhone.length < 11) {
    ElMessage.warning('请输入完整的手机号')
    return
  }
  memberSearchLoading.value = true
  try {
    const { getMemberList } = await import('@/modules/member/api')
    const res = await getMemberList({ page: 1, size: 1, phone: createForm.memberPhone })
    const records = res.data.records || []
    const first = records[0]
    if (first) {
      createForm.memberId = first.id
      memberFound.value = true
    } else {
      createForm.memberId = null
      memberFound.value = false
      ElMessage.warning('未找到该会员')
    }
  } catch { memberFound.value = false }
  finally { memberSearchLoading.value = false }
}

/** 打开创建弹窗 */
function openCreateDialog() {
  createForm.memberPhone = ''
  createForm.memberId = null
  createForm.computerId = null
  createForm.reservationDate = ''
  createForm.startTime = ''
  createForm.endTime = ''
  createForm.depositAmount = 0
  memberFound.value = false
  createDialogVisible.value = true
}

/** 提交创建预约 */
async function handleCreate() {
  if (!createForm.memberId) { ElMessage.warning('请先搜索会员'); return }
  if (!createForm.reservationDate) { ElMessage.warning('请选择预约日期'); return }
  if (!createForm.startTime) { ElMessage.warning('请选择开始时间'); return }
  if (!createForm.endTime) { ElMessage.warning('请选择结束时间'); return }

  createLoading.value = true
  try {
    await createReservation({
      memberId: createForm.memberId,
      computerId: createForm.computerId || undefined,
      reservationDate: createForm.reservationDate,
      startTime: `${createForm.reservationDate}T${createForm.startTime}:00`,
      endTime: `${createForm.reservationDate}T${createForm.endTime}:00`,
      depositAmount: createForm.depositAmount || undefined,
    })
    ElMessage.success('预约创建成功')
    createDialogVisible.value = false
    fetchData()
  } catch { /* handled by interceptor */ }
  finally { createLoading.value = false }
}

/** 取消预约 */
async function handleCancel(row: ReservationItem) {
  if (row.status === 3 || row.status === 4) {
    ElMessage.warning('该预约已取消或超时')
    return
  }
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入取消原因', '取消预约', {
      confirmButtonText: '确定',
      cancelButtonText: '返回',
      inputPlaceholder: '请输入取消原因',
    })
    await cancelReservation(row.id, reason)
    ElMessage.success('预约已取消')
    fetchData()
  } catch { /* cancelled */ }
}

/** 获取状态标签类型 */
function getStatusType(status: number) {
  switch (status) {
    case 0: return 'warning'
    case 1: return 'primary'
    case 2: return 'success'
    case 3: return 'info'
    case 4: return 'danger'
    default: return 'info'
  }
}

onMounted(() => { fetchData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">预约管理</h1>
      <p class="page-desc">管理会员预约，支持创建、查看和取消预约</p>
    </div>

    <div class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="会员姓名">
          <el-input v-model="queryParams.memberName" placeholder="请输入会员姓名" clearable />
        </el-form-item>
        <el-form-item label="机位编号">
          <el-input v-model="queryParams.computerNo" placeholder="请输入机位编号" clearable />
        </el-form-item>
        <el-form-item label="预约日期">
          <el-date-picker v-model="queryParams.reservationDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, val) in RESERVATION_STATUS_MAP" :key="val" :label="label" :value="Number(val)" />
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
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建预约</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="reservationNo" label="预约编号" width="150" />
        <el-table-column prop="memberName" label="会员" width="100" />
        <el-table-column prop="memberPhone" label="手机号" width="130" />
        <el-table-column prop="computerNo" label="机位" width="100">
          <template #default="{ row }">{{ row.computerNo || '到店分配' }}</template>
        </el-table-column>
        <el-table-column label="预约日期" width="120">
          <template #default="{ row }">{{ formatDate(row.reservationDate, 'YYYY-MM-DD') }}</template>
        </el-table-column>
        <el-table-column label="时间段" width="140">
          <template #default="{ row }">
            {{ formatDate(row.startTime, 'HH:mm') }} - {{ formatDate(row.endTime, 'HH:mm') }}
          </template>
        </el-table-column>
        <el-table-column label="保证金" width="100">
          <template #default="{ row }">{{ row.depositAmount ? '¥' + formatMoney(row.depositAmount) : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ row.statusLabel || RESERVATION_STATUS_MAP[row.status] || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0 || row.status === 1"
              type="danger" link size="small" :icon="Delete"
              @click="handleCancel(row)"
            >取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
      </div>
    </div>

    <!-- 新建预约弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建预约" width="500px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="会员手机号" required>
          <div style="display: flex; gap: 8px; width: 100%;">
            <el-input v-model="createForm.memberPhone" placeholder="请输入会员手机号" maxlength="11" @keyup.enter="searchMemberForCreate" />
            <el-button type="primary" :icon="Search" :loading="memberSearchLoading" @click="searchMemberForCreate">搜索</el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="memberFound" label="会员信息">
          <el-tag type="success">已找到会员</el-tag>
        </el-form-item>
        <el-form-item label="预约日期" required>
          <el-date-picker v-model="createForm.reservationDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-time-picker v-model="createForm.startTime" format="HH:mm" value-format="HH:mm" placeholder="选择开始时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-time-picker v-model="createForm.endTime" format="HH:mm" value-format="HH:mm" placeholder="选择结束时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="保证金（元）">
          <el-input-number v-model="createForm.depositAmount" :min="0" :step="10" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">确认创建</el-button>
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
.table-toolbar { margin-bottom: 16px; display: flex; justify-content: flex-end; }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }
</style>
