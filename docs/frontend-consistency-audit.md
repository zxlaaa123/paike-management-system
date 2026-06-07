# 前端页面统一性审查

审查分支：`audit/frontend-consistency`

## 结论

当前前端页面已经形成了基础统一模式，但只覆盖了一部分 CRUD 页面。

- 视图文件：44 个 Vue 视图
- 表格页面：30 个
- 表单页面：23 个
- 弹窗表单页面：10 个
- 分页页面：14 个
- 已使用 `useCrudForm` 的页面：4 个

已统一页面：

- `frontend/src/views/teacher/TeacherView.vue`
- `frontend/src/views/classInfo/ClassInfoView.vue`
- `frontend/src/views/classroom/ClassroomView.vue`
- `frontend/src/views/course/CourseView.vue`

## P1：适合继续迁移到 useCrudForm

这些页面和已统一 CRUD 页面结构最接近，风险较低。

### SemesterView

位置：`frontend/src/views/semester/SemesterView.vue`

重复逻辑：

- `fetchData`：第 48 行
- `handleSearch`：第 64 行
- `handleReset`：第 69 行
- `openAdd`：第 75 行
- `openEdit`：第 88 行
- `handleSubmit`：第 101 行
- `handleDelete`：第 120 行

保留页面专属逻辑：

- `handleSetCurrent`
- `statusTagType`
- 当前学期禁止删除/设置按钮状态

### TeacherUnavailableTimeView

位置：`frontend/src/views/teacher/TeacherUnavailableTimeView.vue`

重复逻辑：

- `fetchData`：第 52 行
- `handleSearch`：第 67 行
- `handleReset`：第 72 行
- `openAdd`：第 90 行
- `openEdit`：第 102 行
- `handleSubmit`：第 113 行
- `handleDelete`：第 132 行

保留页面专属逻辑：

- `loadOptions`
- `handleStatusChange`
- 教师/时间段选项加载

### TeachingTaskView

位置：`frontend/src/views/teachingTask/TeachingTaskView.vue`

重复逻辑：

- `fetchData`：第 65 行
- `handleSearch`：第 103 行
- `handleReset`：第 108 行
- `openAdd`：第 117 行
- `openEdit`：第 134 行
- `handleSubmit`：第 147 行
- `handleDelete`：第 166 行

保留页面专属逻辑：

- `fetchOptions`
- `needContinuousText`
- `getSemesterName`
- 当前学期判断
- 课程、教师、班级、学期选项加载

## P2：先统一交互规范，不建议立即抽象

这些页面业务行为更复杂，直接迁移到 `useCrudForm` 风险较高。

- `frontend/src/views/schedule/ScheduleView.vue`
- `frontend/src/views/schedule/SchedulePlanView.vue`
- `frontend/src/views/schedule/SchedulePlanDetailView.vue`
- `frontend/src/views/schedule/ScheduleScoreReportView.vue`
- `frontend/src/views/schedule/UnscheduledTaskView.vue`
- `frontend/src/views/v4/ScheduleRiskCenter.vue`
- `frontend/src/views/v5/RepairTaskListView.vue`
- `frontend/src/views/v5/RepairTaskDetailView.vue`

建议先统一：

- 搜索按钮顺序：`搜索`、`重置`
- 删除确认弹窗文案
- 空状态
- 分页配置：`[10, 20, 50]`
- 表格卡片类名：`table-card`
- 搜索卡片类名：`search-card`
- 成功/失败提示走 `extractMessage`

## P3：视觉结构统一候选

多页面使用了不同页面容器和头部类名：

- 常见：`page-container`
- 其他：`page`、`v4-page`、`detail-page`、`dashboard-page`、`score-page`
- 常见卡片：`search-card`、`table-card`
- 常见头部：`card-header`

建议后续建立轻量页面规范，不急于新增组件：

- 管理页统一使用 `page-container`
- 搜索区域统一 `search-card`
- 表格区域统一 `table-card`
- 卡片标题操作区统一 `card-header`
- 详情/分析类页面可以保留专属结构

## 不建议现在做

- 不建议把所有排课、V4、V5 页面强行迁移到 `useCrudForm`
- 不建议新增大型 UI 框架组件层
- 不建议一次性重构所有页面样式

原因：排课、分析、修复任务页面业务流复杂，抽象收益不稳定，回归风险高。

## 建议执行顺序

1. 迁移 `SemesterView` 到 `useCrudForm`
2. 迁移 `TeacherUnavailableTimeView` 到 `useCrudForm`
3. 迁移 `TeachingTaskView` 到 `useCrudForm`
4. 对排课/V4/V5 页面只做按钮顺序、空状态、分页和错误提示统一
5. 运行前端 build/lint
6. 再决定是否抽取更高层的 `CrudPage` 组件
