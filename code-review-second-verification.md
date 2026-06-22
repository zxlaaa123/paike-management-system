# 代码审查报告二次验证

验证时间：2026-06-22

验证对象：

- `code-review-report.md`
- `frontend-code-review-report.md`
- `CODE_REVIEW_REPORT.md`
- `code-review-report-verification.md`
- `code-review-comprehensive-verification.md`

## 总结

已有核实报告的大方向成立：原始报告里确实有一批真实问题，但严重级别和技术表述需要修正。最关键的纠错仍是 MyBatis-Plus 逻辑删除：`BaseMapper.delete(Wrapper)` 在实体配置 `@TableLogic` 且全局逻辑删除开启时，不应被描述为“绕过软删除的物理删除”。

本次二次验证结果：

- 属实或基本属实：15 项
- 部分属实 / 需降级：7 项
- 误报或技术论断错误：2 项
- 待人工复核：1 项

## P0/P1 建议修复项

### 1. 手动排课未写入 V10 周段字段

结论：属实。优先级：P0。**状态：✅ 已修复（2026-06-22，未提交）**

修复内容：
- `ScheduleService.create` 从 `task` 透传 `weekType`/`startWeek`/`endWeek`
- `SchedulePlanService.applyPlanInternal` 同步补齐 `startWeek`/`endWeek` 透传（原仅 `weekType`）
- `ScheduleServiceAuditTest` 新增 ODD/3/15 断言
- `SchedulePlanServiceTest` 新增 ODD/3/15 断言
- 测试验证：ScheduleServiceAuditTest 6/6 + SchedulePlanServiceTest 10/10 全过

证据：

- `backend/src/main/java/com/paike/scheduler/service/ScheduleService.java:111`
- `backend/src/main/java/com/paike/scheduler/service/ScheduleService.java:123`

`ScheduleService.create` 创建 `Schedule` 后插入，但未发现 `setWeekType`、`setStartWeek`、`setEndWeek`。V10 已支持连续周段后，手动排课如果仍只写基础字段，会造成手动课表与周段冲突检测、展示、导出语义不一致。

### 2. 应用方案会清理旧课表再写入

结论：属实，但“物理删除”说法错误。优先级：P0/P1。**状态：✅ 已修复（2026-06-22，未提交）**

修复内容：
- **并发保护**：`applyPlanInternal` 入口加 `SemesterMapper.selectByIdForUpdate`（SELECT ... FOR UPDATE）锁定学期行，串行化同学期的方案应用
- **幂等保护**：获锁后重新读取方案状态，若已被并发应用则拒绝重复执行
- **审计补全**：新增 `CLEAR_SEMESTER_SCHEDULES` 和 `REVERT_APPLIED_PLAN` 审计动作，记录旧课表清理数量和旧方案回退
- 测试：新增 4 个测试用例（行锁顺序、学期不存在、重复应用拒绝、旧课表审计），SchedulePlanServiceTest 14/14 全过

证据：

- `backend/src/main/java/com/paike/scheduler/service/SchedulePlanService.java:378`
- `backend/src/main/java/com/paike/scheduler/service/SchedulePlanService.java:392`
- `backend/src/main/java/com/paike/scheduler/service/SchedulePlanService.java:395`
- `backend/src/main/java/com/paike/scheduler/service/SchedulePlanService.java:433`

`applyPlanInternal` 会按学期删除旧 `Schedule`，再从方案明细写入新 `Schedule`。由于 `Schedule` 有 `@TableLogic`，这里更准确的问题是：应用方案覆盖语义重、并发/审计/幂等风险高，而不是“绕过软删除物理删除”。

### 3. ErrorBoundary 持久化错误信息

结论：属实。优先级：P1。

证据：

- `frontend/src/components/ErrorBoundary.vue:22`
- `frontend/src/components/ErrorBoundary.vue:41`

组件将错误写入 `sessionStorage`，并伴随 `console.error`。若错误对象包含 stack/component trace，存在前端敏感信息留存风险。

### 4. Cookie 读取正则未转义

结论：属实，但建议从“高危”降为中等风险。优先级：P1/P2。

证据：

- `frontend/src/utils/request.ts:7`
- `frontend/src/utils/request.ts:8`
- `frontend/src/utils/request.ts:59`

