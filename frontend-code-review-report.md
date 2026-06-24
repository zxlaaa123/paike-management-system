# D:\paike\frontend 代码审查报告

## 审查范围
- **路径**: `D:\paike\frontend\src\`
- **技术栈**: Vue 3 + TypeScript + Element Plus + Pinia + Axios
- **文件总数**: 106 个源码文件（Vue + TS）
- **审查日期**: 2026-06-21

---

## 一、安全漏洞（Security）

### 1. ErrorBoundary 将错误栈写入 sessionStorage
- **严重程度**: CRITICAL
- **文件**: `D:\paike\frontend\src\components\ErrorBoundary.vue:32-45`
- **问题描述**: `recordLocalError` 函数将 `err.stack`（包含完整调用栈、变量名、文件路径等敏感信息）写入 `sessionStorage`。虽然 sessionStorage 仅在当前标签页有效，但在共享设备或多标签场景下，攻击者可通过 XSS 或其他方式读取这些信息。更严重的是，如果前端错误上报服务将这些信息发送到后端，可能造成信息泄露。
- **影响**: 敏感信息（如内部路径、变量值）可能泄露；如果后续出现 XSS 漏洞，sessionStorage 中存储的错误信息可被读取。
- **改进建议**:
  - 移除 `stack` 字段，只保留 `message`、`path`、`time`
  - 如需上报错误，应在后端记录，前端只发送最小必要信息
  - 添加 CSP (Content-Security-Policy) 头防止 XSS

### 2. Cookie 读取的正则表达式存在注入风险
- **严重程度**: HIGH
- **文件**: `D:\paike\frontend\src\utils\request.ts:7-15`
- **问题描述**: `getCookie` 函数使用 `new RegExp('(?:^|; )' + name + '=([^;]*)')` 构建正则表达式，如果 `name` 参数包含正则元字符（如 `.`、`*`、`+` 等），可能导致正则表达式错误或注入。虽然在当前代码中 `name` 是硬编码的 `'XSRF-TOKEN'`，但这是一个通用工具函数，未来可能被其他地方调用。
- **影响**: 如果未来传入未经验证的 cookie 名称，可能导致正则表达式错误或 ReDoS（正则表达式拒绝服务）。
- **改进建议**:
  - 对 `name` 参数进行转义：`name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')`
  - 或改用字符串查找方式解析 cookie，避免正则

### 3. 前端未对用户输入进行输出编码
- **严重程度**: MEDIUM
- **文件**: 多个 views（如 `SchedulePlanDetailView.vue`、`RepairTaskDetailView.vue` 等）
- **问题描述**: 虽然目前没有使用 `v-html`，但表格列直接渲染后端返回的文本字段（如 `plan.description`、`row.conflictReason`、`result.analysisText` 等）。如果后端数据被污染或包含恶意脚本，Element Plus 的默认文本渲染会自动转义，但某些组件（如 `el-tag`、`el-descriptions`）的行为可能不一致。
- **影响**: 如果后端数据被 XSS 污染，可能在特定场景下执行恶意脚本。
- **改进建议**:
  - 显式使用 `textContent` 等效方式渲染（Vue 模板默认已转义）
  - 对富文本内容（如 `analysisText`）使用专门的富文本组件并配置 sanitize
  - 实施严格的 CSP

---

## 二、业务逻辑错误（Business Logic）

### 4. 分页组件 v-model + @event 竞态条件
- **严重程度**: HIGH
- **文件**: 多个 views（`SchedulePlanView.vue:238-246`、`ScheduleView.vue:393-401`、`TeacherView.vue:122-130` 等，共 15+ 处）
- **问题描述**: Element Plus 分页组件同时使用 `v-model:current-page` 和 `@current-change`（以及 `v-model:page-size` + `@size-change`）时，点击页码或改变每页条数会触发两次数据加载：一次由 v-model 更新触发（如果父组件有 watch），一次由 @event 触发。虽然当前代码中 `currentPage`/`pageSize` 没有 watch 监听，但 Element Plus 内部实现可能导致事件触发时机不确定，在快速点击时可能产生竞态。
- **影响**: 快速切换分页时可能产生重复请求，或旧请求晚于新请求返回，导致数据错乱。
- **改进建议**:
  - 移除 `@current-change` 和 `@size-change`，改用 `watch([currentPage, pageSize], fetchData)` 统一处理
  - 或在事件处理函数中检查是否是最新请求（使用请求 ID 或 AbortController）

