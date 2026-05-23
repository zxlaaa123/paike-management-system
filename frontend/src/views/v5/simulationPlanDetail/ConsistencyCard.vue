<template>
  <el-card v-if="props.detail" shadow="never" class="main-card">
    <template #header>
      <div class="header-row">
        <div class="title">一致性校验</div>
        <div class="header-actions">
          <el-tag v-if="props.consistencyReport" :type="consistencyStatusTag(props.consistencyReport.status)">
            {{ props.consistencyReport.status }}
          </el-tag>
          <el-button size="small" type="primary" :loading="props.checking" @click="emit('check')">立即校验</el-button>
        </div>
      </div>
    </template>
    <el-empty v-if="!props.consistencyReport" description="尚未执行一致性校验，请点击上方按钮触发" />
    <template v-else>
      <el-alert
        :type="consistencyAlertType(props.consistencyReport.status)"
        :title="props.consistencyReport.summary"
        :description="props.consistencyReport.recommendation"
        :closable="false"
        show-icon
      />
      <el-row :gutter="12" class="block">
        <el-col :span="6"><el-statistic title="阻塞问题" :value="props.consistencyReport.blockingIssueCount" /></el-col>
        <el-col :span="6"><el-statistic title="警告问题" :value="props.consistencyReport.warningIssueCount" /></el-col>
        <el-col :span="6"><el-statistic title="提示问题" :value="props.consistencyReport.infoIssueCount" /></el-col>
        <el-col :span="6"><el-statistic title="问题总数" :value="props.consistencyReport.issues?.length ?? 0" /></el-col>
      </el-row>
      <div class="sub" v-if="props.consistencyReport.checkedAt">检查时间：{{ props.consistencyReport.checkedAt }}</div>
      <el-empty v-if="!props.consistencyReport.issues?.length" description="未发现问题" class="block" />
      <el-table v-else :data="props.consistencyReport.issues" border stripe class="block">
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
</template>

<script setup lang="ts">
import type { V5ConsistencyCheckReport, V5SimulationPlanDetail } from '../../../api/v5SimulationApi'
import { consistencyAlertType, consistencyStatusTag, severityTag } from './formatters'

const props = defineProps<{
  detail: V5SimulationPlanDetail | null
  consistencyReport: V5ConsistencyCheckReport | null
  checking: boolean
}>()

const emit = defineEmits<{
  check: []
}>()
</script>
