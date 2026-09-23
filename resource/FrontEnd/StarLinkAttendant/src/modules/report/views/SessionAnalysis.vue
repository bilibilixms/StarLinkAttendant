<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useReportStore } from '../stores'

const fmtDate = (d: Date) => d.toISOString().slice(0, 10)

const store = useReportStore()
const queryDate = ref(fmtDate(new Date()))

const fetchAll = () => {
  store.fetchHourlySessions(queryDate.value)
  store.fetchAvgSessionDuration(queryDate.value)
  store.fetchPeakHours(queryDate.value)
}

const maxSessionCount = computed(() => {
  if (!store.hourlySessions.length) return 1
  return Math.max(...store.hourlySessions.map(s => s.sessionCount))
})

const fullHourlyData = computed(() => {
  const data = Array.from({ length: 24 }, (_, i) => {
    const found = store.hourlySessions.find(s => s.hour === i)
    return { hour: i, sessionCount: found ? found.sessionCount : 0 }
  })
  return data
})

const peakData = computed(() => {
  if (!store.peakHours.length) return []
  return [...store.peakHours].sort((a, b) => b.activeCount - a.activeCount)
})

const formatHour = (h: number) => `${String(h).padStart(2, '0')}:00`

onMounted(fetchAll)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">上机分析</h1>
      <div class="header-actions">
        <el-date-picker
          v-model="queryDate"
          type="date"
          placeholder="选择日期"
          value-format="YYYY-MM-DD"
          style="width: 180px"
          @change="fetchAll"
        />
      </div>
    </div>

    <div class="detail-grid">
      <!-- 平均上机时长 -->
      <div class="page-card">
        <h3 class="card-title">平均上机时长</h3>
        <div class="big-stat">
          <span class="big-number">{{ store.avgDuration?.avgDurationMinutes ?? '--' }}</span>
          <span class="big-unit">分钟</span>
        </div>
      </div>

      <!-- 高峰时段 Top5 -->
      <div class="page-card">
        <h3 class="card-title">高峰时段 Top 5</h3>
        <div class="peak-list">
          <div
            v-for="(item, idx) in peakData.slice(0, 5)"
            :key="item.hour"
            class="peak-row"
          >
            <span class="peak-rank" :class="'rank-' + (idx + 1)">{{ idx + 1 }}</span>
            <span class="peak-hour">{{ formatHour(item.hour) }} ~ {{ formatHour(item.hour + 1) }}</span>
            <span class="peak-count">{{ item.activeCount }} 人</span>
          </div>
          <div v-if="!peakData.length" class="empty-hint">暂无数据</div>
        </div>
      </div>
    </div>

    <!-- 24小时分布图 -->
    <div class="page-card">
      <h3 class="card-title">24小时上机分布</h3>
      <div class="hourly-chart">
        <div class="hourly-bars">
          <div
            v-for="item in fullHourlyData"
            :key="item.hour"
            class="hourly-column"
          >
            <div class="hourly-count">{{ item.sessionCount || '' }}</div>
            <div
              class="hourly-bar"
              :style="{ height: maxSessionCount > 0 ? ((item.sessionCount / maxSessionCount) * 100) + '%' : '0%' }"
              :class="{ 'is-peak': item.sessionCount > maxSessionCount * 0.7 }"
            ></div>
            <span class="hourly-label">{{ formatHour(item.hour) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.header-actions { display: flex; gap: 12px; }

.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin-bottom: 24px; }

.page-card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05); }
.card-title { font-size: 16px; font-weight: 600; color: #1e293b; margin: 0 0 20px; }

.big-stat { text-align: center; padding: 20px; }
.big-number { font-size: 48px; font-weight: 700; color: #3b82f6; }
.big-unit { font-size: 16px; color: #64748b; margin-left: 8px; }

.peak-list { display: flex; flex-direction: column; gap: 10px; }
.peak-row { display: flex; align-items: center; gap: 12px; padding: 8px 12px; background: #f8fafc; border-radius: 8px; }
.peak-rank { width: 24px; height: 24px; border-radius: 6px; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; color: white; }
.rank-1 { background: #f59e0b; }
.rank-2 { background: #94a3b8; }
.rank-3 { background: #d97706; }
.rank-4, .rank-5 { background: #cbd5e1; color: #64748b; }
.peak-hour { flex: 1; font-size: 14px; color: #1e293b; }
.peak-count { font-size: 14px; font-weight: 600; color: #3b82f6; }
.empty-hint { text-align: center; color: #94a3b8; font-size: 14px; padding: 20px; }

.hourly-chart { padding: 10px 0; }
.hourly-bars { display: flex; align-items: flex-end; gap: 3px; height: 200px; }
.hourly-column { flex: 1; display: flex; flex-direction: column; align-items: center; min-width: 0; }
.hourly-count { font-size: 9px; color: #94a3b8; margin-bottom: 2px; min-height: 12px; }
.hourly-bar { width: 100%; max-width: 40px; background: #e2e8f0; border-radius: 3px 3px 0 0; min-height: 2px; transition: height 0.3s; }
.hourly-bar.is-peak { background: #f59e0b; }
.hourly-label { font-size: 10px; color: #94a3b8; margin-top: 4px; }

@media (max-width: 768px) { .detail-grid { grid-template-columns: 1fr; } }
</style>
