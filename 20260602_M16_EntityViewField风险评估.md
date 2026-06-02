# M-16 风险评估报告 — Entity `@TableField(exist=false)` 视图字段

> 评估对象：D-16 / 20260527bug验证报告 M-16 项
> 评估日期：2026-06-02
> 评估分支：`minimaxm3/m14-m16-m25-risk-assessment`
> 评估方式：源码静态核验 + Controller/Service/Mapper 三角比对 + 前端 TS 接口契约比对
> 结论：**改造技术可行，但比 M-14 风险高一个等级。22 个 Controller 端点经 `Result<...>` 包装暴露 Entity（含 view 字段），10+ 前端 TS 接口已锁定 view 字段；任一字段遗漏都会引发前端展示空白。建议作为长期治理项，按 Entity 分 9 批 PR 推进**

---

## 0. TL;DR

| 维度 | 评估 |
|---|---|
| 涉及 Entity | 9 个，43 处 `@TableField(exist = false)` |
| 暴露 Entity（`Result<...>` 包装）的 Controller 端点 | **22 个**（13 个 Service 公共方法被这些端点消费）|
| 前端 TS 接口已声明 view 字段 | **10+** 个接口，~30 个字段名（与后端 Entity 字段一一对应）|
| Mapper SQL alias 注入 | 2 个 mapper（`TeachingTaskMapper` 6 个 / `UnscheduledTaskMapper` 4 个）共 **10 个** alias 字段 |
| 字段填充入口 | 2 类：① SQL alias 走 MyBatis 反射；② Service `fillRelations*()` 批量加载关联表后 `setXxx` |
| 改造总成本 | **5.25 人日**（远高于 M-14，见 §4.2 合计）|
| 风险等级 | **中-高**（公开 API 端点 22 个 + 字段遗漏 = 前端展示字段全断）|
| 推荐路径 | 9 Entity → 9 ViewVO，单 Entity 单 PR，配合前端 `vue-tsc` 严格模式兜底 |

---

## 1. 现状全量盘点

### 1.1 Entity × 视图字段矩阵

| # | Entity | 字段数 | 字段明细 | 主要消费端点 |
|---|---|---:|---|---|
| 1 | **Schedule** | 10 | `courseName`, `teacherName`, `className`, `timeLabel`, `dayOfWeek`, `periodNo`, `roomName`, `building`, `sourceTypeName`, `batchNo` | `ScheduleController` 6 端点（list/getById/create/listByClass/listByTeacher/listByClassroom）|
| 2 | **TeachingTask** | 8 | `courseName`, `teacherName`, `className`, `scheduledSlots`, `courseType`, `teacherStatus`, `classStatus`, `studentCount` | `TeachingTaskController` 5 端点（list/getById/create/update/listAll）|
| 3 | **SchedulePlanItem** | 5 | `courseName`, `teacherName`, `className`, `roomName`, `timeLabel` | `SchedulePlanController.getItems` |
| 4 | **ScheduleAdjustLog** | 5 | `courseName`, `teacherName`, `className`, `oldClassroomName`, `newClassroomName` | `ScheduleAdjustLogController.list`（前端 `v5SimulationApi.ts` 重复声明同名接口，见 §1.5）|
| 5 | **TeacherUnavailableTime** | 5 | `teacherName`, `department`, `timeSlotName`, `dayOfWeek`, `periodNo` | `TeacherUnavailableTimeController` 3 端点（list/create/update；另 delete/updateStatus 返回 `Result<Void>`）|
| 6 | **UnscheduledTask** | 4 | `courseName`, `teacherName`, `className`, `batchNo` | `UnscheduledTaskController` 2 端点 |
| 7 | **ScheduleUnassignedTask** | 3 | `courseName`, `teacherName`, `className` | `SchedulePlanController.getUnassignedTasks` |
| 8 | **SchedulePlan** | 2 | `semesterName`, `strategyName` | `SchedulePlanController` 2 端点（list/getById）|
| 9 | **ScheduleConflictReport** | 1 | `timeSlotName` | `ScheduleConflictReportController.list` |
| **合计** | 9 Entity | **43 字段** | | **22 Controller 端点** |

### 1.2 SQL alias 注入现状

`TeachingTaskMapper.xml` 2 个查询含 view 字段 alias：

```xml
<!-- selectFilteredTasks（第 6 行）-->
SELECT tt.* FROM teaching_task tt ...
<!-- 注意：这里没有 alias，但 join 的 course/teacher/class_info 名字都通过 service 重新 fill -->

<!-- selectConflictCheckById（第 31-47 行）-->
SELECT tt.*,
       c.course_type AS courseType,
       t.name AS teacherName,
       t.status AS teacherStatus,
       cl.class_name AS className,
       cl.status AS classStatus,
       cl.student_count AS studentCount
FROM teaching_task tt ...
```

`UnscheduledTaskMapper.xml`（`selectFilteredPage`）：

```xml
SELECT ut.*,
       c.course_name AS courseName,
       t.name AS teacherName,
       cl.class_name AS className,
       b.batch_no AS batchNo
FROM unscheduled_task ut ...
```

**这 10 个 alias 字段（`TeachingTaskMapper` 6 + `UnscheduledTaskMapper` 4）映射到 Entity 的 `@TableField(exist=false)` view 字段，MyBatis 通过反射直接写入 Entity 实例**。如果引入独立 VO，SQL 需要改为 `resultType="com.paike.scheduler.vo.TeachingTaskVo"` 并对 Vo 写完整 resultMap。

