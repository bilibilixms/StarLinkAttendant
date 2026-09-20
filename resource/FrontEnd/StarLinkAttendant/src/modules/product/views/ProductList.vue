<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete, RefreshRight } from '@element-plus/icons-vue'
import { getProductList, deleteProduct, updateProductStatus } from '../api'
import { PRODUCT_TYPE_MAP, PRODUCT_STATUS_MAP } from '@/common/constants'
import { formatMoney } from '@/common/utils/money'
import { formatDate } from '@/common/utils/date'
import type { ProductItem } from '../types'

const router = useRouter()
const loading = ref(false)
const tableData = ref<ProductItem[]>([])
const total = ref(0)

const queryParams = reactive({
  page: 1, size: 10, productName: '',
  productType: null as number | null, isActive: null as number | null,
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getProductList(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { tableData.value = [] }
  finally { loading.value = false }
}

const handleSearch = () => { queryParams.page = 1; fetchData() }
const handleReset = () => {
  queryParams.productName = ''
  queryParams.productType = null; queryParams.isActive = null
  handleSearch()
}
const handlePageChange = (page: number) => { queryParams.page = page; fetchData() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchData() }

const handleEdit = (row: ProductItem) => {
  router.push(`/product/edit/${row.id}`)
}

const handleToggleStatus = async (row: ProductItem) => {
  const newStatus = row.isActive === 1 ? 0 : 1
  const action = newStatus === 1 ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(`确定${action}商品「${row.productName}」？`, `确认${action}`, { type: 'warning' })
    await updateProductStatus(row.id, newStatus)
    ElMessage.success(`${action}成功`)
    fetchData()
  } catch { /* cancelled */ }
}

const handleDelete = async (row: ProductItem) => {
  try {
    await ElMessageBox.confirm(`确定删除商品「${row.productName}」？删除后不可恢复。`, '确认删除', { type: 'warning' })
    await deleteProduct(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* cancelled */ }
}

onMounted(() => { fetchData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">商品列表</h1>
      <p class="page-desc">管理所有商品信息</p>
    </div>

    <div class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="商品名称">
          <el-input v-model="queryParams.productName" placeholder="请输入商品名称" clearable />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.productType" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="(label, val) in PRODUCT_TYPE_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
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
        <el-button type="primary" :icon="Plus" @click="router.push('/product/edit/0')">新增商品</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="productCode" label="编码" width="120" />
        <el-table-column label="图片" width="70" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.imageUrl"
              :src="row.imageUrl"
              :preview-src-list="[row.imageUrl]"
              fit="cover"
              preview-teleported
              class="product-thumb"
            />
            <div v-else class="product-thumb-placeholder">
              <span>无</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="商品名称" min-width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ PRODUCT_TYPE_MAP[row.productType] || '-' }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column label="成本价" width="90">
          <template #default="{ row }">¥{{ formatMoney(row.costPrice) }}</template>
        </el-table-column>
        <el-table-column label="零售价" width="90">
          <template #default="{ row }">¥{{ formatMoney(row.retailPrice) }}</template>
        </el-table-column>
        <el-table-column label="会员价" width="90">
          <template #default="{ row }">{{ row.memberPrice ? '¥' + formatMoney(row.memberPrice) : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isActive === 1 ? 'success' : 'info'" size="small">{{ PRODUCT_STATUS_MAP[row.isActive] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
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
.product-thumb { width: 44px; height: 44px; border-radius: 6px; cursor: pointer; }
.product-thumb-placeholder {
  width: 44px; height: 44px; border-radius: 6px; background: #f1f5f9;
  display: flex; align-items: center; justify-content: center;
  color: #c0c4cc; font-size: 12px; margin: 0 auto;
}
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }
</style>
