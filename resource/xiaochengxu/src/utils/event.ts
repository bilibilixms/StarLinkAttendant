/**
 * uni-app 事件取值工具。
 *
 * uni-app 的 input / textarea / picker / switch 事件在**运行时**是
 * `{ detail: { value } }`，但 Vue 为模板生成的类型把它标注成原生 `Event`。
 * 于是 `e.detail.value` 会报 TS2339 / TS2322。
 *
 * 与其在每个页面写 `any`，不如集中在这里做一次带校验的取值。
 */

interface UniEventLike {
  detail?: { value?: unknown }
}

/** 从 input / textarea / picker 事件里取字符串值 */
export function eventValue(e: Event): string {
  const v = (e as unknown as UniEventLike).detail?.value
  if (typeof v === 'string') return v
  if (v === undefined || v === null) return ''
  return String(v)
}

/** 从 switch 事件里取布尔值 */
export function eventChecked(e: Event): boolean {
  return (e as unknown as UniEventLike).detail?.value === true
}

/** 从 picker 事件里取数值下标 */
export function eventIndex(e: Event): number {
  const v = (e as unknown as UniEventLike).detail?.value
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}