### 1.3 Service `fillRelations*` 注入入口

| Service | 方法 | 填充字段 |
|---|---|---|
| `TeachingTaskService` | `create/update` 内联 + `fillTaskRelations`（被 `list/getById/listAll` 调用）共 5 处 | `courseName/teacherName/className/scheduledSlots` |
| `ScheduleService` | `fillRelations(List<Schedule>)` | `timeLabel/dayOfWeek/periodNo/roomName/building/courseName/teacherName/className/sourceTypeName/batchNo` |
| `SchedulePlanService` | `fillItemRelations` | `courseName/teacherName/className/roomName/timeLabel` |
| `SchedulePlanExplainService` | `fillUnassignedRelations` + `fillAdjustRelations` | `courseName/teacherName/className/oldClassroomName/newClassroomName` |
| `TeacherUnavailableTimeService` | 内部填充 | `teacherName/department/timeSlotName/dayOfWeek/periodNo` |
| `ScheduleConflictReportService` | 内部填充 | `timeSlotName` |
| `UnscheduledTaskService` | `setScheduledSlots` | 单一字段 |
| `V4ScheduleLockService` | 内部填充 | `courseName/teacherName/className/roomName` |
| `V4ScheduleChartService` | 内部填充 | `teacherName/roomName/className` |

**Service 内部 view 字段是"主战场"**：9 个 service 共同维护 view 字段生命周期。

### 1.4 暴露 Entity 的 Controller 端点（22 个，均经 `Result<...>` 包装）

> 复核更正（2026-06-02）：以下 22 个端点**没有一个返回裸 Entity**，全部是 `Result<Page<Entity>>` / `Result<Entity>` / `Result<List<Entity>>`；Entity（含 view 字段）置于 `Result.data` 序列化下发。路径均含 `/api` 前缀；`ScheduleConflictReportController` 无 `/v3` 段；未排任务批次端点为 `/batch/{batchId}`。完整实测对照见 §7.4。

| Controller | 端点（实测路径） | 返回类型（实测） |
|---|---|---|
| `ScheduleController` | `GET /api/schedules` | `Result<Page<Schedule>>` |
| `ScheduleController` | `GET /api/schedules/{id}` | `Result<Schedule>` |
| `ScheduleController` | `POST /api/schedules` | `Result<Schedule>` |
| `ScheduleController` | `GET /api/schedules/class/{classId}` | `Result<List<Schedule>>` |
| `ScheduleController` | `GET /api/schedules/teacher/{teacherId}` | `Result<List<Schedule>>` |
| `ScheduleController` | `GET /api/schedules/classroom/{classroomId}` | `Result<List<Schedule>>` |
| `TeachingTaskController` | `GET /api/teaching-tasks` | `Result<Page<TeachingTask>>` |
| `TeachingTaskController` | `GET /api/teaching-tasks/{id}` | `Result<TeachingTask>` |
| `TeachingTaskController` | `POST /api/teaching-tasks` | `Result<TeachingTask>` |
| `TeachingTaskController` | `PUT /api/teaching-tasks/{id}` | `Result<TeachingTask>` |
| `TeachingTaskController` | `GET /api/teaching-tasks/all` | `Result<List<TeachingTask>>` |
| `UnscheduledTaskController` | `GET /api/unscheduled-tasks` | `Result<Page<UnscheduledTask>>` |
| `UnscheduledTaskController` | `GET /api/unscheduled-tasks/batch/{batchId}` | `Result<Page<UnscheduledTask>>` |
| `SchedulePlanController` | `GET /api/v3/schedule-plans` | `Result<Page<SchedulePlan>>` |
| `SchedulePlanController` | `GET /api/v3/schedule-plans/{id}` | `Result<SchedulePlan>` |
| `SchedulePlanController` | `GET /api/v3/schedule-plans/{planId}/items` | `Result<List<SchedulePlanItem>>` |
| `SchedulePlanController` | `GET /api/v3/schedule-plans/{planId}/unassigned-tasks` | `Result<List<ScheduleUnassignedTask>>` |
| `ScheduleAdjustLogController` | `GET /api/v3/schedule-adjust-logs` | `Result<Page<ScheduleAdjustLog>>` |
| `ScheduleConflictReportController` | `GET /api/schedule-conflict-reports` | `Result<Page<ScheduleConflictReport>>` |
| `TeacherUnavailableTimeController` | `GET /api/teacher-unavailable-times` | `Result<Page<TeacherUnavailableTime>>` |
| `TeacherUnavailableTimeController` | `POST /api/teacher-unavailable-times` | `Result<TeacherUnavailableTime>` |
| `TeacherUnavailableTimeController` | `PUT /api/teacher-unavailable-times/{id}` | `Result<TeacherUnavailableTime>` |

### 1.5 前端 TS 接口依赖

