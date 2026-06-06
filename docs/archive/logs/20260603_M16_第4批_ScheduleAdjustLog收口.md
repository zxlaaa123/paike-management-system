# M-16 第4批收口报告 —— ScheduleAdjustLog

日期：2026-06-03　分支：`refactor/m16-batch4-adjust-log-vo`（建议）
状态：调查完成，待实施

---

## 0. 选型说明（为什么不是 SchedulePlanItem）

本轮原定 `SchedulePlanItem`，侦察后改选 `ScheduleAdjustLog`。原因：

`SchedulePlanItem` 的 view 填充走 `getPlanItems`，其结果喂进 **V5SimulationService 的 compare 子系统**（`loadCompareItems`→`indexByTeachingTaskId`→`buildLoadChanges`/`aggregateLoad`/`resolveLockedCourseNames`/…约 10-15 个方法签名），且 `buildLoadChanges(..., SchedulePlanItem::getTeacherName/::getClassName)`、`:852 after.getCourseName()`、`:995 item.getCourseName()` 直接读 view 字段做展示标签；该集群又与同类「Entity 世界」（`copyDetachedItem`/`new SchedulePlanItem`/`planItemMapper` 增改）交织，干净抽 VO 需逐方法做 Entity/VO 类型甄别，diff 大、风险中高 —— 非 routine。留作后续中等批。

`ScheduleAdjustLog` 的 view 填充走 `listAdjustLogs`，消费方只有 3 个、且与插入路径（`new ScheduleAdjustLog()`）干净隔离，全是机械类型替换 —— 真正的 routine（已经用户确认改选）。

---

## 1. 链路确认

### 1.1 Entity 字段（亲数）
`entity/ScheduleAdjustLog.java`：**19 个真实持久化列** + **5 个 `@TableField(exist=false)` view 字段**。

持久化列（声明序）：id, planId, scheduleId, semesterId, teachingTaskId, oldClassroomId, oldWeekday, oldStartPeriod, oldEndPeriod, newClassroomId, newWeekday, newStartPeriod, newEndPeriod, beforeScore(BigDecimal), afterScore(BigDecimal), conflictFlag, adjustReason, **createdAt(`@TableField("created_at")`)**, **deleted(`@TableLogic`)**。

view 字段：courseName, teacherName, className, oldClassroomName, newClassroomName。

> 注意：本批 **不删 `@TableField` import** —— createdAt 还在用 `@TableField("created_at")`。（与第1批不同。）
> 无 `updatedAt`（只有 createdAt）。

### 1.2 view 字段填充点（唯一）
`service/SchedulePlanExplainService.java:232 fillAdjustRelations(List<ScheduleAdjustLog>)` 填全部 5 个，被 `listAdjustLogs:176` 调用（`selectPage` 后 `fillAdjustRelations(page.getRecords())`）。

### 1.3 view-filled 路径（`listAdjustLogs`）消费方 —— 全部 3 个
1. **`controller/ScheduleAdjustLogController.java:24 list`** → `Result<Page<ScheduleAdjustLog>>` → 前端（GET `/api/v3/schedule-adjust-logs`）。
2. **`service/V4ScheduleSourceService.java:75 getPlanAdjustmentLogs`** → `listAdjustLogs(...).getRecords()`，读 `getCourseName()`(:83) / `getOldClassroomName()`(:86) / `getNewClassroomName()`(:89) + 持久化字段；`:77 Comparator.comparing(ScheduleAdjustLog::getCreatedAt)`。
3. **`service/V5SimulationService.java:304`** → `vo.setAdjustLogs(adjustLogs)`（嵌进 `V5SimulationPlanDetailVo.adjustLogs`）+ `:308 buildPersistedLocalReplanSummary(task, plan, adjustLogs)`（`:630` 该方法**只读持久化字段** getTeachingTaskId/getAdjustReason/getOld*/getNew* 周期段，**不读 view 字段**）。

### 1.4 Entity 世界（不碰、与展示路径隔离）
- 插入：`V4ScheduleAdjustmentService:182` / `V5SimulationService:611` / `SchedulePlanService:172`（`new ScheduleAdjustLog()` + `appendAdjustLog`）。
- 计数/直查：`V4ScheduleSourceService:51 selectCount`、`V5ConsistencyCheckService:306 selectList`（读 getTeachingTaskId，持久化）。
- 这些都不经 `listAdjustLogs`、不读 view 字段 → 保持 Entity，不动。

> 排查确认：V5 `:852 after.getCourseName()`、`:995 item.getCourseName()` 的接收者是 **SchedulePlanItem**，不是 ScheduleAdjustLog；old/newClassroomName 全库唯一属本实体，读取方仅 V4ScheduleSourceService。前端 JSON 逐字节不变 → **前端零改动**。

---

## 2. VO 字段表 —— `ScheduleAdjustLogVo`（24 字段）

= Entity 当前被 Jackson 序列化的全部字段（19 持久化，含 `createdAt`/`deleted`）+ 5 view 字段，字段名/类型/顺序照 Entity 声明序。普通 POJO `@Data @NoArgsConstructor @AllArgsConstructor`，**不加 NON_NULL**。

