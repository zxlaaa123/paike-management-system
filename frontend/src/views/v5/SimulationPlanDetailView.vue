<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  applySimulation,
  confirmSimulation,
  discardSimulation,
  generateRepairExplanation,
  getLatestConsistencyReport,
  getSimulationDetail,
  runConsistencyCheck,
  type V5ConsistencyCheckReport,
  type V5RepairExplanation,
  type V5SimulationPlanDetail,
} from '../../api/v5SimulationApi'
import { extractMessage } from '../../utils/errors'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => Number(route.params.taskId))
const planId = computed(() => Number(route.params.planId))
const loading = ref(false)
const acting = ref(false)
const checking = ref(false)
const explaining = ref(false)
const detail = ref<V5SimulationPlanDetail | null>(null)
const consistencyReport = ref<V5ConsistencyCheckReport | null>(null)
const explanation = ref<V5RepairExplanation | null>(null)

function statusType(status?: string) {
  if (status === 'APPLIED') return 'success'
  if (status === 'DISCARDED') return 'info'
  if (status === 'CONFIRMED') return 'warning'
  return 'primary'
}

function deltaText(value?: number | null) {
  if (value == null) return '0'
  return value > 0 ? `+${value}` : `${value}`
}

function decimalDeltaText(value?: number | null) {
  if (value == null) return '0'
  const normalized = Number(value).toFixed(2)
  return value > 0 ? `+${normalized}` : normalized
}

function trendType(delta: number, improveWhenLower = false): '' | 'success' | 'danger' | 'info' {
  if (delta === 0) return 'info'
  const improved = improveWhenLower ? delta < 0 : delta > 0
  return improved ? 'success' : 'danger'
}

function trendText(delta: number, improveWhenLower = false) {
  if (delta === 0) return '无变化'
  const improved = improveWhenLower ? delta < 0 : delta > 0
  return improved ? '提升' : '下降'
}

function riskLevelTag(level?: string) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  if (level === 'LOW') return 'info'
  return ''
}

function formatPercent(value?: number | null) {
  return `${Number(value ?? 0).toFixed(2)}%`
}

const compare = computed(() => detail.value?.compare ?? null)
const canOperate = computed(() => ['SIMULATION', 'CONFIRMED'].includes(detail.value?.plan.status || ''))
const hasBlockingConflicts = computed(() => !!compare.value?.hasNewHardConflicts)
const hasConsistencyBlocking = computed(() => (consistencyReport.value?.blockingIssueCount ?? 0) > 0)
const canApply = computed(() => canOperate.value && !hasBlockingConflicts.value && !hasConsistencyBlocking.value)

function severityTag(severity?: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  if (severity === 'BLOCKING') return 'danger'
  if (severity === 'WARNING') return 'warning'
  if (severity === 'INFO') return 'info'
  return ''
}

function consistencyStatusTag(status?: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'PASS') return 'success'
  if (status === 'WARN') return 'warning'
  if (status === 'FAIL') return 'danger'
  return 'info'
}

function consistencyAlertType(status?: string): 'success' | 'warning' | 'error' | 'info' {
  if (status === 'PASS') return 'success'
  if (status === 'WARN') return 'warning'
  if (status === 'FAIL') return 'error'
  return 'info'
}

