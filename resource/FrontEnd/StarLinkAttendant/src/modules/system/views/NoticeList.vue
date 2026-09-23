<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete, Search, RefreshRight } from '@element-plus/icons-vue'
import { getNoticeList, createNotice, updateNotice, deleteNotice } from '../api'
import { NOTIFY_TYPE_MAP } from '@/common/constants'
import { formatDate } from '@/common/utils/date'
import type { NoticeItem, NoticeCreateRequest } from '../types'

const loading = ref(false)
const tableData = ref<NoticeItem[]>([])
const total = ref(0)
const queryParams = reactive({ page: 1, size: 10, title: '', notifyType: null as number | null })

const dialogVisible = ref(false)
const dialogTitle = ref('新增公告')
const formRef = ref<FormInstance>()
const editId = ref<number | null>(null)
const formData = reactive<NoticeCreateRequest>({
  title: '',
  content: '',
  notifyType: 1,
  targetType: 0,
  publishedAt: '',
  expiredAt: '',
})

const formRules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getNoticeList(queryParams as any)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { queryParams.page = 1; fetchData() }
const handleReset = () => { queryParams.title = ''; queryParams.notifyType = null; handleSearch() }
const handlePageChange = (page: number) => { queryParams.page = page; fetchData() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchData() }

const openCreateDialog = () => {
  editId.value = null
  dialogTitle.value = '新增公告'
  Object.assign(formData, { title: '', content: '', notifyType: 1, targetType: 0, publishedAt: '', expiredAt: '' })
  dialogVisible.value = true
}

const openEditDialog = (row: NoticeItem) => {
  editId.value = row.id
  dialogTitle.value = '编辑公告'
  Object.assign(formData, { title: row.title, content: row.content, notifyType: row.notifyType, targetType: row.targetType, publishedAt: row.publishedAt, expiredAt: row.expiredAt })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (editId.value) {
        await updateNotice(editId.value, { title: formData.title, content: formData.content, publishedAt: formData.publishedAt, expiredAt: formData.expiredAt })
        ElMessage.success('更新成功')
      } else {
        await createNotice(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch { /* interceptor */ }
  })
}

const handleDelete = async (row: NoticeItem) => {
  try {
    await ElMessageBox.confirm(`确定要删除公告「${row.title}」吗？`, '确认删除', { type: 'warning' })
    await deleteNotice(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* cancelled */ }
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">通知公告</h1>
      <p class="page-desc">管理系统通知公告</p>
    </div>

    <div class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="标题">
          <el-input v-model="queryParams.title" placeholder="请输入标题" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.notifyType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(label, val) in NOTIFY_TYPE_MAP" :key="val" :label="label" :value="Number(val)" />
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
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增公告</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ NOTIFY_TYPE_MAP[row.notifyType] || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="170">
          <template #default="{ row }">{{ formatDate(row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column label="过期时间" width="170">
          <template #default="{ row }">{{ formatDate(row.expiredAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="formData.notifyType" style="width: 100%">
            <el-option v-for="(label, val) in NOTIFY_TYPE_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="formData.content" type="textarea" placeholder="请输入公告内容" :rows="5" />
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker v-model="formData.publishedAt" type="datetime" placeholder="选择发布时间" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker v-model="formData.expiredAt" type="datetime" placeholder="选择过期时间" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
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
