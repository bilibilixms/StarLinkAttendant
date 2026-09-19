<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, RefreshRight, View, Document, Edit, Plus, Delete, UserFilled, Grid } from '@element-plus/icons-vue'
import { getLogList } from '../api'
import { formatDate } from '@/common/utils/date'
import type { LogItem, LogQuery } from '../types'

/** 操作动作 → 标签/颜色/图标 */
const ACTION_META: Record<string, { label: string; color: string; icon: any }> = {
  create: { label: '新增', color: '#22c55e', icon: Plus },
  update: { label: '修改', color: '#f59e0b', icon: Edit },
  delete: { label: '删除', color: '#ef4444', icon: Delete },
  audit:  { label: '审核', color: '#6366f1', icon: Document },
  login:  { label: '登录', color: '#3b82f6', icon: UserFilled },
}
function getActionMeta(action: string) {
  return ACTION_META[action] || { label: action, color: '#94a3b8', icon: Document }
}

/** 字段名 → 中文标签映射（含驼峰与下划线两种格式） */
const FIELD_LABELS: Record<string, string> = {
  // 通用
  id: 'ID', status: '状态', reason: '原因', description: '描述',
  remark: '备注', title: '标题', content: '内容',
  is_active: '是否启用', isActive: '是否启用',
  // 员工
  real_name: '姓名', realName: '姓名', phone: '手机号',
  position: '职位', employment_type: '用工类型', employmentType: '用工类型',
  employee_no: '工号', employeeNo: '工号', gender: '性别',
  email: '邮箱', address: '地址', nickname: '昵称', avatar: '头像',
  // 会员
  balance: '余额', level_id: '等级', levelId: '等级', points: '积分',
  member_id: '会员ID', memberId: '会员ID',
  // 角色/权限
  role_name: '角色名', roleName: '角色名', role_code: '角色编码', roleCode: '角色编码',
  perm_name: '权限名', permName: '权限名', perm_code: '权限编码', permCode: '权限编码',
  perm_type: '权限类型', permType: '权限类型', icon: '图标',
  route: '路由', sort_order: '排序', sortOrder: '排序',
  // 密码
  password: '密码',
  // 机位/会话
  seat_no: '座位号', seatNo: '座位号', computer_name: '电脑名', computerName: '电脑名',
  seat_id: '机位ID', seatId: '机位ID', computer_status: '机位状态', computerStatus: '机位状态',
  zone: '区域', tariff_id: '资费方案', tariffId: '资费方案',
  auth_method: '认证方式', authMethod: '认证方式',
  started_at: '开始时间', startedAt: '开始时间',
  ended_at: '结束时间', endedAt: '结束时间',
  duration_minutes: '时长(分钟)', durationMinutes: '时长(分钟)',
  // 计费/订单
  amount: '金额', total_amount: '总金额', totalAmount: '总金额',
  discount_amount: '优惠金额', discountAmount: '优惠金额',
  paid_amount: '实付金额', paidAmount: '实付金额',
  pay_type: '支付方式', payType: '支付方式',
  // 商品
  price: '价格', cost_price: '成本价', costPrice: '成本价',
  stock: '库存', category_id: '分类', categoryId: '分类',
  unit: '单位', image_url: '图片', imageUrl: '图片',
  // 班次/日结
  open_at: '开班时间', openAt: '开班时间',
  close_at: '结班时间', closeAt: '结班时间',
  opening_balance: '备用金', openingBalance: '备用金',
  total_income: '总营收', totalIncome: '总营收',
  cash_income: '现金收入', cashIncome: '现金收入',
  cash_expenditure: '现金支出', cashExpenditure: '现金支出',
  // 通知
  notify_type: '通知类型', notifyType: '通知类型',
  is_read: '是否已读', isRead: '是否已读',
  operator_name: '操作人', operatorName: '操作人',
  register_source: '注册来源', registerSource: '注册来源',
}
function getFieldLabel(field: string): string { return FIELD_LABELS[field] || field }

