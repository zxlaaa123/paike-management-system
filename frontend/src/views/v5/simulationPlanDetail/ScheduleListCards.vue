<template>
  <template v-if="props.detail && props.compare">
    <el-card shadow="never" class="main-card">
      <template #header><div class="title">课程变动明细</div></template>
      <el-table :data="props.compare.changedItems" border stripe>
        <el-table-column label="课程" prop="courseName" min-width="140" />
        <el-table-column label="教师" prop="teacherName" min-width="120" />
        <el-table-column label="班级" prop="className" min-width="120" />
        <el-table-column label="调整前" min-width="180">
          <template #default="{ row }">周{{ row.beforeWeekday }} {{ row.beforeStartPeriod }}-{{ row.beforeEndPeriod }} {{ row.beforeClassroomName || '-' }}</template>
        </el-table-column>
        <el-table-column label="调整后" min-width="180">
          <template #default="{ row }">周{{ row.afterWeekday }} {{ row.afterStartPeriod }}-{{ row.afterEndPeriod }} {{ row.afterClassroomName || '-' }}</template>
        </el-table-column>
        <el-table-column label="冲突" width="100">
          <template #default="{ row }"><el-tag :type="row.conflictFlag ? 'danger' : 'success'">{{ row.conflictFlag ? '有' : '无' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="conflictReason" label="原因" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card v-if="props.detail.localReplanSummary || props.detail.adjustLogs?.length" shadow="never" class="main-card">
      <template #header><div class="title">局部重排日志</div></template>
      <el-timeline v-if="props.detail.localReplanSummary?.logs?.length">
        <el-timeline-item v-for="(log, index) in props.detail.localReplanSummary.logs" :key="index" type="primary">
          {{ log }}
        </el-timeline-item>
      </el-timeline>
      <el-table v-if="props.detail.adjustLogs?.length" :data="props.detail.adjustLogs" border stripe class="block">
        <el-table-column prop="courseName" label="课程" min-width="140" />
        <el-table-column prop="teacherName" label="教师" min-width="120" />
        <el-table-column prop="className" label="班级" min-width="120" />
        <el-table-column label="原位置" min-width="170">
          <template #default="{ row }">周{{ row.oldWeekday ?? '-' }} {{ row.oldStartPeriod ?? '-' }}-{{ row.oldEndPeriod ?? '-' }} {{ row.oldClassroomName || '-' }}</template>
        </el-table-column>
        <el-table-column label="新位置" min-width="170">
          <template #default="{ row }">周{{ row.newWeekday ?? '-' }} {{ row.newStartPeriod ?? '-' }}-{{ row.newEndPeriod ?? '-' }} {{ row.newClassroomName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="afterScore" label="评分变化" width="110" />
        <el-table-column prop="adjustReason" label="日志说明" min-width="240" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card v-if="props.detail" shadow="never" class="main-card">
      <template #header><div class="title">试算课表</div></template>
      <el-table :data="props.detail.items" border stripe>
        <el-table-column prop="courseName" label="课程" min-width="140" />
        <el-table-column prop="teacherName" label="教师" min-width="120" />
        <el-table-column prop="className" label="班级" min-width="120" />
        <el-table-column prop="roomName" label="教室" min-width="120" />
        <el-table-column label="时间" min-width="140">
          <template #default="{ row }">周{{ row.weekday }} 第{{ row.startPeriod }}-{{ row.endPeriod }}节</template>
        </el-table-column>
        <el-table-column label="冲突" width="100">
          <template #default="{ row }"><el-tag :type="row.conflictFlag ? 'danger' : 'success'">{{ row.conflictFlag ? '有' : '无' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="conflictReason" label="冲突原因" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card v-if="props.detail" shadow="never" class="main-card">
      <template #header><div class="title">评分与风险</div></template>
      <el-table :data="props.detail.scoreDetails" border stripe>
        <el-table-column prop="ruleName" label="规则" min-width="180" />
        <el-table-column prop="ruleType" label="类型" width="100" />
        <el-table-column prop="score" label="得分/扣分" width="120" />
        <el-table-column prop="violationCount" label="违规数" width="100" />
        <el-table-column prop="detailMessage" label="说明" min-width="240" show-overflow-tooltip />
      </el-table>
      <el-table :data="props.detail.risks.risks" border stripe class="block">
        <el-table-column prop="level" label="等级" width="100" />
        <el-table-column prop="riskTypeName" label="风险类型" min-width="140" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="建议" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-card>
  </template>
</template>

<script setup lang="ts">
import type { V5SimulationCompare, V5SimulationPlanDetail } from '../../../api/v5SimulationApi'

const props = defineProps<{
  detail: V5SimulationPlanDetail | null
  compare: V5SimulationCompare | null
}>()
</script>
