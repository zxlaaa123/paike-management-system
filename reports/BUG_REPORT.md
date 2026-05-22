# 项目 Bug 排查报告

日期：2026-05-13

范围：后端 Spring Boot、前端 Vue/Vite、根目录 Playwright 测试配置。未修改业务代码，仅生成本报告。

说明：排查时确认本机 `5173` 和 `8090` 端口均处于监听状态，即前端开发服务和后端服务当前在运行。本文中的“失败”指构建、类型检查或自动化验收命令失败，不等同于当前开发服务无法启动。

## 验证结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `netstat -ano | findstr ":8090 :5173"` | 运行中 | `5173`、`8090` 均有监听进程。 |
| `cd backend && mvn test -q` | 通过 | 使用 Windows `cmd.exe` 执行成功。Git Bash 环境下 `mvn` 会报 `ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher`，属于本机 shell/Maven 环境问题。 |
| `cd frontend && npm run build` | 生产构建失败 | 开发服务可运行，但 `vue-tsc` 报 `TS6133`，见问题 1。 |
| `npx playwright test --reporter=line` | 未完整验证 | 22 个 API/前置用例通过；浏览器用例因本机缺少 Playwright Chromium 中断：`Executable doesn't exist ... chromium-1223 ...`。 |

## 问题 1：前端构建失败，未使用的 `watch` 导入触发 TS6133

- 严重级别：高
- 位置：`frontend/src/views/schedule/UnscheduledTaskView.vue:2`
- 现象：前端开发服务可运行，但 `npm run build` 的类型检查阶段失败。
- 证据：`src/views/schedule/UnscheduledTaskView.vue(2,36): error TS6133: 'watch' is declared but its value is never read.`
- 影响：前端无法打生产包，CI/CD 会被阻断。
- 建议：删除 `watch` 导入；如果确实需要监听筛选条件，再补充实际 `watch` 逻辑。

## 问题 2：未排任务列表先分页后内存过滤，搜索结果和总数不可靠

- 严重级别：高
- 位置：`backend/src/main/java/com/paike/scheduler/service/UnscheduledTaskService.java:36-58`
- 现象：`batchId`、`reasonType` 在数据库层过滤；`courseName`、`teacherName`、`className` 在 `selectPage` 返回当前页后才内存过滤，并把 `total` 设为当前页过滤后的数量。
- 影响：
  - 匹配项如果在其他页，不会出现在当前搜索结果中。
  - `total` 变成当前页命中数，不是全量命中数，分页控件会显示错误。
  - 用户按课程/教师/班级搜索未排任务时可能误判没有数据。
- 建议：把关联字段搜索下推到 SQL 层。可用 `JOIN teaching_task/course/teacher/class_info` 查询，或先查符合关联条件的 `task_id` 集合再分页查询 `unscheduled_task`。不要对分页后的 records 再计算全局 `total`。

## 问题 3：V2 `schedule` 扩展字段迁移脚本未纳入启动初始化

- 严重级别：高
- 位置：
  - `backend/src/main/resources/application.yml:12-15`
  - `backend/src/main/resources/db/schema.sql:116-128`
  - `backend/src/main/resources/db/v2_alter_schedule.sql:11-15`
  - `backend/src/main/java/com/paike/scheduler/entity/Schedule.java:31-33`
- 现象：`application.yml` 只加载 `schema.sql` 和 `v2_schema.sql`；`schema.sql` 中 `schedule` 表没有 `source_type`、`batch_id`；实体 `Schedule` 已包含 `sourceType`、`batchId`；补充字段在 `v2_alter_schedule.sql`，但该脚本未被 `schema-locations` 引用。
- 影响：新库或未手工迁移的库会缺少列，自动排课/排课写入或查询可能出现 `Unknown column 'source_type'` / `Unknown column 'batch_id'`。
- 建议：采用可重复执行的迁移方案。最低限度应让主 schema 中的 `schedule` 表包含 V2 字段；更稳妥是引入 Flyway/Liquibase，并把 `ALTER TABLE` 迁移做成幂等或版本化迁移。

## 问题 4：未排任务清空接口允许无 `batchId` 删除全部记录

- 严重级别：中
- 位置：
  - `backend/src/main/java/com/paike/scheduler/controller/UnscheduledTaskController.java:41-47`
  - `backend/src/main/java/com/paike/scheduler/service/UnscheduledTaskService.java:83-90`
  - `frontend/src/api/unscheduledTask.ts:35-38`
- 现象：`DELETE /unscheduled-tasks` 如果不传 `batchId`，后端调用 `clearAll()` 删除所有未排任务。前端 API 也把 `batchId` 定义为可选参数。
- 影响：一次漏传参数或误调用会清空所有批次未排任务，数据破坏范围过大。
- 建议：拆分接口语义。批次清空使用 `DELETE /unscheduled-tasks/batch/{batchId}`；全量清空需要单独管理端接口，并要求显式确认参数，例如 `confirm=true`。

## 问题 5：左侧菜单激活状态固定为 `/dashboard`

- 严重级别：低
- 位置：`frontend/src/layout/BaseLayout.vue:19`
- 现象：`<el-menu router default-active="/dashboard">` 使用固定默认值。用户刷新或直接打开 `/teachers`、`/unscheduled-tasks` 等路由时，菜单高亮仍可能停在仪表盘。
- 影响：导航状态和当前页面不一致，降低可用性。
- 建议：用当前路由驱动 active 值，例如 `:default-active="route.path"`，并在组件中引入 `useRoute()`。

## 其他观察

- 根目录 `package.json` 的 `test` 脚本仍是占位命令：`echo "Error: no test specified" && exit 1`。如果希望统一验收，建议改成 `npx playwright test` 或组合后端/前端验证命令。
- 当前工作区已有未提交变更和未跟踪目录：`backend/src/main/java/com/paike/scheduler/service/UnscheduledTaskService.java`、`frontend/src/layout/BaseLayout.vue`、`frontend/src/router/index.ts`、`frontend/src/views/schedule/AutoScheduleView.vue`、`frontend/src/views/schedule/UnscheduledTaskView.vue`、`.playwright-mcp/`、`test-results/`、`tests/test-results/`。排查时未回滚这些改动。
