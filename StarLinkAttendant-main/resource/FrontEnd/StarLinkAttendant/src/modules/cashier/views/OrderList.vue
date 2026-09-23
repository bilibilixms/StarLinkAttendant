<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight, View, RefreshLeft } from '@element-plus/icons-vue'
import { getOrderList, getOrderDetail, refundOrder } from '../api'
import { ORDER_STATUS_MAP, ORDER_TYPE_MAP } from '@/common/constants'
import { formatMoney } from '@/common/utils/money'
import { formatDate } from '@/common/utils/date'
import type { OrderItem, OrderDetail } from '../types'

const loading = ref(false)
const tableData = ref<OrderItem[]>([])
const total = ref(0)
const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const orderDetail = ref<OrderDetail | null>(null)
const refundDialogVisible = ref(false)
const currentOrderId = ref<number>(0)
const refundAmount = ref(0)
const refundReason = ref('')
const activeTab = ref('all')
const refunding = ref(false)

const queryParams = reactive({
  page: 1, size: 10, orderNo: '', status: null as number | null,
  statusList: [1, 2, 3] as number[] | undefined,
  orderType: null as number | null,
  startTime: '', endTime: '',
})
const dateRange = ref<[string, string] | null>(null)

const tabs = [
  { key: 'all', label: '全部', status: null, statusList: [1, 2, 3] as number[] },
  { key: 'paid', label: '已支付', status: 1, statusList: undefined as number[] | undefined },
  { key: 'partial_refund', label: '部分退款', status: 2, statusList: undefined as number[] | undefined },
  { key: 'refunded', label: '已退款', status: 3, statusList: undefined as number[] | undefined },
]

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getOrderList(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { tableData.value = [] }
  finally { loading.value = false }
}

const handleTabChange = (tab: typeof tabs[0]) => {
  activeTab.value = tab.key
  if (tab.statusList) {
    queryParams.statusList = tab.statusList
    queryParams.status = null
  } else {
    queryParams.statusList = undefined
    queryParams.status = tab.status
  }
  queryParams.page = 1
  fetchData()
}

const handleSearch = () => { queryParams.page = 1; fetchData() }
const handleReset = () => {
  queryParams.orderNo = ''; queryParams.status = null; queryParams.orderType = null
  queryParams.startTime = ''; queryParams.endTime = ''
  queryParams.statusList = [1, 2, 3]
  dateRange.value = null
  activeTab.value = 'all'
  handleSearch()
}
const handlePageChange = (page: number) => { queryParams.page = page; fetchData() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchData() }

const handleDateChange = (val: [string, string] | null) => {
  queryParams.startTime = val?.[0] || ''
  queryParams.endTime = val?.[1] || ''
}

const viewDetail = async (row: OrderItem) => {
  detailDialogVisible.value = true
  detailLoading.value = true
  try {
    const res = await getOrderDetail(row.id)
    orderDetail.value = res.data
  } catch (e: any) {
    ElMessage.error('获取订单详情失败：' + (e?.message || '未知错误'))
    orderDetail.value = null
  }
  finally { detailLoading.value = false }
}

const maxRefundable = ref(0)

const handleRefund = async (row: OrderItem) => {
  currentOrderId.value = row.id
  refundReason.value = ''
  try {
    const res = await getOrderDetail(row.id)
    const detail = res.data
    const paid = Number(detail.paidAmount) || 0
    const alreadyRefunded = (detail.refunds || [])
      .filter((r: any) => r.status !== 3) // exclude rejected
      .reduce((s: number, r: any) => s + (Number(r.refundAmount) || 0), 0)
    maxRefundable.value = Math.round((paid - alreadyRefunded) * 100) / 100
    refundAmount.value = maxRefundable.value
  } catch {
    maxRefundable.value = Number(row.paidAmount) || 0
    refundAmount.value = maxRefundable.value
  }
  refundDialogVisible.value = true
}

