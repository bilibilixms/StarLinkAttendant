/**
 * 统一请求封装。
 *
 * 设计要点：
 * 1. 后端统一响应为 { code, message, data, timestamp }，code === 0 表示成功，
 *    与 BackEnd/starlink-common/.../response/ApiResponse.java 完全对齐。
 * 2. 业务层拿到的永远是「拆包后的 data」，不需要层层判断 code。
 * 3. 混合模式：VITE_USE_MOCK=true 时默认走本地 mock，但会员端真实接口
 *    （/api/member/login、/register、/recharge、/{id}/recharge-records、/{id}/points-records）
 *    由 isMemberEndpoint() 识别后穿透 mock 层，直接打真实后端。
 * 4. 401 统一清登录态并回登录页；其他错误码统一 toast 并 reject。
 */
import type { ApiResponse, RequestOptions } from '@/types/api'
import { getToken, clearAuth } from '@/utils/storage'
import { toastError, showLoading, hideLoading } from '@/utils/ui'
import { LOGIN_PAGE } from '@/utils/nav'
import { mockRequest } from '@/mock'
// 静态 import（勿改回动态 import()）：
// 微信小程序（mp-weixin）构建不支持此处使用动态 import —— 编译器会把
// import('@/stores/user') 编译成一个「字符串字面量」，运行时报
// TypeError: "../stores/user.js".then is not a function。
// 静态 import 会被编译为顶部 require()，且对命名导出的引用改写为「调用时属性访问」，
// 因此即使存在 stores/user → api/auth → api/request → stores/user 的循环依赖也安全。
import { useUserStore } from '@/stores/user'

const BASE_URL = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '')

/** 是否使用本地 mock 数据层（.env.development / .env.production 控制） */
export const USE_MOCK = String(import.meta.env.VITE_USE_MOCK ?? 'true').toLowerCase() !== 'false'

let redirectingToLogin = false
let modeLogged = false

/**
 * 判断 URL 是否为已对接后端的会员端接口。
 * 命中的请求即使在 USE_MOCK=true 时也会穿透 mock 层，直接打真实后端。
 */
function isMemberEndpoint(url: string): boolean {
  // 去掉 query 后再匹配
  const u = url.split('?')[0]
  return (
    u === '/api/member/login' ||
    u === '/api/member/register' ||
    u === '/api/member/recharge' ||
    // 充值试算（只读预览）：同样走真实后端，避免页面显示与实际入账不一致
    u === '/api/member/recharge/preview' ||
    // 小程序商品：自助点餐列表/分类/详情 + 热门列表/热门详情，均穿透 mock 直达真实后端商品表
    u === '/api/member/products' ||
    u === '/api/member/products/categories' ||
    /^\/api\/member\/products\/\d+$/.test(u) ||
    u === '/api/member/products/hot' ||
    /^\/api\/member\/products\/hot\/\d+$/.test(u) ||
    // 小程序机位查询：座位图 / 区域摘要，穿透 mock 直达真实后端机位表
    u === '/api/member/seats/areas' ||
    u === '/api/member/seats/map' ||
    // 小程序自助上机：一键开机 / 当前会话 / 自助下机 / 临时下机 / 恢复 / 记录，
    // 穿透 mock 直达真实后端 session 表，下机费用由后端实算并扣减 member 表余额
    /^\/api\/member\/session\//.test(u) ||
    // 当前登录者信息：走真实后端（返回实际登录的会员）
    u === '/api/auth/info' ||
    /^\/api\/member\/\d+\/recharge-records$/.test(u) ||
    /^\/api\/member\/\d+\/points-records$/.test(u)
  )
}

/** 启动时打印一次当前数据源，避免出现「以为在联调其实在 mock」的误会 */
export function initApiMode(): void {
  if (modeLogged) return
  modeLogged = true
  if (USE_MOCK) {
    console.log(
      '%c[StarLink] 数据源 = 混合模式%c  (VITE_USE_MOCK=true)\n' +
        '会员端登录/充值/积分接口走真实后端 /api/member/**，其他业务走本地 mock。',
      'color:#5B5BD6;font-weight:bold',
      'color:#8A8A99'
    )
  } else {
    console.log(
      `%c[StarLink] 数据源 = 真实后端%c  ${BASE_URL || '(同源)'}/api/member/**`,
      'color:#22C55E;font-weight:bold',
      'color:#8A8A99'
    )
  }
}

function buildUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) return url
  return `${BASE_URL}${url}`
}

/**
 * token 失效统一处理：清登录态 + 回登录页（加锁防止并发请求重复跳转）。
 *
 * 必须连 store 的内存态一起清 —— 只清 storage 会让 userStore 仍然认为
 * 「已登录」（isLogin 为 true），跳到登录页后用户点「暂不登录，先逛逛」
 * 返回业务页会再次 401，陷入死循环。
 *
 * store 用动态 import 引入：静态 import 会形成
 * stores/user → api/auth → api/request → stores/user 的循环 chunk（rollup 会告警），
 * 且初始化顺序不可控。这里先清 storage 兜底，再补清内存态。
 */
