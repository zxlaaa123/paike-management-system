<template>
  <el-card v-if="props.detail" shadow="never" class="main-card">
    <template #header>
      <div class="header-row">
        <div class="title">AI 修复解释</div>
        <div class="header-actions">
          <el-tag v-if="props.explanation" :type="props.explanation.remote ? 'success' : 'info'">
            {{ props.explanation.remote ? '远程 AI' : '本地模板' }}
          </el-tag>
          <el-button size="small" type="primary" :loading="props.explaining" @click="emit('generate')">
            生成 AI 修复解释
          </el-button>
          <el-button size="small" :disabled="!props.explanation" @click="emit('copy')">复制全文</el-button>
        </div>
      </div>
    </template>
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="AI 建议仅供参考，最终以系统校验结果为准。AI 不会自动应用方案或修改数据。"
    />
    <el-empty v-if="!props.explanation" description="尚未生成 AI 修复解释，请点击上方按钮触发" class="block" />
    <template v-else>
      <el-descriptions :column="3" border class="block">
        <el-descriptions-item label="任务 ID">{{ props.explanation.taskId }}</el-descriptions-item>
        <el-descriptions-item label="试算方案 ID">{{ props.explanation.planId }}</el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ props.explanation.generatedAt }}</el-descriptions-item>
        <el-descriptions-item label="是否建议应用" :span="3">
          <el-tag :type="props.explanation.recommendApply ? 'success' : 'danger'">
            {{ props.explanation.recommendApply ? '建议应用' : '不建议应用' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div class="section-title">总体评价</div>
      <el-alert type="info" :closable="false" :title="props.explanation.overallEvaluation" />

      <div class="section-title">推荐理由</div>
      <el-alert type="success" :closable="false" :title="props.explanation.recommendationReason" />

      <el-row :gutter="16" class="block">
        <el-col :span="12">
          <div class="section-title">改善的指标</div>
          <el-empty v-if="!props.explanation.improvedMetrics?.length" description="无显著改善指标" />
          <ul v-else class="ai-list">
            <li v-for="(item, idx) in props.explanation.improvedMetrics" :key="idx">{{ item }}</li>
          </ul>
        </el-col>
        <el-col :span="12">
          <div class="section-title">仍存在的问题</div>
          <el-empty v-if="!props.explanation.remainingIssues?.length" description="未发现遗留问题" />
          <ul v-else class="ai-list">
            <li v-for="(item, idx) in props.explanation.remainingIssues" :key="idx">{{ item }}</li>
          </ul>
        </el-col>
      </el-row>

      <div class="section-title">应用建议</div>
      <el-alert :type="props.explanation.recommendApply ? 'success' : 'warning'" :closable="false" :title="props.explanation.applyAdvice" />

      <div class="section-title">答辩展示摘要</div>
      <el-alert type="info" :closable="false" :title="props.explanation.defenseSummary" />

      <div class="sub block">{{ props.explanation.disclaimer }}</div>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import type { V5RepairExplanation, V5SimulationPlanDetail } from '../../../api/v5SimulationApi'

const props = defineProps<{
  detail: V5SimulationPlanDetail | null
  explanation: V5RepairExplanation | null
  explaining: boolean
}>()

const emit = defineEmits<{
  generate: []
  copy: []
}>()
</script>
