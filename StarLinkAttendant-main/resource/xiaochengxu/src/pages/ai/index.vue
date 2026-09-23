<template>
  <view class="ai-page">
    <!-- ===== 自定义导航栏 ===== -->
    <view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="nav-inner">
        <view v-if="canGoBack" class="nav-back" @click="goBack">
          <text class="back-icon">‹</text>
        </view>
        <view class="nav-title">AI 智能助手</view>
        <view class="nav-actions">
          <view class="nav-btn" @click="newSession">
            <image class="nav-icon" src="/static/ai_img/new-chat.png" mode="aspectFit" />
            <text class="nav-btn-text">新建</text>
          </view>
          <view class="nav-btn" @click="showSessions = true">
            <image class="nav-icon" src="/static/ai_img/lishihuihua.png" mode="aspectFit" />
            <text class="nav-btn-text">历史</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ===== 消息列表 ===== -->
    <scroll-view
      class="msg-list"
      scroll-y
      :scroll-into-view="scrollIntoId"
      scroll-with-animation
      :style="{ paddingTop: (statusBarHeight + 44) + 'px' }"
    >
      <!-- 欢迎页 -->
      <view v-if="!messages.length" class="welcome">
        <view class="welcome-icon">🤖</view>
        <view class="welcome-title">星络灵侍馆智能助手</view>
        <view class="welcome-desc">您好，我是星络灵侍馆智能助手，可以帮您查询空闲电脑、上机收费、会员权益等问题</view>
        <view class="quick-tips">
          <view
            v-for="tip in quickTips"
            :key="tip"
            class="quick-tip"
            @click="quickSend(tip)"
          >{{ tip }}</view>
        </view>
      </view>

      <view
        v-for="(m, idx) in messages"
        :key="m.id"
        :id="'msg-' + m.id"
        class="msg-row"
        :class="m.role"
      >
        <image
          v-if="m.role === 'assistant'"
          class="avatar assistant"
          src="/static/ai_img/a-aizhushou_huaban1fuben15_huaban1fuben15-copy.png"
          mode="aspectFill"
        />
        <view v-else class="avatar user">我</view>
        <view class="bubble" :class="m.role">
          <!-- 用户发送的图片 -->
          <image v-if="m.image" class="msg-image" :src="m.image" mode="widthFix" @click="previewImage(m.image)" />
          <!-- 思考过程（AI 消息，可折叠；无步骤则不展示） -->
          <view v-if="m.role === 'assistant' && m.steps && m.steps.length" class="thinking-box">
            <view class="thinking-head" @click="m.showSteps = !m.showSteps">
              <text class="thinking-arrow">{{ m.showSteps ? '▾' : '▸' }}</text>
              <text class="thinking-label">思考过程（{{ m.steps.length }} 步）</text>
            </view>
            <view v-if="m.showSteps" class="thinking-body">
              <view v-for="(s, si) in m.steps" :key="si" class="step-item">
                <view class="step-label">{{ stepIcon(s.stepType) }} {{ s.label }}</view>
                <text v-if="s.content" class="step-text" :user-select="true">{{ s.content }}</text>
              </view>
            </view>
          </view>

          <!-- AI 回答（结构化渲染：表格/列表/文本，SQL/敏感信息已被过滤） -->
          <view v-if="m.role === 'assistant'" class="answer">
            <template v-for="(node, ni) in renderNodes(m)" :key="ni">
              <!-- 表格 -->
              <view v-if="node.type === 'table'" class="md-table">
                <view class="md-tr md-th-row">
                  <view v-for="(h, hi) in node.headers" :key="'h' + hi" class="md-th">{{ h }}</view>
                </view>
                <view
                  v-for="(row, ri) in node.rows"
                  :key="'r' + ri"
                  class="md-tr"
                  :class="{ 'md-row-alt': ri % 2 === 1 }"
                >
                  <view v-for="(cell, ci) in row" :key="'c' + ci" class="md-td">{{ cell }}</view>
                </view>
              </view>
              <!-- 列表 -->
              <view v-else-if="node.type === 'list'" class="md-list">
                <view v-for="(item, ii) in node.items" :key="ii" class="md-li">
                  <text class="md-li-mark">{{ node.ordered ? (ii + 1) + '. ' : '• ' }}</text>
                  <text class="md-li-text" :user-select="true">{{ item }}</text>
                </view>
              </view>
              <!-- 文本段落 -->
              <view v-else class="md-text" :user-select="true">{{ node.text }}</view>
            </template>
          </view>
          <text v-else-if="m.content" class="user-text" :user-select="true">{{ m.content }}</text>

          <!-- 输入中动画 -->
          <view v-if="m.loading" class="typing">
            <view class="dot"></view>
            <view class="dot"></view>
            <view class="dot"></view>
          </view>

          <!-- 元信息 -->
          <view v-if="m.costMs" class="meta">{{ m.model || 'AI' }} · {{ m.costMs }}ms</view>
        </view>
      </view>
      <view class="list-bottom"></view>
    </scroll-view>

    <!-- ===== 输入区 ===== -->
    <view class="input-wrapper">
      <!-- 已选图片预览 -->
      <view v-if="selectedImage" class="selected-image-bar">
        <view class="selected-image-preview">
          <image :src="selectedImage" mode="aspectFill" class="selected-image" />
          <view class="remove-image-btn" @click="clearSelectedImage">×</view>
        </view>
      </view>
      <view class="input-bar">
        <view class="image-pick-btn" :class="{ disabled: streaming }" @click="chooseImage">
          <text class="image-pick-icon">🖼️</text>
        </view>
        <textarea
          v-model="input"
          class="input-area"
          placeholder="输入您的问题，Enter 发送…"
          :disabled="streaming"
          :maxlength="1000"
          confirm-type="send"
          :adjust-position="true"
          @confirm="handleSend"
        ></textarea>
        <button v-if="!streaming" class="send-btn" :class="{ disabled: !input.trim() && !selectedImage }" :disabled="!input.trim() && !selectedImage" @click="handleSend">发送</button>
        <button v-else class="stop-btn" @click="handleStop">■ 停止</button>
      </view>
    </view>

    <!-- ===== 历史会话面板 ===== -->
    <view v-if="showSessions" class="mask" @click="showSessions = false">
      <view class="session-panel" @click.stop>
        <view class="panel-header">
          <text class="panel-title">历史会话</text>
          <text class="panel-close" @click="showSessions = false">✕</text>
        </view>
        <scroll-view scroll-y class="session-list">
          <view
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: s.id === currentId }"
            @click="switchSession(s.id)"
          >
            <view class="session-info">
              <text class="session-title">{{ s.title }}</text>
              <text class="session-time">{{ formatTime(s.updateTime) }}</text>
            </view>
            <text class="session-del" @click.stop="deleteSession(s.id)">删除</text>
          </view>
          <view v-if="!sessions.length" class="session-empty">暂无历史会话</view>
        </scroll-view>
        <view class="panel-footer">
          <button class="new-session-btn" @click="newSession">＋ 新建会话</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import {
  streamChat, sendChatSync, buildHistory,
  createAiSession, listAiSessions, getAiSessionDetail, addAiMessage, deleteAiSession, parseDbTime,
} from '@/api/ai.js'
import { cleanText, cleanStepContent } from '@/utils/chatFilter.js'
import { parseContent } from '@/utils/markdown.js'

