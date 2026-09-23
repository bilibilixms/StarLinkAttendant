/**
 * AI 智能助手接口配置
 * ----------------------------------------------------------
 * 与 api/request.ts 同一来源：VITE_API_BASE_URL（.env 系列配置）
 * - H5 开发：走 vite proxy 同源
 * - 微信小程序：.env.development 里配完整后端地址（如 http://localhost:8080）
 *
 * AI 接口（/api/ai/**）不经过本地 mock 层，直接打真实后端。
 */
/** AI 后端地址（会话接口拼路径用） */
export const BASE_URL = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '')

/** 流式接口路径（SSE） */
export const CHAT_STREAM_URL = `${BASE_URL}/api/ai/chat/stream`

/** 同步接口路径（兜底，正常优先走流式） */
export const CHAT_URL = `${BASE_URL}/api/ai/chat`

/** 获取 AI 配置 */
export const CONFIG_URL = `${BASE_URL}/api/ai/config`

/** 一次请求携带的最大历史消息条数（避免 token 过长） */
export const MAX_HISTORY = 20
