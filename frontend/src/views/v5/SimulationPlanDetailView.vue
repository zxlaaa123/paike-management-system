<template>
  <div class="page" v-loading="loading">
    <el-page-header content="试算方案详情" @back="router.push(`/v5/repair-tasks/${taskId}`)" />
    <OverviewCard
      :detail="detail"
      :compare="compare"
      :task-id="taskId"
      :can-operate="canOperate"
      :can-apply="canApply"
      :has-blocking-conflicts="hasBlockingConflicts"
      :acting="acting"
      :checking="checking"
      @check="triggerConsistencyCheck()"
      @confirm="confirmPlan"
      @apply="applyPlan"
      @discard="discardPlan"
    /><ConsistencyCard :detail="detail" :consistency-report="consistencyReport" :checking="checking" @check="triggerConsistencyCheck()" />
    <ExplanationCard :detail="detail" :explanation="explanation" :explaining="explaining" @generate="handleGenerateExplanation" @copy="handleCopyExplanation" />
    <MetricsCompareCard v-if="compare" :metric-rows="metricRows" />
    <RiskChangesCard v-if="compare" :compare="compare" />
    <LoadUtilizationCard v-if="compare" :compare="compare" />
    <ScheduleListCards :detail="detail" :compare="compare" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applySimulation, confirmSimulation, discardSimulation, generateRepairExplanation, getLatestConsistencyReport, getSimulationDetail, runConsistencyCheck, type V5ConsistencyCheckReport, type V5RepairExplanation, type V5SimulationCompare, type V5SimulationPlanDetail } from '../../api/v5SimulationApi'
import { extractMessage } from '../../utils/errors'
import { copyText } from '../../utils/clipboard'
import { buildExplanationCopyText, buildMetricRows } from './simulationPlanDetail/formatters'
import OverviewCard from './simulationPlanDetail/OverviewCard.vue'
import ConsistencyCard from './simulationPlanDetail/ConsistencyCard.vue'
import ExplanationCard from './simulationPlanDetail/ExplanationCard.vue'
import MetricsCompareCard from './simulationPlanDetail/MetricsCompareCard.vue'
import RiskChangesCard from './simulationPlanDetail/RiskChangesCard.vue'
import LoadUtilizationCard from './simulationPlanDetail/LoadUtilizationCard.vue'
import ScheduleListCards from './simulationPlanDetail/ScheduleListCards.vue'

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
const compare = computed<V5SimulationCompare | null>(() => detail.value?.compare ?? null)
const canOperate = computed(() => ['SIMULATION', 'CONFIRMED'].includes(detail.value?.plan.status || ''))
const hasBlockingConflicts = computed(() => !!compare.value?.hasNewHardConflicts)
const hasConsistencyBlocking = computed(() => (consistencyReport.value?.blockingIssueCount ?? 0) > 0)
const canApply = computed(() => canOperate.value && !hasBlockingConflicts.value && !hasConsistencyBlocking.value)

const metricRows = computed(() => buildMetricRows(compare.value))
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
async function handleCopyExplanation() {
  const text = buildExplanationCopyText(explanation.value)
  if (!text) {
    ElMessage.warning('暂无可复制内容')
    return
  }
  if (await copyText(text)) {
    ElMessage.success('已复制 AI 修复解释')
  } else {
    ElMessage.error('复制失败，请手动复制')
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
  let confirmedBeforeApply = false
  try {
    if (detail.value?.plan.status === 'SIMULATION') {
      detail.value = await confirmSimulation(taskId.value, planId.value)
      confirmedBeforeApply = true
    }
    await applySimulation(taskId.value, planId.value)
    ElMessage.success('试算方案已应用')
    await fetchData()
  } catch (error: unknown) {
    const fallback = confirmedBeforeApply ? '方案已确认但应用失败，请刷新后重试' : '应用失败'
    ElMessage.error(extractMessage(error, fallback))
    await fetchData()
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
</script><style>
/* 去掉 scoped 以便子组件复用 class，详见 D3.3 拆分 */ .page { display: flex; flex-direction: column; gap: 16px; } .main-card { border-radius: 8px; } .header-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; } .header-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.title { font-size: 18px; font-weight: 700; color: #243447; }
.sub { margin-top: 6px; color: #667085; font-size: 13px; }
.block { margin-top: 16px; }
.actions { margin-top: 16px; display: flex; gap: 10px; flex-wrap: wrap; }
.section-title { margin-top: 12px; margin-bottom: 8px; font-size: 14px; font-weight: 600; color: #344054; }
.ai-list { margin: 0; padding-left: 20px; color: #344054; line-height: 1.9; }
.ai-list li { margin-bottom: 2px; }
</style>