const SESSIONS_KEY = 'ai_sessions'
const STATUS_BAR_KEY = 'ai_status_bar_height'

const statusBarHeight = ref(20)
const input = ref('')
const streaming = ref(false)
const showSessions = ref(false)
const scrollIntoId = ref('')
let abortRef = null

/** 待发送的图片（base64 dataURL，直接传给后端，不存储） */
const selectedImage = ref('')
/** 图片处理中状态 */
const processingImage = ref(false)

/** 点击图片按钮 → 从相册选图，转base64 */
function chooseImage() {
  if (streaming.value || processingImage.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      const path = res.tempFilePaths && res.tempFilePaths[0]
      if (path) readImageAsBase64(path)
    },
  })
}

/** 读取本地图片文件，转成 base64 dataURL（兼容 H5 和小程序） */
function readImageAsBase64(localPath) {
  processingImage.value = true
  uni.showLoading({ title: '处理图片...' })

  // H5 端：localPath 是 blob URL，用 fetch + FileReader 读 base64
  // 小程序端：localPath 是临时文件路径，用 FileSystemManager 读 base64
  // #ifdef H5
  fetch(localPath)
    .then((r) => r.blob())
    .then((blob) => {
      const reader = new FileReader()
      reader.onload = (e) => {
        selectedImage.value = e.target.result
        processingImage.value = false
        uni.hideLoading()
      }
      reader.onerror = () => {
        uni.showToast({ title: '图片读取失败', icon: 'none' })
        processingImage.value = false
        uni.hideLoading()
      }
      reader.readAsDataURL(blob)
    })
    .catch(() => {
      uni.showToast({ title: '图片读取失败', icon: 'none' })
      processingImage.value = false
      uni.hideLoading()
    })
  // #endif

  // #ifndef H5
  // 微信小程序 / App 端：用 FileSystemManager
  try {
    const fs = uni.getFileSystemManager()
    fs.readFile({
      filePath: localPath,
      encoding: 'base64',
      success: (res) => {
        const ext = (localPath.split('.').pop() || 'jpg').toLowerCase()
        const mime = ext === 'png' ? 'image/png'
          : ext === 'gif' ? 'image/gif'
          : ext === 'webp' ? 'image/webp'
          : 'image/jpeg'
        selectedImage.value = `data:${mime};base64,${res.data}`
      },
      fail: () => {
        uni.showToast({ title: '图片读取失败', icon: 'none' })
      },
      complete: () => {
        processingImage.value = false
        uni.hideLoading()
      },
    })
  } catch (e) {
    processingImage.value = false
    uni.hideLoading()
    uni.showToast({ title: '图片读取失败', icon: 'none' })
  }
  // #endif
}

