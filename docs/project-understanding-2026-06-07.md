# 项目了解报告（2026-06-07）

## 1. 当前进度判断

项目当前实现主线为 V1-V5。README 写明 V1-V5 功能、V5 阶段 11 验收、bug 审计修复、M-13 到 M-19 架构收口已完成，后端 74 个测试通过，API 冒烟、Playwright E2E、前端构建均已通过。

当前 Git 分支为 `audit/frontend-consistency`，工作区干净。最近提交集中在前端一致性审查与 CRUD 表单抽象迁移：

- `SemesterView` 已迁移到 `useCrudForm`。
- `TeacherView`、`ClassInfoView`、`ClassroomView`、`CourseView` 已迁移到 `useCrudForm`。
- 已新增前端页面统一性审查文档。

结论：功能不是早期原型，已经是 V1-V5 完整系统后的整理、审计、收口阶段。

## 2. 技术栈和结构

后端：

- Spring Boot 3.3.x
- Java 17
- Maven
- MyBatis Plus
- MySQL 8
- 主要目录：`backend/src/main/java/com/paike/scheduler`

前端：

- Vue 3
- Vite
- TypeScript
- Element Plus
- Pinia
- Vue Router
- Axios
- 主要目录：`frontend/src`

CodeGraph 当前索引：

- 源码文件：457
- Java：350
- TypeScript：51
- Vue：51
- Controller 路由：159
- 总符号：8122

## 3. 已实现功能范围

V1：

- 基础数据管理
- 教学任务
- 手动排课
- 冲突检测
- 课表查询

V2：

- 自动排课
- 教师禁排
- 规则配置
- 未排任务
- 冲突报告
- 评分报告
- Excel 导出

V3：

- 学期管理
- 排课方案
- 多方案生成
- 评分明细
- 方案对比
- 方案应用
- 历史回滚
- 排课日志

V4：

- 排课质量分析
- 风险诊断
- 图表
- 局部调整
- 课程锁定
- 报告导出
- AI 辅助分析

V5：

- 智能修复建议
- 候选位置推荐
- 试算方案
- 局部重排
- 优化前后对比
- 一致性检查
- AI 修复解释
- 最终回归验收

## 4. 前端路由覆盖

当前前端路由覆盖：

- 登录和首页：`/login`、`/`
- 基础数据：教师、班级、教室、课程、教学任务、学期
- V2/V3：自动排课、未排任务、冲突报告、评分、方案、方案生成、规则权重、统计
- V4：分析概览、详情、评分、风险、图表、锁定、报告、AI 分析
- V5：修复任务、修复详情、试算详情、候选位置
- 课表：班级课表、教师课表、教室课表

结论：前端不是只有 CRUD 页面，V3-V5 的分析、修复、试算链路已有页面入口。

## 5. docs 文档现状

`docs` 文档量较大，覆盖：

- `docs/v1` 到 `docs/v5`：版本需求、设计、接口、测试、验收
- `docs/v6`：下一阶段草案
- `docs/archive`：调查、审查、计划、交接、日志归档
- 根目录文档：测试报告、修复记录、代码设计审查、前端一致性审查、V1-V5 后续建议

关键判断：

- `docs/v6` 不是当前已实现产品版本。
- `docs/v6/V6_10_现状校准与执行建议.md` 是 2026-05-26 的现状校准文档，说明当时产品功能线实际做到 V1-V5；数据库脚本里的 `v6_*.sql`、`v7_*.sql`、`v8_*.sql` 是迁移脚本编号，不等于产品版本已完整进入 V6。
- 但 Git 历史显示 2026-05-26 到 2026-05-27 已经做过一点 V6：审计日志最小闭环和课程锁定审计日志。当前代码已有 `/api/v6/audit-logs`、`system_audit_log`、`SystemAuditLogService`、`SystemAuditLogController`。
- 当前 V6 仍不是完整版本：回归测试中心、数据一致性检查中心、性能基线、完整错误码体系等草案能力未实现。

## 6. 测试和质量状态

已知验证状态：

- README：后端 74 测试通过。
- 本地统计：`backend/src/test` 下有 74 个 Java 测试文件。
- E2E：`tests` 下有 3 个 Playwright spec。
- 历史测试报告显示基础数据 CRUD、教学任务、手动排课、冲突检测、课表查询、自动排课、控制台错误检查均通过。

历史仍记录的已知问题：

- 首页统计仍为占位页。
- Element Plus 弹窗内 `el-select` 对 Playwright 直接点击不友好，人工操作基本可用。
- 控制台存在 Element Plus `el-radio` label-as-value 废弃 API warning。