const metricRows = computed(() => {
  const c = compare.value
  if (!c) return []
  return [
    { key: 'score', label: '总评分', before: c.baselineScore, after: c.simulationScore, delta: c.scoreDelta, lowerBetter: false, formatter: decimalDeltaText },
    { key: 'scheduled', label: '已排任务数量', before: c.baselineScheduledCount, after: c.simulationScheduledCount, delta: c.scheduledDelta, lowerBetter: false, formatter: deltaText },
    { key: 'unscheduled', label: '未排任务数量', before: c.baselineUnscheduledCount, after: c.simulationUnscheduledCount, delta: c.unscheduledDelta, lowerBetter: true, formatter: deltaText },
    { key: 'high', label: '高风险数量', before: c.baselineHighRiskCount, after: c.simulationHighRiskCount, delta: c.highRiskDelta, lowerBetter: true, formatter: deltaText },
    { key: 'medium', label: '中风险数量', before: c.baselineMediumRiskCount, after: c.simulationMediumRiskCount, delta: c.mediumRiskDelta, lowerBetter: true, formatter: deltaText },
    { key: 'low', label: '低风险数量', before: c.baselineLowRiskCount, after: c.simulationLowRiskCount, delta: c.lowRiskDelta, lowerBetter: true, formatter: deltaText },
    { key: 'risk', label: '总风险数量', before: c.baselineRiskCount, after: c.simulationRiskCount, delta: c.riskDelta, lowerBetter: true, formatter: deltaText },
    { key: 'conflict', label: '硬冲突数量', before: c.baselineConflictCount, after: c.simulationConflictCount, delta: c.conflictDelta, lowerBetter: true, formatter: deltaText },
    { key: 'changed', label: '课程变动数量', before: 0, after: c.courseChangeCount, delta: c.courseChangeCount, lowerBetter: true, formatter: deltaText },
  ]
})

async function fetchData() {
  loading.value = true
  try {
    detail.value = await getSimulationDetail(taskId.value, planId.value)
    consistencyReport.value = detail.value?.latestConsistencyReport ?? null
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '加载试算方案失败'))
  } finally {
    loading.value = false
  }
}

async function triggerConsistencyCheck(opts?: { silent?: boolean }): Promise<V5ConsistencyCheckReport | null> {
  checking.value = true
  try {
    const report = await runConsistencyCheck(taskId.value, planId.value)
    consistencyReport.value = report
    if (!opts?.silent) {
      if (report.passed) {
        ElMessage.success(report.summary || '一致性校验通过')
      } else {
        ElMessage.warning(report.summary || '一致性校验存在阻塞问题')
      }
    }
    return report
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '一致性校验失败'))
    return null
  } finally {
    checking.value = false
  }
}

async function refreshConsistencyLatest() {
  try {
    consistencyReport.value = await getLatestConsistencyReport(taskId.value, planId.value)
  } catch {
    // 忽略历史报告读取错误
  }
}

async function handleGenerateExplanation() {
  explaining.value = true
  try {
    explanation.value = await generateRepairExplanation(taskId.value, planId.value)
    ElMessage.success(explanation.value?.remote ? 'AI 修复解释已生成' : 'AI 修复解释已生成（本地模板）')
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, 'AI 修复解释生成失败'))
  } finally {
    explaining.value = false
  }
}

function buildExplanationCopyText() {
  const e = explanation.value
  if (!e) return ''
  const lines = [
    `修复任务 ${e.taskId} · 试算方案 ${e.planId}`,
    `生成方式：${e.remote ? '远程 AI' : '本地模板'}`,
    `生成时间：${e.generatedAt}`,
    '',
    `【总体评价】${e.overallEvaluation}`,
    `【推荐理由】${e.recommendationReason}`,
  ]
  if (e.improvedMetrics?.length) {
    lines.push('【改善指标】')
    e.improvedMetrics.forEach((m, i) => lines.push(`  ${i + 1}. ${m}`))
  }
  if (e.remainingIssues?.length) {
    lines.push('【仍存在问题】')
    e.remainingIssues.forEach((m, i) => lines.push(`  ${i + 1}. ${m}`))
  }
  lines.push(`【应用建议】${e.applyAdvice}`)
  lines.push(`【答辩摘要】${e.defenseSummary}`)
  lines.push('')
  lines.push(e.disclaimer)
  return lines.join('\n')
}

async function handleCopyExplanation() {
  const text = buildExplanationCopyText()
  if (!text) {
    ElMessage.warning('暂无可复制内容')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制 AI 修复解释')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('已复制 AI 修复解释')
  }
}

