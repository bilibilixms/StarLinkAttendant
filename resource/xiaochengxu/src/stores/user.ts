/**
 * 用户状态：token / 会员信息。
 *
 * 登录态既写内存也写 storage，页面刷新后由 restore() 恢复。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { Member, MemberSummary } from '@/types/member'
import {
  clearAuth,
  getCachedMember,
  getToken,
  setCachedMember,
  setToken,
} from '@/utils/storage'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const member = ref<Member | null>(null)
  /** 后端无 summary 接口，永远为 null；保留字段供页面 computed 兼容旧代码 */
  const summary = ref<MemberSummary | null>(null)
  /** 正在登录中，用于按钮防重复点击 */
  const logging = ref(false)

  const isLogin = computed(() => !!token.value && !!member.value)
  const balance = computed(() => member.value?.balance ?? 0)
  const points = computed(() => member.value?.availablePoints ?? 0)
  const growthValue = computed(() => member.value?.growthValue ?? 0)
  const levelName = computed(() => member.value?.levelName ?? '普通会员')
  const nickname = computed(() => member.value?.nickName || member.value?.realName || '会员用户')
  const avatar = computed(() => member.value?.avatar || '')
  const verified = computed(() => member.value?.verified === true)

  /** 从本地缓存恢复登录态（App onLaunch 时调用） */
  function restore(): void {
    const cachedToken = getToken()
    const cachedMember = getCachedMember()
    if (cachedToken && cachedMember) {
      token.value = cachedToken
      member.value = cachedMember
    } else {
      token.value = ''
      member.value = null
    }
  }

  /** 写入登录态（同时落盘），供登录完成后调用 */
  function applyLogin(result: { token: string; member: Member }): void {
    token.value = result.token
    member.value = result.member
    setToken(result.token)
    setCachedMember(result.member)
  }

  /** 会员端登录：手机号 + 密码 → POST /api/member/login */
  async function loginByPhonePassword(phone: string, password: string): Promise<void> {
    if (logging.value) return
    logging.value = true
    try {
      const res = await authApi.loginMember(phone, password)
      applyLogin(res)
    } finally {
      logging.value = false
    }
  }

  /** 直接更新本地余额（充值/下单/下机后立即反映到 UI，避免等待下一次请求） */
  function patchBalance(newBalance: number): void {
    if (member.value) {
      member.value.balance = newBalance
      setCachedMember(member.value)
    }
  }

  function patchPoints(newPoints: number): void {
    if (member.value) {
      member.value.availablePoints = newPoints
      setCachedMember(member.value)
    }
  }

  /** 只清本地登录态，不动服务端（JWT 无状态） */
  function clearLocal(): void {
    token.value = ''
    member.value = null
    summary.value = null
    clearAuth()
  }

  async function logout(): Promise<void> {
    // 后端无 logout 接口，直接清本地
    clearLocal()
  }

  return {
    token,
    member,
    summary,
    logging,
    isLogin,
    balance,
    points,
    growthValue,
    levelName,
    nickname,
    avatar,
    verified,
    restore,
    applyLogin,
    loginByPhonePassword,
    patchBalance,
    patchPoints,
    clearLocal,
    logout,
  }
})
