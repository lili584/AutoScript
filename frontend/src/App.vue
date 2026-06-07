<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import {
  BarChart3,
  Bot,
  BookOpen,
  ChevronDown,
  ClipboardPlus,
  Download,
  Eye,
  FileText,
  FileUp,
  Layers,
  ListTree,
  Plus,
  RefreshCw,
  Save,
  Scissors,
  Sparkles,
  Trophy,
  Trash2,
  Upload,
  X,
} from 'lucide-vue-next'

const activeWorkspace = ref('novels')
const novels = ref([])
const selectedNovel = ref(null)
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const createFileInput = ref(null)
const contentFileInput = ref(null)
const contentEditor = ref(null)
const pendingFileMode = ref('append')
const chapters = ref([])
const expandedChapterIds = ref(new Set())
const parseSummary = ref(null)
const scriptTask = ref(null)
const scriptScenes = ref([])
const yamlPreview = ref('')
const yamlFileName = ref('')
const pollingTimer = ref(null)
const evaluationFileInput = ref(null)
const evaluationSelectedNovelId = ref('')
const evaluationFiles = ref([])
const evaluating = ref(false)

const form = reactive({
  title: '',
  description: '',
  content: '',
})

const contentDraft = ref('')
const appendDraft = ref('')

const selectedId = computed(() => selectedNovel.value?.id)
const hasNovels = computed(() => novels.value.length > 0)
const outlineItems = computed(() => parseMarkdownOutline(contentDraft.value))
const chunkCount = computed(() => chapters.value.reduce((total, chapter) => total + chapter.chunkCount, 0))
const evaluationResults = computed(() => evaluationFiles.value.filter((item) => item.status === 'succeeded' && item.report))
const evaluationSummary = computed(() => {
  const results = evaluationResults.value
  if (results.length === 0) {
    return null
  }

  const sorted = [...results].sort((left, right) => scoreOf(right.report) - scoreOf(left.report))
  const total = results.reduce((sum, item) => sum + scoreOf(item.report), 0)
  return {
    best: sorted[0],
    lowest: sorted[sorted.length - 1],
    average: Math.round((total / results.length) * 10) / 10,
    passedCount: results.filter((item) => isReportPassed(item.report)).length,
    totalCount: results.length,
  }
})
const evaluationMetricExtremes = computed(() => {
  const extremes = {}
  for (const item of evaluationResults.value) {
    const metrics = item.report?.scorecard?.metrics || []
    for (const metric of metrics) {
      if (!metric?.key) {
        continue
      }
      const score = Number(metric.score || 0)
      const current = extremes[metric.key] || {
        highest: score,
        lowest: score,
      }
      current.highest = Math.max(current.highest, score)
      current.lowest = Math.min(current.lowest, score)
      extremes[metric.key] = current
    }
  }
  return extremes
})
const canEvaluateYaml = computed(() => {
  return evaluationSelectedNovelId.value && evaluationFiles.value.length > 0 && evaluationFiles.value.length <= 5 && !evaluating.value
})

onMounted(() => {
  loadNovels()
})

onUnmounted(() => {
  stopPolling()
})

async function request(path, options = {}) {
  const isFormData = options.body instanceof FormData
  const headers = isFormData
    ? options.headers || {}
    : {
      'Content-Type': 'application/json',
      ...options.headers,
    }

  const response = await fetch(path, {
    ...options,
    headers,
  })
  const result = await response.json()
  if (result.code >= 400) {
    throw new Error(result.msg || '请求失败')
  }
  return result.data
}

function validateMarkdownFile(file) {
  if (!file) {
    return false
  }
  if (!file.name.toLowerCase().endsWith('.md')) {
    errorMessage.value = '只支持上传 .md 文件'
    return false
  }
  return true
}

function validateYamlFiles(files) {
  if (files.length === 0) {
    errorMessage.value = '请选择至少 1 个 YAML 文件'
    return false
  }
  if (files.length > 5) {
    errorMessage.value = '一次最多测评 5 个 YAML 文件'
    return false
  }
  const invalidFile = files.find((file) => !/\.(ya?ml)$/i.test(file.name))
  if (invalidFile) {
    errorMessage.value = `只支持上传 .yaml 或 .yml 文件：${invalidFile.name}`
    return false
  }
  return true
}