const confirmRefund = async () => {
  if (refundAmount.value <= 0) { ElMessage.warning('退款金额必须大于0'); return }
  if (refundAmount.value > maxRefundable.value) { ElMessage.warning(`退款金额不能超过最大可退金额 ¥${maxRefundable.value.toFixed(2)}`); return }
  if (refunding.value) return
  refunding.value = true
  try {
    await refundOrder(currentOrderId.value, { refundAmount: refundAmount.value, refundReason: refundReason.value })
    ElMessage.success('退款成功')
    refundDialogVisible.value = false
    fetchData()
  } catch { /* handled */ }
  finally { refunding.value = false }
}

const getStatusType = (status: number): string => {
  const map: Record<number, string> = { 0: 'warning', 1: 'success', 2: '', 3: 'danger', 4: 'info' }
  return map[status] || 'info'
}

const getOrderTypeTag = (type: number) => {
  const map: Record<number, { color: string; label: string }> = {
    1: { color: '#3b82f6', label: '商品' },
    2: { color: '#10b981', label: '上机' },
    3: { color: '#8b5cf6', label: '混合' },
  }
  return map[type] || { color: '#6b7280', label: '其他' }
}

onMounted(() => { fetchData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">订单管理</h1>
      <p class="page-desc">查看和管理所有收银订单</p>
    </div>

    <div class="filter-card">
      <div class="tab-bar">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-btn"
          :class="{ active: activeTab === tab.key }"
          @click="handleTabChange(tab)"
        >
          {{ tab.label }}
        </div>
      </div>
      <el-divider style="margin: 12px 0" />
      <el-form :inline="true" @submit.prevent class="search-form">
        <el-form-item label="订单号">
          <el-input v-model="queryParams.orderNo" placeholder="订单号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.orderType" placeholder="全部" clearable style="width: 100px">
            <el-option v-for="(label, val) in ORDER_TYPE_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" @change="handleDateChange" style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="orderNo" label="订单号" width="180">
          <template #default="{ row }">
            <span class="order-no">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <span class="type-tag" :style="{ background: getOrderTypeTag(row.orderType).color + '18', color: getOrderTypeTag(row.orderType).color }">
              {{ getOrderTypeTag(row.orderType).label }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="会员" width="90">
          <template #default="{ row }">
            <span :class="{ 'text-muted': !row.memberName }">{{ row.memberName || '散客' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="应付" width="95" align="right">
          <template #default="{ row }">
            <span class="amount">¥{{ formatMoney(row.payableAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="light">
              {{ ORDER_STATUS_MAP[row.status] || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :icon="View" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.status === 1 || row.status === 2" type="warning" link size="small" :icon="RefreshLeft" @click="handleRefund(row)">退款</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
      </div>
    </div>

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="订单详情" width="720px" v-loading="detailLoading">
      <template v-if="orderDetail">
        <div class="detail-header">
          <div class="detail-order-no">
            <span class="label">订单号</span>
            <span class="value">{{ orderDetail.orderNo }}</span>
          </div>
          <el-tag :type="getStatusType(orderDetail.status)" effect="light">{{ ORDER_STATUS_MAP[orderDetail.status] }}</el-tag>
        </div>

        <el-descriptions :column="3" border size="small" style="margin-top: 12px">
          <el-descriptions-item label="订单类型">{{ ORDER_TYPE_MAP[orderDetail.orderType] }}</el-descriptions-item>
          <el-descriptions-item label="会员">{{ orderDetail.memberName || '散客' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(orderDetail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="应付金额">¥{{ formatMoney(orderDetail.payableAmount) }}</el-descriptions-item>
          <el-descriptions-item label="优惠金额">¥{{ formatMoney(orderDetail.discountAmount) }}</el-descriptions-item>
          <el-descriptions-item label="实付金额"><span style="color: #ef4444; font-weight: 700">¥{{ formatMoney(orderDetail.paidAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ orderDetail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <div class="section-title">商品明细</div>
          <el-table :data="orderDetail.items" size="small" border>
            <el-table-column prop="productName" label="商品名称" min-width="150" />
            <el-table-column label="类型" width="70" align="center">
              <template #default="{ row }">{{ row.itemType === 1 ? '单品' : '套餐' }}</template>
            </el-table-column>
            <el-table-column label="单价" width="85" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="60" align="center" />
            <el-table-column label="小计" width="90" align="right">
              <template #default="{ row }"><span style="font-weight: 600">¥{{ formatMoney(row.subtotal) }}</span></template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="orderDetail.payment" class="detail-section">
          <div class="section-title">支付记录</div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="支付单号">{{ orderDetail.payment.paymentNo }}</el-descriptions-item>
            <el-descriptions-item label="支付方式">{{ orderDetail.payment.paymentMethodLabel }}</el-descriptions-item>
            <el-descriptions-item label="支付状态">
              <el-tag :type="orderDetail.payment.paymentStatus === 1 ? 'success' : 'warning'" size="small">
                {{ orderDetail.payment.paymentStatusLabel }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="支付金额">
              <span style="color: #10b981; font-weight: 600">¥{{ formatMoney(orderDetail.payment.totalAmount) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="退款金额">
              <span v-if="orderDetail.payment.refundAmount > 0" style="color: #ef4444; font-weight: 600">¥{{ formatMoney(orderDetail.payment.refundAmount) }}</span>
              <span v-else class="text-muted">-</span>
            </el-descriptions-item>
            <el-descriptions-item label="支付时间">{{ formatDate(orderDetail.payment.paidAt) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="orderDetail.refunds && orderDetail.refunds.length > 0" class="detail-section">
          <div class="section-title">退款记录</div>
          <el-table :data="orderDetail.refunds" size="small" border>
            <el-table-column prop="refundNo" label="退款单号" width="180" />
            <el-table-column label="退款金额" width="100" align="right">
              <template #default="{ row }"><span style="color: #ef4444; font-weight: 600">¥{{ formatMoney(row.refundAmount) }}</span></template>
            </el-table-column>
            <el-table-column label="退款类型" width="80" align="center">
              <template #default="{ row }">{{ row.refundTypeLabel }}</template>
            </el-table-column>
            <el-table-column label="退款原因" min-width="150">
              <template #default="{ row }">{{ row.refundReason || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </template>
      <div v-else-if="!detailLoading" style="text-align: center; padding: 40px 0; color: #94a3b8">
        <p style="font-size: 14px">暂无订单详情数据</p>
        <p style="font-size: 12px; margin-top: 4px">请检查浏览器控制台(F12)查看错误信息</p>
      </div>
    </el-dialog>

    <!-- 退款弹窗 -->
    <el-dialog v-model="refundDialogVisible" title="订单退款" width="420px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="退款金额">
          <el-input-number v-model="refundAmount" :min="0.01" :max="maxRefundable" :precision="2" style="width: 100%" />
          <div style="font-size: 12px; color: #94a3b8; margin-top: 4px">最大可退金额：¥{{ maxRefundable.toFixed(2) }}</div>
        </el-form-item>
        <el-form-item label="退款原因">
          <el-input v-model="refundReason" type="textarea" :rows="3" placeholder="请输入退款原因（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="refunding" @click="confirmRefund">确认退款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 20px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.page-desc { font-size: 14px; color: #64748b; margin-top: 4px; }

.filter-card { background: white; border-radius: 12px; padding: 16px 20px 4px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.tab-bar { display: flex; gap: 4px; }
.tab-btn {
  padding: 8px 16px; border-radius: 6px; font-size: 13px; color: #64748b;
  cursor: pointer; transition: all 0.2s; font-weight: 500;
}
.tab-btn:hover { background: #f1f5f9; color: #334155; }
.tab-btn.active { background: #3b82f6; color: white; }

.table-card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.order-no { font-family: 'SF Mono', 'Consolas', monospace; font-size: 12px; color: #475569; }
.type-tag { font-size: 12px; padding: 2px 8px; border-radius: 4px; font-weight: 500; }
.amount { font-weight: 600; color: #1e293b; }
.text-muted { color: #94a3b8; }
.time-text { font-size: 12px; color: #64748b; }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }

.detail-header { display: flex; justify-content: space-between; align-items: center; }
.detail-order-no .label { font-size: 12px; color: #94a3b8; }
.detail-order-no .value { font-family: monospace; font-size: 15px; font-weight: 600; color: #1e293b; }
.detail-section { margin-top: 20px; }
.section-title { font-size: 14px; font-weight: 600; color: #334155; margin-bottom: 8px; padding-left: 8px; border-left: 3px solid #3b82f6; }
</style>
