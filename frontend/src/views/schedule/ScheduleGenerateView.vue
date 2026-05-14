<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { generateMultipleSchedulePlans, generateSchedulePlan, type ScheduleGenerateResult } from '../../api/scheduleGenerate'
import { getAllSemesters, getCurrentSemester, type Semester } from '../../api/semester'

const router = useRouter()
const loading = ref(false)
const generating = ref(false)
const semesterList = ref<Semester[]>([])
const currentSemester = ref<Semester | null>(null)
const latestResults = ref<ScheduleGenerateResult[]>([])

const singleForm = reactive({
  semesterId: undefined as number | undefined,
  strategyType: 'COMPREHENSIVE',
  planName: '',
  overwriteDraft: false,
})

const multipleForm = reactive({
  semesterId: undefined as number | undefined,
  strategyTypes: ['TEACHER_PRIORITY', 'CLASS_BALANCE', 'CLASSROOM_UTILIZATION', 'COMPREHENSIVE'] as string[],
  overwriteDraft: false,
})

const strategyOptions = [
  { label: '教师优先', value: 'TEACHER_PRIORITY' },
  { label: '班级均衡', value: 'CLASS_BALANCE' },
  { label: '教室利用率', value: 'CLASSROOM_UTILIZATION' },
  { label: '综合最优', value: 'COMPREHENSIVE' },
]

const currentSemesterName = computed(() => {
  const semesterId = singleForm.semesterId ?? multipleForm.semesterId
  const semester = semesterList.value.find((item) => item.id === semesterId)
  return semester?.name || currentSemester.value?.name || '未设置'
})

async function fetchSemesters() {
  loading.value = true
  try {
    const [allSemesters, current] = await Promise.all([
      getAllSemesters(),
      getCurrentSemester().catch(() => null),
    ])
    semesterList.value = allSemesters
    currentSemester.value = current
    if (current) {
      singleForm.semesterId = current.id
      multipleForm.semesterId = current.id
    }
  } finally {
    loading.value = false
  }
}

async function handleGenerateSingle() {
  generating.value = true
  try {
    const result = await generateSchedulePlan({
      semesterId: singleForm.semesterId,
      strategyType: singleForm.strategyType,
      planName: singleForm.planName || undefined,
      overwriteDraft: singleForm.overwriteDraft,
    })
    latestResults.value = [result]
    ElMessage.success('单方案生成成功')
    router.push(`/v3/schedule-plans/${result.planId}`)
  } catch (error) {
    console.error(error)
  } finally {
    generating.value = false
  }
}

async function handleGenerateMultiple() {
  if (multipleForm.strategyTypes.length === 0) {
    ElMessage.warning('请至少选择一个策略')
    return
  }
  generating.value = true
  try {
    const results = await generateMultipleSchedulePlans({
      semesterId: multipleForm.semesterId,
      strategyTypes: multipleForm.strategyTypes,
      overwriteDraft: multipleForm.overwriteDraft,
    })
    latestResults.value = results
    ElMessage.success(`多方案生成成功，共生成 ${results.length} 个方案`)
    router.push('/v3/schedule-plans')
  } catch (error) {
    console.error(error)
  } finally {
    generating.value = false
  }
}

function strategyText(value: string) {
  return strategyOptions.find((item) => item.value === value)?.label || value
}

onMounted(fetchSemesters)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="阶段 5 多方案自动排课"
      :description="`当前将只生成排课方案，不会覆盖正式课表。当前学期：${currentSemesterName}`"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>生成单个方案</span>
          <el-button type="primary" :loading="generating" @click="handleGenerateSingle">生成单方案</el-button>
        </div>
      </template>
      <el-form label-width="110px">
        <el-form-item label="目标学期">
          <el-select v-model="singleForm.semesterId" style="width: 320px">
            <el-option v-for="semester in semesterList" :key="semester.id" :label="semester.name" :value="semester.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略类型">
          <el-select v-model="singleForm.strategyType" style="width: 320px">
            <el-option v-for="item in strategyOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="方案名称">
          <el-input v-model="singleForm.planName" placeholder="不填则自动生成方案名称" style="width: 420px" />
        </el-form-item>
        <el-form-item label="覆盖草稿">
          <el-switch v-model="singleForm.overwriteDraft" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>一次生成多个方案</span>
          <el-button type="success" :loading="generating" @click="handleGenerateMultiple">生成多方案</el-button>
        </div>
      </template>
      <el-form label-width="110px">
        <el-form-item label="目标学期">
          <el-select v-model="multipleForm.semesterId" style="width: 320px">
            <el-option v-for="semester in semesterList" :key="semester.id" :label="semester.name" :value="semester.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略集合">
          <el-checkbox-group v-model="multipleForm.strategyTypes">
            <el-checkbox v-for="item in strategyOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="覆盖草稿">
          <el-switch v-model="multipleForm.overwriteDraft" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="latestResults.length > 0" shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>最近生成结果</span>
          <el-button type="primary" link @click="router.push('/v3/schedule-plans')">查看全部方案</el-button>
        </div>
      </template>
      <el-table :data="latestResults" stripe>
        <el-table-column prop="planName" label="方案名称" min-width="220" />
        <el-table-column label="策略类型" width="120">
          <template #default="{ row }">{{ strategyText(row.strategyType) }}</template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="100" />
        <el-table-column prop="scheduledCount" label="已排数量" width="100" />
        <el-table-column prop="unscheduledCount" label="未排数量" width="100" />
        <el-table-column prop="conflictCount" label="冲突数量" width="100" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push(`/v3/schedule-plans/${row.planId}`)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
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
</style>