注意：本次只做了解和文档校准，没有重新执行 Maven、前端构建或 E2E。

## 7. 当前最合理的下一步

短期主线建议：

1. 继续当前分支目标：前端一致性收口。
   - 已完成 5 个基础 CRUD 页面的 `useCrudForm` 迁移。
   - 下一批适合迁移：`SemesterView` 已做，剩余重点看 `TeacherUnavailableTimeView`、`TeachingTaskView`。
   - 排课、V4、V5 页面先统一交互规范，不建议强行抽象。

2. 修掉低风险前端遗留项。
   - 首页统计占位接入真实数据。
   - `el-radio` 废弃 API warning 改为 `value`。
   - Playwright 选择器按 Element Plus teleported 下拉行为优化测试写法。

3. 若要继续 V6，优先补完审计日志最小闭环。
   - 已有 `system_audit_log`、审计服务、审计查询接口。
   - 已接入方案应用/回滚、课程锁定/解除锁定，且后续修复过方案应用失败审计。
   - 下一步应核对手动排课/调整、应用试算方案等关键写操作是否已接入审计。
   - 前端若未做，可补简单审计日志列表。

4. 暂缓大工程。
   - 完整回归测试中心。
   - 完整数据一致性检查中心。
   - 性能基线趋势页面。
   - Flyway/Liquibase 迁移体系切换。
   - 全量错误码体系重构。

## 8. V6 审计日志专项校准

Git 历史确认 V6 已做过一小段，然后进入 bug/security/perf/stability 修复阶段：

- `2f6848e`（2026-05-26）：`feat(v6): 建立审计日志最小闭环`
- `51b65ce`（2026-05-27）：`feat(v6): 记录课程锁定审计日志`
- `af413be`（2026-05-28）：`fix(audit): 记录方案应用失败审计`

当前已实现：

- 表：`system_audit_log`
- SQL：`backend/src/main/resources/db/v12_system_audit_log.sql`
- Entity：`SystemAuditLog`
- Mapper：`SystemAuditLogMapper`
- Service：`SystemAuditLogService`
- Controller：`SystemAuditLogController`
- 后端接口：`GET /api/v6/audit-logs`、`GET /api/v6/audit-logs/{id}`
- 测试：`SystemAuditLogServiceTest`

当前已接入审计的业务服务：

| 服务 | 审计覆盖 |
|---|---|
| `SchedulePlanService` | 方案应用、方案回滚、方案应用失败审计 |
| `V4ScheduleLockService` | 锁定、解除锁定 |

当前未接入审计、但属于 V6 审计规划里的关键写操作：

| 服务 | 关键写操作 | 状态 |
|---|---|---|
| `ScheduleService` | 手动排课新增、删除 | 未接入审计 |
| `V4ScheduleAdjustmentService` | V4 局部调整应用 | 未接入审计 |
| `V4ScheduleReplanService` | V4 局部重排方案创建 | 未接入审计 |
| `V5SimulationService` | 试算生成、局部重排、应用试算方案 | 未接入审计 |
| `AutoScheduleService` | 自动排课批次生成正式排课 | 未接入审计 |
| `V5RepairSuggestionService` | 修复建议生成、标记试算、任务建议状态更新 | 未接入审计 |

当前前端状态：

- `docs/v6/V6_05_前端页面设计.md` 规划了 `/v6/audit-logs` 审计日志页。
- 当前 `frontend/src` 未发现审计日志 API、路由或页面实现。

推荐继续顺序：

1. 后端先补审计覆盖，不先做大页面。
2. 第一批补正式课表写入路径：`ScheduleService`、`V4ScheduleAdjustmentService`、`V5SimulationService.apply`。
3. 第二批补方案/试算生成类路径：`V4ScheduleReplanService`、`V5SimulationService.generate/localReplan`、`AutoScheduleService`。
4. 第三批补前端只读审计日志列表：筛选 action、semester、plan、success、createdAt。
5. 每补一类写路径，增加对应单测验证 `recordSuccess` / `recordFailure`。

## 9. 风险边界

后续开发必须守住：

- 正式课表写入必须由用户显式触发，并由后端二次校验。
- 自动排课、局部重排、AI 修复不能绕过后端规则直接改正式课表。
- 所有学期、方案、评分、风险、日志、报告必须保持 `semester_id` 和 `plan_id` 隔离。
- 锁定课程不能被移动、删除、替换教室、替换时间、替换教师。
- AI 只能解释、建议、辅助排序，不能生成不可追踪、不可复现的写入结果。