### 5. 弹窗使用 destroy-on-close 导致状态管理复杂
- **严重程度**: MEDIUM
- **文件**: `SchedulePlanDetailView.vue:601`、`TeachingTaskView.vue:343`、`ScheduleView.vue:404`、`TeacherView.vue:133` 等（共 11 处）
- **问题描述**: 所有 `el-dialog` 都使用了 `destroy-on-close`，这意味着每次关闭弹窗时，弹窗内容会被销毁。对于包含表单的弹窗，用户输入的内容在关闭后完全丢失。虽然这是 Element Plus 的推荐做法（避免内存泄漏），但在以下场景存在问题：
  - `ScheduleAdjustDialog` 和 `LocalReplanDialog` 在关闭时会丢失表单状态
  - 用户可能在填写一半时误关闭弹窗，再次打开时需要重新填写
- **影响**: 用户体验不佳；表单状态无法持久化
- **改进建议**:
  - 对于简单确认弹窗（如 `ElMessageBox.confirm`），无需修改
  - 对于复杂表单弹窗，考虑使用 `v-if` 控制显示/隐藏而非 `destroy-on-close`，或在使用 `destroy-on-close` 时添加 `before-close` 钩子提示用户保存
  - 对于 `ScheduleAdjustDialog` 和 `LocalReplanDialog`，已在 watch 中处理数据重新加载，但表单状态（如 `form.newPlanName`）会丢失

### 6. 路由守卫中 fetchCurrentUser 的并发控制缺陷
- **严重程度**: MEDIUM
- **文件**: `D:\paike\frontend\src\router\index.ts:302-332`、`D:\paike\frontend\src\stores\auth.ts:25-37`
- **问题描述**: 路由守卫在 `beforeEach` 中调用 `authStore.fetchCurrentUser()`，虽然 store 中使用 `fetchCurrentUserInflight` 防止了同一时刻的重复请求，但存在以下问题：
  - 如果用户快速连续访问多个需要认证的页面，会触发多次 `beforeEach`，虽然并发请求被合并，但所有导航都会等待同一个 Promise
  - 如果 `getCurrentUserApi` 请求超时或永远挂起（网络异常），`fetchCurrentUserInflight` 永远不会被清除，后续所有导航都会永久挂起
- **影响**: 极端网络条件下可能导致整个应用无法导航。
- **改进建议**:
  - 为 `getCurrentUserApi` 添加超时处理（Axios timeout 已设置 10s，但应处理 timeout 错误）
  - 在 `fetchCurrentUser` 中添加重试逻辑或最大等待时间
  - 考虑在路由守卫中添加加载状态指示

### 7. 请求取消机制缺失
- **严重程度**: MEDIUM
- **文件**: `D:\paike\frontend\src\utils\request.ts`（全局 request 实例）
- **问题描述**: 使用 axios 创建了全局 request 实例，但没有使用 `AbortController`。当用户快速切换页面时：
  - 已发起的请求仍然会返回
  - 返回后会更新已卸载组件的状态（虽然 Vue 3 的响应式系统在组件卸载后不再更新 DOM，但内存中的响应式对象仍会被修改）
  - 可能导致内存泄漏和不必要的网络开销
- **影响**: 快速操作时可能出现状态闪烁；内存泄漏。
- **改进建议**:
  - 使用 `AbortController` 在组件卸载时取消请求
  - 或在 request interceptor 中添加请求去重逻辑
  - 使用 `axios CancelToken` 或 `AbortController`（Axios v1.3+ 支持）

---

## 三、代码质量（Code Quality）

