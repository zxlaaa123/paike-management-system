# M-16 第1批：ScheduleConflictReport 视图字段收口（调查 + 五层范式确立）

> 分支 `refactor/m16-batch1-conflict-report-vo`，从 `main`(HEAD=`06a531b`) 切出。
> 本批是 M-16 九批治理的第 1 批（最简热身：仅 1 个 view 字段、无 SQL alias、无下游复用），目的是跑通「删字段 + VO + 改下游 + 测试归零」五层范式，作为后续 8 批模板。

## 0. 范围与决策（已拍板，本批直接执行）

- **删字段才算真治理**：删 Entity 的 `@TableField(exist = false)` view 字段，而非只加一层 VO。
- **一波一波推进**：本批只动 `ScheduleConflictReport`（9 个 Entity 里最简）。
- **VO 用普通 POJO**：`@Data @NoArgsConstructor @AllArgsConstructor`，保留 null 序列化（不加 `@JsonInclude(NON_NULL)`），因前端字段多为可选。
- **`timeSlotName` 保留照发**：前端在用，JSON 字段名/类型不变，前端零改动。

## 1. 链路确认（开工前复核结论，与代码一致）

### 1.1 Entity 字段分布
`ScheduleConflictReport.java`（`backend/.../entity`）共 **12 个真实持久化列** + **1 个 view 字段**：

- 持久化列（声明序）：`id` / `semesterId` / `reportNo` / `conflictType` / `objectType` / `objectId` / `objectName` / `timeSlotId` / `relatedScheduleIds` / `description` / `suggestion` / `createTime`（`createTime` 为 `LocalDateTime`）。
- view 字段：`@TableField(exist = false) String timeSlotName`（约 :43-44，本批删除目标）。
- `@TableField` 注解在本 Entity **仅被 timeSlotName 这一个字段使用**，删字段后连同 import 一并删除；`@TableId(IdType.AUTO)`/`@TableName` 保留（本 Entity 无 `@TableLogic`）。

> 勘误：前置提示词写「11 真实列 / VO 12 字段」，实际是 **12 真实列**（字段名清单完整无误，仅数字标号少算 1）。故 **VO = 13 字段**（12 真实 + timeSlotName），序列化测试断言 13 字段集。该数字偏差不影响投资测试改动（见 §4），后者按 `@TableField(exist=false)` 命中数统计，ScheduleConflictReport 恰好 1 处。

### 1.2 双角色：持久化路径 vs 下发路径
- **持久化路径**：`generate(semesterId)` → 9 个 `detectXxx` → `buildReport(...)` `new` 实体、`insert` 入库。**只写 12 个持久化列，不碰 timeSlotName**。返回 `GenerateResult`（不含任何 view 字段）。→ 本批不动。
- **下发路径**：`list(...)` → `conflictReportMapper.selectPage` 查出 → `fillRelationFields(records)` 给每条 `setTimeSlotName(...)` → 返回 `Page<...>`。**唯一返回前端的方法**，Controller 包 `Result<Page<...>>`。→ 本批转 VO。

### 1.3 下游零耦合（已 grep 复核）
`grep getTimeSlotName/setTimeSlotName`（backend/src）：
- `ScheduleConflictReportService` 自身 `setTimeSlotName` ×3（:465/467/470，下发路径）——本批改造对象。
- `TimetableService:227` / `TeacherUnavailableTimeService:93` 的 `setTimeSlotName` 是**各自不同的 Entity/VO**（后续批次目标，本批不碰）。
- `ScheduleConflictReport.timeSlotName` 的 **Java 读取方 = 0**（无 `getTimeSlotName` 调用方）。→ 删 Entity 字段安全，仅需把 setter 迁到 VO。

### 1.4 前端契约
- `frontend/src/api/scheduleConflictReport.ts`：`ScheduleConflictReport` 接口 `timeSlotName?: string`（可选）。
- 唯一渲染处 `frontend/src/views/schedule/ScheduleConflictReportView.vue:223`：`{{ row.timeSlotName || '-' }}`。
- VO 保留同名同类型 → **前端零改动**。

### 1.5 测试守卫
- `tests/stage9.spec.ts:80` 的 `timeSlotName === '周一 第1-2节'` 断言走的是 **timetables 端点（TimetableService）**，与 conflict report **无关**，不受影响。
- `ScheduleConflictReportServiceTest`（mockito）只测 `generate()` 持久化路径，不碰 `list()`/view 字段，不受影响。
- `M16TableFieldViewFieldsInvestigationTest` 锁 43 字段 / 9 Entity 分布、含 `ScheduleConflictReport=1`——本批改 42 / 8（见 §4）。

