/**
 * 展示层格式化工具。所有金额后端以「元」为单位返回（decimal(12,2)）。
 */

/** 金额格式化：18 → "18.00"，18.5 → "18.50" */
export function formatMoney(value: number | null | undefined, withSymbol = false): string {
  const n = Number(value ?? 0)
  const s = (Number.isFinite(n) ? n : 0).toFixed(2)
  return withSymbol ? `¥${s}` : s
}

/** 金额取整展示（用于卡片上的大字价格）：18.00 → "18"，18.5 → "18.5" */
export function formatMoneyShort(value: number | null | undefined): string {
  const n = Number(value ?? 0)
  if (!Number.isFinite(n)) return '0'
  return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/0$/, '')
}

/** 分钟 → "2小时15分" / "45分钟" */
export function formatDuration(minutes: number | null | undefined): string {
  const m = Math.max(0, Math.floor(Number(minutes ?? 0)))
  if (m < 60) return `${m}分钟`
  const h = Math.floor(m / 60)
  const rest = m % 60
  return rest === 0 ? `${h}小时` : `${h}小时${rest}分`
}

/** 分钟 → "02:15:30"（当前上机计时用） */
export function formatClock(minutes: number): string {
  const total = Math.max(0, Math.floor(minutes * 60))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return [h, m, s].map((v) => String(v).padStart(2, '0')).join(':')
}

/** 秒 → "12:31:43"（任务倒计时） */
export function formatCountdown(totalSeconds: number): string {
  const t = Math.max(0, Math.floor(totalSeconds))
  const h = Math.floor(t / 3600)
  const m = Math.floor((t % 3600) / 60)
  const s = t % 60
  return [h, m, s].map((v) => String(v).padStart(2, '0')).join(':')
}

/** 数字千分位：12345 → "12,345" */
export function formatThousands(value: number | null | undefined): string {
  const n = Number(value ?? 0)
  if (!Number.isFinite(n)) return '0'
  return n.toLocaleString('en-US')
}

/** 大数缩写：12345 → "1.2万" */
export function formatCompact(value: number | null | undefined): string {
  const n = Number(value ?? 0)
  if (!Number.isFinite(n)) return '0'
  if (n < 10000) return String(n)
  return `${(n / 10000).toFixed(1)}万`
}

/** 手机号脱敏：138****1234 */
export function maskPhone(phone: string | null | undefined): string {
  const p = String(phone ?? '')
  if (p.length !== 11) return p
  return `${p.slice(0, 3)}****${p.slice(7)}`
}

/** 解析后端时间字符串，兼容 iOS 不识别 "yyyy-MM-dd HH:mm:ss" 的问题 */
export function parseTime(input: string | number | Date | null | undefined): Date {
  if (input instanceof Date) return input
  if (typeof input === 'number') return new Date(input)
  const s = String(input ?? '').trim()
  if (!s) return new Date(NaN)
  // iOS / Safari 不支持 "2026-07-15 10:00:00"，统一替换为 ISO 形式
  const normalized = s.includes('T') ? s : s.replace(/-/g, '/')
  return new Date(normalized)
}

/** "2026-07-15 10:00:00" → "07-15 10:00" */
export function formatDateTime(input: string | number | Date, withYear = false): string {
  const d = parseTime(input)
  if (Number.isNaN(d.getTime())) return '--'
  const p = (n: number) => String(n).padStart(2, '0')
  const base = `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  return withYear ? `${d.getFullYear()}-${base}` : base
}

/** → "09-16"（社区帖子右上角） */
export function formatMonthDay(input: string | number | Date): string {
  const d = parseTime(input)
  if (Number.isNaN(d.getTime())) return '--'
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** → "2026-07-15" */
export function formatDate(input: string | number | Date): string {
  const d = parseTime(input)
  if (Number.isNaN(d.getTime())) return '--'
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 生成幂等键：时间戳 + 随机串（下单/充值防重复提交） */
export function genIdempotentKey(prefix = ''): string {
  const rand = Math.random().toString(36).slice(2, 10)
  return `${prefix}${Date.now()}${rand}`.toUpperCase()
}
