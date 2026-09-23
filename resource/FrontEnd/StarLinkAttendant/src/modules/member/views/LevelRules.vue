<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { getLevelList, createLevel, updateLevel, deleteLevel } from '../api'
import type { LevelItem, LevelCreateRequest } from '../types'

const loading = ref(false)
const tableData = ref<LevelItem[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增等级')
const formRef = ref<FormInstance>()
const editId = ref<number | null>(null)
const formData = reactive<LevelCreateRequest>({
  levelName: '', levelOrder: 0, minGrowth: 0, maxGrowth: 0,
  discountRate: 100, hourlyDiscount: 100, rechargeBonusRate: 0,
  pointsMultiple: 1, autoUpgrade: 1, iconUrl: '',
})

const formRules: FormRules = {
  levelName: [{ required: true, message: '请输入等级名称', trigger: 'blur' }],
  levelOrder: [{ required: true, message: '请输入等级序号', trigger: 'blur' }],
  minGrowth: [{ required: true, message: '请输入最低成长值', trigger: 'blur' }],
  maxGrowth: [{ required: true, message: '请输入最高成长值', trigger: 'blur' }],
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getLevelList()
    tableData.value = res.data || []
  } catch { tableData.value = [] }
  finally { loading.value = false }
}

const openCreateDialog = () => {
  editId.value = null
  dialogTitle.value = '新增等级'
  Object.assign(formData, { levelName: '', levelOrder: 0, minGrowth: 0, maxGrowth: 0, discountRate: 100, hourlyDiscount: 100, rechargeBonusRate: 0, pointsMultiple: 1, autoUpgrade: 1, iconUrl: '' })
  dialogVisible.value = true
}

const openEditDialog = (row: LevelItem) => {
  editId.value = row.id
  dialogTitle.value = '编辑等级'
  Object.assign(formData, { levelName: row.levelName, levelOrder: row.levelOrder, minGrowth: row.minGrowth, maxGrowth: row.maxGrowth, discountRate: row.discountRate, hourlyDiscount: row.hourlyDiscount, rechargeBonusRate: row.rechargeBonusRate, pointsMultiple: row.pointsMultiple, autoUpgrade: row.autoUpgrade, iconUrl: row.iconUrl })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (editId.value) {
        await updateLevel(editId.value, formData)
        ElMessage.success('更新成功')
      } else {
        await createLevel(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch {}
  })
}

const handleDelete = async (row: LevelItem) => {
  try {
    await ElMessageBox.confirm(`确定要删除等级「${row.levelName}」吗？`, '确认删除', { type: 'warning' })
    await deleteLevel(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {}
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">等级规则</h1>
      <p class="page-desc">配置会员等级体系，包括成长值阈值和各项权益</p>
    </div>
    <div class="table-card">
      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增等级</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="levelOrder" label="序号" width="70" />
        <el-table-column prop="levelName" label="等级名称" width="140" />
        <el-table-column label="成长值范围" width="140">
          <template #default="{ row }">{{ row.minGrowth }} ~ {{ row.maxGrowth }}</template>
        </el-table-column>
        <el-table-column label="消费折扣" width="100">
          <template #default="{ row }">{{ row.discountRate }}%</template>
        </el-table-column>
        <el-table-column label="上机折扣" width="100">
          <template #default="{ row }">{{ row.hourlyDiscount }}%</template>
        </el-table-column>
        <el-table-column label="充值赠送" width="100">
          <template #default="{ row }">{{ row.rechargeBonusRate }}%</template>
        </el-table-column>
        <el-table-column label="积分倍率" width="100">
          <template #default="{ row }">{{ row.pointsMultiple }}x</template>
        </el-table-column>
        <el-table-column label="自动升级" width="100">
          <template #default="{ row }">
            <el-tag :type="row.autoUpgrade === 1 ? 'success' : 'info'" size="small">{{ row.autoUpgrade === 1 ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="等级名称" prop="levelName">
          <el-input v-model="formData.levelName" placeholder="如：普通会员、银卡、金卡" />
        </el-form-item>
        <el-form-item label="等级序号" prop="levelOrder">
          <el-input-number v-model="formData.levelOrder" :min="0" :max="99" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="最低成长值" prop="minGrowth">
              <el-input-number v-model="formData.minGrowth" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最高成长值" prop="maxGrowth">
              <el-input-number v-model="formData.maxGrowth" :min="0" :max="2147483647" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider>权益配置</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="消费折扣(%)">
              <el-input-number v-model="formData.discountRate" :min="0" :max="100" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上机折扣(%)">
              <el-input-number v-model="formData.hourlyDiscount" :min="0" :max="100" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="充值赠送(%)">
              <el-input-number v-model="formData.rechargeBonusRate" :min="0" :max="100" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="积分倍率">
              <el-input-number v-model="formData.pointsMultiple" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="自动升级">
          <el-radio-group v-model="formData.autoUpgrade">
            <el-radio :value="1">是</el-radio>
            <el-radio :value="0">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="图标URL">
          <el-input v-model="formData.iconUrl" placeholder="图标地址（可选）" />
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
