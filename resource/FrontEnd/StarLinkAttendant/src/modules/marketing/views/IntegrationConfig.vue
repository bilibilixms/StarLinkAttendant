<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMarketingStore } from '../stores'

const store = useMarketingStore()

const auditTestResult = ref<{ success: boolean; message: string } | null>(null)

const getAuditStatusLabel = (uploaded: number) => {
  return uploaded === 1 ? '已上报' : '待上报'
}

const handleGenerateAudit = async () => {
  auditTestResult.value = null
  const count = await store.generateAuditData()
  auditTestResult.value = {
    success: count > 0,
    message: count > 0 ? `已生成 ${count} 条审计数据` : '没有新的上网记录需要生成'
  }
}

const handleUploadAudit = async () => {
  auditTestResult.value = null
  const count = await store.uploadAuditData()
  auditTestResult.value = {
    success: count > 0,
    message: count > 0 ? `已上报 ${count} 条审计数据` : '没有待上报的审计数据'
  }
}

onMounted(() => {
  store.fetchAuditLogs()
  store.fetchPendingSessions()
})
</script>

<template>
  <div class="page-container">
    <div class="form-actions" style="margin-bottom: 16px">
      <button class="btn btn-primary" :disabled="store.loading" @click="handleGenerateAudit">
        <span v-if="store.loading" class="loading-dot"></span>
        {{ store.loading ? '生成中...' : '生成审计数据' }}
      </button>
      <button class="btn btn-secondary" :disabled="store.loading" @click="handleUploadAudit">
        一键上报
      </button>
    </div>

    <div v-if="auditTestResult" class="test-result" :class="auditTestResult.success ? 'result-success' : 'result-error'" style="margin-bottom: 16px">
      <span class="result-text">{{ auditTestResult.message }}</span>
    </div>

    <div class="table-wrapper" style="margin-bottom: 24px">
      <table class="data-table">
        <thead>
          <tr>
            <th>会话ID</th>
            <th>会员ID</th>
            <th>机位ID</th>
            <th>上机时间</th>
            <th>下机时间</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in store.pendingSessions" :key="s.session_id">
            <td>{{ s.session_id }}</td>
            <td>{{ s.member_id }}</td>
            <td>{{ s.computer_id }}</td>
            <td>{{ s.login_time }}</td>
            <td>{{ s.logout_time || '未下机' }}</td>
            <td>{{ s.status === 2 ? '已下机' : s.status === 3 ? '强制下机' : '未知' }}</td>
          </tr>
          <tr v-if="store.pendingSessions.length === 0">
            <td colspan="6" class="empty-row">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th>会员ID</th>
            <th>机位ID</th>
            <th>登录时间</th>
            <th>下机时间</th>
            <th>上报状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in store.auditLogs" :key="record.id">
            <td>{{ record.memberId }}</td>
            <td>{{ record.computerId }}</td>
            <td>{{ record.loginTime }}</td>
            <td>{{ record.logoutTime || '-' }}</td>
            <td>
              <span class="status-badge" :class="record.uploaded === 1 ? 'status-active' : 'status-pending'">
                {{ getAuditStatusLabel(record.uploaded) }}
              </span>
            </td>
          </tr>
          <tr v-if="store.auditLogs.length === 0">
            <td colspan="5" class="empty-row">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.page-container { padding: 20px; }

.form-actions { display: flex; gap: 8px; }

.btn {
  height: 36px;
  padding: 0 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s ease;
  border: none;
}

.btn-primary {
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.btn-primary:disabled { opacity: 0.7; cursor: not-allowed; }

.btn-secondary {
  background: #f1f5f9;
  color: #64748b;
}

.btn-secondary:hover { background: #e2e8f0; }

.test-result {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 14px;
}

.result-success { background: rgba(34, 197, 94, 0.1); color: #22c55e; }
.result-error  { background: rgba(239, 68, 68, 0.1); color: #ef4444; }

.status-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.status-active  { background: rgba(34, 197, 94, 0.1); color: #22c55e; }
.status-pending { background: rgba(245, 158, 11, 0.1); color: #f59e0b; }

.table-wrapper { overflow-x: auto; }

.data-table {
  width: 100%;
  border-collapse: collapse;
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.data-table th {
  text-align: left;
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  background: #f8fafc;
  border-bottom: 2px solid #e2e8f0;
}

.data-table td {
  padding: 12px 16px;
  font-size: 14px;
  color: #1e293b;
  border-bottom: 1px solid #f1f5f9;
}

.data-table tbody tr:hover { background: #f8fafc; }

.empty-row { text-align: center; color: #94a3b8; padding: 40px !important; }

.loading-dot {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }
</style>
