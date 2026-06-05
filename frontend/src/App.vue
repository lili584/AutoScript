<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  BookOpen,
  ClipboardPlus,
  FileText,
  FileUp,
  Plus,
  RefreshCw,
  Save,
  Trash2,
  Upload,
} from 'lucide-vue-next'

const novels = ref([])
const selectedNovel = ref(null)
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const createFileInput = ref(null)
const contentFileInput = ref(null)
const pendingFileMode = ref('append')

const form = reactive({
  title: '',
  description: '',
  content: '',
})

const contentDraft = ref('')
const appendDraft = ref('')

const selectedId = computed(() => selectedNovel.value?.id)
const hasNovels = computed(() => novels.value.length > 0)

onMounted(() => {
  loadNovels()
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
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
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
</script>

<template>
  <main class="workspace">
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
          <button class="primary-button compact" type="button" :disabled="saving" @click="saveContent">
            <Save :size="17" />
            保存
          </button>
        </div>
        <textarea
          v-model="contentDraft"
          class="content-editor"
          spellcheck="false"
          placeholder="# 小说标题&#10;&#10;## 第一章&#10;&#10;输入或粘贴小说正文"
        />
      </template>
    </section>
  </main>
</template>
