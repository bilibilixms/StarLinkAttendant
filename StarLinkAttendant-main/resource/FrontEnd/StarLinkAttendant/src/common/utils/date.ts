/** 格式化日期 */
export function formatDate(date: string | Date | null | undefined, fmt = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!date) return '-'
  const d = new Date(date)
  if (isNaN(d.getTime())) return '-'
  const map: Record<string, string> = {
    YYYY: String(d.getFullYear()),
    MM: String(d.getMonth() + 1).padStart(2, '0'),
    DD: String(d.getDate()).padStart(2, '0'),
    HH: String(d.getHours()).padStart(2, '0'),
    mm: String(d.getMinutes()).padStart(2, '0'),
    ss: String(d.getSeconds()).padStart(2, '0'),
  }
  let result = fmt
  for (const [key, value] of Object.entries(map)) {
    result = result.replace(key, value)
  }
  return result
}

/** 格式化为仅日期 */
export function formatDateOnly(date: string | Date | null | undefined): string {
  return formatDate(date, 'YYYY-MM-DD')
}

/** 格式化为仅时间 */
export function formatTimeOnly(date: string | Date | null | undefined): string {
  return formatDate(date, 'HH:mm:ss')
}

/** 计算相对时间 */
export function timeAgo(date: string | Date): string {
  const d = new Date(date)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return '刚刚'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  return formatDate(date)
}