function buildMarkdownFormData(file, extra = {}) {
  const data = new FormData()
  data.append('file', file)
  Object.entries(extra).forEach(([key, value]) => {
    if (value) {
      data.append(key, value)
    }
  })
  return data
}

function selectEvaluationFiles(event) {
  clearMessage()
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!validateYamlFiles(files)) {
    return
  }
  evaluationFiles.value = files.map((file, index) => ({
    id: `${Date.now()}-${index}`,
    file,
    name: file.name,
    size: file.size,
    status: 'waiting',
    report: null,
    error: '',
  }))
}

function clearEvaluationFiles() {
  evaluationFiles.value = []
}

async function evaluateYamlFiles() {
  clearMessage()
  if (!evaluationSelectedNovelId.value) {
    errorMessage.value = '请先选择小说'
    return
  }
  if (!validateYamlFiles(evaluationFiles.value.map((item) => item.file))) {
    return
  }

  evaluating.value = true
  try {
    for (const item of evaluationFiles.value) {
      item.status = 'running'
      item.error = ''
      item.report = null
      try {
        const data = new FormData()
        data.append('file', item.file)
        item.report = await request(`/api/novels/${evaluationSelectedNovelId.value}/evaluations/yaml`, {
          method: 'POST',
          body: data,
        })
        item.status = 'succeeded'
      } catch (error) {
        item.status = 'failed'
        item.error = error.message
      }
    }
    successMessage.value = 'YAML 测评完成'
  } finally {
    evaluating.value = false
  }
}

