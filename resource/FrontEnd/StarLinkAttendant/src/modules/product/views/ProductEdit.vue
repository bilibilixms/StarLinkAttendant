<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { getProduct, createProduct, updateProduct, getCategoryTree, uploadFile } from '../api'
import { PRODUCT_TYPE_MAP } from '@/common/constants'
import type { CategoryItem, ProductForm } from '../types'
import type { UploadProps, UploadFile as ElUploadFile } from 'element-plus'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const loading = ref(false)
const categoryOptions = ref<CategoryItem[]>([])
const imageUploading = ref(false)

const isEdit = computed(() => route.params.id !== '0')
const pageTitle = computed(() => isEdit.value ? '编辑商品' : '新增商品')

const flattenCategories = (cats: CategoryItem[], prefix = ''): { id: number; name: string }[] => {
  const result: { id: number; name: string }[] = []
  for (const c of cats) {
    result.push({ id: c.id, name: prefix + c.categoryName })
    if (c.children?.length) {
      result.push(...flattenCategories(c.children, prefix + c.categoryName + ' / '))
    }
  }
  return result
}

const flatCategories = ref<{ id: number; name: string }[]>([])

const form = reactive<ProductForm>({
  categoryId: null,
  productCode: '',
  productName: '',
  productType: 1,
  unit: '个',
  costPrice: 0,
  retailPrice: 0,
  memberPrice: null,
  imageUrl: null,
  isVipOnly: 0,
  isActive: 1,
})

const rules = {
  productCode: [{ required: true, message: '请输入商品编码', trigger: 'blur' }],
  productName: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  productType: [{ required: true, message: '请选择商品类型', trigger: 'change' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
  costPrice: [{ required: true, message: '请输入成本价', trigger: 'blur' }],
  retailPrice: [{ required: true, message: '请输入零售价', trigger: 'blur' }],
}

const fetchCategories = async () => {
  try {
    const res = await getCategoryTree()
    categoryOptions.value = res.data || []
    flatCategories.value = flattenCategories(res.data || [])
  } catch { categoryOptions.value = [] }
}

const fetchProduct = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getProduct(Number(route.params.id))
    const p = res.data
    form.categoryId = p.categoryId
    form.productCode = p.productCode
    form.productName = p.productName
    form.productType = p.productType
    form.unit = p.unit
    form.costPrice = p.costPrice
    form.retailPrice = p.retailPrice
    form.memberPrice = p.memberPrice
    form.imageUrl = p.imageUrl
    form.isVipOnly = p.isVipOnly
    form.isActive = p.isActive
  } catch { /* handled */ }
  finally { loading.value = false }
}

const fileImageUrl = computed(() => {
  return form.imageUrl ? [{ name: 'image', url: form.imageUrl }] : []
})

const handleImageUpload: UploadProps['httpRequest'] = async (options) => {
  const file = options.file as File
  // 校验文件类型
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif']
  if (!allowedTypes.includes(file.type)) {
    ElMessage.warning('仅支持 JPG / PNG / GIF 格式图片')
    return
  }
  // 校验文件大小 (10MB)
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 10MB')
    return
  }
  imageUploading.value = true
  try {
    const res = await uploadFile(file, 'product')
    form.imageUrl = res.data.url
    ElMessage.success('图片上传成功')
  } catch {
    ElMessage.error('图片上传失败')
  } finally {
    imageUploading.value = false
  }
}

const handleImageRemove = () => {
  form.imageUrl = null
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    if (isEdit.value) {
      await updateProduct(Number(route.params.id), form)
      ElMessage.success('商品更新成功')
    } else {
      await createProduct(form)
      ElMessage.success('商品创建成功')
    }
    router.push('/product/list')
  } catch { /* handled */ }
  finally { loading.value = false }
}

onMounted(() => { fetchCategories(); fetchProduct() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">{{ pageTitle }}</h1>
    </div>

    <div class="form-card" v-loading="loading">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width: 600px">
        <el-form-item label="商品编码" prop="productCode">
          <el-input v-model="form.productCode" placeholder="请输入商品编码" />
        </el-form-item>
        <el-form-item label="商品名称" prop="productName">
          <el-input v-model="form.productName" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="商品分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" clearable style="width: 100%">
            <el-option v-for="c in flatCategories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品类型" prop="productType">
          <el-select v-model="form.productType" style="width: 100%">
            <el-option v-for="(label, val) in PRODUCT_TYPE_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="form.unit" placeholder="个/瓶/份" style="width: 120px" />
        </el-form-item>
        <el-form-item label="成本价" prop="costPrice">
          <el-input-number v-model="form.costPrice" :min="0" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item label="零售价" prop="retailPrice">
          <el-input-number v-model="form.retailPrice" :min="0" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item label="会员价" prop="memberPrice">
          <el-input-number v-model="form.memberPrice" :min="0" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item label="商品图片">
          <el-upload
            class="image-uploader"
            :show-file-list="false"
            :http-request="handleImageUpload"
            accept="image/jpeg,image/png,image/gif"
            drag
          >
            <div v-if="form.imageUrl" class="image-preview-wrapper">
              <el-image :src="form.imageUrl" fit="contain" class="uploaded-image" />
              <div class="image-overlay">
                <span>点击更换图片</span>
              </div>
            </div>
            <div v-else class="upload-placeholder">
              <el-icon :size="32" color="#c0c4cc"><Plus /></el-icon>
              <span class="upload-text">{{ imageUploading ? '上传中...' : '点击或拖拽上传图片' }}</span>
              <span class="upload-hint">支持 JPG / PNG / GIF，最大 10MB</span>
            </div>
          </el-upload>
          <el-button v-if="form.imageUrl" type="danger" link size="small" @click="handleImageRemove" style="margin-top: 4px">
            移除图片
          </el-button>
        </el-form-item>
        <el-form-item label="仅会员">
          <el-switch v-model="form.isVipOnly" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="上架状态">
          <el-switch v-model="form.isActive" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit">保存</el-button>
          <el-button @click="router.push('/product/list')">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.form-card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }

.image-uploader :deep(.el-upload) {
  width: 200px; height: 200px; border-radius: 8px; overflow: hidden;
  border: 2px dashed #dcdfe6; transition: border-color 0.2s;
}
.image-uploader :deep(.el-upload:hover) { border-color: #409eff; }
.image-uploader :deep(.el-upload-dragger) {
  width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
  padding: 0; border: none; border-radius: 0;
}

.image-preview-wrapper {
  width: 100%; height: 100%; position: relative; display: flex; align-items: center; justify-content: center;
}
.uploaded-image { width: 100%; height: 100%; }
.image-overlay {
  position: absolute; inset: 0; background: rgba(0,0,0,0.4); color: white;
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity 0.2s; font-size: 13px;
}
.image-preview-wrapper:hover .image-overlay { opacity: 1; }

.upload-placeholder {
  display: flex; flex-direction: column; align-items: center; gap: 6px;
}
.upload-text { font-size: 13px; color: #606266; }
.upload-hint { font-size: 11px; color: #a8abb2; }
</style>
