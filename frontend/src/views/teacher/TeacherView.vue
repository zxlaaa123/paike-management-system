<script setup lang="ts">
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  getTeacherList,
  createTeacher,
  updateTeacher,
  deleteTeacher,
  updateTeacherStatus,
  type Teacher,
  type TeacherForm,
} from '../../api/teacher'
import { statusText, statusTagType } from '../../utils/status'
import { useCrudForm } from '../../composables/useCrudForm'

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
} = useCrudForm<Teacher, TeacherForm>({
  fetchList: (q) => getTeacherList(q as Parameters<typeof getTeacherList>[0]),
  createItem: createTeacher,
  updateItem: updateTeacher,
  deleteItem: deleteTeacher,
  searchDefaults: { name: '', teacherNo: '', department: '', status: undefined as number | undefined },
  formDefaults: { teacherNo: '', name: '', department: '', phone: '', status: 1, remark: '' },
  entityName: '教师',
})

void formRef

function handleSizeChange() {
  currentPage.value = 1
  fetchData()
}

const rules = {
  teacherNo: [{ required: true, message: '请输入教师编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入教师姓名', trigger: 'blur' }],
}

async function handleStatusChange(row: Teacher) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定${action}教师「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await updateTeacherStatus(row.id, newStatus)
    ElMessage.success(`${action}成功`)
    fetchData()
  } catch {
    // 错误已由 request interceptor 处理
  }
}
</script>

<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="教师编号">
          <el-input v-model="searchForm.teacherNo" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="教师姓名">
          <el-input v-model="searchForm.name" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="所属部门">
          <el-input v-model="searchForm.department" placeholder="请输入" clearable />
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
          <span>教师列表</span>
          <el-button type="primary" @click="openAdd">新增教师</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="teacherNo" label="教师编号" width="120" />
        <el-table-column prop="name" label="教师姓名" width="100" />
        <el-table-column prop="department" label="所属部门" width="140" />
        <el-table-column prop="phone" label="联系电话" width="130" />
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
            <el-button type="danger" link @click="handleDelete(row, 'name')">删除</el-button>
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
        <el-form-item label="教师编号" prop="teacherNo">
          <el-input v-model="form.teacherNo" placeholder="请输入教师编号" />
        </el-form-item>
        <el-form-item label="教师姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入教师姓名" />
        </el-form-item>
        <el-form-item label="所属部门" prop="department">
          <el-input v-model="form.department" placeholder="请输入所属部门" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入联系电话" />
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
