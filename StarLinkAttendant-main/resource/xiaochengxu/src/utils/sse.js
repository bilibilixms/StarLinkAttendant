/**
 * 跨端 SSE 流式客户端
 * ----------------------------------------------------------
 *  - H5 / App：fetch + ReadableStream（支持 AbortController 中断）
 *  - 微信小程序：uni.request({ enableChunked: true })（支持 requestTask.abort 中断）
 *
 * 用法：
 *   const { abort } = startSSE(url, body, {
 *     onEvent: (evt) => {},   // 每个 SSE 事件（已 JSON.parse）
 *     onError: (err) => {},   // 出错 / 手动中断（err.name === 'AbortError'）
 *     onComplete: () => {},   // 正常结束
 *   })
 *   abort()  // 用户点击“停止”时调用
 */

/** ArrayBuffer → UTF-8 字符串（微信小程序 chunk 解码用） */
function ab2str(buf) {
  if (!buf) return ''
  const bytes = new Uint8Array(buf)
  let result = ''
  let i = 0
  const chunk = 0x8000
  while (i < bytes.length) {
    const slice = bytes.subarray(i, i + chunk)
    result += String.fromCharCode.apply(null, slice)
    i += chunk
  }
  try {
    return decodeURIComponent(escape(result))
  } catch (e) {
    return result
  }
}

/** 解析 SSE 文本块，返回事件对象数组 */
function parseSSEBlock(block) {
  const events = []
  const parts = String(block).split('\n\n')
  for (const part of parts) {
    if (!part.trim()) continue
    const dataLine = part
      .split('\n')
      .map((l) => l.trim())
      .find((l) => l.startsWith('data:'))
    if (!dataLine) continue
    const jsonStr = dataLine.slice(5).trim()
    if (!jsonStr) continue
    try {
      events.push(JSON.parse(jsonStr))
    } catch (e) {
      /* 忽略无法解析的事件 */
    }
  }
  return events
}

/**
 * 启动 SSE 流式请求
 * @param {string} url 请求地址
 * @param {object} body 请求体
 * @param {object} handlers { onEvent, onError, onComplete }
 * @returns {{ abort: Function }}
 */
export function startSSE(url, body, handlers) {
  const { onEvent, onError, onComplete } = handlers

  // #ifdef H5 || APP-PLUS
  const controller = new AbortController()
  let settled = false
  const fail = (err) => {
    if (settled) return
    settled = true
    onError && onError(err)
    onComplete && onComplete()
  }
  fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: controller.signal,
  })
    .then((res) => {
      if (!res.ok) throw new Error('HTTP ' + res.status)
      const reader = res.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      const pump = () => {
        reader
          .read()
          .then(({ done, value }) => {
            if (done) {
              // 解析 buffer 中残留的最后一段（SSE 末尾数据可能没有 \n\n 分隔，done 事件可能藏在这里）
              if (buffer && buffer.trim()) {
                const evts = parseSSEBlock(buffer)
                evts.forEach((e) => onEvent && onEvent(e))
                buffer = ''
              }
              if (!settled) {
                settled = true
                onComplete && onComplete()
              }
              return
            }
            buffer += decoder.decode(value, { stream: true })
            const parts = buffer.split('\n\n')
            buffer = parts.pop() || ''
            for (const part of parts) {
              const evts = parseSSEBlock(part)
              evts.forEach((e) => onEvent && onEvent(e))
            }
            pump()
          })
          .catch(fail)
      }
      pump()
    })
    .catch(fail)

  return {
    abort() {
      controller.abort()
    },
  }
  // #endif

  // #ifdef MP-WEIXIN
  let task = null
  let buffer = ''
  let aborted = false
  let settled = false

  const finish = (err) => {
    if (settled) return
    settled = true
    if (err) onError && onError(err)
    onComplete && onComplete()
  }

  task = uni.request({
    url,
    method: 'POST',
    data: body,
    header: { 'Content-Type': 'application/json' },
    enableChunked: true,
    success: () => {
      finish()
    },
    fail: (err) => {
      finish(err)
    },
  })

  if (task && task.onChunkReceived) {
    task.onChunkReceived((res) => {
      buffer += ab2str(res.data)
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''
      for (const part of parts) {
        const evts = parseSSEBlock(part)
        evts.forEach((e) => onEvent && onEvent(e))
      }
    })
  } else {
    // 基础库过低不支持 chunked：直接报错提示
    setTimeout(() => {
      finish(new Error('当前微信基础库版本过低，不支持流式输出（需 2.20.1+）'))
    }, 0)
  }

  return {
    abort() {
      if (aborted) return
      aborted = true
      if (task && task.abort) {
        task.abort()
      }
      const err = new Error('手动停止')
      err.name = 'AbortError'
      finish(err)
    },
  }
  // #endif
}
