/**
 * 认证接口。
 * 后端契约：POST /api/member/login（starlink-module-member.MemberController.loginMember）。
 * 返回 MemberLoginResponse { token, id, memberNo, realName, phone, levelName, totalPoints, balance }。
 */
import { post } from './request'
import { API_PREFIX } from '@/config'
import type { LoginResult, Member } from '@/types/member'

/** 后端 MemberLoginResponse 原始结构 */
interface MemberLoginResponse {
  token: string
  id: number
  memberNo: string
  realName: string | null
  phone: string | null
  levelName: string | null
  totalPoints: number | null
  balance: number | string | null
}

/**
 * 将后端 MemberLoginResponse 映射为前端 LoginResult。
 * 后端未返回的字段（头像/性别/累计数据等）用默认值填充，保证 Member 类型完整。
 */
function mapLoginResult(r: MemberLoginResponse): LoginResult {
  const balance = Number(r.balance ?? 0) || 0
  const totalPoints = Number(r.totalPoints ?? 0) || 0
  const member: Member = {
    id: r.id,
    memberNo: r.memberNo ?? '',
    realName: r.realName ?? '',
    nickName: '',
    gender: 0,
    phone: r.phone ?? '',
    avatar: '',
    birthday: '',
    levelId: 0,
    levelName: r.levelName ?? '普通会员',
    availablePoints: totalPoints,
    totalPoints,
    balance,
    totalRecharge: 0,
    totalConsumption: 0,
    totalOnlineHours: 0,
    status: 1,
    verified: false,
    createdAt: '',
  }
  return { token: r.token, member }
}

/**
 * 会员端登录（手机号 + 密码）。
 * 失败时让 request 层统一 toast，**不要**传 silent: true，避免登录失败后无任何提示。
 */
export function loginMember(phone: string, password: string): Promise<LoginResult> {
  return post<MemberLoginResponse>(`${API_PREFIX}/login`, { phone, password }).then(mapLoginResult)
}
