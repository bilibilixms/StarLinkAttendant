<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete, RefreshRight } from '@element-plus/icons-vue'
import { getComboList, createCombo, updateCombo, deleteCombo, updateComboStatus, getProductList } from '../api'
import { PRODUCT_STATUS_MAP } from '@/common/constants'
import { formatMoney } from '@/common/utils/money'
import { formatDate } from '@/common/utils/date'
import type { ComboItem, ComboForm, ComboItemRequest, ProductItem } from '../types'
import type { FormInstance } from 'element-plus'

const loading = ref(false)
const tableData = ref<ComboItem[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number | null>(null)
const productList = ref<ProductItem[]>([])

const queryParams = reactive({
  page: 1, size: 10, comboName: '', isActive: null as number | null,
})

const form = reactive<ComboForm>({
  comboName: '',
  comboCode: '',
  description: '',
  comboPrice: 0,
  imageUrl: '',
  isActive: 1,
  sortOrder: 0,
  comboItems: [],
})

const rules = {
  comboName: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  comboCode: [{ required: true, message: '请输入套餐编码', trigger: 'blur' }],
  comboPrice: [{ required: true, message: '请输入套餐价格', trigger: 'blur' }],
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getComboList(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { tableData.value = [] }
  finally { loading.value = false }
}

const fetchProducts = async () => {
  try {
    const res = await getProductList({ page: 1, size: 200, isActive: 1 })
    productList.value = res.data.records || []
  } catch { productList.value = [] }
}

const handleSearch = () => { queryParams.page = 1; fetchData() }
const handleReset = () => {
  queryParams.comboName = ''; queryParams.isActive = null
  handleSearch()
}
const handlePageChange = (page: number) => { queryParams.page = page; fetchData() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchData() }

const handleAdd = () => {
  editId.value = null
  form.comboName = ''; form.comboCode = ''; form.description = ''
  form.comboPrice = 0; form.imageUrl = ''; form.isActive = 1; form.sortOrder = 0
  form.comboItems = []
  dialogVisible.value = true
}

const handleEdit = (row: ComboItem) => {
  editId.value = row.id
  form.comboName = row.comboName
  form.comboCode = row.comboCode
  form.description = row.description || ''
  form.comboPrice = row.comboPrice
  form.imageUrl = row.imageUrl || ''
  form.isActive = row.isActive
  form.sortOrder = row.sortOrder
  form.comboItems = row.items.map(i => ({ productId: i.productId, quantity: i.quantity }))
  dialogVisible.value = true
}

const addComboItem = () => {
  form.comboItems.push({ productId: 0, quantity: 1 })
}

const removeComboItem = (index: number) => {
  form.comboItems.splice(index, 1)
}

const getProductName = (productId: number) => {
  const p = productList.value.find(p => p.id === productId)
  return p ? p.productName : '-'
}

const getProductPrice = (productId: number) => {
  const p = productList.value.find(p => p.id === productId)
  return p ? p.retailPrice : 0
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  if (form.comboItems.length === 0) {
    ElMessage.warning('请至少添加一个商品')
    return
  }
  try {
    if (editId.value) {
      await updateCombo(editId.value, form)
      ElMessage.success('套餐更新成功')
    } else {
      await createCombo(form)
      ElMessage.success('套餐创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch { /* handled */ }
}

const handleToggleStatus = async (row: ComboItem) => {
  const newStatus = row.isActive === 1 ? 0 : 1
  const action = newStatus === 1 ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(`确定${action}套餐「${row.comboName}」？`, `确认${action}`, { type: 'warning' })
    await updateComboStatus(row.id, newStatus)
    ElMessage.success(`${action}成功`)
    fetchData()
  } catch { /* cancelled */ }
}

const handleDelete = async (row: ComboItem) => {
  try {
    await ElMessageBox.confirm(`确定删除套餐「${row.comboName}」？`, '确认删除', { type: 'warning' })
    await deleteCombo(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* cancelled */ }
}

onMounted(() => { fetchData(); fetchProducts() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">套餐管理</h1>
      <p class="page-desc">管理商品套餐组合</p>
    </div>

    <div class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="套餐名称">
          <el-input v-model="queryParams.comboName" placeholder="请输入套餐名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.isActive" placeholder="全部" clearable style="width: 100px">
            <el-option v-for="(label, val) in PRODUCT_STATUS_MAP" :key="val" :label="label" :value="Number(val)" />
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
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增套餐</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="comboCode" label="编码" width="120" />
        <el-table-column prop="comboName" label="套餐名称" min-width="150" />
        <el-table-column label="原价" width="90">
          <template #default="{ row }">{{ row.originalPrice ? '¥' + formatMoney(row.originalPrice) : '-' }}</template>
        </el-table-column>
        <el-table-column label="套餐价" width="90">
          <template #default="{ row }">
            <span style="color: #ef4444; font-weight: 600">¥{{ formatMoney(row.comboPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="包含商品" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="item in row.items" :key="item.id" size="small" style="margin: 2px 4px 2px 0">
              {{ item.productName }} x{{ item.quantity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isActive === 1 ? 'success' : 'info'" size="small">{{ PRODUCT_STATUS_MAP[row.isActive] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button :type="row.isActive === 1 ? 'warning' : 'success'" link size="small" @click="handleToggleStatus(row)">
              {{ row.isActive === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
      </div>
    </div>

    <!-- 新增/编辑套餐弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑套餐' : '新增套餐'" width="650px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="套餐编码" prop="comboCode">
          <el-input v-model="form.comboCode" placeholder="请输入编码" />
        </el-form-item>
        <el-form-item label="套餐名称" prop="comboName">
          <el-input v-model="form.comboName" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="套餐描述" />
        </el-form-item>
        <el-form-item label="套餐价格" prop="comboPrice">
          <el-input-number v-model="form.comboPrice" :min="0" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" style="width: 200px" />
        </el-form-item>
        <el-form-item label="图片URL">
          <el-input v-model="form.imageUrl" placeholder="可选" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.isActive" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>

        <el-divider>包含商品</el-divider>
        <div v-for="(item, index) in form.comboItems" :key="index" class="combo-item-row">
          <el-select v-model="item.productId" placeholder="选择商品" style="flex: 1">
            <el-option v-for="p in productList" :key="p.id" :label="`${p.productName} (¥${formatMoney(p.retailPrice)})`" :value="p.id" />
          </el-select>
          <el-input-number v-model="item.quantity" :min="1" size="small" style="width: 100px; margin: 0 8px" />
          <span class="item-subtotal">¥{{ formatMoney(getProductPrice(item.productId) * item.quantity) }}</span>
          <el-button type="danger" text :icon="Delete" @click="removeComboItem(index)" />
        </div>
        <el-button type="primary" text :icon="Plus" @click="addComboItem" style="margin-top: 8px">添加商品</el-button>
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
.combo-item-row { display: flex; align-items: center; margin-bottom: 8px; }
.item-subtotal { font-size: 13px; color: #ef4444; font-weight: 600; min-width: 60px; text-align: right; }
</style>
