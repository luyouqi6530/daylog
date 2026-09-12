<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
// ECharts 按需注册：只打包用到的图表 / 组件 / 渲染器，代替全量 import *
import * as echarts from 'echarts/core'
import { LineChart, PieChart, HeatmapChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  VisualMapComponent,
  CalendarComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  PieChart,
  HeatmapChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  VisualMapComponent,
  CalendarComponent,
  CanvasRenderer
])

import { getOverview, getMoodTrend, getMoodDistribution, getTagCloud, getHeatmap } from '@/api/stats'
import { MOOD_LIST } from '@/utils/mood'

const overview = ref(null)
const trendChart = ref(null)
const distributionChart = ref(null)
const heatmapChart = ref(null)
const tagCloudData = ref([])

let trendChartInstance = null
let distributionChartInstance = null
let heatmapChartInstance = null

function handleResize() {
  trendChartInstance?.resize()
  distributionChartInstance?.resize()
  heatmapChartInstance?.resize()
}

async function initTrendChart() {
  const res = await getMoodTrend({})
  trendChartInstance = echarts.init(trendChart.value)
  trendChartInstance.setOption({
    title: { text: '近 30 天心情曲线', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    xAxis: {
      type: 'category',
      data: res.data.map((d) => d.date),
      axisLabel: { rotate: 40, fontSize: 10 }
    },
    yAxis: { type: 'value', min: 0, max: 5, interval: 1 },
    series: [
      {
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: res.data.map((d) => d.avgMood),
        lineStyle: { color: '#409EFF' },
        itemStyle: { color: '#409EFF' },
        areaStyle: { color: 'rgba(64,158,255,0.12)' }
      }
    ]
  })
}

async function initDistributionChart() {
  const res = await getMoodDistribution({})
  distributionChartInstance = echarts.init(distributionChart.value)
  const countMap = Object.fromEntries(res.data.map((d) => [d.moodScore, d.count]))
  distributionChartInstance.setOption({
    title: { text: '近 90 天心情分布', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['40%', '65%'],
        label: { formatter: '{b}: {c} 天' },
        data: MOOD_LIST.map((m) => ({
          name: `${m.emoji} ${m.label}`,
          value: countMap[m.score] || 0,
          itemStyle: { color: m.color }
        }))
      }
    ]
  })
}

async function loadTagCloud() {
  const res = await getTagCloud(20)
  const tags = res.data.filter((t) => t.count > 0)
  const max = Math.max(...tags.map((t) => t.count), 1)
  tagCloudData.value = tags.map((t) => ({
    ...t,
    fontSize: 12 + (t.count / max) * 20
  }))
}

async function initHeatmap() {
  const year = new Date().getFullYear()
  const res = await getHeatmap(year)
  heatmapChartInstance = echarts.init(heatmapChart.value)
  const moodColor = { 1: '#F56C6C', 2: '#E6A23C', 3: '#909399', 4: '#67C23A', 5: '#409EFF' }
  heatmapChartInstance.setOption({
    title: { text: `${year} 年记录热力图`, left: 'center', textStyle: { fontSize: 14 } },
    tooltip: {
      formatter: (p) => `${p.value[0]}<br/>心情 ${p.value[1]} 分`
    },
    visualMap: {
      min: 1,
      max: 5,
      show: false
    },
    calendar: {
      range: String(year),
      cellSize: ['auto', 16],
      left: 40,
      right: 20,
      itemStyle: { borderWidth: 2, borderColor: '#fff' },
      yearLabel: { show: false },
      monthLabel: { fontSize: 10 },
      dayLabel: { fontSize: 9 },
      splitLine: { show: false }
    },
    series: [
      {
        type: 'heatmap',
        coordinateSystem: 'calendar',
        data: res.data.map((d) => [d.date, d.moodScore]),
        itemStyle: {
          color: (p) => moodColor[p.value[1]] || '#909399'
        }
      }
    ]
  })
}

onMounted(async () => {
  const res = await getOverview()
  overview.value = res.data
  initTrendChart()
  initDistributionChart()
  loadTagCloud()
  initHeatmap()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChartInstance?.dispose()
  distributionChartInstance?.dispose()
  heatmapChartInstance?.dispose()
})
</script>

<template>
  <div>
    <!-- 总览数字 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="5">
        <el-card shadow="never" class="mini-card"><div class="mini-value">{{ overview?.totalDiaries ?? '-' }}</div><div class="mini-label">累计日记</div></el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="never" class="mini-card"><div class="mini-value">{{ overview?.totalWords ?? '-' }}</div><div class="mini-label">累计字数</div></el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="never" class="mini-card"><div class="mini-value">{{ overview?.currentStreak ?? '-' }}</div><div class="mini-label">连续记录(天)</div></el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="never" class="mini-card"><div class="mini-value">{{ overview?.longestStreak ?? '-' }}</div><div class="mini-label">最长连续(天)</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="mini-card"><div class="mini-value">{{ overview?.monthCount ?? '-' }}</div><div class="mini-label">本月篇数</div></el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never"><div ref="trendChart" style="height: 320px" /></el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never"><div ref="distributionChart" style="height: 320px" /></el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>标签云（字号越大用得越多）</template>
      <div class="tag-cloud">
        <span
          v-for="tag in tagCloudData"
          :key="tag.name"
          class="tag-item"
          :style="{ fontSize: tag.fontSize + 'px', color: tag.color }"
          :title="`${tag.count} 篇`"
        >{{ tag.name }}</span>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <div ref="heatmapChart" style="height: 200px" />
    </el-card>
  </div>
</template>

<style scoped>
.mini-card {
  text-align: center;
}

.mini-value {
  font-size: 26px;
  font-weight: 700;
  color: #409eff;
}

.mini-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.tag-cloud {
  min-height: 120px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 14px;
  padding: 8px 0;
}

.tag-cloud .tag-item {
  cursor: default;
  font-weight: 600;
  transition: opacity 0.2s;
}

.tag-cloud .tag-item:hover {
  opacity: 0.7;
}
</style>
