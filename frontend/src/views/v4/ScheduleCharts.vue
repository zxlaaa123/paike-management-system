<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import EChartPanel from '../../components/v4/EChartPanel.vue'
import { getSchedulePlanById, type SchedulePlan } from '../../api/schedulePlan'
import {
  getScheduleClassDailyLoadChart,
  getScheduleRoomUtilizationChart,
  getScheduleScoreRadarChart,
  getScheduleTeacherHoursChart,
  getScheduleTimeDensityChart,
  type ScheduleClassDailyLoadChart,
  type ScheduleRoomUtilizationChart,
  type ScheduleScoreRadarChart,
  type ScheduleTeacherHoursChart,
  type ScheduleTimeDensityChart,
} from '../../api/v4ScheduleAnalysisApi'
import { strategyText } from '../../utils/status'

const route = useRoute()
const router = useRouter()

const planId = computed(() => Number(route.params.planId))
const loading = ref(false)
const activeTab = ref('teacher')
const plan = ref<SchedulePlan | null>(null)
const teacherChart = ref<ScheduleTeacherHoursChart | null>(null)
const roomChart = ref<ScheduleRoomUtilizationChart | null>(null)
const classChart = ref<ScheduleClassDailyLoadChart | null>(null)
const densityChart = ref<ScheduleTimeDensityChart | null>(null)
const scoreChart = ref<ScheduleScoreRadarChart | null>(null)
const detailVisible = ref(false)
const detailTitle = ref('')
const detailLines = ref<string[]>([])

async function fetchData() {
  loading.value = true
  try {
    const [planData, teacherData, roomData, classData, densityData, scoreData] = await Promise.all([
      getSchedulePlanById(planId.value),
      getScheduleTeacherHoursChart(planId.value),
      getScheduleRoomUtilizationChart(planId.value),
      getScheduleClassDailyLoadChart(planId.value),
      getScheduleTimeDensityChart(planId.value),
      getScheduleScoreRadarChart(planId.value),
    ])
    plan.value = planData
    teacherChart.value = teacherData
    roomChart.value = roomData
    classChart.value = classData
    densityChart.value = densityData
    scoreChart.value = scoreData
  } catch (error: any) {
    ElMessage.error(error?.message || '加载图表数据失败')
  } finally {
    loading.value = false
  }
}

function openDetail(title: string, lines: string[]) {
  detailTitle.value = title
  detailLines.value = lines
  detailVisible.value = true
}

const teacherOption = computed<EChartsOption>(() => {
  const items = teacherChart.value?.items ?? []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 40, bottom: 64 },
    xAxis: {
      type: 'category',
      axisLabel: { interval: 0, rotate: items.length > 6 ? 28 : 0 },
      data: items.map((item) => item.teacherName),
    },
    yAxis: { type: 'value', name: '课时' },
    series: [
      {
        type: 'bar',
        data: items.map((item) => item.totalHours),
        itemStyle: { color: '#b85c38', borderRadius: [8, 8, 0, 0] },
      },
    ],
  }
})

const roomOption = computed<EChartsOption>(() => {
  const items = roomChart.value?.items ?? []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 40, bottom: 74 },
    xAxis: {
      type: 'category',
      axisLabel: { interval: 0, rotate: items.length > 6 ? 28 : 0 },
      data: items.map((item) => item.roomName),
    },
    yAxis: { type: 'value', name: '利用率 %', max: 100 },
    series: [
      {
        type: 'bar',
        data: items.map((item) => Number(item.utilizationRate)),
        itemStyle: { color: '#2f6c8f', borderRadius: [8, 8, 0, 0] },
      },
    ],
  }
})

const classOption = computed<EChartsOption>(() => {
  const items = classChart.value?.items ?? []
  const topClassNames = Array.from(
    new Map(
      [...items]
        .sort((a, b) => b.lessonCount - a.lessonCount)
        .map((item) => [item.classId, item.className]),
    ).values(),
  ).slice(0, 6)
  const weekDays = [1, 2, 3, 4, 5]
  const series = topClassNames.map((className) => ({
    name: className,
    type: 'line' as const,
    smooth: true,
    data: weekDays.map((weekDay) => items.find((item) => item.className === className && item.weekDay === weekDay)?.lessonCount ?? 0),
  }))
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 6 },
    grid: { left: 48, right: 24, top: 56, bottom: 32 },
    xAxis: { type: 'category', data: weekDays.map((day) => `周${day}`) },
    yAxis: { type: 'value', name: '课时' },
    series,
  }
})

const densityOption = computed<EChartsOption>(() => {
  const items = densityChart.value?.items ?? []
  const maxCount = Math.max(...items.map((item) => item.courseCount), 0)
  return {
    tooltip: {
      formatter: (params: any) => `周${params.value[0]} 第${params.value[1]}节：${params.value[2]} 门课`,
    },
    grid: { left: 48, right: 72, top: 24, bottom: 36 },
    xAxis: { type: 'category', data: [1, 2, 3, 4, 5].map((day) => `周${day}`) },
    yAxis: { type: 'category', data: [1, 2, 3, 4, 5, 6, 7, 8].map((period) => `第${period}节`) },
    visualMap: {
      min: 0,
      max: maxCount || 1,
      calculable: true,
      orient: 'vertical',
      right: 8,
      top: 'center',
      inRange: {
        color: ['#f7efe3', '#d7b17a', '#8d4f2b'],
      },
    },
    series: [
      {
        type: 'heatmap',
        data: items.map((item) => [item.weekDay - 1, item.period - 1, item.courseCount]),
        label: { show: true, color: '#1f3045' },
      },
    ],
  }
})

