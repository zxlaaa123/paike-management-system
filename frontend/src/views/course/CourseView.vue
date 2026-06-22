<script setup lang="ts">
import {
  getCourseList,
  createCourse,
  updateCourse,
  deleteCourse,
  type Course,
  type CourseForm,
} from '../../api/course'
import { useCrudForm } from '../../composables/useCrudForm'

const courseTypeOptions = [
  { label: '普通课', value: 'NORMAL' },
  { label: '实验课', value: 'EXPERIMENT' },
  { label: '机房课', value: 'COMPUTER' },
  { label: '体育课', value: 'PE' },
]

function courseTypeLabel(type: string) {
  return courseTypeOptions.find((o) => o.value === type)?.label || type
}

const {
  loading,
  tableData,
  total,
  currentPage,
  pageSize,
  searchForm,
  dialogVisible,
  dialogTitle,
  formRef,
  form,
  fetchData,
  handleSearch,
  handleReset,
  openAdd,
  openEdit,
  handleSubmit,
  handleDelete,
} = useCrudForm<Course, CourseForm>({
  fetchList: (q) => getCourseList(q as Parameters<typeof getCourseList>[0]),
  createItem: createCourse,
  updateItem: updateCourse,
  deleteItem: deleteCourse,
  searchDefaults: { courseName: '', courseNo: '', courseType: '' },
  formDefaults: { courseNo: '', courseName: '', courseType: 'NORMAL', courseNature: '', totalHours: 48, weeklyHours: 4, remark: '' },
  entityName: '课程',
})

void formRef

function handleSizeChange() {
  currentPage.value = 1
  fetchData()
}

const rules = {
  courseNo: [{ required: true, message: '请输入课程编号', trigger: 'blur' }],
  courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  totalHours: [{ required: true, message: '请输入总学时', trigger: 'blur' }],
  weeklyHours: [{ required: true, message: '请输入每周课时', trigger: 'blur' }],
}
</script>

<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="课程编号">
          <el-input v-model="searchForm.courseNo" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="课程名称">
          <el-input v-model="searchForm.courseName" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="课程类型">
          <el-select v-model="searchForm.courseType" placeholder="全部" clearable>
            <el-option v-for="opt in courseTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
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
          <span>课程列表</span>
          <el-button type="primary" @click="openAdd">新增课程</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="courseNo" label="课程编号" width="120" />
        <el-table-column prop="courseName" label="课程名称" width="140" />
        <el-table-column prop="courseType" label="课程类型" width="100">
          <template #default="{ row }">
            {{ courseTypeLabel(row.courseType) }}
          </template>
        </el-table-column>
        <el-table-column prop="courseNature" label="课程性质" width="100" />
        <el-table-column prop="totalHours" label="总学时" width="80" />
        <el-table-column prop="weeklyHours" label="每周课时" width="100" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row, 'courseName')">删除</el-button>
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
        @current-change="fetchData" @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="课程编号" prop="courseNo">
          <el-input v-model="form.courseNo" placeholder="请输入课程编号" />
        </el-form-item>
        <el-form-item label="课程名称" prop="courseName">
          <el-input v-model="form.courseName" placeholder="请输入课程名称" />
        </el-form-item>
        <el-form-item label="课程类型" prop="courseType">
          <el-select v-model="form.courseType" :teleported="false">
            <el-option v-for="opt in courseTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程性质" prop="courseNature">
          <el-input v-model="form.courseNature" placeholder="请输入课程性质" />
        </el-form-item>
        <el-form-item label="总学时" prop="totalHours">
          <el-input-number v-model="form.totalHours" :min="1" />
        </el-form-item>
        <el-form-item label="每周课时" prop="weeklyHours">
          <el-input-number v-model="form.weeklyHours" :min="1" />
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
