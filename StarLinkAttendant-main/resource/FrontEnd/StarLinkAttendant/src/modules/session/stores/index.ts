import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getSeatMap, getSeatStatus } from '../api'
import type { SeatAreaItem, SeatStatusItem } from '../types'

export const useSessionStore = defineStore('session', () => {
  /** 座位图配置（区域 + 机位） */
  const seatAreas = ref<SeatAreaItem[]>([])
  /** 机位实时状态 Map（id → status） */
  const seatStatusMap = ref<Record<number, SeatStatusItem>>({})
  /** 轮询定时器 ID */
  let pollTimer: ReturnType<typeof setInterval> | null = null

  /** 加载座位图配置 */
  async function loadSeatMap() {
    try {
      const res = await getSeatMap()
      seatAreas.value = res.data || []
    } catch {
      seatAreas.value = []
    }
  }

  /** 加载机位实时状态 */
  async function loadSeatStatus() {
    try {
      const res = await getSeatStatus()
      const list: SeatStatusItem[] = res.data || []
      const map: Record<number, SeatStatusItem> = {}
      for (const item of list) {
        map[item.id] = item
      }
      seatStatusMap.value = map
    } catch { /* ignore poll errors */ }
  }

  /** 启动轮询（5 秒间隔） */
  function startPolling() {
    stopPolling()
    loadSeatStatus()
    pollTimer = setInterval(loadSeatStatus, 5000)
  }

  /** 停止轮询 */
  function stopPolling() {
    if (pollTimer !== null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  /** 获取某机位的实时状态 */
  function getSeatStatusById(id: number): SeatStatusItem | undefined {
    return seatStatusMap.value[id]
  }

  return {
    seatAreas,
    seatStatusMap,
    loadSeatMap,
    loadSeatStatus,
    startPolling,
    stopPolling,
    getSeatStatusById,
  }
})