/** 字段值映射：字段名 → { 数值: 标签 }（同时支持驼峰和下划线格式） */
const FIELD_VALUE_MAP: Record<string, Record<string, string>> = {
  status: { '0': '正常', '1': '正常', '2': '冻结', '3': '黑名单', '4': '已注销' },
  employment_type: { '1': '全职', '2': '兼职', '3': '实习' },
  employmentType: { '1': '全职', '2': '兼职', '3': '实习' },
  register_source: { '1': '前台注册', '2': '后台导入' },
  registerSource: { '1': '前台注册', '2': '后台导入' },
  gender: { '0': '未知', '1': '男', '2': '女' },
  is_active: { '0': '否', '1': '是' },
  isActive: { '0': '否', '1': '是' },
  perm_type: { '1': '菜单', '2': '按钮', '3': '接口' },
  permType: { '1': '菜单', '2': '按钮', '3': '接口' },
  notify_type: { '1': '系统通知', '2': '内部通知', '3': '公告' },
  notifyType: { '1': '系统通知', '2': '内部通知', '3': '公告' },
  pay_type: { '1': '现金', '2': '微信', '3': '支付宝', '4': '余额' },
  payType: { '1': '现金', '2': '微信', '3': '支付宝', '4': '余额' },
  auth_method: { '1': '刷卡', '2': '扫码', '3': '人脸', '4': '临时密码' },
  authMethod: { '1': '刷卡', '2': '扫码', '3': '人脸', '4': '临时密码' },
  computer_status: { '0': '空闲', '1': '使用中', '2': '锁定', '3': '维修中', '4': '关机' },
  computerStatus: { '0': '空闲', '1': '使用中', '2': '锁定', '3': '维修中', '4': '关机' },
  session_status: { '0': '上机中', '1': '已暂停', '2': '已下机', '3': '强制下机', '4': '异常中断' },
  is_read: { '0': '未读', '1': '已读' },
  isRead: { '0': '未读', '1': '已读' },
}

/** 按 bizType 覆盖 status 映射 */
const BIZ_STATUS_MAP: Record<string, Record<string, string>> = {
  employee: { '1': '在职', '2': '离职', '3': '停用' },
  member:   { '1': '正常', '2': '冻结', '3': '黑名单', '4': '已注销' },
}

/** 敏感字段（值脱敏显示） */
const SENSITIVE_FIELDS = new Set(['password', 'password_hash', 'secret', 'token'])

function formatValue(val: unknown, field?: string, bizType?: string): string {
  if (val === null || val === undefined) return '空'
  if (typeof val === 'boolean') return val ? '是' : '否'
  if (typeof val === 'object') return JSON.stringify(val)
  const strVal = String(val)
  if (field && SENSITIVE_FIELDS.has(field)) return '••••••'
  if (field && bizType && BIZ_STATUS_MAP[bizType] && field === 'status') {
    return BIZ_STATUS_MAP[bizType][strVal] ?? strVal
  }
  if (field && FIELD_VALUE_MAP[field]) return FIELD_VALUE_MAP[field][strVal] ?? strVal
  return strVal
}

/** 将 detail JSON 解析为 { label, value } 行列表。
 *  兼容旧格式 {field: {old, new}} 和新平铺格式 {field: value}。
 */
function parseDetail(detail: string | null | undefined, bizType?: string): { label: string; value: string }[] {
  if (!detail) return []
  try {
    const obj = JSON.parse(detail)
    const rows: { label: string; value: string }[] = []
    for (const [field, val] of Object.entries(obj)) {
      const label = getFieldLabel(field)
      if (val && typeof val === 'object' && ('old' in val || 'new' in val)) {
        // 旧格式：{old, new} — 仅取 new 值
        const v = val as { new?: unknown }
        rows.push({ label, value: formatValue(v.new, field, bizType) })
      } else {
        rows.push({ label, value: formatValue(val, field, bizType) })
      }
    }
    return rows
  } catch {
    return [{ label: '原始数据', value: detail }]
  }
}

/** 表格列简要展示：仅显示字段数量 */
function detailSummary(detail: string | null | undefined, action?: string): string {
  const rows = parseDetail(detail)
  if (!rows.length) {
    if (action === 'create') return '新增记录'
    if (action === 'delete') return '删除记录'
    return '-'
  }
  if (action === 'create') return `${rows.length} 个字段`
  if (action === 'delete') return `${rows.length} 个字段`
  return `${rows.length} 个字段`
}

// 详情弹窗
const detailDialogVisible = ref(false)
const currentDetail = ref<{ label: string; value: string }[]>([])
const currentRow = ref<LogItem | null>(null)

const showDetail = (row: LogItem) => {
  currentRow.value = row
  currentDetail.value = parseDetail(row.detail, row.bizType)
  detailDialogVisible.value = true
}

const loading = ref(false)
const tableData = ref<LogItem[]>([])
const total = ref(0)
const queryParams = reactive<{ page: number; size: number } & LogQuery>({
  page: 1,
  size: 10,
  operatorName: '',
  bizType: '',
  action: '',
  startTime: '',
  endTime: '',
})

const dateRange = ref<[string, string] | null>(null)

