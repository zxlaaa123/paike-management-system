<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSchedulePlanById, type SchedulePlan } from '../../api/schedulePlan'
import {
  downloadScheduleReport,
  generateScheduleReport,
  getScheduleReportList,
  type ScheduleReportFormat,
  type ScheduleReportItem,
  type ScheduleReportType,
} from '../../api/v4ScheduleReportApi'
import { strategyText } from '../../utils/status'
import { extractMessage } from '../../utils/errors'

const route = useRoute()
const router = useRouter()

const planId = computed(() => Number(route.params.planId))
const loading = ref(false)
const generating = ref(false)
const downloadingId = ref<number | null>(null)
const plan = ref<SchedulePlan | null>(null)
const reportItems = ref<ScheduleReportItem[]>([])

const form = ref({
  reportType: 'ANALYSIS' as ScheduleReportType,
  format: 'HTML' as ScheduleReportFormat,
  includeCharts: true,
  includeRisks: true,
  includeSuggestions: true,
})

const reportTypeOptions: Array<{ label: string; value: ScheduleReportType }> = [
  { label: '方案质量分析报告', value: 'ANALYSIS' },
  { label: '方案对比报告', value: 'COMPARE' },
  { label: '冲突风险报告', value: 'RISK' },
  { label: '教师课时统计报告', value: 'TEACHER_LOAD' },
  { label: '教室使用率报告', value: 'ROOM_USAGE' },
]

async function fetchData() {
  loading.value = true
  try {
    const [planData, reportList] = await Promise.all([getSchedulePlanById(planId.value), getScheduleReportList(planId.value)])
    plan.value = planData
    reportItems.value = reportList.items ?? []
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '加载报告数据失败'))
  } finally {
    loading.value = false
  }
}

async function handleGenerate() {
  generating.value = true
  try {
    await generateScheduleReport(planId.value, form.value)
    await fetchData()
    ElMessage.success('报告生成成功')
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '报告生成失败'))
  } finally {
    generating.value = false
  }
}

async function handleDownload(item: ScheduleReportItem) {
  downloadingId.value = item.reportId
  try {
    await downloadScheduleReport(item)
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '下载失败'))
  } finally {
    downloadingId.value = null
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="report-page" v-loading="loading">
    <el-page-header content="V4 排课报告导出" @back="router.push(`/v4/schedule-analysis/${planId}`)" />

    <el-card v-if="plan" shadow="never" class="header-card">
      <div class="header-top">
        <div>
          <div class="page-title">{{ plan.name }}</div>
          <div class="page-meta">{{ strategyText(plan.strategyType) }} · 学期 ID {{ plan.semesterId }} · 状态 {{ plan.status }}</div>
        </div>
        <div class="header-actions">
          <el-button @click="router.push(`/v4/schedule-analysis/${plan.id}`)">回到质量分析</el-button>
          <el-button @click="router.push(`/v3/schedule-plans/${plan.id}`)">回到 V3 方案详情</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="card">
      <template #header><div class="card-title">报告生成区</div></template>
      <el-form label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="报告类型">
              <el-select v-model="form.reportType" style="width: 100%">
                <el-option v-for="option in reportTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="导出格式">
              <el-radio-group v-model="form.format">
                <el-radio label="HTML">HTML</el-radio>
                <el-radio label="EXCEL">EXCEL</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-checkbox v-model="form.includeCharts">包含图表说明</el-checkbox>
          </el-col>
          <el-col :span="8">
            <el-checkbox v-model="form.includeRisks">包含风险统计</el-checkbox>
          </el-col>
          <el-col :span="8">
            <el-checkbox v-model="form.includeSuggestions">包含优化建议</el-checkbox>
          </el-col>
        </el-row>
        <div class="form-actions">
          <el-button type="primary" :loading="generating" @click="handleGenerate">生成报告</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="card">
      <template #header><div class="card-title">报告列表区</div></template>
      <el-table :data="reportItems" stripe>
        <el-table-column prop="reportId" label="报告 ID" width="110" />
        <el-table-column prop="reportType" label="报告类型" width="150" />
        <el-table-column prop="format" label="文件格式" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="createdAt" label="生成时间" width="180" />
        <el-table-column prop="downloadUrl" label="下载地址" min-width="280" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" link :loading="downloadingId === row.reportId" @click="handleDownload(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="reportItems.length === 0" description="暂无报告，请先生成" />
    </el-card>
  </div>
</template>

<style scoped>
.report-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-card,
.card {
  border-radius: 18px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
}

.page-meta {
  margin-top: 6px;
  color: #64748b;
}

.header-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}

.form-actions {
  margin-top: 14px;
}

@media (max-width: 768px) {
  :deep(.el-col) {
    max-width: 100%;
    flex: 0 0 100%;
  }
}
</style>
