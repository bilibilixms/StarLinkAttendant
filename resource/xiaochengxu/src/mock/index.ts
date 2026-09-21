/**
 * Mock 请求分发器。
 *
 * request.ts 在 VITE_USE_MOCK=true 时把请求交给这里，
 * 返回值结构与真实后端 ApiResponse 完全一致，
 * 所以 request.ts 的拆包 / 401 / 错误提示逻辑走的是同一条路径。
 */
import type { ApiResponse } from '@/types/api'
import { db } from './db'
import { routes, type MockContext, type MockRoute } from './routes'

interface MockRequestOptions {
  url: string
  method: string
  data: Record<string, unknown>
  auth: boolean
  token: string
}

interface CompiledRoute {
  route: MockRoute
  regex: RegExp
  keys: string[]
}

/** 把 `/api/applet/orders/:id/pay` 编译成正则 + 参数名列表 */
function compile(path: string): { regex: RegExp; keys: string[] } {
  const keys: string[] = []
  const pattern = path
    .replace(/[.+*?^${}()|[\]\\]/g, '\\$&')
    .replace(/:([A-Za-z0-9_]+)/g, (_m, key: string) => {
      keys.push(key)
      return '([^/]+)'
    })
  return { regex: new RegExp(`^${pattern}/?$`), keys }
}

const compiledRoutes: CompiledRoute[] = routes.map((route) => ({
  route,
  ...compile(route.path),
}))

function parseQuery(search: string): Record<string, string> {
  const out: Record<string, string> = {}
  if (!search) return out
  search
    .replace(/^\?/, '')
    .split('&')
    .filter(Boolean)
    .forEach((pair) => {
      const [k, v = ''] = pair.split('=')
      try {
        out[decodeURIComponent(k)] = decodeURIComponent(v)
      } catch {
        out[k] = v
      }
    })
  return out
}

/** 模拟网络延迟，让页面的 loading 态真的能被看到 */
function latency(): number {
  return 180 + Math.floor(Math.random() * 220)
}

export function mockRequest<T>(options: MockRequestOptions): Promise<ApiResponse<T>> {
  return new Promise((resolve) => {
    setTimeout(() => {
      const method = (options.method || 'GET').toUpperCase()
      const [path, search = ''] = options.url.split('?')

      // GET 的参数在 query 上，其余方法在 body 里（与 uni.request 行为一致）
      const query: Record<string, string> = { ...parseQuery(search) }
      const body: Record<string, unknown> = {}
      if (method === 'GET' || method === 'DELETE') {
        Object.entries(options.data ?? {}).forEach(([k, v]) => {
          if (v !== undefined && v !== null) query[k] = String(v)
        })
      } else {
        Object.assign(body, options.data ?? {})
      }

      const matched = compiledRoutes.find(
        (c) => c.route.method.toUpperCase() === method && c.regex.test(path)
      )

      if (!matched) {
        // 明确报「接口不存在」，而不是静默返回假数据，避免误以为已经联调
        console.warn(`[mock] 未匹配到接口: ${method} ${path}`)
        resolve({
          code: 404,
          message: `接口未实现：${method} ${path}`,
          data: null as T,
          timestamp: new Date().toISOString(),
        })
        return
      }

      const matchResult = matched.regex.exec(path)!
      const params: Record<string, string> = {}
      matched.keys.forEach((key, i) => {
        params[key] = decodeURIComponent(matchResult[i + 1] ?? '')
      })

      // 鉴权：未登录访问需要登录的接口，返回 401（与真实后端 SecurityConfig 行为一致）
      if (matched.route.auth && !db().loggedIn) {
        resolve({
          code: 401,
          message: '请先登录后再操作',
          data: null as T,
          timestamp: new Date().toISOString(),
        })
        return
      }

      const ctx: MockContext = { params, query, body, authed: options.auth }

      try {
        const data = matched.route.handler(ctx)
        resolve({
          code: 0,
          message: '操作成功',
          data: data as T,
          timestamp: new Date().toISOString(),
        })
      } catch (e) {
        const err = e as Error & { code?: number }
        resolve({
          code: err.code ?? -1,
          message: err.message || '操作失败',
          data: null as T,
          timestamp: new Date().toISOString(),
        })
      }
    }, latency())
  })
}
