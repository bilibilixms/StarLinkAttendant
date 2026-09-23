<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, watch } from 'vue'
import { ChatLineSquare, Promotion, Delete, Setting, Picture } from '@element-plus/icons-vue'
import { sendChatStreamSSE, sendChat, getAiConfig, uploadMinerUDoc, listMinerUFiles, getMinerUFileContent, createAiSession, listAiSessions, getAiSessionDetail, addAiMessage, deleteAiSession, parseDbTime } from '../api'
import type { ChatMessage, ChatRequest, ThinkingStep, MinerUFileInfo, AiSessionRow } from '../api'
import newChatIcon from '../img_data/new-chat.png'
import historyIcon from '../img_data/lishihuihua.png'
import uploadIcon from '../img_data/shangchuan.png'
import aiAvatarImg from '../img_data/a-aizhushou_huaban1fuben15_huaban1fuben15-copy.png'
import { ElMessage } from 'element-plus'

// 用于中断流式请求
let abortController: AbortController | null = null

/** 简单 Markdown 渲染 */
function renderMarkdown(text: string): string {
  if (!text) return ''
  return text
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/\n/g, '<br>')
}

// ============ 聊天状态 ============
interface DisplayMessage {
  role: 'user' | 'assistant'
  content: string
  image?: string  // 用户发送的图片（base64 dataURL）
  loading?: boolean
  costMs?: number
  model?: string
  thinkingSteps?: ThinkingStep[]
  showThinking?: boolean
}

const MESSAGES_STORAGE_KEY = 'ai-chat-messages'
const SESSIONS_STORAGE_KEY = 'ai-chat-sessions'

const messages = ref<DisplayMessage[]>([])
const input = ref('')
const sending = ref(false)
const chatBoxRef = ref<HTMLElement | null>(null)

// ============ 多会话管理（数据库为准，本地不再持久化） ============
interface ChatSession {
  id: string
  title: string
  updateTime: number
  messages: DisplayMessage[]
  /** true=已入库（id 为后端 sessionNo），false=本地临时会话（后端不可用时） */
  persisted?: boolean
}

const sessions = ref<ChatSession[]>([])
const currentSessionId = ref('')
const historyVisible = ref(false)

