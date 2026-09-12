<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getCalendar } from '@/api/diary'
import { getMood } from '@/utils/mood'

const router = useRouter()
const calendarRef = ref()
const date = ref(new Date())
// '2026-09-07' -> moodScore
const moodMap = ref({})

async function loadCalendar() {
  const year = date.value.getFullYear()
  const month = date.value.getMonth() + 1
  const res = await getCalendar(year, month)
  moodMap.value = Object.fromEntries(res.data.map((d) => [d.recordDate, d.moodScore]))
}

function handlePickDay(day) {
  const key = formatDate(day)
  if (moodMap.value[key]) {
    router.push({ path: '/diary/view', query: { date: key } })
  } else {
    router.push({ path: '/diary/edit', query: { date: key } })
  }
}

function formatDate(d) {
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

watch(date, () => loadCalendar())
onMounted(loadCalendar)
</script>

<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>日历视图</span>
          <div class="legend">
            <span v-for="mood in 5" :key="mood" class="legend-item">
              {{ getMood(mood).emoji }} {{ getMood(mood).label }}
            </span>
          </div>
        </div>
      </template>

      <el-calendar ref="calendarRef" v-model="date">
        <template #date-cell="{ data }">
          <div class="calendar-cell" :class="{ recorded: moodMap[data.day] }" @click="handlePickDay(new Date(data.day))">
            <span class="cell-day">{{ Number(data.day.split('-')[2]) }}</span>
            <span v-if="moodMap[data.day]" class="cell-mood">{{ getMood(moodMap[data.day]).emoji }}</span>
            <span v-else class="cell-add">+</span>
          </div>
        </template>
      </el-calendar>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.legend {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.legend-item {
  font-weight: 400;
}

.calendar-cell {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.calendar-cell.recorded {
  cursor: pointer;
}

.cell-day {
  font-size: 13px;
}

.calendar-cell.recorded .cell-day {
  color: #1f1f1f;
  font-weight: 600;
}

.cell-mood {
  font-size: 18px;
}

.cell-add {
  font-size: 14px;
  color: #d4d4d8;
  opacity: 0;
  transition: opacity 0.2s;
}

.calendar-cell:hover .cell-add {
  opacity: 1;
}
</style>
