<template>
  <el-card v-if="props.compare" shadow="never" class="main-card">
    <template #header><div class="title">问题变化</div></template>
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="section-title">已解决问题</div>
        <el-empty v-if="!props.compare.resolvedRisks.length" description="没有已解决风险" />
        <el-table v-else :data="props.compare.resolvedRisks" border stripe>
          <el-table-column label="等级" width="100">
            <template #default="{ row }"><el-tag :type="riskLevelTag(row.level)">{{ row.level }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="riskTypeName" label="类型" min-width="120" />
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        </el-table>
      </el-col>
      <el-col :span="12">
        <div class="section-title">新增问题</div>
        <el-empty v-if="!props.compare.newRisks.length" description="没有新增风险" />
        <el-table v-else :data="props.compare.newRisks" border stripe>
          <el-table-column label="等级" width="100">
            <template #default="{ row }"><el-tag :type="riskLevelTag(row.level)">{{ row.level }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="riskTypeName" label="类型" min-width="120" />
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        </el-table>
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup lang="ts">
import type { V5SimulationCompare } from '../../../api/v5SimulationApi'
import { riskLevelTag } from './formatters'

const props = defineProps<{
  compare: V5SimulationCompare | null
}>()
</script>