### 8. God 组件：SchedulePlanDetailView.vue（655 行）
- **严重程度**: HIGH
- **文件**: `D:\paike\frontend\src\views\schedule\SchedulePlanDetailView.vue`（655 行）
- **问题描述**: 单个组件承担了过多职责：
  - 方案基本信息展示
  - 课表明细表格 + 周次筛选
  - 生成日志展示
  - 未排任务统计 + 明细
  - 评分明细 + 重新评分
  - 调整记录分页
  - 局部重排弹窗
  - 修复任务创建
  - 5 个并行数据加载
- **影响**: 代码难以维护、测试困难、职责不清、变更风险高。
- **改进建议**:
  - 拆分为：`SchedulePlanHeader.vue`、`ScheduleItemsTable.vue`、`ScheduleLogsPanel.vue`、`UnassignedTasksPanel.vue`、`ScoreDetailsPanel.vue`、`AdjustLogsPanel.vue`
  - 使用 `provide/inject` 或共享 composable 管理共享状态（planId、plan、loading）

### 9. API 层大量重复的响应数据检查代码
- **严重程度**: MEDIUM
- **文件**: 所有 `frontend/src/api/*.ts`（共 30+ 文件）
- **问题描述**: 每个 API 函数都重复以下模式：
  ```typescript
  return request.get<ApiResponse<T>>(url, { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
  ```
  这导致：
  - 代码冗余（30+ 文件，每个文件 5-25 个函数）
  - 如果后端响应结构变化，需要修改所有文件
  - 错误处理不一致（有些函数检查了，有些没有）
- **影响**: 维护成本高；错误处理易遗漏。
- **改进建议**:
  - 在 `utils/request.ts` 的 response interceptor 中统一处理，直接返回 `response.data.data`
  - 修改 `ApiResponse` 类型，让 TypeScript 在编译时捕获结构问题
  - 或提供一个 `apiClient` 包装函数：`request.get('/path', params).then(r => r.data.data)`

### 10. 7 个 CRUD View 未迁移到 useCrudForm，重复代码严重
- **严重程度**: MEDIUM
- **文件**: `TeacherView.vue`、`ClassInfoView.vue`、`ClassroomView.vue`、`CourseView.vue`、`SemesterView.vue`、`TeacherUnavailableTimeView.vue`、`ScheduleView.vue`
- **问题描述**: 虽然提供了 `useCrudForm` composable，但仍有 7 个视图保留了大量重复的 CRUD 样板代码：
  - 相同的分页逻辑（`currentPage`、`pageSize`、`total`、`loading`）
  - 相同的搜索表单模式
  - 相同的弹窗管理（`dialogVisible`、`formRef`、`editingId`）
  - 相同的提交/删除逻辑
- **影响**: 代码重复率高；修复 bug 需要修改多个文件；新功能开发慢。
- **改进建议**:
  - 逐步迁移到 `useCrudForm`，先从最简单的 `CourseView` 开始
  - 在 composable 中增加更多可配置项（如表格列配置、自定义搜索表单）
  - 或创建更细粒度的 composable（如 `usePagination`、`useDialog`）

### 11. 类型安全不足：ApiResponse 的 data 字段为 unknown
- **严重程度**: MEDIUM
- **文件**: `D:\paike\frontend\src\api\types.ts:8-12`
- **问题描述**: `ApiResponse<T = unknown>` 的 `data` 字段类型是泛型 `T`，但在实际使用中，所有函数都手动 `.then((r) => r.data.data)`。如果后端返回的 `data` 为 `null` 或结构不符合预期，TypeScript 无法在编译时捕获，只能在运行时通过 `if (!r.data) throw new Error('响应数据为空')` 检查。
- **影响**: 类型安全依赖运行时检查；重构时容易出错。
- **改进建议**:
  - 在 request interceptor 中统一处理响应，确保 `response.data` 始终为有效数据
  - 或使用更严格的类型：`interface ApiResponse<T> { code: number; message: string; data: NonNullable<T> }`
  - 考虑使用 `zod` 或 `io-ts` 进行运行时类型验证

