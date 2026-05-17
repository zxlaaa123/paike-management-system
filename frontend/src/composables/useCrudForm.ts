/**
 * 通用 CRUD 表单 Composable
 *
 * 使用示例（以 TeacherView 为例）：
 * ```
 * const { loading, tableData, total, currentPage, pageSize,
 *         searchForm, dialogVisible, dialogTitle, formRef, editingId, form,
 *         handleSearch, handleReset, openAdd, openEdit, handleSubmit, handleDelete } =
 *   useCrudForm<Teacher, TeacherForm>({
 *     fetchList: (q) => getTeacherList(q as Parameters<typeof getTeacherList>[0]),
 *     createItem: createTeacher,
 *     updateItem: updateTeacher,
 *     deleteItem: deleteTeacher,
 *     searchDefaults: { name: '', teacherNo: '', department: '', status: undefined },
 *     formDefaults: { teacherNo: '', name: '', department: '', phone: '', status: 1, remark: '' },
 *     entityName: '教师',
 *   })
 * ```
 * 现有 7 个 CRUD View 暂未迁移到此 composable，仅提取了共享工具函数，
 * 后续可按需逐页面迁移以减少重复代码。
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { PageResult } from '../api/types'

export interface CrudOptions<T, F> {
  /** 获取列表数据的 API 函数 */
  fetchList: (params: Record<string, unknown>) => Promise<PageResult<T>>
  /** 创建记录的 API 函数 */
  createItem: (form: F) => Promise<unknown>
  /** 更新记录的 API 函数 */
  updateItem: (id: number, form: F) => Promise<unknown>
  /** 删除记录的 API 函数 */
  deleteItem: (id: number) => Promise<unknown>
  /** 搜索表单的初始值 */
  searchDefaults: Record<string, unknown>
  /** 表单的初始值 */
  formDefaults: F
  /** 表单验证规则 */
  rules?: Record<string, unknown>
  /** 实体名称（用于提示信息） */
  entityName: string
  /** 列表数据转换函数（可选） */
  transformRecords?: (records: T[]) => T[]
}

export function useCrudForm<T extends { id: number }, F>(options: CrudOptions<T, F>) {
  const loading = ref(false)
  const tableData = ref<T[]>([]) as { value: T[] }
  const total = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(10)

  const searchForm = reactive<Record<string, unknown>>({ ...options.searchDefaults })

  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const formRef = ref()
  const editingId = ref<number | null>(null)

  const form = reactive<F>({ ...options.formDefaults })

  async function fetchData() {
    loading.value = true
    try {
      const params: Record<string, unknown> = {
        ...searchForm,
        page: currentPage.value,
        size: pageSize.value,
      }
      const res = await options.fetchList(params)
      tableData.value = options.transformRecords ? options.transformRecords(res.records) : res.records
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
    for (const key of Object.keys(options.searchDefaults)) {
      ;(searchForm as Record<string, unknown>)[key] = options.searchDefaults[key]
    }
    handleSearch()
  }

  function openAdd() {
    dialogTitle.value = `新增${options.entityName}`
    editingId.value = null
    Object.assign(form, options.formDefaults)
    dialogVisible.value = true
  }

  function openEdit(row: T) {
    dialogTitle.value = `编辑${options.entityName}`
    editingId.value = row.id
    Object.assign(form, row)
    dialogVisible.value = true
  }

  async function handleSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    try {
      if (editingId.value) {
        await options.updateItem(editingId.value, form)
        ElMessage.success('修改成功')
      } else {
        await options.createItem(form)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (_e) {
      // 错误已由 request interceptor 处理
    }
  }

  async function handleDelete(row: T, nameField: keyof T) {
    const name = row[nameField] as unknown as string
    await ElMessageBox.confirm(`确定删除${options.entityName}「${name}」吗？`, '提示', { type: 'warning' })
    await options.deleteItem(row.id)
    ElMessage.success('删除成功')
    fetchData()
  }

  onMounted(fetchData)

  return {
    loading,
    tableData,
    total,
    currentPage,
    pageSize,
    searchForm,
    dialogVisible,
    dialogTitle,
    formRef,
    editingId,
    form,
    fetchData,
    handleSearch,
    handleReset,
    openAdd,
    openEdit,
    handleSubmit,
    handleDelete,
  }
}