/** 清除已选图片 */
function clearSelectedImage() {
  selectedImage.value = ''
}

// ============ 会话管理 ============
const sessions = ref([])
const currentId = ref('')

const messages = computed(() => {
  const cur = sessions.value.find((s) => s.id === currentId.value)
  return cur ? cur.messages : []
})

const quickTips = ['现在有空闲电脑吗？', '会员有什么优惠？', '上机怎么收费？']

function uid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

function createSession() {
  return {
    id: uid(),
    title: '新会话',
    createTime: Date.now(),
    updateTime: Date.now(),
    messages: [],
    // true=已入库（id 为后端 sessionNo），false=本地临时会话（后端不可用时）
    persisted: false,
  }
}

/** 后端会话记录 → 前端会话结构 */
function mapDbSession(row) {
  return {
    id: row.sessionNo,
    title: row.title || '新对话',
    createTime: parseDbTime(row.createTime),
    updateTime: parseDbTime(row.updateTime),
    messages: [],
    persisted: true,
  }
}

/** thinking_json 字符串 → 前端步骤数组 */
function parseSteps(jsonStr) {
  if (!jsonStr) return []
  try {
    const arr = JSON.parse(jsonStr)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
}

/** 确保会话已入库：本地临时会话（persisted=false）→ 异步在后端建号并替换 id（幂等 + 并发锁） */
function ensureSessionPersisted(s) {
  if (!s || s.persisted) return Promise.resolve()
  if (s._persisting) return s._persisting
  s._persisting = createAiSession()
    .then((row) => {
      if (!row || !row.sessionNo) return
      const oldId = s.id
      s.id = row.sessionNo
      s.persisted = true
      s.createTime = parseDbTime(row.createTime) || s.createTime
      s.updateTime = parseDbTime(row.updateTime) || s.updateTime
      if (currentId.value === oldId) currentId.value = row.sessionNo
    })
    .catch(() => { /* 离线：保持本地临时会话 */ })
    .finally(() => { s._persisting = null })
  return s._persisting
}

/**
 * 消息入库（用户提问 / AI 回答统一走这里）：
 * 先确保会话已建到后端，再写入消息 —— 保证"发消息必入库"。
 */
function persistMsg(cur, msg) {
  if (!cur || !msg) return
  ensureSessionPersisted(cur).then(() => {
    if (cur.id && cur.persisted) {
      addAiMessage(cur.id, msg).catch(() => {})
    }
  })
}

/**
 * 本地缓存已停用：历史会话以数据库为准（用户要求清除本地缓存）。
 * 保留函数名仅避免改调用点；不再读写 storage。
 */
function saveSessions() { /* no-op：会话持久化由数据库负责 */ }

function loadSessions() {
  // 一次性清除历史本地缓存（用户要求：会话以数据库为准）
  try {
    uni.removeStorageSync(SESSIONS_KEY)
  } catch (e) { /* ignore */ }

  // 全部会话从数据库加载（未登录/未建会话时为空，兜底创建临时会话）
  listAiSessions()
    .then((list) => {
      const rows = Array.isArray(list) ? list : []
      sessions.value = rows.map(mapDbSession)
      if (!sessions.value.length) {
        const s = createSession()
        sessions.value.push(s)
        currentId.value = s.id
        ensureSessionPersisted(s)
      } else if (!sessions.value.some((s) => s.id === currentId.value)) {
        currentId.value = sessions.value[0].id
      }
    })
    .catch(() => {
      // 后端不可用：临时内存会话（不落本地缓存）
      const s = createSession()
      sessions.value = [s]
      currentId.value = s.id
    })
}

function newSession() {
  if (streaming.value) return
  const s = createSession()
  sessions.value.push(s)
  currentId.value = s.id
  showSessions.value = false
  saveSessions()
  scrollToBottom()
  // 异步在后端建会话，成功后用 sessionNo 替换本地 id（未登录/离线则保持本地）
  ensureSessionPersisted(s)
}

function switchSession(id) {
  if (streaming.value) return
  currentId.value = id
  showSessions.value = false
  saveSessions()
  const cur = sessions.value.find((s) => s.id === id)
  // 已入库但本地无消息的会话：从后端拉详情
  if (cur && cur.persisted && (!cur.messages || !cur.messages.length)) {
    getAiSessionDetail(id)
      .then((detail) => {
        const rows = detail && detail.messages ? detail.messages : []
        cur.messages = rows.map((r) => ({
          id: 'm' + r.id,
          role: r.role,
          content: r.content || '',
          steps: parseSteps(r.thinkingJson),
          showSteps: false,
          loading: false,
          costMs: r.costMs,
          model: r.model,
        }))
        saveSessions()
        nextFrame(() => scrollToBottom())
      })
      .catch(() => { /* 拉取失败：空会话展示 */ })
  }
  nextFrame(() => scrollToBottom())
}

function deleteSession(id) {
  if (streaming.value) return
  uni.showModal({
    title: '删除会话',
    content: '确定删除该历史会话吗？',
    success: (res) => {
      if (!res.confirm) return
      // 先删后端（失败静默，本地照删）
      deleteAiSession(id).catch(() => {})
      const idx = sessions.value.findIndex((s) => s.id === id)
      if (idx > -1) {
        sessions.value.splice(idx, 1)
        if (currentId.value === id) {
          currentId.value = sessions.value.length ? sessions.value[0].id : ''
          if (!currentId.value) newSession()
        }
        saveSessions()
      }
    },
  })
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const pad = (n) => (n < 10 ? '0' + n : '' + n)
  const hm = pad(d.getHours()) + ':' + pad(d.getMinutes())
  if (d.toDateString() === now.toDateString()) return '今天 ' + hm
  return pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + hm
}

// ============ 渲染辅助 ============
function renderNodes(m) {
  // 展示层统一过 SQL/敏感过滤：流式中显示 raw，完成后显示已过滤的 content
  const source = m.raw || m.content || ''
  return parseContent(cleanText(source))
}

function stepIcon(type) {
  return { tool: '🔧', tool_result: '📊', thinking: '🧠' }[type] || '💬'
}

// ============ 聊天逻辑 ============
function handleSend() {
  const text = input.value.trim()
  const hasImage = !!selectedImage.value
  if ((!text && !hasImage) || streaming.value) return

  const imagePath = selectedImage.value
  input.value = ''
  clearSelectedImage()

  // 1. 用户消息
  const userMsg = { id: uid(), role: 'user', content: text, image: imagePath || undefined }
  // 2. AI 占位消息
  // 必须用 reactive 创建：后续对流式内容的修改要能触发 Vue 响应式更新（否则界面不刷新）
  const aiMsg = reactive({
    id: uid(),
    role: 'assistant',
    raw: '',
    content: '',
    steps: [],
    showSteps: false,
    loading: true,
    costMs: null,
    model: '',
  })

  const cur = sessions.value.find((s) => s.id === currentId.value)
  if (!cur) return
  cur.messages.push(userMsg, aiMsg)
  // 用首条用户消息做会话标题
  const titleText = text || '图片消息'
  if (cur.title === '新会话') {
    cur.title = titleText.length > 18 ? titleText.slice(0, 18) + '…' : titleText
  }
  cur.updateTime = Date.now()
  saveSessions()
  // 用户消息即时入库（会话未建先建，保证"发了就入库"；失败静默）
  persistMsg(cur, userMsg)
  // 发送后滚动到用户刚发的问题位置（等 DOM 渲染完成）
  nextFrame(() => scrollToBottom(userMsg.id))

  streaming.value = true
  const history = buildHistory(cur.messages.slice(0, -2))
  const startTs = Date.now()

  abortRef = streamChat(
    { message: text, image: imagePath || undefined, history },
    {
      onStep: (step) => {
        // 面向普通用户：思考过程只显示友好进度，不展示原始查询数据/内部信息
        let label = ''
        let content = ''
        if (step.stepType === 'tool_call') {
          label = step.label && step.label.indexOf('KnowledgeSearch') > -1 ? '正在查阅业务规则…' : '正在查询数据…'
        } else if (step.stepType === 'tool_result') {
          label = '查询完成，正在生成回答…'
        } else {
          label = step.label || '正在处理…'
          content = cleanStepContent(step.content)
        }
        aiMsg.steps.push({ stepType: step.stepType, label, content })
        aiMsg.showSteps = true
        nextFrame(() => scrollToBottom())
      },
      onDelta: (chunk) => {
        aiMsg.raw += chunk
        aiMsg.loading = false
        nextFrame(() => scrollToBottom())
      },
      onDone: (full) => {
        finishAnswer(aiMsg, full || aiMsg.raw, startTs)
      },
      onError: (err) => {
        // 防重：回答已结束后不再处理错误回调
        if (!aiMsg.loading && aiMsg.content) return
        // 手动停止（兼容 H5/App 的 AbortError 与小程序 requestTask.abort 的错误形态）
        const isAbort =
          err &&
          (err.name === 'AbortError' ||
            /abort/i.test(err.errMsg || '') ||
            /abort/i.test(err.message || ''))
        if (isAbort) {
          if (aiMsg.raw) {
            aiMsg.raw += '\n\n[已停止生成]'
            finishAnswer(aiMsg, aiMsg.raw, startTs)
          } else {
            aiMsg.content = '[已停止生成]'
            aiMsg.loading = false
            streaming.value = false
            abortRef = null
            persistAnswer(aiMsg)
            persistAndScroll()
          }
          return
        }
        // 流式失败且还没有任何内容 → 尝试同步兜底
        if (!aiMsg.raw) {
          fallbackSync(text, history, aiMsg, startTs, imagePath)
        } else {
          aiMsg.raw += '\n\n[输出中断：' + (err.message || '网络错误') + ']'
          finishAnswer(aiMsg, aiMsg.raw, startTs)
        }
      },
    }
  )
}

/** 结束一次回答：写入过滤后的最终内容、耗时与模型信息 */
function finishAnswer(aiMsg, rawText, startTs) {
  aiMsg.content = cleanText(rawText)
  aiMsg.raw = ''
  aiMsg.loading = false
  aiMsg.costMs = Date.now() - startTs
  streaming.value = false
  abortRef = null
  persistAnswer(aiMsg)
  persistAndScroll()
}

/** AI 回答完成后入库（防重：_saved 标志；会话未建先建，保证回答也入库） */
function persistAnswer(aiMsg) {
  if (aiMsg._saved) return
  aiMsg._saved = true
  const cur = sessions.value.find((s) => s.id === currentId.value)
  persistMsg(cur, aiMsg)
}

/** 同步接口兜底（流式不可用时） */
async function fallbackSync(text, history, aiMsg, startTs, image) {
  try {
    const data = await sendChatSync(text, history, image)
    aiMsg.content = cleanText(data.reply || '')
    aiMsg.model = data.model || ''
    if (data.thinkingSteps && data.thinkingSteps.length) {
      aiMsg.steps = (data.thinkingSteps || []).map(() => ({
        stepType: 'tool_result',
        label: '已获取数据，正在生成回答…',
        content: '',
      }))
      aiMsg.showSteps = true
    }
    aiMsg.loading = false
    aiMsg.costMs = data.costMs || Date.now() - startTs
  } catch (err) {
    aiMsg.content = '请求失败：' + (err.message || '未知错误')
    aiMsg.loading = false
  }
  streaming.value = false
  abortRef = null
  persistAnswer(aiMsg)
  persistAndScroll()
}

function handleStop() {
  // 1. 中断底层请求（H5/App：AbortController；小程序：requestTask.abort）
  if (abortRef) {
    abortRef.abort()
    abortRef = null
  }
  // 2. 立即强制复位 UI，不依赖中断回调的触发时机
  forceStopCurrent()
}

/** 强制停止当前正在生成的 AI 消息（点击停止后的兜底，保证 UI 一定复位） */
function forceStopCurrent() {
  const cur = sessions.value.find((s) => s.id === currentId.value)
  if (!cur) return
  const aiMsg = [...cur.messages].reverse().find((m) => m.role === 'assistant' && m.loading)
  if (!aiMsg) return
  if (aiMsg.raw) {
    aiMsg.raw += '\n\n[已停止生成]'
    aiMsg.content = cleanText(aiMsg.raw)
    aiMsg.raw = ''
  } else {
    aiMsg.content = '[已停止生成]'
  }
  aiMsg.loading = false
  streaming.value = false
  abortRef = null
  persistAnswer(aiMsg)
  persistAndScroll()
}

function persistAndScroll() {
  const cur = sessions.value.find((s) => s.id === currentId.value)
  if (cur) cur.updateTime = Date.now()
  saveSessions()
  nextFrame(() => scrollToBottom())
}

// ============ 其他 ============
function quickSend(tip) {
  if (streaming.value) return
  input.value = tip
  handleSend()
}

/** 点击消息中的图片 → 预览大图 */
function previewImage(src) {
  if (!src) return
  uni.previewImage({
    urls: [src],
    current: src,
  })
}

const canGoBack = ref(false)

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  }
}

