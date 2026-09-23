<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete, Search, RefreshRight, Setting } from '@element-plus/icons-vue'
import { getRoleList, createRole, updateRole, deleteRole, assignRolePermissions, getMenuTree } from '../api'
import { formatDate } from '@/common/utils/date'
import type { RoleItem, RoleCreateRequest, MenuItem } from '../types'

const loading = ref(false)
const tableData = ref<RoleItem[]>([])
const total = ref(0)
const queryParams = reactive({ page: 1, size: 10 })

const dialogVisible = ref(false)
const dialogTitle = ref('新增角色')
const formRef = ref<FormInstance>()
const editId = ref<number | null>(null)
const formData = reactive<RoleCreateRequest>({
  roleName: '',
  roleCode: '',
  description: '',
  sortOrder: 0,
  isActive: 1,
})

const formRules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
}

// Permission tree
const permDialogVisible = ref(false)
const permRoleId = ref<number>(0)
const permRoleName = ref('')
const menuTree = ref<MenuItem[]>([])
const checkedPermIds = ref<number[]>([])
const permTreeRef = ref<any>(null)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getRoleList(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { queryParams.page = 1; fetchData() }
const handlePageChange = (page: number) => { queryParams.page = page; fetchData() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchData() }

const openCreateDialog = () => {
  editId.value = null
  dialogTitle.value = '新增角色'
  Object.assign(formData, { roleName: '', roleCode: '', description: '', sortOrder: 0, isActive: 1 })
  dialogVisible.value = true
}

const openEditDialog = (row: RoleItem) => {
  editId.value = row.id
  dialogTitle.value = '编辑角色'
  Object.assign(formData, { roleName: row.roleName, roleCode: row.roleCode, description: row.description, sortOrder: row.sortOrder, isActive: row.isActive })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (editId.value) {
        await updateRole(editId.value, { roleName: formData.roleName, description: formData.description, sortOrder: formData.sortOrder, isActive: formData.isActive })
        ElMessage.success('更新成功')
      } else {
        await createRole(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch { /* interceptor */ }
  })
}

const handleDelete = async (row: RoleItem) => {
  try {
    await ElMessageBox.confirm(`确定要删除角色「${row.roleName}」吗？`, '确认删除', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* cancelled */ }
}

const openPermDialog = async (row: RoleItem) => {
  permRoleId.value = row.id
  permRoleName.value = row.roleName
  checkedPermIds.value = []
  try {
    const res = await getMenuTree()
    menuTree.value = res.data || []
  } catch {
    menuTree.value = []
  }
  permDialogVisible.value = true
}

const handleSavePermissions = async () => {
  try {
    const checkedKeys = permTreeRef.value?.getCheckedKeys() || []
    const halfCheckedKeys = permTreeRef.value?.getHalfCheckedKeys() || []
    const allIds = [...checkedKeys, ...halfCheckedKeys]
    await assignRolePermissions(permRoleId.value, allIds)
    ElMessage.success('权限分配成功')
    permDialogVisible.value = false
  } catch { /* interceptor */ }
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">角色管理</h1>
      <p class="page-desc">管理系统角色，分配权限</p>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增角色</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="roleName" label="角色名称" width="160" />
        <el-table-column prop="roleCode" label="角色编码" width="160" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isActive === 1 ? 'success' : 'danger'" size="small">{{ row.isActive === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="warning" link size="small" :icon="Setting" @click="openPermDialog(row)">权限</el-button>
            <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row)" :disabled="row.isSystem === 1">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
      </div>
    </div>

    <!-- 角色表单对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="formData.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="formData.roleCode" placeholder="请输入角色编码" :disabled="!!editId" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" placeholder="请输入描述" :rows="3" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.isActive">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 权限分配对话框 -->
    <el-dialog v-model="permDialogVisible" :title="`权限分配 - ${permRoleName}`" width="500px" destroy-on-close>
      <el-tree
        ref="permTreeRef"
        :data="menuTree"
        :props="{ label: 'permName', children: 'children' }"
        show-checkbox
        node-key="id"
        :default-checked-keys="checkedPermIds"
        check-strictly
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSavePermissions">保存</el-button>
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
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }
</style>
