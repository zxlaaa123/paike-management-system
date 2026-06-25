<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getScheduleAdjustLogs, type ScheduleAdjustLog } from '../../../api/schedulePlan'
import { extractMessage } from '../../../utils/errors'

const props = defineProps<{
  planId: number
}>()

const adjustLogs = ref<ScheduleAdjustLog[]>([])
const adjustLogTotal = ref(0)
const adjustLogPageNum = ref(1)
const adjustLogPageSize = ref(10)
const adjustLogLoading = ref(false)

async function loadAdjustLogs() {
  adjustLogLoading.value = true
  try {
    const page = await getScheduleAdjustLogs({
      planId: props.planId,
      pageNum: adjustLogPageNum.value,
      pageSize: adjustLogPageSize.value,
    })
    adjustLogs.value = page.records || []
    adjustLogTotal.value = page.total || 0
    adjustLogPageNum.value = page.current || 1
    adjustLogPageSize.value = page.size || 10
  } catch (e: unknown) {
    ElMessage.error(extractMessage(e, '加载调整日志失败'))
  } finally {
    adjustLogLoading.value = false
  }
}

function handleAdjustLogPageChange(page: number) {
  adjustLogPageNum.value = page
  loadAdjustLogs()
}

/** 暴露刷新方法供父组件在调整成功后调用 */
defineExpose({ refresh: loadAdjustLogs })

onMounted(() => {
  loadAdjustLogs()
})
</script>

<template>
  <div>
    <el-table :data="adjustLogs" stripe size="small" v-loading="adjustLogLoading">
      <el-table-column prop="courseName" label="课程" width="120" />
      <el-table-column prop="teacherName" label="教师" width="100" />
      <el-table-column prop="className" label="班级" width="120" />
      <el-table-column label="调整前" min-width="160">
        <template #default="{ row }">
          {{ row.oldClassroomName || '—' }} / 周{{ row.oldWeekday }} 第{{ row.oldStartPeriod }}-{{ row.oldEndPeriod }}节
        </template>
      </el-table-column>
      <el-table-column label="调整后" min-width="160">
        <template #default="{ row }">
          {{ row.newClassroomName || '—' }} / 周{{ row.newWeekday }} 第{{ row.newStartPeriod }}-{{ row.newEndPeriod }}节
        </template>
      </el-table-column>
      <el-table-column label="评分变化" width="150">
        <template #default="{ row }">{{ row.beforeScore ?? '—' }} → {{ row.afterScore ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="冲突" width="80">
        <template #default="{ row }">
          <el-tag :type="row.conflictFlag === 1 ? 'danger' : 'success'" size="small">
            {{ row.conflictFlag === 1 ? '有' : '无' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="adjustReason" label="调整原因" min-width="220" />
      <el-table-column prop="createdAt" label="时间" width="180" />
    </el-table>
    <div style="margin-top: 12px; display: flex; justify-content: flex-end">
      <el-pagination
        background
        layout="prev, pager, next, jumper, total"
        :current-page="adjustLogPageNum"
        :page-size="adjustLogPageSize"
        :total="adjustLogTotal"
        @current-change="handleAdjustLogPageChange"
      />
    </div>
    <el-empty v-if="!adjustLogLoading && adjustLogs.length === 0" description="暂无调整记录" />
  </div>
</template>