function handleUnauthorized(): void {
  // 本函数必须是「尽力而为、绝不抛异常」的：
  // 它是在 uni.request 的 success 回调里被同步调用的，一旦抛错，
  // 紧随其后的 reject() 就永远不会执行 → Promise 永不 settle → 页面永久卡在「支付中...」。
  // （历史故障：动态 import 在 mp-weixin 下报 TypeError，正是由此导致卡死。）
  console.warn('[api] handleUnauthorized：登录态失效，清理并跳转登录页')

  try {
    clearAuth()
  } catch (e) {
    console.warn('[api] clearAuth 失败（忽略）', e)
  }

  try {
    // 同步清内存态：静态 import 在 mp-weixin 下编译为顶部 require()，
    // 此处为「调用时属性访问」，循环依赖下同样安全。
    useUserStore().clearLocal()
  } catch (e) {
    // 兜底：storage 已清，内存态随下次冷启动消失
    console.warn('[api] 清理内存登录态失败（忽略）', e)
  }

  if (redirectingToLogin) return
  redirectingToLogin = true
  try {
    toastError('登录状态已失效，请重新登录')
  } catch (e) {
    console.warn('[api] 提示失败（忽略）', e)
  }
  setTimeout(() => {
    redirectingToLogin = false
    try {
      uni.reLaunch({ url: LOGIN_PAGE })
    } catch (e) {
      console.warn('[api] 跳转登录页失败（忽略）', e)
    }
  }, 1200)
}

/** 拆响应信封，把业务失败转成 reject */
function unwrap<T>(res: ApiResponse<T>, options: RequestOptions): T {
  if (res && res.code === 0) return res.data

  const code = res?.code ?? -1
  const message = res?.message || '请求失败'

  if (code === 401) {
    // 先保证「一定会 throw」把请求结束掉；handleUnauthorized 已保证不抛异常
    handleUnauthorized()
  } else if (!options.silent) {
    toastError(message)
  }
  throw new Error(message)
}

/** 发起原始请求，返回完整信封（未拆包） */
function rawRequest<T>(options: RequestOptions, token: string): Promise<ApiResponse<T>> {
  const { url, method = 'GET', data, header = {}, timeout = 15000 } = options

  // 诊断日志：只记录方法/URL/状态码，绝不记录 Authorization、token、密码等敏感信息
  console.log(`[api] → ${method} ${url}`)

  return new Promise<ApiResponse<T>>((resolve, reject) => {
    uni.request({
      url: buildUrl(url),
      method,
      data: (data ?? {}) as Record<string, unknown>,
      timeout,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...header,
      },
      success: (res) => {
        const status = res.statusCode
        console.log(`[api] ← ${status} ${method} ${url}`)
        if (status >= 200 && status < 300) {
          resolve(res.data as ApiResponse<T>)
          return
        }
        if (status === 401) {
          // 关键顺序：先 reject 结束请求，再清理登录态。
          // 若先调用 handleUnauthorized 且它抛异常，reject 将永不执行 → 页面永久 loading。
          console.warn(`[api] 401 → handleUnauthorized：${method} ${url}`)
          reject(new Error('登录状态已失效'))
          handleUnauthorized()
          return
        }
        // 后端未按统一结构返回（如 500 白页），给出可读提示
        const body = res.data as ApiResponse<T> | undefined
        const message = body?.message || `服务异常 (HTTP ${status})`
        if (!options.silent) toastError(message)
        reject(new Error(message))
      },
      fail: (err) => {
        const message = /timeout/i.test(String(err?.errMsg))
          ? '网络超时，请稍后重试'
          : '网络连接失败，请检查网络'
        if (!options.silent) toastError(message)
        reject(new Error(message))
      },
    })
  })
}

/**
 * 统一请求入口。成功时 resolve 拆包后的 data，失败时 reject Error。
 * 调用方务必 catch，或使用 api 层封装好的方法。
 */
export function request<T = unknown>(options: RequestOptions): Promise<T> {
  const loading = options.loading === true
  if (loading) showLoading()

  const token = options.auth === false ? '' : getToken()

  // 会员端真实接口穿透 mock 层，直接打后端（isMemberEndpoint 命中或显式 bypassMock）
  const useMock = USE_MOCK && !isMemberEndpoint(options.url) && !options.bypassMock

  const task: Promise<ApiResponse<T>> = useMock
    ? mockRequest<T>({
        url: options.url,
        method: options.method ?? 'GET',
        data: (options.data ?? {}) as Record<string, unknown>,
        auth: options.auth !== false,
        token,
      })
    : rawRequest<T>(options, token)

  return task
    .then((res) => unwrap<T>(res, options))
    .finally(() => {
      if (loading) hideLoading()
    })
}

/* ==================== 便捷方法 ==================== */

export function get<T = unknown>(
  url: string,
  data?: Record<string, unknown>,
  options: Partial<RequestOptions> = {}
): Promise<T> {
  return request<T>({ url, method: 'GET', data, ...options })
}

export function post<T = unknown>(
  url: string,
  data?: Record<string, unknown>,
  options: Partial<RequestOptions> = {}
): Promise<T> {
  return request<T>({ url, method: 'POST', data, ...options })
}

export function put<T = unknown>(
  url: string,
  data?: Record<string, unknown>,
  options: Partial<RequestOptions> = {}
): Promise<T> {
  return request<T>({ url, method: 'PUT', data, ...options })
}

export function del<T = unknown>(
  url: string,
  data?: Record<string, unknown>,
  options: Partial<RequestOptions> = {}
): Promise<T> {
  return request<T>({ url, method: 'DELETE', data, ...options })
}
