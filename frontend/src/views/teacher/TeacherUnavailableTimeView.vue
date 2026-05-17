<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getUnavailableTimeList,
  createUnavailableTime,
  updateUnavailableTime,
  deleteUnavailableTime,
  updateUnavailableTimeStatus,
  type TeacherUnavailableTime,
  type UnavailableTimeForm,
} from '../../api/teacherUnavailableTime'
import { getAllTeachers, type Teacher } from '../../api/teacher'
import { getAllTimeSlots, type TimeSlot } from '../../api/timeSlot'
import { statusText, statusTagType } from '../../utils/status'

const loading = ref(false)
const tableData = ref<TeacherUnavailableTime[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = reactive({
  teacherName: '',
  timeSlotId: undefined as number | undefined,
  status: undefined as number | undefined,
})

const teachers = ref<Teacher[]>([])
const timeSlots = ref<TimeSlot[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<number | null>(null)

const form = reactive<UnavailableTimeForm>({
  teacherId: 0,
  timeSlotId: 0,
  reason: '',
  status: 1,
  remark: '',
})

const rules = {
  teacherId: [{ required: true, message: '请选择教师', trigger: 'change' }],
  timeSlotId: [{ required: true, message: '请选择时间段', trigger: 'change' }],
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getUnavailableTimeList({
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

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  searchForm.teacherName = ''
  searchForm.timeSlotId = undefined
  searchForm.status = undefined
  handleSearch()
}

async function loadOptions() {
  try {
    // 教师和时间段都是基础档案数据，弹窗编辑依赖它们，页面初始化时统一预加载。
    teachers.value = await getAllTeachers()
    timeSlots.value = await getAllTimeSlots()
  } catch (_e) {
    console.error(_e)
  }
}

function openAdd() {
  dialogTitle.value = '新增禁排时间'
  editingId.value = null
  // 显式重置表单，避免上次编辑残留的教师或时间段误带入新增操作。
  form.teacherId = 0
  form.timeSlotId = 0
  form.reason = ''
  form.status = 1
  form.remark = ''
  dialogVisible.value = true
}

function openEdit(row: TeacherUnavailableTime) {
  dialogTitle.value = '编辑禁排时间'
  editingId.value = row.id
  form.teacherId = row.teacherId
  form.timeSlotId = row.timeSlotId
  form.reason = row.reason || ''
  form.status = row.status
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (editingId.value) {
      await updateUnavailableTime(editingId.value, form)
      ElMessage.success('修改成功')
    } else {
      await createUnavailableTime(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (_e) {
    console.error(_e)
  }
}

async function handleDelete(row: TeacherUnavailableTime) {
  await ElMessageBox.confirm(`确定删除「${row.teacherName}」在「${row.timeSlotName}」的禁排时间吗？`, '提示', { type: 'warning' })
  await deleteUnavailableTime(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

async function handleStatusChange(row: TeacherUnavailableTime) {
  // 状态切换只决定该禁排规则是否参与排课约束，不删除原记录，便于后续临时恢复。
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(`确定${action}该禁排时间吗？`, '提示', { type: 'warning' })
  await updateUnavailableTimeStatus(row.id, newStatus)
  ElMessage.success(`${action}成功`)
  fetchData()
}

onMounted(() => {
  loadOptions()
  fetchData()
})
</script>

<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="教师姓名">
          <el-input v-model="searchForm.teacherName" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="时间段">
          <el-select v-model="searchForm.timeSlotId" placeholder="全部" clearable style="width: 180px">
            <el-option v-for="ts in timeSlots" :key="ts.id" :label="ts.timeLabel" :value="ts.id" />
          </el-select>
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
          <span>教师禁排时间列表</span>
          <el-button type="primary" @click="openAdd">新增禁排时间</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="teacherName" label="教师姓名" width="120" />
        <el-table-column prop="department" label="所属部门" width="140" />
        <el-table-column prop="timeSlotName" label="禁排时间段" width="150" />
        <el-table-column prop="reason" label="禁排原因" min-width="150" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="primary" link @click="handleStatusChange(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="教师" prop="teacherId">
          <el-select v-model="form.teacherId" placeholder="请选择教师" filterable :teleported="false" style="width: 100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间段" prop="timeSlotId">
          <el-select v-model="form.timeSlotId" placeholder="请选择时间段" :teleported="false" style="width: 100%">
            <el-option v-for="ts in timeSlots" :key="ts.id" :label="ts.timeLabel" :value="ts.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="禁排原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" placeholder="请输入禁排原因" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
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
