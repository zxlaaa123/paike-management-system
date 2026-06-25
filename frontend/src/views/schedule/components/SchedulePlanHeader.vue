<script setup lang="ts">
import type { SchedulePlan } from '../../../api/schedulePlan'
import { schedulePlanStatusTagType as statusTagType, schedulePlanStatusText as statusText, strategyText } from '../../../utils/status'

const props = defineProps<{
  plan: SchedulePlan
  applying?: boolean
  creatingRepair?: boolean
}>()

const emit = defineEmits<{
  back: []
  apply: []
  rollback: []
  'v4-analysis': []
  'v4-locks': []
  replan: []
  'create-repair': []
}>()
</script>

<template>
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
            @click="emit('apply')"
          >应用方案</el-button>
          <el-button
            v-if="plan.status !== 'ABANDONED'"
            type="warning"
            size="small"
            :loading="applying"
            @click="emit('rollback')"
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
      <el-button type="warning" plain @click="emit('v4-analysis')">
        进入 V4 质量分析
      </el-button>
      <el-button type="info" plain @click="emit('v4-locks')">
        课程锁定管理
      </el-button>
      <el-button
        type="success"
        plain
        :disabled="plan.status === 'ABANDONED' || plan.status === 'FAILED'"
        @click="emit('replan')"
      >
        局部重排生成新方案
      </el-button>
      <el-button
        type="primary"
        plain
        :loading="creatingRepair"
        :disabled="plan.status === 'ABANDONED'"
        @click="emit('create-repair')"
      >
        创建修复任务
      </el-button>
    </div>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
