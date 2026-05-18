<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getSchedulePlanById, getSchedulePlanItems, type SchedulePlan } from '../../api/schedulePlan'
import { getScheduleLockList, type ScheduleLockList } from '../../api/v4ScheduleLockApi'
import { createLocalReplanPlan, type ScheduleReplanResult } from '../../api/v4ScheduleReplanApi'

const props = defineProps<{
  modelValue: boolean
  planId: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: [result: ScheduleReplanResult]
}>()

const loading = ref(false)
const submitting = ref(false)
const plan = ref<SchedulePlan | null>(null)
const lockData = ref<ScheduleLockList | null>(null)
const itemCount = ref(0)

const form = reactive({
  newPlanName: '',
  keepLocked: true,
  strategyCode: 'LOCAL_REPLAN',
})

const lockedCount = computed(() => lockData.value?.lockedCount ?? 0)
const replanableCount = computed(() => Math.max(0, itemCount.value - lockedCount.value))
const canSubmit = computed(() => !!form.newPlanName.trim())

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      void fetchData()
    }
  },
)

async function fetchData() {
  loading.value = true
  try {
    const [planData, itemsData, lockList] = await Promise.all([
      getSchedulePlanById(props.planId),
      getSchedulePlanItems(props.planId),
      getScheduleLockList(props.planId),
    ])
    plan.value = planData
    itemCount.value = itemsData.length
    lockData.value = lockList
    form.newPlanName = buildDefaultPlanName(planData.name)
    form.keepLocked = true
    form.strategyCode = 'LOCAL_REPLAN'
  } catch (error: any) {
    ElMessage.error(error?.message || '加载局部重排数据失败')
    handleClose()
  } finally {
    loading.value = false
  }
}

function buildDefaultPlanName(sourcePlanName: string) {
  return `${sourcePlanName}-局部重排版`
}

function handleClose() {
  emit('update:modelValue', false)
}

async function handleSubmit() {
  if (!canSubmit.value) {
    ElMessage.warning('请填写新方案名称')
    return
  }
  submitting.value = true
  try {
    const result = await createLocalReplanPlan(props.planId, {
      newPlanName: form.newPlanName.trim(),
      keepLocked: form.keepLocked,
      strategyCode: form.strategyCode,
      forceGenerate: false,
    })
    ElMessage.success('新方案已生成，需手动应用')
    emit('success', result)
    handleClose()
  } catch (error: any) {
    ElMessage.error(error?.message || '局部重排生成失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="局部重排生成新方案"
    width="720px"
    destroy-on-close
    @close="handleClose"
  >
    <div v-loading="loading" class="replan-dialog">
      <el-alert
        type="warning"
        show-icon
        :closable="false"
        title="局部重排会基于当前方案生成一个新方案，不会覆盖原方案，也不会直接修改正式课表。新方案生成后，仍需手动应用。"
      />

      <el-card v-if="plan" shadow="never" class="summary-card">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="来源方案">{{ plan.name }}</el-descriptions-item>
          <el-descriptions-item label="方案状态">{{ plan.status }}</el-descriptions-item>
          <el-descriptions-item label="已锁定课程数量">{{ lockedCount }}</el-descriptions-item>
          <el-descriptions-item label="可重排课程数量">{{ replanableCount }}</el-descriptions-item>
          <el-descriptions-item label="已排课程数量">{{ plan.scheduledCount }}</el-descriptions-item>
          <el-descriptions-item label="未排任务数量">{{ plan.unscheduledCount }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-form label-width="112px" class="replan-form">
        <el-form-item label="新方案名称" required>
          <el-input v-model="form.newPlanName" maxlength="80" show-word-limit placeholder="请输入新方案名称" />
        </el-form-item>
        <el-form-item label="策略标识">
          <el-select v-model="form.strategyCode" style="width: 100%">
            <el-option label="LOCAL_REPLAN（推荐）" value="LOCAL_REPLAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="保留锁定课程">
          <el-switch v-model="form.keepLocked" />
          <div class="field-tip">开启后，当前已锁定课程会同步到新方案，后续调整时仍可继续保护这些课程。</div>
        </el-form-item>
      </el-form>

      <div class="dialog-note">
        当前阶段实现的是文档允许的最小可用版：会生成独立新方案并保留锁定课程，不会覆盖原方案，也不会绕过 V3 apply。
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">
          生成新方案
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.replan-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-card {
  border-radius: 16px;
}

.replan-form {
  margin-top: 4px;
}

.field-tip {
  color: #69717d;
  font-size: 13px;
  line-height: 1.6;
}

.dialog-note {
  color: #5b6472;
  font-size: 13px;
  line-height: 1.7;
  background: #f6f8fb;
  border-radius: 14px;
  padding: 14px 16px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
