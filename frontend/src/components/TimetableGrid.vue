<script setup lang="ts">
import { computed } from 'vue'
import type { TimetableItem } from '../api/timetable'

const props = defineProps<{
  items: TimetableItem[]
  /** 高亮重点: class=课程/教师/教室, teacher=课程/班级/教室, room=课程/教师/班级 */
  highlight?: 'class' | 'teacher' | 'room'
}>()

const dayNameMap = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
const defaultDays = [1, 2, 3, 4, 5]
const defaultPeriods = [1, 2, 3, 4]

const days = computed(() => {
  const values = [...new Set(props.items.map((item) => item.dayOfWeek).filter(Boolean))]
  return (values.length ? values : defaultDays).sort((a, b) => a - b)
})

const periods = computed(() => {
  const values = [...new Set(props.items.map((item) => item.period).filter(Boolean))]
  return (values.length ? values : defaultPeriods).sort((a, b) => a - b).map((period) => {
    const item = props.items.find((entry) => entry.period === period)
    return {
      index: period,
      label: item?.timeSlotName || `第${period * 2 - 1}-${period * 2}节`,
    }
  })
})

const cells = computed(() => {
  const map: Record<string, TimetableItem> = {}
  for (const item of props.items) {
    const key = `${item.dayOfWeek}-${item.period}`
    map[key] = item
  }
  return map
})

function getCell(day: number, period: number) {
  return cells.value[`${day}-${period}`]
}
</script>

<template>
  <div class="timetable-wrapper">
    <table class="timetable-grid">
      <thead>
        <tr>
          <th class="period-col"></th>
          <th v-for="d in days" :key="d" class="day-col">{{ dayNameMap[d] || `周${d}` }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in periods" :key="p.index">
          <td class="period-label">{{ p.label }}</td>
          <td v-for="d in days" :key="d" class="cell">
            <template v-if="getCell(d, p.index)">
              <div class="cell-content">
                <div class="primary">{{ getCell(d, p.index).courseName }}</div>
                <div class="secondary">
                  <span v-if="props.highlight !== 'teacher'">{{ getCell(d, p.index).teacherName }}</span>
                  <span v-if="props.highlight !== 'class'">{{ getCell(d, p.index).className }}</span>
                  <span v-if="props.highlight !== 'room'">{{ getCell(d, p.index).classroomName }}</span>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="cell-empty">—</div>
            </template>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.timetable-wrapper {
  overflow-x: auto;
}
.timetable-grid {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  min-width: 700px;
}
.timetable-grid th,
.timetable-grid td {
  border: 1px solid #ebeef5;
  padding: 8px 6px;
  text-align: center;
  vertical-align: middle;
}
.timetable-grid thead th {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #606266;
  font-size: 14px;
}
.period-col {
  width: 80px;
}
.day-col {
  min-width: 120px;
}
.period-label {
  background-color: #fafafa;
  font-weight: 500;
  color: #606266;
  font-size: 13px;
}
.cell {
  height: 72px;
  padding: 6px 4px;
}
.cell-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  height: 100%;
  justify-content: center;
}
.cell-content .primary {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  line-height: 1.3;
}
.cell-content .secondary {
  font-size: 11px;
  color: #909399;
  line-height: 1.3;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cell-empty {
  color: #dcdfe6;
  font-size: 16px;
}
</style>