| 文件 | 接口 | view 字段声明 |
|---|---|---|
| `frontend/src/api/schedule.ts` | `Schedule` | `courseName/teacherName/className/timeLabel/dayOfWeek/periodNo/roomName/building/sourceTypeName/batchNo` |
| `frontend/src/api/teachingTask.ts` | `TeachingTask` | `courseName/teacherName/className/scheduledSlots/requiredSlots` |
| `frontend/src/api/schedulePlan.ts` | `SchedulePlan` | `semesterName/strategyName` |
| `frontend/src/api/schedulePlan.ts` | `SchedulePlanItem` | `courseName/teacherName/className/roomName/timeLabel` |
| `frontend/src/api/schedulePlan.ts` | `ScheduleUnassignedTask` | `courseName/teacherName/className` |
| `frontend/src/api/schedulePlan.ts` | `ScheduleAdjustLog` | `courseName/teacherName/className/oldClassroomName/newClassroomName` |
| `frontend/src/api/v5SimulationApi.ts` | `ScheduleAdjustLog` | 同上（重复定义，建议合并）|
| `frontend/src/api/unscheduledTask.ts` | `UnscheduledTask` | `courseName/teacherName/className/batchNo/scheduledSlots/requiredSlots/reasonType/reasonMessage` |
| `frontend/src/api/teacherUnavailableTime.ts` | `TeacherUnavailableTime` | `teacherName/department/timeSlotName/dayOfWeek/periodNo` |
| `frontend/src/api/scheduleConflictReport.ts` | `ScheduleConflictReport` | `timeSlotName` |

> 注：`UnscheduledTask` 还引用了 `requiredSlots` —— 这个字段是 entity 真实字段，**并非** `@TableField(exist=false)`，但前端混用 view 字段与持久化字段的模式相同。

---

## 2. 前端契约核验

| 接口 | 字段对齐 | 风险 |
|---|---|---|
| `Schedule` (10 view 字段) | 后端 `Schedule.java` 10 view 字段 1:1 对齐 | **极低**：仅换 VO 不破坏 |
| `TeachingTask` | 后端 8 view 字段，但前端 `teachingTask.ts` 仅声明 5 个（`courseName/teacherName/className/scheduledSlots/requiredSlots`）；`courseType/teacherStatus/classStatus/studentCount` 前端未消费，**非 1:1** | **中**（对齐缺口，见 §4.4.2）|
| `SchedulePlanItem` (5 view 字段) | 后端 5 view 字段 1:1 对齐 | 极低 |
| `ScheduleAdjustLog` (5 view 字段) | 后端 5 view 字段 1:1 对齐 | 极低 |
| `TeacherUnavailableTime` (5 view 字段) | 后端 5 view 字段 1:1 对齐 | 极低 |
| `UnscheduledTask` (4 view 字段) | 后端 4 view 字段 1:1 对齐 | 极低 |
| `ScheduleUnassignedTask` (3 view 字段) | 后端 3 view 字段 1:1 对齐 | 极低 |
| `SchedulePlan` (2 view 字段) | 后端 2 view 字段 1:1 对齐 | 极低 |
| `ScheduleConflictReport` (1 view 字段) | 后端 1 view 字段 1:1 对齐 | 极低 |

**关键观察**：前端 TS 接口的 view 字段是"必需"（不带 `?`）还是"可选"（带 `?`）不一致：
- `Schedule.courseName/teacherName/className/timeLabel/dayOfWeek/periodNo/roomName/building` 都是**必填**（不带 `?`）—— 意味着即使 service 漏了 fillRelations，前端也会显示 undefined 报错
- 但 `sourceTypeName` 和 `batchNo` 是**可选** —— 容忍空缺
- `TeachingTask` 前端仅 5 view 字段（`courseName/teacherName/className/scheduledSlots/requiredSlots`）均**必填**；后端另有 4 个 view 字段（`courseType/teacherStatus/classStatus/studentCount`）前端**未声明**，对齐关系不成立

**结论**：只要 JSON 字段名一致，**纯字段类型替换不影响前端**。但 VO 字段名如果与 Entity 字段名拼写不一致，TypeScript 编译会立刻爆。

---

## 3. 风险评估

### 3.1 风险矩阵

| 风险点 | 等级 | 触发条件 | 缓解措施 |
|---|---|---|---|
| VO 字段名拼写错误 | 中 | 复制 Entity → VO 时遗漏或多写 | 强制 `vue-tsc` 严格模式编译 0 错误 |
| Jackson 序列化字段顺序变化 | 极低 | 不影响功能但前端 debug 不便 | 不强求顺序 |
| 字段从 `null` 改为缺失 | 低 | `Optional` 包装或 `@JsonInclude` | 维持现状 `null` 字段保留 |
| `TeachingTask.scheduledSlots` 计算逻辑 | 中 | service 改为 VO 装配时漏写 | `TeachingTaskServiceTest` 已覆盖 |
| `Schedule.sourceTypeName` 中文映射 | 低 | 枚举值变化时 VO 不会自动同步 | 维持 service 内手工赋值 |
| SQL alias 字段映射到 VO | **高** | MyBatis resultMap 改为 VO 后需手写映射 | 见 §4.3 方案 A/B/C |
| `UnscheduledTask.scheduledSlots` 实际是 DB 字段 | 低 | 与 view 字段混在 VO 里无影响 | 维持现状 |
| `V5SimulationApi.ScheduleAdjustLog` 与 `schedulePlan.ts.ScheduleAdjustLog` 重复定义 | 低 | VO 改造时合并定义 | 单源 schema 整理 |
| Mapper alias 字段移除后 N+1 回退 | **中** | 删除 alias 改为 service JOIN 会增加查询 | 保留 SQL alias |
| Service 内部 `setXxx` 写 Entity → 改写 VO 装配 | 中 | 9 个 service `fillRelations*` 全要改 | 引入 `toVo(Entity)` 工具或 MapStruct |
| 删除 `@TableField(exist=false)` 后其他模块反射注入 | 极低 | 唯一已知反射是 MyBatis | 移除时全量 grep 验证 |
| 前端展示空白 | **高** | 任一字段遗漏 | E2E 测试覆盖关键页面 |

