<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { BarChart, HeatmapChart, LineChart, RadarChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  RadarComponent,
  TitleComponent,
  TooltipComponent,
  VisualMapComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { use, init, type ECElementEvent, type ECharts, type EChartsCoreOption } from 'echarts/core'

use([
  BarChart,
  HeatmapChart,
  LineChart,
  RadarChart,
  GridComponent,
  LegendComponent,
  RadarComponent,
  TitleComponent,
  TooltipComponent,
  VisualMapComponent,
  CanvasRenderer,
])

const props = withDefaults(defineProps<{
  option: EChartsCoreOption
  loading?: boolean
  empty?: boolean
  height?: string
}>(), {
  loading: false,
  empty: false,
  height: '360px',
})

const emit = defineEmits<{
  chartClick: [params: ECElementEvent]
}>()

const elRef = ref<HTMLDivElement | null>(null)
let chart: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

function renderChart() {
  if (!elRef.value || props.empty) {
    return
  }
  if (!chart) {
    chart = init(elRef.value)
    chart.on('click', (params: ECElementEvent) => emit('chartClick', params))
  }
  chart.setOption(props.option, true)
  if (props.loading) {
    chart.showLoading('default', { text: '图表加载中' })
  } else {
    chart.hideLoading()
  }
  chart.resize()
}

function disposeChart() {
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
}

onMounted(async () => {
  await nextTick()
  if (elRef.value) {
    resizeObserver = new ResizeObserver(() => {
      chart?.resize()
    })
    resizeObserver.observe(elRef.value)
  }
  renderChart()
})

watch(
  () => [props.option, props.loading, props.empty],
  async () => {
    await nextTick()
    if (props.empty) {
      chart?.clear()
      chart?.hideLoading()
      return
    }
    renderChart()
  },
  { deep: true },
)

onBeforeUnmount(() => {
  disposeChart()
})
</script>

<template>
  <div class="chart-shell">
    <el-empty v-if="empty" description="暂无图表数据，请确认该方案已有排课明细。" />
    <div v-else ref="elRef" class="chart-canvas" :style="{ height }" />
  </div>
</template>

<style scoped>
.chart-shell {
  width: 100%;
}

.chart-canvas {
  width: 100%;
  min-height: 320px;
}
</style>
