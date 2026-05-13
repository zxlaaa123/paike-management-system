<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getAllClasses, type ClassInfo } from '../../api/classInfo'
import { exportClassTimetable, getClassTimetable, type TimetableItem } from '../../api/timetable'
import TimetableGrid from '../../components/TimetableGrid.vue'

const classList = ref<ClassInfo[]>([])
const selectedClassId = ref<number | undefined>()
const timetable = ref<TimetableItem[]>([])
const loading = ref(false)
const exportLoading = ref(false)

const selectedClassName = computed(() => {
  const c = classList.value.find((x) => x.id === selectedClassId.value)
  return c?.className || ''
})

onMounted(async () => {
  try {
    classList.value = await getAllClasses()
  } catch {
    ElMessage.error('加载班级列表失败')
  }
})

async function handleChange(classId: number) {
  if (!classId) return
  loading.value = true
  try {
    timetable.value = await getClassTimetable(classId)
  } catch {
    ElMessage.error('加载课表失败')
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  if (!selectedClassId.value) {
    ElMessage.warning('请先选择班级')
    return
  }
  exportLoading.value = true
  try {
    await exportClassTimetable(selectedClassId.value)
  } catch {
    // request 拦截器已统一提示错误，这里吞掉异常避免控制台未处理告警
  } finally {
    exportLoading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form :inline="true">
        <el-form-item label="选择班级">
          <el-select
            v-model="selectedClassId"
            placeholder="请选择班级"
            filterable
            style="width: 240px"
            @change="handleChange"
          >
            <el-option
              v-for="c in classList"
              :key="c.id"
              :label="c.className"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!selectedClassId" :loading="exportLoading" @click="handleExport">
            导出班级课表
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span v-if="selectedClassName">{{ selectedClassName }} 课表</span>
          <span v-else>请选择班级查看课表</span>
        </div>
      </template>
      <div v-loading="loading">
        <TimetableGrid v-if="timetable.length > 0" :items="timetable" highlight="class" />
        <el-empty v-else-if="selectedClassId && !loading" description="该班级暂无排课数据" />
        <el-empty v-else description="请先选择班级" />
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
