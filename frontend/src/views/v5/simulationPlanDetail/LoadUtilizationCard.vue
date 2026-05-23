<template>
  <el-card v-if="props.compare" shadow="never" class="main-card">
    <template #header><div class="title">负载与利用率变化</div></template>
    <div class="section-title">教师负载变化</div>
    <el-empty v-if="!props.compare.teacherLoadChanges.length" description="教师负载无变化" />
    <el-table v-else :data="props.compare.teacherLoadChanges" border stripe class="block">
      <el-table-column prop="entityName" label="教师" min-width="160" />
      <el-table-column prop="baselineLoad" label="优化前" width="120" />
      <el-table-column prop="simulationLoad" label="优化后" width="120" />
      <el-table-column label="变化" width="120">
        <template #default="{ row }">
          <el-tag :type="row.delta === 0 ? 'info' : 'warning'">{{ deltaText(row.delta) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="section-title">班级负载变化</div>
    <el-empty v-if="!props.compare.classLoadChanges.length" description="班级负载无变化" />
    <el-table v-else :data="props.compare.classLoadChanges" border stripe class="block">
      <el-table-column prop="entityName" label="班级" min-width="160" />
      <el-table-column prop="baselineLoad" label="优化前" width="120" />
      <el-table-column prop="simulationLoad" label="优化后" width="120" />
      <el-table-column label="变化" width="120">
        <template #default="{ row }">
          <el-tag :type="row.delta === 0 ? 'info' : 'warning'">{{ deltaText(row.delta) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="section-title">教室利用率变化</div>
    <el-empty v-if="!props.compare.roomUtilizationChanges.length" description="教室利用率无变化" />
    <el-table v-else :data="props.compare.roomUtilizationChanges" border stripe class="block">
      <el-table-column prop="classroomName" label="教室" min-width="160" />
      <el-table-column label="优化前" min-width="140">
        <template #default="{ row }">{{ row.baselineUsedPeriods }} / {{ formatPercent(row.baselineUtilizationRate) }}</template>
      </el-table-column>
      <el-table-column label="优化后" min-width="140">
        <template #default="{ row }">{{ row.simulationUsedPeriods }} / {{ formatPercent(row.simulationUtilizationRate) }}</template>
      </el-table-column>
      <el-table-column label="变化" min-width="140">
        <template #default="{ row }">
          <el-tag :type="row.utilizationDelta === 0 ? 'info' : 'warning'">{{ decimalDeltaText(row.utilizationDelta) }}%</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import type { V5SimulationCompare } from '../../../api/v5SimulationApi'
import { decimalDeltaText, deltaText, formatPercent } from './formatters'

const props = defineProps<{
  compare: V5SimulationCompare | null
}>()
</script>
