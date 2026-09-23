<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { getCategoryTree, createCategory, updateCategory, deleteCategory } from '../api'
import { PRODUCT_STATUS_MAP } from '@/common/constants'
import type { CategoryItem, CategoryForm } from '../types'
import type { FormInstance } from 'element-plus'

const loading = ref(false)
const treeData = ref<CategoryItem[]>([])
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number | null>(null)
const parentOptions = ref<CategoryItem[]>([])

const form = reactive<CategoryForm>({
  parentId: null,
  categoryName: '',
  icon: '',
  sortOrder: 0,
  isActive: 1,
})

const rules = {
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getCategoryTree()
    treeData.value = res.data || []
    parentOptions.value = [{ id: 0, parentId: null, categoryName: '顶级分类', icon: null, sortOrder: 0, level: 0, isActive: 1, isActiveLabel: '上架', children: res.data || [] }]
  } catch { treeData.value = [] }
  finally { loading.value = false }
}

const handleAdd = (parentId?: number) => {
  editId.value = null
  form.parentId = parentId || null
  form.categoryName = ''
  form.icon = ''
  form.sortOrder = 0
  form.isActive = 1
  dialogVisible.value = true
}

const handleEdit = (row: CategoryItem) => {
  editId.value = row.id
  form.parentId = row.parentId
  form.categoryName = row.categoryName
  form.icon = row.icon || ''
  form.sortOrder = row.sortOrder
  form.isActive = row.isActive
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  try {
    if (editId.value) {
      await updateCategory(editId.value, form)
      ElMessage.success('分类更新成功')
    } else {
      await createCategory(form)
      ElMessage.success('分类创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch { /* handled */ }
}

const handleDelete = async (row: CategoryItem) => {
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.categoryName}」？子分类也会被删除。`, '确认删除', { type: 'warning' })
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* cancelled */ }
}

onMounted(() => { fetchData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">分类管理</h1>
      <p class="page-desc">管理商品分类，支持多级分类</p>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="handleAdd()">新增顶级分类</el-button>
      </div>

      <el-table :data="treeData" v-loading="loading" row-key="id" default-expand-all style="width: 100%">
        <el-table-column prop="categoryName" label="分类名称" min-width="200" />
        <el-table-column prop="icon" label="图标" width="100">
          <template #default="{ row }">{{ row.icon || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isActive === 1 ? 'success' : 'info'" size="small">{{ PRODUCT_STATUS_MAP[row.isActive] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :icon="Plus" @click="handleAdd(row.id)">添加子分类</el-button>
            <el-button type="primary" link size="small" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editId ? '编辑分类' : '新增分类'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="上级分类">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'categoryName', value: 'id', children: 'children' }"
            placeholder="无（顶级分类）"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="可选，如 emoji 或图标名" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.isActive" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
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
.table-card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.table-toolbar { margin-bottom: 16px; display: flex; justify-content: flex-end; }
</style>
