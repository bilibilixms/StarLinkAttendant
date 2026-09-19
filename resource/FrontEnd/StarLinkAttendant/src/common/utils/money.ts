/** 格式化金额（元） */
export function formatMoney(amount: number | string | null | undefined, decimals = 2): string {
  if (amount === null || amount === undefined || amount === '') return '0.00'
  const num = typeof amount === 'string' ? parseFloat(amount) : amount
  if (isNaN(num)) return '0.00'
  return num.toFixed(decimals)
}

/** 格式化金额带人民币符号 */
export function formatCNY(amount: number | string | null | undefined): string {
  return '¥' + formatMoney(amount)
}

/** 安全加法（防浮点精度丢失） */
export function safeAdd(a: number, b: number): number {
  return Math.round((a + b) * 100) / 100
}

/** 安全乘法 */
export function safeMultiply(a: number, b: number): number {
  return Math.round(a * 100 * b) / 100
}
