/**
 * AI 智能助手接口封装
 * ----------------------------------------------------------
 * 仅对接后端已就绪的 /api/ai/** 接口：
 *  - GET  /api/ai/config        获取 AI 配置（不含 key）
 *  - POST /api/ai/chat/stream  SSE 流式聊天（主用）
 *  - POST /api/ai/chat          同步聊天（兜底）
 *
 * 说明：后端 /api/ai/** 已放行（无需登录 token）。
 */
import { BASE_URL, CHAT_STREAM_URL, CHAT_URL, CONFIG_URL, MAX_HISTORY } from '@/config/ai'
import { startSSE } from '@/utils/sse.js'

/** 组装发送给后端的历史消息（最近 N 条，只保留 user/assistant） */
export function buildHistory(messages, limit = MAX_HISTORY) {
  return (messages || [])
    .filter((m) => m && (m.role === 'user' || m.role === 'assistant') && m.content)
    .slice(-limit)
    .map((m) => ({ role: m.role, content: String(m.content).trim() }))
}

/** 获取 AI 配置 */
export function getAiConfig() {
  return new Promise((resolve, reject) => {
    uni.request({
      url: CONFIG_URL,
      method: 'GET',
      success: (res) => {
        if (res.statusCode === 200 && res.data && res.data.code === 0) {
          resolve(res.data.data)
        } else {
          reject(new Error((res.data && res.data.message) || '获取配置失败'))
        }
      },
      fail: (err) => reject(err),
    })
  })
}

/**
 * 读取当前登录会员信息（本地缓存 key 与主小程序一致：starlink_member）
 * 登录后 AI 会知道"我是谁"，结合身份回答；未登录返回空串
 */
function getUserInfo() {
  try {
    const m = uni.getStorageSync('starlink_member')
    if (!m) return ''
    return typeof m === 'string' ? m : JSON.stringify(m)
  } catch (e) {
    return ''
  }
}

/**
 * 流式聊天（SSE）
 * @param {object} params { message, history }
 * @param {object} handlers
 *   onStep(step)    思考步骤 { stepType, label, content }
 *   onDelta(text)   答案增量
 *   onDone(full)    结束（完整文本）
 *   onError(err)    出错 / 手动中断（err.name === 'AbortError'）
 * @returns {{ abort: Function }}
 */
export function streamChat(params, handlers) {
  return startSSE(
    CHAT_STREAM_URL,
    { message: params.message, image: params.image || undefined, history: params.history || [], role: 'user', userInfo: getUserInfo() },
    {
      onEvent: (evt) => {
        if (!evt || !evt.type) return
        if (evt.type === 'step') {
          handlers.onStep && handlers.onStep({
            stepType: evt.stepType || 'tool',
            label: evt.label || '',
            content: evt.content || '',
          })
        } else if (evt.type === 'delta') {
          handlers.onDelta && handlers.onDelta(evt.content || '')
        } else if (evt.type === 'done') {
          handlers.onDone && handlers.onDone(evt.content || '')
        } else if (evt.type === 'error') {
          handlers.onError && handlers.onError(new Error(evt.content || '服务异常'))
        }
      },
      onError: (err) => handlers.onError && handlers.onError(err),
      onComplete: () => {
        /* 流结束（onDone/onError 已触发） */
      },
    }
  )
}

/**
 * 同步聊天（兜底：流式不可用时使用）
 */
export function sendChatSync(message, history, image) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: CHAT_URL,
      method: 'POST',
      data: { message, image: image || undefined, history: history || [], role: 'user', userInfo: getUserInfo() },
      success: (res) => {
        if (res.statusCode === 200 && res.data && res.data.code === 0) {
          resolve(res.data.data)
        } else {
          reject(new Error((res.data && res.data.message) || '请求失败'))
        }
      },
      fail: (err) => reject(err),
    })
  })
}

/* ==================== 会话持久化（数据库） ==================== */

const SESSION_URL = (BASE) => `${BASE}/api/ai/session`

/** 当前登录身份（用户端）：userType=user，userId 取会员 id，未登录为 null */
function currentIdentity() {
  try {
    const m = uni.getStorageSync('starlink_member')
    const obj = typeof m === 'string' ? JSON.parse(m) : m
    const uid = obj && (obj.id || obj.memberId || obj.member_id)
    const name = obj && (obj.nickname || obj.realName || obj.real_name || obj.phone)
    return { userType: 'user', userId: uid ? Number(uid) : null, userName: name ? String(name) : null }
  } catch (e) {
    return { userType: 'user', userId: null, userName: null }
  }
}

function sessionRequest(url, method, data) {
  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method,
      data: data || {},
      timeout: 10000,
      success: (res) => {
        if (res.statusCode === 200 && res.data && res.data.code === 0) {
          resolve(res.data.data)
        } else {
          reject(new Error((res.data && res.data.message) || '请求失败'))
        }
      },
      fail: (err) => reject(err),
    })
  })
}

/** 创建会话，返回后端会话记录（含 sessionNo） */
export function createAiSession(title) {
  const { userType, userId, userName } = currentIdentity()
  return sessionRequest(SESSION_URL(BASE_URL), 'POST', { userType, userId, userName, title })
}

/** 会话列表（元信息，不含消息） */
export function listAiSessions() {
  const { userType, userId } = currentIdentity()
  const q = userId ? `?userType=${userType}&userId=${userId}` : `?userType=${userType}`
  return sessionRequest(SESSION_URL(BASE_URL) + '/list' + q, 'GET')
}

/** 会话详情（含消息） */
export function getAiSessionDetail(sessionNo) {
  return sessionRequest(SESSION_URL(BASE_URL) + '/' + encodeURIComponent(sessionNo), 'GET')
}

/** 追加一条消息 */
export function addAiMessage(sessionNo, msg) {
  const data = {
    role: msg.role,
    content: msg.content || '',
    thinkingJson: msg.steps && msg.steps.length ? JSON.stringify(msg.steps) : null,
    costMs: msg.costMs || null,
    model: msg.model || null,
  }
  return sessionRequest(SESSION_URL(BASE_URL) + '/' + encodeURIComponent(sessionNo) + '/message', 'POST', data)
}

/** 逻辑删除会话 */
export function deleteAiSession(sessionNo) {
  return sessionRequest(SESSION_URL(BASE_URL) + '/' + encodeURIComponent(sessionNo), 'DELETE')
}

/** 后端 DATETIME 字符串（"2026-09-22 15:00:00"）→ 时间戳（解析失败返回 0） */
export function parseDbTime(ts) {
  if (!ts) return 0
  const n = new Date(String(ts).replace(' ', 'T')).getTime()
  return Number.isNaN(n) ? 0 : n
}