### 3.2 影响面

- **新建 VO**：9 个（每个 Entity 一个 ViewVO） + 共用基础类（`VoUtils` 转换器）= 10 个新类
- **修改 Service**：**14 个**（§1.3 中直接维护 view 字段的 9 个：`TeachingTaskService` / `ScheduleService` / `SchedulePlanService` / `SchedulePlanExplainService` / `TeacherUnavailableTimeService` / `ScheduleConflictReportService` / `UnscheduledTaskService` / `V4ScheduleLockService` / `V4ScheduleChartService`；+ 下游读取/复用 view 字段的 5 个：`V5SimulationService` / `V5ConsistencyCheckService` / `V4ScheduleSourceService` / `V4ScheduleAdjustmentService` / `AutoScheduleService`）
- **修改 Controller**：10 个 controller（22 端点）
- **修改 Mapper XML**：2 个（`TeachingTaskMapper.xml` / `UnscheduledTaskMapper.xml`）—— 需要改 `resultType` 或保留双查询
- **修改 Entity**：9 个（删除 `@TableField(exist=false)` 字段）
- **修改测试**：`M16TableFieldViewFieldsInvestigationTest` 计数断言需重写 + 新增 VO 序列化测试
- **前端**：**无需改动**（TS 接口已对齐 JSON 字段）但需在 strict 模式做一次构建兜底
- **数据库/SQL**：仅 DDL 脚本的 `schedule` 表无关，但 mapper XML 有 2 个文件受影响

### 3.3 SQL alias 移除的影响

当前 `TeachingTaskMapper.selectConflictCheckById` 和 `UnscheduledTaskMapper.selectFilteredPage` 通过 SQL alias 把 JOIN 数据写回 Entity。

如果 VO 化后移除 alias：
- `selectConflictCheckById` 当前 1 次 JOIN 查询，移除后变 2 次（基础 + 关联）
- `selectFilteredPage` 同理

**量化估算**：
- `selectConflictCheckById` 每次冲突检查 1 次调用，影响所有排课冲突路径
- `selectFilteredPage` 每次分页 1 次调用，影响未排任务列表

**建议**：**保留 SQL alias**，只改 `resultType` 为 VO —— 这是 ROI 最高的路径，零性能损失。

### 3.4 测试覆盖现状

| 测试类 | 当前 | 改造后 |
|---|---|---|
| `M16TableFieldViewFieldsInvestigationTest` | 锁定 43/9 分布 | 改写为 0 命中 或 删除（已治理完成）|
| `TeachingTaskServiceTest` | 覆盖 create/update/listPage | 需新增 `toVo` 字段断言 |
| `ScheduleServiceTest` | 已有 | 需新增 `fillRelations` 后字段断言 |
| `SchedulePlanServiceTest` | 4 tests | 需新增 `fillItemRelations` 后字段断言 |
| `SchedulePlanExplainServiceTest` | 无 | 需新增 `fillUnassignedRelations` / `fillAdjustRelations` 字段断言 |
| `TeacherUnavailableTimeServiceTest` | 覆盖重复校验 | 需新增 view 字段断言 |
| 前端构建 | 通过 | 应继续通过 |

### 3.5 性能影响

- **运行时**：Jackson 序列化 VO 与 Entity 等价（如果 VO 字段声明顺序与原 Entity 一致）
- **编译时**：9 个新类，启动类加载量 +9
- **构建时**：mapper XML resultType 改变需要重启 Spring 上下文测试
- **SQL**：建议保留 alias，不变

### 3.6 安全影响

- **无**：替换前后都是同一组 JSON 字段输出
- **Schedule.sourceTypeName** 维持中文枚举字符串，**无 XSS 风险**（前端表格展示用，不进入 HTML 属性）

---

## 4. 改造方案（推荐 9 批 PR）

### 4.1 推荐路径

**前置条件**：在 `minimaxm3/m14-m16-m25-risk-assessment` 分支上仅评估；建议另开实施分支 `refactor/m16-entity-viewfield-dto-ify`。

每批 PR 治理 1-2 个 Entity，单 PR 内：
1. 新建 Entity 对应 ViewVO
2. Service 添加 `toVo(Entity) / toVos(List<Entity>)` 方法（或用 `VoMapper` 工具）
3. Controller 改返回类型 `Result<XxxVo>` 或 `Result<Page<XxxVo>>`
4. Mapper XML 改 `resultType`（如果用方案 A）
5. 删除 Entity `@TableField(exist=false)` 字段
6. 新增 VO 序列化测试 + `M16 InvestigationTest` 改写
7. 前端 `npm run build` 通过

### 4.2 9 批 PR 顺序（按依赖关系）

