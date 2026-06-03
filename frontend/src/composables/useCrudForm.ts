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

import { ref, reactive, onMounted, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import type { PageResult } from '../api/types'
import { extractMessage } from '../utils/errors'

export interface CrudOptions<T, F extends object, S extends Record<string, unknown> = Record<string, unknown>> {
  /** 获取列表数据的 API 函数 */
  fetchList: (params: S & { page: number; size: number }) => Promise<PageResult<T>>
  /** 创建记录的 API 函数 */
  createItem: (form: F) => Promise<unknown>
  /** 更新记录的 API 函数 */
  updateItem: (id: number, form: F) => Promise<unknown>
  /** 删除记录的 API 函数 */
  deleteItem: (id: number) => Promise<unknown>
  /** 搜索表单的初始值（其形状即搜索表单类型 S） */
  searchDefaults: S
  /** 表单的初始值 */
  formDefaults: F
  /** 表单验证规则 */
  rules?: Record<string, unknown>
  /** 实体名称（用于提示信息） */
  entityName: string
  /** 列表数据转换函数（可选） */
  transformRecords?: (records: T[]) => T[]
}

export function useCrudForm<
  T extends { id: number },
  F extends object,
  S extends Record<string, unknown> = Record<string, unknown>,
>(options: CrudOptions<T, F, S>) {
  const loading = ref(false)
  const tableData = ref<T[]>([]) as Ref<T[]>
  const total = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(10)

  const searchForm = reactive({ ...options.searchDefaults }) as S

  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const formRef = ref<FormInstance>()
  const editingId = ref<number | null>(null)

  const form = reactive({ ...options.formDefaults }) as F

  async function fetchData() {
    loading.value = true
    try {
      const params = {
        ...searchForm,
        page: currentPage.value,
        size: pageSize.value,
      } as S & { page: number; size: number }
      const res = await options.fetchList(params)
      tableData.value = options.transformRecords ? options.transformRecords(res.records) : res.records
      total.value = res.total
    } catch (error: unknown) {
      ElMessage.error(extractMessage(error, '数据加载失败'))
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
    Object.assign(form, structuredClone(options.formDefaults))
    dialogVisible.value = true
  }

  function openEdit(row: T) {
    dialogTitle.value = `编辑${options.entityName}`
    editingId.value = row.id
    Object.assign(form, structuredClone(row))
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
      await fetchData()
    } catch (_e) {
      // 错误已由 request interceptor 处理
    }
  }

  async function handleDelete(row: T, nameField: keyof T) {
    const name = row[nameField] as unknown as string
    try {
      await ElMessageBox.confirm(`确定删除${options.entityName}「${name}」吗？`, '提示', { type: 'warning' })
    } catch {
      return
    }
    try {
      await options.deleteItem(row.id)
      ElMessage.success('删除成功')
      await fetchData()
    } catch {
      // 拦截器已弹错误提示
    }
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
