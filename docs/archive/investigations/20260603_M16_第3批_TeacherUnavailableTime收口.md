# M-16 第3批：TeacherUnavailableTime 视图字段收口（范式扩展：多端点全转 VO）

> 分支 `refactor/m16-batch3-teacher-unavailable-vo`，从 `main`(HEAD=`b63cfe1`，第2批 deleted fix 合并点) 切出。
> M-16 九批治理第 3 批。本批在前两批基础上新增验证点：**同一 Entity 被多个端点返回（list/create/update），且全部填充 view 字段** → 三端点统一转 VO。同时首次应用「`deleted` 进 VO（严格逐字段）」规则。

## 0. 选型理由（为何第3批选它，不按蓝图序号）

按真实难度（端点数 + SQL alias + view 字段下游读取方 + 嵌套-in-VO）排，剩 7 个里无 mapper XML alias 的有 `TeacherUnavailableTime`(5)/`SchedulePlanItem`(5)/`ScheduleAdjustLog`(5)/`SchedulePlan`(2,特例)。侦察结论：

| 候选 | 端点 | view 字段内部 Java 消费方 | 嵌套-in-VO | 评级 |
|---|---|---|---|---|
| **TeacherUnavailableTime** | 3（list/create/update，**同一 controller/service**）| **0**（仅前端读）| 无 | **最干净** |
| ScheduleAdjustLog | 1（list）| 有（V4ScheduleSourceService 读 old/newClassroomName）| **有**（V5SimulationPlanDetailVo 内嵌 `List<ScheduleAdjustLog>`）| 4 文件波及 |
| SchedulePlanItem | getItems 等 | 核心实体、多处内部读 | 待查 | 高耦合 |
| SchedulePlan | 2（list+getById，getById 被 ScheduleScoreController 复用）| — | — | 两 view 字段恒 null 疑漏填，单独查 |

→ `TeacherUnavailableTime` 改面全收敛在 `TeacherUnavailableTimeService` + `TeacherUnavailableTimeController` + 测试，无任何 V4/V5/其它 VO 波及，是当前最优推进项。

## 1. 链路确认（已源码核实）

### 1.1 Entity 字段分布
`TeacherUnavailableTime.java`：**9 个持久化列** + **5 个 view 字段**：
- 持久化列（声明序）：`id`(@TableId) / `teacherId`(@NotNull) / `timeSlotId`(@NotNull) / `reason` / `status` / `remark` / `deleted`(@TableLogic) / `createTime`(@TableField("create_time")) / `updateTime`(@TableField("update_time"))。
- view 字段（:44-57）：`teacherName` / `department` / `timeSlotName` / `dayOfWeek` / `periodNo`，均 `@TableField(exist = false)`，本批删除目标。
- `@TableField` 注解还被 `create_time`/`update_time` 用 → **import 保留**；`@TableId`/`@TableName`/`@TableLogic` 保留。

> VO = **14 字段**（9 持久化〔含 `deleted` 恒 0、`createTime`、`updateTime`〕+ 5 view）。按「deleted 进 VO」规则，VO 镜像 Entity 当前序列化的全部字段以保 JSON 逐字段不变（前端虽不声明 `deleted`/`updateTime`，但历史响应含之）。

### 1.2 三端点全填充 view 字段（本批关键差异）
`TeacherUnavailableTimeController`（3 个返回 Entity 的端点，全部经 service 填充）：
1. `list`（:23）→ `Result<Page<TeacherUnavailableTime>>`：`service.list` 查出后 `fillRelationFields` 填 5 字段。
2. `create`（:39）→ `Result<TeacherUnavailableTime>`：`service.create` insert 后 `fillRelationFields(singletonList(entity))` 填（:122）。
3. `update`（:45）→ `Result<TeacherUnavailableTime>`：`service.update` updateById 后 `fillRelationFields(singletonList(existing))` 填（:145）。

→ 与前两批「仅 list 下发」不同：**三端点都返回填充后的 Entity** → 三端点统一转 VO（create/update 也返回 `...Vo`，行为/字段逐一不变）。`delete`/`updateStatus` 返回 `Result<Void>`，不动。

### 1.3 下游零耦合（已 grep 复核）
- `TeacherUnavailableTimeService.list/create/update` 仅被 `TeacherUnavailableTimeController` 调用，**无内部 service 调用方**。
- `TeacherUnavailableTimeService` 的其它注入方（SchedulePlanService/ScheduleConflictService/V5RuleEvaluation/V5ConsistencyCheck/V5CandidatePosition/V4ScheduleAdjustment）只调 `isUnavailable(...)`（返回 boolean，持久化 count），不碰 view 字段。
- `TeacherUnavailableTime` Entity 在 ScheduleConflictReportService/ScheduleScoreReportService/SchedulingSupport/SchedulingReferenceLoader 被 `mapper.selectList` 读，但只读 `teacherId`/`timeSlotId`/`reason`/`status` 等**持久化字段**，**零 view 字段读取方**。
- view 字段（teacherName/department/timeSlotName/dayOfWeek/periodNo）只在 `fillRelationFields` set、只由前端序列化读。

