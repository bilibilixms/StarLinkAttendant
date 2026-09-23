/**
 * 用户端展示层过滤规则（“用户端提示词”的兜底实现）
 * ----------------------------------------------------------
 * 后端系统提示词已要求 AI 不输出 SQL 代码块、工具调用步骤不回传 SQL 参数；
 * 但为绝对保证「SQL 语句不出现到 AI 回答或思考过程」，用户端再执行一层过滤：
 *  1. 剔除 markdown 代码块中的 SQL
 *  2. 剔除以 SQL 关键字开头的行
 *  3. 打码数据库连接串 / 账号密码等敏感信息
 *  4. 打码手机号 / 身份证等个人敏感信息
 */

/** SQL 关键字行判定（出现在行首即判定为 SQL 语句） */
const SQL_LINE_START = /^\s*(SELECT|INSERT|UPDATE|DELETE|ALTER|DROP|CREATE|TRUNCATE|GRANT|REVOKE|SHOW|DESC|EXPLAIN)\b/i

/** 行内可能残留的 SQL 片段（整行剔除） */
const SQL_INLINE = /\b(SELECT|INSERT INTO|UPDATE|DELETE FROM|ALTER TABLE|DROP TABLE)\b/i

/** 数据库连接 / 账号密码 */
const DB_SECRET = /(jdbc:mysql|localhost:\d+|127\.0\.0\.1:\d+|user(name)?\s*[:=]\s*\w+|pass(word)?\s*[:=]\s*\S+|password\s*\d*)/gi

/** 手机号（11 位，1 开头） */
const PHONE = /(?<!\d)1[3-9]\d{9}(?!\d)/g

/** 身份证号（18 位，末位可为 X） */
const ID_CARD = /(?<!\d)\d{17}[\dXx](?!\d)/g

/**
 * 剔除文本中的 SQL 语句与敏感信息
 * @param {string} text 原始文本
 * @returns {string} 过滤后的文本
 */
export function cleanText(text) {
  if (!text) return ''
  const lines = String(text).split('\n')
  const out = []
  let inSqlBlock = false

  for (const raw of lines) {
    const line = raw
    const trimmed = line.trim()

    // 处理 ```sql 代码块：整块剔除
    if (/^```\s*(sql|mysql)?\s*$/i.test(trimmed)) {
      inSqlBlock = !inSqlBlock
      continue
    }
    if (inSqlBlock) continue

    // SQL 关键字开头的行整行剔除
    if (SQL_LINE_START.test(trimmed)) continue
    // 行内出现明显 SQL 片段也剔除
    if (SQL_INLINE.test(trimmed)) continue

    // 敏感信息打码
    let safe = line
      .replace(DB_SECRET, () => '***')
      .replace(PHONE, (m) => m.slice(0, 3) + '****' + m.slice(7))
      .replace(ID_CARD, (m) => m.slice(0, 6) + '********' + m.slice(14))

    out.push(safe)
  }

  return out.join('\n').replace(/\n{3,}/g, '\n\n').trim()
}

/**
 * 思考步骤内容过滤（tool_result 返回的表格数据可能含 SQL 错误信息或敏感字段）
 */
export function cleanStepContent(text) {
  if (!text) return ''
  const cleaned = cleanText(text)
  // 若过滤后整段为空（说明原内容就是 SQL），返回占位提示
  if (!cleaned) return '（数据已省略）'
  return cleaned
}
