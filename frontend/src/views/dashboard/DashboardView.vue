<template>
  <div class="dashboard-page">
    <el-card shadow="never">
      <template #header>
        <div class="header">首页统计</div>
      </template>
      <el-alert v-if="loadError" type="warning" show-icon :closable="false" :title="loadError" />
      <div class="cards">
        <el-card v-for="item in statsCards" :key="item.label" shadow="hover">
          <div class="label">{{ item.label }}</div>
          <div class="value">{{ item.value }}</div>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getTeacherList } from '../../api/teacher'
import { getClassList } from '../../api/classInfo'
import { getClassroomList } from '../../api/classroom'
import { getCourseList } from '../../api/course'

const loadError = ref('')
const stats = reactive({
  teacherCount: 0,
  classCount: 0,
  classroomCount: 0,
  courseCount: 0,
})

const statsCards = computed(() => [
  { label: '教师数量', value: stats.teacherCount },
  { label: '班级数量', value: stats.classCount },
  { label: '教室数量', value: stats.classroomCount },
  { label: '课程数量', value: stats.courseCount },
])

async function fetchStats() {
  loadError.value = ''
  try {
    const [teacherRes, classRes, classroomRes, courseRes] = await Promise.all([
      getTeacherList({ page: 1, size: 1 }),
      getClassList({ page: 1, size: 1 }),
      getClassroomList({ page: 1, size: 1 }),
      getCourseList({ page: 1, size: 1 }),
    ])
    stats.teacherCount = teacherRes.total
    stats.classCount = classRes.total
    stats.classroomCount = classroomRes.total
    stats.courseCount = courseRes.total
  } catch (_error) {
    loadError.value = '统计数据加载失败，请稍后重试。'
    ElMessage.error(loadError.value)
  }
}

onMounted(fetchStats)
</script>

<style scoped>
.dashboard-page {
  padding: 8px;
}

.header {
  font-weight: 600;
}

.cards {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(160px, 1fr));
  gap: 12px;
}

.label {
  color: #909399;
  font-size: 14px;
}

.value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 600;
}
</style>
