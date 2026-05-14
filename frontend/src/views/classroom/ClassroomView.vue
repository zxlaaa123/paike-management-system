<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getClassroomList,
  createClassroom,
  updateClassroom,
  deleteClassroom,
  updateClassroomStatus,
  type Classroom,
  type ClassroomForm,
} from '../../api/classroom'

const loading = ref(false)
const tableData = ref<Classroom[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = reactive({
  roomName: '',
  building: '',
  roomType: '',
  status: undefined as number | undefined,
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<number | null>(null)

const form = reactive<ClassroomForm>({
  roomName: '',
  building: '',
  capacity: 50,
  roomType: 'NORMAL',
  status: 1,
  remark: '',
})

const rules = {
  roomName: [{ required: true, message: '请输入教室名称', trigger: 'blur' }],
  capacity: [{ required: true, message: '请输入教室容量', trigger: 'blur' }],
}

const roomTypeOptions = [
  { label: '普通教室', value: 'NORMAL' },
  { label: '多媒体教室', value: 'MULTIMEDIA' },
  { label: '实验室', value: 'LAB' },
  { label: '机房', value: 'COMPUTER' },
]

function roomTypeLabel(type: string) {
  return roomTypeOptions.find((o) => o.value === type)?.label || type
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getClassroomList({
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
  searchForm.roomName = ''
  searchForm.building = ''
  searchForm.roomType = ''
  searchForm.status = undefined
  handleSearch()
}

function openAdd() {
  dialogTitle.value = '新增教室'
  editingId.value = null
  form.roomName = ''
  form.building = ''
  form.capacity = 50
  form.roomType = 'NORMAL'
  form.status = 1
  form.remark = ''
  dialogVisible.value = true
}

function openEdit(row: Classroom) {
  dialogTitle.value = '编辑教室'
  editingId.value = row.id
  form.roomName = row.roomName
  form.building = row.building || ''
  form.capacity = row.capacity
  form.roomType = row.roomType
  form.status = row.status
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (editingId.value) {
      await updateClassroom(editingId.value, form)
      ElMessage.success('修改成功')
    } else {
      await createClassroom(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (_e) {
    console.error(_e)
  }
}

async function handleDelete(row: Classroom) {
  await ElMessageBox.confirm(`确定删除教室「${row.roomName}」吗？`, '提示', { type: 'warning' })
  await deleteClassroom(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

async function handleStatusChange(row: Classroom) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(`确定${action}教室「${row.roomName}」吗？`, '提示', { type: 'warning' })
  await updateClassroomStatus(row.id, newStatus)
  ElMessage.success(`${action}成功`)
  fetchData()
}

function statusText(status: number) {
  return status === 1 ? '启用' : '停用'
}

function statusTagType(status: number) {
  return status === 1 ? 'success' : 'danger'
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="教室名称">
          <el-input v-model="searchForm.roomName" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="教学楼">
          <el-input v-model="searchForm.building" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="教室类型">
          <el-select v-model="searchForm.roomType" placeholder="全部" clearable>
            <el-option v-for="opt in roomTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
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
          <span>教室列表</span>
          <el-button type="primary" @click="openAdd">新增教室</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="roomName" label="教室名称" width="120" />
        <el-table-column prop="building" label="教学楼" width="120" />
        <el-table-column prop="capacity" label="容量" width="80" />
        <el-table-column prop="roomType" label="教室类型" width="120">
          <template #default="{ row }">
            {{ roomTypeLabel(row.roomType) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="260" fixed="right">
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
        @change="fetchData"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="教室名称" prop="roomName">
          <el-input v-model="form.roomName" placeholder="请输入教室名称" />
        </el-form-item>
        <el-form-item label="教学楼" prop="building">
          <el-input v-model="form.building" placeholder="请输入教学楼" />
        </el-form-item>
        <el-form-item label="教室容量" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" />
        </el-form-item>
        <el-form-item label="教室类型" prop="roomType">
          <el-select v-model="form.roomType" :teleported="false">
            <el-option v-for="opt in roomTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
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
