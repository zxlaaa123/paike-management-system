<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ScheduleAdjustDialog from '../../components/v4/ScheduleAdjustDialog.vue'
import LocalReplanDialog from '../../components/v4/LocalReplanDialog.vue'
import { getAllClassrooms, type Classroom } from '../../api/classroom'
import { getAllTimeSlots, type TimeSlot } from '../../api/timeSlot'
import {
  applySchedulePlan,
  getScheduleAdjustLogs,
  getSchedulePlanById,
  getSchedulePlanItems,
  getSchedulePlanLogs,
  getSchedulePlanTaskLogs,
  getSchedulePlanUnassignedSummary,
  getSchedulePlanUnassignedTasks,
  rollbackSchedulePlan,
  type ScheduleAdjustLog,
  type ScheduleGenerateLog,
  type SchedulePlan,
  type SchedulePlanItem,
  type ScheduleUnassignedTask,
  type UnassignedSummaryItem,
} from '../../api/schedulePlan'
import { createRepairTask } from '../../api/v5RepairTaskApi'
import type { ScheduleReplanResult } from '../../api/v4ScheduleReplanApi'
import { getScoreDetails, getScoreSummary, rescore, type ScheduleScoreDetail, type ScoreSummary } from '../../api/scheduleScore'
import { schedulePlanStatusTagType as statusTagType, schedulePlanStatusText as statusText, strategyText } from '../../utils/status'
import { extractMessage } from '../../utils/errors'

const route = useRoute()
const router = useRouter()
const planId = computed(() => Number(route.params.id))

const loading = ref(false)
const scoring = ref(false)
const applying = ref(false)
const logLoading = ref(false)
const unassignedLoading = ref(false)
const adjustLogLoading = ref(false)
const taskLogLoading = ref(false)
const plan = ref<SchedulePlan | null>(null)
const items = ref<SchedulePlanItem[]>([])
const scoreDetails = ref<ScheduleScoreDetail[]>([])
const scoreSummary = ref<ScoreSummary | null>(null)
const generateLogs = ref<ScheduleGenerateLog[]>([])
const unassignedTasks = ref<ScheduleUnassignedTask[]>([])
const unassignedSummary = ref<UnassignedSummaryItem[]>([])
const adjustLogs = ref<ScheduleAdjustLog[]>([])
const adjustLogTotal = ref(0)
const adjustLogPageNum = ref(1)
const adjustLogPageSize = ref(10)
const classroomOptions = ref<Classroom[]>([])
const timeSlotOptions = ref<TimeSlot[]>([])

const activeTab = ref('items')
const taskLogDialogVisible = ref(false)
const taskLogTitle = ref('')
const currentTaskLogs = ref<ScheduleGenerateLog[]>([])
const currentTaskId = ref<number | null>(null)

const adjustDialogVisible = ref(false)
const adjustingItem = ref<SchedulePlanItem | null>(null)
const localReplanVisible = ref(false)
const creatingRepair = ref(false)

async function fetchData() {
  loading.value = true
  try {
    const [planData, itemsData] = await Promise.all([
      getSchedulePlanById(planId.value),
      getSchedulePlanItems(planId.value),
    ])
    plan.value = planData
    items.value = itemsData
  } catch (e: unknown) {
    ElMessage.error(extractMessage(e, '加载方案详情失败'))
  } finally {
    loading.value = false
  }
}

async function fetchScoreData() {
  try {
    const [details, summary] = await Promise.all([
      getScoreDetails(planId.value),
      getScoreSummary(planId.value),
    ])
    scoreDetails.value = details
    scoreSummary.value = summary
  } catch (e: unknown) {
    ElMessage.error(extractMessage(e, '加载评分数据失败'))
  }
}

async function loadExplainData() {
  await Promise.all([loadLogs(), loadUnassigned(), loadAdjustLogs()])
}

async function loadLogs() {
  logLoading.value = true
  try {
    generateLogs.value = await getSchedulePlanLogs(planId.value)
  } catch (e: unknown) {
    ElMessage.error(extractMessage(e, '加载排课日志失败'))
  } finally {
    logLoading.value = false
  }
}

async function loadUnassigned() {
  unassignedLoading.value = true
  try {
    const [tasks, summary] = await Promise.all([
      getSchedulePlanUnassignedTasks(planId.value),
      getSchedulePlanUnassignedSummary(planId.value),
    ])
    unassignedTasks.value = tasks
    unassignedSummary.value = summary
  } catch (e: unknown) {
    ElMessage.error(extractMessage(e, '加载未排任务失败'))
  } finally {
    unassignedLoading.value = false
  }
}

