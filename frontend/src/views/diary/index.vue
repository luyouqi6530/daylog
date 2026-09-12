<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageDiaries, deleteDiary } from '@/api/diary'
import { listTags } from '@/api/tag'
import { getMood } from '@/utils/mood'

const router = useRouter()

const loading = ref(false)
const total = ref(0)
const diaries = ref([])
const tags = ref([])

const query = reactive({
  startDate: null,
  endDate: null,
  mood: null,
  tagId: null,
  keyword: '',
  page: 1,
  size: 10
})

async function loadDiaries() {
  loading.value = true
  try {
    const params = { ...query }
    if (query.startDate) params.startDate = query.startDate
    if (query.endDate) params.endDate = query.endDate
    const res = await pageDiaries(params)
    diaries.value = res.data.records
    total.value = Number(res.data.total)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadDiaries()
}

function handleReset() {
  Object.assign(query, {
    startDate: null,
    endDate: null,
    mood: null,
    tagId: null,
    keyword: '',
    page: 1,
    size: 10
  })
  loadDiaries()
}

function handleView(row) {
  router.push({ path: '/diary/view', query: { date: row.recordDate } })
}

function handleEdit(row) {
  router.push({ path: '/diary/edit', query: { date: row.recordDate } })
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除《${row.title}》吗？删除后不可恢复。`, '删除日记', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteDiary(row.id)
  ElMessage.success('删除成功')
  // 当前页删空后回退一页
  if (diaries.value.length === 1 && query.page > 1) {
    query.page--
  }
  loadDiaries()
}

onMounted(async () => {
  const tagRes = await listTags()
  tags.value = tagRes.data
  loadDiaries()
})
</script>

<template>
  <div>
    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="日期">
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 240px"
            @change="(v) => { query.startDate = v?.[0] || null; query.endDate = v?.[1] || null }"
          />
        </el-form-item>
        <el-form-item label="心情">
          <el-select v-model="query.mood" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="m in 5" :key="m" :value="m" :label="`${getMood(m).emoji} ${getMood(m).label}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="query.tagId" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="tag in tags" :key="tag.id" :value="tag.id" :label="tag.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="标题 / 正文" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <div v-loading="loading" class="diary-list">
      <el-empty v-if="!loading && diaries.length === 0" description="还没有日记，写下第一篇吧" />
      <el-card
        v-for="diary in diaries"
        :key="diary.id"
        shadow="never"
        class="diary-item"
        @click="handleView(diary)"
      >
        <div class="diary-head">
          <div class="diary-title-row">
            <span class="diary-mood">{{ getMood(diary.moodScore).emoji }}</span>
            <span class="diary-title">{{ diary.title }}</span>
          </div>
          <div class="diary-actions" @click.stop>
            <el-button link @click="handleEdit(diary)">编辑</el-button>
            <el-button link class="delete-btn" @click="handleDelete(diary)">删除</el-button>
          </div>
        </div>
        <div class="diary-content">{{ diary.content.replace(/[#*`>\[\]()-]/g, '').slice(0, 120) }}</div>
        <div class="diary-meta">
          <span>{{ diary.recordDate }}</span>
          <template v-if="diary.weather"><span class="meta-sep">·</span><span>{{ diary.weather }}</span></template>
          <span class="meta-sep">·</span><span>{{ diary.wordCount }} 字</span>
          <template v-if="diary.imageCount > 0"><span class="meta-sep">·</span><span>{{ diary.imageCount }} 图</span></template>
          <span
            v-for="tag in diary.tags"
            :key="tag.id"
            class="meta-tag"
          ><span class="tag-dot" :style="{ background: tag.color }" />{{ tag.name }}</span>
        </div>
      </el-card>

      <div v-if="total > query.size" class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          :page-size="query.size"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="loadDiaries"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

.diary-item {
  margin-bottom: 12px;
  cursor: pointer;
}

.diary-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.diary-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.diary-mood {
  font-size: 18px;
}

.diary-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main);
}

.diary-item:hover .diary-title {
  text-decoration: underline;
  text-decoration-color: #d4d4d8;
  text-underline-offset: 4px;
}

.delete-btn {
  color: #a8a8a8;
}

.delete-btn:hover {
  color: #dc2626;
}

.diary-content {
  margin: 10px 0;
  font-size: 13px;
  color: #71717a;
  line-height: 1.8;
}

.diary-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #a8a8a8;
}

.meta-sep {
  color: #d4d4d8;
}

.meta-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: 10px;
  color: #71717a;
}

.tag-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