async function loadNovels() {
  loading.value = true
  clearMessage()
  try {
    novels.value = await request('/api/novels')
    if (selectedId.value) {
      const stillExists = novels.value.some((novel) => novel.id === selectedId.value)
      if (!stillExists) {
        selectedNovel.value = null
        contentDraft.value = ''
      }
    }
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function createNovel() {
  clearMessage()
  if (!form.title.trim()) {
    errorMessage.value = '请输入小说标题'
    return
  }

  saving.value = true
  try {
    const novel = await request('/api/novels', {
      method: 'POST',
      body: JSON.stringify({
        title: form.title.trim(),
        description: form.description.trim(),
        content: form.content,
      }),
    })
    resetForm()
    await loadNovels()
    await selectNovel(novel.id)
    successMessage.value = '小说已创建'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

async function createNovelFromFile(event) {
  clearMessage()
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!validateMarkdownFile(file)) {
    return
  }

  saving.value = true
  try {
    const novel = await request('/api/novels/import/file', {
      method: 'POST',
      body: buildMarkdownFormData(file, {
        title: form.title.trim(),
        description: form.description.trim(),
      }),
    })
    resetForm()
    await loadNovels()
    await selectNovel(novel.id)
    successMessage.value = 'Markdown 文件已导入为新小说'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

async function selectNovel(id) {
  clearMessage()
  loading.value = true
  try {
    selectedNovel.value = await request(`/api/novels/${id}`)
    contentDraft.value = selectedNovel.value.content || ''
    resetYamlState()
    await loadChapters(id)
    await loadScriptState(id)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function loadScriptState(id = selectedId.value) {
  if (!id) {
    resetScriptState()
    return
  }

  try {
    scriptTask.value = await request(`/api/novels/${id}/scripts/tasks/latest`)
    scriptScenes.value = await request(`/api/novels/${id}/scripts/scenes`)
    if (scriptTask.value && ['pending', 'running'].includes(scriptTask.value.status)) {
      startPolling()
    }
  } catch (error) {
    scriptTask.value = null
    scriptScenes.value = []
  }
}

async function loadChapters(id = selectedId.value) {
  if (!id) {
    chapters.value = []
    parseSummary.value = null
    return
  }

  try {
    chapters.value = await request(`/api/novels/${id}/chapters`)
    parseSummary.value = chapters.value.length
      ? {
        chapterCount: chapters.value.length,
        chunkCount: chunkCount.value,
      }
      : null
  } catch (error) {
    chapters.value = []
    parseSummary.value = null
  }
}

async function parseChapters() {
  if (!selectedId.value) {
    return
  }

  clearMessage()
  saving.value = true
  try {
    const result = await request(`/api/novels/${selectedId.value}/chapters/parse`, {
      method: 'POST',
    })
    chapters.value = result.chapters || []
    parseSummary.value = {
      chapterCount: result.chapterCount,
      chunkCount: result.chunkCount,
    }
    expandedChapterIds.value = new Set(chapters.value.slice(0, 1).map((chapter) => chapter.id))
    successMessage.value = `解析完成：${result.chapterCount} 章，${result.chunkCount} 个分块`
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

async function saveContent() {
  if (!selectedId.value) {
    return
  }

  clearMessage()
  saving.value = true
  try {
    selectedNovel.value = await request(`/api/novels/${selectedId.value}/content`, {
      method: 'PUT',
      body: JSON.stringify({ content: contentDraft.value }),
    })
    contentDraft.value = selectedNovel.value.content || ''
    chapters.value = []
    parseSummary.value = null
    resetScriptState()
    successMessage.value = '原始文本已保存'
    await loadNovels()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

async function appendText() {
  if (!selectedId.value) {
    return
  }

  clearMessage()
  if (!appendDraft.value.trim()) {
    errorMessage.value = '请输入要追加的文本'
    return
  }

  saving.value = true
  try {
    selectedNovel.value = await request(`/api/novels/${selectedId.value}/content/append`, {
      method: 'POST',
      body: JSON.stringify({ content: appendDraft.value }),
    })
    contentDraft.value = selectedNovel.value.content || ''
    appendDraft.value = ''
    chapters.value = []
    parseSummary.value = null
    resetScriptState()
    successMessage.value = '文本已追加'
    await loadNovels()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

function triggerContentFileUpload(mode) {
  pendingFileMode.value = mode
  contentFileInput.value?.click()
}

async function saveContentFromFile(event) {
  clearMessage()
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!selectedId.value || !validateMarkdownFile(file)) {
    return
  }

  saving.value = true
  try {
    selectedNovel.value = await request(`/api/novels/${selectedId.value}/content/file`, {
      method: 'POST',
      body: buildMarkdownFormData(file, { mode: pendingFileMode.value }),
    })
    contentDraft.value = selectedNovel.value.content || ''
    chapters.value = []
    parseSummary.value = null
    resetScriptState()
    successMessage.value = pendingFileMode.value === 'overwrite' ? 'Markdown 文件已覆盖原始文本' : 'Markdown 文件已追加'
    await loadNovels()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

async function deleteNovel(id) {
  clearMessage()
  const novel = novels.value.find((item) => item.id === id)
  const confirmed = window.confirm(`确认删除《${novel?.title || '该小说'}》吗？`)
  if (!confirmed) {
    return
  }

  saving.value = true
  try {
    await request(`/api/novels/${id}`, { method: 'DELETE' })
    if (selectedId.value === id) {
      selectedNovel.value = null
      contentDraft.value = ''
      chapters.value = []
      parseSummary.value = null
      resetScriptState()
    }
    await loadNovels()
    successMessage.value = '小说已删除'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

function resetForm() {
  form.title = ''
  form.description = ''
  form.content = ''
}

function toggleChapter(id) {
  const next = new Set(expandedChapterIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedChapterIds.value = next
}

async function startAiAnalysis() {
  if (!selectedId.value) {
    return
  }

  clearMessage()
  saving.value = true
  try {
    scriptScenes.value = []
    resetYamlState()
    scriptTask.value = await request(`/api/novels/${selectedId.value}/scripts/generate`, {
      method: 'POST',
    })
    successMessage.value = 'AI 分析任务已创建'
    startPolling()
  } catch (error) {
    errorMessage.value = error.message
    window.alert(error.message)
  } finally {
    saving.value = false
  }
}

async function clearScriptScenes() {
  if (!selectedId.value) {
    return
  }

  clearMessage()
  saving.value = true
  try {
    await request(`/api/novels/${selectedId.value}/scripts/scenes`, { method: 'DELETE' })
    scriptScenes.value = []
    resetYamlState()
    successMessage.value = 'AI 场景草稿已清空'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

function startPolling() {
  stopPolling()
  pollingTimer.value = window.setInterval(async () => {
    if (!selectedId.value) {
      stopPolling()
      return
    }
    try {
      scriptTask.value = await request(`/api/novels/${selectedId.value}/scripts/tasks/latest`)
      if (!scriptTask.value || !['pending', 'running'].includes(scriptTask.value.status)) {
        stopPolling()
        scriptScenes.value = await request(`/api/novels/${selectedId.value}/scripts/scenes`)
        resetYamlState()
      }
    } catch (error) {
      stopPolling()
      errorMessage.value = error.message
    }
  }, 2000)
}

function stopPolling() {
  if (pollingTimer.value) {
    window.clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
}

function resetScriptState() {
  stopPolling()
  scriptTask.value = null
  scriptScenes.value = []
  resetYamlState()
}

function resetYamlState() {
  yamlPreview.value = ''
  yamlFileName.value = ''
}

async function previewYaml() {
  if (!selectedId.value) {
    return
  }

  clearMessage()
  saving.value = true
  try {
    const preview = await request(`/api/novels/${selectedId.value}/scripts/yaml/preview`)
    yamlPreview.value = preview.content || ''
    yamlFileName.value = preview.fileName || ''
    successMessage.value = 'YAML 已生成预览'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

async function downloadYaml() {
  if (!selectedId.value) {
    return
  }

  clearMessage()
  saving.value = true
  try {
    const response = await fetch(`/api/novels/${selectedId.value}/scripts/yaml/download`)
    if (!response.ok) {
      throw new Error(await response.text() || '下载 YAML 失败')
    }
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = extractDownloadFileName(response) || yamlFileName.value || `${selectedNovel.value?.title || '剧本'}-剧本.yaml`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    successMessage.value = 'YAML 已开始下载'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

function extractDownloadFileName(response) {
  const disposition = response.headers.get('Content-Disposition') || response.headers.get('content-disposition')
  if (!disposition) {
    return ''
  }

  const encodedMatch = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (encodedMatch?.[1]) {
    try {
      return decodeURIComponent(encodedMatch[1])
    } catch {
      return encodedMatch[1]
    }
  }

  const quotedMatch = disposition.match(/filename="([^"]+)"/i)
  if (quotedMatch?.[1]) {
    return quotedMatch[1]
  }

  const plainMatch = disposition.match(/filename=([^;]+)/i)
  return plainMatch?.[1]?.trim() || ''
}

function taskStatusText(status) {
  const statusMap = {
    pending: '等待中',
    running: '分析中',
    succeeded: '已完成',
    failed: '失败',
  }
  return statusMap[status] || '未开始'
}

function evaluationStatusText(status) {
  const statusMap = {
    waiting: '等待测评',
    running: '测评中',
    succeeded: '已完成',
    failed: '失败',
  }
  return statusMap[status] || '等待测评'
}

function scoreOf(report) {
  return Number(report?.overallScore || 0)
}

function isReportPassed(report) {
  if (typeof report?.passed === 'boolean') {
    return report.passed
  }
  const metrics = report?.scorecard?.metrics || []
  return scoreOf(report) >= 80 && metrics.every((metric) => Number(metric.score || 0) >= metricThreshold(metric))
}

function metricThreshold(metric) {
  return Number(metric?.threshold ?? 80)
}

function scoreClass(score) {
  if (Number(score) >= 85) {
    return 'score-high'
  }
  if (Number(score) >= 70) {
    return 'score-medium'
  }
  return 'score-low'
}

function overallRankLabel(item) {
  if (!evaluationSummary.value || evaluationResults.value.length < 2 || !item.report) {
    return ''
  }
  const score = scoreOf(item.report)
  if (score === scoreOf(evaluationSummary.value.best.report)) {
    return '最高'
  }
  if (score === scoreOf(evaluationSummary.value.lowest.report)) {
    return '最低'
  }
  return ''
}

function metricRankLabel(metric) {
  if (evaluationResults.value.length < 2 || !metric?.key) {
    return ''
  }
  const extreme = evaluationMetricExtremes.value[metric.key]
  if (!extreme || extreme.highest === extreme.lowest) {
    return ''
  }
  const score = Number(metric.score || 0)
  if (score === extreme.highest) {
    return '最高'
  }
  if (score === extreme.lowest) {
    return '最低'
  }
  return ''
}

function metricRankClass(metric) {
  const label = metricRankLabel(metric)
  return {
    'metric-best': label === '最高',
    'metric-worst': label === '最低',
  }
}

function formatFileSize(size) {
  if (!size) {
    return '0 KB'
  }
  if (size < 1024 * 1024) {
    return `${Math.ceil(size / 1024)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function clearMessage() {
  errorMessage.value = ''
  successMessage.value = ''
}

function formatDate(value) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function parseMarkdownOutline(content) {
  if (!content) {
    return []
  }

  const lines = content.split(/\r?\n/)
  let offset = 0
  const items = []
  lines.forEach((line, index) => {
    const match = /^(#{1,2})\s+(.+)$/.exec(line)
    if (match) {
      items.push({
        id: `${index}-${offset}`,
        level: match[1].length,
        title: match[2].trim(),
        line: index + 1,
        start: offset,
        end: offset + line.length,
      })
    }
    offset += line.length + 1
  })
  return items
}

function jumpToOutline(item) {
  const editor = contentEditor.value
  if (!editor) {
    return
  }
  editor.focus()
  editor.setSelectionRange(item.start, item.end)

  const lineHeight = Number.parseFloat(window.getComputedStyle(editor).lineHeight) || 22
  editor.scrollTop = Math.max(0, (item.line - 3) * lineHeight)
}
</script>

<template>
  <div class="app-shell">
    <nav class="workspace-switcher" aria-label="工作区切换">
      <button
        type="button"
        :class="{ active: activeWorkspace === 'novels' }"
        @click="activeWorkspace = 'novels'"
      >
        <BookOpen :size="18" />
        小说管理
      </button>
      <button
        type="button"
        :class="{ active: activeWorkspace === 'evaluation' }"
        @click="activeWorkspace = 'evaluation'"
      >
        <BarChart3 :size="18" />
        测评对比
      </button>
    </nav>

  <main v-if="activeWorkspace === 'novels'" class="workspace">
    <aside class="sidebar">
      <header class="panel-header">
        <div>
          <p class="eyebrow">AutoScript</p>
          <h1>小说管理</h1>
        </div>
        <button class="icon-button" type="button" title="刷新列表" @click="loadNovels">
          <RefreshCw :size="18" />
        </button>
      </header>

      <section class="create-panel">
        <h2><Plus :size="18" /> 新建小说</h2>
        <form @submit.prevent="createNovel">
          <label>
            <span>标题</span>
            <input v-model="form.title" type="text" placeholder="输入小说标题" />
          </label>
          <label>
            <span>简介</span>
            <input v-model="form.description" type="text" placeholder="可选" />
          </label>
          <label>
            <span>原始文本</span>
            <textarea v-model="form.content" rows="6" placeholder="# 小说标题&#10;&#10;## 第一章&#10;&#10;从这里开始输入正文" />
          </label>
          <button class="primary-button" type="submit" :disabled="saving">
            <Plus :size="18" />
            创建小说
          </button>
          <input
            ref="createFileInput"
            class="file-input"
            type="file"
            accept=".md,text/markdown"
            @change="createNovelFromFile"
          />
          <button class="secondary-button" type="button" :disabled="saving" @click="createFileInput?.click()">
            <Upload :size="18" />
            上传 md 创建
          </button>
        </form>
      </section>

      <section class="list-panel">
        <div class="section-title">
          <h2><BookOpen :size="18" /> 小说列表</h2>
          <span>{{ novels.length }}</span>
        </div>

        <div v-if="loading && !hasNovels" class="empty-state">加载中...</div>
        <div v-else-if="!hasNovels" class="empty-state">暂无小说</div>
        <ul v-else class="novel-list">
          <li v-for="novel in novels" :key="novel.id">
            <button
              class="novel-item"
              :class="{ active: selectedId === novel.id }"
              type="button"
              @click="selectNovel(novel.id)"
            >
              <strong>{{ novel.title }}</strong>
              <span>{{ novel.description || '未填写简介' }}</span>
              <small>更新于 {{ formatDate(novel.updatedAt) }}</small>
            </button>
            <button class="icon-button danger" type="button" title="删除小说" @click="deleteNovel(novel.id)">
              <Trash2 :size="17" />
            </button>
          </li>
        </ul>
      </section>
    </aside>

    <section class="detail">
      <div v-if="errorMessage" class="message error">{{ errorMessage }}</div>
      <div v-if="successMessage" class="message success">{{ successMessage }}</div>

      <div v-if="!selectedNovel" class="placeholder">
        <FileText :size="44" />
        <h2>选择一部小说查看详情</h2>
      </div>

      <template v-else>
        <header class="detail-header">
          <div>
            <p class="eyebrow">小说详情</p>
            <h2>{{ selectedNovel.title }}</h2>
            <p>{{ selectedNovel.description || '未填写简介' }}</p>
          </div>
          <div class="meta">
            <span>创建 {{ formatDate(selectedNovel.createdAt) }}</span>
            <span>更新 {{ formatDate(selectedNovel.updatedAt) }}</span>
          </div>
        </header>

        <section class="import-panel">
          <div class="section-title">
            <h2><FileUp :size="18" /> Markdown 导入</h2>
          </div>
          <input
            ref="contentFileInput"
            class="file-input"
            type="file"
            accept=".md,text/markdown"
            @change="saveContentFromFile"
          />
          <div class="button-row">
            <button class="secondary-button" type="button" :disabled="saving" @click="triggerContentFileUpload('overwrite')">
              <Upload :size="17" />
              上传覆盖
            </button>
            <button class="secondary-button" type="button" :disabled="saving" @click="triggerContentFileUpload('append')">
              <ClipboardPlus :size="17" />
              上传追加
            </button>
          </div>
          <label>
            <span>追加文本</span>
            <textarea v-model="appendDraft" rows="4" placeholder="输入要追加到原始文本末尾的内容" />
          </label>
          <button class="secondary-button align-start" type="button" :disabled="saving" @click="appendText">
            <ClipboardPlus :size="17" />
            追加文本
          </button>
        </section>

        <div class="editor-toolbar">
          <h3>原始文本</h3>
          <div class="button-row">
            <button class="secondary-button compact" type="button" :disabled="saving" @click="parseChapters">
              <Scissors :size="17" />
              解析章节
            </button>
            <button class="primary-button compact" type="button" :disabled="saving" @click="saveContent">
              <Save :size="17" />
              保存
            </button>
          </div>
        </div>

        <section class="chapter-panel">
          <div class="section-title">
            <h2><Layers :size="18" /> 章节分块</h2>
            <span>{{ parseSummary ? `${parseSummary.chapterCount}/${parseSummary.chunkCount}` : '0/0' }}</span>
          </div>
          <div v-if="chapters.length === 0" class="outline-empty">点击解析章节生成分块</div>
          <ul v-else class="chapter-list">
            <li v-for="chapter in chapters" :key="chapter.id" class="chapter-item">
              <button class="chapter-header" type="button" @click="toggleChapter(chapter.id)">
                <ChevronDown :class="{ collapsed: !expandedChapterIds.has(chapter.id) }" :size="18" />
                <strong>{{ chapter.orderIndex }}. {{ chapter.title }}</strong>
                <span>{{ chapter.chunkCount }} 个分块</span>
              </button>
              <div v-if="expandedChapterIds.has(chapter.id)" class="chunk-list">
                <div v-for="chunk in chapter.chunks" :key="chunk.id" class="chunk-item">
                  <div>
                    <strong>Chunk {{ chunk.chunkIndex }}</strong>
                    <span>{{ chunk.charCount }} 字 · 段落 {{ chunk.paragraphStart }}-{{ chunk.paragraphEnd }}</span>
                  </div>
                  <small>{{ chunk.hasContext ? '含上下文' : '无上下文' }}</small>
                </div>
              </div>
            </li>
          </ul>
        </section>

        <section class="ai-panel">
          <div class="section-title">
            <h2><Bot :size="18" /> AI 分析</h2>
            <span>{{ scriptScenes.length }}</span>
          </div>

          <div class="ai-actions">
            <button class="primary-button compact" type="button" :disabled="saving" @click="startAiAnalysis">
              <Sparkles :size="17" />
              开始 AI 分析
            </button>
            <button class="secondary-button compact" type="button" :disabled="saving || scriptScenes.length === 0" @click="clearScriptScenes">
              <Trash2 :size="17" />
              清空草稿
            </button>
          </div>

          <div v-if="scriptTask" class="task-card">
            <div>
              <strong>{{ taskStatusText(scriptTask.status) }}</strong>
              <span>{{ scriptTask.processedChunks }} / {{ scriptTask.totalChunks }} chunks</span>
            </div>
            <div class="progress">
              <i :style="{ width: `${scriptTask.progressPercent || 0}%` }" />
            </div>
            <p v-if="scriptTask.errorMessage">{{ scriptTask.errorMessage }}</p>
          </div>
          <div v-else class="outline-empty">解析章节后可开始 AI 场景抽取</div>

          <ul v-if="scriptScenes.length > 0" class="scene-list">
            <li v-for="scene in scriptScenes" :key="scene.id" class="scene-item">
              <div class="scene-main">
                <strong>{{ scene.title }}</strong>
                <span>{{ scene.location || '未知地点' }} · {{ scene.timeOfDay || '未知时间' }}</span>
                <p>{{ scene.summary || '暂无概要' }}</p>
              </div>
              <div class="scene-meta">
                <span>{{ scene.characters?.join('、') || '未识别人物' }}</span>
                <small>{{ scene.beatsCount }} beats</small>
              </div>
            </li>
          </ul>

          <div v-if="scriptScenes.length > 0" class="yaml-export">
            <div class="ai-actions">
              <button class="secondary-button compact" type="button" :disabled="saving" @click="previewYaml">
                <Eye :size="17" />
                预览 YAML
              </button>
              <button class="primary-button compact" type="button" :disabled="saving" @click="downloadYaml">
                <Download :size="17" />
                下载 YAML
              </button>
            </div>
            <textarea
              v-if="yamlPreview"
              v-model="yamlPreview"
              class="yaml-preview"
              spellcheck="false"
              readonly
            />
          </div>
        </section>

        <div class="editor-grid">
          <textarea
            ref="contentEditor"
            v-model="contentDraft"
            class="content-editor"
            spellcheck="false"
            placeholder="# 小说标题&#10;&#10;## 第一章&#10;&#10;输入或粘贴小说正文"
          />

          <aside class="outline-panel">
            <div class="section-title">
              <h2><ListTree :size="18" /> 文档大纲</h2>
              <span>{{ outlineItems.length }}</span>
            </div>
            <div v-if="outlineItems.length === 0" class="outline-empty">暂无一级或二级标题</div>
            <ul v-else class="outline-list">
              <li v-for="item in outlineItems" :key="item.id">
                <button
                  class="outline-item"
                  :class="{ child: item.level === 2 }"
                  type="button"
                  @click="jumpToOutline(item)"
                >
                  <span>{{ item.title }}</span>
                  <small>第 {{ item.line }} 行</small>
                </button>
              </li>
            </ul>
          </aside>
        </div>
      </template>
    </section>
  </main>
  <main v-else class="evaluation-workspace">
    <div v-if="errorMessage" class="message error">{{ errorMessage }}</div>
    <div v-if="successMessage" class="message success">{{ successMessage }}</div>

    <header class="detail-header evaluation-header">
      <div>
        <p class="eyebrow">Evaluation</p>
        <h2>YAML 多文件测评对比</h2>
        <p>选择一部小说，上传 1-5 份剧本 YAML，横向比较总分和各项指标。</p>
      </div>
      <button class="icon-button" type="button" title="刷新小说列表" @click="loadNovels">
        <RefreshCw :size="18" />
      </button>
    </header>

    <section class="evaluation-controls">
      <label>
        <span>选择小说</span>
        <select v-model="evaluationSelectedNovelId">
          <option value="">请选择要测评的小说</option>
          <option v-for="novel in novels" :key="novel.id" :value="novel.id">
            {{ novel.title }}
          </option>
        </select>
      </label>

      <div class="upload-box">
        <input
          ref="evaluationFileInput"
          class="file-input"
          type="file"
          accept=".yaml,.yml,application/x-yaml,text/yaml"
          multiple
          @change="selectEvaluationFiles"
        />
        <button class="secondary-button" type="button" :disabled="evaluating" @click="evaluationFileInput?.click()">
          <Upload :size="18" />
          选择 YAML
        </button>
        <button class="secondary-button" type="button" :disabled="evaluating || evaluationFiles.length === 0" @click="clearEvaluationFiles">
          <X :size="18" />
          清空文件
        </button>
        <button class="primary-button" type="button" :disabled="!canEvaluateYaml" @click="evaluateYamlFiles">
          <BarChart3 :size="18" />
          开始测评
        </button>
      </div>
    </section>

    <section class="evaluation-files">
      <div class="section-title">
        <h2><FileText :size="18" /> 待测文件</h2>
        <span>{{ evaluationFiles.length }}/5</span>
      </div>
      <div v-if="evaluationFiles.length === 0" class="outline-empty">选择 1-5 个 YAML 文件开始测评</div>
      <ul v-else class="evaluation-file-list">
        <li v-for="item in evaluationFiles" :key="item.id">
          <div>
            <strong>{{ item.name }}</strong>
            <span>{{ formatFileSize(item.size) }}</span>
          </div>
          <small :class="`eval-status ${item.status}`">{{ evaluationStatusText(item.status) }}</small>
        </li>
      </ul>
    </section>

    <section v-if="evaluationSummary" class="evaluation-summary">
      <div class="summary-card">
        <span>最佳文件</span>
        <strong>{{ evaluationSummary.best.name }}</strong>
        <b :class="scoreClass(scoreOf(evaluationSummary.best.report))">{{ scoreOf(evaluationSummary.best.report) }}</b>
      </div>
      <div class="summary-card">
        <span>最低文件</span>
        <strong>{{ evaluationSummary.lowest.name }}</strong>
        <b :class="scoreClass(scoreOf(evaluationSummary.lowest.report))">{{ scoreOf(evaluationSummary.lowest.report) }}</b>
      </div>
      <div class="summary-card">
        <span>平均分</span>
        <strong>{{ evaluationSummary.average }}</strong>
        <b :class="scoreClass(evaluationSummary.average)">{{ evaluationSummary.passedCount }}/{{ evaluationSummary.totalCount }} 通过</b>
      </div>
    </section>

    <section class="evaluation-results">
      <div class="section-title">
        <h2><Trophy :size="18" /> 测评结果</h2>
        <span>{{ evaluationResults.length }}</span>
      </div>
      <div v-if="evaluationFiles.length === 0" class="outline-empty">暂无测评结果</div>
      <div v-else class="result-grid">
        <article v-for="item in evaluationFiles" :key="item.id" class="result-card" :class="{ failed: item.status === 'failed' }">
          <header>
            <div>
              <strong>{{ item.name }}</strong>
              <span>{{ evaluationStatusText(item.status) }}</span>
            </div>
            <b v-if="item.report" class="score-pill" :class="scoreClass(scoreOf(item.report))">
              {{ scoreOf(item.report) }}
            </b>
          </header>

          <p v-if="item.error" class="result-error">{{ item.error }}</p>
          <template v-else-if="item.report">
            <div class="result-meta">
              <span v-if="overallRankLabel(item)" :class="overallRankLabel(item) === '最高' ? 'best' : 'worst'">
                总分{{ overallRankLabel(item) }}
              </span>
              <span :class="{ pass: isReportPassed(item.report), fail: !isReportPassed(item.report) }">
                {{ isReportPassed(item.report) ? '通过' : '未通过' }}
              </span>
              <span>{{ item.report.issues?.length || 0 }} issues</span>
            </div>
            <ul class="metric-list">
              <li
                v-for="metric in item.report.scorecard?.metrics || []"
                :key="metric.key"
                :class="[{ risky: Number(metric.score || 0) < metricThreshold(metric) }, metricRankClass(metric)]"
              >
                <span>{{ metric.name }}</span>
                <strong>
                  {{ metric.score }}
                  <em v-if="metricRankLabel(metric)">{{ metricRankLabel(metric) }}</em>
                </strong>
                <small>{{ metric.numerator }}/{{ metric.denominator }}</small>
              </li>
            </ul>
          </template>
          <div v-else class="result-empty">{{ item.status === 'running' ? '正在测评...' : '等待开始' }}</div>
        </article>
      </div>
    </section>
  </main>
  </div>
</template>