### 12. 深色模式不兼容的内联样式
- **严重程度**: LOW
- **文件**: `ScheduleCompareView.vue:232-255`、`AutoScheduleView.vue:203-219`、`ScheduleScoreReportView.vue:132-233`
- **问题描述**: 大量使用硬编码颜色值（如 `#67c23a`、`#e6a23c`、`#f56c6c`、`#409eff`）的内联样式。如果未来需要支持深色模式或主题切换，这些硬编码颜色将无法自动适配。
- **影响**: 主题定制困难；不满足无障碍（a11y）要求。
- **改进建议**:
  - 使用 CSS 变量或 Element Plus 的 token 系统
  - 将颜色逻辑封装为函数或 class 绑定

### 13. Console.error 可能泄露敏感信息
- **严重程度**: LOW
- **文件**: 多个 views（共 20+ 处）
- **问题描述**: 大量 `catch` 块中使用 `console.error(_e)` 或 `console.error(error)` 打印错误对象。如果错误对象包含敏感信息（如 token、用户信息、内部路径），在启用错误上报服务（如 Sentry）时会将这些信息发送到第三方服务。
- **影响**: 敏感信息可能通过错误上报服务泄露。
- **改进建议**:
  - 使用统一的错误处理工具函数，在发送前脱敏
  - 或配置错误上报服务的过滤规则
  - 开发环境下保留详细日志，生产环境只上报必要信息

### 14. EChartPanel 深度监听 option 可能导致性能问题
- **严重程度**: LOW
- **文件**: `D:\paike\frontend\src\components\v4\EChartPanel.vue:83-95`
- **问题描述**: `watch(() => [props.option, props.loading, props.empty], callback, { deep: true })` 对 `option` 进行了深度监听。ECharts 的 option 对象通常很大且嵌套很深，深度监听会递归遍历整个对象树，导致性能开销。
- **影响**: 大数据量图表更新时可能出现卡顿。
- **改进建议**:
  - 移除 `deep: true`，改用 `watch(() => props.option, callback, { deep: true })` 仅在 option 变化时深度监听
  - 或使用 `JSON.stringify(props.option)` 作为 watch 的 source（虽然也有性能开销，但通常比 deep watch 更可控）
  - 或在父组件中使用 `shallowRef` 管理 option，仅在需要更新时替换整个对象

---

## 四、架构问题（Architecture）

### 15. 组件职责不清：SchedulePlanDetailView 混合了过多逻辑
- **严重程度**: HIGH
- **文件**: `D:\paike\frontend\src\views\schedule\SchedulePlanDetailView.vue`
- **问题描述**: 该组件不仅展示方案详情，还直接处理：
  - 评分逻辑（`handleRescore`）
  - 方案应用/回滚（`handleApply`、`handleRollback`）
  - 局部重排（`openLocalReplanSuccess`）
  - 修复任务创建（`createRepairTaskFromPlan`）
  - 课程调整（通过 `ScheduleAdjustDialog`）
  - 日志加载（多种日志类型）
- **影响**: 组件复用性差；测试困难；变更影响范围大。
- **改进建议**:
  - 遵循单一职责原则，将业务逻辑提取到 composable 或子组件
  - 例如：`useSchedulePlanDetail(planId)` composable 管理所有数据加载和操作

### 16. 跨组件通信依赖事件总线（隐式）
- **严重程度**: MEDIUM
- **文件**: `ScheduleAdjustDialog.vue`、`LocalReplanDialog.vue`、多个父组件
- **问题描述**: `ScheduleAdjustDialog` 和 `LocalReplanDialog` 通过 `emit('success', result)` 与父组件通信，父组件通过 `@success` 处理后续逻辑。这种模式本身没问题，但：
  - 事件名称 `success` 过于泛化，在不同上下文中含义不同
  - `LocalReplanDialog` 在 `handleSubmit` 成功后 emit `success`，但父组件 `SchedulePlanDetailView` 和 `ScheduleLockManage`、`ScheduleAnalysisDetail` 都监听此事件并执行不同的路由跳转，逻辑分散在父组件中