async function confirmPlan() {
  acting.value = true
  try {
    detail.value = await confirmSimulation(taskId.value, planId.value)
    ElMessage.success('试算方案已确认')
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '确认失败'))
  } finally {
    acting.value = false
  }
}

async function applyPlan() {
  if (hasBlockingConflicts.value) {
    ElMessage.error(compare.value?.recommendationMessage || '存在新增硬冲突，不能应用')
    return
  }
  // 应用前自动触发一致性校验，确保后端最新状态没有 BLOCKING
  const preCheck = await triggerConsistencyCheck({ silent: true })
  if (!preCheck) return
  if (!preCheck.passed) {
    ElMessageBox.alert(preCheck.recommendation || '一致性校验存在阻塞问题，禁止应用', '应用被阻止', { type: 'error' })
    return
  }
  try {
    await ElMessageBox.confirm('应用后会写入正式课表，并替换当前已应用方案。', '应用试算方案', { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    if (detail.value?.plan.status === 'SIMULATION') {
      detail.value = await confirmSimulation(taskId.value, planId.value)
    }
    await applySimulation(taskId.value, planId.value)
    ElMessage.success('试算方案已应用')
    await fetchData()
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '应用失败'))
    await refreshConsistencyLatest()
  } finally {
    acting.value = false
  }
}