const scoreOption = computed<EChartsOption>(() => {
  const items = scoreChart.value?.items ?? []
  return {
    tooltip: {},
    radar: {
      radius: '62%',
      indicator: items.map((item) => ({ name: item.name, max: 100 })),
      splitArea: {
        areaStyle: {
          color: ['rgba(247, 239, 227, 0.2)', 'rgba(215, 177, 122, 0.18)'],
        },
      },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: items.map((item) => Number(item.value)),
            name: '方案评分维度',
            areaStyle: { color: 'rgba(184, 92, 56, 0.2)' },
            lineStyle: { color: '#b85c38' },
            itemStyle: { color: '#b85c38' },
          },
        ],
      },
    ],
  }
})

function handleTeacherClick(params: any) {
  const item = teacherChart.value?.items?.[params.dataIndex]
  if (!item) return
  openDetail(item.teacherName, [`总课时：${item.totalHours} 节`, `涉及课程数：${item.courseCount}`])
}

function handleRoomClick(params: any) {
  const item = roomChart.value?.items?.[params.dataIndex]
  if (!item) return
  openDetail(item.roomName, [
    `教室类型：${item.roomType}`,
    `容量：${item.capacity ?? '—'}`,
    `已使用节次：${item.usedPeriods}`,
    `总可用节次：${item.totalPeriods}`,
    `利用率：${item.utilizationRate}%`,
  ])
}

function handleDensityClick(params: any) {
  const value = params.value as [number, number, number]
  openDetail(`周${value[0] + 1} 第${value[1] + 1}节`, [`课程密度：${value[2]} 门课`])
}

function handleRadarClick(params: any) {
  const item = scoreChart.value?.items?.[params.dataIndex ?? 0]
  if (!item) return
  openDetail(item.name, [`得分：${item.value}`, `说明：${item.description}`])
}

onMounted(fetchData)
</script>

<template>
  <div class="charts-page" v-loading="loading">
    <el-page-header content="V4 可视化分析" @back="router.push(`/v4/schedule-analysis/${planId}`)" />

    <el-card v-if="plan" shadow="never" class="hero-card">
      <div class="hero-row">
        <div>
          <div class="eyebrow">V4 阶段 4</div>
          <h1>{{ plan.name }}</h1>
          <p>{{ strategyText(plan.strategyType) }} · 学期 ID {{ plan.semesterId }} · 当前状态 {{ plan.status }}</p>
        </div>
        <div class="hero-actions">
          <el-button type="primary" plain :loading="loading" @click="fetchData">刷新图表</el-button>
          <el-button @click="router.push(`/v4/schedule-analysis/${planId}`)">回到质量分析</el-button>
        </div>
      </div>
      <div class="safe-note">图表数据全部来自后端只读统计接口，不会修改方案明细、正式课表或评分配置。</div>
    </el-card>

    <el-card shadow="never" class="tabs-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="教师课时" name="teacher">
          <EChartPanel
            :option="teacherOption"
            :loading="loading"
            :empty="(teacherChart?.items?.length ?? 0) === 0"
            @chart-click="handleTeacherClick"
          />
        </el-tab-pane>
        <el-tab-pane label="教室利用" name="room">
          <EChartPanel
            :option="roomOption"
            :loading="loading"
            :empty="(roomChart?.items?.length ?? 0) === 0"
            @chart-click="handleRoomClick"
          />
        </el-tab-pane>
        <el-tab-pane label="班级负载" name="class">
          <EChartPanel
            :option="classOption"
            :loading="loading"
            :empty="(classChart?.items?.length ?? 0) === 0"
          />
        </el-tab-pane>
        <el-tab-pane label="时间密度" name="density">
          <EChartPanel
            :option="densityOption"
            :loading="loading"
            :empty="(densityChart?.items?.length ?? 0) === 0"
            @chart-click="handleDensityClick"
          />
        </el-tab-pane>
        <el-tab-pane label="评分雷达" name="score">
          <EChartPanel
            :option="scoreOption"
            :loading="loading"
            :empty="(scoreChart?.items?.length ?? 0) === 0"
            @chart-click="handleRadarClick"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="420px">
      <ul class="detail-list">
        <li v-for="line in detailLines" :key="line">{{ line }}</li>
      </ul>
    </el-drawer>
  </div>
</template>

<style scoped>
.charts-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-card,
.tabs-card {
  border-radius: 18px;
}

.hero-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.hero-row h1 {
  margin: 6px 0 10px;
  color: #223047;
  font-size: 28px;
}

.hero-row p {
  margin: 0;
  color: #667085;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.eyebrow {
  color: #b85c38;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.safe-note {
  margin-top: 14px;
  color: #69717d;
  font-size: 13px;
}

.detail-list {
  margin: 0;
  padding-left: 18px;
  color: #475467;
  line-height: 1.9;
}

@media (max-width: 768px) {
  .hero-row {
    flex-direction: column;
  }
}
</style>
