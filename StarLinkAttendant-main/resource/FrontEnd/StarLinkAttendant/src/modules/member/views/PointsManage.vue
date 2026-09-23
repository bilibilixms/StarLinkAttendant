<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, RefreshRight } from '@element-plus/icons-vue'
import { getMemberList, getPointsRecords } from '../api'
import { POINTS_BIZ_TYPE_MAP } from '@/common/constants'
import { formatDate } from '@/common/utils/date'
import type { MemberItem, PointsRecordItem } from '../types'

const loading = ref(false)
const records = ref<PointsRecordItem[]>([])
const total = ref(0)

const memberOptions = ref<MemberItem[]>([])
const memberLoading = ref(false)
const selectedMemberId = ref<number | null>(null)
const selectedMemberName = ref('')

const queryParams = reactive({
  page: 1,
  size: 10,
  bizType: null as number | null,
  startTime: '',
  endTime: '',
})

const dateRange = ref<[string, string] | null>(null)

const searchMember = async (query: string) => {
  if (!query) { memberOptions.value = []; return }
  memberLoading.value = true
  try {
    const res = await getMemberList({ page: 1, size: 10, realName: query })
    memberOptions.value = res.data.records || []
  } catch { memberOptions.value = [] }
  finally { memberLoading.value = false }
}

const handleMemberSelect = (member: MemberItem) => {
  selectedMemberId.value = member.id
  selectedMemberName.value = member.realName || member.memberNo
  queryParams.page = 1
  fetchRecords()
}

const fetchRecords = async () => {
  if (!selectedMemberId.value) return
  loading.value = true
  try {
    if (dateRange.value) {
      queryParams.startTime = dateRange.value[0]
      queryParams.endTime = dateRange.value[1]
    } else {
      queryParams.startTime = ''
      queryParams.endTime = ''
    }
    const res = await getPointsRecords(selectedMemberId.value, queryParams as any)
    records.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { records.value = [] }
  finally { loading.value = false }
}

const handleSearch = () => { queryParams.page = 1; fetchRecords() }
const handleReset = () => {
  queryParams.bizType = null
  dateRange.value = null
  queryParams.startTime = ''
  queryParams.endTime = ''
  handleSearch()
}
const handlePageChange = (page: number) => { queryParams.page = page; fetchRecords() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchRecords() }
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">积分管理</h1>
      <p class="page-desc">查询会员积分流水记录</p>
    </div>
    <div class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="选择会员">
          <el-select v-model="selectedMemberId" filterable remote reserve-keyword :remote-method="searchMember" :loading="memberLoading" placeholder="输入姓名搜索会员" style="width: 240px" @change="(val: any) => { const m = memberOptions.find(o => o.id === val); if (m) handleMemberSelect(m) }">
            <el-option v-for="member in memberOptions" :key="member.id" :label="`${member.realName || member.memberNo} (${member.phone})`" :value="member.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="积分类型">
          <el-select v-model="queryParams.bizType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(label, val) in POINTS_BIZ_TYPE_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch" :disabled="!selectedMemberId">搜索</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="table-card">
      <div v-if="selectedMemberName" class="member-badge">当前查看：<strong>{{ selectedMemberName }}</strong> 的积分记录</div>
      <el-empty v-if="!selectedMemberId" description="请先选择一个会员" />
      <template v-else>
        <el-table :data="records" v-loading="loading" stripe style="width: 100%">
          <el-table-column label="积分变动" width="120">
            <template #default="{ row }">
              <span :style="{ color: row.points > 0 ? '#22c55e' : '#ef4444', fontWeight: 600 }">{{ row.points > 0 ? '+' : '' }}{{ row.points }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="balanceBefore" label="变动前" width="100" />
          <el-table-column prop="balanceAfter" label="变动后" width="100" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }"><el-tag size="small">{{ POINTS_BIZ_TYPE_MAP[row.bizType] || '-' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrapper">
          <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
        </div>
      </template>
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
.member-badge { margin-bottom: 16px; padding: 10px 16px; background: #f0f9ff; border-radius: 8px; font-size: 14px; color: #1e40af; }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }
</style>
