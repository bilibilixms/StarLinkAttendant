/**
 * 认证接口。
 * 后端契约：POST /api/member/login（starlink-module-member.MemberController.loginMember）。
 * 返回 MemberLoginResponse { token, id, memberNo, realName, phone, levelName, totalPoints, balance }。
 */
import { get, post } from './request'
import { API_PREFIX } from '@/config'
import type { LoginResult, Member, MemberProfile, MemberStatus } from '@/types/member'

/** 后端 MemberLoginResponse 原始结构 */
interface MemberLoginResponse {
  token: string
  id: number
  memberNo: string
  realName: string | null
  phone: string | null
  levelName: string | null
  totalPoints: number | null
  growthValue: number | null
  balance: number | string | null
}

/**
 * 将后端 MemberLoginResponse 映射为前端 LoginResult。
 * 后端未返回的字段（头像/性别/累计数据等）用默认值填充，保证 Member 类型完整。
 */
function mapLoginResult(r: MemberLoginResponse): LoginResult {
  const balance = Number(r.balance ?? 0) || 0
  const totalPoints = Number(r.totalPoints ?? 0) || 0
  const growthValue = Number(r.growthValue ?? 0) || 0
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
    growthValue,
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

/* ==================== 当前登录者信息（会员端刷新余额） ==================== */

/** 后端 UserInfoResponse 中的会员实时信息段 */
interface MemberInfoPayload {
  id: number
  memberNo: string | null
  realName: string | null
  phone: string | null
  levelId: number | null
  levelName: string | null
  balance: number | string | null
  availablePoints: number | null
  totalPoints: number | null
  totalRecharge: number | string | null
  totalConsumption: number | string | null
  status: number | null
}

/** 后端 UserInfoResponse（员工登录时 member 为 null） */
interface UserInfoPayload {
  id: number
  phone: string | null
  realName: string | null
  member: MemberInfoPayload | null
}

function num(v: number | string | null | undefined, fallback = 0): number {
  const n = Number(v ?? fallback)
  return Number.isFinite(n) ? n : fallback
}

/**
 * 获取「当前登录者」的实时会员信息（GET /api/auth/info）。
 *
 * 复用既有的当前用户接口，而不是新增余额接口：
 * 会员端「我的」页据此拉取最新余额，使后台充值后无需退出登录即可看到。
 * 员工令牌调用时 `member` 为 null，调用方应视为「无需刷新」。
 */
export function getMemberProfile(): Promise<MemberProfile | null> {
  return get<UserInfoPayload>('/api/auth/info').then((res) => {
    if (!res?.member) return null
    const m = res.member
    // 状态按契约收敛到 MemberStatus（未知值按「正常」处理），保证类型与运行期一致
    const status: MemberStatus = m.status === 2 || m.status === 3 ? m.status : 1
    return {
      id: m.id,
      memberNo: m.memberNo ?? '',
      realName: m.realName ?? '',
      phone: m.phone ?? '',
      levelId: m.levelId ?? 0,
      levelName: m.levelName ?? '',
      balance: num(m.balance),
      availablePoints: num(m.availablePoints),
      totalPoints: num(m.totalPoints),
      totalRecharge: num(m.totalRecharge),
      totalConsumption: num(m.totalConsumption),
      status,
    }
  })
}

/**
 * 会员余额消费（小程序自助点餐等场景）。
 * 调真实后端 POST /api/member/balance/consume，从数据库扣减余额并写流水。
 * post 经 unwrap 拆包后直接返回 data（BigDecimal 余额值）。
 * @param amount 消费金额
 * @param bizId 业务单据ID（如订单号），可为空
 * @param remark 备注，可为空
 * @returns 扣减后的余额
 */
export function consumeBalance(
  amount: number,
  bizId?: number,
  remark?: string,
): Promise<number> {
  return post<number | string>(
    `${API_PREFIX}/balance/consume`,
    { amount, bizId, remark },
    { bypassMock: true, silent: true },
  ).then((res) => Number(res))
}