`getCookie(name)` 直接把 `name` 拼进 `RegExp`。当前调用固定为 `XSRF-TOKEN`，实际利用面较小，但实现本身不稳，应改为转义 cookie name 或不用正则解析。

### 5. 分页 v-model 与事件混用

结论：属实。优先级：P1/P2。

证据示例：

- `frontend/src/views/classInfo/ClassInfoView.vue:124`
- `frontend/src/views/classInfo/ClassInfoView.vue:130`
- `frontend/src/views/classroom/ClassroomView.vue:140`
- `frontend/src/views/classroom/ClassroomView.vue:146`
- `frontend/src/views/course/CourseView.vue:110`
- `frontend/src/views/course/CourseView.vue:116`
- `frontend/src/views/schedule/SchedulePlanView.vue:239`
- `frontend/src/views/schedule/SchedulePlanView.vue:245`

扫描到约 60 行相关绑定。问题不是一定必现，而是重复触发请求、状态竞态、分页大小变化时页码重置不一致。

### 6. 缺少请求取消机制

结论：属实。优先级：P2。

证据：

- 前端未发现 `AbortController`、`CancelToken`、统一 `signal` 模式。

快速切换筛选、分页、路由时，旧请求可能覆盖新状态。

### 7. 缺少乐观锁

结论：属实。优先级：P1/P2。

证据：

- 后端未发现 `@Version`。

并发更新场景可能出现后写覆盖先写。是否 P1 取决于当前是否有多人同时编辑排课基础数据或方案。

## 重要但需降级/修正的问题

### 8. MyBatis-Plus `delete(Wrapper)` 物理删除

结论：误报 / 技术论断错误。

证据：

- `backend/src/main/resources/application.yml:41`
- `backend/src/main/resources/application.yml:42`
- `backend/src/main/resources/application.yml:43`
- `backend/src/main/java/com/paike/scheduler/entity/Schedule.java:55`
- `backend/src/main/java/com/paike/scheduler/entity/SchedulePlanItem.java:64`
- `backend/src/main/java/com/paike/scheduler/entity/ScheduleScoreDetail.java:43`

项目启用了全局逻辑删除，多数实体也有 `@TableLogic`。因此“`delete(Wrapper)` 绕过 `@TableLogic` 物理删除”不成立。应删除该定性，保留“批量覆盖删除语义需谨慎”的业务风险。

### 9. JWT 默认 secret 硬编码

结论：误报。

证据：

- `backend/src/main/resources/application.yml:50`
- `backend/src/main/resources/application.yml:52`

`secret: ${JWT_SECRET}` 要求环境变量注入，注释还说明启动时会校验长度与默认值。不应列为硬编码默认密钥。

### 10. AuthInterceptor context-path 管理权限匹配

结论：部分属实。

证据：

- `backend/src/main/java/com/paike/scheduler/auth/AuthInterceptor.java:95`
- `backend/src/main/java/com/paike/scheduler/auth/AuthInterceptor.java:99`

使用 `request.getRequestURI()` 做路径判断。若应用部署在 context-path 下，路径前缀可能影响匹配。当前默认部署未必触发，应作为兼容性缺陷，不宜按立即高危处理。

### 11. Entity 直接暴露

结论：部分属实，数量需修正。

证据示例：

- `backend/src/main/java/com/paike/scheduler/controller/ClassInfoController.java:28`
- `backend/src/main/java/com/paike/scheduler/controller/ClassInfoController.java:43`
- `backend/src/main/java/com/paike/scheduler/controller/ClassroomController.java:28`
- `backend/src/main/java/com/paike/scheduler/controller/CourseController.java:26`
- `backend/src/main/java/com/paike/scheduler/controller/AutoScheduleBatchController.java:26`

确实存在 Controller 返回 Entity/Page<Entity>。但原报告“15 个 Controller”需要重新统计，不能直接沿用。

### 12. `useCrudForm` 未迁移数量

结论：部分属实，数量过期。

证据：

- 已有 5 个 view 使用 `useCrudForm`。
- 仍扫描到 9 处 `destroy-on-close`。

“7 个 CRUD View 未迁移”需要按当前文件重新统计。问题方向成立，但数量不准确。

### 13. schema.sql 严重滞后

结论：待人工复核 / 原证据不足。