async function loadAdjustLogs() {
  adjustLogLoading.value = true
  try {
    const page = await getScheduleAdjustLogs({
      planId: planId.value,
      pageNum: adjustLogPageNum.value,
      pageSize: adjustLogPageSize.value,
    })
    adjustLogs.value = page.records || []
    adjustLogTotal.value = page.total || 0
    adjustLogPageNum.value = page.current || 1
    adjustLogPageSize.value = page.size || 10
  } catch (e: unknown) {
    ElMessage.error(extractMessage(e, '加载调整日志失败'))
  } finally {
    adjustLogLoading.value = false
  }
}

async function handleRescore() {
  scoring.value = true
  try {
    const result = await rescore(planId.value)
    ElMessage.success(`重新评分完成，总分：${result.totalScore}，冲突：${result.conflictCount}`)
    await fetchScoreData()
    await fetchData()
  } catch (e: unknown) {
    ElMessage.error(extractMessage(e, '重新评分失败'))
  } finally {
    scoring.value = false
  }
}

async function handleApply() {
  if (!plan.value) return
  if (plan.value.status === 'ABANDONED') {
    ElMessage.warning('已废弃方案不能应用')
    return
  }

  if (plan.value.conflictCount > 0) {
    await ElMessageBox.alert(
      `方案存在 ${plan.value.conflictCount} 项冲突，请先调整或重排冲突项后再应用`,
      '应用被阻止',
      { type: 'error', confirmButtonText: '我知道了' }
    )
    return
  }

  if (plan.value.unscheduledCount > 0) {
    await ElMessageBox.confirm(
      `该方案存在 ${plan.value.unscheduledCount} 个未排任务，确定要继续应用吗？`,
      '应用方案',
      {
        type: 'warning',
        confirmButtonText: '仍要应用',
        cancelButtonText: '取消',
      }
    )
  } else {
    await ElMessageBox.confirm(`确定将「${plan.value.name}」应用为当前学期正式课表吗？`, '应用方案', {
      type: 'info',
      confirmButtonText: '确定应用',
      cancelButtonText: '取消',
    })
  }

  applying.value = true
  try {
    const result = await applySchedulePlan(planId.value)
    ElMessage.success(`方案已应用，共写入 ${result.appliedCount} 条课表记录`)
    await fetchData()
  } catch (e: unknown) {
    ElMessage.error(extractMessage(e, '应用失败'))
  } finally {
    applying.value = false
  }
}

async function handleRollback() {
  if (!plan.value) return
  if (plan.value.status === 'ABANDONED') {
    ElMessage.warning('已废弃方案不能回滚')
    return
  }

  await ElMessageBox.confirm(
    `确定回滚到「${plan.value.name}」吗？这将替换当前学期的正式课表。`,
    '回滚方案',
    { type: 'warning', confirmButtonText: '确定回滚', cancelButtonText: '取消' }
  )

  applying.value = true
  try {
    const result = await rollbackSchedulePlan(planId.value)
    ElMessage.success(`已回滚到该方案，共写入 ${result.appliedCount} 条课表记录`)
    await fetchData()
  } catch (e: unknown) {
    ElMessage.error(extractMessage(e, '回滚失败'))
  } finally {
    applying.value = false
  }
}

async function openTaskLogs(item: SchedulePlanItem) {
  taskLogLoading.value = true
  currentTaskId.value = item.teachingTaskId
  taskLogTitle.value = `${item.courseName || '课程'} - ${item.className || '班级'} 日志`
  taskLogDialogVisible.value = true
  try {
    currentTaskLogs.value = await getSchedulePlanTaskLogs(planId.value, item.teachingTaskId)
  } catch (e: unknown) {
    currentTaskLogs.value = []
    ElMessage.error(extractMessage(e, '加载任务日志失败'))
  } finally {
    taskLogLoading.value = false
  }
}

function openAdjustDialog(item: SchedulePlanItem) {
  adjustingItem.value = item
  adjustDialogVisible.value = true
}

function openCandidatesByItem(item: SchedulePlanItem) {
  router.push(`/v5/candidate-positions?planItemId=${item.id}`)
}

async function handleAdjustSuccess() {
  adjustDialogVisible.value = false
  await Promise.all([fetchData(), fetchScoreData(), loadExplainData()])
  if (taskLogDialogVisible.value && currentTaskId.value) {
    currentTaskLogs.value = await getSchedulePlanTaskLogs(planId.value, currentTaskId.value)
  }
}

