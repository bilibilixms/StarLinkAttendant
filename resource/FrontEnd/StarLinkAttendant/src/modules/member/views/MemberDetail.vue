<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getMember, getRechargeRecords, getPointsRecords, updateMember } from '../api'
import { MEMBER_STATUS_MAP, GENDER_MAP, PAYMENT_METHOD_MAP, RECHARGE_STATUS_MAP, POINTS_BIZ_TYPE_MAP } from '@/common/constants'
import { formatMoney } from '@/common/utils/money'
import { formatDate } from '@/common/utils/date'
import type { MemberItem, RechargeRecordItem, PointsRecordItem } from '../types'

const route = useRoute()
const router = useRouter()
const memberId = computed(() => Number(route.params.id))

const member = ref<MemberItem | null>(null)
const rechargeRecords = ref<RechargeRecordItem[]>([])
const pointsRecords = ref<PointsRecordItem[]>([])
const rechargeTotal = ref(0)
const pointsTotal = ref(0)
const rechargeLoading = ref(false)
const pointsLoading = ref(false)
const activeTab = ref('info')
const rechargePage = ref(1)
const pointsPage = ref(1)

// 标签编辑
const editTag = ref('')
const originalTag = ref('')
const tagDirty = computed(() => editTag.value !== originalTag.value)
const saving = ref(false)

const fetchMember = async () => {
  // 先重置编辑状态，避免显示上一位会员的残留数据
  editTag.value = ''
  originalTag.value = ''
  member.value = null
  try {
    const res = await getMember(memberId.value)
    member.value = res.data
    editTag.value = res.data.tag || ''
    originalTag.value = res.data.tag || ''
  } catch { /* interceptor */ }
}

const fetchRechargeRecords = async () => {
  rechargeLoading.value = true
  try {
    const res = await getRechargeRecords(memberId.value, { page: rechargePage.value, size: 10 })
    rechargeRecords.value = res.data.records || []
    rechargeTotal.value = res.data.total || 0
  } catch { rechargeRecords.value = [] }
  finally { rechargeLoading.value = false }
}

const fetchPointsRecords = async () => {
  pointsLoading.value = true
  try {
    const res = await getPointsRecords(memberId.value, { page: pointsPage.value, size: 10 })
    pointsRecords.value = res.data.records || []
    pointsTotal.value = res.data.total || 0
  } catch { pointsRecords.value = [] }
  finally { pointsLoading.value = false }
}

const handleTabChange = (tab: string) => {
  if (tab === 'recharge') fetchRechargeRecords()
  else if (tab === 'points') fetchPointsRecords()
}

const handleSaveTag = async () => {
  saving.value = true
  try {
    await updateMember(memberId.value, { tag: editTag.value })
    ElMessage.success('标签保存成功')
    router.push('/member/list')
  } catch { /* interceptor */ }
  finally { saving.value = false }
}

onMounted(fetchMember)

// 监听路由参数变化，切换会员时重新加载并重置编辑状态
watch(() => route.params.id, () => {
  fetchMember()
})
</script>

