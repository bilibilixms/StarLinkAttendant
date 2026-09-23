import request from '@/common/api/request'
import type { ApiResponse } from '@/common/api/types'
import { getToken } from '@/common/auth'

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
}

export interface ChatRequest {
  message: string
  history?: ChatMessage[]
  /** 覆盖服务端默认模型（可选） */
  model?: string
  /** 覆盖服务端 API 基础地址（可选） */
  baseUrl?: string
  /** 覆盖服务端 API Key（可选） */
  apiKey?: string
  /** 覆盖服务端温度参数（可选） */
  temperature?: number
}

export interface ThinkingStep {
  type: 'thinking' | 'sql' | 'sql_result' | 'tool' | 'tool_result'
  content: string
  label: string
}

export interface ChatResponse {
  reply: string
  model: string
  costMs: number
  thinkingSteps?: ThinkingStep[]
}

export interface AiConfigResponse {
  baseUrl: string
  model: string
  temperature: number
  apiKeyConfigured: boolean
}

/** 获取服务端 AI 配置 */
export function getAiConfig(): Promise<ApiResponse<AiConfigResponse>> {
  return request.get('/api/ai/config') as any
}

// ============ MinerU 文档转换 ============

export interface MinerUConvertResult {
  fileName: string
  size: number
  chars: number
  loaded: boolean
  message: string
}

export interface MinerUFileInfo {
  name: string
  size: number
  lastModified: number
}

/**
 * 上传文档并转换为 Markdown（加入 AI 知识库）
 * 转换可能耗时较长（30~300s），使用 fetch 绕开 axios 15s 超时
 */
export async function uploadMinerUDoc(
  file: File,
  ocr: boolean,
  onError: (err: Error) => void,
): Promise<MinerUConvertResult | null> {
  try {
    const baseURL = import.meta.env.VITE_API_BASE_URL || ''
    const token = getToken()
    const form = new FormData()
    form.append('file', file)
    form.append('ocr', String(ocr))
    const res = await fetch(`${baseURL}/api/ai/minerU/convert`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form,
    })
    const json = await res.json()
    if (!res.ok) throw new Error(json?.message || `HTTP ${res.status}`)
    if (json.code !== 0) throw new Error(json.message || '转换失败')
    return json.data as MinerUConvertResult
  } catch (err: any) {
    onError(err)
    return null
  }
}

/** 已转换文档列表 */
export function listMinerUFiles(): Promise<ApiResponse<MinerUFileInfo[]>> {
  return request.get('/api/ai/minerU/files') as any
}

/** 查看已转换文档内容 */
export function getMinerUFileContent(name: string): Promise<ApiResponse<{ name: string; content: string; chars: number }>> {
  return request.get(`/api/ai/minerU/files/${encodeURIComponent(name)}`) as any
}

/**
 * 读取当前登录管理员信息（localStorage key 与登录态一致：starlink_attendant_user）
 * 登录后 AI 会知道"我是谁"，结合身份回答；未登录返回空串
 */
function getAdminUserInfo(): string {
  try {
    const raw = localStorage.getItem('starlink_attendant_user')
    if (!raw) return ''
    const u = JSON.parse(raw)
    if (!u || !u.isLoggedIn) return ''
    return JSON.stringify({ userId: u.userId, username: u.username, realName: u.realName, position: u.position })
  } catch (e) {
    return ''
  }
}

// ============ 会话持久化（数据库 ai_chat_session / ai_chat_message） ============

export interface AiSessionRow {
  sessionNo: string
  title: string
  messageCount: number
  createTime: string
  updateTime: string
}

export interface AiSessionMessageRow {
  id: number
  role: 'user' | 'assistant'
  content: string
  thinkingJson: string | null
  costMs: number | null
  model: string | null
  createTime: string
}

/** 当前登录身份（管理端）：userType=admin，userId 取员工 id，未登录为 null */
function currentAdminIdentity(): { userType: string; userId: number | null; userName: string | null } {
  try {
    const raw = localStorage.getItem('starlink_attendant_user')
    if (!raw) return { userType: 'admin', userId: null, userName: null }
    const u = JSON.parse(raw)
    if (!u || !u.isLoggedIn) return { userType: 'admin', userId: null, userName: null }
    const uid = u.userId != null ? Number(u.userId) : null
    const name = String(u.realName || u.username || '')
    return { userType: 'admin', userId: Number.isNaN(uid as number) ? null : uid, userName: name || null }
  } catch {
    return { userType: 'admin', userId: null, userName: null }
  }
}