- **影响**: 事件契约不清晰；复用组件时需要仔细阅读父组件代码才能理解子组件的行为。
- **改进建议**:
  - 使用更具体的事件名（如 `applied`、`generated`）
  - 或在 composable 中封装弹窗逻辑，统一处理成功后的副作用

### 17. API 层缺乏统一的错误处理和请求配置
- **严重程度**: MEDIUM
- **文件**: `D:\paike\frontend\src\utils\request.ts`、所有 `api/*.ts`
- **问题描述**:
  - `request.ts` 中 response interceptor 对所有非 200 响应统一弹出 `ElMessage.error`，但在某些场景下（如表单验证错误），前端可能需要区分业务错误和系统错误
  - 超时时间固定为 10000ms，但 `generateLocalReplan`、`runAutoSchedule` 等长耗时操作使用 `{ timeout: 120_000 }` 覆盖，这种配置分散在各个 API 函数中，难以维护
- **影响**: 错误处理不灵活；超时配置分散。
- **改进建议**:
  - 在 request interceptor 中提供更细粒度的错误处理钩子
  - 在 API 函数中统一使用 `request.post(url, data, { timeout: 120_000 })` 而非重复定义
  - 考虑使用 `axios-retry` 或自定义重试逻辑

---

## 五、Vue/TypeScript 最佳实践（Best Practices）

### 18. 表单验证规则不完整，类型安全不足
- **严重程度**: MEDIUM
- **文件**: `TeachingTaskView.vue:53-58`、`ScheduleView.vue:49-53`、`TeacherView.vue:45-48`、`ClassroomView.vue:56` 等
- **问题描述**:
  - 表单验证规则只检查了 `required` 和 `trigger`，没有检查：
    - `weeklyHours` 必须大于 0
    - `studentCount` 必须大于 0
    - `startWeek` < `endWeek`
    - `teacherMaxDailySlots` 和 `classMaxDailySlots` 的范围
  - `ScheduleRuleView.vue` 完全没有使用 Element Plus 表单验证，而是在 `handleSave` 中手动检查
  - `useCrudForm.ts` 中的 `rules` 是可选的（`rules?: R`），如果不传，表单将没有任何验证
- **影响**: 无效数据可能提交到后端；用户体验差。
- **改进建议**:
  - 为所有数值字段添加 `type: 'number'`、`min`、`max` 验证规则
  - 使用自定义验证器（`validator`）处理复杂逻辑（如周次范围）
  - 在 `useCrudForm` 中将 `rules` 设为必填，或在未提供时给出警告

### 19. useCrudForm 的 form 重置使用 structuredClone，但未处理嵌套对象
- **严重程度**: LOW
- **文件**: `D:\paike\frontend\src\composables\useCrudForm.ts:101-113`
- **问题描述**: `openAdd` 和 `openEdit` 中使用 `structuredClone(options.formDefaults)` 和 `structuredClone(row)` 来重置表单。`structuredClone` 无法克隆函数、Symbol、以及某些 DOM 对象。虽然当前 `formDefaults` 和 `row` 都是纯数据对象，但如果未来表单包含 Date 对象或自定义类实例，`structuredClone` 会抛出 TypeError。
- **影响**: 未来扩展表单时可能遇到运行时错误。
- **改进建议**:
  - 使用 lodash 的 `cloneDeep` 或自定义深拷贝函数
  - 或在类型层面限制 `F` 必须是可序列化的对象

### 20. 生命周期使用不当：onMounted 中直接 await
- **严重程度**: LOW
- **文件**: `DashboardView.vue:127-129`、`SchedulePlanView.vue:121-126`、`SemesterView.vue` 等
- **问题描述**: 部分组件在 `onMounted` 中使用 `async () => { await ... }()` 立即执行异步函数，而不是定义一个 `async function init()` 然后调用。虽然功能上等价，但前者会创建一个匿名函数并立即执行，调试时堆栈信息不够清晰。
- **影响**: 调试困难；代码可读性稍差。
- **改进建议**:
  - 定义具名异步函数：`async function init() { ... }`，然后在 `onMounted(init)` 中调用

