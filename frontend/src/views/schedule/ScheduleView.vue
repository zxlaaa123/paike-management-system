<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getScheduleList,
  createSchedule,
  deleteSchedule,
  checkConflict,
  type Schedule,
  type ScheduleForm,
} from '../../api/schedule'
import { getAllTeachingTasks, type TeachingTask } from '../../api/teachingTask'
import { getAllTimeSlots, type TimeSlot } from '../../api/timeSlot'
import { getAllClassrooms, type Classroom } from '../../api/classroom'

const loading = ref(false)
const tableData = ref<Schedule[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = reactive({
  courseName: '',
  teacherName: '',
  className: '',
  roomName: '',
  dayOfWeek: undefined as number | undefined,
})

const dialogVisible = ref(false)
const formRef = ref()
const submitting = ref(false)

const form = reactive<ScheduleForm>({
  teachingTaskId: 0,
  timeSlotId: 0,
  classroomId: 0,
})

const rules = {
  teachingTaskId: [{ required: true, message: '请选择教学任务', trigger: 'change' }],
  timeSlotId: [{ required: true, message: '请选择时间段', trigger: 'change' }],
  classroomId: [{ required: true, message: '请选择教室', trigger: 'change' }],
}

const taskList = ref<TeachingTask[]>([])
const timeSlotList = ref<TimeSlot[]>([])
const classroomList = ref<Classroom[]>([])

// 按星期分组的时间段
const timeSlotsByDay = computed(() => {
  const map: Record<number, TimeSlot[]> = { 1: [], 2: [], 3: [], 4: [], 5: [] }
  for (const slot of timeSlotList.value) {
    if (map[slot.dayOfWeek]) map[slot.dayOfWeek].push(slot)
  }
  return map
})

const dayNames: Record<number, string> = { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五' }

async function fetchData() {
  loading.value = true
  try {
    const res = await getScheduleList({
      ...searchForm,
      page: currentPage.value,
      size: pageSize.value,
    })
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function fetchOptions() {
  const [tasks, slots, rooms] = await Promise.all([
    getAllTeachingTasks(),
    getAllTimeSlots(),
    getAllClassrooms(),
  ])
  taskList.value = tasks
  timeSlotList.value = slots
  classroomList.value = rooms
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  searchForm.courseName = ''
  searchForm.teacherName = ''
  searchForm.className = ''
  searchForm.roomName = ''
  searchForm.dayOfWeek = undefined
  handleSearch()
}

function openAdd() {
  form.teachingTaskId = 0
  form.timeSlotId = 0
  form.classroomId = 0
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (form.teachingTaskId === 0 || form.timeSlotId === 0 || form.classroomId === 0) {
    ElMessage.warning('请完整填写排课信息')
    return
  }

  submitting.value = true
  try {
    // 先调用预检接口
    const checkResult = await checkConflict({
      teachingTaskId: form.teachingTaskId,
      timeSlotId: form.timeSlotId,
      classroomId: form.classroomId,
    })
    if (checkResult.hasConflict) {
      ElMessage.error(checkResult.message)
      return
    }
    // 无冲突，正式提交
    await createSchedule(form)
    ElMessage.success('排课成功')
    dialogVisible.value = false
    fetchData()
  } catch (_e) {
    console.error(_e)
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: Schedule) {
  await ElMessageBox.confirm('确定删除该排课记录吗？', '提示', { type: 'warning' })
  await deleteSchedule(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

function getTaskLabel(task: TeachingTask) {
  const requiredSlots = Math.ceil((task.weeklyHours || 0) / 2)
  const scheduledSlots = task.scheduledSlots || 0
  return `${task.courseName} / ${task.teacherName} / ${task.className} / 每周${task.weeklyHours}节 / 已排${scheduledSlots}/${requiredSlots}大节`
}

function getClassroomLabel(room: Classroom) {
  return `${room.roomName}（${room.building ? room.building + ' · ' : ''}容量${room.capacity} · ${roomTypeText(room.roomType)}）`
}

function roomTypeText(type: string) {
  const map: Record<string, string> = {
    NORMAL: '普通教室',
    MULTIMEDIA: '多媒体教室',
    LAB: '实验室',
    COMPUTER: '机房',
  }
  return map[type] || type
}

onMounted(() => {
  fetchData()
  fetchOptions()
})
</script>

<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="课程名称">
          <el-input v-model="searchForm.courseName" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="教师姓名">
          <el-input v-model="searchForm.teacherName" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="班级名称">
          <el-input v-model="searchForm.className" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="教室">
          <el-input v-model="searchForm.roomName" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="星期">
          <el-select v-model="searchForm.dayOfWeek" placeholder="全部" clearable>
            <el-option label="周一" :value="1" />
            <el-option label="周二" :value="2" />
            <el-option label="周三" :value="3" />
            <el-option label="周四" :value="4" />
            <el-option label="周五" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>排课列表</span>
          <el-button type="primary" @click="openAdd">新增排课</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="courseName" label="课程名称" min-width="140" />
        <el-table-column prop="teacherName" label="教师" width="100" />
        <el-table-column prop="className" label="班级" width="120" />
        <el-table-column prop="timeLabel" label="时间段" width="130" />
        <el-table-column label="教室" min-width="160">
          <template #default="{ row }">
            <span>{{ row.roomName }}</span>
            <span v-if="row.building" style="color: #909399; margin-left: 4px">({{ row.building }})</span>
          </template>
        </el-table-column>
        <el-table-column prop="sourceTypeName" label="排课来源" width="100" />
        <el-table-column label="自动排课批次" min-width="160">
          <template #default="{ row }">
            <span v-if="row.batchNo">{{ row.batchNo }}</span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @change="fetchData"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增排课" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="教学任务" prop="teachingTaskId">
          <el-select
            v-model="form.teachingTaskId"
            placeholder="请选择教学任务"
            filterable
            :teleported="false"
            style="width: 100%"
          >
            <el-option
              v-for="item in taskList"
              :key="item.id"
              :label="getTaskLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时间段" prop="timeSlotId">
          <el-select v-model="form.timeSlotId" placeholder="请选择时间段" :teleported="false" style="width: 100%">
            <el-option-group v-for="(slots, day) in timeSlotsByDay" :key="day" :label="dayNames[day]">
              <el-option v-for="slot in slots" :key="slot.id" :label="slot.timeLabel" :value="slot.id" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="教室" prop="classroomId">
          <el-select v-model="form.classroomId" placeholder="请选择教室" filterable :teleported="false" style="width: 100%">
            <el-option
              v-for="item in classroomList"
              :key="item.id"
              :label="getClassroomLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card {
  padding: 4px 0;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
