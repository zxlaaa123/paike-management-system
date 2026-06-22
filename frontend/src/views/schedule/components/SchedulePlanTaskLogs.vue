<script setup lang="ts">
import { computed } from 'vue'
import type { ScheduleGenerateLog } from '../../../api/schedulePlan'
import { logLevelTagType, logTypeText } from '../../../utils/status'

const props = defineProps<{
  modelValue: boolean
  title: string
  loading?: boolean
  logs: ScheduleGenerateLog[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title" width="760px" destroy-on-close>
    <el-table :data="logs" stripe size="small" v-loading="loading">
      <el-table-column prop="stepNo" label="步骤" width="70" />
      <el-table-column prop="logLevel" label="级别" width="90">
        <template #default="{ row }"><el-tag :type="logLevelTagType(row.logLevel)" size="small">{{ row.logLevel }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="logType" label="类型" width="130">
        <template #default="{ row }">{{ logTypeText(row.logType) }}</template>
      </el-table-column>
      <el-table-column prop="message" label="日志内容" min-width="260" />
      <el-table-column prop="createdAt" label="时间" width="180" />
    </el-table>
  </el-dialog>
</template>
