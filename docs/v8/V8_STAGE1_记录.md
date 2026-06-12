# V8 阶段 1 记录

日期：2026-06-12

分支：`feature/v8-stage1-engine-model`

## 做了什么

### 1. engine/model/ - 内存模型（纯 Java）

| 文件 | 说明 |
|---|---|
| `EngineContext.java` | 不可变内存模型：任务/时段/教室/教师/班级/课程/禁排/规则/权重/锁定项 |
| `EngineTask.java` | 引擎任务表示，含 requiredSlots、候选教室列表 |
| `Assignment.java` | (taskIndex, slotIndex, timeSlotIndex, classroomIndex) |
| `EngineSolution.java` | 求解结果：分配列表 + 未排列表 |

所有 ID 映射为 0..n-1 稠密 int 索引，引擎内部只用 int。

### 2. engine/conflict/ - 内存冲突检测器

| 文件 | 说明 |
|---|---|
| `InMemoryConflictDetector.java` | O(1) place/remove/check，与 `ScheduleConflictService.checkConflict` 硬约束语义对齐 |

检测顺序：TEACHER_UNAVAILABLE → TEACHER_CONFLICT → CLASS_CONFLICT → ROOM_CONFLICT → CLASSROOM_CAPACITY_NOT_ENOUGH → ROOM_TYPE_MISMATCH → TASK_NOT_FULLY_SCHEDULED → TEACHER_DAILY_LIMIT → CLASS_DAILY_LIMIT

### 3. service/EngineContextLoader.java - 数据装载器

从 Mapper 装载学期全量数据构建 EngineContext，事务内一次性完成。放在 service 层（不是 engine 包），因为依赖 Mapper 和 Spring。

### 4. 测试

| 测试 | 内容 | 结果 |
|---|---|---|
| T1 `InMemoryConflictDetectorTest` | 13 个用例，覆盖每条硬约束正/反 | ✅ 全绿 |
| T2 `ConflictDetectorPairTest` | 双跑对拍：所有学期全量 (task, slot, room) 组合逐格比对 | ✅ 全绿 |
| T8 `EnginePurityTest` | engine 包无 Spring 注解、无 Mapper 引用、无 Math.random() | ✅ 全绿 |

## 测试结果

```
mvn test -Dtest="com.paike.scheduler.engine.**"
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

全量 `mvn test` 有 7 个已有的数据库迁移测试失败（`DatabaseSchemaScriptTest`、`M35RelatedScheduleIdsColumnLengthTest`），与本次改动无关（修改前已存在）。

## 偏离文档之处

1. **EngineContextLoader 放在 service 层而非 engine 包**：V8_02 设计说"Spring 侧由一个新的 V8SolverGenerateStrategy（放在 service 层）负责装载数据"，装载器属于 Spring 侧，放在 `service/` 符合设计意图，同时满足 engine 包纯度约束。
2. **每日上限检查顺序**：check 方法中 `TASK_NOT_FULLY_SCHEDULED` 在 `TEACHER_DAILY_LIMIT` / `CLASS_DAILY_LIMIT` 之前检查。这与 `ScheduleConflictService.checkConflict` 的检查顺序一致（先检查每周课时上限 10，再检查每日上限 11）。
3. **同课同日检查（SAME_COURSE_SAME_DAY）**：InMemoryConflictDetector 中暂未实现此检查，因为它需要按 (classId, courseId, day) 三元组统计，当前数据结构不直接支持。此检查将在阶段 2 完善（需要在 EngineContext 中增加 classCourseDay 三维数组）。
4. **锁定项处理**：当前实现将锁定的 SchedulePlanItem 转为 Assignment 预占用，通过 SchedulePlanItem 的 weekday/startPeriod/classroomId 映射。仅支持有完整信息的锁定项，信息不完整的跳过。