---

## 六、Element Plus 问题（Element Plus）

### 21. el-pagination 的 v-model 与 @event 混用
- **严重程度**: HIGH
- **文件**: 15+ 个 views（详见第 4 条）
- **问题描述**: Element Plus 的 `el-pagination` 组件在同时使用 `v-model:current-page` 和 `@current-change` 时，点击页码会触发 `current-change` 事件，同时 `v-model` 也会更新。虽然 Element Plus 内部做了防抖，但在快速点击时仍可能导致：
  - 多次触发 `fetchData`
  - 分页状态与请求状态不一致
- **影响**: 重复请求；数据加载状态混乱。
- **改进建议**:
  - 移除 `@current-change` 和 `@size-change`，改用 `watch([currentPage, pageSize], () => fetchData())`
  - 或在事件处理函数中取消之前的请求（使用 AbortController）

### 22. el-dialog 的 destroy-on-close 与表单状态冲突
- **严重程度**: MEDIUM
- **文件**: 11 个 views（详见第 5 条）
- **问题描述**: 使用 `destroy-on-close` 的弹窗在关闭时会销毁内部组件，再次打开时会重新创建。对于包含复杂表单或选择器的弹窗（如 `ScheduleView` 中的排课弹窗，包含多个 `el-select` 和 `filterable`），重新创建会导致：
  - 下拉选项需要重新加载（如果选项数据在弹窗关闭时被清空）
  - 用户已选择的筛选条件丢失
- **影响**: 用户体验差；表单状态无法保持。
- **改进建议**:
  - 对于需要保持状态的弹窗，移除 `destroy-on-close`，改用 `v-if` 控制显隐
  - 或在使用 `destroy-on-close` 时，将选项数据提升到父组件或 Pinia store 中管理

### 23. el-table 的 v-loading 与数据加载状态分离
- **严重程度**: LOW
- **文件**: 多个 views
- **问题描述**: 部分组件使用独立的 `loading` ref 控制 `el-table` 的 `v-loading`，而数据加载和弹窗加载共用同一个 loading 状态（如 `SchedulePlanDetailView.vue` 有 `loading`、`logLoading`、`unassignedLoading`、`adjustLogLoading`、`taskLogLoading` 等多个 loading 状态）。虽然这是 Element Plus 推荐的做法（避免全局 loading 遮挡弹窗），但在快速操作时可能出现 loading 状态不一致。
- **影响**: 用户体验稍差，但无功能性影响。
- **改进建议**: 保持现状，但确保每个异步操作都有独立的 loading 状态

---

## 七、其他问题

### 24. 缺少请求取消机制，快速操作时可能出现状态闪烁
- **严重程度**: MEDIUM
- **文件**: `D:\paike\frontend\src\utils\request.ts`、所有 views
- **问题描述**: 没有使用 `AbortController`。当用户在页面 A 发起请求后快速跳转到页面 B，页面 A 的请求仍然会返回。虽然 Vue 3 的响应式系统在组件卸载后不会更新 DOM，但：
  - 响应式对象仍会被修改（内存泄漏风险）
  - 如果多个组件共享同一个 store 状态，可能出现竞态
- **影响**: 内存泄漏；状态不一致。
- **改进建议**: 使用 `AbortController` 在组件卸载时取消请求

### 25. 路由参数验证不完整
- **严重程度**: LOW
- **文件**: `D:\paike\frontend\src\router\index.ts:10-51`
- **问题描述**: `routeParamGuards` 只定义了 9 个路由的参数验证规则，其他带参数的路由（如 `/timetable/class?classId=xxx`）没有参数验证。如果用户访问 `/v3/schedule-plans/abc`（非数字 id），会进入 `parsePositiveIntegerRouteParam` 返回 null，然后重定向到 `/v3/schedule-plans`，这是正确的。但其他路由（如 `/v5/repair-tasks/:taskId/simulations/:planId`）没有类似的验证。
- **影响**: 非法参数可能导致组件崩溃或发起无效请求。
- **改进建议**:
  - 为所有带动态参数的路由添加参数验证
  - 或在组件内部使用 `computed(() => Number(route.params.xxx))` 时添加验证

