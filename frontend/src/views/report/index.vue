<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { pageReports, getReport, generateReport } from '@/api/report'

const loading = ref(false)
const generating = ref(false)
const total = ref(0)
const page = ref(1)
const reports = ref([])

// 详情抽屉
const detailVisible = ref(false)
const currentReport = ref(null)

function lastMonday() {
  const d = new Date()
  const day = d.getDay() === 0 ? 7 : d.getDay()
  d.setDate(d.getDate() - day + 1 - 7)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${dd}`
}

async function loadReports() {
  loading.value = true
  try {
    const res = await pageReports({ page: page.value, size: 10 })
    reports.value = res.data.records
    total.value = Number(res.data.total)
  } finally {
    loading.value = false
  }
}

async function handleGenerate() {
  generating.value = true
  try {
    await generateReport(lastMonday())
    ElMessage.success('上周周报已生成')
    page.value = 1
    loadReports()
  } finally {
    generating.value = false
  }
}

async function openDetail(row) {
  const res = await getReport(row.id)
  currentReport.value = res.data
  detailVisible.value = true
}

onMounted(loadReports)
</script>

<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>AI 周报</span>
          <el-button type="primary" :loading="generating" @click="handleGenerate">生成上周周报</el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="每周一 08:00 系统自动为上周有日记的用户生成周报；也可以在这里手动生成/重新生成（覆盖旧版）。"
        style="margin-bottom: 16px"
      />

      <el-table v-loading="loading" :data="reports" stripe>
        <el-table-column label="周期" width="220">
          <template #default="{ row }">{{ row.weekStart }} ~ {{ row.weekEnd }}</template>
        </el-table-column>
        <el-table-column prop="diaryCount" label="日记篇数" width="100" align="center" />
        <el-table-column prop="moodAvg" label="平均心情" width="100" align="center">
          <template #default="{ row }">{{ row.moodAvg }} 分</template>
        </el-table-column>
        <el-table-column prop="model" label="生成模型" width="140" />
        <el-table-column label="内容预览">
          <template #default="{ row }">{{ row.summary.replace(/[#*\n]/g, ' ').slice(0, 60) }}……</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > 10" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          :total="total"
          :page-size="10"
          layout="prev, pager, next, total"
          @current-change="loadReports"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" size="45%" :title="`${currentReport?.weekStart} ~ ${currentReport?.weekEnd}`">
      <div v-if="currentReport" class="report-detail">
        <div class="report-meta">
          <el-tag>{{ currentReport.diaryCount }} 篇日记</el-tag>
          <el-tag type="success">平均心情 {{ currentReport.moodAvg }} 分</el-tag>
          <el-tag type="info">{{ currentReport.model }}</el-tag>
        </div>
        <MdPreview :model-value="currentReport.summary" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.report-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
</style>
