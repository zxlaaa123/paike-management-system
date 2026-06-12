# V8 阶段 1 记录

日期：2026-06-12

分支：`feature/v8-stage1-engine-model` → `feature/v8-stage1-rework`

## 做了什么

### 1. engine/model/ - 内存模型（纯 Java）

| 文件 | 说明 |
|---|---|
| `EngineContext.java` | 不可变内存模型：任务/时段/教室/教师/班级/课程/禁排/规则/权重/锁定项/已有课表/禁用标记 |
| `EngineTask.java` | 引擎任务表示，含 requiredSlots、候选教室列表、studentCount（-1=未配置） |
| `Assignment.java` | (taskIndex, slotIndex, timeSlotIndex, classroomIndex) |
| `EngineSolution.java` | 求解结果：分配列表 + 未排列表 |

所有 ID 映射为 0..n-1 稠密 int 索引，引擎内部只用 int。

### 2. engine/conflict/ - 内存冲突检测器

| 文件 | 说明 |
|---|---|
| `InMemoryConflictDetector.java` | per-record 迭代检测，与 `ScheduleConflictService.checkConflict` 判定顺序完全一致 |

检测顺序（严格对齐 DB 版）：
1. TEACHER_DISABLED
2. TEACHER_UNAVAILABLE
3. CLASS_DISABLED
4. CLASSROOM_DISABLED
5. CLASSROOM_CAPACITY_NOT_ENOUGH（null=未配置=拒绝）
6. ROOM_TYPE_MISMATCH
7-9. 同时段已有记录逐条：TEACHER_CONFLICT → CLASS_CONFLICT → ROOM_CONFLICT
10. TASK_NOT_FULLY_SCHEDULED
11. TEACHER_DAILY_LIMIT
12. CLASS_DAILY_LIMIT
13. SAME_COURSE_SAME_DAY

### 3. service/EngineContextLoader.java - 数据装载器

从 Mapper 装载学期全量数据构建 EngineContext，事务内一次性完成。放在 service 层（不是 engine 包），因为依赖 Mapper 和 Spring。

装载内容：
- 教学任务（status=1、同学期）
- 全部教师/班级/教室/课程（含停用，用于禁用标记）
- 教师禁排时间
- 排课规则（TEACHER_MAX_DAILY_SLOTS / CLASS_MAX_DAILY_SLOTS / ALLOW_SAME_COURSE_SAME_DAY）
- 软规则权重（schedule_rule_weight SOFT 类型）
- 已有 schedule 记录（deleted=0、同学期）→ 初始占用
- 锁定项（active_flag=1）

### 4. 测试

| 测试 | 内容 | 结果 |
|---|---|---|
| T1 `InMemoryConflictDetectorTest` | 20 个用例，覆盖每条硬约束正/反 + 禁用/未配置/同课同日/已有课表 | ✅ 全绿 |
| T2 `ConflictDetectorPairTest` | 自建数据集双跑对拍，23200 次比对，含增量对拍 | ✅ 全绿 |
| T8 `EnginePurityTest` | engine 包无 Spring 注解、无 Mapper 引用、无 Math.random() | ✅ 全绿 |

## 测试结果

```
mvn test -Dtest="com.paike.scheduler.engine.**"
Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

全量 `mvn test`：261 通过，2 失败（`M39ManualDeletedUpdateInvestigationTest`、`M41DeletedZeroConditionCleanupTest`），均为 V7 遗留的架构检查测试，修改前已存在。

V7 遗留的 5 个数据库迁移测试失败已在本分支修复（`test: fix schema assertion drift from v7 migration hardening (36fa83d)`）。

## 返工记录（2026-06-12 审核不通过后）

### 修复项

| # | 问题 | 修复 |
|---|---|---|
| 0 | V7 遗留测试债（DatabaseSchemaScriptTest 4个 + M35 1个） | 更新断言匹配 PREPARE/EXECUTE 新格式，单独提交 |
| 1 | check 判定顺序未对齐 DB 版 | 重写为 per-record 迭代（teacher→class→room for each record at same slot） |
| 2 | 容量未配置语义反了 | studentCount/capacity 用 -1 哨兵表示未配置，null → 拒绝 |
| 3 | SAME_COURSE_SAME_DAY 未实现 | 新增 classCourseDay[classIdx][courseIdx][day] 三维计数器 |
| 4 | 已有课表未装载为初始占用 | 从 schedule 表加载 deleted=0 记录，placeInternal 到检测器 |
| 5 | 停用教师/班级未处理 | 新增 teacherDisabled/classDisabled/classroomDisabled 布尔数组 |
| 6 | ruleWeights 为空 | 从 schedule_rule_weight 表加载 SOFT 类型规则权重 |
| 7 | T2 对拍空转绿 | 重写为自建数据集（固定种子），23200 次逐格比对 + 增量对拍 |

### 撤销

~~偏离文档 3（SAME_COURSE_SAME_DAY 推迟到阶段 2）~~ → 已撤销，已在阶段 1 实现。

## 偏离文档之处（最终版）

1. **EngineContextLoader 放在 service 层而非 engine 包**：V8_02 设计说"Spring 侧由一个新的 V8SolverGenerateStrategy（放在 service 层）负责装载数据"，装载器属于 Spring 侧，放在 `service/` 符合设计意图，同时满足 engine 包纯度约束。
2. **per-record 迭代 vs 全局 busy 数组**：check 方法使用 per-record 迭代（遍历同时段已有 assignment，逐条检查 teacher→class→room），而非全局 busy 数组。这是为了与 DB 版 `ScheduleConflictService.checkConflict` 的判定顺序完全一致。
3. **中/大数据集对拍**：pairTestMedium 和 pairTestLarge 未加 `@Test` 注解（默认不运行），因全量逐格枚举对 DB 调用次数过大。可用 `-Dtest="ConflictDetectorPairTest#pairTestMedium"` 手动运行。