/** 创建会话，返回后端会话记录（含 sessionNo） */
export async function createAiSession(title?: string): Promise<AiSessionRow> {
  const { userType, userId, userName } = currentAdminIdentity()
  const res = await request.post('/api/ai/session', { userType, userId, userName, title })
  return res.data as AiSessionRow
}

/** 会话列表（元信息，不含消息） */
export async function listAiSessions(): Promise<AiSessionRow[]> {
  const { userType, userId } = currentAdminIdentity()
  const q = userId != null ? `?userType=${userType}&userId=${userId}` : `?userType=${userType}`
  const res = await request.get(`/api/ai/session/list${q}`)
  return res.data as AiSessionRow[]
}

/** 会话详情（含消息） */
export async function getAiSessionDetail(sessionNo: string): Promise<{ sessionNo: string; title: string; messages: AiSessionMessageRow[] }> {
  const res = await request.get(`/api/ai/session/${encodeURIComponent(sessionNo)}`)
  return res.data as any
}

/** 追加一条消息 */
export async function addAiMessage(
  sessionNo: string,
  msg: { role: string; content: string; thinkingJson?: string | null; costMs?: number | null; model?: string | null },
): Promise<void> {
  await request.post(`/api/ai/session/${encodeURIComponent(sessionNo)}/message`, {
    role: msg.role,
    content: msg.content || '',
    thinkingJson: msg.thinkingJson ?? null,
    costMs: msg.costMs ?? null,
    model: msg.model ?? null,
  })
}

/** 逻辑删除会话 */
export async function deleteAiSession(sessionNo: string): Promise<void> {
  await request.delete(`/api/ai/session/${encodeURIComponent(sessionNo)}`)
}

/** 后端 DATETIME 字符串（"2026-09-22 15:00:00"）→ 时间戳（解析失败返回 0） */
export function parseDbTime(ts?: string | null): number {
  if (!ts) return 0
  const n = new Date(String(ts).replace(' ', 'T')).getTime()
  return Number.isNaN(n) ? 0 : n
}

/** 聊天（统一使用 /chat 接口，后端同步完成全部 SQL 循环后返回最终答案） */
export function sendChat(data: ChatRequest): Promise<ApiResponse<ChatResponse>> {
  return request.post('/api/ai/chat', { ...data, role: 'admin', userInfo: getAdminUserInfo() }) as any
}

/**
 * 聊天（SSE 真正流式）
 * 事件类型：step（思考步骤）、delta（答案增量）、done（结束）、error
 */
export async function sendChatStreamSSE(
  data: ChatRequest,
  onStep: (step: { stepType: string; label: string; content: string }) => void,
  onDelta: (text: string) => void,
  onDone: (full: string) => void,
  onError: (err: Error) => void,
  signal?: AbortSignal,
) {
  try {
    // 手动拼完整 URL + 带 token（fetch 不走 axios 拦截器）
    const baseURL = import.meta.env.VITE_API_BASE_URL || ''
    const token = getToken()
    const res = await fetch(`${baseURL}/api/ai/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ ...data, role: 'admin', userInfo: getAdminUserInfo() }),
      signal,
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    if (!res.body) throw new Error('No response body')

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let full = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 按 \n\n 分割事件
      const lines = buffer.split('\n\n')
      buffer = lines.pop() || ''

      for (const block of lines) {
        const dataLine = block.split('\n').find(l => l.startsWith('data:'))
        if (!dataLine) continue
        const jsonStr = dataLine.slice(5).trim()
        if (!jsonStr) continue
        // 注意：error 信息不能在下面的解析 try 内 throw——会被"解析失败忽略"的 catch 吞掉
        let errMsg: string | null = null
        try {
          const evt = JSON.parse(jsonStr)
          if (evt.type === 'step') {
            onStep({
              stepType: evt.stepType || 'tool',
              label: evt.label || evt.content || '',
              content: evt.content || '',
            })
          }
          else if (evt.type === 'delta') { full += evt.content; onDelta(evt.content) }
          else if (evt.type === 'done') { full = evt.content || full }
          else if (evt.type === 'error') { errMsg = evt.content || 'AI 服务异常' }
        } catch (e) { /* 解析单个事件失败忽略 */ }
        if (errMsg) throw new Error(errMsg)
      }
    }
    onDone(full)
  } catch (err: any) {
    onError(err)
  }
}