function uid(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

function currentSession(): ChatSession | undefined {
  return sessions.value.find((s) => s.id === currentSessionId.value)
}

/** 更新会话标题（首条用户消息）与时间戳；不再写 localStorage（历史以数据库为准） */
function persistSession() {
  const cur = currentSession()
  if (!cur) return
  cur.updateTime = Date.now()
  if ((cur.title === '新对话' || !cur.title) && messages.value.length) {
    const first = messages.value.find((m) => m.role === 'user')
    if (first) cur.title = first.content.slice(0, 18)
  }
}

/** 后端会话记录 → 前端会话结构 */
function mapDbSession(row: AiSessionRow): ChatSession {
  return {
    id: row.sessionNo,
    title: row.title || '新对话',
    updateTime: parseDbTime(row.updateTime),
    messages: [],
    persisted: true,
  }
}

/** thinking_json 字符串 → 前端思考步骤数组 */
function parseSteps(jsonStr: string | null): ThinkingStep[] {
  if (!jsonStr) return []
  try {
    const arr = JSON.parse(jsonStr)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

/** 确保会话已入库：本地临时会话（persisted=false）→ 后端建号并替换 id（幂等 + 并发锁） */
function ensureSessionPersisted(s: ChatSession): Promise<void> {
  if (!s || s.persisted) return Promise.resolve()
  if ((s as any)._persisting) return (s as any)._persisting as Promise<void>
  ;(s as any)._persisting = createAiSession(s.title === '新对话' ? undefined : s.title)
    .then((row) => {
      if (!row || !row.sessionNo) return
      const oldId = s.id
      s.id = row.sessionNo
      s.persisted = true
      s.updateTime = parseDbTime(row.updateTime) || s.updateTime
      if (currentSessionId.value === oldId) currentSessionId.value = row.sessionNo
    })
    .catch(() => { /* 离线：保持本地临时会话 */ })
    .finally(() => { (s as any)._persisting = null })
  return (s as any)._persisting as Promise<void>
}

/** 消息入库（用户提问 / AI 回答统一走这里）：先确保会话建到后端，再写入消息 */
function persistMsg(cur: ChatSession | undefined, msg: DisplayMessage) {
  if (!cur || !msg) return
  ensureSessionPersisted(cur).then(() => {
    if (cur.id && cur.persisted) {
      addAiMessage(cur.id, {
        role: msg.role,
        content: msg.content || '',
        thinkingJson: msg.thinkingSteps && msg.thinkingSteps.length ? JSON.stringify(msg.thinkingSteps) : null,
        costMs: msg.costMs ?? null,
        model: msg.model ?? null,
      }).catch(() => {})
    }
  })
}

function switchSession(id: string) {
  const s = sessions.value.find((x) => x.id === id)
  if (!s) return
  currentSessionId.value = id
  messages.value = s.messages
  historyVisible.value = false
  nextTick(() => scrollToBottom())
  // 已入库但本地无消息：从后端拉详情
  if (s.persisted && (!s.messages || !s.messages.length)) {
    getAiSessionDetail(id)
      .then((detail) => {
        const rows = detail && detail.messages ? detail.messages : []
        s.messages = rows.map((r) => ({
          role: r.role,
          content: r.content || '',
          thinkingSteps: r.thinkingJson ? parseSteps(r.thinkingJson) : [],
          showThinking: false,
          loading: false,
          costMs: r.costMs ?? undefined,
          model: r.model ?? undefined,
        }))
        if (currentSessionId.value === id) messages.value = s.messages
        nextTick(() => scrollToBottom())
      })
      .catch(() => { /* 拉取失败：空会话展示 */ })
  }
}

function newSession() {
  const s: ChatSession = { id: uid(), title: '新对话', updateTime: Date.now(), messages: [], persisted: false }
  sessions.value.push(s)
  currentSessionId.value = s.id
  messages.value = s.messages
  historyVisible.value = false
}

function deleteSession(id: string) {
  // 先删后端（失败静默，本地照删）
  deleteAiSession(id).catch(() => {})
  const idx = sessions.value.findIndex((s) => s.id === id)
  if (idx === -1) return
  sessions.value.splice(idx, 1)
  if (currentSessionId.value === id) {
    if (sessions.value.length) {
      const next = sessions.value[Math.min(idx, sessions.value.length - 1)]
      currentSessionId.value = next.id
      messages.value = next.messages
    } else {
      newSession()
    }
  }
}

function formatTime(ts: number): string {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const pad = (n: number) => (n < 10 ? '0' + n : '' + n)
  const hm = pad(d.getHours()) + ':' + pad(d.getMinutes())
  if (d.toDateString() === now.toDateString()) return '今天 ' + hm
  return pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + hm
}

/** 从数据库加载会话（历史以数据库为准；顺带清除历史本地缓存） */
function loadSessions() {
  try { localStorage.removeItem(SESSIONS_STORAGE_KEY) } catch { /* ignore */ }
  try { localStorage.removeItem(MESSAGES_STORAGE_KEY) } catch { /* ignore */ }
  listAiSessions()
    .then((list) => {
      const rows = Array.isArray(list) ? list : []
      sessions.value = rows.map(mapDbSession)
      if (!sessions.value.length) {
        newSession()
      } else {
        currentSessionId.value = sessions.value[0].id
        messages.value = sessions.value[0].messages
      }
    })
    .catch(() => { newSession() })
}

watch(messages, () => persistSession(), { deep: true })

// ============ 设置状态 ============
const STORAGE_KEY = 'ai-chat-settings'
const settingsVisible = ref(false)
const serverConfigured = ref(false) // 服务端是否已配置 apiKey

const settings = reactive({
  provider: 'custom',   // 预设服务商
  baseUrl: '',
  apiKey: '',
  model: '',
})

/** 预设服务商列表 */
const PROVIDERS = [
  { label: 'OpenAI',    value: 'openai',   baseUrl: 'https://api.openai.com',       models: ['gpt-4o-mini', 'gpt-4o', 'gpt-3.5-turbo'] },
  { label: 'DeepSeek',  value: 'deepseek', baseUrl: 'https://api.deepseek.com',      models: ['deepseek-chat', 'deepseek-reasoner'] },
  { label: '通义千问',   value: 'qwen',     baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode', models: ['qwen-turbo', 'qwen-plus', 'qwen-max'] },
  { label: 'Ollama (本地)', value: 'ollama', baseUrl: 'http://localhost:11434',       models: ['qwen2.5', 'llama3', 'mistral'] },
  { label: '自定义',     value: 'custom',   baseUrl: '',                             models: [] },
]

const currentProvider = ref(PROVIDERS[0])
const modelInput = ref('') // 自定义模型名输入框

function onProviderChange(val: string) {
  const p = PROVIDERS.find(x => x.value === val)
  if (p) {
    currentProvider.value = p
    settings.baseUrl = p.baseUrl
    if (p.models.length > 0) {
      settings.model = p.models[0]
      modelInput.value = ''
    } else {
      settings.model = ''
      modelInput.value = ''
    }
  }
}

function onModelPresetChange(val: string) {
  if (val === '__custom__') {
    settings.model = modelInput.value
  } else {
    settings.model = val
    modelInput.value = ''
  }
}

/** 保存设置到 localStorage */
function saveSettings() {
  const data = {
    provider: settings.provider,
    baseUrl: settings.baseUrl,
    apiKey: settings.apiKey,
    model: settings.model,
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
  settingsVisible.value = false
}

/** 重置为服务端默认配置 */
function resetSettings() {
  localStorage.removeItem(STORAGE_KEY)
  settings.provider = 'custom'
  settings.baseUrl = ''
  settings.apiKey = ''
  settings.model = ''
  modelInput.value = ''
  currentProvider.value = PROVIDERS[4]
  settingsVisible.value = false
}

/** 从 localStorage 加载设置 */
function loadSettings() {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (raw) {
    try {
      const data = JSON.parse(raw)
      Object.assign(settings, data)
      const p = PROVIDERS.find(x => x.value === data.provider)
      if (p) currentProvider.value = p
    } catch { /* ignore */ }
  }
}

/** 获取请求级覆盖参数 */
function getRequestOverrides(): Pick<ChatRequest, 'model' | 'baseUrl' | 'apiKey'> {
  const overrides: any = {}
  if (settings.model) overrides.model = settings.model
  if (settings.baseUrl) overrides.baseUrl = settings.baseUrl
  if (settings.apiKey) overrides.apiKey = settings.apiKey
  return overrides
}

/** 当前显示用的模型名 */
const displayModel = ref('')

onMounted(async () => {
  loadSettings()
  loadSessions()
  if (messages.value.length > 0) scrollToBottom()
  try {
    const res = await getAiConfig()
    serverConfigured.value = res.data.apiKeyConfigured
    if (!settings.model) displayModel.value = res.data.model
    if (!settings.baseUrl) settings.baseUrl = res.data.baseUrl || ''
  } catch {
    // 接口不可用时忽略
  }
})

watch(() => settings.model, (v) => { displayModel.value = v || '服务端默认' })

// ============ 聊天逻辑 ============

async function scrollToBottom() {
  await nextTick()
  if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
}

function getHistory(): ChatMessage[] {
  return messages.value
    .filter(m => !m.loading)
    .map(m => ({ role: m.role as 'user' | 'assistant', content: m.content }))
}

/** 组合用户问题与文档引用：如果有引用文档，拼上前缀 */
function buildSendText(userInput: string): string {
  if (!referencedDoc.value) return userInput
  return `请根据知识库中的《${referencedDoc.value}》回答：${userInput}`
}

async function handleSend() {
  const userInput = input.value.trim()
  if (!userInput || sending.value) return

  const text = buildSendText(userInput)
  sending.value = true
  const userMsg: DisplayMessage = { role: 'user', content: text }
  messages.value.push(userMsg)
  const aiMsg = reactive({ role: 'assistant' as const, content: '', loading: true, thinkingSteps: [] as ThinkingStep[], showThinking: false, costMs: undefined as number | undefined, model: undefined as string | undefined })
  messages.value.push(aiMsg)
  input.value = ''
  clearReferencedDoc()
  await scrollToBottom()

  // 用户提问即时入库（会话未建先建，保证"发了就入库"）
  persistMsg(currentSession(), userMsg)

  try {
    const res = await sendChat({
      message: text,
      history: getHistory().slice(0, -1),
      ...getRequestOverrides(),
    })
    aiMsg.content = res.data.reply
    aiMsg.loading = false
    aiMsg.costMs = res.data.costMs
    aiMsg.model = res.data.model
    aiMsg.thinkingSteps = res.data.thinkingSteps || []
    aiMsg.showThinking = aiMsg.thinkingSteps.length > 0
    persistMsg(currentSession(), aiMsg)
  } catch (err: any) {
    aiMsg.content = '请求失败: ' + (err.message || '未知错误')
    aiMsg.loading = false
    persistMsg(currentSession(), aiMsg)
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

async function handleSendStream() {
  const userInput = input.value.trim()
  const hasImage = !!selectedImage.value
  if ((!userInput && !hasImage) || sending.value) return

  const text = buildSendText(userInput)
  const imageData = selectedImage.value
  sending.value = true
  const userMsg: DisplayMessage = { role: 'user', content: text, image: imageData || undefined }
  messages.value.push(userMsg)
  const last = reactive({ role: 'assistant' as const, content: '', loading: true, thinkingSteps: [] as ThinkingStep[], showThinking: false, costMs: undefined as number | undefined, model: undefined as string | undefined })
  messages.value.push(last)
  input.value = ''
  clearReferencedDoc()
  clearSelectedImage()
  await scrollToBottom()

  // 用户提问即时入库（会话未建先建，保证"发了就入库"）
  persistMsg(currentSession(), userMsg)

  // 创建 AbortController 用于中断请求
  abortController = new AbortController()

  await sendChatStreamSSE(
    {
      message: text,
      image: imageData || undefined,
      history: getHistory().slice(0, -1),
      ...getRequestOverrides(),
    },
    // step 事件：思考步骤
    (step) => {
      last.thinkingSteps.push({
        type: step.stepType === 'tool_result' ? 'tool_result' : 'tool',
        content: step.content || '',
        label: step.label,
      })
    },
    // delta 事件：答案增量
    (chunk) => {
      last.content += chunk
      last.loading = false
      scrollToBottom()
    },
    // done 事件：结束
    (full) => {
      last.content = full
      last.loading = false
      last.showThinking = last.thinkingSteps.length > 0
      persistMsg(currentSession(), last)
      scrollToBottom()
    },
    // error
    (err) => {
      if (err.name === 'AbortError') {
        last.content += '\n\n[已手动停止]'
      } else {
        last.content = '请求失败: ' + (err.message || '未知错误')
      }
      last.loading = false
      persistMsg(currentSession(), last)
    },
    abortController?.signal,
  )

  sending.value = false
  abortController = null
  await scrollToBottom()
}

/** 停止生成 */
function handleStop() {
  if (abortController) {
    abortController.abort()
  }
}

function handleClear() {
  const cur = currentSession()
  if (cur) {
    cur.messages = []
    messages.value = cur.messages
  } else {
    messages.value = []
  }
}

function stepIcon(type: string): string {
  const icons: Record<string, string> = {
    thinking: '🧠', sql: '📝', sql_result: '📊', tool: '🔧', tool_result: '📦',
  }
  return icons[type] || '💬'
}

// ============ MinerU 文档转换 ============
const minerUVisible = ref(false)
const minerUConverting = ref(false)
const minerUOcr = ref(false)
const minerUFiles = ref<MinerUFileInfo[]>([])
const minerUPreview = ref('')
const minerUPreviewName = ref('')
const minerUPreviewVisible = ref(false)

/** 当前引用的文档（点击"提问"后设置，发送时拼到问题前） */
const referencedDoc = ref<string>('')

/** 清除文档引用 */
function clearReferencedDoc() {
  referencedDoc.value = ''
}

/** 待发送的图片（base64 dataURL） */
const selectedImage = ref<string>('')
const imageInputRef = ref<HTMLInputElement | null>(null)

/** 点击图片按钮 → 打开文件选择 */
function triggerImagePicker() {
  imageInputRef.value?.click()
}

/** 选择图片后处理 */
function onImageChange(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  if (!/^image\/(png|jpe?g|gif|webp)$/i.test(file.type)) {
    ElMessage.warning('仅支持 PNG / JPG / GIF / WebP 图片')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }
  const reader = new FileReader()
  reader.onload = (ev) => {
    selectedImage.value = ev.target?.result as string
  }
  reader.readAsDataURL(file)
  // 清空 input，允许重复选同一张图
  target.value = ''
}

/** 清除已选图片 */
function clearSelectedImage() {
  selectedImage.value = ''
}

/** 打开对话框时刷新已转换文档列表 */
async function openMinerU() {
  minerUVisible.value = true
  await refreshMinerUFiles()
}

async function refreshMinerUFiles() {
  try {
    const res = await listMinerUFiles()
    minerUFiles.value = res.data || []
  } catch { /* 接口不可用时忽略 */ }
}

/** 选择文件后直接开始转换（el-upload 触发） */
async function onMinerUFileChange(uploadFile: any) {
  const file: File | undefined = uploadFile?.raw
  if (!file) return
  const okExt = /\.(pdf|doc|docx|txt)$/i.test(file.name)
  if (!okExt) {
    ElMessage.warning('仅支持 pdf / doc / docx / txt 文件')
    return
  }
  minerUConverting.value = true
  ElMessage.info(`正在转换「${file.name}」，大文件可能需要几分钟…`)
  const result = await uploadMinerUDoc(file, minerUOcr.value, (err) => {
    ElMessage.error(err.message || '转换失败')
  })
  minerUConverting.value = false
  if (result) {
    ElMessage.success(result.message || '转换成功')
    await refreshMinerUFiles()
  }
}

/** 查看已转换文档内容 */
async function previewMinerUFile(name: string) {
  try {
    const res = await getMinerUFileContent(name)
    minerUPreview.value = res.data.content
    minerUPreviewName.value = res.data.name
    minerUPreviewVisible.value = true
  } catch {
    ElMessage.error('读取文档内容失败')
  }
}

/** 引用文档：设置引用标签，关闭弹窗，等用户自己输入问题后发送 */
function askMinerUFile(name: string) {
  const base = name.replace(/\.md$/i, '')
  referencedDoc.value = base
  minerUVisible.value = false
  minerUPreviewVisible.value = false
  // 聚焦输入框，方便用户直接打字
  nextTick(() => {
    const textarea = document.querySelector('.chat-input textarea') as HTMLTextAreaElement
    textarea?.focus()
  })
}

function formatFileSize(n: number): string {
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(2) + ' MB'
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSendStream() }
}
</script>

<template>
  <div class="ai-chat-container">
    <!-- 头部 -->
    <div class="chat-header">
      <div class="header-left">
        <el-icon :size="22" color="var(--color-primary)"><ChatLineSquare /></el-icon>
        <h3>AI 智能助手</h3>
        <el-tag size="small" effect="plain" :type="serverConfigured || settings.apiKey ? 'success' : 'warning'">
          {{ serverConfigured || settings.apiKey ? '在线' : '未配置Key' }}
        </el-tag>
        <el-tag v-if="displayModel" size="small" effect="plain" type="info" class="model-tag">
          {{ displayModel }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-button size="small" plain class="icon-btn" @click="newSession">
          <img :src="newChatIcon" class="btn-icon" alt="新建" /> 新建对话
        </el-button>
        <el-button size="small" plain class="icon-btn" @click="historyVisible = true">
          <img :src="historyIcon" class="btn-icon" alt="历史" /> 历史对话
        </el-button>
        <el-button size="small" plain class="icon-btn" @click="openMinerU">
          <img :src="uploadIcon" class="btn-icon" alt="转换" /> 文档转换
        </el-button>
        <el-button :icon="Setting" size="small" plain @click="settingsVisible = true">
          设置
        </el-button>
        <el-button :icon="Delete" size="small" type="danger" plain
          :disabled="messages.length === 0" @click="handleClear">
          清空对话
        </el-button>
      </div>
    </div>

    <!-- 消息区域 -->
    <div ref="chatBoxRef" class="chat-messages">
      <div v-if="messages.length === 0" class="welcome">
        <el-icon :size="48" color="var(--color-primary-light)"><ChatLineSquare /></el-icon>
        <h4>星络灵侍馆 AI 助手</h4>
        <p>我可以帮您解答关于上机管理、会员、计费、库存、经营分析等问题。</p>
        <div class="quick-tips">
          <el-tag v-for="tip in ['今日上机率怎么查？', '如何配置计费方案？', '库存预警规则是什么？']"
            :key="tip" class="tip-tag" effect="plain" @click="input = tip">{{ tip }}</el-tag>
        </div>
      </div>

      <div v-for="(msg, idx) in messages" :key="idx" :class="['message-row', msg.role]">
        <div class="avatar">
          <el-avatar :size="36"
            :src="msg.role === 'assistant' ? aiAvatarImg : ''"
            :style="msg.role === 'user' ? { backgroundColor: 'var(--color-primary)' } : { backgroundColor: '#fff', border: '1px solid #e5e7eb' }">
            {{ msg.role === 'user' ? '我' : 'AI' }}
          </el-avatar>
        </div>
        <div class="bubble">
          <!-- 用户发送的图片 -->
          <div v-if="msg.image" class="msg-image-wrapper">
            <img :src="msg.image" class="msg-image" alt="用户发送的图片" />
          </div>
          <!-- 思考过程（可折叠） -->
          <div v-if="msg.thinkingSteps && msg.thinkingSteps.length > 0" class="thinking-section">
            <div class="thinking-toggle" @click="msg.showThinking = !msg.showThinking">
              <span class="thinking-icon">💡</span>
              <span>思考过程（{{ msg.thinkingSteps.length }} 步）</span>
              <span class="thinking-arrow">{{ msg.showThinking ? '▲' : '▼' }}</span>
            </div>
            <div v-if="msg.showThinking" class="thinking-steps">
              <div v-for="(step, si) in msg.thinkingSteps" :key="si" :class="['thinking-step', 'step-' + step.type]">
                <div class="step-label">
                  <span class="step-badge">{{ stepIcon(step.type) }}</span>
                  {{ step.label }}
                </div>
                <div class="step-content">
                  <pre v-if="step.type === 'sql'" class="step-sql"><code>{{ step.content }}</code></pre>
                  <div v-else-if="step.type === 'sql_result'" class="step-result" v-html="renderMarkdown(step.content)" />
                  <div v-else v-html="renderMarkdown(step.content)" />
                </div>
              </div>
            </div>
          </div>
          <!-- 最终回答 -->
          <div v-if="msg.content" class="bubble-content" v-html="renderMarkdown(msg.content)" />
          <div v-if="msg.loading" class="typing-indicator"><span></span><span></span><span></span></div>
          <div v-if="msg.costMs" class="meta-info">{{ msg.model }} · 耗时 {{ msg.costMs }}ms</div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-wrapper">
      <!-- 引用文档标签 -->
      <div v-if="referencedDoc" class="referenced-doc-bar">
        <el-tag closable size="small" type="primary" effect="plain" @close="clearReferencedDoc">
          📄 引用文档：《{{ referencedDoc }}》
        </el-tag>
        <span class="referenced-hint">请输入您的具体问题，发送时将自动基于该文档回答</span>
      </div>
      <!-- 已选图片预览 -->
      <div v-if="selectedImage" class="selected-image-bar">
        <div class="selected-image-preview">
          <img :src="selectedImage" alt="已选图片" />
          <span class="remove-image-btn" @click="clearSelectedImage">×</span>
        </div>
      </div>
      <div class="chat-input">
        <!-- 隐藏的文件选择器 -->
        <input ref="imageInputRef" type="file" accept="image/*" style="display:none" @change="onImageChange" />
        <el-button circle class="image-pick-btn" :disabled="sending" title="发送图片" @click="triggerImagePicker">
          <el-icon :size="18"><Picture /></el-icon>
        </el-button>
        <el-input v-model="input" type="textarea" :rows="2"
          :placeholder="referencedDoc ? `围绕《${referencedDoc}》输入您的问题...` : '输入您的问题，Enter 发送，Shift+Enter 换行...'"
          :disabled="sending" @keydown="handleKeydown" resize="none" />
        <el-button v-if="!sending" :icon="Promotion" type="primary" :disabled="!input.trim() && !selectedImage"
          @click="handleSendStream" class="send-btn">发送</el-button>
        <el-button v-else type="danger" @click="handleStop" class="send-btn">停止</el-button>
      </div>
    </div>

    <!-- ===== 历史会话抽屉 ===== -->
    <el-drawer v-model="historyVisible" title="历史对话" size="320px">
      <div class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          :class="['session-item', { active: s.id === currentSessionId }]"
          @click="switchSession(s.id)"
        >
          <div class="session-info">
            <div class="session-title">{{ s.title || '新对话' }}</div>
            <div class="session-time">{{ formatTime(s.updateTime) }} · {{ s.messages.length }} 条消息</div>
          </div>
          <el-button link type="danger" size="small" @click.stop="deleteSession(s.id)">删除</el-button>
        </div>
        <el-empty v-if="!sessions.length" description="暂无历史对话" />
      </div>
      <template #footer>
        <el-button type="primary" plain @click="newSession" class="session-new-btn">
          <img :src="newChatIcon" class="btn-icon" alt="新建" /> 新建对话
        </el-button>
      </template>
    </el-drawer>

    <!-- ===== 设置弹窗 ===== -->
    <el-dialog v-model="settingsVisible" title="AI 模型设置" width="520px" :close-on-click-modal="false">
      <el-form label-width="90px" label-position="top">
        <!-- 服务商预设 -->
        <el-form-item label="服务商">
          <el-select v-model="settings.provider" @change="onProviderChange" style="width: 100%">
            <el-option v-for="p in PROVIDERS" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>

        <!-- API 基础地址 -->
        <el-form-item label="API 地址">
          <el-input v-model="settings.baseUrl" placeholder="https://api.openai.com"
            :disabled="settings.provider !== 'custom'" />
        </el-form-item>

        <!-- API Key -->
        <el-form-item label="API Key">
          <el-input v-model="settings.apiKey" type="password" show-password
            placeholder="sk-...（留空则使用服务端配置）" />
          <div class="form-hint">不填写时使用服务端 application.yml 中配置的 Key</div>
        </el-form-item>

        <!-- 模型选择 -->
        <el-form-item label="模型">
          <template v-if="currentProvider.models.length > 0">
            <el-select v-model="settings.model" @change="onModelPresetChange" style="width: 100%"
              filterable allow-create default-first-option>
              <el-option v-for="m in currentProvider.models" :key="m" :label="m" :value="m" />
              <el-option label="✏️ 自定义输入..." value="__custom__" />
            </el-select>
            <el-input v-if="settings.model === '__custom__' || !currentProvider.models.includes(settings.model)"
              v-model="modelInput" placeholder="输入模型名称" style="margin-top: 6px"
              @input="settings.model = modelInput" />
          </template>
          <template v-else>
            <el-input v-model="settings.model" placeholder="输入模型名称，如 qwen2.5" />
          </template>
          <div class="form-hint">留空则使用服务端默认模型</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="resetSettings">重置为默认</el-button>
        <el-button @click="settingsVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSettings">保存设置</el-button>
      </template>
    </el-dialog>

    <!-- MinerU 文档转换对话框 -->
    <el-dialog v-model="minerUVisible" title="MinerU 文档转换" width="640px" :close-on-click-modal="false">
      <div class="minerU-body">
        <el-upload
          drag
          :auto-upload="false"
          :show-file-list="false"
          accept=".pdf,.doc,.docx,.txt"
          :disabled="minerUConverting"
          :on-change="onMinerUFileChange"
        >
          <div class="minerU-upload-area">
            <img :src="uploadIcon" class="minerU-upload-icon" alt="上传" />
            <div class="minerU-upload-title">
              {{ minerUConverting ? '正在转换中…' : '点击或拖拽文档到此处上传' }}
            </div>
            <div class="minerU-upload-hint">
              支持 PDF / Word（doc、docx）/ txt，转换后自动加入 AI 知识库
            </div>
          </div>
        </el-upload>

        <div class="minerU-options">
          <el-switch v-model="minerUOcr" :disabled="minerUConverting" />
          <span class="minerU-option-label">OCR 识别（扫描件/图片型 PDF 建议开启）</span>
        </div>

        <div class="minerU-section-title">已转换文档（{{ minerUFiles.length }}）</div>
        <div v-if="minerUFiles.length === 0" class="minerU-empty">暂无转换文档，上传后 AI 即可回答文档内容</div>
        <div v-else class="minerU-file-list">
          <div v-for="f in minerUFiles" :key="f.name" class="minerU-file-item">
            <span class="minerU-file-name" :title="f.name">{{ f.name }}</span>
            <span class="minerU-file-meta">{{ formatFileSize(f.size) }}</span>
            <el-button size="small" text type="primary" @click="previewMinerUFile(f.name)">查看</el-button>
            <el-button size="small" text type="success" @click="askMinerUFile(f.name)">提问</el-button>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 已转换文档内容预览 -->
    <el-dialog v-model="minerUPreviewVisible" :title="minerUPreviewName" width="720px">
      <div class="minerU-preview">{{ minerUPreview }}</div>
      <template #footer>
        <el-button @click="minerUPreviewVisible = false">关闭</el-button>
        <el-button type="primary" @click="askMinerUFile(minerUPreviewName)">就此文档提问</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ai-chat-container {
  display: flex; flex-direction: column; height: calc(100vh - 120px);
  background: #fff; border-radius: var(--radius-lg); box-shadow: var(--shadow-md); overflow: hidden;
}
.chat-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; border-bottom: 1px solid var(--color-border); background: #fafbfc;
}
.header-left { display: flex; align-items: center; gap: 8px; }
.header-left h3 { margin: 0; font-size: 16px; font-weight: 600; color: var(--color-text-primary); }
.header-right { display: flex; gap: 8px; }
.model-tag { max-width: 160px; overflow: hidden; text-overflow: ellipsis; }

.icon-btn { display: inline-flex; align-items: center; }
.btn-icon { width: 15px; height: 15px; margin-right: 5px; vertical-align: -2px; }

/* 历史会话列表 */
.session-list { display: flex; flex-direction: column; gap: 10px; }
.session-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 12px; border: 1px solid var(--color-border);
  border-radius: var(--radius-md); cursor: pointer;
  transition: all var(--transition-fast);
}
.session-item:hover { border-color: var(--color-primary); }
.session-item.active { border-color: var(--color-primary); background: rgba(99, 102, 241, 0.06); }
.session-info { flex: 1; min-width: 0; margin-right: 8px; }
.session-title { font-size: 14px; font-weight: 600; color: var(--color-text-primary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-time { font-size: 12px; color: var(--color-text-muted); margin-top: 2px; }
.session-new-btn { width: 100%; }

.chat-messages { flex: 1; overflow-y: auto; padding: 20px 24px; background: #f8fafc; scroll-behavior: smooth; }
.welcome { display: flex; flex-direction: column; align-items: center; justify-content: center;
  height: 100%; color: var(--color-text-secondary); text-align: center; }
.welcome h4 { margin: 16px 0 8px; font-size: 18px; color: var(--color-text-primary); }
.welcome p { margin: 0 0 20px; font-size: 14px; max-width: 400px; }
.quick-tips { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.tip-tag { cursor: pointer; transition: all var(--transition-fast); }
.tip-tag:hover { color: var(--color-primary); border-color: var(--color-primary); }

.message-row { display: flex; gap: 12px; margin-bottom: 16px; align-items: flex-start; }
.message-row.user { flex-direction: row-reverse; }
.message-row.user .bubble { background: var(--color-primary); color: #fff;
  border-radius: var(--radius-lg) var(--radius-sm) var(--radius-lg) var(--radius-lg); }
.message-row.assistant .bubble { background: #fff; color: var(--color-text-primary);
  border-radius: var(--radius-sm) var(--radius-lg) var(--radius-lg) var(--radius-lg); box-shadow: var(--shadow-sm); }

.bubble { max-width: 65%; padding: 12px 16px; line-height: 1.65; font-size: 14px; word-break: break-word; }
.bubble-content :deep(pre) { background: #1e293b; color: #e2e8f0; padding: 10px 14px;
  border-radius: 6px; overflow-x: auto; margin: 8px 0; font-size: 13px; }
.bubble-content :deep(code) { background: rgba(0,0,0,0.06); padding: 2px 5px; border-radius: 4px; font-size: 13px; }
.bubble-content :deep(pre code) { background: none; padding: 0; }
.meta-info { margin-top: 6px; font-size: 11px; opacity: 0.5; }

.typing-indicator { display: flex; gap: 4px; padding: 4px 0; }
.typing-indicator span { width: 7px; height: 7px; background: var(--color-text-muted);
  border-radius: 50%; animation: typing 1.4s infinite both; }
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing { 0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; } 40% { transform: scale(1); opacity: 1; } }

.chat-input-wrapper { border-top: 1px solid var(--color-border); background: #fff; }
.referenced-doc-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 20px 0;
}
.referenced-hint { font-size: 12px; color: var(--color-text-muted); }

/* 已选图片预览 */
.selected-image-bar { padding: 8px 20px 0; }
.selected-image-preview {
  position: relative; display: inline-block;
  border: 1px solid var(--color-border); border-radius: var(--radius-md);
  overflow: hidden;
}
.selected-image-preview img {
  width: 80px; height: 80px; object-fit: cover; display: block;
}
.remove-image-btn {
  position: absolute; top: 2px; right: 2px;
  width: 18px; height: 18px; line-height: 16px; text-align: center;
  background: rgba(0,0,0,0.6); color: #fff; border-radius: 50%;
  font-size: 14px; cursor: pointer;
}
.remove-image-btn:hover { background: rgba(0,0,0,0.8); }

.chat-input { display: flex; align-items: flex-end; gap: 12px; padding: 10px 20px 14px; background: #fff; }
.image-pick-btn { flex-shrink: 0; margin-bottom: 4px; }

/* 用户消息中的图片 */
.msg-image-wrapper { margin-bottom: 8px; }
.msg-image {
  max-width: 220px; max-height: 220px; border-radius: var(--radius-md);
  display: block;
}
.message-row.user .msg-image { border: 2px solid rgba(255,255,255,0.3); }
.chat-input :deep(.el-textarea__inner) { border-radius: var(--radius-md); font-size: 14px; box-shadow: none; }
.send-btn { height: 54px; min-width: 80px; border-radius: var(--radius-md); font-size: 14px; }

/* 思考过程 */
.thinking-section { margin-bottom: 10px; }
.thinking-toggle {
  display: flex; align-items: center; gap: 6px; cursor: pointer;
  font-size: 13px; color: var(--color-text-secondary); padding: 6px 10px;
  background: #f1f5f9; border-radius: var(--radius-sm); transition: background var(--transition-fast);
}
.thinking-toggle:hover { background: #e2e8f0; }
.thinking-icon { font-size: 15px; }
.thinking-arrow { margin-left: auto; font-size: 11px; }
.thinking-steps {
  margin-top: 8px; border-left: 3px solid var(--color-primary-light);
  padding-left: 12px; display: flex; flex-direction: column; gap: 8px;
}
.thinking-step {
  padding: 8px 10px; background: #f8fafc; border-radius: var(--radius-sm);
  font-size: 13px; line-height: 1.5;
}
.step-label {
  font-weight: 600; color: var(--color-text-primary); margin-bottom: 4px;
  display: flex; align-items: center; gap: 4px;
}
.step-badge { font-size: 14px; }
.step-content { color: var(--color-text-secondary); }
.step-sql {
  background: #1e293b; color: #e2e8f0; padding: 8px 12px;
  border-radius: 6px; overflow-x: auto; margin: 4px 0; font-size: 12px;
}
.step-sql code { background: none; color: inherit; padding: 0; }
.step-result { }
.step-result :deep(table) { width: 100%; border-collapse: collapse; font-size: 12px; }
.step-result :deep(th), .step-result :deep(td) {
  padding: 3px 6px; border: 1px solid #e2e8f0; text-align: left;
}
.step-result :deep(th) { background: #f1f5f9; font-weight: 600; }
.step-thinking { border-left: 2px solid #818cf8; }
.step-sql { border-left: 2px solid #22c55e; }
.step-sql_result { border-left: 2px solid #f59e0b; }
.step-tool { border-left: 2px solid #3b82f6; }
.step-tool_result { border-left: 2px solid #64748b; }

/* 设置弹窗 */
.form-hint { font-size: 12px; color: var(--color-text-muted); margin-top: 4px; line-height: 1.4; }

/* MinerU 文档转换 */
.minerU-body { display: flex; flex-direction: column; gap: 14px; }
.minerU-upload-area { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 24px 0; }
.minerU-upload-icon { width: 44px; height: 44px; margin-bottom: 10px; }
.minerU-upload-title { font-size: 14px; font-weight: 600; color: var(--color-text-primary); }
.minerU-upload-hint { font-size: 12px; color: var(--color-text-muted); margin-top: 6px; }
.minerU-options { display: flex; align-items: center; gap: 8px; }
.minerU-option-label { font-size: 13px; color: var(--color-text-secondary); }
.minerU-section-title { font-size: 14px; font-weight: 600; color: var(--color-text-primary); margin-top: 4px; }
.minerU-empty { font-size: 13px; color: var(--color-text-muted); padding: 10px 0; }
.minerU-file-list { display: flex; flex-direction: column; gap: 6px; max-height: 220px; overflow-y: auto; }
.minerU-file-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; border: 1px solid var(--color-border);
  border-radius: var(--radius-md); background: #fafbfc;
}
.minerU-file-name { flex: 1; min-width: 0; font-size: 13px; color: var(--color-text-primary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.minerU-file-meta { font-size: 12px; color: var(--color-text-muted); flex-shrink: 0; }
.minerU-preview {
  max-height: 55vh; overflow-y: auto; white-space: pre-wrap; word-break: break-all;
  font-size: 13px; line-height: 1.7; color: var(--color-text-primary);
  background: #f8fafc; border: 1px solid var(--color-border); border-radius: var(--radius-md);
  padding: 12px 14px;
}

.chat-messages::-webkit-scrollbar { width: 6px; }
.chat-messages::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }
.chat-messages::-webkit-scrollbar-track { background: transparent; }
</style>
