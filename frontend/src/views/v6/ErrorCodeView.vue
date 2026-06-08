<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getErrorCodeDetail, getErrorCodes } from '../../api/errorCode'
import type { ErrorCodeInfo } from '../../api/errorCode'

const loading = ref(false)
const detailLoading = ref(false)
const category = ref('')
const records = ref<ErrorCodeInfo[]>([])
const selected = ref<ErrorCodeInfo | null>(null)
const detailVisible = ref(false)

const categoryOptions = ['AUTH', 'VALIDATION', 'BUSINESS', 'CONFLICT', 'SYSTEM']

const categoryCounts = computed(() => {
  const result: Record<string, number> = {}
  for (const item of records.value) {
    result[item.category] = (result[item.category] || 0) + 1
  }
  return result
})

async function fetchData() {
  loading.value = true
  try {
    records.value = await getErrorCodes(category.value || undefined)
  } catch (error) {
    console.error(error)
    ElMessage.error('加载错误码失败')
  } finally {
    loading.value = false
  }
}

async function showDetail(row: ErrorCodeInfo) {
  detailLoading.value = true
  detailVisible.value = true
  try {
    selected.value = await getErrorCodeDetail(row.code)
  } catch (error) {
    console.error(error)
    selected.value = row
    ElMessage.error('加载错误码详情失败')
  } finally {
    detailLoading.value = false
  }
}

function resetFilters() {
  category.value = ''
  fetchData()
}

function categoryType(value: string) {
  if (value === 'AUTH') return 'danger'
  if (value === 'VALIDATION') return 'warning'
  if (value === 'SYSTEM') return 'info'
  if (value === 'CONFLICT') return 'warning'
  return 'success'
}

onMounted(fetchData)
</script>

<template>
  <div class="error-code-view">
    <div class="page-header">
      <div>
        <h2>错误码与提示中心</h2>
        <p>统一查看后端错误码、前端默认提示和处理建议</p>
      </div>
      <el-button type="primary" :loading="loading" @click="fetchData">刷新</el-button>
    </div>

    <div class="summary-grid">
      <el-card v-for="option in categoryOptions" :key="option">
        <div class="summary-label">{{ option }}</div>
        <div class="summary-value">{{ categoryCounts[option] || 0 }}</div>
      </el-card>
    </div>

    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="分类">
          <el-select v-model="category" clearable placeholder="全部" style="width: 180px">
            <el-option v-for="option in categoryOptions" :key="option" :label="option" :value="option" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">搜索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="table-title">错误码列表（共 {{ records.length }} 条）</div>
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="code" label="错误码" min-width="190" />
        <el-table-column label="分类" width="120">
          <template #default="{ row }">
            <el-tag :type="categoryType(row.category)">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="numericCode" label="业务码" width="100" />
        <el-table-column prop="httpStatus" label="HTTP" width="90" />
        <el-table-column prop="defaultMessage" label="默认消息" min-width="220" />
        <el-table-column prop="frontendPrompt" label="前端提示" min-width="220" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="错误码详情" width="620px">
      <div v-loading="detailLoading" class="detail-panel" v-if="selected">
        <div class="detail-code">{{ selected.code }}</div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="分类">{{ selected.category }}</el-descriptions-item>
          <el-descriptions-item label="业务码">{{ selected.numericCode }}</el-descriptions-item>
          <el-descriptions-item label="HTTP 状态">{{ selected.httpStatus }}</el-descriptions-item>
          <el-descriptions-item label="默认消息">{{ selected.defaultMessage }}</el-descriptions-item>
          <el-descriptions-item label="前端提示">{{ selected.frontendPrompt }}</el-descriptions-item>
          <el-descriptions-item label="处理建议">{{ selected.handlingSuggestion }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.error-code-view {
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
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
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

.filter-card {
  margin-bottom: 16px;
}

.table-title {
  margin-bottom: 12px;
  font-weight: 600;
}

.detail-panel {
  min-height: 220px;
}

.detail-code {
  margin-bottom: 16px;
  font-size: 20px;
  font-weight: 600;
}
</style>
