<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRuleWeights,
  initDefaultRules,
  updateRuleWeight,
  batchUpdateRuleWeights,
  type ScheduleRuleWeight,
} from '../../api/scheduleRuleWeight'
import { getAllSemesters, getCurrentSemester, type Semester } from '../../api/semester'

const loading = ref(false)
const saving = ref(false)
const tableData = ref<ScheduleRuleWeight[]>([])

const searchForm = reactive({
  semesterId: undefined as number | undefined,
  strategyType: 'COMPREHENSIVE',
  ruleType: '',
})

const semesterList = ref<Semester[]>([])
const currentSemester = ref<Semester | null>(null)

const hasCurrentSemester = computed(() => currentSemester.value !== null)

const groupedData = computed(() => {
  const hard = tableData.value.filter(r => r.ruleType === 'HARD')
  const soft = tableData.value.filter(r => r.ruleType === 'SOFT')
  return { hard, soft }
})

async function fetchData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { ...searchForm }
    if (!params.semesterId && currentSemester.value) {
      params.semesterId = currentSemester.value.id
    }
    tableData.value = await getRuleWeights(params)
  } finally {
    loading.value = false
  }
}

async function fetchOptions() {
  const [semesters, current] = await Promise.all([
    getAllSemesters(),
    getCurrentSemester().catch(() => null),
  ])
  semesterList.value = semesters
  currentSemester.value = current
  if (current) {
    searchForm.semesterId = current.id
  }
}

function handleSearch() {
  fetchData()
}

async function handleInitDefault() {
  if (!currentSemester.value) {
    ElMessage.warning('当前未设置学期')
    return
  }
  await ElMessageBox.confirm(
    `确定初始化「${searchForm.strategyType}」策略的默认规则权重吗？如果已有规则则不会覆盖。`,
    '提示',
    { type: 'warning' }
  )
  await initDefaultRules({ semesterId: currentSemester.value.id, strategyType: searchForm.strategyType })
  ElMessage.success('初始化成功')
  fetchData()
}

async function handleSave(row: ScheduleRuleWeight) {
  saving.value = true
  try {
    await updateRuleWeight(row.id, {
      weight: row.weight,
      enabled: row.enabled,
    })
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

async function handleBatchSave() {
  saving.value = true
  try {
    await batchUpdateRuleWeights(tableData.value)
    ElMessage.success('批量保存成功')
  } finally {
    saving.value = false
  }
}

function strategyText(type: string) {
  const map: Record<string, string> = {
    TEACHER_PRIORITY: '教师优先',
    CLASS_BALANCE: '班级均衡',
    CLASSROOM_UTILIZATION: '教室利用率',
    COMPREHENSIVE: '综合最优',
    CUSTOM: '自定义',
  }
  return map[type] || type
}

onMounted(() => {
  fetchOptions().then(fetchData)
})
</script>

<template>
  <div class="page-container">
    <el-alert
      v-if="!hasCurrentSemester"
      title="当前未设置学期，部分功能无法使用。请先在「学期管理」中创建并设置当前学期。"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="学期">
          <el-select v-model="searchForm.semesterId" placeholder="选择学期" clearable style="width: 220px">
            <el-option v-for="s in semesterList" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略类型">
          <el-select v-model="searchForm.strategyType" placeholder="选择策略">
            <el-option label="综合最优" value="COMPREHENSIVE" />
            <el-option label="教师优先" value="TEACHER_PRIORITY" />
            <el-option label="班级均衡" value="CLASS_BALANCE" />
            <el-option label="教室利用率" value="CLASSROOM_UTILIZATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="searchForm.ruleType" placeholder="全部" clearable>
            <el-option label="硬约束" value="HARD" />
            <el-option label="软约束" value="SOFT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button type="success" @click="handleInitDefault">初始化默认规则</el-button>
          <el-button type="warning" @click="handleBatchSave" :loading="saving">批量保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 硬约束 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>硬约束规则（必须满足）</span>
        </div>
      </template>
      <el-table :data="groupedData.hard" v-loading="loading" stripe size="small">
        <el-table-column prop="ruleName" label="规则名称" width="150" />
        <el-table-column prop="ruleCode" label="规则编码" width="180" />
        <el-table-column label="权重" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.weight" :min="0" :max="200" :step="5" size="small" style="width: 100px" />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" />
          </el-template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="200" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleSave(row)" :loading="saving">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 软约束 -->
    <el-card class="table-card" shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>软约束规则（尽量满足）</span>
        </div>
      </template>
      <el-table :data="groupedData.soft" v-loading="loading" stripe size="small">
        <el-table-column prop="ruleName" label="规则名称" width="150" />
        <el-table-column prop="ruleCode" label="规则编码" width="180" />
        <el-table-column label="权重" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.weight" :min="0" :max="100" :step="5" size="small" style="width: 100px" />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="200" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleSave(row)" :loading="saving">保存</el-button>
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
.search-card {
  padding: 4px 0;
}
.table-card {
  padding: 0;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
