# M-16 第5批收口报告 —— UnscheduledTask

日期：2026-06-03　分支：`refactor/m16-batch5-unscheduled-task-vo`

---

## 1. 选型说明

余 5 候选里，`SchedulePlan` 特例（恒 null、需查 bug）、`SchedulePlanItem` 中等（V5 compare 集群），三个 alias 组里最简的是 `UnscheduledTask`(4 字段)。选它。

## 2. Entity 字段（亲数）

`entity/UnscheduledTask.java`：**13 个真实持久化列** + **4 个 `@TableField(exist=false)` view 字段**。

持久化列：id, batchId, semesterId, taskId, courseId, teacherId, classId, requiredSlots, scheduledSlots, remainingSlots, reasonType, reasonMessage, createTime。

view 字段：courseName, teacherName, className, batchNo。

- **无 `@TableLogic`** / 无 `deleted` → VO 不含 deleted。
- **无 `@TableField(...)` 列名映射** → `@TableField` import 可删。

## 3. alias 模式（不同于前 4 批）

view 字段由 `UnscheduledTaskMapper.xml` 的 `selectFilteredPage` SQL 别名填充（`c.course_name AS courseName` 等），**非 Java fillRelations**。改造方式：

- Entity 删 view 字段（`@TableField` import 删除）。
- Mapper XML `resultType` 从 Entity 改为 `UnscheduledTaskVo`。
- Mapper 接口 `selectFilteredPage` 返回类型 → `Page<UnscheduledTaskVo>`（`BaseMapper<UnscheduledTask>` 泛型保持 Entity）。
- **无需 toVo 方法**：MyBatis 直接映射 SQL 别名到 VO 字段，比前 4 批更简。

## 4. 五层改法

1. **新建** `service/vo/UnscheduledTaskVo.java`（17 字段）。
2. **Entity** `UnscheduledTask.java` 删 4 view 字段 + 删 `@TableField` import。
3. **XML** `UnscheduledTaskMapper.xml` `resultType` → `com.paike.scheduler.service.vo.UnscheduledTaskVo`。
4. **Mapper** `UnscheduledTaskMapper.java`：加 VO import、`selectFilteredPage` 返回 `Page<UnscheduledTaskVo>`。
5. **Service** `UnscheduledTaskService.list` → `Page<UnscheduledTaskVo>`（加 VO import）。
6. **Controller** `UnscheduledTaskController` `list`/`listByBatch` → `Result<Page<UnscheduledTaskVo>>`（import 换 VO）。
7. **测试**：
   - 新增 `M16UnscheduledTaskVoSerializationTest`（17 字段集 + 填充/null 两态，无 deleted）。
   - 改 `M16TableFieldViewFieldsInvestigationTest`：29→**25**、5→**4**、删 UnscheduledTask=4。
   - 改 `UnscheduledTaskServiceTest`：`Page<UnscheduledTask>` → `Page<UnscheduledTaskVo>`（3 处）。

## 5. 消费方确认

- Controller `list`/`listByBatch` → 前端（GET `/api/unscheduled-tasks`、`/api/unscheduled-tasks/batch/{batchId}`）。JSON 逐字节不变 → 前端零改动。
- `AutoScheduleService:133` 调 `addUnscheduledTask`、`:82/89` 调 `clearBySemester` → Entity 世界，不碰。
- `UnscheduledTaskService` 的 `addUnscheduledTask`/`clearByBatchId`/`clearBySemester` → Entity 世界，不碰。

## 6. 影响面

主 6 文件 + 测试 3 文件 = 9 文件。alias 模式首次确立（XML resultType→VO + MyBatis 直接映射、无 toVo），为后续 TeachingTask/Schedule 提供模板。

收口后：view 字段命中 **29→25**、承载 Entity **5→4**，M-16 进度 **4/9→5/9**。