<template>
  <div class="page-container" v-if="member">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="router.push('/member/list')">返回列表</el-button>
      <h1 class="page-title" style="margin-top: 12px;">会员详情</h1>
    </div>

    <!-- 基本信息卡片 -->
    <div class="info-card">
      <div class="info-header">
        <div class="avatar">{{ member.realName?.charAt(0) || 'M' }}</div>
        <div class="info-main">
          <h2>{{ member.realName || member.memberNo }}</h2>
          <p>{{ member.memberNo }} · {{ GENDER_MAP[member.gender] || '未知' }} · {{ member.phone }}</p>
        </div>
        <div class="info-badge">
          <el-tag :type="member.status === 1 ? 'success' : 'danger'" size="large">{{ MEMBER_STATUS_MAP[member.status] }}</el-tag>
          <el-tag type="warning" size="large" style="margin-left: 8px;">{{ member.levelName || '普通会员' }}</el-tag>
        </div>
      </div>
      <div class="stats-row">
        <div class="stat-item">
          <span class="stat-label">余额</span>
          <span class="stat-value">¥{{ formatMoney(member.balance) }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">累计充值</span>
          <span class="stat-value">¥{{ formatMoney(member.totalRecharge) }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">累计消费</span>
          <span class="stat-value">¥{{ formatMoney(member.totalConsumption) }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">可用积分</span>
          <span class="stat-value">{{ member.availablePoints }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">累计上网</span>
          <span class="stat-value">{{ member.totalOnlineHours || 0 }}小时</span>
        </div>
      </div>
    </div>

    <!-- 详情标签页 -->
    <div class="detail-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="会员卡号">{{ member.memberNo }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ member.realName }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ GENDER_MAP[member.gender] || '未知' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ member.phone }}</el-descriptions-item>
            <el-descriptions-item label="生日">{{ member.birthday || '-' }}</el-descriptions-item>
            <el-descriptions-item label="等级">{{ member.levelName || '普通会员' }}</el-descriptions-item>
            <el-descriptions-item label="注册来源">{{ member.registerSourceLabel || '-' }}</el-descriptions-item>
            <el-descriptions-item label="标签" :span="2">
              <el-input
                v-model="editTag"
                placeholder="请输入标签（如：VIP客户、常客、新会员）"
                clearable
                maxlength="50"
                show-word-limit
                style="max-width: 400px;"
              />
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatDate(member.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="最后上机">{{ formatDate(member.lastOnlineTime) }}</el-descriptions-item>
          </el-descriptions>
          <div style="margin-top: 16px; text-align: right;">
            <el-button
              type="primary"
              :disabled="!tagDirty"
              :loading="saving"
              @click="handleSaveTag"
            >保存标签</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="充值记录" name="recharge">
          <el-table :data="rechargeRecords" v-loading="rechargeLoading" stripe>
            <el-table-column prop="rechargeNo" label="充值单号" width="180" />
            <el-table-column label="充值金额" width="120">
              <template #default="{ row }">¥{{ formatMoney(row.rechargeAmount) }}</template>
            </el-table-column>
            <el-table-column label="赠送金额" width="120">
              <template #default="{ row }">¥{{ formatMoney(row.bonusAmount) }}</template>
            </el-table-column>
            <el-table-column label="到账金额" width="120">
              <template #default="{ row }">¥{{ formatMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column label="支付方式" width="100">
              <template #default="{ row }">{{ PAYMENT_METHOD_MAP[row.paymentMethod] || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ RECHARGE_STATUS_MAP[row.status] || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="充值时间" width="170">
              <template #default="{ row }">{{ formatDate(row.paidAt || row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination v-model:current-page="rechargePage" :total="rechargeTotal" :page-size="10" layout="total, prev, pager, next" background @update:current-page="fetchRechargeRecords" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="积分记录" name="points">
          <el-table :data="pointsRecords" v-loading="pointsLoading" stripe>
            <el-table-column label="积分变动" width="120">
              <template #default="{ row }">
                <span :style="{ color: row.points > 0 ? '#22c55e' : '#ef4444' }">{{ row.points > 0 ? '+' : '' }}{{ row.points }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="balanceBefore" label="变动前" width="100" />
            <el-table-column prop="balanceAfter" label="变动后" width="100" />
            <el-table-column label="类型" width="120">
              <template #default="{ row }">{{ POINTS_BIZ_TYPE_MAP[row.bizType] || '-' }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination v-model:current-page="pointsPage" :total="pointsTotal" :page-size="10" layout="total, prev, pager, next" background @update:current-page="fetchPointsRecords" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 16px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.info-card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); margin-bottom: 16px; }
.info-header { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; }
.avatar { width: 56px; height: 56px; border-radius: 50%; background: linear-gradient(135deg, #3b82f6, #1d4ed8); color: white; display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: 700; flex-shrink: 0; }
.info-main h2 { margin: 0 0 4px; font-size: 20px; color: #1e293b; }
.info-main p { margin: 0; font-size: 14px; color: #64748b; }
.info-badge { margin-left: auto; }
.stats-row { display: flex; gap: 32px; border-top: 1px solid #e2e8f0; padding-top: 16px; }
.stat-item { display: flex; flex-direction: column; }
.stat-label { font-size: 13px; color: #94a3b8; margin-bottom: 4px; }
.stat-value { font-size: 18px; font-weight: 700; color: #1e293b; }
.detail-card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }
</style>
