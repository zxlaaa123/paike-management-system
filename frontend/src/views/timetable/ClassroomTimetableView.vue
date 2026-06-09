<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getAllClassrooms, type Classroom } from '../../api/classroom'
import { getCurrentSemester, type Semester } from '../../api/semester'
import { exportClassroomTimetable, getClassroomTimetable, type TimetableItem } from '../../api/timetable'
import TimetableGrid from '../../components/TimetableGrid.vue'

const roomList = ref<Classroom[]>([])
const selectedRoomId = ref<number | undefined>()
const timetable = ref<TimetableItem[]>([])
const loading = ref(false)
const exportLoading = ref(false)
const currentSemester = ref<Semester | null>(null)

const selectedRoomName = computed(() => {
  const r = roomList.value.find((x) => x.id === selectedRoomId.value)
  return r?.roomName || ''
})

const currentSemesterName = computed(() => currentSemester.value?.name || '当前学期')

onMounted(async () => {
  try {
    const [rooms, semester] = await Promise.all([
      getAllClassrooms(),
      getCurrentSemester().catch(() => null),
    ])
    roomList.value = rooms
    currentSemester.value = semester
  } catch (_e) {
    console.error(_e)
    ElMessage.error('加载教室列表失败')
  }
})

async function handleChange(roomId: number) {
  if (!roomId) return
  if (!currentSemester.value?.id) {
    ElMessage.warning('当前学期未设置，无法查看课表')
    return
  }
  loading.value = true
  try {
    timetable.value = await getClassroomTimetable(roomId, { semesterId: currentSemester.value.id })
  } catch (_e) {
    console.error(_e)
    ElMessage.error('加载课表失败')
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  if (!selectedRoomId.value) {
    ElMessage.warning('请先选择教室')
    return
  }
  if (!currentSemester.value?.id) {
    ElMessage.warning('当前学期未设置，无法导出课表')
    return
  }
  exportLoading.value = true
  try {
    await exportClassroomTimetable(selectedRoomId.value, { semesterId: currentSemester.value.id })
  } catch (_e) {
    console.error(_e)
    ElMessage.error('导出教室占用表失败')
  } finally {
    exportLoading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form :inline="true">
        <el-form-item label="学期">
          <el-tag type="info">{{ currentSemesterName }}</el-tag>
        </el-form-item>
        <el-form-item label="选择教室">
          <el-select
            v-model="selectedRoomId"
            placeholder="请选择教室"
            filterable
            style="width: 240px"
            @change="handleChange"
          >
            <el-option
              v-for="r in roomList"
              :key="r.id"
              :label="`${r.roomName}${r.building ? ' (' + r.building + ')' : ''}`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!selectedRoomId" :loading="exportLoading" @click="handleExport">
            导出教室占用表
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span v-if="selectedRoomName">{{ selectedRoomName }} 课表</span>
          <span v-else>请选择教室查看课表</span>
        </div>
      </template>
      <div v-loading="loading">
        <TimetableGrid v-if="timetable.length > 0" :items="timetable" highlight="room" />
        <el-empty v-else-if="selectedRoomId && !loading" description="该教室暂无排课数据" />
        <el-empty v-else description="请先选择教室" />
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
