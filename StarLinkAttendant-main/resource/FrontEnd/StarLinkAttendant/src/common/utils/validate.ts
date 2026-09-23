/** 手机号校验 */
export function isValidPhone(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone)
}

/** 身份证校验（简单） */
export function isValidIdCard(idCard: string): boolean {
  return /^\d{17}[\dXx]$/.test(idCard)
}

/** 邮箱校验 */
export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

/** Element Plus 表单校验规则 - 手机号 */
export const phoneValidator = {
  pattern: /^1[3-9]\d{9}$/,
  message: '请输入正确的手机号',
  trigger: 'blur' as const,
}

/** Element Plus 表单校验规则 - 必填 */
export function requiredRule(message: string, trigger = 'blur' as const) {
  return { required: true, message, trigger }
}
