<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSemesterList,
  createSemester,
  updateSemester,
  deleteSemester,
  setCurrentSemester,
  type Semester,
  type SemesterForm,
} from '../../api/semester'

const loading = ref(false)
const tableData = ref<Semester[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = reactive({
  keyword: '',
  status: '',
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<number | null>(null)

const form = reactive<SemesterForm>({
  name: '',
  schoolYear: '',
  term: '',
  startDate: '',
  endDate: '',
  status: '未开始',
  remark: '',
})

const rules = {
  name: [{ required: true, message: '请输入学期名称', trigger: 'blur' }],
  schoolYear: [{ required: true, message: '请输入学年', trigger: 'blur' }],
  term: [{ required: true, message: '请输入学期', trigger: 'blur' }],
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getSemesterList({
      keyword: searchForm.keyword || undefined,
      status: searchForm.status || undefined,
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
  searchForm.keyword = ''
  searchForm.status = ''
  handleSearch()
}

function openAdd() {
  dialogTitle.value = '新增学期'
  editingId.value = null
  form.name = ''
  form.schoolYear = ''
  form.term = ''
  form.startDate = ''
  form.endDate = ''
  form.status = '未开始'
  form.remark = ''
  dialogVisible.value = true
}

function openEdit(row: Semester) {
  dialogTitle.value = '编辑学期'
  editingId.value = row.id
  form.name = row.name
  form.schoolYear = row.schoolYear
  form.term = row.term
  form.startDate = row.startDate || ''
  form.endDate = row.endDate || ''
  form.status = row.status
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (editingId.value) {
      await updateSemester(editingId.value, form)
      ElMessage.success('修改成功')
    } else {
      await createSemester(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (_e) {
    console.error(_e)
  }
}

async function handleDelete(row: Semester) {
  await ElMessageBox.confirm(
    row.isCurrent === 1
      ? '当前学期不能直接删除，请先设置其他学期为当前学期'
      : `确定删除学期「${row.name}」吗？`,
    '提示',
    { type: 'warning', confirmButtonDisabled: row.isCurrent === 1 }
  )
  await deleteSemester(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

async function handleSetCurrent(row: Semester) {
  if (row.isCurrent === 1) return
  await ElMessageBox.confirm(
    `确定将「${row.name}」设置为当前学期吗？切换后，教学任务、课表、排课方案和统计数据将按该学期展示。`,
    '提示',
    { type: 'warning' }
  )
  await setCurrentSemester(row.id)
  ElMessage.success('已设置为当前学期')
  fetchData()
}

function statusTagType(status: string) {
  if (status === '进行中') return 'success'
  if (status === '已结束') return 'info'
  return 'warning'
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <!-- 空状态提示 -->
    <el-alert
      v-if="!loading && tableData.length === 0"
      title="暂无学期数据，请先新增一个学期。"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="学期名称">
          <el-input v-model="searchForm.keyword" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="未开始" value="未开始" />
            <el-option label="进行中" value="进行中" />
            <el-option label="已结束" value="已结束" />
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
          <span>学期列表</span>
          <el-button type="primary" @click="openAdd">新增学期</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="name" label="学期名称" min-width="180" />
        <el-table-column prop="schoolYear" label="学年" width="120" />
        <el-table-column prop="term" label="学期" width="100" />
        <el-table-column prop="startDate" label="开始日期" width="120" />
        <el-table-column prop="endDate" label="结束日期" width="120" />
        <el-table-column label="当前学期" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isCurrent === 1" type="success">是</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button
              type="success"
              link
              :disabled="row.isCurrent === 1"
              @click="handleSetCurrent(row)"
            >
              设为当前
            </el-button>
            <el-button
              type="danger"
              link
              :disabled="row.isCurrent === 1"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="学期名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：2025-2026学年第二学期" />
        </el-form-item>
        <el-form-item label="学年" prop="schoolYear">
          <el-input v-model="form.schoolYear" placeholder="例如：2025-2026" />
        </el-form-item>
        <el-form-item label="学期" prop="term">
          <el-select v-model="form.term" placeholder="请选择">
            <el-option label="第一学期" value="第一学期" />
            <el-option label="第二学期" value="第二学期" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker
            v-model="form.startDate"
            type="date"
            placeholder="选择开始日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            placeholder="选择结束日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择">
            <el-option label="未开始" value="未开始" />
            <el-option label="进行中" value="进行中" />
            <el-option label="已结束" value="已结束" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
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