function scrollToBottom(targetId) {
  const list = messages.value
  const target = targetId || (list.length ? list[list.length - 1].id : '')
  if (!target) return
  // 先清空再赋值，确保 scroll-into-view 值变化触发滚动；
  // 用 nextTick 等新消息渲染完成后再滚动（否则目标节点还不存在，滚动无效）
  scrollIntoId.value = ''
  nextTick(() => {
    scrollIntoId.value = 'msg-' + target
  })
}

function nextFrame(fn) {
  setTimeout(fn, 50)
}

onMounted(() => {
  try {
    const info = uni.getSystemInfoSync()
    statusBarHeight.value = info.statusBarHeight || 20
    uni.setStorageSync(STATUS_BAR_KEY, statusBarHeight.value)
  } catch (e) {
    statusBarHeight.value = uni.getStorageSync(STATUS_BAR_KEY) || 20
  }
  canGoBack.value = getCurrentPages().length > 1
  loadSessions()
  nextFrame(() => scrollToBottom())
})
</script>

<style lang="scss" scoped>
.ai-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f6f7fb;
}

/* ===== 导航栏 ===== */
.nav-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: #ffffff;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}
.nav-inner {
  height: 88rpx;
  display: flex;
  align-items: center;
  padding: 0 24rpx;
}
.nav-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-icon {
  font-size: 52rpx;
  color: #1f2937;
  line-height: 1;
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 34rpx;
  font-weight: 600;
  color: #1f2937;
}
.nav-actions {
  width: 170rpx;
  display: flex;
  justify-content: flex-end;
  gap: 24rpx;
}
.nav-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rpx;
  padding: 6rpx 4rpx;
}
.nav-icon {
  width: 44rpx;
  height: 44rpx;
}
.nav-btn-text {
  font-size: 20rpx;
  color: #6b7280;
  line-height: 1.2;
}

