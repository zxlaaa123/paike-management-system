<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getAllTeachers, type Teacher } from '../../api/teacher'
import { exportTeacherTimetable, getTeacherTimetable, type TimetableItem } from '../../api/timetable'
import TimetableGrid from '../../components/TimetableGrid.vue'

const teacherList = ref<Teacher[]>([])
const selectedTeacherId = ref<number | undefined>()
const timetable = ref<TimetableItem[]>([])
const loading = ref(false)
const exportLoading = ref(false)

const selectedTeacherName = computed(() => {
  const t = teacherList.value.find((x) => x.id === selectedTeacherId.value)
  return t?.name || ''
})

onMounted(async () => {
  try {
    teacherList.value = await getAllTeachers()
  } catch (_e) {
    console.error(_e)
    ElMessage.error('加载教师列表失败')
  }
})

async function handleChange(teacherId: number) {
  if (!teacherId) return
  loading.value = true
  try {
    timetable.value = await getTeacherTimetable(teacherId)
  } catch (_e) {
    console.error(_e)
    ElMessage.error('加载课表失败')
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  if (!selectedTeacherId.value) {
    ElMessage.warning('请先选择教师')
    return
  }
  exportLoading.value = true
  try {
    await exportTeacherTimetable(selectedTeacherId.value)
  } catch (_e) {
    console.error(_e)
  } finally {
    exportLoading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form :inline="true">
        <el-form-item label="选择教师">
          <el-select
            v-model="selectedTeacherId"
            placeholder="请选择教师"
            filterable
            style="width: 240px"
            @change="handleChange"
          >
            <el-option
              v-for="t in teacherList"
              :key="t.id"
              :label="t.name"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!selectedTeacherId" :loading="exportLoading" @click="handleExport">
            导出教师课表
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span v-if="selectedTeacherName">{{ selectedTeacherName }} 课表</span>
          <span v-else>请选择教师查看课表</span>
        </div>
      </template>
      <div v-loading="loading">
        <TimetableGrid v-if="timetable.length > 0" :items="timetable" highlight="teacher" />
        <el-empty v-else-if="selectedTeacherId && !loading" description="该教师暂无排课数据" />
        <el-empty v-else description="请先选择教师" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.card-header {
  font-size: 16px;
  font-weight: 600;
}
</style>
