/**
 * Markdown → 结构化节点解析器
 * ----------------------------------------------------------
 * 把 AI 回答解析成结构化节点数组，供页面按类型渲染：
 *   { type: 'text',  text: string }             普通段落
 *   { type: 'table', headers: string[], rows: string[][] }  Markdown 表格
 *   { type: 'list',  ordered: boolean, items: string[] }    无序/有序列表
 * 表格与列表渲染为美观的可视化卡片，而非挤在一起的纯文本。
 */

/** 结构化节点 */
export type ContentNode =
  | { type: 'text'; text: string }
  | { type: 'table'; headers: string[]; rows: string[][] }
  | { type: 'list'; ordered: boolean; items: string[] }

/** 去掉粗体/斜体/行内代码标记，返回干净文本 */
function cleanInline(t: string): string {
  return String(t || '')
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1') // [文字](链接) → 文字
    .trim()
}

/** 解析 Markdown 表格行：| a | b | → ['a', 'b'] */
function splitRow(line: string): string[] {
  const s = String(line).trim().replace(/^\|/, '').replace(/\|$/, '')
  return s.split('|').map((c) => c.trim())
}

/** 判断是否为表格分隔行：|---|:---| 等 */
function isSepLine(line?: string): boolean {
  if (!line) return false
  const t = String(line).trim()
  if (!t.startsWith('|')) return false
  return /^\|?[\s:|-]+\|?\s*$/.test(t) && t.includes('-')
}

/**
 * 解析 AI 回答为结构化节点数组
 * @param md 原始 Markdown 文本
 */
export function parseContent(md: string): ContentNode[] {
  if (!md) return []
  const lines = String(md).split('\n')
  const nodes: ContentNode[] = []
  let i = 0

  while (i < lines.length) {
    const raw = lines[i]
    const trimmed = raw.trim()

    // ---- 表格：当前行以 | 开头且下一行为分隔行 ----
    if (trimmed.startsWith('|') && isSepLine(lines[i + 1])) {
      const headers = splitRow(trimmed)
      i += 2 // 跳过表头行与分隔行
      const rows: string[][] = []
      while (i < lines.length && lines[i].trim().startsWith('|')) {
        rows.push(splitRow(lines[i]))
        i++
      }
      if (headers.length) nodes.push({ type: 'table', headers, rows })
      continue
    }

    // ---- 列表：连续的无序/有序列表项合并成一个节点 ----
    const isUl = /^\s*[-*+]\s+/.test(raw)
    const isOl = /^\s*\d+[.、)]\s+/.test(raw)
    if (isUl || isOl) {
      const ordered = isOl
      const items: string[] = []
      while (i < lines.length) {
        const line = lines[i]
        if (/^\s*[-*+]\s+/.test(line)) {
          items.push(cleanInline(line.replace(/^\s*[-*+]\s+/, '')))
          i++
        } else if (/^\s*\d+[.、)]\s+/.test(line)) {
          items.push(cleanInline(line.replace(/^\s*\d+[.、)]\s+/, '')))
          i++
        } else {
          break
        }
      }
      if (items.length) nodes.push({ type: 'list', ordered, items })
      continue
    }

    // ---- 标题 ----
    if (/^#{1,6}\s+/.test(trimmed)) {
      nodes.push({ type: 'text', text: cleanInline(trimmed.replace(/^#{1,6}\s+/, '')) })
      i++
      continue
    }

    // ---- 普通文本（合并连续非空、非结构行） ----
    if (trimmed) {
      let buf = cleanInline(trimmed)
      i++
      while (i < lines.length) {
        const n = lines[i].trim()
        if (
          !n ||
          n.startsWith('|') ||
          /^\s*[-*+]\s+/.test(n) ||
          /^\s*\d+[.、)]\s+/.test(n) ||
          /^#{1,6}\s+/.test(n) ||
          isSepLine(lines[i])
        ) {
          break
        }
        buf += '\n' + cleanInline(n)
        i++
      }
      nodes.push({ type: 'text', text: buf })
      continue
    }

    i++
  }

  return nodes
}