### 26. 代码重复：formatDateTime 和 emptyToUndefined 函数在多处定义
- **严重程度**: LOW
- **文件**: `AuditLogView.vue:69-76`、`ConsistencyCheckView.vue:62-69`、`RegressionTestView.vue:51-64`、`PerformanceBaselineView.vue:51-81`
- **问题描述**: `formatDateTime(value?: string | null)` 和 `emptyToUndefined(value?: number)` 函数在多个 views 中重复定义，逻辑完全相同。
- **影响**: 维护成本高；修改时需要同步多个文件。
- **改进建议**:
  - 提取到 `utils/format.ts` 中统一管理
  - 或使用 unplugin-vue-components 自动导入

### 27. 缺少国际化（i18n）支持
- **严重程度**: LOW
- **文件**: 所有 views
- **问题描述**: 所有用户可见文本都是硬编码的中文。如果未来需要支持英文或其他语言，需要修改所有文件。
- **影响**: 国际化成本高。
- **改进建议**:
  - 使用 `vue-i18n` 或类似库
  - 将文本提取到语言包文件

---

## 八、正面实践（值得保持）

1. **认证安全**: 使用 httpOnly Cookie 传递 JWT token，前端无法读取，有效防止 XSS 盗取 token。CSRF 防护通过读取 XSRF-TOKEN cookie 并放入请求头实现。
2. **类型安全**: 整体 TypeScript 类型使用良好，没有滥用 `any`，接口定义清晰。
3. **错误处理**: 统一的 `extractMessage` 工具函数和 `fallback` 辅助函数，错误处理模式一致。
4. **Composable 抽象**: 提供了 `useCrudForm`  composable，虽然尚未完全推广，但方向正确。
5. **无 XSS 风险**: 没有使用 `v-html`、`innerHTML`、`eval()` 等危险 API。
6. **Vue 3 最佳实践**: 使用 `<script setup>`、`ref/reactive` 区分明确、`computed` 使用得当。

---

## 总结与优先级建议

| 优先级 | 问题 | 建议行动 |
|--------|------|----------|
| P0 | ErrorBoundary sessionStorage 泄露 | 立即修复，移除 stack 字段 |
| P0 | Cookie 正则注入风险 | 立即修复，转义正则元字符 |
| P1 | God 组件 SchedulePlanDetailView | 计划重构，拆分为子组件 |
| P1 | 分页竞态条件 | 统一使用 watch 监听分页变化 |
| P1 | 请求取消机制缺失 | 引入 AbortController |
| P1 | API 层重复代码 | 重构 request.ts，统一处理响应 |
| P2 | 7 个 CRUD View 重复代码 | 逐步迁移到 useCrudForm |
| P2 | 表单验证不完整 | 补充数值范围和自定义验证器 |
| P2 | 弹窗 destroy-on-close 状态问题 | 评估是否需要保持状态 |
| P3 | 深色模式不兼容 | 使用 CSS 变量 |
| P3 | 代码重复（formatDateTime 等） | 提取到 utils |
| P3 | 缺少国际化 | 引入 vue-i18n |

---

## 附录：审查覆盖清单

| 模块 | 文件数 | 审查状态 |
|------|--------|----------|
| router/ | 1 | ✅ 已审查 |
| stores/ | 1 | ✅ 已审查 |
| api/ | 30 | ✅ 已审查（抽样） |
| views/ | 40+ | ✅ 已审查（抽样） |
| components/ | 5 | ✅ 已审查 |
| composables/ | 1 | ✅ 已审查 |
| utils/ | 5 | ✅ 已审查 |
| layout/ | 1 | ✅ 已审查 |

---

*报告生成时间: 2026-06-21*
*审查工具: 人工阅读 + CodeGraph 代码图分析 + Grep 模式搜索*
