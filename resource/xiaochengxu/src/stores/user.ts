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

  /**
   * 从后端拉取「当前登录者」的最新会员信息并同步到 Pinia + storage。
   *
   * 用途：后台管理员给该会员充值后，会员端无需退出登录即可看到最新余额。
   * 行为约束：
   * - 只做<b>合并</b>更新：后端未返回的字段（头像/昵称/生日等）保持登录时的值不变；
   * - 值未变化时不写回，避免无意义的响应式更新与 UI 闪烁；
   * - 出错不抛出（轮询调用方不该被异常打断）；401 由 request 层统一清理登录态，
   *   清理后 isLogin 变 false，轮询会自行停止，不会形成死循环。
   *
   * @returns 是否发生了实际变化
   */
  async function refreshMember(): Promise<boolean> {
    if (!token.value || !member.value) return false
    try {
      const profile = await authApi.getMemberProfile()
      // 员工令牌或后端未返回会员段时无需刷新
      if (!profile) return false

      const current = member.value
      const next: Member = {
        ...current,
        // 仅用后端权威字段覆盖；其余保持登录时的本地值
        id: profile.id || current.id,
        memberNo: profile.memberNo || current.memberNo,
        realName: profile.realName || current.realName,
        phone: profile.phone || current.phone,
        levelId: profile.levelId || current.levelId,
        levelName: profile.levelName || current.levelName,
        balance: profile.balance,
        availablePoints: profile.availablePoints,
        totalPoints: profile.totalPoints,
        totalRecharge: profile.totalRecharge,
        totalConsumption: profile.totalConsumption,
        status: profile.status,
      }

      const changed =
        next.balance !== current.balance ||
        next.availablePoints !== current.availablePoints ||
        next.totalPoints !== current.totalPoints ||
        next.totalRecharge !== current.totalRecharge ||
        next.totalConsumption !== current.totalConsumption ||
        next.levelId !== current.levelId ||
        next.levelName !== current.levelName ||
        next.status !== current.status
      if (!changed) return false

      member.value = next
      setCachedMember(next) // Pinia 与 storage 一起更新，避免下次进入页面又显示旧值
      console.log('[user] 会员信息已刷新: balance=', next.balance, 'points=', next.availablePoints)
      return true
    } catch (e) {
      // 静默失败：不打断页面，也不重试（下一次轮询/onShow 会再试）
      console.warn('[user] 刷新会员信息失败', e)
      return false
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
    levelName,
    nickname,
    avatar,
    verified,
    restore,
    applyLogin,
    loginByPhonePassword,
    patchBalance,
    patchPoints,
    refreshMember,
    clearLocal,
    logout,
  }
})