function handleLocalReplanSuccess(result: ScheduleReplanResult) {
  localReplanVisible.value = false
  router.push(`/v3/schedule-plans/${result.newPlanId}`)
}

function scoreLevelType(level: string) {
  const map: Record<string, string> = { 优秀: 'success', 良好: 'primary', 一般: 'warning', 较差: 'danger', 不推荐: 'danger' }
  return map[level] || 'info'
}

function logLevelType(level: string) {
  const map: Record<string, string> = { INFO: 'primary', WARN: 'warning', ERROR: 'danger' }
  return map[level] || 'info'
}

function logTypeText(type: string) {
  const map: Record<string, string> = {
    START_GENERATE: '开始生成',
    LOAD_TASK: '读取任务',
    CHECK_TEACHER: '检查教师',
    CHECK_CLASSROOM: '检查教室',
    CHECK_CLASS: '检查班级',
    CALCULATE_SCORE: '计算评分',
    ASSIGN_SUCCESS: '排课成功',
    ASSIGN_FAILED: '排课失败',
    GENERATE_SCORE: '生成评分',
    FINISH_GENERATE: '生成完成',
  }
  return map[type] || type
}

function handleAdjustLogPageChange(page: number) {
  adjustLogPageNum.value = page
  loadAdjustLogs()
}

const adjustContext = computed(() => {
  if (!adjustingItem.value) return null
  return {
    targetType: 'PLAN_ITEM' as const,
    planId: planId.value,
    planItemId: adjustingItem.value.id,
    courseName: adjustingItem.value.courseName,
    teacherName: adjustingItem.value.teacherName,
    className: adjustingItem.value.className,
    currentRoomId: adjustingItem.value.classroomId,
    currentRoomName: adjustingItem.value.roomName,
    currentWeekDay: adjustingItem.value.weekday,
    currentPeriodStart: adjustingItem.value.startPeriod,
    currentPeriodEnd: adjustingItem.value.endPeriod,
    currentTimeLabel: adjustingItem.value.timeLabel,
  }
})

function goBack() {
  router.push('/v3/schedule-plans')
}

async function createRepairTaskFromPlan() {
  if (!plan.value) return
  creatingRepair.value = true
  try {
    const task = await createRepairTask({
      semesterId: plan.value.semesterId,
      planId: plan.value.id,
      sourcePlanId: plan.value.id,
      taskType: 'LOCAL_REPLAN',
      title: `方案修复：${plan.value.name}`,
      triggerSource: 'MANUAL',
      riskTypes: [],
      riskItemIds: [],
      scopePlanItemIds: [],
    })
    ElMessage.success('修复任务已创建')
    router.push(`/v5/repair-tasks/${task.id}`)
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '创建修复任务失败'))
  } finally {
    creatingRepair.value = false
  }
}

