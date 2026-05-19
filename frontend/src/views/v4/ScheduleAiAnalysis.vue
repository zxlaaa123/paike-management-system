<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSchedulePlanById, type SchedulePlan } from '../../api/schedulePlan'
import {
  generateScheduleAiAnalysis,
  type ScheduleAiAnalysisResult,
  type ScheduleAiAnalysisType,
} from '../../api/v4ScheduleAiApi'
import { strategyText } from '../../utils/status'

const route = useRoute()
const router = useRouter()

const planId = computed(() => Number(route.params.planId))
const loading = ref(false)
const generating = ref(false)
const plan = ref<SchedulePlan | null>(null)
const result = ref<ScheduleAiAnalysisResult | null>(null)

const form = ref({
  analysisType: 'SUMMARY' as ScheduleAiAnalysisType,
  includeRisks: true,
  includeSuggestions: true,
})

const analysisTypeOptions: Array<{ label: string; value: ScheduleAiAnalysisType }> = [
  { label: '总体分析', value: 'SUMMARY' },
  { label: '风险分析', value: 'RISK' },
  { label: '优化建议', value: 'OPTIMIZATION' },
  { label: '答辩说明', value: 'DEFENSE' },
  { label: '报告摘要', value: 'REPORT_SUMMARY' },
]

const resultTypeLabel = computed(() => {
  const type = result.value?.analysisType
  if (!type) {
    return ''
  }
  return analysisTypeOptions.find((item) => item.value === type)?.label || type
})

async function fetchPlan() {
  loading.value = true
  try {
    plan.value = await getSchedulePlanById(planId.value)
  } catch (error: any) {
    ElMessage.error(error?.message || '加载方案信息失败')
  } finally {
    loading.value = false
  }
}

async function handleGenerate() {
  generating.value = true
  try {
    result.value = await generateScheduleAiAnalysis(planId.value, form.value)
    ElMessage.success('AI 分析已生成')
  } catch (error: any) {
    ElMessage.error(error?.message || 'AI 分析生成失败')
  } finally {
    generating.value = false
  }
}

function buildCopyText() {
  if (!result.value) {
    return ''
  }
  const lines = [
    `分析类型：${analysisTypeOptions.find((item) => item.value === result.value?.analysisType)?.label || result.value.analysisType}`,
    `分析内容：${result.value.analysisText}`,
  ]
  if (result.value.suggestions.length > 0) {
    lines.push('建议列表：')
    result.value.suggestions.forEach((item, index) => lines.push(`${index + 1}. ${item}`))
  }
  return lines.join('\n')
}

async function handleCopy() {
  const text = buildCopyText()
  if (!text) {
    ElMessage.warning('暂无可复制内容')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制分析结果')
  } catch (_error) {
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('已复制分析结果')
  }
}

onMounted(fetchPlan)
</script>

<template>
  <div class="ai-page" v-loading="loading">
    <el-page-header content="V4 AI 分析建议" @back="router.push(`/v4/schedule-analysis/${planId}`)" />

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
      <template #header><div class="card-title">AI 分析配置</div></template>
      <el-form label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分析类型">
              <el-select v-model="form.analysisType" style="width: 100%">
                <el-option v-for="option in analysisTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="输出控制">
              <el-space>
                <el-checkbox v-model="form.includeRisks">包含风险</el-checkbox>
                <el-checkbox v-model="form.includeSuggestions">包含建议</el-checkbox>
              </el-space>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="form-actions">
          <el-button type="primary" :loading="generating" @click="handleGenerate">生成 AI 分析</el-button>
        </div>
      </el-form>
      <div class="safe-note">AI 建议仅供参考，不会自动修改课表。</div>
    </el-card>

    <el-card shadow="never" class="card">
      <template #header>
        <div class="result-header">
          <div class="card-title">分析结果区</div>
          <el-button type="primary" plain @click="handleCopy">复制</el-button>
        </div>
      </template>

      <el-empty v-if="!result" description="请先生成 AI 分析结果" />
      <template v-else>
        <el-alert :title="resultTypeLabel" type="info" :closable="false" show-icon />
        <el-card class="analysis-panel" shadow="never">
          <p>{{ result.analysisText }}</p>
        </el-card>

        <el-card shadow="never" class="suggestion-panel">
          <template #header><div class="card-title">建议列表区</div></template>
          <el-empty v-if="result.suggestions.length === 0" description="当前未返回建议" />
          <ol v-else class="suggestion-list">
            <li v-for="item in result.suggestions" :key="item">{{ item }}</li>
          </ol>
        </el-card>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-card,
.card,
.analysis-panel,
.suggestion-panel {
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

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-actions {
  margin-top: 14px;
}

.safe-note {
  margin-top: 10px;
  color: #64748b;
  font-size: 13px;
}

.analysis-panel {
  margin-top: 14px;
}

.analysis-panel p {
  margin: 0;
  line-height: 1.8;
  color: #334155;
}

.suggestion-panel {
  margin-top: 14px;
}

.suggestion-list {
  margin: 0;
  padding-left: 20px;
  color: #334155;
  line-height: 1.8;
}

@media (max-width: 768px) {
  :deep(.el-col) {
    max-width: 100%;
    flex: 0 0 100%;
  }
}
</style>
