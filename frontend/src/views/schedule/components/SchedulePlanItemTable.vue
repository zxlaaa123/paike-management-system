<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SchedulePlanItem } from '../../../api/schedulePlan'
import { weekTypeTagType, weekTypeText } from '../../../utils/status'

const props = defineProps<{
  items: SchedulePlanItem[]
  planStatus: string
}>()

const emit = defineEmits<{
  'open-task-logs': [item: SchedulePlanItem]
  'open-candidates': [item: SchedulePlanItem]
  'open-adjust': [item: SchedulePlanItem]
}>()

/** 周次筛选：'ALL' 全部 / 'ODD' 仅单周 / 'EVEN' 仅双周（V9 阶段0 原型） */
const weekTypeFilter = ref<'ALL' | 'ODD' | 'EVEN'>('ALL')
/** 按周次筛选后的方案明细（V9 阶段0 原型） */
const filteredItems = computed(() => {
  if (weekTypeFilter.value === 'ALL') return props.items
  return props.items.filter((item) => item.weekType === weekTypeFilter.value)
})
</script>

<template>
  <div>
    <div class="items-toolbar">
      <span class="toolbar-label">周次筛选：</span>
      <el-radio-group v-model="weekTypeFilter" size="small">
        <el-radio-button value="ALL">全部</el-radio-button>
        <el-radio-button value="ODD">单周</el-radio-button>
        <el-radio-button value="EVEN">双周</el-radio-button>
      </el-radio-group>
      <span class="toolbar-count">共 {{ filteredItems.length }} 条</span>
    </div>
    <el-table :data="filteredItems" stripe>
      <el-table-column prop="courseName" label="课程" width="120" />
      <el-table-column prop="teacherName" label="教师" width="100" />
      <el-table-column prop="className" label="班级" width="120" />
      <el-table-column label="时间" width="120">
        <template #default="{ row }">周{{ row.weekday }} 第{{ row.startPeriod }}-{{ row.endPeriod }}节</template>
      </el-table-column>
      <el-table-column label="周次" width="90">
        <template #default="{ row }">
          <el-tag :type="weekTypeTagType(row.weekType)" size="small">{{ weekTypeText(row.weekType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="roomName" label="教室" width="120" />
      <el-table-column label="来源" width="80">
        <template #default="{ row }">
          <el-tag size="small">{{ row.sourceType === 'MANUAL' ? '手动' : '自动' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="冲突" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.conflictFlag === 1" type="danger" size="small">有冲突</el-tag>
          <span v-else>无</span>
        </template>
      </el-table-column>
      <el-table-column prop="conflictReason" label="冲突原因" min-width="180" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="emit('open-task-logs', row)">日志</el-button>
          <el-button type="info" link size="small" @click="emit('open-candidates', row)">候选位置</el-button>
          <el-button v-if="planStatus !== 'ABANDONED'" type="warning" link size="small" @click="emit('open-adjust', row)">调整</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="filteredItems.length === 0" description="暂无方案明细" />
  </div>
</template>

<style scoped>
.items-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.toolbar-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.toolbar-count {
  margin-left: auto;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
