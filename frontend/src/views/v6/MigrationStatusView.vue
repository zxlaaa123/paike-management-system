<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMigrationStatus } from '../../api/migrationStatus'
import type { MigrationStatusOverview, MigrationScriptStatus } from '../../api/migrationStatus'

const loading = ref(false)
const overview = ref<MigrationStatusOverview | null>(null)

const statusMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  CONFIGURED: { label: '已配置', type: 'success' },
  MISSING: { label: '缺失', type: 'danger' },
  UNCONFIGURED: { label: '未配置', type: 'warning' },
}

const riskMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  LOW: { label: '低', type: 'success' },
  MEDIUM: { label: '中', type: 'warning' },
  HIGH: { label: '高', type: 'danger' },
  UNKNOWN: { label: '未知', type: 'info' },
}

async function fetchData() {
  loading.value = true
  try {
    overview.value = await getMigrationStatus()
  } catch (error) {
    console.error(error)
    ElMessage.error('加载数据库迁移状态失败')
  } finally {
    loading.value = false
  }
}

function statusMeta(status: string) {
  return statusMap[status] || { label: status, type: 'info' as const }
}

function riskMeta(riskLevel: string) {
  return riskMap[riskLevel] || { label: riskLevel, type: 'info' as const }
}

function configuredOrder(row: MigrationScriptStatus) {
  return row.configuredOrder == null ? '-' : row.configuredOrder
}

onMounted(fetchData)
</script>

<template>
  <div class="migration-status-view">
    <div class="page-header">
      <div>
        <h2>数据库迁移状态中心</h2>
        <p>当前迁移工具：{{ overview?.migrationTool || '-' }}</p>
      </div>
      <el-button type="primary" :loading="loading" @click="fetchData">刷新</el-button>
    </div>

    <div class="summary-grid">
      <el-card>
        <div class="summary-label">脚本总数</div>
        <div class="summary-value">{{ overview?.totalScriptCount ?? '-' }}</div>
      </el-card>
      <el-card>
        <div class="summary-label">已配置</div>
        <div class="summary-value">{{ overview?.configuredScriptCount ?? '-' }}</div>
      </el-card>
      <el-card>
        <div class="summary-label">缺失</div>
        <div class="summary-value danger">{{ overview?.missingScriptCount ?? '-' }}</div>
      </el-card>
      <el-card>
        <div class="summary-label">未配置</div>
        <div class="summary-value warning">{{ overview?.unconfiguredScriptCount ?? '-' }}</div>
      </el-card>
    </div>

    <el-card class="table-card">
      <div class="table-title">SQL 初始化脚本</div>
      <el-table v-loading="loading" :data="overview?.scripts || []" stripe>
        <el-table-column label="顺序" width="80">
          <template #default="{ row }">{{ configuredOrder(row) }}</template>
        </el-table-column>
        <el-table-column prop="scriptName" label="脚本" min-width="220" />
        <el-table-column prop="resourcePath" label="资源路径" min-width="240" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险" width="90">
          <template #default="{ row }">
            <el-tag :type="riskMeta(row.riskLevel).type">{{ riskMeta(row.riskLevel).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="idempotentHint" label="幂等/风险说明" min-width="300" />
      </el-table>
    </el-card>

    <el-card class="table-card">
      <div class="table-title">Java 初始化器</div>
      <el-table v-loading="loading" :data="overview?.initializers || []" stripe>
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="type" label="类型" width="150" />
        <el-table-column prop="className" label="类名" min-width="280" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag type="success">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="280" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.migration-status-view {
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0 0 6px;
}

.page-header p {
  margin: 0;
  color: #666;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-label {
  color: #8c8c8c;
  font-size: 13px;
}

.summary-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 600;
}

.summary-value.danger {
  color: #cf1322;
}

.summary-value.warning {
  color: #d48806;
}

.table-card {
  margin-bottom: 16px;
}

.table-title {
  margin-bottom: 12px;
  font-weight: 600;
}
</style>
