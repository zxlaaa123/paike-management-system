<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTeachingTaskList,
  createTeachingTask,
  updateTeachingTask,
  deleteTeachingTask,
  type TeachingTask,
  type TeachingTaskForm,
} from '../../api/teachingTask'
import { getAllCourses, type Course } from '../../api/course'
import { getAllTeachers, type Teacher } from '../../api/teacher'
import { getAllClasses, type ClassInfo } from '../../api/classInfo'

const loading = ref(false)
const tableData = ref<TeachingTask[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = reactive({
  courseName: '',
  teacherName: '',
  className: '',
  status: undefined as number | undefined,
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<number | null>(null)

const form = reactive<TeachingTaskForm>({
  courseId: 0,
  teacherId: 0,
  classId: 0,
  weeklyHours: 4,
  needContinuous: 0,
  status: 1,
  remark: '',
})

const rules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  teacherId: [{ required: true, message: '请选择教师', trigger: 'change' }],
  classId: [{ required: true, message: '请选择班级', trigger: 'change' }],
  weeklyHours: [{ required: true, message: '请输入每周课时', trigger: 'blur' }],
}

const courseList = ref<Course[]>([])
const teacherList = ref<Teacher[]>([])
const classList = ref<ClassInfo[]>([])

async function fetchData() {
  loading.value = true
  try {
    const res = await getTeachingTaskList({
      ...searchForm,
      page: currentPage.value,
      size: pageSize.value,
    })
    tableData.value = res.records.map((t) => ({
      ...t,
      requiredSlots: Math.ceil(t.weeklyHours / 2),
    }))
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function fetchOptions() {
  const [courses, teachers, classes] = await Promise.all([
    getAllCourses(),
    getAllTeachers(),
    getAllClasses(),
  ])
  courseList.value = courses
  teacherList.value = teachers
  classList.value = classes
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  searchForm.courseName = ''
  searchForm.teacherName = ''
  searchForm.className = ''
  searchForm.status = undefined
  handleSearch()
}

function openAdd() {
  dialogTitle.value = '新增教学任务'
  editingId.value = null
  form.courseId = 0
  form.teacherId = 0
  form.classId = 0
  form.weeklyHours = 4
  form.needContinuous = 0
  form.status = 1
  form.remark = ''
  dialogVisible.value = true
}

function openEdit(row: TeachingTask) {
  dialogTitle.value = '编辑教学任务'
  editingId.value = row.id
  form.courseId = row.courseId
  form.teacherId = row.teacherId
  form.classId = row.classId
  form.weeklyHours = row.weeklyHours
  form.needContinuous = row.needContinuous
  form.status = row.status
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (editingId.value) {
      await updateTeachingTask(editingId.value, form)
      ElMessage.success('修改成功')
    } else {
      await createTeachingTask(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (_e) {
    console.error(_e)
  }
}

async function handleDelete(row: TeachingTask) {
  await ElMessageBox.confirm(`确定删除该教学任务吗？`, '提示', { type: 'warning' })
  await deleteTeachingTask(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

function statusText(status: number) {
  return status === 1 ? '启用' : '停用'
}

function statusTagType(status: number) {
  return status === 1 ? 'success' : 'danger'
}

function needContinuousText(val: number) {
  return val === 1 ? '是' : '否'
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
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
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
          <span>教学任务列表</span>
          <el-button type="primary" @click="openAdd">新增教学任务</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="courseName" label="课程名称" width="140" />
        <el-table-column prop="teacherName" label="教师姓名" width="100" />
        <el-table-column prop="className" label="班级名称" width="120" />
        <el-table-column prop="weeklyHours" label="每周课时" width="90" />
        <el-table-column label="已排/需排" width="100">
          <template #default="{ row }">
            <span>{{ row.scheduledSlots }}/{{ row.requiredSlots }}大节</span>
          </template>
        </el-table-column>
        <el-table-column prop="needContinuous" label="连续排课" width="90">
          <template #default="{ row }">
            {{ needContinuousText(row.needContinuous) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="form.courseId" placeholder="请选择课程" :teleported="false" style="width: 100%">
            <el-option v-for="item in courseList" :key="item.id" :label="`${item.courseNo} ${item.courseName}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教师" prop="teacherId">
          <el-select v-model="form.teacherId" placeholder="请选择教师" :teleported="false" style="width: 100%">
            <el-option v-for="item in teacherList" :key="item.id" :label="`${item.teacherNo} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级" prop="classId">
          <el-select v-model="form.classId" placeholder="请选择班级" :teleported="false" style="width: 100%">
            <el-option v-for="item in classList" :key="item.id" :label="item.className" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="每周课时" prop="weeklyHours">
          <el-input-number v-model="form.weeklyHours" :min="1" />
        </el-form-item>
        <el-form-item label="连续排课" prop="needContinuous">
          <el-switch v-model="form.needContinuous" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
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