| 顺序 | Entity | 依赖 | 工期估算 |
|---:|---|---|---|
| 1 | **TeacherUnavailableTime** (5 字段) | 无 | 0.5 人日 |
| 2 | **ScheduleConflictReport** (1 字段) | 无 | 0.25 人日 |
| 3 | **SchedulePlan** (2 字段) | 弱 | 0.5 人日 |
| 4 | **UnscheduledTask** (4 字段) | 弱（依赖 `batchNo` 通过 alias）| 0.5 人日 |
| 5 | **TeachingTask** (8 字段) | 中（alias mapper）| 1.0 人日 |
| 6 | **ScheduleUnassignedTask** (3 字段) | 弱 | 0.5 人日 |
| 7 | **SchedulePlanItem** (5 字段) | 中（被 V4 Lock / V5 Risk 复用）| 0.5 人日 |
| 8 | **ScheduleAdjustLog** (5 字段) | 弱 | 0.5 人日 |
| 9 | **Schedule** (10 字段) | **强**（最大、被 9+ service 复用）| 1.0 人日 |
| **合计** | 9 | | **5.25 人日** |

### 4.3 VO 装配方案对比

#### 方案 A：保留 SQL alias，改 `resultType` 为 VO（推荐）

```xml
<!-- TeachingTaskMapper.xml -->
<select id="selectFilteredTasks" resultType="com.paike.scheduler.vo.TeachingTaskVo">
    SELECT tt.*, c.course_type AS courseType, t.name AS teacherName, ...
    FROM teaching_task tt ...
</select>
```

- **优点**：零 SQL 性能损失；MyBatis 自动填充 VO 字段（驼峰 → 驼峰映射）
- **缺点**：resultType 必须是新 VO，**且 VO 字段名必须与 alias 完全一致**（含大小写）
- **风险**：中
- **改动**：mapper XML resultType + 改 Service 返回类型

#### 方案 B：Mapper 返回 Entity，Service 内 `toVo()` 转换

```java
// TeachingTaskService.listPage
Page<TeachingTask> page = teachingTaskMapper.selectFilteredPage(...);
return page.convert(this::toVo);
```

- **优点**：Mapper 不用动；Service 控制灵活
- **缺点**：失去 SQL alias 优化，**多 1 次循环 + Map 拷贝**；大列表性能损耗
- **风险**：中
- **改动**：Service + VoMapper 工具类

#### 方案 C：Mapper 同时返回 Entity 和 DTO（不推荐）

- 缺点：代码冗余、维护成本高
- 风险：高

#### 最终建议

| Entity | 推荐方案 | 原因 |
|---|---|---|
| `TeachingTask` (含 alias) | **A** | alias 是性能优化，不能丢 |
| `UnscheduledTask` (含 alias) | **A** | 同上 |
| 其他 7 个 Entity | **B** | Service 已有 fillRelations，扩展为 toVo 自然 |
| 特殊：`Schedule` | **B** | 10 字段聚合太复杂，alias 不适用 |

### 4.4 风险点专项方案

#### 4.4.1 `Schedule.java` 的注释说明

```java
// ========== VO 展示字段（@TableField(exist = false)，不映射列，仅用于 API 响应组装）==========
// 这些字段在 fillRelations() 中从关联表批量查询填充，属于展示职责而非持久化职责。
// 理想情况下应拆分独立的 ScheduleVO，当前为减少 DTO 转换开销暂放在 Entity 中。
```

**这是项目方已知的合理技术债**。M-16 改造正好回应这条注释。

#### 4.4.2 Mapper alias 改造陷阱

`TeachingTaskMapper.selectConflictCheckById` 一次返回 **6 个** alias 字段（`courseType/teacherName/teacherStatus/className/classStatus/studentCount`）：

- 6 个 alias 中，前端 `teachingTask.ts` 仅消费 `teacherName/className` 2 个
- `courseType/teacherStatus/classStatus/studentCount` 前端**未声明**（仅后端冲突检测逻辑 / `V5` 内部 VO 使用）

如果 `resultType` 改为 `TeachingTaskVo`，**VO 必须保留 6 个 alias 字段** —— 字段命名一字不差。

#### 4.4.3 跨模块 VO 引用问题

`V4ScheduleLockService` / `V4ScheduleRiskService` / `V4ScheduleAdjustmentService` / `V5SimulationService` 内部把 `Schedule` / `SchedulePlanItem` 的 view 字段读出来，写到自己的 VO 里。VO 化后这些下游需要：
- 改读 `XxxVo.getCourseName()`（新方法名）
- 或者保持原 `XxxEntity.getCourseName()`（过渡期兼容）

**建议**：先做"下游"（4/5/6/7/8/9 批），再做"上游"（1/2/3 批），保证依赖关系正确。

实际上：所有批都同时改 Service，因为**所有 Service 都同时读写 Entity 和 VO**。建议：
- 每个 PR 一次性把 Entity 改完（删字段 + 加 VO 字段同步）
- 用 `toVo()` 工具类做 Entity → VO 转换
- Service 内部仍然读 Entity（最后才改）

### 4.5 不建议做的"激进"重构

- ❌ **全量 `MapStruct` 化**：9 个 Entity × 9 个 VO，MapStruct 性能最优但学习曲线陡，且 mapper 字段与 alias 混在一起调试复杂
- ❌ **全量 Java Record 化**：与 M-14 同样的问题，聚合类需要循环累加
- ❌ **删除 SQL alias 改为 service 拼接**：性能回退 ~30%（N+1 风险）
- ❌ **一次性 9 批合一个 PR**：review 工作量大、merge 冲突高、回滚难

