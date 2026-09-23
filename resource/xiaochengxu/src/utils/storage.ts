import { STORAGE_KEYS } from '@/config'
import type { Member } from '@/types/member'

/** 同步读取本地缓存，读不到或解析失败返回 fallback */
export function getStorage<T>(key: string, fallback: T): T {
  try {
    const raw = uni.getStorageSync(key)
    if (raw === '' || raw === null || raw === undefined) return fallback
    return typeof raw === 'string' ? (JSON.parse(raw) as T) : (raw as T)
  } catch {
    return fallback
  }
}

/** 同步写入本地缓存 */
export function setStorage(key: string, value: unknown): void {
  try {
    uni.setStorageSync(key, JSON.stringify(value))
  } catch (e) {
    console.warn('[storage] 写入失败', key, e)
  }
}

export function removeStorage(key: string): void {
  try {
    uni.removeStorageSync(key)
  } catch {
    /* ignore */
  }
}

/* ==================== 登录态 ==================== */

export function getToken(): string {
  try {
    return uni.getStorageSync(STORAGE_KEYS.TOKEN) || ''
  } catch {
    return ''
  }
}

export function setToken(token: string): void {
  try {
    uni.setStorageSync(STORAGE_KEYS.TOKEN, token)
  } catch {
    /* ignore */
  }
}

export function getCachedMember(): Member | null {
  return getStorage<Member | null>(STORAGE_KEYS.MEMBER, null)
}

export function setCachedMember(member: Member | null): void {
  if (member) setStorage(STORAGE_KEYS.MEMBER, member)
  else removeStorage(STORAGE_KEYS.MEMBER)
}

/** 清空登录态（token 失效 / 退出登录） */
export function clearAuth(): void {
  removeStorage(STORAGE_KEYS.TOKEN)
  removeStorage(STORAGE_KEYS.MEMBER)
}
