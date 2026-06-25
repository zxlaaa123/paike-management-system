<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAllClassrooms, type Classroom } from '../../api/classroom'
import { getAllTimeSlots, type TimeSlot } from '../../api/timeSlot'
import {
  applyScheduleAdjustment,
  checkScheduleAdjustment,
  type ScheduleAdjustmentApplyResult,
  type ScheduleAdjustmentCheckResult,
  type ScheduleAdjustmentTargetType,
} from '../../api/v4ScheduleAdjustmentApi'
import { extractMessage, isCancel } from '../../utils/errors'

interface AdjustDialogContext {
  targetType: ScheduleAdjustmentTargetType
  planId?: number | null
  planItemId?: number | null
  scheduleId?: number | null
  courseName?: string | null
  teacherName?: string | null
  className?: string | null
  currentRoomId?: number | null
  currentRoomName?: string | null
  currentWeekDay?: number | null
  currentPeriodStart?: number | null
  currentPeriodEnd?: number | null
  currentTimeLabel?: string | null
  version?: number | null
}

const props = defineProps<{
  modelValue: boolean
  context: AdjustDialogContext | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: [result: ScheduleAdjustmentApplyResult]
}>()

const classroomOptions = ref<Classroom[]>([])
const timeSlotOptions = ref<TimeSlot[]>([])
const loadingOptions = ref(false)
const checking = ref(false)
const saving = ref(false)
const checkResult = ref<ScheduleAdjustmentCheckResult | null>(null)

const form = reactive({
  newRoomId: 0,
  timeSlotId: 0,
  adjustReason: '',
})

const selectedTimeSlot = computed(() => timeSlotOptions.value.find((slot) => slot.id === form.timeSlotId) ?? null)

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const timeSlotsByDay = computed(() => {
  const grouped: Record<number, TimeSlot[]> = { 1: [], 2: [], 3: [], 4: [], 5: [], 6: [], 7: [] }
  for (const slot of timeSlotOptions.value) {
    if (!grouped[slot.dayOfWeek]) grouped[slot.dayOfWeek] = []
    grouped[slot.dayOfWeek].push(slot)
  }
  return grouped
})

const dayNames: Record<number, string> = {
  1: '周一',
  2: '周二',
  3: '周三',
  4: '周四',
  5: '周五',
  6: '周六',
  7: '周日',
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible || !props.context) return
    await ensureOptions()
    resetForm()
  },
)

function roomTypeText(type: string) {
  const map: Record<string, string> = {
    NORMAL: '普通教室',
    MULTIMEDIA: '多媒体教室',
    LAB: '实验室',
    COMPUTER: '机房',
  }
  return map[type] || type
}

async function ensureOptions() {
  if (classroomOptions.value.length > 0 && timeSlotOptions.value.length > 0) {
    return
  }
  loadingOptions.value = true
  try {
    const [rooms, slots] = await Promise.all([getAllClassrooms(), getAllTimeSlots()])
    classroomOptions.value = rooms
    timeSlotOptions.value = slots
  } finally {
    loadingOptions.value = false
  }
}

function findSlotId(weekDay?: number | null, periodStart?: number | null) {
  if (!weekDay || !periodStart) return 0
  const periodNo = Math.ceil(periodStart / 2)
  return timeSlotOptions.value.find((slot) => slot.dayOfWeek === weekDay && slot.periodNo === periodNo)?.id ?? 0
}

function resetForm() {
  form.newRoomId = Number(props.context?.currentRoomId || 0)
  form.timeSlotId = findSlotId(props.context?.currentWeekDay, props.context?.currentPeriodStart)
  form.adjustReason = ''
  checkResult.value = null
}

function buildPayload(forceAdjust = false) {
  const slot = selectedTimeSlot.value
  if (!props.context) {
    throw new Error('调整上下文不存在')
  }
  if (!slot) {
    throw new Error('请选择新的时间段')
  }
  if (!form.newRoomId) {
    throw new Error('请选择新的教室')
  }
  return {
    targetType: props.context.targetType,
    planId: props.context.planId ?? undefined,
    planItemId: props.context.planItemId ?? undefined,
    scheduleId: props.context.scheduleId ?? undefined,
    newWeekDay: slot.dayOfWeek,
    newPeriodStart: slot.periodNo * 2 - 1,
    newPeriodEnd: slot.periodNo * 2,
    newRoomId: form.newRoomId,
    adjustReason: form.adjustReason,
    forceAdjust,
    version: props.context.version ?? undefined,
  }
}

