<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getAllClassrooms, type Classroom } from '../../api/classroom'
import { getClassroomTimetable, type TimetableItem } from '../../api/timetable'
import TimetableGrid from '../../components/TimetableGrid.vue'

const roomList = ref<Classroom[]>([])
const selectedRoomId = ref<number | undefined>()
const timetable = ref<TimetableItem[]>([])
const loading = ref(false)

const selectedRoomName = computed(() => {
  const r = roomList.value.find((x) => x.id === selectedRoomId.value)
  return r?.roomName || ''
})

onMounted(async () => {
  try {
    roomList.value = await getAllClassrooms()
  } catch {
    ElMessage.error('加载教室列表失败')
  }
})

async function handleChange(roomId: number) {
  if (!roomId) return
  loading.value = true
  try {
    timetable.value = await getClassroomTimetable(roomId)
  } catch {
    ElMessage.error('加载课表失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form :inline="true">
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