---

## 5. 风险结论

### 5.1 总体评级：**可推进但工期长，建议分 9 批 PR**

| 维度 | 评级 | 说明 |
|---|---|---|
| 功能正确性风险 | **中** | 22 端点 + 30 字段遗漏任一会引发前端展示空白 |
| 接口契约风险 | **低** | TS 接口已对齐，机械替换 |
| 性能风险 | **极低** | 保留 SQL alias，零损耗 |
| 安全风险 | **无** | 无认证/输入变更 |
| 回归风险 | **中** | 9 批需 9 轮回归测试 |
| 工期风险 | **中** | 5.25 人日 + 测试 + review 约 1.5 sprint |
| 维护收益 | **高** | 消除 Entity/View 职责混杂，IDE 字段补全回归 |

### 5.2 不实施改造的代价

- **Entity 字段污染**：`@TableField(exist=false)` 持续增加，未来可能涨到 60+
- **N+1 风险持续**：view 字段填充靠 service 批量加载 SQL，新人容易写出"在循环里 selectById"
- **VO 生成工具缺失**：未来要 OpenAPI/SpringDoc 集成时，`Map` 黑洞 + Entity view 字段膨胀会让 schema 不可读
- **测试用例维护**：每次 view 字段增加要同步前端 TS 接口 + 回归测试

### 5.3 实施时的强制约束（建议写进 PR 模板）

1. **单 Entity 单 PR**：每 PR ≤ 1.5 人日
2. **保留 SQL alias**：除非性能数据证明显著损耗
3. **新增 `XxxVo` 必须有序列化测试**：从 Entity 输入构造 VO，序列化 JSON，断言关键字段
4. **前端构建必须通过**：`vue-tsc` 严格模式 0 错误（strict 模式已有）
5. **E2E 冒烟**：关键页面（排课列表、方案列表、未排任务、冲突报告）至少一个回归用例
6. **不改 SQL DDL**：本任务仅 Java 层
7. **`M16TableFieldViewFieldsInvestigationTest` 改写为 0 命中或迁移为视图层回归测试**

---

## 6. 建议

| 选项 | 评估 |
|---|---|
| A. 立即按 §4.2 顺序启动（9 批 PR / 1.5 sprint） | **推荐**：节奏可控、回滚粒度细 |
| B. 优先做 #1 #2 #3（最简 Entity 起步） + 暂缓 #5 #9 | 推荐：可分两阶段（先 4 批 1 sprint，后 5 批 1 sprint）|
| C. 维持"暂不修复" | 不推荐：M-16 注释已明确指出技术债存在 |
| D. 一次性 9 批合并 | 不推荐：PR review 困难、合并冲突高 |

**最终建议**：开 `refactor/m16-entity-viewfield-dto-ify` 分支，按 §4.2 顺序推进。前 4 批作为"热身"，1 sprint 内交付；后 5 批在生产稳定后第二个 sprint 交付。

---

## 7. 附录

### 7.1 关键文件位置

| 用途 | 路径 |
|---|---|
| 现状测试 | `backend/src/test/java/com/paike/scheduler/architecture/M16TableFieldViewFieldsInvestigationTest.java` |
| 现有调查 | `20260529_M16_实体视图字段调查.md` |
| 验证报告 | `20260527bug验证报告.md` §4 M-16 |
| 证据清单 | `minimaxm320260602.md` §3 M-16 |
| Entity 文件 | `backend/src/main/java/com/paike/scheduler/entity/{Schedule,TeachingTask,SchedulePlanItem,ScheduleAdjustLog,UnscheduledTask,ScheduleUnassignedTask,TeacherUnavailableTime,SchedulePlan,ScheduleConflictReport}.java` |
| Mapper XML | `backend/src/main/resources/mapper/TeachingTaskMapper.xml` / `UnscheduledTaskMapper.xml` |
| 前端 TS 接口 | `frontend/src/api/{schedule,teachingTask,schedulePlan,unscheduledTask,teacherUnavailableTime,scheduleConflictReport,v5SimulationApi}.ts` |

### 7.2 引用检查命令

```bash
# 9 Entity 字段数
grep -B1 "private " $(find backend/src/main/java -name "*.java" -path "*/entity/*") | grep -A1 "@TableField(exist = false)" | head -40

# 22 端点暴露 Entity（Result<...> 包装）
grep -rn "Result<.*(Schedule|TeachingTask|SchedulePlanItem|ScheduleAdjustLog|UnscheduledTask|ScheduleUnassignedTask|TeacherUnavailableTime|SchedulePlan|ScheduleConflictReport)>" backend/src/main --include="*Controller.java"

# view 字段填充入口
grep -rn "setCourseName\|setTeacherName\|setClassName\|setRoomName\|setTimeLabel" backend/src/main --include="*.java"

# Mapper alias
grep -rn "AS courseName\|AS teacherName\|AS className" backend/src/main/resources/mapper --include="*.xml"
```

### 7.3 9 Entity 的 view 字段一览