| # | 字段 | 类型 | 来源 |
|---|---|---|---|
| 1 | id | Long | 持久化 |
| 2 | planId | Long | 持久化 |
| 3 | scheduleId | Long | 持久化 |
| 4 | semesterId | Long | 持久化 |
| 5 | teachingTaskId | Long | 持久化 |
| 6 | oldClassroomId | Long | 持久化 |
| 7 | oldWeekday | Integer | 持久化 |
| 8 | oldStartPeriod | Integer | 持久化 |
| 9 | oldEndPeriod | Integer | 持久化 |
| 10 | newClassroomId | Long | 持久化 |
| 11 | newWeekday | Integer | 持久化 |
| 12 | newStartPeriod | Integer | 持久化 |
| 13 | newEndPeriod | Integer | 持久化 |
| 14 | beforeScore | BigDecimal | 持久化 |
| 15 | afterScore | BigDecimal | 持久化 |
| 16 | conflictFlag | Integer | 持久化 |
| 17 | adjustReason | String | 持久化 |
| 18 | createdAt | LocalDateTime | 持久化（`created_at`） |
| 19 | deleted | Integer | 持久化（`@TableLogic`，恒序列化 0） |
| 20 | courseName | String | view |
| 21 | teacherName | String | view |
| 22 | className | String | view |
| 23 | oldClassroomName | String | view |
| 24 | newClassroomName | String | view |

---

## 3. 五层改法

1. **新建** `service/vo/ScheduleAdjustLogVo.java`（24 字段，上表序）。
2. **Entity** `ScheduleAdjustLog.java` 删 5 个 view 字段（**保留 `@TableField` import**，createdAt 在用）。
3. **Service** `SchedulePlanExplainService.java`：
   - `listAdjustLogs` 返回 `Page<ScheduleAdjustLogVo>`：`selectPage` 取 Entity 页 → 建 `new Page<>(current,size,total)` → `setRecords(records.stream().map(this::adjustLogToVo)...)` → `fillAdjustRelations(voPage.getRecords())` → 返回 voPage。
   - `fillAdjustRelations` 签名改 `List<ScheduleAdjustLogVo>`（仅读持久化 getter + set 5 view，逻辑不动）。
   - 新增私有 `adjustLogToVo(ScheduleAdjustLog)` 逐字段拷 19 持久化列（命名避开 Batch2 已有的 `toVo(ScheduleUnassignedTask)`）。
4. **消费方改类型**（仅换类型、逻辑不动）：
   - `ScheduleAdjustLogController.list` → `Result<Page<ScheduleAdjustLogVo>>`，import 换 VO。
   - `V4ScheduleSourceService:75` `logs` → `List<ScheduleAdjustLogVo>`，`:77` 比较器 `ScheduleAdjustLogVo::getCreatedAt`，加 VO import。
   - `V5SimulationService:304` `adjustLogs` → `List<ScheduleAdjustLogVo>`；`:630 buildPersistedLocalReplanSummary` 参数 → `List<ScheduleAdjustLogVo>`，加 VO import。
   - `V5SimulationPlanDetailVo.adjustLogs` 字段 → `List<ScheduleAdjustLogVo>`，import 换 VO。
5. **测试**：
   - 新增 `architecture/M16AdjustLogVoSerializationTest`：真实 wire 路径 `mapper.readTree(writeValueAsString(vo))` + `findAndRegisterModules()`；断言 24 字段集；填充态（5 view 有值）+ 关联缺失 null 态（5 view + createdAt 为 null）；`deleted=0`。
   - 改 `architecture/M16TableFieldViewFieldsInvestigationTest`：`hits` 34→**29**，`byEntity.size()` 6→**5**，删 `assertEquals(5, byEntity.get("ScheduleAdjustLog"))`（剩 Schedule10/TeachingTask8/SchedulePlanItem5/UnscheduledTask4/SchedulePlan2，和=29）。
   - 改 `service/M43SimulationDiscardOrderInvestigationTest:99`：`Page<ScheduleAdjustLogVo> adjustLogPage = new Page<>()`，import 换 VO（mock `listAdjustLogs` 返回类型对齐）。

---

## 4. 测试归零写法 / 定向命令

```
mvn -f D:\paike\backend\pom.xml "-Dtest=M16AdjustLogVoSerializationTest,M16TableFieldViewFieldsInvestigationTest,M43SimulationDiscardOrderInvestigationTest,V4ScheduleSourceServiceTest" test
```
（test 阶段全量编译 main，验证 7 处主代码类型替换全部通过；不启 Spring Boot。）

---

## 5. 影响面小结

主代码 7 文件（1 新建）+ 测试 3 文件（1 新建）= 10 文件，全机械类型替换，无逻辑改动，无 Entity/VO 甄别。前端零改动（JSON 逐字节不变，含 `deleted:0` 与 5 个 view 字段填充/null）。

收口后：view 字段命中 34→**29**、承载 Entity 6→**5**，M-16 进度 3/9→**4/9**。