async function discardPlan() {
  try {
    await ElMessageBox.confirm('放弃后该试算方案不会进入正式课表。', '放弃试算方案', { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    detail.value = await discardSimulation(taskId.value, planId.value)
    ElMessage.success('试算方案已放弃')
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '放弃失败'))
  } finally {
    acting.value = false
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="page" v-loading="loading">
    <el-page-header content="试算方案详情" @back="router.push(`/v5/repair-tasks/${taskId}`)" />

    <el-card v-if="detail && compare" shadow="never" class="main-card">
      <template #header>
        <div class="header-row">
          <div>
            <div class="title">{{ detail.plan.name }}</div>
            <div class="sub">方案ID：{{ detail.plan.id }} · 任务ID：{{ taskId }} · 学期：{{ detail.plan.semesterId }}</div>
          </div>
          <div class="header-actions">
            <el-tag :type="statusType(detail.plan.status)">{{ detail.plan.status }}</el-tag>
            <el-tag :type="compare.recommended ? 'success' : 'danger'">
              {{ compare.recommended ? '推荐应用' : '不推荐应用' }}
            </el-tag>
          </div>
        </div>
      </template>

      <el-alert
        :type="compare.recommended ? 'success' : 'error'"
        :title="compare.recommendationMessage"
        :closable="false"
        show-icon
      />

      <el-row :gutter="12" class="block">
        <el-col :span="6"><el-statistic title="试算评分" :value="detail.plan.totalScore ?? 0" /></el-col>
        <el-col :span="6"><el-statistic title="风险数" :value="detail.risks.riskCount" /></el-col>
        <el-col :span="6"><el-statistic title="冲突数" :value="detail.plan.conflictCount" /></el-col>
        <el-col :span="6"><el-statistic title="课程变动数" :value="compare.courseChangeCount" /></el-col>
      </el-row>

      <el-row v-if="detail.localReplanSummary" :gutter="12" class="block">
        <el-col :span="6"><el-statistic title="局部范围课程" :value="detail.localReplanSummary.scopeItemCount" /></el-col>
        <el-col :span="6"><el-statistic title="锁定保留" :value="detail.localReplanSummary.lockedCount" /></el-col>
        <el-col :span="6"><el-statistic title="实际移动" :value="detail.localReplanSummary.movedCount" /></el-col>
        <el-col :span="6"><el-statistic title="重排失败" :value="detail.localReplanSummary.failedCount" /></el-col>
      </el-row>

      <el-descriptions :column="3" border class="block">
        <el-descriptions-item label="对比基线">{{ compare.baselinePlanName }}（{{ compare.baselinePlanId ?? compare.baselineSourceScheduleId ?? '正式课表' }}）</el-descriptions-item>
        <el-descriptions-item label="试算方案">{{ compare.simulationPlanName }}（{{ compare.simulationPlanId }}）</el-descriptions-item>
        <el-descriptions-item label="对比摘要">{{ compare.summary }}</el-descriptions-item>
        <el-descriptions-item label="锁定课程保护">
          <el-tag :type="compare.lockedCoursesPreserved ? 'success' : 'danger'">
            {{ compare.lockedCoursesPreserved ? '通过' : '未通过' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="新增硬冲突">
          <el-tag :type="compare.hasNewHardConflicts ? 'danger' : 'success'">
            {{ compare.hasNewHardConflicts ? `${compare.newHardConflictCount} 个` : '无' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="变动锁定课程">
          {{ compare.changedLockedCourseNames.length ? compare.changedLockedCourseNames.join('，') : '无' }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="actions" v-if="canOperate">
        <el-button type="primary" :loading="checking" @click="triggerConsistencyCheck()">一致性校验</el-button>
        <el-button type="warning" :loading="acting" @click="confirmPlan" v-if="detail.plan.status === 'SIMULATION'">确认试算方案</el-button>
        <el-button type="success" :loading="acting" :disabled="!canApply" @click="applyPlan">应用试算方案</el-button>
        <el-button type="danger" :loading="acting" @click="discardPlan">放弃试算方案</el-button>
      </div>
    </el-card>

    <el-card v-if="detail" shadow="never" class="main-card">
      <template #header>
        <div class="header-row">
          <div class="title">一致性校验</div>
          <div class="header-actions">
            <el-tag v-if="consistencyReport" :type="consistencyStatusTag(consistencyReport.status)">
              {{ consistencyReport.status }}
            </el-tag>
            <el-button size="small" type="primary" :loading="checking" @click="triggerConsistencyCheck()">立即校验</el-button>
          </div>
        </div>
      </template>
      <el-empty v-if="!consistencyReport" description="尚未执行一致性校验，请点击上方按钮触发" />
      <template v-else>
        <el-alert
          :type="consistencyAlertType(consistencyReport.status)"
          :title="consistencyReport.summary"
          :description="consistencyReport.recommendation"
          :closable="false"
          show-icon
        />
        <el-row :gutter="12" class="block">
          <el-col :span="6"><el-statistic title="阻塞问题" :value="consistencyReport.blockingIssueCount" /></el-col>
          <el-col :span="6"><el-statistic title="警告问题" :value="consistencyReport.warningIssueCount" /></el-col>
          <el-col :span="6"><el-statistic title="提示问题" :value="consistencyReport.infoIssueCount" /></el-col>
          <el-col :span="6"><el-statistic title="问题总数" :value="consistencyReport.issues?.length ?? 0" /></el-col>
        </el-row>
        <div class="sub" v-if="consistencyReport.checkedAt">检查时间：{{ consistencyReport.checkedAt }}</div>
        <el-empty v-if="!consistencyReport.issues?.length" description="未发现问题" class="block" />
        <el-table v-else :data="consistencyReport.issues" border stripe class="block">
          <el-table-column label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="severityTag(row.severity)">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="分类" width="100" />
          <el-table-column prop="name" label="规则" min-width="160" />
          <el-table-column prop="message" label="说明" min-width="260" show-overflow-tooltip />
          <el-table-column label="关联课程" min-width="180">
            <template #default="{ row }">
              <span v-if="row.courseName || row.teacherName || row.className">
                {{ row.courseName || '-' }} / {{ row.teacherName || '-' }} / {{ row.className || '-' }}
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="时间" min-width="140">
            <template #default="{ row }">
              <span v-if="row.weekday">周{{ row.weekday }} 第{{ row.startPeriod }}-{{ row.endPeriod }}节</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="suggestion" label="处理建议" min-width="220" show-overflow-tooltip />
        </el-table>
      </template>
    </el-card>

    <el-card v-if="detail" shadow="never" class="main-card">
      <template #header>
        <div class="header-row">
          <div class="title">AI 修复解释</div>
          <div class="header-actions">
            <el-tag v-if="explanation" :type="explanation.remote ? 'success' : 'info'">
              {{ explanation.remote ? '远程 AI' : '本地模板' }}
            </el-tag>
            <el-button size="small" type="primary" :loading="explaining" @click="handleGenerateExplanation">
              生成 AI 修复解释
            </el-button>
            <el-button size="small" :disabled="!explanation" @click="handleCopyExplanation">复制全文</el-button>
          </div>
        </div>
      </template>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="AI 建议仅供参考，最终以系统校验结果为准。AI 不会自动应用方案或修改数据。"
      />
      <el-empty v-if="!explanation" description="尚未生成 AI 修复解释，请点击上方按钮触发" class="block" />
      <template v-else>
        <el-descriptions :column="3" border class="block">
          <el-descriptions-item label="任务 ID">{{ explanation.taskId }}</el-descriptions-item>
          <el-descriptions-item label="试算方案 ID">{{ explanation.planId }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ explanation.generatedAt }}</el-descriptions-item>
          <el-descriptions-item label="是否建议应用" :span="3">
            <el-tag :type="explanation.recommendApply ? 'success' : 'danger'">
              {{ explanation.recommendApply ? '建议应用' : '不建议应用' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">总体评价</div>
        <el-alert type="info" :closable="false" :title="explanation.overallEvaluation" />

        <div class="section-title">推荐理由</div>
        <el-alert type="success" :closable="false" :title="explanation.recommendationReason" />

        <el-row :gutter="16" class="block">
          <el-col :span="12">
            <div class="section-title">改善的指标</div>
            <el-empty v-if="!explanation.improvedMetrics?.length" description="无显著改善指标" />
            <ul v-else class="ai-list">
              <li v-for="(item, idx) in explanation.improvedMetrics" :key="idx">{{ item }}</li>
            </ul>
          </el-col>
          <el-col :span="12">
            <div class="section-title">仍存在的问题</div>
            <el-empty v-if="!explanation.remainingIssues?.length" description="未发现遗留问题" />
            <ul v-else class="ai-list">
              <li v-for="(item, idx) in explanation.remainingIssues" :key="idx">{{ item }}</li>
            </ul>
          </el-col>
        </el-row>

        <div class="section-title">应用建议</div>
        <el-alert :type="explanation.recommendApply ? 'success' : 'warning'" :closable="false" :title="explanation.applyAdvice" />

        <div class="section-title">答辩展示摘要</div>
        <el-alert type="info" :closable="false" :title="explanation.defenseSummary" />

        <div class="sub block">{{ explanation.disclaimer }}</div>
      </template>
    </el-card>

    <el-card v-if="compare" shadow="never" class="main-card">
      <template #header><div class="title">优化前后对比</div></template>
      <el-table :data="metricRows" border stripe>
        <el-table-column prop="label" label="指标" min-width="160" />
        <el-table-column label="优化前" min-width="140">
          <template #default="{ row }">{{ row.before }}</template>
        </el-table-column>
        <el-table-column label="优化后" min-width="140">
          <template #default="{ row }">{{ row.after }}</template>
        </el-table-column>
        <el-table-column label="变化值" min-width="140">
          <template #default="{ row }">{{ row.formatter(row.delta) }}</template>
        </el-table-column>
        <el-table-column label="趋势" width="120">
          <template #default="{ row }">
            <el-tag :type="trendType(Number(row.delta), row.lowerBetter)">{{ trendText(Number(row.delta), row.lowerBetter) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="compare" shadow="never" class="main-card">
      <template #header><div class="title">问题变化</div></template>
      <el-row :gutter="16">
        <el-col :span="12">
          <div class="section-title">已解决问题</div>
          <el-empty v-if="!compare.resolvedRisks.length" description="没有已解决风险" />
          <el-table v-else :data="compare.resolvedRisks" border stripe>
            <el-table-column label="等级" width="100">
              <template #default="{ row }"><el-tag :type="riskLevelTag(row.level)">{{ row.level }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="riskTypeName" label="类型" min-width="120" />
            <el-table-column prop="title" label="标题" min-width="180" />
            <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
          </el-table>
        </el-col>
        <el-col :span="12">
          <div class="section-title">新增问题</div>
          <el-empty v-if="!compare.newRisks.length" description="没有新增风险" />
          <el-table v-else :data="compare.newRisks" border stripe>
            <el-table-column label="等级" width="100">
              <template #default="{ row }"><el-tag :type="riskLevelTag(row.level)">{{ row.level }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="riskTypeName" label="类型" min-width="120" />
            <el-table-column prop="title" label="标题" min-width="180" />
            <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
          </el-table>
        </el-col>
      </el-row>
    </el-card>

    <el-card v-if="compare" shadow="never" class="main-card">
      <template #header><div class="title">负载与利用率变化</div></template>
      <div class="section-title">教师负载变化</div>
      <el-empty v-if="!compare.teacherLoadChanges.length" description="教师负载无变化" />
      <el-table v-else :data="compare.teacherLoadChanges" border stripe class="block">
        <el-table-column prop="entityName" label="教师" min-width="160" />
        <el-table-column prop="baselineLoad" label="优化前" width="120" />
        <el-table-column prop="simulationLoad" label="优化后" width="120" />
        <el-table-column label="变化" width="120">
          <template #default="{ row }">
            <el-tag :type="row.delta === 0 ? 'info' : 'warning'">{{ deltaText(row.delta) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="section-title">班级负载变化</div>
      <el-empty v-if="!compare.classLoadChanges.length" description="班级负载无变化" />
      <el-table v-else :data="compare.classLoadChanges" border stripe class="block">
        <el-table-column prop="entityName" label="班级" min-width="160" />
        <el-table-column prop="baselineLoad" label="优化前" width="120" />
        <el-table-column prop="simulationLoad" label="优化后" width="120" />
        <el-table-column label="变化" width="120">
          <template #default="{ row }">
            <el-tag :type="row.delta === 0 ? 'info' : 'warning'">{{ deltaText(row.delta) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="section-title">教室利用率变化</div>
      <el-empty v-if="!compare.roomUtilizationChanges.length" description="教室利用率无变化" />
      <el-table v-else :data="compare.roomUtilizationChanges" border stripe class="block">
        <el-table-column prop="classroomName" label="教室" min-width="160" />
        <el-table-column label="优化前" min-width="140">
          <template #default="{ row }">{{ row.baselineUsedPeriods }} / {{ formatPercent(row.baselineUtilizationRate) }}</template>
        </el-table-column>
        <el-table-column label="优化后" min-width="140">
          <template #default="{ row }">{{ row.simulationUsedPeriods }} / {{ formatPercent(row.simulationUtilizationRate) }}</template>
        </el-table-column>
        <el-table-column label="变化" min-width="140">
          <template #default="{ row }">
            <el-tag :type="row.utilizationDelta === 0 ? 'info' : 'warning'">{{ decimalDeltaText(row.utilizationDelta) }}%</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="compare" shadow="never" class="main-card">
      <template #header><div class="title">课程变动明细</div></template>
      <el-table :data="compare.changedItems" border stripe>
        <el-table-column label="课程" prop="courseName" min-width="140" />
        <el-table-column label="教师" prop="teacherName" min-width="120" />
        <el-table-column label="班级" prop="className" min-width="120" />
        <el-table-column label="调整前" min-width="180">
          <template #default="{ row }">周{{ row.beforeWeekday }} {{ row.beforeStartPeriod }}-{{ row.beforeEndPeriod }} {{ row.beforeClassroomName || '-' }}</template>
        </el-table-column>
        <el-table-column label="调整后" min-width="180">
          <template #default="{ row }">周{{ row.afterWeekday }} {{ row.afterStartPeriod }}-{{ row.afterEndPeriod }} {{ row.afterClassroomName || '-' }}</template>
        </el-table-column>
        <el-table-column label="冲突" width="100">
          <template #default="{ row }"><el-tag :type="row.conflictFlag ? 'danger' : 'success'">{{ row.conflictFlag ? '有' : '无' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="原因" prop="conflictReason" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card v-if="detail?.localReplanSummary || detail?.adjustLogs?.length" shadow="never" class="main-card">
      <template #header><div class="title">局部重排日志</div></template>
      <el-timeline v-if="detail?.localReplanSummary?.logs?.length">
        <el-timeline-item v-for="(log, index) in detail.localReplanSummary.logs" :key="index" type="primary">
          {{ log }}
        </el-timeline-item>
      </el-timeline>
      <el-table v-if="detail?.adjustLogs?.length" :data="detail.adjustLogs" border stripe class="block">
        <el-table-column prop="courseName" label="课程" min-width="140" />
        <el-table-column prop="teacherName" label="教师" min-width="120" />
        <el-table-column prop="className" label="班级" min-width="120" />
        <el-table-column label="原位置" min-width="170">
          <template #default="{ row }">周{{ row.oldWeekday ?? '-' }} {{ row.oldStartPeriod ?? '-' }}-{{ row.oldEndPeriod ?? '-' }} {{ row.oldClassroomName || '-' }}</template>
        </el-table-column>
        <el-table-column label="新位置" min-width="170">
          <template #default="{ row }">周{{ row.newWeekday ?? '-' }} {{ row.newStartPeriod ?? '-' }}-{{ row.newEndPeriod ?? '-' }} {{ row.newClassroomName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="afterScore" label="评分变化" width="110" />
        <el-table-column prop="adjustReason" label="日志说明" min-width="240" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card v-if="detail" shadow="never" class="main-card">
      <template #header><div class="title">试算课表</div></template>
      <el-table :data="detail.items" border stripe>
        <el-table-column prop="courseName" label="课程" min-width="140" />
        <el-table-column prop="teacherName" label="教师" min-width="120" />
        <el-table-column prop="className" label="班级" min-width="120" />
        <el-table-column prop="roomName" label="教室" min-width="120" />
        <el-table-column label="时间" min-width="140">
          <template #default="{ row }">周{{ row.weekday }} 第{{ row.startPeriod }}-{{ row.endPeriod }}节</template>
        </el-table-column>
        <el-table-column label="冲突" width="100">
          <template #default="{ row }"><el-tag :type="row.conflictFlag ? 'danger' : 'success'">{{ row.conflictFlag ? '有' : '无' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="conflictReason" label="冲突原因" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card v-if="detail" shadow="never" class="main-card">
      <template #header><div class="title">评分与风险</div></template>
      <el-table :data="detail.scoreDetails" border stripe>
        <el-table-column prop="ruleName" label="规则" min-width="180" />
        <el-table-column prop="ruleType" label="类型" width="100" />
        <el-table-column prop="score" label="得分/扣分" width="120" />
        <el-table-column prop="violationCount" label="违规数" width="100" />
        <el-table-column prop="detailMessage" label="说明" min-width="240" show-overflow-tooltip />
      </el-table>
      <el-table :data="detail.risks.risks" border stripe class="block">
        <el-table-column prop="level" label="等级" width="100" />
        <el-table-column prop="riskTypeName" label="风险类型" min-width="140" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="建议" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.main-card { border-radius: 8px; }
.header-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.header-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.title { font-size: 18px; font-weight: 700; color: #243447; }
.sub { margin-top: 6px; color: #667085; font-size: 13px; }
.block { margin-top: 16px; }
.actions { margin-top: 16px; display: flex; gap: 10px; flex-wrap: wrap; }
.section-title { margin-top: 12px; margin-bottom: 8px; font-size: 14px; font-weight: 600; color: #344054; }
.ai-list { margin: 0; padding-left: 20px; color: #344054; line-height: 1.9; }
.ai-list li { margin-bottom: 2px; }
</style>
