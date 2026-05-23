<template>
  <el-card shadow="never" class="main-card">
    <template #header><div class="title">优化前后对比</div></template>
    <el-table :data="metricRows" border stripe>
      <el-table-column prop="label" label="指标" min-width="160" />
      <el-table-column label="优化前" min-width="140">
        <template #default="{ row }">{{ row.before }}</template>
      </el-table-column>
      <el-table-column label="优化后" min-width="140">
        <template #default="{ row }">{{ row.after }}</template>
      </el-table-column>
      <el-table-column label="变化值" min-width="140">
        <template #default="{ row }">{{ row.formatter(row.delta) }}</template>
      </el-table-column>
      <el-table-column label="趋势" width="120">
        <template #default="{ row }">
          <el-tag :type="trendType(Number(row.delta), row.lowerBetter)">{{ trendText(Number(row.delta), row.lowerBetter) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { trendText, trendType, type MetricRow } from './formatters'

defineProps<{
  metricRows: MetricRow[]
}>()
</script>
