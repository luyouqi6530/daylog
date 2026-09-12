<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { getDiaryByDate, deleteDiary } from '@/api/diary'
import { getMood } from '@/utils/mood'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const diary = ref(null)
const date = route.query.date || ''
const previewId = 'diary-preview'

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

function weekdayLabel(dateStr) {
  if (!dateStr) return ''
  const d = new Date(`${dateStr}T00:00:00`)
  return `星期${WEEKDAYS[d.getDay()]}`
}

async function loadDiary() {
  loading.value = true
  try {
    const res = await getDiaryByDate(date)
    diary.value = res.data
  } finally {
    loading.value = false
  }
}

function goBack() {
  // 有浏览历史就回退，否则去列表
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/diary')
  }
}

function goEdit() {
  router.push({ path: '/diary/edit', query: { date } })
}

function goWrite() {
  router.push({ path: '/diary/edit', query: { date } })
}

async function handleDelete() {
  await ElMessageBox.confirm(`确定删除《${diary.value.title}》吗？删除后不可恢复。`, '删除日记', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteDiary(diary.value.id)
  ElMessage.success('删除成功')
  router.push('/diary')
}

onMounted(loadDiary)
</script>

<template>
  <div v-loading="loading" class="view-page">
    <!-- 返回 -->
    <button class="back-btn" @click="goBack">← 返回</button>

    <!-- 无日记 -->
    <div v-if="!loading && !diary" class="empty-wrap">
      <div class="empty-date">{{ date }}</div>
      <div class="empty-text">这一天还没有日记</div>
      <el-button type="primary" plain @click="goWrite">补写这一天</el-button>
    </div>

    <!-- 正文 -->
    <article v-else-if="diary" class="article">
      <header class="article-header">
        <div class="article-date">{{ date }} {{ weekdayLabel(date) }}</div>
        <h1 class="article-title">{{ diary.title }}</h1>
        <div class="article-meta">
          <span class="meta-mood">
            {{ getMood(diary.moodScore).emoji }} {{ getMood(diary.moodScore).label }}
          </span>
          <span v-if="diary.weather" class="meta-item">{{ diary.weather }}</span>
          <span class="meta-item">{{ diary.wordCount }} 字</span>
        </div>
        <div v-if="diary.tags?.length" class="article-tags">
          <span
            v-for="tag in diary.tags"
            :key="tag.id"
            class="tag-dot"
            :style="{ background: tag.color }"
          >{{ tag.name }}</span>
        </div>
      </header>

      <el-divider class="article-divider" />

      <MdPreview :id="previewId" :model-value="diary.content" class="article-content" />

      <div v-if="diary.images?.length" class="article-images">
        <el-image
          v-for="img in diary.images"
          :key="img.id"
          :src="img.url"
          :preview-src-list="diary.images.map((i) => i.url)"
          :initial-index="diary.images.indexOf(img)"
          fit="cover"
          class="image-item"
        />
      </div>

      <footer class="article-footer">
        <el-button type="primary" plain @click="goEdit">编辑</el-button>
        <el-button text class="delete-btn" @click="handleDelete">删除</el-button>
      </footer>
    </article>
  </div>
</template>

<style scoped>
.view-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 8px 0 48px;
}

.back-btn {
  border: none;
  background: none;
  font-size: 13px;
  color: #a8a8a8;
  cursor: pointer;
  padding: 4px 0;
  margin-bottom: 8px;
  transition: color 0.2s;
}

.back-btn:hover {
  color: #1f1f1f;
}

/* 空状态 */
.empty-wrap {
  text-align: center;
  padding: 96px 0;
}

.empty-date {
  font-size: 14px;
  color: #a8a8a8;
  letter-spacing: 0.5px;
}

.empty-text {
  font-size: 18px;
  color: #52525b;
  margin: 12px 0 28px;
}

/* 文章 */
.article-date {
  font-size: 13px;
  color: #a8a8a8;
  letter-spacing: 1px;
}

.article-title {
  font-size: 28px;
  font-weight: 700;
  color: #18181b;
  line-height: 1.4;
  margin: 12px 0 16px;
}

.article-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: #71717a;
}

.meta-item::before {
  content: '·';
  margin-right: 16px;
  color: #d4d4d8;
}

.article-tags {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.tag-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #52525b;
  background: #f4f4f5;
  padding: 3px 10px;
  border-radius: 999px;
}

.tag-dot::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: inherit;
}

.article-divider {
  margin: 28px 0 8px;
  border-color: #ebebeb;
}

.article-content {
  --md-color: #27272a;
}

/* 图片 */
.article-images {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 28px;
}

.image-item {
  width: 100%;
  height: 160px;
  border-radius: 8px;
  border: 1px solid #ebebeb;
}

/* 底部操作 */
.article-footer {
  margin-top: 40px;
  padding-top: 20px;
  border-top: 1px solid #ebebeb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.delete-btn {
  color: #a8a8a8;
}

.delete-btn:hover {
  color: #dc2626;
}
</style>
