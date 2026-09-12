<script setup>
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { getDiaryByDate, createDiary, updateDiary } from '@/api/diary'
import { listTags } from '@/api/tag'
import { MOOD_LIST, getMood, todayStr } from '@/utils/mood'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const tags = ref([])
const editorId = 'diary-editor'
const hasDraft = ref(false)

const DRAFT_KEY = 'daylog-draft'

// 表单（编辑已有日记时回填）
const form = reactive({
  id: null,
  title: '',
  content: '',
  moodScore: 4,
  weather: '',
  recordDate: route.query.date || todayStr(),
  tagIds: [],
  images: []
})

function getDraftKey() {
  return `${DRAFT_KEY}:${form.recordDate}`
}

function saveDraft() {
  if (form.id) return
  const draft = {
    title: form.title,
    content: form.content,
    moodScore: form.moodScore,
    weather: form.weather,
    recordDate: form.recordDate,
    tagIds: form.tagIds,
    savedAt: Date.now()
  }
  localStorage.setItem(getDraftKey(), JSON.stringify(draft))
  hasDraft.value = true
}

function loadDraft() {
  const raw = localStorage.getItem(getDraftKey())
  if (!raw) return false
  try {
    const draft = JSON.parse(raw)
    if (draft.title || draft.content) {
      form.title = draft.title || ''
      form.content = draft.content || ''
      form.moodScore = draft.moodScore || 4
      form.weather = draft.weather || ''
      form.tagIds = draft.tagIds || []
      hasDraft.value = true
      return true
    }
  } catch (e) {
    // ignore
  }
  return false
}

function clearDraft() {
  localStorage.removeItem(getDraftKey())
  hasDraft.value = false
}

let draftTimer = null
function startAutoSave() {
  draftTimer = setInterval(() => {
    if (!form.id && (form.title || form.content)) {
      saveDraft()
    }
  }, 5000)
}

async function loadDiary() {
  loading.value = true
  try {
    const res = await getDiaryByDate(form.recordDate)
    if (res.data) {
      form.id = res.data.id
      form.title = res.data.title
      form.content = res.data.content
      form.moodScore = res.data.moodScore
      form.weather = res.data.weather || ''
      form.tagIds = res.data.tags.map((t) => t.id)
      form.images = res.data.images || []
    } else {
      const restored = loadDraft()
      if (restored) {
        ElMessage.info('已恢复上次编辑的草稿')
      }
    }
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('正文不能为空')
    return
  }

  const data = {
    title: form.title,
    content: form.content,
    moodScore: form.moodScore,
    weather: form.weather || null,
    recordDate: form.recordDate,
    tagIds: form.tagIds,
    attachmentIds: form.images.map((img) => img.id)
  }

  saving.value = true
  try {
    if (form.id) {
      await updateDiary(form.id, data)
      ElMessage.success('日记已更新')
    } else {
      await createDiary(data)
      ElMessage.success('日记已保存')
      clearDraft()
    }
    router.push('/diary')
  } finally {
    saving.value = false
  }
}

async function handleUploadImage(files, callback) {
  const results = await Promise.all(
    files.map(async (file) => {
      const formData = new FormData()
      formData.append('file', file)
      const res = await request.post('/files/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      form.images.push(res.data)
      return { url: res.data.url, alt: res.data.originalName, title: res.data.originalName }
    })
  )
  callback(results)
}

onMounted(async () => {
  const tagRes = await listTags()
  tags.value = tagRes.data
  await loadDiary()
  startAutoSave()
})

onUnmounted(() => {
  if (draftTimer) {
    clearInterval(draftTimer)
  }
})
</script>

<template>
  <div v-loading="loading" class="edit-page">
    <el-card shadow="never">
      <!-- 顶部操作栏 -->
      <div class="toolbar">
        <el-button @click="router.back()">← 返回</el-button>
        <div class="toolbar-title">
          {{ form.id ? '编辑日记' : '写日记' }}
          <span v-if="hasDraft && !form.id" class="draft-badge">草稿已自动保存</span>
        </div>
        <el-button type="primary" :loading="saving" @click="handleSave">保 存</el-button>
      </div>

      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="记录日期">
              <el-date-picker v-model="form.recordDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="天气（可选）">
              <el-select v-model="form.weather" placeholder="选择天气" clearable style="width: 100%">
                <el-option label="☀️ 晴" value="晴" />
                <el-option label="⛅ 多云" value="多云" />
                <el-option label="☁️ 阴" value="阴" />
                <el-option label="🌧️ 雨" value="雨" />
                <el-option label="⛈️ 雷阵雨" value="雷阵雨" />
                <el-option label="❄️ 雪" value="雪" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="标签（可选）">
              <el-select v-model="form.tagIds" multiple placeholder="选择标签" style="width: 100%">
                <el-option v-for="tag in tags" :key="tag.id" :value="tag.id" :label="tag.name" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="今天的一句话标题" />
        </el-form-item>

        <el-form-item label="今天的心情">
          <div class="mood-picker">
            <div
              v-for="mood in MOOD_LIST"
              :key="mood.score"
              class="mood-item"
              :class="{ active: form.moodScore === mood.score }"
              :style="form.moodScore === mood.score ? { borderColor: mood.color } : {}"
              @click="form.moodScore = mood.score"
            >
              <span class="mood-emoji">{{ mood.emoji }}</span>
              <span class="mood-label">{{ mood.label }}</span>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="正文（支持 Markdown 与图片，图片会自动上传）">
          <MdEditor
            :id="editorId"
            v-model="form.content"
            style="height: 480px"
            :toolbars="['bold', 'underline', 'italic', 'title', 'strikeThrough', 'sub', 'sup', 'quote', 'unorderedList', 'orderedList', 'task', 'codeRow', 'code', 'link', 'image', 'table', 'revoke', 'next', 'preview', 'catalog']"
            @on-upload-img="handleUploadImage"
          />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar-title {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 10px;
}

.draft-badge {
  font-size: 11px;
  font-weight: 400;
  color: #67C23A;
  background: #f0f9eb;
  padding: 2px 8px;
  border-radius: 10px;
}

.mood-picker {
  display: flex;
  gap: 12px;
}

.mood-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 76px;
  padding: 10px 0;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.mood-item:hover {
  border-color: #c0c4cc;
}

.mood-item.active {
  background: #f5f7fa;
}

.mood-emoji {
  font-size: 26px;
}

.mood-label {
  font-size: 12px;
  color: #606266;
}
</style>