### 1.4 前端契约
- `frontend/src/api/teacherUnavailableTime.ts:4-17`：接口含 `teacherName`(非可选)/`department?`/`timeSlotName`(非可选)/`dayOfWeek?`/`periodNo?` + 持久化字段；**未声明** `deleted`/`updateTime`（历史响应含之、前端忽略）。
- VO 保留全部同名同类型字段（多发的 `deleted`/`updateTime` 前端忽略、无害）→ **前端零改动**。

### 1.5 测试守卫
- `TeacherUnavailableTimeServiceTest`：两用例均「校验失败抛 BusinessException、不 insert/update」，**不接收 create/update 返回值** → 返回类型 Entity→VO 不破。
- `SchedulingSupportTest.newUnavailable`：只 set `teacherId`/`timeSlotId`，不碰 view 字段 → 删字段不破。
- `M41DeletedZeroConditionCleanupTest:48`：断言 Entity 含 `@TableLogic` → 保留、不破。
- `ControllerPaginationValidationTest`：只校验 list 的 page/size 注解 → 不动、不破。
- `M16TableFieldViewFieldsInvestigationTest`：锁 `TeacherUnavailableTime=5`，本批改 39→34、7→6。

## 2. VO 字段表（`TeacherUnavailableTimeVo`，14 字段，声明序照 Entity）

| # | 字段 | 类型 | 来源 |
|---:|---|---|---|
| 1 | id | Long | 持久化 |
| 2 | teacherId | Long | 持久化 |
| 3 | timeSlotId | Long | 持久化 |
| 4 | reason | String | 持久化 |
| 5 | status | Integer | 持久化 |
| 6 | remark | String | 持久化 |
| 7 | deleted | Integer | 持久化（`@TableLogic`，恒 0；保留以维持 JSON 逐字段不变）|
| 8 | createTime | LocalDateTime | 持久化（`create_time`）|
| 9 | updateTime | LocalDateTime | 持久化（`update_time`）|
| 10 | teacherName | String | view（fillRelationFields 填）|
| 11 | department | String | view |
| 12 | timeSlotName | String | view |
| 13 | dayOfWeek | Integer | view |
| 14 | periodNo | Integer | view |

包：`com.paike.scheduler.service.vo`。注解：`@Data @NoArgsConstructor @AllArgsConstructor`（普通 POJO，保留 null 序列化）。**含 `deleted`**（严格逐字段）。

## 3. 五层改法（前两批模板 + 多端点全转 VO）

1. **新建 VO** `TeacherUnavailableTimeVo`（14 字段，§2）。
2. **Entity 删字段**：删 5 个 `@TableField(exist=false)` view 字段；`@TableField` import 保留（create_time/update_time 用）。
3. **Service 改 VO**：
   - `list` 返回 `Page<TeacherUnavailableTimeVo>`：保留 teacherName 过滤的空分页早返回（`new Page<>(page,size,0)`）；查出后建 VO 分页（`new Page<>(current,size,total)` + `setRecords(map(toVo))`）→ `fillRelationFields(voPage.getRecords())`。
   - `create`/`update` 返回 `TeacherUnavailableTimeVo`：insert/updateById 后 `toVo(entity)` → `fillRelationFields(singletonList(vo))` → 返回 vo。
   - `fillRelationFields` 签名改 `List<TeacherUnavailableTimeVo>`、循环变量改 Vo（setter/getter 均在 VO 上）。
   - 新增私有 `toVo(entity)` 拷 9 持久化列（含 `deleted`/`createTime`/`updateTime`）。
4. **Controller 改类型**：`list`/`create`/`update` 返回 `Result<...Vo>`、局部变量改 Vo 类型；entity import 换 VO import（无其它引用）。
5. **测试**：新增 `M16UnavailableTimeVoSerializationTest`（14 字段集 + view 填充态/null 态 + deleted=0）；改 `M16TableFieldViewFieldsInvestigationTest` 计数 `34`、`6`、删 `assertEquals(5, byEntity.get("TeacherUnavailableTime"))`。

## 4. 跑测试 / 前端

```powershell
mvn -f D:\paike\backend\pom.xml "-Dtest=M16UnavailableTimeVoSerializationTest,M16TableFieldViewFieldsInvestigationTest,TeacherUnavailableTimeServiceTest" test
```
前端预期零改动。

## 5. 验收口径

- M-16 验证报告：状态行更新「第1+2+3批已收口，余 6 批」；§2 加第3批时间线；§6 加测试行；view 字段命中 39→34、承载 Entity 7→6。
- 提交（feature 分支 add backend + 本报告 + `20260527bug验证报告.md`）→ `git checkout main` → `git merge --no-ff` → **不 push**。
