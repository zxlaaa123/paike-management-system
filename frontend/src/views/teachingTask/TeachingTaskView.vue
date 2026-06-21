<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
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
import { getAllSemesters, getCurrentSemester, type Semester } from '../../api/semester'
import { statusText, statusTagType } from '../../utils/status'
import { extractMessage, isCancel } from '../../utils/errors'
import { fallback } from '../../utils/async'

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
  semesterId: undefined as number | undefined,
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive<TeachingTaskForm>({
  courseId: 0,
  teacherId: 0,
  classId: 0,
  weeklyHours: 4,
  weekType: 'ALL',
  startWeek: 1,
  endWeek: 20,
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
const semesterList = ref<Semester[]>([])
const currentSemester = ref<Semester | null>(null)

const hasCurrentSemester = computed(() => currentSemester.value !== null)

async function fetchData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { ...searchForm, page: currentPage.value, size: pageSize.value }
    // 如果没有指定 semesterId 且有当前学期，则传当前学期
    if (!params.semesterId && currentSemester.value) {
      params.semesterId = currentSemester.value.id
    }
    const res = await getTeachingTaskList(params)
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
  const [courses, teachers, classes, semesters, current] = await Promise.all([
    fallback(getAllCourses(), []),
    fallback(getAllTeachers(), []),
    fallback(getAllClasses(), []),
    fallback(getAllSemesters(), []),
    fallback(getCurrentSemester(), null),
  ])
  courseList.value = courses
  teacherList.value = teachers
  classList.value = classes
  semesterList.value = semesters
  currentSemester.value = current
  // 默认选中当前学期
  if (current) {
    searchForm.semesterId = current.id
  }
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
  searchForm.semesterId = currentSemester.value?.id
  handleSearch()
}

function openAdd() {
  if (!hasCurrentSemester.value) {
    ElMessage.warning('当前未设置学期，请先在「学期管理」中设置当前学期')
    return
  }
  dialogTitle.value = '新增教学任务'
  editingId.value = null
  form.courseId = 0
  form.teacherId = 0
  form.classId = 0
  form.weeklyHours = 4
  form.weekType = 'ALL'
  form.startWeek = 1
  form.endWeek = 20
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
  form.weekType = row.weekType || 'ALL'
  form.startWeek = row.startWeek ?? 1
  form.endWeek = row.endWeek ?? 20
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
    ElMessage.error('保存教学任务失败')
  }
}

async function handleDelete(row: TeachingTask) {
  try {
    await ElMessageBox.confirm(`确定删除该教学任务吗？`, '提示', { type: 'warning' })
    await deleteTeachingTask(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (err: unknown) {
    if (isCancel(err)) return
    ElMessage.error(extractMessage(err, '删除失败'))
    fetchData()
  }
}

function needContinuousText(val: number) {
  return val === 1 ? '是' : '否'
}

/** 周次类型展示文案：ALL 全周 / ODD 单周 / EVEN 双周 */
function weekTypeText(val: string) {
  switch (val) {
    case 'ODD':
      return '单周'
    case 'EVEN':
      return '双周'
    case 'ALL':
    default:      return '全周'
  }
}

/** 周段展示文案：默认 1-20 显示「整学期」，否则显示「1-8周」 */
function weekRangeText(start: number | undefined, end: number | undefined) {
  const s = start ?? 1
  const e = end ?? 20
  if (s === 1 && e === 20) return '整学期'
  return `${s}-${e}周`
}

function getSemesterName(id: number | undefined) {
  if (!id) return '—'
  const s = semesterList.value.find((x) => x.id === id)
  return s ? s.name : `ID:${id}`
}

onMounted(() => {
  void (async () => {
    await fetchOptions()
    await fetchData()
  })()
})
</script>

<template>
  <div class="page-container">
    <!-- 无当前学期提示 -->
    <el-alert
      v-if="!hasCurrentSemester"
      title="当前未设置学期，部分功能无法使用。请先在「学期管理」中创建并设置当前学期。"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <!-- 当前学期信息条 -->
    <el-alert
      v-if="currentSemester"
      :title="`当前学期：${currentSemester.name}`"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="学期">
          <el-select v-model="searchForm.semesterId" placeholder="选择学期" clearable style="width: 220px">
            <el-option
              v-for="s in semesterList"
              :key="s.id"
              :label="s.name"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
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
          <el-button type="primary" @click="openAdd" :disabled="!hasCurrentSemester">新增教学任务</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column label="所属学期" width="180">
          <template #default="{ row }">
            <span>{{ getSemesterName(row.semesterId) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程名称" width="140" />
        <el-table-column prop="teacherName" label="教师姓名" width="100" />
        <el-table-column prop="className" label="班级名称" width="120" />
        <el-table-column prop="weeklyHours" label="每周课时" width="90" />
        <el-table-column prop="weekType" label="周次类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.weekType === 'ALL' ? 'info' : 'warning'">{{ weekTypeText(row.weekType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="周段" width="110">
          <template #default="{ row }">
            <span>{{ weekRangeText(row.startWeek, row.endWeek) }}</span>
          </template>
        </el-table-column>
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
        @current-change="fetchData" @size-change="fetchData"
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
        <el-form-item label="周次类型" prop="weekType">
          <el-select v-model="form.weekType" placeholder="请选择周次类型" style="width: 100%">
            <el-option label="全周" value="ALL" />
            <el-option label="单周" value="ODD" />
            <el-option label="双周" value="EVEN" />
          </el-select>
        </el-form-item>
        <el-form-item label="起止周" prop="startWeek">
          <el-input-number v-model="form.startWeek" :min="1" :max="form.endWeek" controls-position="right" />
          <span style="margin: 0 8px">至</span>
          <el-input-number v-model="form.endWeek" :min="form.startWeek" :max="63" controls-position="right" />
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
