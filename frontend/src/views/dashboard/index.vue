<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getOverview } from '@/api/stats'
import { getDiaryByDate } from '@/api/diary'
import { getMood, todayStr } from '@/utils/mood'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const overview = ref(null)
const todayDiary = ref(null)

const statCards = [
  { key: 'totalDiaries', label: '累计日记', unit: '篇' },
  { key: 'totalWords', label: '累计字数', unit: '字' },
  { key: 'currentStreak', label: '连续记录', unit: '天' },
  { key: 'longestStreak', label: '最长连续', unit: '天' }
]

onMounted(async () => {
  try {
    const [overviewRes, diaryRes] = await Promise.all([
      getOverview(),
      getDiaryByDate(todayStr())
    ])
    overview.value = overviewRes.data
    todayDiary.value = diaryRes.data
  } finally {
    loading.value = false
  }
})

function goWrite() {
  router.push({ path: '/diary/edit', query: { date: todayStr() } })
}

function goView() {
  router.push({ path: '/diary/view', query: { date: todayStr() } })
}
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <!-- 顶部欢迎区 -->
    <el-card shadow="never" class="welcome-card">
      <div class="welcome">
        <div>
          <div class="welcome-hi">{{ userStore.userInfo?.nickname || '朋友' }}，{{ todayDiary ? '今天的日记已经写完啦' : '今天还没写日记哦' }}</div>
          <div class="welcome-sub">
            <template v-if="todayDiary">
              今日心情：{{ getMood(todayDiary.moodScore).emoji }} {{ getMood(todayDiary.moodScore).label }}
            </template>
            <template v-else>用一分钟，记录今天的心情</template>
          </div>
        </div>
        <el-button type="primary" size="large" @click="todayDiary ? goView() : goWrite()">
          {{ todayDiary ? '查看今天的日记' : '写今天的日记' }}
        </el-button>
      </div>
    </el-card>

    <!-- 数据卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in statCards" :key="card.key" :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">
            {{ overview ? overview[card.key] : '-' }}
            <span class="stat-unit">{{ card.unit }}</span>
          </div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>本月记录</template>
          <div class="month-count">{{ overview ? overview.monthCount : '-' }} 篇</div>
          <div class="stat-label">本月已写的日记数量</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>快捷入口</template>
          <div class="quick-links">
            <el-button @click="router.push('/diary')">我的日记</el-button>
            <el-button @click="router.push('/calendar')">日历视图</el-button>
            <el-button @click="router.push('/stats')">统计分析</el-button>
            <el-button @click="router.push('/report')">AI 周报</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.welcome-card {
  margin-bottom: 16px;
}

.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.welcome-hi {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 8px;
}

.welcome-sub {
  font-size: 14px;
  color: var(--text-sub);
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-main);
  letter-spacing: 0.5px;
}

.stat-unit {
  font-size: 13px;
  font-weight: 400;
  color: var(--text-sub);
  margin-left: 4px;
}

.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-sub);
}

.month-count {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-main);
}

.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