async function handleCheck() {
  try {
    checking.value = true
    checkResult.value = await checkScheduleAdjustment(buildPayload(false))
    if (checkResult.value.hasConflict) {
      ElMessage.warning(`检测到 ${checkResult.value.blockingIssueCount} 项冲突`)
    } else {
      ElMessage.success('未检测到阻塞冲突，可以保存')
    }
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '检测失败'))
  } finally {
    checking.value = false
  }
}

async function handleSubmit(forceAdjust = false) {
  if (!form.adjustReason.trim()) {
    ElMessage.warning('请填写调整原因')
    return
  }
  saving.value = true
  try {
    const result = await applyScheduleAdjustment(buildPayload(forceAdjust))
    checkResult.value = result.checkResult
    if (result.requiresConfirmation && !forceAdjust) {
      await ElMessageBox.confirm(
        `检测到 ${result.checkResult.blockingIssueCount} 项冲突，是否继续强制保存？`,
        '强制保存确认',
        {
          type: 'warning',
          confirmButtonText: '强制保存',
          cancelButtonText: '取消',
        },
      )
      await handleSubmit(true)
      return
    }
    ElMessage.success(result.message || '调整成功')
    emit('success', result)
    emit('update:modelValue', false)
  } catch (error: unknown) {
    if (isCancel(error)) return
    ElMessage.error(extractMessage(error, '保存失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="context?.targetType === 'SCHEDULE' ? '调整正式课表' : '调整方案明细'"
    width="760px"
    destroy-on-close
  >
    <div v-loading="loadingOptions">
      <el-descriptions v-if="context" :column="2" border class="summary-panel">
        <el-descriptions-item label="课程">{{ context.courseName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="教师">{{ context.teacherName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ context.className || '—' }}</el-descriptions-item>
        <el-descriptions-item label="当前教室">{{ context.currentRoomName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="当前时间" :span="2">
          {{ context.currentTimeLabel || '—' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="context?.targetType === 'SCHEDULE' && context.planId"
        title="当前正式课表与来源方案有关联，保存后会同步到已应用方案明细。"
        type="warning"
        :closable="false"
        class="tip-alert"
      />

      <el-form label-width="110px">
        <el-form-item label="新教室">
          <el-select v-model="form.newRoomId" filterable placeholder="请选择教室" style="width: 100%">
            <el-option
              v-for="room in classroomOptions"
              :key="room.id"
              :label="`${room.roomName}（${room.building ? room.building + ' · ' : ''}容量${room.capacity} · ${roomTypeText(room.roomType)}）`"
              :value="room.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="新时间段">
          <el-select v-model="form.timeSlotId" filterable placeholder="请选择时间段" style="width: 100%">
            <el-option-group v-for="(slots, day) in timeSlotsByDay" :key="day" :label="dayNames[Number(day)] || `周${day}`">
              <el-option v-for="slot in slots" :key="slot.id" :label="slot.timeLabel" :value="slot.id" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="form.adjustReason" type="textarea" :rows="3" placeholder="请记录本次调整原因，保存时会写入调整日志" />
        </el-form-item>
      </el-form>

      <el-card v-if="checkResult" shadow="never" class="check-panel">
        <template #header>
          <div class="check-header">
            <span>冲突检测结果</span>
            <el-tag :type="checkResult.hasConflict ? 'danger' : 'success'">
              {{ checkResult.hasConflict ? `发现 ${checkResult.blockingIssueCount} 项冲突` : '可安全保存' }}
            </el-tag>
          </div>
        </template>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="调整后教室">{{ checkResult.newRoomName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="调整后时间">{{ checkResult.newTimeLabel || '—' }}</el-descriptions-item>
        </el-descriptions>
        <ul class="issue-list">
          <li v-for="issue in checkResult.issues" :key="`${issue.issueType}-${issue.message}`">
            <el-tag size="small" :type="issue.blocking ? 'danger' : 'info'">{{ issue.issueName }}</el-tag>
            <span>{{ issue.message }}</span>
          </li>
        </ul>
      </el-card>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button :loading="checking" @click="handleCheck">检测冲突</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit(false)">保存调整</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.summary-panel {
  margin-bottom: 16px;
}

.tip-alert {
  margin-bottom: 16px;
}

.check-panel {
  margin-top: 16px;
}

.check-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.issue-list {
  margin: 16px 0 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.issue-list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  line-height: 1.5;
}
</style>
