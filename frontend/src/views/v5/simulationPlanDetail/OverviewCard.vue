<template>
  <el-card v-if="props.detail && props.compare" shadow="never" class="main-card">
    <template #header>
      <div class="header-row">
        <div>
          <div class="title">{{ props.detail.plan.name }}</div>
          <div class="sub">方案ID：{{ props.detail.plan.id }} · 任务ID：{{ props.taskId }} · 学期：{{ props.detail.plan.semesterId }}</div>
        </div>
        <div class="header-actions">
          <el-tag :type="statusType(props.detail.plan.status)">{{ props.detail.plan.status }}</el-tag>
          <el-tag :type="props.compare.recommended ? 'success' : 'danger'">
            {{ props.compare.recommended ? '推荐应用' : '不推荐应用' }}
          </el-tag>
        </div>
      </div>
    </template>

    <el-alert
      :type="props.compare.recommended ? 'success' : 'error'"
      :title="props.compare.recommendationMessage"
      :closable="false"
      show-icon
    />

    <el-row :gutter="12" class="block">
      <el-col :span="6"><el-statistic title="试算评分" :value="props.detail.plan.totalScore ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="风险数" :value="props.detail.risks.riskCount" /></el-col>
      <el-col :span="6"><el-statistic title="冲突数" :value="props.detail.plan.conflictCount" /></el-col>
      <el-col :span="6"><el-statistic title="课程变动数" :value="props.compare.courseChangeCount" /></el-col>
    </el-row>

    <el-row v-if="props.detail.localReplanSummary" :gutter="12" class="block">
      <el-col :span="6"><el-statistic title="局部范围课程" :value="props.detail.localReplanSummary.scopeItemCount" /></el-col>
      <el-col :span="6"><el-statistic title="锁定保留" :value="props.detail.localReplanSummary.lockedCount" /></el-col>
      <el-col :span="6"><el-statistic title="实际移动" :value="props.detail.localReplanSummary.movedCount" /></el-col>
      <el-col :span="6"><el-statistic title="重排失败" :value="props.detail.localReplanSummary.failedCount" /></el-col>
    </el-row>

    <el-descriptions :column="3" border class="block">
      <el-descriptions-item label="对比基线">{{ props.compare.baselinePlanName }}（{{ props.compare.baselinePlanId ?? props.compare.baselineSourceScheduleId ?? '正式课表' }}）</el-descriptions-item>
      <el-descriptions-item label="试算方案">{{ props.compare.simulationPlanName }}（{{ props.compare.simulationPlanId }}）</el-descriptions-item>
      <el-descriptions-item label="对比摘要">{{ props.compare.summary }}</el-descriptions-item>
      <el-descriptions-item label="锁定课程保护">
        <el-tag :type="props.compare.lockedCoursesPreserved ? 'success' : 'danger'">
          {{ props.compare.lockedCoursesPreserved ? '通过' : '未通过' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="新增硬冲突">
        <el-tag :type="props.compare.hasNewHardConflicts ? 'danger' : 'success'">
          {{ props.compare.hasNewHardConflicts ? `${props.compare.newHardConflictCount} 个` : '无' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="变动锁定课程">
        {{ props.compare.changedLockedCourseNames.length ? props.compare.changedLockedCourseNames.join('，') : '无' }}
      </el-descriptions-item>
    </el-descriptions>

    <div class="actions" v-if="props.canOperate">
      <el-button type="primary" :loading="props.checking" @click="emit('check')">一致性校验</el-button>
      <el-button type="warning" :loading="props.acting" @click="emit('confirm')" v-if="props.detail.plan.status === 'SIMULATION'">确认试算方案</el-button>
      <el-button type="success" :loading="props.acting" :disabled="!props.canApply" @click="emit('apply')">应用试算方案</el-button>
      <el-button type="danger" :loading="props.acting" @click="emit('discard')">放弃试算方案</el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { V5SimulationCompare, V5SimulationPlanDetail } from '../../../api/v5SimulationApi'
import { statusType } from './formatters'

const props = defineProps<{
  detail: V5SimulationPlanDetail | null
  compare: V5SimulationCompare | null
  taskId: number
  canOperate: boolean
  canApply: boolean
  hasBlockingConflicts: boolean
  acting: boolean
  checking: boolean
}>()

const emit = defineEmits<{
  check: []
  confirm: []
  apply: []
  discard: []
}>()
</script>
