/**
 * 设备 / 安全区适配。
 * 微信小程序必须处理状态栏高度与底部安全区，H5 下这两项为 0。
 */

interface SystemInfo {
  statusBarHeight: number
  safeAreaBottom: number
  windowWidth: number
  windowHeight: number
  platform: string
  safeAreaTop: number
}

let cache: SystemInfo | null = null

export function getSystemInfo(): SystemInfo {
  if (cache) return cache

  let statusBarHeight = 0
  let safeAreaBottom = 0
  let windowWidth = 375
  let windowHeight = 667
  let safeAreaTop = 0
  let platform = 'devtools'

  try {
    const info = uni.getSystemInfoSync()
    statusBarHeight = info.statusBarHeight ?? 0
    windowWidth = info.windowWidth ?? windowWidth
    windowHeight = info.windowHeight ?? windowHeight
    platform = info.platform ?? platform

    const safeArea = info.safeArea
    if (safeArea) {
      safeAreaTop = safeArea.top ?? statusBarHeight
      safeAreaBottom = Math.max(0, windowHeight - (safeArea.bottom ?? windowHeight))
    }
  } catch (e) {
    console.warn('[system] getSystemInfoSync 失败', e)
  }

  cache = { statusBarHeight, safeAreaBottom, windowWidth, windowHeight, platform, safeAreaTop }
  return cache
}

/** 状态栏高度（px） */
export function getStatusBarHeight(): number {
  return getSystemInfo().statusBarHeight
}

/** 底部安全区高度（px），iPhone 刘海屏约 34 */
export function getSafeAreaBottom(): number {
  return getSystemInfo().safeAreaBottom
}

/**
 * 统一导航栏总高度（状态栏 + 胶囊行），用于自定义 NavBar 占位。
 * 微信小程序右上角胶囊高度为 32px，上下各留 4px。
 */
export function getNavBarHeight(): number {
  const { statusBarHeight } = getSystemInfo()
  return statusBarHeight + 44
}

/** px 转 rpx（750 设计稿） */
export function px2rpx(px: number): number {
  const { windowWidth } = getSystemInfo()
  if (!windowWidth) return px * 2
  return (px * 750) / windowWidth
}

/** rpx 转 px */
export function rpx2px(rpx: number): number {
  const { windowWidth } = getSystemInfo()
  if (!windowWidth) return rpx / 2
  return (rpx * windowWidth) / 750
}
