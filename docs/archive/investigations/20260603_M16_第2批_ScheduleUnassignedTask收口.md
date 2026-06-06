# M-16 第2批：ScheduleUnassignedTask 视图字段收口（范式扩展：含内部 Java 消费方）

> 分支 `refactor/m16-batch2-unassigned-task-vo`，从 `main`(HEAD=`17586f9`，第1批 merge 点) 切出。
> M-16 九批治理第 2 批。本批在第1批五层范式上**新增一层验证点：view 字段不仅下发前端、还被内部 Java 服务读取**——确立「有内部消费方」时的改法。

## 0. 选型理由（为何不按蓝图序号走 SchedulePlan）

蓝图 §4.2 序号 3 是 SchedulePlan(2 字段)，但真实难度按「端点数 + SQL alias + view 字段下游读取方」排，不是字段数。剩 8 个里无 mapper XML alias 的只有 3 个（其余 UnscheduledTask/TeachingTask/Schedule 有 alias）。两候选对比：

| 维度 | ScheduleUnassignedTask（本批） | SchedulePlan |
|---|---|---|
| view 字段 | 3（courseName/teacherName/className，标准关联名）| 2（semesterName/strategyName）|
| SQL alias | 无 | 无 |
| 返回端点 | 单端点 `getUnassignedTasks→List` | 双端点 `list`(Page)+`getById` |
| 跨 controller 复用 | 无 | `getById` 被 `ScheduleScoreController` 复用 2 次 |
| view 字段填充 | `fillUnassignedRelations` 标准三字段拷（与第1批同构）| **零填充**——`get/setSemesterName/StrategyName` 全库 0 命中、恒 null（疑似漏填 bug，类比 requiredSlots）|

结论：ScheduleUnassignedTask 是第1批的结构同构放大版（单端点、无 alias、三字段标准拷），范式直接复用、风险最低；SchedulePlan 含跨 controller 改面 + 恒 null 语义疑点，留后续单独处理。

## 1. 链路确认（已源码核实）

### 1.1 Entity 字段分布
`ScheduleUnassignedTask.java`：**8 个持久化列** + **3 个 view 字段**：
- 持久化列：`id` / `planId` / `semesterId` / `teachingTaskId` / `reasonCode` / `reasonMessage` / `suggestion` / `createdAt`（`@TableField("created_at")`）+ `deleted`（`@TableLogic`，软删除）。
- view 字段（:39-46）：`courseName` / `teacherName` / `className`，均 `@TableField(exist = false)`，本批删除目标。
- **注意与第1批不同**：`@TableField` 注解还被 `@TableField("created_at")`(:33) 使用，删 3 个 view 字段后 **`@TableField` import 保留**（第1批是删字段后 import 也删）。`@TableId`/`@TableName`/`@TableLogic` 保留。

> 【2026-06-03 勘误】最初判 `deleted` 不下发、VO 取 11 字段；实测 `application.yml` 无 jackson 全局配置、Entity `@TableLogic deleted` 被 Jackson 序列化为 `0`（历史 JSON **确含** `deleted`）。按拍板「`deleted` 进 VO（严格逐字段）」，VO = **12 字段**：id/planId/semesterId/teachingTaskId/reasonCode/reasonMessage/suggestion/createdAt/**deleted**/courseName/teacherName/className（已由 `fix/m16-batch2-vo-deleted-field` 回填）。

### 1.2 唯一查询入口 + 双消费方（本批关键差异）
`SchedulePlanExplainService.listUnassignedTasks(planId)`（:118-125）：`selectList` 查出 → `fillUnassignedRelations` 填三字段 → 返回。**该方法被两处消费**：
1. **下发前端**：`SchedulePlanController.getUnassignedTasks`（:126-129）→ `Result<List<ScheduleUnassignedTask>>`。
2. **内部 Java 读取**：`V4ScheduleRiskService`（:70）拿 list → `detectUnscheduledTasks`（:281-311）**读 `getCourseName()/getTeacherName()/getClassName()/getReasonMessage()/getSuggestion()/getTeachingTaskId()`**（:284-307，组装风险项标题/受影响对象/明细行）。

→ 删 Entity view 字段后，**两个消费方都要改吃 VO**（类型替换，读取方法签名不变、逻辑不动）。这是本批相对第1批的唯一新增面。