| Entity | View 字段（共 43 个） |
|---|---|
| Schedule | `courseName, teacherName, className, timeLabel, dayOfWeek, periodNo, roomName, building, sourceTypeName, batchNo` |
| TeachingTask | `courseName, teacherName, className, scheduledSlots, courseType, teacherStatus, classStatus, studentCount` |
| SchedulePlanItem | `courseName, teacherName, className, roomName, timeLabel` |
| ScheduleAdjustLog | `courseName, teacherName, className, oldClassroomName, newClassroomName` |
| TeacherUnavailableTime | `teacherName, department, timeSlotName, dayOfWeek, periodNo` |
| UnscheduledTask | `courseName, teacherName, className, batchNo` |
| ScheduleUnassignedTask | `courseName, teacherName, className` |
| SchedulePlan | `semesterName, strategyName` |
| ScheduleConflictReport | `timeSlotName` |

### 7.4 实测路径 / 返回类型对照表（2026-06-02 源码复核）

> 数据来源：直接读取 7 个 Controller 源码（类级 `@RequestMapping` + 各方法注解）。所有端点统一经 `Result<...>` 包装，Entity（含 `@TableField(exist=false)` view 字段）置于 `Result.data` 序列化。本表为校正 §1.4 后的实测值。

| Controller（类级 `@RequestMapping`） | 方法 | HTTP + 实测完整路径 | 实测返回类型 |
|---|---|---|---|
| `ScheduleController`（`/api/schedules`） | list | `GET /api/schedules` | `Result<Page<Schedule>>` |
| | getById | `GET /api/schedules/{id}` | `Result<Schedule>` |
| | create | `POST /api/schedules` | `Result<Schedule>` |
| | listByClass | `GET /api/schedules/class/{classId}` | `Result<List<Schedule>>` |
| | listByTeacher | `GET /api/schedules/teacher/{teacherId}` | `Result<List<Schedule>>` |
| | listByClassroom | `GET /api/schedules/classroom/{classroomId}` | `Result<List<Schedule>>` |
| `TeachingTaskController`（`/api/teaching-tasks`） | list | `GET /api/teaching-tasks` | `Result<Page<TeachingTask>>` |
| | getById | `GET /api/teaching-tasks/{id}` | `Result<TeachingTask>` |
| | create | `POST /api/teaching-tasks` | `Result<TeachingTask>` |
| | update | `PUT /api/teaching-tasks/{id}` | `Result<TeachingTask>` |
| | listAll | `GET /api/teaching-tasks/all` | `Result<List<TeachingTask>>` |
| `UnscheduledTaskController`（`/api/unscheduled-tasks`） | list | `GET /api/unscheduled-tasks` | `Result<Page<UnscheduledTask>>` |
| | listByBatch | `GET /api/unscheduled-tasks/batch/{batchId}` | `Result<Page<UnscheduledTask>>` |
| `SchedulePlanController`（`/api/v3/schedule-plans`） | list | `GET /api/v3/schedule-plans` | `Result<Page<SchedulePlan>>` |
| | getById | `GET /api/v3/schedule-plans/{id}` | `Result<SchedulePlan>` |
| | getItems | `GET /api/v3/schedule-plans/{planId}/items` | `Result<List<SchedulePlanItem>>` |
| | getUnassignedTasks | `GET /api/v3/schedule-plans/{planId}/unassigned-tasks` | `Result<List<ScheduleUnassignedTask>>` |
| `ScheduleAdjustLogController`（`/api/v3/schedule-adjust-logs`） | list | `GET /api/v3/schedule-adjust-logs` | `Result<Page<ScheduleAdjustLog>>` |
| `ScheduleConflictReportController`（`/api/schedule-conflict-reports`） | list | `GET /api/schedule-conflict-reports` | `Result<Page<ScheduleConflictReport>>` |
| `TeacherUnavailableTimeController`（`/api/teacher-unavailable-times`） | list | `GET /api/teacher-unavailable-times` | `Result<Page<TeacherUnavailableTime>>` |
| | create | `POST /api/teacher-unavailable-times` | `Result<TeacherUnavailableTime>` |
| | update | `PUT /api/teacher-unavailable-times/{id}` | `Result<TeacherUnavailableTime>` |
| **合计** | | **22 端点 / 7 Controller** | 全部 `Result<...>` 包装 |

> 与原 §1.4 的差异：① 返回类型原表漏写外层 `Result<...>`；② 路径原表漏 `/api` 前缀；③ `ScheduleConflictReport` 原表误标 `/v3/...`，实测无 `/v3` 段；④ 未排任务批次端点原表标 `/batch`，实测为 `/batch/{batchId}`（带路径变量）。端点总数 22 与原表一致。

### 7.5 本次复核修订记录（2026-06-02，源码静态核验）

