<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getClassList,
  createClass,
  updateClass,
  deleteClass,
  updateClassStatus,
  type ClassInfo,
  type ClassForm,
} from '../../api/classInfo'

const loading = ref(false)
const tableData = ref<ClassInfo[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = reactive({
  className: '',
  major: '',
  grade: '',
  status: undefined as number | undefined,
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<number | null>(null)

const form = reactive<ClassForm>({
  className: '',
  major: '',
  grade: '',
  studentCount: 30,
  headTeacher: '',
  status: 1,
  remark: '',
})

const rules = {
  className: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  studentCount: [{ required: true, message: '请输入班级人数', trigger: 'blur' }],
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getClassList({
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
  searchForm.className = ''
  searchForm.major = ''
  searchForm.grade = ''
  searchForm.status = undefined
  handleSearch()
}

function openAdd() {
  dialogTitle.value = '新增班级'
  editingId.value = null
  form.className = ''
  form.major = ''
  form.grade = ''
  form.studentCount = 30
  form.headTeacher = ''
  form.status = 1
  form.remark = ''
  dialogVisible.value = true
}

function openEdit(row: ClassInfo) {
  dialogTitle.value = '编辑班级'
  editingId.value = row.id
  form.className = row.className
  form.major = row.major || ''
  form.grade = row.grade || ''
  form.studentCount = row.studentCount
  form.headTeacher = row.headTeacher || ''
  form.status = row.status
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (editingId.value) {
      await updateClass(editingId.value, form)
      ElMessage.success('修改成功')
    } else {
      await createClass(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (_e) {
    console.error(_e)
  }
}

async function handleDelete(row: ClassInfo) {
  await ElMessageBox.confirm(`确定删除班级「${row.className}」吗？`, '提示', { type: 'warning' })
  await deleteClass(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

async function handleStatusChange(row: ClassInfo) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(`确定${action}班级「${row.className}」吗？`, '提示', { type: 'warning' })
  await updateClassStatus(row.id, newStatus)
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
        <el-form-item label="班级名称">
          <el-input v-model="searchForm.className" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="专业">
          <el-input v-model="searchForm.major" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="年级">
          <el-input v-model="searchForm.grade" placeholder="请输入" clearable />
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
          <span>班级列表</span>
          <el-button type="primary" @click="openAdd">新增班级</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="className" label="班级名称" width="140" />
        <el-table-column prop="major" label="专业" width="140" />
        <el-table-column prop="grade" label="年级" width="80" />
        <el-table-column prop="studentCount" label="班级人数" width="100" />
        <el-table-column prop="headTeacher" label="班主任" width="100" />
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
        <el-form-item label="班级名称" prop="className">
          <el-input v-model="form.className" placeholder="请输入班级名称" />
        </el-form-item>
        <el-form-item label="专业" prop="major">
          <el-input v-model="form.major" placeholder="请输入专业名称" />
        </el-form-item>
        <el-form-item label="年级" prop="grade">
          <el-input v-model="form.grade" placeholder="请输入年级" />
        </el-form-item>
        <el-form-item label="班级人数" prop="studentCount">
          <el-input-number v-model="form.studentCount" :min="1" />
        </el-form-item>
        <el-form-item label="班主任" prop="headTeacher">
          <el-input v-model="form.headTeacher" placeholder="请输入班主任姓名" />
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