## 2. VO 字段表（`ScheduleConflictReportVo`，13 字段，声明序照 Entity）

| # | 字段 | 类型 | 来源 |
|---:|---|---|---|
| 1 | id | Long | 持久化列 |
| 2 | semesterId | Long | 持久化列 |
| 3 | reportNo | String | 持久化列（下发前经 `normalizeReportNo` 去 `-NN` 序号后缀）|
| 4 | conflictType | String | 持久化列 |
| 5 | objectType | String | 持久化列 |
| 6 | objectId | Long | 持久化列 |
| 7 | objectName | String | 持久化列 |
| 8 | timeSlotId | Long | 持久化列 |
| 9 | relatedScheduleIds | String | 持久化列 |
| 10 | description | String | 持久化列 |
| 11 | suggestion | String | 持久化列 |
| 12 | createTime | LocalDateTime | 持久化列 |
| 13 | timeSlotName | String | view 字段（`fillRelationFields` 三分支填充）|

包：`com.paike.scheduler.service.vo`。注解：`@Data @NoArgsConstructor @AllArgsConstructor`（普通 POJO，保留 null 序列化）。

## 3. 五层改法（本批确立模板）

1. **新建 VO**：`ScheduleConflictReportVo`（13 字段，§2）。
2. **Entity 删字段**：删 `timeSlotName` + 删 `import ...TableField`（仅此字段用）。
3. **Service `list()` 转 VO**：
   - `Page<ScheduleConflictReportVo> voPage = result.convert(this::toVo);`
   - `fillRelationFields(voPage.getRecords());`
   - `return voPage;`
   - 新增私有 `toVo(ScheduleConflictReport)` 逐字段拷 12 持久化列。
   - `fillRelationFields` 签名改 `List<ScheduleConflictReportVo>`；`extractDayOfWeekFromRelatedSchedules` 的 record 参数改 `ScheduleConflictReportVo`。
   - **必须保留**：① `record.setReportNo(normalizeReportNo(...))`；② timeSlotName 三分支（`timeSlotId` 有→时段 label / `TASK_NOT_FULLY_SCHEDULED`→`"全周"` / 否则按 relatedSchedules 取 weekday，取不到→`"-"`）。
4. **generate()/buildReport() 不动**（只用持久化列，返回 `GenerateResult`）。
5. **Controller `list` 返回类型** → `Result<Page<ScheduleConflictReportVo>>`。

## 4. 测试归零写法

- **新增** `M16ConflictReportVoSerializationTest`：走真实 wire 路径（`mapper.readTree(mapper.writeValueAsString(vo))`，`findAndRegisterModules()` 支持 `LocalDateTime`），断言：
  - 13 字段名集合（§2）；
  - `timeSlotName` 三态：时段 label / `"全周"` / `"-"`；
  - 普通 POJO 不省略 null（任一可选字段为 null 时键仍在）。
- **改** `M16TableFieldViewFieldsInvestigationTest.tableFieldExistFalseViewFieldsRemainOnEntities`：
  - `assertEquals(43 → 42, hits.size())`；
  - `assertEquals(9 → 8, byEntity.size())`（删掉 ScheduleConflictReport 唯一 view 字段后，它命中 0、从 byEntity 消失）；
  - 删 `assertEquals(1, byEntity.get("ScheduleConflictReport"))` 这一行；
  - 其余 8 Entity 计数不变。

## 5. 跑测试 / 前端

```powershell
mvn -f D:\paike\backend\pom.xml "-Dtest=M16ConflictReportVoSerializationTest,M16TableFieldViewFieldsInvestigationTest" test
```
（test 阶段全量编译 main，验证 Entity 删字段 + Service/Controller 改动整体编译通过。）

前端：预期零改动；如担心 `cd D:\paike\frontend; npm run build`。

## 6. 验收口径

- M-16 状态：`部分成立，暂不修复` → `部分修复（第1批 ScheduleConflictReport 已收口，余 8 批）`。
- `20260527bug验证报告.md`：§2 加第1批时间线、§6 加测试行。
- 提交（feature 分支 add backend + 本报告 + `20260527bug验证报告.md`）→ `git checkout main` → `git merge --no-ff`（真 merge commit）→ **不 push**。
