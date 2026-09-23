/**
 * 应用级状态：当前门店、安全区尺寸、当前上机会话。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { DEFAULT_STORE, STORAGE_KEYS } from '@/config'
import { getStorage, setStorage } from '@/utils/storage'
import { getNavBarHeight, getSafeAreaBottom, getStatusBarHeight } from '@/utils/system'
import * as sessionApi from '@/api/session'
import type { CurrentSession } from '@/types/session'

export interface CurrentStore {
  id: number
  name: string
  shortName: string
  address: string
}

export const useAppStore = defineStore('app', () => {
  const store = ref<CurrentStore>({ ...DEFAULT_STORE })
  const currentSession = ref<CurrentSession | null>(null)
  const sessionLoading = ref(false)

  /** 安全区尺寸只需要取一次 */
  const statusBarHeight = ref(getStatusBarHeight())
  const navBarHeight = ref(getNavBarHeight())
  const safeAreaBottom = ref(getSafeAreaBottom())

  const hasActiveSession = computed(() => !!currentSession.value)

  function restore(): void {
    const cached = getStorage<CurrentStore | null>(STORAGE_KEYS.CURRENT_STORE, null)
    if (cached && cached.id) store.value = cached
  }

  function setStore(next: CurrentStore): void {
    store.value = next
    setStorage(STORAGE_KEYS.CURRENT_STORE, next)
  }

  /** 拉取当前上机会话；未登录时静默返回 null */
  async function fetchCurrentSession(): Promise<CurrentSession | null> {
    sessionLoading.value = true
    try {
      currentSession.value = await sessionApi.getCurrentSession()
    } catch {
      currentSession.value = null
    } finally {
      sessionLoading.value = false
    }
    return currentSession.value
  }

  function setCurrentSession(session: CurrentSession | null): void {
    currentSession.value = session
  }

  return {
    store,
    currentSession,
    sessionLoading,
    statusBarHeight,
    navBarHeight,
    safeAreaBottom,
    hasActiveSession,
    restore,
    setStore,
    fetchCurrentSession,
    setCurrentSession,
  }
})
