# N-24 / N-30 useCrudForm 类型收口

> 调查对象：`20260527bug验证报告.md` N-24（`tableData` 类型断言）、N-30（搜索表单 `Record<string, unknown>` 类型）
> 调查日期：2026-06-03
> 分支：`refactor/n24-n30-usecrudform-types`
> 结论：**确认 → 已修复**。两项均在 `frontend/src/composables/useCrudForm.ts`，本次一并收口。

---

## 1. 背景

`20260527bug验证报告.md` 两项原状态均为 `确认`：

- **N-24**：`useCrudForm.ts` 仍存在 `tableData` 相关类型断言。
- **N-30**：`useCrudForm.ts` 检出多处 `Record<string, unknown>` 搜索表单类型。

## 2. 调查发现

| 点 | 原代码 | 问题 |
|---|---|---|
| N-24 | `const tableData = ref<T[]>([]) as { value: T[] }`（行 52） | 为绕过 Vue 泛型 `UnwrapRef` 对 `T[]` 的类型擦除而强转，但 `as { value: T[] }` 丢掉了 `Ref` 语义（不再是 `Ref<T[]>`，无法当 ref 传递）。 |
| N-30 | `searchDefaults: Record<string, unknown>`、`reactive<Record<string, unknown>>(...)`、`fetchList: (params: Record<string, unknown>) => ...`（行 31/39/57/69） | 搜索表单整体退化为无键约束的 `Record<string, unknown>`，调用方拿不到字段级类型。 |

**调用方核验**：全前端 `useCrudForm` 仅在自身文件出现（注释示例 + 定义），**无任何 View 实际调用**（注释亦说明"7 个 CRUD View 暂未迁移到此 composable"）。故收紧其泛型签名**零破坏面**。

## 3. 改造

`frontend/src/composables/useCrudForm.ts`：

- **N-24**：`as { value: T[] }` → `as Ref<T[]>`（`import { ..., type Ref } from 'vue'`）。保留规范的"泛型 ref 强转"写法，同时不丢 `Ref` 语义。
- **N-30**：为 composable 增加第三个泛型 `S extends Record<string, unknown> = Record<string, unknown>`，表示搜索表单类型：
  - `searchDefaults: S`、`searchForm` 由 `reactive(...)` 转 `as S`；
  - `fetchList: (params: S & { page: number; size: number }) => Promise<PageResult<T>>`，列表请求参数携带字段级类型；
  - `useCrudForm<T, F, S = Record<string, unknown>>(...)`。**默认值保证现有 2 参调用法 `useCrudForm<Entity, Form>(...)` 完全向后兼容**，调用方按需传第三个泛型即可获得搜索表单类型约束。
- `handleReset` 内对动态键的读写保持局部 `as Record<string, unknown>` cast（按运行期键遍历，必要且语义清晰），不扩散。

## 4. 验证

- `cd D:\paike\frontend; npm run build`（`vue-tsc -b && vite build`）：**通过**，vue-tsc 全量类型检查 0 error，vite 构建 20.40s 完成。
  - 末尾 `ScheduleCharts` chunk 体积告警为既有项（ECharts 打包），与本次无关。

## 5. 关联文件

| 用途 | 路径 |
|---|---|
| Composable | `frontend/src/composables/useCrudForm.ts` |
| 列表分页类型 | `frontend/src/api/types.ts`（`PageResult<T>`） |
