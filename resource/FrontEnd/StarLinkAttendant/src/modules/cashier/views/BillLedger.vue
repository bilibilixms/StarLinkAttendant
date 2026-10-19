<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getBillDetail, getBillList, getOrderDetail } from '../api'
import { PAYMENT_METHOD_MAP } from '@/common/constants'
import { formatDate } from '@/common/utils/date'
import { formatMoney } from '@/common/utils/money'
import type { BillCategory, BillRecord, OrderDetail } from '../types'

const categoryLabels: Record<BillCategory, string> = {
  PRODUCT: '商品订单',
  SESSION: '上机账单',
  RECHARGE: '会员充值',
}
const statusLabels: Record<number, string> = {
  0: '待支付', 1: '已支付', 2: '部分退款', 3: '已退款',
  4: '已取消', 5: '部分支付', 6: '支付失败',
}
const statusTypes: Record<number, 'success' | 'warning' | 'danger' | 'info'> = {
  0: 'warning', 1: 'success', 2: 'warning', 3: 'info',
  4: 'info', 5: 'warning', 6: 'danger',
}

const loading = ref(false)
const records = ref<BillRecord[]>([])
const total = ref(0)
const query = reactive({
  page: 1, size: 10,
  category: undefined as BillCategory | undefined,
  billNo: '',
  memberId: undefined as number | undefined,
  status: undefined as number | undefined,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
})
const memberIdInput = ref('')
const dateRange = ref<[string, string] | null>(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<BillRecord | null>(null)
const orderDetail = ref<OrderDetail | null>(null)

async function fetchRecords() {
  loading.value = true
  try {
    const response = await getBillList(query)
    records.value = response.data.records || []
    total.value = response.data.total || 0
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  const memberId = memberIdInput.value.trim()
  if (memberId && !/^[1-9]\d*$/.test(memberId)) {
    ElMessage.warning('会员 ID 必须为正整数')
    return
  }
  query.memberId = memberId ? Number(memberId) : undefined
  query.startDate = dateRange.value?.[0]
  query.endDate = dateRange.value?.[1]
  query.page = 1
  fetchRecords()
}

function reset() {
  query.category = undefined
  query.billNo = ''
  query.memberId = undefined
  query.status = undefined
  query.startDate = undefined
  query.endDate = undefined
  memberIdInput.value = ''
  dateRange.value = null
  query.page = 1
  fetchRecords()
}

async function viewDetail(row: BillRecord) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  orderDetail.value = null
  try {
    const response = await getBillDetail(row.source, row.id)
    detail.value = response.data
    if (row.source === 'ORDER') {
      const order = await getOrderDetail(row.id)
      orderDetail.value = order.data
    }
  } catch {
    detail.value = null
    ElMessage.error('账单详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

onMounted(fetchRecords)
</script>

<template>
  <div class="bill-page">
    <div class="page-header">
      <h1>账单管理</h1>
      <p>查询收银订单、上机结算与会员充值记录。充值实付与赠送金额分别展示。</p>
    </div>

    <div class="card filters">
      <el-form inline @submit.prevent="search">
        <el-form-item label="来源">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, category) in categoryLabels" :key="category" :label="label" :value="category" />
          </el-select>
        </el-form-item>
        <el-form-item label="单号">
          <el-input v-model="query.billNo" placeholder="订单号 / 账单号" clearable style="width: 190px" />
        </el-form-item>
        <el-form-item label="会员 ID">
          <el-input v-model="memberIdInput" placeholder="全部会员" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, status) in statusLabels" :key="status" :label="label" :value="Number(status)" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建日期">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card table-card">
      <el-table :data="records" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="billNo" label="单号" min-width="190" />
        <el-table-column label="来源" width="115">
          <template #default="{ row }">{{ categoryLabels[row.category as BillCategory] }}</template>
        </el-table-column>
        <el-table-column label="会员" min-width="100">
          <template #default="{ row }">{{ row.memberName || (row.memberId ? `#${row.memberId}` : '散客') }}</template>
        </el-table-column>
        <el-table-column label="应付" width="105" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.payableAmount) }}</template>
        </el-table-column>
        <el-table-column label="实付" width="105" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column label="已退" width="105" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.refundAmount) }}</template>
        </el-table-column>
        <el-table-column label="净额" width="105" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.netAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="105" align="center">
          <template #default="{ row }"><el-tag :type="statusTypes[row.status] || 'info'" size="small">{{ statusLabels[row.status] || '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="创建时间" width="165">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="viewDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size"
          :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
          background @update:current-page="fetchRecords" @update:page-size="query.page = 1; fetchRecords()" />
      </div>
    </div>

    <el-dialog v-model="detailVisible" title="账单详情" width="760px" v-loading="detailLoading">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="单号">{{ detail.billNo }}</el-descriptions-item>
          <el-descriptions-item label="类别">{{ categoryLabels[detail.category] }}</el-descriptions-item>
          <el-descriptions-item label="会员">{{ detail.memberName || (detail.memberId ? `#${detail.memberId}` : '散客') }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabels[detail.status] }}</el-descriptions-item>
          <el-descriptions-item label="原价">¥{{ formatMoney(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="优惠">¥{{ formatMoney(detail.discountAmount) }}</el-descriptions-item>
          <el-descriptions-item label="应付">¥{{ formatMoney(detail.payableAmount) }}</el-descriptions-item>
          <el-descriptions-item label="实付">¥{{ formatMoney(detail.paidAmount) }}</el-descriptions-item>
          <el-descriptions-item label="已退">¥{{ formatMoney(detail.refundAmount) }}</el-descriptions-item>
          <el-descriptions-item label="净额">¥{{ formatMoney(detail.netAmount) }}</el-descriptions-item>
          <el-descriptions-item label="支付方式">{{ detail.paymentMethod ? PAYMENT_METHOD_MAP[detail.paymentMethod] : '-' }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatDate(detail.paidAt) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.remark" label="备注">{{ detail.remark }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions v-if="detail.source === 'SESSION'" title="上机结算" :column="2" border class="detail-section">
          <el-descriptions-item label="会话 ID">{{ detail.sessionId }}</el-descriptions-item>
          <el-descriptions-item label="机位">{{ detail.computerNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="计费时长">{{ detail.billedMinutes ?? 0 }} 分钟</el-descriptions-item>
          <el-descriptions-item label="计费时段">{{ formatDate(detail.billingStart) }} 至 {{ formatDate(detail.billingEnd) }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions v-if="detail.source === 'RECHARGE'" title="充值入账" :column="2" border class="detail-section">
          <el-descriptions-item label="赠送余额">¥{{ formatMoney(detail.bonusAmount) }}</el-descriptions-item>
          <el-descriptions-item label="合计到账">¥{{ formatMoney(Number(detail.payableAmount) + Number(detail.bonusAmount)) }}</el-descriptions-item>
          <el-descriptions-item label="充值前余额">¥{{ formatMoney(detail.balanceBefore) }}</el-descriptions-item>
          <el-descriptions-item label="充值后余额">¥{{ formatMoney(detail.balanceAfter) }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="orderDetail">
          <div class="section-title">订单商品</div>
          <el-table :data="orderDetail.items" border size="small">
            <el-table-column prop="productName" label="商品" min-width="160" />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column label="单价" width="100"><template #default="{ row }">¥{{ formatMoney(row.unitPrice) }}</template></el-table-column>
            <el-table-column label="小计" width="100"><template #default="{ row }">¥{{ formatMoney(row.subtotal) }}</template></el-table-column>
          </el-table>
          <div v-if="orderDetail.refunds?.length" class="section-title">退款记录</div>
          <el-table v-if="orderDetail.refunds?.length" :data="orderDetail.refunds" border size="small">
            <el-table-column prop="refundNo" label="退款单号" min-width="170" />
            <el-table-column label="金额" width="100"><template #default="{ row }">¥{{ formatMoney(row.refundAmount) }}</template></el-table-column>
            <el-table-column prop="statusLabel" label="状态" width="100" />
            <el-table-column prop="refundReason" label="原因" min-width="140" />
          </el-table>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bill-page { width: 100%; }
.page-header { margin-bottom: 20px; }
.page-header h1 { margin: 0; font-size: 24px; color: #1e293b; }
.page-header p { margin: 5px 0 0; color: #64748b; font-size: 14px; }
.card { background: white; border-radius: 12px; padding: 18px 20px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.05); }
.filters { padding-bottom: 0; }
.pagination { display: flex; justify-content: flex-end; margin-top: 16px; }
.detail-section { margin-top: 20px; }
.section-title { margin: 20px 0 10px; color: #334155; font-weight: 600; }
</style>