const fetchData = async () => {
  loading.value = true
  try {
    if (dateRange.value) {
      queryParams.startTime = dateRange.value[0]
      queryParams.endTime = dateRange.value[1]
    } else {
      queryParams.startTime = ''
      queryParams.endTime = ''
    }
    const res = await getLogList(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { queryParams.page = 1; fetchData() }
const handleReset = () => {
  queryParams.operatorName = ''
  queryParams.bizType = ''
  queryParams.action = ''
  dateRange.value = null
  queryParams.startTime = ''
  queryParams.endTime = ''
  handleSearch()
}
const handlePageChange = (page: number) => { queryParams.page = page; fetchData() }
const handleSizeChange = (size: number) => { queryParams.size = size; queryParams.page = 1; fetchData() }

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">操作日志</h1>
      <p class="page-desc">系统操作审计日志查询</p>
    </div>

    <div class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="操作人">
          <el-input v-model="queryParams.operatorName" placeholder="请输入操作人" clearable />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input v-model="queryParams.bizType" placeholder="请输入业务类型" clearable />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column prop="bizTypeLabel" label="业务类型" width="110">
          <template #default="{ row }">
            <span>{{ row.bizTypeLabel || row.bizType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="actionLabel" label="操作" width="120" />
        <el-table-column prop="detail" label="详情" min-width="140">
          <template #default="{ row }">
            <span class="detail-cell" @click.stop="showDetail(row)">
              <el-tag :color="getActionMeta(row.action).color" effect="dark" size="small" round style="border:none; color:#fff; font-size:12px">
                {{ detailSummary(row.detail, row.action) }}
              </el-tag>
              <el-icon class="detail-view-icon"><View /></el-icon>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP地址" width="140" />
        <el-table-column label="操作时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
      </div>
    </div>
    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="" width="620px" destroy-on-close :show-close="true">
      <template #header>
        <div class="detail-dialog-header">
          <div class="detail-header-left">
            <span class="detail-header-title">操作详情</span>
            <el-tag v-if="currentRow" :color="getActionMeta(currentRow.action).color" effect="dark" size="small" round style="border:none; color:#fff">
              {{ getActionMeta(currentRow.action).label }}
            </el-tag>
            <el-tag v-if="currentRow" type="info" size="small" round>
              {{ currentRow.bizTypeLabel || currentRow.bizType }}
            </el-tag>
          </div>
          <span v-if="currentRow?.bizId" class="detail-header-id">#{{ currentRow.bizId }}</span>
        </div>
      </template>
      <div v-if="currentRow" class="detail-dialog">
        <!-- 元信息 -->
        <div class="detail-meta-bar">
          <div class="meta-item">
            <el-icon :size="14" color="#64748b"><UserFilled /></el-icon>
            <span>{{ currentRow.operatorName || '系统' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">IP</span>
            <span>{{ currentRow.ipAddress || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">时间</span>
            <span>{{ formatDate(currentRow.createdAt) }}</span>
          </div>
        </div>

        <!-- 记录信息表格 -->
        <div v-if="currentDetail.length" class="detail-record-section">
          <div class="detail-table-title">
            <el-icon :size="15"><Grid /></el-icon>
            <span>{{ (currentRow.bizTypeLabel || currentRow.bizType) }} 记录信息</span>
          </div>
          <table class="detail-record-table">
            <thead>
              <tr>
                <th class="col-field">字段</th>
                <th class="col-value">值</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, idx) in currentDetail" :key="idx">
                <td class="col-field">{{ item.label }}</td>
                <td class="col-value">{{ item.value }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <el-empty v-else description="无记录详情" :image-size="60" />
      </div>
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
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; }

.detail-cell {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.detail-cell:hover .detail-view-icon { opacity: 1; }
.detail-view-icon { flex-shrink: 0; font-size: 14px; opacity: 0.4; color: #3b82f6; transition: opacity .15s; }

/* 弹窗 header */
.detail-dialog-header {
  display: flex; align-items: center; justify-content: space-between; width: 100%;
}
.detail-header-left { display: flex; align-items: center; gap: 8px; }
.detail-header-title { font-size: 16px; font-weight: 700; color: #1e293b; }
.detail-header-id { font-size: 13px; color: #94a3b8; font-family: monospace; }

.detail-dialog { display: flex; flex-direction: column; gap: 16px; }

/* 元信息条 */
.detail-meta-bar {
  display: flex; gap: 20px; padding: 10px 14px;
  background: #f8fafc; border-radius: 8px; border: 1px solid #e2e8f0;
}
.meta-item { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #475569; }
.meta-label { color: #94a3b8; font-size: 12px; }

/* 记录信息表格 */
.detail-record-section { display: flex; flex-direction: column; gap: 8px; }
.detail-table-title {
  display: flex; align-items: center; gap: 6px;
  font-weight: 600; font-size: 14px; color: #334155;
}
.detail-record-table {
  width: 100%; border-collapse: collapse; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;
}
.detail-record-table thead th {
  background: #f1f5f9; font-size: 13px; font-weight: 600; color: #475569;
  padding: 8px 14px; text-align: left; border-bottom: 1px solid #e2e8f0;
}
.detail-record-table tbody tr { transition: background .12s; }
.detail-record-table tbody tr:hover { background: #f8fafc; }
.detail-record-table tbody td {
  padding: 8px 14px; font-size: 13px; border-bottom: 1px solid #f1f5f9;
}
.detail-record-table tbody tr:last-child td { border-bottom: none; }
.col-field { width: 130px; color: #64748b; font-weight: 500; white-space: nowrap; }
.col-value { color: #1e293b; word-break: break-all; }
</style>