onMounted(async () => {
  await Promise.all([
    fetchData(),
    fetchScoreData(),
    loadExplainData(),
    getAllClassrooms().then((data) => {
      classroomOptions.value = data
    }),
    getAllTimeSlots().then((data) => {
      timeSlotOptions.value = data
    }),
  ])
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-page-header @back="goBack" content="排课方案详情" />

    <template v-if="plan">
      <el-card shadow="never" style="margin-top: 16px">
        <template #header>
          <div class="card-header">
            <span>{{ plan.name }}</span>
            <div>
              <el-tag :type="statusTagType(plan.status)" style="margin-right: 8px">{{ statusText(plan.status) }}</el-tag>
              <el-button
                v-if="plan.status === 'DRAFT'"
                type="primary"
                size="small"
                :loading="applying"
                @click="handleApply"
              >应用方案</el-button>
              <el-button
                v-if="plan.status !== 'ABANDONED'"
                type="warning"
                size="small"
                :loading="applying"
                @click="handleRollback"
              >{{ plan.status === 'APPLIED' ? '重新应用' : '回滚应用' }}</el-button>
            </div>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="所属学期">{{ plan.semesterName || `ID:${plan.semesterId}` }}</el-descriptions-item>
          <el-descriptions-item label="策略类型">{{ strategyText(plan.strategyType) }}</el-descriptions-item>
          <el-descriptions-item label="方案状态">{{ statusText(plan.status) }}</el-descriptions-item>
          <el-descriptions-item label="总分">
            <span v-if="plan.totalScore !== null">{{ plan.totalScore }}</span>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="已排任务">{{ plan.scheduledCount }}</el-descriptions-item>
          <el-descriptions-item label="未排任务">{{ plan.unscheduledCount }}</el-descriptions-item>
          <el-descriptions-item label="冲突数量">{{ plan.conflictCount }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ plan.generatedAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="应用时间">{{ plan.appliedAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="方案说明" :span="3">{{ plan.description || '—' }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 16px; display: flex; gap: 12px; flex-wrap: wrap">
          <el-button type="warning" plain @click="router.push(`/v4/schedule-analysis/${plan.id}`)">
            进入 V4 质量分析
          </el-button>
          <el-button type="info" plain @click="router.push(`/v4/schedule-analysis/${plan.id}/locks`)">
            课程锁定管理
          </el-button>
          <el-button
            type="success"
            plain
            :disabled="plan.status === 'ABANDONED' || plan.status === 'FAILED'"
            @click="localReplanVisible = true"
          >
            局部重排生成新方案
          </el-button>
          <el-button
            type="primary"
            plain
            :loading="creatingRepair"
            :disabled="plan.status === 'ABANDONED'"
            @click="createRepairTaskFromPlan"
          >
            创建修复任务
          </el-button>
        </div>
      </el-card>

      <el-card shadow="never" style="margin-top: 16px">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="课表明细" name="items">
            <el-table :data="items" stripe>
              <el-table-column prop="courseName" label="课程" width="120" />
              <el-table-column prop="teacherName" label="教师" width="100" />
              <el-table-column prop="className" label="班级" width="120" />
              <el-table-column label="时间" width="120">
                <template #default="{ row }">周{{ row.weekday }} 第{{ row.startPeriod }}-{{ row.endPeriod }}节</template>
              </el-table-column>
              <el-table-column prop="roomName" label="教室" width="120" />
              <el-table-column label="来源" width="80">
                <template #default="{ row }">
                  <el-tag size="small">{{ row.sourceType === 'MANUAL' ? '手动' : '自动' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="冲突" width="70">
                <template #default="{ row }">
                  <el-tag v-if="row.conflictFlag === 1" type="danger" size="small">有冲突</el-tag>
                  <span v-else>无</span>
                </template>
              </el-table-column>
              <el-table-column prop="conflictReason" label="冲突原因" min-width="180" />
              <el-table-column label="操作" width="220">
                <template #default="{ row }">
                  <el-button type="primary" link size="small" @click="openTaskLogs(row)">日志</el-button>
                  <el-button type="info" link size="small" @click="openCandidatesByItem(row)">候选位置</el-button>
                  <el-button v-if="plan.status !== 'ABANDONED'" type="warning" link size="small" @click="openAdjustDialog(row)">调整</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="items.length === 0" description="暂无方案明细" />
          </el-tab-pane>

          <el-tab-pane label="生成日志" name="logs">
            <div class="summary-note">展示自动排课过程中的关键步骤、候选筛选和成功/失败原因。</div>
            <el-table :data="generateLogs" stripe size="small" v-loading="logLoading">
              <el-table-column prop="stepNo" label="步骤" width="80" />
              <el-table-column prop="logLevel" label="级别" width="90">
                <template #default="{ row }"><el-tag :type="logLevelType(row.logLevel)" size="small">{{ row.logLevel }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="logType" label="类型" width="130">
                <template #default="{ row }">{{ logTypeText(row.logType) }}</template>
              </el-table-column>
              <el-table-column prop="message" label="日志内容" min-width="360" />
              <el-table-column prop="createdAt" label="时间" width="180" />
            </el-table>
            <el-empty v-if="!logLoading && generateLogs.length === 0" description="暂无生成日志" />
          </el-tab-pane>

          <el-tab-pane label="未排任务" name="unassigned">
            <el-row :gutter="12" style="margin-bottom: 12px">
              <el-col v-for="row in unassignedSummary" :key="row.reasonCode" :span="6">
                <el-card shadow="never">
                  <el-statistic :title="row.reasonName" :value="row.count" />
                </el-card>
              </el-col>
            </el-row>
            <el-table :data="unassignedTasks" stripe size="small" v-loading="unassignedLoading">
              <el-table-column prop="courseName" label="课程" width="120" />
              <el-table-column prop="teacherName" label="教师" width="100" />
              <el-table-column prop="className" label="班级" width="120" />
              <el-table-column prop="reasonCode" label="原因码" width="180" />
              <el-table-column prop="reasonMessage" label="原因说明" min-width="240" />
              <el-table-column prop="suggestion" label="建议" min-width="240" />
            </el-table>
            <el-empty v-if="!unassignedLoading && unassignedTasks.length === 0" description="暂无未排任务" />
          </el-tab-pane>

          <el-tab-pane label="评分明细" name="score">
            <div style="margin-bottom: 16px">
              <el-button type="primary" @click="handleRescore" :loading="scoring">重新评分</el-button>
            </div>
            <el-row :gutter="16" v-if="scoreSummary" style="margin-bottom: 16px">
              <el-col :span="6">
                <el-statistic title="总分" :value="scoreSummary.totalScore">
                  <template #suffix>
                    <el-tag :type="scoreLevelType(scoreSummary.scoreLevel)" size="small">{{ scoreSummary.scoreLevel }}</el-tag>
                  </template>
                </el-statistic>
              </el-col>
              <el-col :span="6">
                <el-statistic title="硬约束违规" :value="scoreSummary.hardViolationCount" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="软约束扣分项" :value="scoreSummary.softViolationCount" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="冲突数量" :value="scoreSummary.conflictCount ?? plan.conflictCount" />
              </el-col>
            </el-row>
            <el-table :data="scoreDetails" stripe size="small">
              <el-table-column prop="ruleName" label="评分项" width="150" />
              <el-table-column label="类型" width="80">
                <template #default="{ row }">
                  <el-tag size="small">{{ row.score < 0 ? '扣分' : '正常' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="score" label="得分" width="80" />
              <el-table-column prop="violationCount" label="违规/偏差值" width="110" />
              <el-table-column prop="detailMessage" label="说明" min-width="200" />
            </el-table>
            <el-empty v-if="scoreDetails.length === 0" description="暂无评分数据，请点击「重新评分」" />
          </el-tab-pane>

          <el-tab-pane label="调整记录" name="adjust">
            <el-table :data="adjustLogs" stripe size="small" v-loading="adjustLogLoading">
              <el-table-column prop="courseName" label="课程" width="120" />
              <el-table-column prop="teacherName" label="教师" width="100" />
              <el-table-column prop="className" label="班级" width="120" />
              <el-table-column label="调整前" min-width="160">
                <template #default="{ row }">
                  {{ row.oldClassroomName || '—' }} / 周{{ row.oldWeekday }} 第{{ row.oldStartPeriod }}-{{ row.oldEndPeriod }}节
                </template>
              </el-table-column>
              <el-table-column label="调整后" min-width="160">
                <template #default="{ row }">
                  {{ row.newClassroomName || '—' }} / 周{{ row.newWeekday }} 第{{ row.newStartPeriod }}-{{ row.newEndPeriod }}节
                </template>
              </el-table-column>
              <el-table-column label="评分变化" width="150">
                <template #default="{ row }">{{ row.beforeScore ?? '—' }} → {{ row.afterScore ?? '—' }}</template>
              </el-table-column>
              <el-table-column label="冲突" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.conflictFlag === 1 ? 'danger' : 'success'" size="small">
                    {{ row.conflictFlag === 1 ? '有' : '无' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="adjustReason" label="调整原因" min-width="220" />
              <el-table-column prop="createdAt" label="时间" width="180" />
            </el-table>
            <div style="margin-top: 12px; display: flex; justify-content: flex-end">
              <el-pagination
                background
                layout="prev, pager, next, jumper, total"
                :current-page="adjustLogPageNum"
                :page-size="adjustLogPageSize"
                :total="adjustLogTotal"
                @current-change="handleAdjustLogPageChange"
              />
            </div>
            <el-empty v-if="!adjustLogLoading && adjustLogs.length === 0" description="暂无调整记录" />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>

    <el-dialog v-model="taskLogDialogVisible" :title="taskLogTitle" width="760px" destroy-on-close>
      <el-table :data="currentTaskLogs" stripe size="small" v-loading="taskLogLoading">
        <el-table-column prop="stepNo" label="步骤" width="70" />
        <el-table-column prop="logLevel" label="级别" width="90">
          <template #default="{ row }"><el-tag :type="logLevelType(row.logLevel)" size="small">{{ row.logLevel }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="logType" label="类型" width="130">
          <template #default="{ row }">{{ logTypeText(row.logType) }}</template>
        </el-table-column>
        <el-table-column prop="message" label="日志内容" min-width="260" />
        <el-table-column prop="createdAt" label="时间" width="180" />
      </el-table>
    </el-dialog>

    <ScheduleAdjustDialog v-model="adjustDialogVisible" :context="adjustContext" @success="handleAdjustSuccess" />
    <LocalReplanDialog v-model="localReplanVisible" :plan-id="planId" @success="handleLocalReplanSuccess" />
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.summary-note {
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