证据：

- 当前主要 DDL 在 `backend/src/main/resources/db/migration/V1__baseline.sql`
- 该文件存在多张表和唯一键，例如 `sys_user`、`teacher`、`class_info`、`classroom`

如果原报告只看 `schema.sql`，可能忽略 Flyway migration。应以 `db/migration` 与实体差异为准重新比对。

## 代码质量问题确认

### 14. `ScheduleConflictService.checkConflict` 方法过长

结论：属实。

证据：

- `backend/src/main/java/com/paike/scheduler/service/ScheduleConflictService.java:54-220`

方法约 167 行。建议拆成教师冲突、教室冲突、班级冲突、周段重叠、结果组装等私有方法。

### 15. `EngineContextLoader.load` 复杂度高

结论：属实，但原路径写错。

证据：

- `backend/src/main/java/com/paike/scheduler/service/EngineContextLoader.java:39-278`

方法约 240 行。原报告若写成 `engine/EngineContextLoader` 是路径错误，实际类在 `service` 包。

### 16. `IncrementalPenaltyState` 19 参数构造函数

结论：属实。

证据：

- `backend/src/main/java/com/paike/scheduler/engine/optimize/IncrementalPenaltyState.java:59-79`

构造函数 19 个参数。虽然是私有构造且主要由 `from` 工厂调用，风险低于公共 API，但可读性和维护性确实差。

### 17. `SchedulePlanDetailView.vue` God Component

结论：属实。

证据：

- `frontend/src/views/schedule/SchedulePlanDetailView.vue` 当前 656 行。

原报告 655 行基本准确。建议拆分为方案概览、明细表、调整日志、任务日志弹窗、操作栏等组件。

### 18. API 层重复响应检查

结论：属实。

证据：

- 前端 `src/api/*.ts` 扫描到大量 `.then((r) => ...)`、`throw new Error`、响应 data 检查模式。

建议收敛到统一 request wrapper 或 `unwrapApiResponse<T>`。

### 19. RateLimiter 单机内存实现

结论：属实，但不是当前单机部署的功能性 bug。

证据：

- `backend/src/main/java/com/paike/scheduler/auth/RateLimiterService.java`

使用内存 Map 保存尝试记录，未见容量上限和集群共享。单机可用；多实例部署不一致，长时间运行存在清理策略风险。

### 20. console.error 信息泄露

结论：属实但需分级。

证据：

- 前端扫描到约 28 处 `console.error` / `console.warn` / `console.log`。

不是所有 console 都构成安全漏洞。真正应优先处理的是输出完整异常对象、响应对象、认证相关信息的位置。

## 修正后的优先级

### P0

1. ~~`ScheduleService.create` 补齐 `weekType/startWeek/endWeek`，并加手动排课周段测试。~~ ✅ 已修复（2026-06-22，未提交）
2. ~~明确 `applyPlanInternal` 覆盖旧课表的业务语义，补幂等/并发/审计保护。~~ ✅ 已修复（2026-06-22，未提交）

### P1

1. ErrorBoundary 不再持久化完整错误栈。
2. Cookie 解析转义或改非正则实现。
3. AuthInterceptor 改用 context-path 安全的 path 获取方式。
4. 关键编辑接口引入乐观锁或版本校验。
5. `ScheduleConflictService.checkConflict` 拆分并补边界测试。

### P2

1. 前端请求取消机制。
2. 分页事件模式统一。
3. API 响应 unwrap 统一。
4. `SchedulePlanDetailView.vue` 拆组件。
5. `EngineContextLoader.load` 分阶段拆分。
6. RateLimiter 增加容量/过期清理，或替换为 Redis/网关限流。

### P3

1. Entity/DTO 边界统一。
2. 权限模型从 ADMIN-only 写操作演进到细粒度 RBAC。
3. `IncrementalPenaltyState` 参数对象化。

## 最终判断

`code-review-comprehensive-verification.md` 比原始两份报告更可信，二次验证支持它的大部分纠错。当前不能直接按原始报告的 CRITICAL/HIGH 执行修复，否则会把 MyBatis-Plus 逻辑删除、JWT secret、schema.sql 等问题误判为高危。

建议后续按本文件的 P0/P1 顺序修复，不再使用原始报告的优先级矩阵。
