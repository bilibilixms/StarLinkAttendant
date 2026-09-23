/**
 * 通用异步数据加载。
 *
 * 统一管理 loading / empty / error / success 四态，
 * 页面只要拿 state 驱动「加载中 / 空状态 / 错误重试 / 正常内容」即可，
 * 避免每个页面各写一套、也避免出现白屏。
 */
import { ref, type Ref } from 'vue'
import type { LoadState } from '@/types/api'

export interface UseAsyncDataOptions<T> {
  /** 是否在调用时立刻执行，默认 false（由页面 onLoad 决定时机） */
  immediate?: boolean
  /** 自定义「是否为空」的判断，默认按数组 length / null 判断 */
  isEmpty?: (data: T) => boolean
  /** 出错时的提示文案 */
  errorText?: string
}

export interface UseAsyncDataReturn<T> {
  data: Ref<T | null>
  state: Ref<LoadState>
  error: Ref<string>
  loading: Ref<boolean>
  load: () => Promise<T | null>
  /** 下拉刷新用：不切到 loading 态，避免内容闪烁 */
  refresh: () => Promise<T | null>
}

function defaultIsEmpty(data: unknown): boolean {
  if (data === null || data === undefined) return true
  if (Array.isArray(data)) return data.length === 0
  if (typeof data === 'object') {
    const obj = data as Record<string, unknown>
    if (Array.isArray(obj.records)) return obj.records.length === 0
    if (Array.isArray(obj.list)) return obj.list.length === 0
  }
  return false
}

export function useAsyncData<T>(
  fetcher: () => Promise<T>,
  options: UseAsyncDataOptions<T> = {}
): UseAsyncDataReturn<T> {
  const data = ref<T | null>(null) as Ref<T | null>
  const state = ref<LoadState>('loading')
  const error = ref('')

  const isEmpty = options.isEmpty ?? (defaultIsEmpty as (d: T) => boolean)

  async function run(showLoading: boolean): Promise<T | null> {
    if (showLoading) state.value = 'loading'
    error.value = ''
    try {
      const result = await fetcher()
      data.value = result
      state.value = isEmpty(result) ? 'empty' : 'success'
      return result
    } catch (e) {
      error.value = (e as Error)?.message || options.errorText || '加载失败，请稍后重试'
      // 静默失败场景（例如未登录时拉 summary 返回 401）不该把整页打成错误态
      state.value = data.value === null ? 'error' : 'success'
      return data.value
    }
  }

  function load(): Promise<T | null> {
    return run(true)
  }

  function refresh(): Promise<T | null> {
    return run(false)
  }

  if (options.immediate) void load()

  return { data, state, error, loading: ref(false), load, refresh }
}