### 1.3 不受影响的路径
- `SchedulePlanExplainService.summarizeUnassignedTasks`（:127-138）：只按 `reasonCode` 分组计数产 `UnassignedSummaryVo`，**不碰 view 字段** → 不动。
- `clearPlanArtifacts`(:48)、`appendUnassignedTask`(:108-115)、`V4ScheduleReplanService:193`：均按持久化字段 `delete/insert/selectList` → 不动。
- view 字段填充逻辑 `fillUnassignedRelations`（:168-213）：批量查 task→course/teacher/class，三字段 set；逻辑不变，仅入参类型 Entity→VO。

### 1.4 前端契约
- `frontend/src/api/schedulePlan.ts:63-75` 的 `ScheduleUnassignedTask` 接口：`courseName?` / `teacherName?` / `className?` 均可选；其余持久化字段一致。
- VO 保留同名同类型 → **前端零改动**。

### 1.5 测试守卫
- 无后端测试直接断言 ScheduleUnassignedTask 的 view 字段（grep 确认）。
- `M16TableFieldViewFieldsInvestigationTest` 锁现状：本批后 `ScheduleUnassignedTask=3` 命中归零、从 byEntity 消失。

## 2. VO 字段表（`ScheduleUnassignedTaskVo`，12 字段，声明序照 Entity）

| # | 字段 | 类型 | 来源 |
|---:|---|---|---|
| 1 | id | Long | 持久化 |
| 2 | planId | Long | 持久化 |
| 3 | semesterId | Long | 持久化 |
| 4 | teachingTaskId | Long | 持久化 |
| 5 | reasonCode | String | 持久化 |
| 6 | reasonMessage | String | 持久化 |
| 7 | suggestion | String | 持久化 |
| 8 | createdAt | LocalDateTime | 持久化（`created_at`）|
| 9 | deleted | Integer | 持久化（`@TableLogic`，恒 0；保留以维持 JSON 逐字段不变）|
| 10 | courseName | String | view（fillUnassignedRelations 填）|
| 11 | teacherName | String | view |
| 12 | className | String | view |

包：`com.paike.scheduler.service.vo`。注解：`@Data @NoArgsConstructor @AllArgsConstructor`（普通 POJO，保留 null 序列化）。**含 `deleted`**（镜像 Entity 当前序列化、保 JSON 逐字段不变）。

## 3. 五层改法（第1批模板 + 内部消费方一层）

1. **新建 VO** `ScheduleUnassignedTaskVo`（12 字段，§2）。
2. **Entity 删字段**：删 courseName/teacherName/className 三个 `@TableField(exist=false)` 字段；**`@TableField` import 保留**（created_at 仍用）。
3. **Service 改 VO**：`listUnassignedTasks` 返回 `List<ScheduleUnassignedTaskVo>`——查出 entity→`map(this::toVo)`→`fillUnassignedRelations` 改吃 `List<ScheduleUnassignedTaskVo>`；新增私有 `toVo(entity)` 拷 8 持久化列。`summarizeUnassignedTasks` 不动。
4. **两个消费方同步改类型**：
   - `SchedulePlanController.getUnassignedTasks` → `Result<List<ScheduleUnassignedTaskVo>>`，import 换 VO（entity import 删，无其他引用）。
   - `V4ScheduleRiskService`：:70 变量 + :281 `detectUnscheduledTasks` 入参 + :282 循环变量改 VO 类型，读取逻辑不变；import 换 VO（entity import 删，无其他引用）。
5. **测试**：新增 `M16UnassignedTaskVoSerializationTest`（12 字段集 + 三字段填充态/null 保留 + deleted=0）；改 `M16TableFieldViewFieldsInvestigationTest` 计数 `42→39`、`8→7`、删 `assertEquals(3, byEntity.get("ScheduleUnassignedTask"))`。

## 4. 跑测试 / 前端

```powershell
mvn -f D:\paike\backend\pom.xml "-Dtest=M16UnassignedTaskVoSerializationTest,M16TableFieldViewFieldsInvestigationTest" test
```
另跑 `V4ScheduleRiskServiceTest`（若存在）确认内部消费方零回归。前端预期零改动。

## 5. 验收口径

- M-16 验证报告：状态行更新「第1批+第2批已收口，余 7 批」；§2 加第2批时间线；§6 加测试行；view 字段命中 42→39、承载 Entity 8→7。
- 提交（feature 分支 add backend + 本报告 + `20260527bug验证报告.md`）→ `git checkout main` → `git merge --no-ff` → **不 push**。
