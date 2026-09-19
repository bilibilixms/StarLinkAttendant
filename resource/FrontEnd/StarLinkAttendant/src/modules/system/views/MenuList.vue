<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { getMenuTree, createMenu, updateMenu, deleteMenu } from '../api'
import { PERM_TYPE_MAP } from '@/common/constants'
import type { MenuItem, MenuCreateRequest } from '../types'

const loading = ref(false)
const treeData = ref<MenuItem[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增菜单')
const formRef = ref<FormInstance>()
const editId = ref<number | null>(null)
const formData = reactive<MenuCreateRequest>({
  parentId: null,
  permName: '',
  permCode: '',
  permType: 1,
  icon: '',
  route: '',
  sortOrder: 0,
})

const formRules: FormRules = {
  permName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  permCode: [{ required: true, message: '请输入权限编码', trigger: 'blur' }],
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getMenuTree()
    treeData.value = res.data || []
  } catch {
    treeData.value = []
  } finally {
    loading.value = false
  }
}

const openCreateDialog = (parentId?: number) => {
  editId.value = null
  dialogTitle.value = '新增菜单'
  Object.assign(formData, { parentId: parentId || null, permName: '', permCode: '', permType: 1, icon: '', route: '', sortOrder: 0 })
  dialogVisible.value = true
}

const openEditDialog = (row: MenuItem) => {
  editId.value = row.id
  dialogTitle.value = '编辑菜单'
  Object.assign(formData, { parentId: row.parentId, permName: row.permName, permCode: row.permCode, permType: row.permType, icon: row.icon, route: row.route, sortOrder: row.sortOrder })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (editId.value) {
        await updateMenu(editId.value, formData)
        ElMessage.success('更新成功')
      } else {
        await createMenu(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch { /* interceptor */ }
  })
}

const handleDelete = async (row: MenuItem) => {
  try {
    await ElMessageBox.confirm(`确定要删除「${row.permName}」吗？`, '确认删除', { type: 'warning' })
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* cancelled */ }
}

const getPermTypeTag = (type: number) => {
  const map: Record<number, string> = { 1: '', 2: 'warning', 3: 'info' }
  return map[type] || 'info'
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">菜单管理</h1>
      <p class="page-desc">管理系统菜单与权限节点</p>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog()">新增顶级菜单</el-button>
      </div>

      <el-table :data="treeData" v-loading="loading" row-key="id" default-expand-all :tree-props="{ children: 'children' }" stripe style="width: 100%">
        <el-table-column prop="permName" label="名称" min-width="200" />
        <el-table-column prop="permCode" label="权限编码" min-width="180" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getPermTypeTag(row.permType)" size="small">{{ PERM_TYPE_MAP[row.permType] || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="route" label="路由" width="180" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :icon="Plus" @click="openCreateDialog(row.id)">子项</el-button>
            <el-button type="warning" link size="small" :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="formData.parentId"
            :data="treeData"
            :props="{ label: 'permName', value: 'id', children: 'children' }"
            check-strictly
            clearable
            placeholder="无（顶级菜单）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="名称" prop="permName">
          <el-input v-model="formData.permName" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="权限编码" prop="permCode">
          <el-input v-model="formData.permCode" placeholder="如 system:user:list" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="formData.permType">
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
            <el-radio :value="3">接口</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="路由">
          <el-input v-model="formData.route" placeholder="前端路由路径" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="formData.icon" placeholder="图标名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="999" />
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