/* ===== 消息列表 ===== */
.msg-list {
  flex: 1;
  height: 0;
  padding-bottom: 20rpx;
  padding-left: 24rpx;
  padding-right: 24rpx;
  box-sizing: content-box;
}
.list-bottom {
  height: 24rpx;
}

.welcome {
  padding-top: 120rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.welcome-icon {
  font-size: 100rpx;
}
.welcome-title {
  margin-top: 24rpx;
  font-size: 38rpx;
  font-weight: 600;
  color: #1f2937;
}
.welcome-desc {
  margin-top: 14rpx;
  font-size: 26rpx;
  color: #9ca3af;
  max-width: 520rpx;
}
.quick-tips {
  margin-top: 48rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  align-items: center;
}
.quick-tip {
  padding: 18rpx 36rpx;
  background: #ffffff;
  border: 2rpx solid #e5e7eb;
  border-radius: 40rpx;
  font-size: 28rpx;
  color: #4f6ef7;
  box-shadow: 0 4rpx 12rpx rgba(79, 110, 247, 0.06);
}

/* 消息行 */
.msg-row {
  display: flex;
  gap: 16rpx;
  margin-top: 28rpx;
  align-items: flex-start;
}
.msg-row.user {
  flex-direction: row-reverse;
}
.avatar {
  width: 68rpx;
  height: 68rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  color: #ffffff;
  flex-shrink: 0;
}
.avatar.user {
  background: #4f6ef7;
}
.avatar.assistant {
  background: #ffffff;
  border: 2rpx solid #eef0f4;
}
.bubble {
  max-width: 74%;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  font-size: 28rpx;
  line-height: 1.6;
  word-break: break-word;
}
.bubble.user {
  background: #4f6ef7;
  color: #ffffff;
  border-top-right-radius: 6rpx;
}
.bubble.assistant {
  background: #ffffff;
  color: #1f2937;
  border-top-left-radius: 6rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}
.user-text {
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-image {
  max-width: 320rpx;
  border-radius: 12rpx;
  margin-bottom: 12rpx;
  display: block;
}
.bubble.user .msg-image {
  border: 2rpx solid rgba(255, 255, 255, 0.3);
}
.answer {
  font-size: 28rpx;
  line-height: 1.7;
  width: 100%;
  word-break: break-word;
}

/* 结构化内容：文本 / 表格 / 列表 */
.md-text {
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: 8rpx;
}
.md-table {
  width: 100%;
  margin: 12rpx 0;
  border: 1rpx solid #e5e7eb;
  border-radius: 14rpx;
  overflow: hidden;
  background: #ffffff;
}
.md-tr {
  display: flex;
  flex-direction: row;
  width: 100%;
}
.md-th-row {
  background: #f3f4f8;
}
.md-th {
  flex: 1;
  min-width: 0;
  padding: 16rpx 12rpx;
  font-size: 25rpx;
  font-weight: 600;
  color: #4b5563;
  text-align: center;
  word-break: break-word;
}
.md-td {
  flex: 1;
  min-width: 0;
  padding: 14rpx 12rpx;
  font-size: 25rpx;
  color: #374151;
  text-align: center;
  border-top: 1rpx solid #f1f3f6;
  word-break: break-word;
}
.md-row-alt {
  background: #fafbfd;
}
.md-list {
  margin: 8rpx 0;
}
.md-li {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  padding: 8rpx 0;
}
.md-li-mark {
  width: 44rpx;
  flex-shrink: 0;
  font-size: 27rpx;
  font-weight: 600;
  color: #6b7bd1;
}
.md-li-text {
  flex: 1;
  font-size: 27rpx;
  color: #374151;
  line-height: 1.6;
  word-break: break-word;
}

/* 思考过程 */
.thinking-box {
  margin-bottom: 16rpx;
  border-left: 6rpx solid #c7d2fe;
  padding-left: 16rpx;
}
.thinking-head {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 0;
}
.thinking-arrow {
  font-size: 22rpx;
  color: #6b7280;
}
.thinking-label {
  font-size: 24rpx;
  color: #6b7280;
}
.thinking-body {
  margin-top: 8rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.step-item {
  background: #f8fafc;
  border-radius: 12rpx;
  padding: 12rpx 16rpx;
}
.step-label {
  font-size: 24rpx;
  font-weight: 600;
  color: #374151;
}
.step-text {
  display: block;
  margin-top: 6rpx;
  font-size: 23rpx;
  color: #6b7280;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 打字动画 */
.typing {
  display: flex;
  gap: 8rpx;
  padding: 8rpx 0;
}
.dot {
  width: 12rpx;
  height: 12rpx;
  background: #c4b5fd;
  border-radius: 50%;
  animation: blink 1.2s infinite both;
}
.dot:nth-child(2) {
  animation-delay: 0.2s;
}
.dot:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}

.meta {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #9ca3af;
}

/* ===== 输入区 ===== */
.input-wrapper {
  background: #ffffff;
  border-top: 2rpx solid #f0f0f0;
}
.selected-image-bar {
  padding: 16rpx 24rpx 0;
}
.selected-image-preview {
  position: relative;
  display: inline-block;
  border-radius: 12rpx;
  overflow: hidden;
  border: 2rpx solid #e5e7eb;
}
.selected-image {
  width: 120rpx;
  height: 120rpx;
  display: block;
}
.remove-image-btn {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  text-align: center;
  background: rgba(0, 0, 0, 0.6);
  color: #ffffff;
  border-radius: 50%;
  font-size: 24rpx;
}
.input-bar {
  display: flex;
  align-items: flex-end;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
}
.image-pick-btn {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f3f4f6;
  border-radius: 50%;
  flex-shrink: 0;
}
.image-pick-btn.disabled {
  opacity: 0.5;
}
.image-pick-icon {
  font-size: 40rpx;
}
.input-area {
  flex: 1;
  height: 88rpx;
  min-height: 88rpx;
  max-height: 200rpx;
  background: #f3f4f6;
  border-radius: 44rpx;
  padding: 20rpx 32rpx;
  font-size: 28rpx;
  line-height: 1.4;
}
.send-btn,
.stop-btn {
  width: 140rpx;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 28rpx;
  color: #ffffff;
  text-align: center;
  padding: 0;
  margin: 0;
}
.send-btn {
  background: #4f6ef7;
}
.send-btn.disabled {
  background: #c7d2fe;
}
.stop-btn {
  background: #ef4444;
}

/* ===== 历史会话面板 ===== */
.mask {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  justify-content: flex-end;
}
.session-panel {
  width: 76%;
  height: 100%;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  animation: slideIn 0.25s ease;
}
@keyframes slideIn {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 32rpx 20rpx;
}
.panel-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #1f2937;
}
.panel-close {
  font-size: 36rpx;
  color: #9ca3af;
  padding: 8rpx;
}
.session-list {
  flex: 1;
  height: 0;
  padding: 0 24rpx;
}
.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 20rpx;
  border-radius: 16rpx;
  margin-bottom: 12rpx;
  background: #f9fafb;
}
.session-item.active {
  background: #eef1fe;
  border: 2rpx solid #c7d2fe;
}
.session-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  overflow: hidden;
}
.session-title {
  font-size: 28rpx;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-time {
  font-size: 22rpx;
  color: #9ca3af;
}
.session-del {
  font-size: 24rpx;
  color: #ef4444;
  padding: 8rpx 12rpx;
}
.session-empty {
  text-align: center;
  padding-top: 120rpx;
  color: #9ca3af;
  font-size: 26rpx;
}
.panel-footer {
  padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
}
.new-session-btn {
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  background: #4f6ef7;
  color: #ffffff;
  font-size: 30rpx;
  text-align: center;
  padding: 0;
  margin: 0;
}
</style>