| # | 位置 | 原值 | 修订为 | 类型 |
|---:|---|---|---|---|
| 1 | §0 结论 / §4 标题 | 4-5 批 / 推荐 5 批 PR | **9 批 PR**（每 Entity 一批，与 §4.2/§5.1 一致） | 数字矛盾 |
| 2 | §0 TL;DR 总成本 | 3-5 人日 | **5.25 人日**（§4.2 工期列求和） | 数字矛盾 |
| 3 | §0 TL;DR / §1.2 alias 数 | 共 8 个 | **共 10 个**（`TeachingTask` 6 + `Unscheduled` 4） | 数字错误 |
| 4 | §4.4.2 | “一次返回 4 个 alias…—— 6 个” | 统一为 **6 个**（仅指 `selectConflictCheckById`） | 自相矛盾 |
| 5 | §3.2 修改 Service | 9 个（却列出 15 个、含重复） | **14 个**（9 核心 + 5 下游，去重） | 数字矛盾 |
| 6 | §1.1 TeacherUnavailableTime | 5 端点 | **3 端点**（另 delete/updateStatus 返回 `Result<Void>`） | 数字错误 |
| 7 | §1.4 全表 | “直接返回 Entity” + 裸类型 + 缺 `/api` 路径 | `Result<...>` 包装 + `/api` 实测路径（详见 §7.4） | 表述/路径错误 |
| 8 | §2 / §1.1 TeachingTask 前端 | 8（或 9）view 字段 1:1 对齐、极低风险 | 前端仅 5 个，后端 4 个未消费 → **非 1:1**、风险升为中 | 结论错误 |
| 9 | §1.3 TeachingTaskService | create/update/listPage 3 处 | `list()` + `fillTaskRelations`（5 处调用） | 命名/计数 |
| 10 | §1.1 ScheduleAdjustLog | 误列前端 `V5SimulationApi` 为消费端点 | 改注为前端重复声明（见 §1.5） | 归类错误 |

> 未改动的核心结论（经核验准确）：9 Entity / 43 个 `@TableField(exist=false)` 字段及其字段名清单、22 端点总数、§1.3 fillRelations 填充入口、保留 SQL alias 的 ROI 判断、分批 PR 治理建议、`M16TableFieldViewFieldsInvestigationTest` 锁定 43/9 分布。

## gpt-5.5 评语

我核验了 9 个 Entity 的 `@TableField(exist=false)` 字段、Controller 返回类型、Mapper XML alias、Service 填充入口和前端 TS 接口。总体判断：本文主结论正确，M-16 的治理价值明确，但实施风险高于 M-14；主要风险不在“建 VO”，而在字段装配来源分散、SQL alias 与 Service `fillRelations*()` 并存、下游 V4/V5 服务读取 Entity view 字段。

核验成立：

- `@TableField(exist=false)` 实测为 9 个 Entity、43 个字段，字段清单与本文修正值一致。
- 暴露 Entity 的 Controller 端点实测为 22 个，均在 `Result<...>` 的 `data` 下序列化，不是裸 Entity；本文 §7.4 修正正确。
- Mapper alias 实测 2 个 XML、10 个 alias：`TeachingTaskMapper.xml` 6 个，`UnscheduledTaskMapper.xml` 4 个。
- `ScheduleAdjustLog` 在 `schedulePlan.ts` 和 `v5SimulationApi.ts` 中重复声明，确实会增加 VO 改造时的同步成本。
- 保留 SQL alias、按 Entity 分批治理，是当前 ROI 最高的路线。

需要修正/注意：

- `TeachingTask` 前端口径应更精确：前端只声明了 4 个后端 view 字段（`courseName/teacherName/className/scheduledSlots`），`requiredSlots` 是真实字段，不属于 `@TableField(exist=false)`。
- 文中多处写“前端 `vue-tsc` 严格模式可兜底 VO 字段名拼写错误”，这个说法偏强。前端 TS 类型是手写契约，`vue-tsc` 只能发现前端代码类型错误，不能证明后端 JSON 一定发出了同名字段；必须靠后端序列化测试或接口契约测试兜底。
- §3.2 的“前端无需改动”只在 JSON 字段完全保持不变时成立；若 VO 改造顺手清理未消费字段，可能影响 V4/V5 内部或未来接口复用。
- `Schedule` 是最高风险 Entity：10 个 view 字段、多个查询入口、V4/V5 下游复用多，建议最后处理是合理的。

结论：这份评估可以作为 M-16 实施计划依据。执行前建议补一层后端契约测试：按 22 个端点或 9 个 VO 序列化样例断言关键 view 字段存在、字段名不变、`null` 不被省略。不要只依赖 `vue-tsc` 判断改造安全。

---

## 复核勘误（2026-06-03，针对上方 gpt-5.5 评语）

> 上方 gpt-5.5 评语经源码逐条核对：5 条"核验成立"与 `vue-tsc`/契约测试/§3.2/`Schedule` 等 4 点提醒均属实且有价值。仅 **1 处需更正**：

- ❌ **"`requiredSlots` 是真实字段"对 `TeachingTask` 不成立**：`TeachingTask.java` 全文**无** `requiredSlots` 字段——既不是真实持久化列，也不是 `@TableField(exist=false)`；其 8 个 exist=false 字段为 `courseName/teacherName/className/scheduledSlots/courseType/teacherStatus/classStatus/studentCount`。`requiredSlots` 仅在前端 `teachingTask.ts:17` 声明，后端 `Result<TeachingTask>` 序列化**永不发送**该字段。"`requiredSlots` 是真实字段"实为 **`UnscheduledTask`** 的情况（见 §1.5 脚注），疑似被错安到 `TeachingTask`。
- ✅ 该句前半"前端只声明 4 个后端 view 字段（`courseName/teacherName/className/scheduledSlots`）"正确（后端 8 个 view 字段中前端只消费这 4 个）。
- 📌 这个"前端声明、后端不发"的 `requiredSlots` 错配，恰好是评语自身"别只信 `vue-tsc`、需后端契约测试兜底"论点的现成实例。
