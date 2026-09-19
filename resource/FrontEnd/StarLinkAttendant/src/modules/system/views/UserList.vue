<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus, Edit, Delete, RefreshRight, Key } from '@element-plus/icons-vue'
import { getUserList, createUser, updateUser, deleteUser, updateUserStatus, resetUserPassword } from '../api'
import { EMPLOYEE_STATUS_MAP, EMPLOYMENT_TYPE_MAP } from '@/common/constants'
import { formatDate } from '@/common/utils/date'
import type { UserItem, UserQuery, UserCreateRequest } from '../types'

const loading = ref(false)
const tableData = ref<UserItem[]>([])
const total = ref(0)
const queryParams = reactive<PageQuery & UserQuery>({
  page: 1,
  size: 10,
  realName: '',
  phone: '',
  status: null,
})

interface PageQuery { page: number; size: number }

const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const formRef = ref<FormInstance>()
const editId = ref<number | null>(null)
const formData = reactive<UserCreateRequest>({
  employeeNo: '',
  realName: '',
  phone: '',
  password: '',
  position: '',
  employmentType: 1,
  status: 1,
})

const formRules: FormRules = {
  employeeNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度6-32位', trigger: 'blur' },
  ],
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getUserList(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.page = 1
  fetchData()
}

const handleReset = () => {
  queryParams.realName = ''
  queryParams.phone = ''
  queryParams.status = null
  handleSearch()
}

const handlePageChange = (page: number) => {
  queryParams.page = page
  fetchData()
}

const handleSizeChange = (size: number) => {
  queryParams.size = size
  queryParams.page = 1
  fetchData()
}

const openCreateDialog = () => {
  editId.value = null
  dialogTitle.value = '新增用户'
  Object.assign(formData, { employeeNo: '', realName: '', phone: '', password: '', position: '', employmentType: 1, status: 1 })
  dialogVisible.value = true
}

const openEditDialog = (row: UserItem) => {
  editId.value = row.id
  dialogTitle.value = '编辑用户'
  Object.assign(formData, {
    employeeNo: row.employeeNo,
    realName: row.realName,
    phone: row.phone,
    password: '',
    position: row.position,
    employmentType: row.employmentType,
    status: row.status,
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (editId.value) {
        await updateUser(editId.value, {
          realName: formData.realName,
          phone: formData.phone,
          position: formData.position,
          employmentType: formData.employmentType,
          status: formData.status,
        })
        ElMessage.success('更新成功')
      } else {
        await createUser(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      // handled by interceptor
    }
  })
}

const handleDelete = async (row: UserItem) => {
  try {
    await ElMessageBox.confirm(`确定要删除用户「${row.realName}」吗？`, '确认删除', {
      type: 'warning',
    })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

const handleToggleStatus = async (row: UserItem) => {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定要${action}用户「${row.realName}」吗？`, '确认操作', { type: 'warning' })
    await updateUserStatus(row.id, newStatus)
    ElMessage.success(`${action}成功`)
    fetchData()
  } catch {
    // cancelled
  }
}

const handleResetPassword = async (row: UserItem) => {
  try {
    await ElMessageBox.confirm(`确定要重置用户「${row.realName}」的密码吗？`, '确认重置', { type: 'warning' })
    await resetUserPassword(row.id)
    ElMessage.success('密码已重置')
  } catch {
    // cancelled
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">用户管理</h1>
      <p class="page-desc">管理系统员工账号，包括创建、编辑、状态管理等</p>
    </div>

    <!-- 搜索栏 -->
    <div class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="姓名">
          <el-input v-model="queryParams.realName" placeholder="请输入姓名" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="queryParams.phone" placeholder="请输入手机号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, val) in EMPLOYEE_STATUS_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 操作栏 + 表格 -->
    <div class="table-card">
      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增用户</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="employeeNo" label="工号" width="120" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="position" label="职位" width="120" />
        <el-table-column label="用工类型" width="100">
          <template #default="{ row }">{{ EMPLOYMENT_TYPE_MAP[row.employmentType] || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ EMPLOYEE_STATUS_MAP[row.status] || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="170">
          <template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="warning" link size="small" :icon="Key" @click="handleResetPassword(row)">重置密码</el-button>
            <el-button :type="row.status === 1 ? 'danger' : 'success'" link size="small" @click="handleToggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @update:current-page="handlePageChange"
          @update:page-size="handleSizeChange"
        />
      </div>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="工号" prop="employeeNo">
          <el-input v-model="formData.employeeNo" placeholder="请输入工号" :disabled="!!editId" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="formData.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item v-if="!editId" label="密码" prop="password">
          <el-input v-model="formData.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="职位">
          <el-input v-model="formData.position" placeholder="请输入职位" />
        </el-form-item>
        <el-form-item label="用工类型">
          <el-select v-model="formData.employmentType" placeholder="请选择" style="width: 100%">
            <el-option v-for="(label, val) in EMPLOYMENT_TYPE_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">在职</el-radio>
            <el-radio :value="0">离职</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
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
