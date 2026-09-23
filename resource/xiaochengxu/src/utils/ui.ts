/**
 * 统一交互反馈封装。集中在这里，方便以后统一换风格或加埋点。
 */

let loadingCount = 0

export function toast(title: string, icon: 'none' | 'success' | 'error' | 'loading' = 'none', duration = 1800): void {
  uni.showToast({ title, icon, duration, mask: false })
}

export function toastSuccess(title: string): void {
  toast(title, 'success')
}

export function toastError(title: string): void {
  // icon='none' 时微信小程序允许 2 行文案，用 error 图标反而会截断长文案
  toast(title || '操作失败', 'none', 2200)
}

/** 支持计数的 loading，避免并发请求时提前 hideLoading */
export function showLoading(title = '加载中...'): void {
  loadingCount += 1
  uni.showLoading({ title, mask: true })
}

export function hideLoading(): void {
  loadingCount = Math.max(0, loadingCount - 1)
  if (loadingCount === 0) uni.hideLoading()
}

/** 强制关闭所有 loading（页面 onUnload / 出错兜底） */
export function resetLoading(): void {
  loadingCount = 0
  uni.hideLoading()
}

/** Promise 化的确认弹窗 */
export function confirm(content: string, title = '提示', confirmText = '确定'): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText,
      confirmColor: '#5B5BD6',
      cancelColor: '#8A8A99',
      success: (res) => resolve(!!res.confirm),
      fail: () => resolve(false),
    })
  })
}

/** 轻提示（操作成功但不需要弹窗确认） */
export function actionSheet(itemList: string[]): Promise<number> {
  return new Promise((resolve) => {
    uni.showActionSheet({
      itemList,
      success: (res) => resolve(res.tapIndex),
      fail: () => resolve(-1),
    })
  })
}
