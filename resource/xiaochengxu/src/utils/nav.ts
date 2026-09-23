/**
 * 统一路由封装。所有跳转都从这里走，便于以后加权限拦截 / 埋点。
 */

/** 登录页路径 */
export const LOGIN_PAGE = '/pages/auth/login'

/** 5 个 Tab 页路径，用于判断是否需要 reLaunch */
const TAB_PAGES = [
  '/pages/index/index',
  '/pages/service/index',
  '/pages/game/index',
  '/pages/community/index',
  '/pages/mine/index',
]

export function isTabPage(path: string): boolean {
  return TAB_PAGES.includes(path.split('?')[0])
}

/** 普通页面跳转 */
export function navTo(url: string): void {
  uni.navigateTo({
    url,
    fail: (err) => console.warn('[nav] navigateTo 失败', url, err),
  })
}

/** 关闭当前页并跳转 */
export function redirectTo(url: string): void {
  uni.redirectTo({
    url,
    fail: (err) => console.warn('[nav] redirectTo 失败', url, err),
  })
}

/** 关闭所有页面并跳转（Tab 切换 / 登录成功后回首页） */
export function reLaunch(url: string): void {
  uni.reLaunch({
    url,
    fail: (err) => console.warn('[nav] reLaunch 失败', url, err),
  })
}

/** 返回上一页，没有上一页时回到首页 */
export function navBack(delta = 1): void {
  const pages = getCurrentPages()
  if (pages.length > delta) {
    uni.navigateBack({ delta })
  } else {
    reLaunch('/pages/index/index')
  }
}

/**
 * 切换 Tab。5 个 Tab 页用 reLaunch 保证页面栈干净，
 * 避免 navigateTo 反复压栈导致「返回」行为异常。
 */
export function switchTab(path: string): void {
  const pages = getCurrentPages()
  const current = pages.length ? `/${pages[pages.length - 1].route}` : ''
  if (current === path) return
  reLaunch(path)
}

/** 跳转到登录页，带 redirect 参数用于登录后回跳 */
export function goLogin(redirect?: string): void {
  const pages = getCurrentPages()
  const from = redirect || (pages.length ? `/${pages[pages.length - 1].route}` : '')
  reLaunch(`${LOGIN_PAGE}?redirect=${encodeURIComponent(from)}`)
}
