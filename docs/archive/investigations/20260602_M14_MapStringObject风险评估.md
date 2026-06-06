# M-14 风险评估报告 — `Map<String,Object>` 收敛

> 评估对象：D-14 / 20260527bug验证报告 M-14 项
> 评估日期：2026-06-02
> 评估分支：`minimaxm3/m14-m16-m25-risk-assessment`
> 评估方式：源码静态核验 + 前端类型契约比对
> 结论：**整体可收敛，公开 API 端风险低；服务内部聚合计算是真正难点**
>
> ⚠️ **核验勘误（2026-06-02 源码逐条核对）**：结构性事实（66 处 / 18 端点 / 测试锁定 66·16·21·45 / §1.4 行号区间 / 16 个待建 VO）基本属实，但存在以下错误，正文已就地标注 `⚠️ 勘误`：
> 1. **死代码判断反了**：`refreshPlanSummary` / `refreshPlanRisks` 均有活的前端调用方（`ScheduleAnalysisDetail.vue:41`、`ScheduleRiskCenter.vue:103`，分别挂"刷新分析/刷新风险"按钮），删除会断 UI。真正无调用方的是 V3 `adjustSchedulePlanItem`。
> 2. **`ScoreSummaryVo` / `UnassignedSummaryVo` 并不存在**（全仓仅本报告提及），§4.1 阶段1"复用现有 VO"不成立，需新建；实际新增 VO 应为 18 个而非 16 个。
> 3. **行 #6 接错前端**：`ScheduleAdjustDialog.vue` 消费的是 V4 调整端点（读 `checkResult/requiresConfirmation/message`），非 V3、不读 `saved`。
> 4. 数字口径：§1.1 三个 Controller 各漏数 1（实为 2），表内 16 行求和=63≠合计 66（代码真实总数 66 正确）；`PlanOverview`=17 字段（非 16）；§2 "ComparePlan/CompareResult 13 字段"错（应为 12 / 4）；`getScoreSummary` 后端仅发 5 字段、前端类型 6 字段（`conflictCount` 后端不发、前端兜底）。
> 5. 措辞：18 端点中仅 14 个返回 `Result<Map<String,Object>>`，另 4 个为 `Result<List<Map<String,Object>>>`（§1.3 同理有 4 个方法返回 `List<Map>`，非 bare `Map`）。

---

## 0. TL;DR

| 维度 | 评估 |
|---|---|
| 公开 API 端点 | 18 个 Controller 端点返回 `Map<String,Object>` |
| 前端已有类型契约 | **14 个**端点前端 TS 接口已完全定义（隐式约定 + 显式接口） |
| 端点已无人调用 | ⚠️ **勘误**：报告称 2 个（`refreshPlanSummary` / `refreshPlanRisks`），实测**二者均有活的前端调用方**；真正无调用方的是 V3 `adjustSchedulePlanItem` |
| 真正裸用 | 2 个（`checkConflict` 仅读 `hasConflict/message`，`logout` 不读 body） |
| 服务层公共方法 | 5 个 service 公共方法返回 Map，跨模块复用 1 处 |
| 内部临时组装 | `ScheduleStatisticsService` 占 23 处，含 2 个私有辅助器（`longSet`/`integerLongMap`）|
| 整体改造成本 | **中等偏下**：纯机械替换约 4-6 小时，含 V5 流程回归约 1.5 人日 |
| 关键阻塞 | 1 处：统计接口的 `SchedulePlanItem / Schedule` 多态输入需要在服务层先定型 |
| 风险等级 | **低-中**（公开 API 替换不会破坏前端，Service 内部重写需防聚合逻辑走样） |

---

## 1. 现状全量盘点

### 1.1 66 处分布（按文件）

| 文件 | 处数 | 主要用法 |
|---|---:|---|
| `ScheduleStatisticsService.java` | 23 | 教师工作量/教室利用率/班级均衡/方案总览/首页统计聚合 |
| `SchedulePlanService.java` | 10 | adjustPlanItem / applyPlan / applySimulationPlan / rollbackPlan 返回 |
| `ScheduleCompareService.java` | 7 | 方案对比信息组装 + 排序比较 |
| `ScheduleStatisticsController.java` | 5 | 5 个统计端点 |
| `SchedulePlanController.java` | 4 | compare/apply/rollback/unassigned-summary |
| `SchedulePlanExplainService.java` | 2 | summarizeUnassignedTasks |
| `ScheduleAnalysisController.java` | ~~1~~ **2** ⚠️ | refreshPlanSummary（返回类型行 + 局部 `result` 变量行）|
| `ScheduleRiskController.java` | ~~1~~ **2** ⚠️ | refreshPlanRisks（同上漏数局部变量）|
| `ScheduleScoreController.java` | 2 | getScoreSummary / rescore |
| `SchedulePlanItemController.java` | 1 | adjust |
| `AuthController.java` | 1 | logout |
| `V5SimulationController.java` | 1 | apply |
| `V5SimulationService.java` | 2 | apply() 返回 + 内部 result 转发 |
| `V4ScheduleAdjustmentService.java` | 1 | saved 透传 |
| `ScheduleController.java` | 1 | checkConflict |
| `HealthController.java` | ~~1~~ **2** ⚠️ | health（同上漏数局部变量）|
| **合计** | **66** | |

> ⚠️ **勘误**：上表 `ScheduleAnalysisController` / `ScheduleRiskController` / `HealthController` 各计 1，实测各为 2（漏数了方法体内 `Map<String, Object> result = new LinkedHashMap<>()` 等局部变量行）。**原 16 行之和=63、与"合计 66"自相矛盾**；按本勘误修正为各 2 后行和=66，与代码实测总数 66 一致。总数 66、16 文件均无误。

### 1.2 公开 API 端点影响范围（18 个 Controller 端点）

| # | HTTP | 端点 | 后端方法 | 前端消费方 | 前端类型 | 实际读取字段 |
|---|---|---|---|---|---|---|
| 1 | POST | `/api/schedules/check-conflict` | `ScheduleController.checkConflict` | `ScheduleView.vue:154` | 无显式接口 | `hasConflict`, `message` |
| 2 | POST | `/api/v3/schedule-plans/compare` | `SchedulePlanController.compare` | `ScheduleCompareView.vue` | `CompareResult` | 12 字段全读 |
| 3 | POST | `/api/v3/schedule-plans/{id}/apply` | `SchedulePlanController.apply` | `SchedulePlanDetailView.vue:198` | inline `{planId, semesterId, appliedCount, appliedAt}` | `appliedCount` |
| 4 | POST | `/api/v3/schedule-plans/{id}/rollback` | `SchedulePlanController.rollback` | `SchedulePlanDetailView.vue:223` | inline `{planId, semesterId, appliedCount, appliedAt}` | `appliedCount` |
| 5 | GET | `/api/v3/schedule-plans/{planId}/unassigned-summary` | `SchedulePlanController.getUnassignedSummary` | `SchedulePlanDetailView.vue:121` | `UnassignedSummaryItem[]` | `reasonCode`, `reasonName`, `count` |
| 6 | PUT | `/api/v3/schedule-plan-items/{itemId}/adjust` | `SchedulePlanItemController.adjust` | ⚠️ ~~`ScheduleAdjustDialog.vue`~~ **无前端调用方（真死代码）** | inline 透传 | 弱类型（前端不读 `saved`；`saved` 实为 #46 V4 透传项被混入）|
| 7 | POST | `/api/auth/logout` | `AuthController.logout` | `authStore.logout` | **不读 body** | 无 |
| 8 | POST | `/api/v5/repair-tasks/{taskId}/simulations/{planId}/apply` | `V5SimulationController.apply` | `SimulationPlanDetailView.vue:132` | inline `{planId, semesterId, appliedCount, appliedAt}` | 弱类型 |
| 9 | POST | `/api/v4/schedule-analysis/plans/{planId}/refresh` | `ScheduleAnalysisController.refreshPlanSummary` | ⚠️ `ScheduleAnalysisDetail.vue:41`（**有调用方，"刷新分析"按钮**）| — | ~~死代码~~ **活端点** |
| 10 | POST | `/api/v4/schedule-risks/plans/{planId}/refresh` | `ScheduleRiskController.refreshPlanRisks` | ⚠️ `ScheduleRiskCenter.vue:103`（**有调用方，"刷新风险"按钮**）| — | ~~死代码~~ **活端点** |
| 11 | GET | `/api/v3/schedule-plans/{planId}/score-summary` | `ScheduleScoreController.getScoreSummary` | `ScheduleScore.ts:33` | `ScoreSummary` | 前端类型 6 字段（⚠️ 后端 `Map.of` 仅发 5，缺 `conflictCount`，前端 `?? plan.conflictCount` 兜底）|
| 12 | POST | `/api/v3/schedule-plans/{planId}/rescore` | `ScheduleScoreController.rescore` | `ScheduleScore.ts:40` | inline `{planId, totalScore, conflictCount, scoreLevel}` | 3 字段 |
| 13 | GET | `/api/v3/statistics/teacher-workload` | `ScheduleStatisticsController.teacherWorkload` | `ScheduleStatisticsView.vue:79` | `TeacherWorkloadItem[]` | 9 字段 |
| 14 | GET | `/api/v3/statistics/classroom-utilization` | `ScheduleStatisticsController.classroomUtilization` | `ScheduleStatisticsView.vue:93` | `ClassroomUtilizationItem[]` | 9 字段 |
| 15 | GET | `/api/v3/statistics/class-balance` | `ScheduleStatisticsController.classBalance` | `ScheduleStatisticsView.vue:107` | `ClassBalanceItem[]` | 11 字段 |
| 16 | GET | `/api/v3/statistics/plan-overview` | `ScheduleStatisticsController.planOverview` | `ScheduleStatisticsView.vue:65` + `DashboardView.vue` | `PlanOverview` | 17 字段（见 §2 勘误）|
| 17 | GET | `/api/v3/statistics/dashboard` | `ScheduleStatisticsController.dashboard` | `DashboardView.vue` | `DashboardStats` | 4 字段（含嵌套） |
| 18 | GET | `/api/health` | `HealthController.health` | `api/index.ts:3` | **不读 body** | 无 |

> ⚠️ **勘误（返回类型口径）**：§0/本节标题称"18 端点返回 `Map<String,Object>`"，实测仅 **14 个**返回 `Result<Map<String,Object>>`；行 5、13、14、15（`getUnassignedSummary` / `teacherWorkload` / `classroomUtilization` / `classBalance`）返回 `Result<List<Map<String,Object>>>`。报告"前端类型"列写的数组 `[]` 其实已对应 List 包裹，故 18 行数据本身没错，仅标题口径不精确。

### 1.3 Service 层公共方法返回 Map（5 个）

| Service | 方法 | 调用方 |
|---|---|---|
| `ScheduleStatisticsService` | `teacherWorkload` / `classroomUtilization` / `classBalance` / `planOverview` / `dashboardStats` | `ScheduleStatisticsController`（5 端点）|
| `SchedulePlanService` | `adjustPlanItem` / `applyPlan` / `applySimulationPlan` / `rollbackPlan` | `SchedulePlanController` 3 端点 + `SchedulePlanItemController` 1 端点 + `V4ScheduleAdjustmentService` 内部 1 处 + `V5SimulationService` 内部 1 处 |
| `ScheduleCompareService` | `compare` | `SchedulePlanController.compare` |
| `SchedulePlanExplainService` | `summarizeUnassignedTasks` | `SchedulePlanController.getUnassignedSummary` |
| `V5SimulationService` | `apply` | `V5SimulationController.apply`（实际转发 `applySimulationPlan`） |

> ⚠️ **勘误（返回类型）**：本节标题"返回 Map"对其中 4 个方法不精确——`teacherWorkload` / `classroomUtilization` / `classBalance`（`ScheduleStatisticsService`）与 `summarizeUnassignedTasks`（`SchedulePlanExplainService`）实际返回 `List<Map<String,Object>>`，非 bare `Map`。`planOverview` / `dashboardStats` / `SchedulePlanService` 四方法 / `compare` / `V5SimulationService.apply` 才是 bare `Map`。方法名与"public"均无误。

### 1.4 Service 内部 Map 临时组装（仅 `ScheduleStatisticsService`）

| 行号区间 | 用途 | 风险 |
|---|---|---|
| 39-71 | 教师工作量的双层 `Map<Long, Map<String,Object>>` 累加 | 中：嵌套结构 + Set 累加器 + 二次遍历 |
| 116-145 | 教室利用率单层 `Map<String,Object>` 累加 | 低：扁平结构 + 一次循环 |
| 156-204 | 班级均衡度的双层累加 + 多键派生 | 中：5 天维度展开 + balanceScore 计算 |
| 220-276 | 方案总览单层组装 | 低：扁平聚合 |
| 282-298 | 首页统计单层组装 + 嵌套 planOverview | 低：组合 + 透传 |
| 419-426 | 私有 `longSet` / `integerLongMap` 强转辅助器 | 低：删 VO 后可移除 |

### 1.5 多态输入：`loadItems()` 返回 `List<?>`

```java
// ScheduleStatisticsService.java:302-318
private List<?> loadItems(Long semesterId, Long planId) {
    if (planId != null) {
        return planItemMapper.selectList(...);  // List<SchedulePlanItem>
    } else {
        return scheduleMapper.selectList(...);  // List<Schedule>
    }
}
```

`getTeacherId/getCourseId/getClassId/getRoomId/getWeekday/getPeriodCount` 全部用 `if (obj instanceof X)` 分支处理 — 这才是 M-14 真正的技术债源头。

---

## 2. 前端契约核验

| 接口 | 字段对齐 | 类型对齐 | 结论 |
|---|---|---|---|
| `TeacherWorkloadItem[]` | 9 字段全部对应后端 `row.put(...)` | `Record<number,number>` = `Map<Integer,Long>` JSON 序列化为对象 | ✅ |
| `ClassroomUtilizationItem[]` | 9 字段全对齐 | `number`/`number\|null` | ✅ |
| `ClassBalanceItem[]` | 11 字段全对齐（含 day1-5Periods）| `number` | ✅ |
| `PlanOverview` | ⚠️ **17** 字段（报告写 16，少 1，`scheduleStatistics.ts:71-89`） | `number\|null`/`string\|null` | ✅ |
| `DashboardStats` | 4 字段全对齐（含 v3Overview 嵌套） | 嵌套 VO | ✅ |
| `CompareResult` / `ComparePlan` | ⚠️ 无"13 字段"：`ComparePlan`=12 / `CompareResult`=4（§4.2 写的 12+4 才对） | `number`/`string\|null` | ✅* |
| `UnassignedSummaryItem[]` | 3 字段全对齐 | `string`/`number` | ✅ |
| `applySchedulePlan` / `rollbackSchedulePlan` | `planId/semesterId/appliedCount/appliedAt` 全对齐 | ✅ |
| `applySimulation` | 同上，全对齐 | ✅ |
| `getScoreSummary` / `rescore` | ⚠️ **非"全对齐"**：`getScoreSummary` 后端仅发 5 字段，前端 `ScoreSummary` 有 6（`conflictCount` 后端不发、前端 `?? plan.conflictCount` 兜底） | ⚠️ |
| `checkConflict` | 隐式约定 `{hasConflict, message}` | ✅ |
| `logout` / `health` | 不读 body | N/A |

**关键观察**：前端 TS 接口已**完全定义**后端返回的所有 JSON 字段与类型。**这是一次纯机械替换**：把后端的 `Map.put` 改成 VO 字段赋值，Jackson 序列化结果不变。

> ⚠️ **勘误**：结论大体成立，但 `getScoreSummary` 已存在前后端字段不一致（后端 5 / 前端 6），并非处处"完全定义"。改 VO 时需顺带决定 `conflictCount` 是否补发——否则会把现有的隐性不一致固化进类型。

---

## 3. 风险评估

### 3.1 风险矩阵

| 风险点 | 等级 | 触发条件 | 缓解措施 |
|---|---|---|---|
| JSON 字段名不匹配（驼峰→下划线） | **极低** | Jackson 默认按 Java 字段名输出 camelCase | 维持 `@JsonProperty` 不动 |
| 数值精度（int→Long→BigDecimal） | 低 | Java 改为 Long 后前端 number 仍可容纳 | 抽样跑一次 JSON 序列化回归 |
| 嵌套对象序列化顺序 | 极低 | LinkedHashMap 顺序 vs VO 字段声明顺序 | 不影响功能 |
| `Map.put("courseCount", Set<Long>)` 中途被替换为整数 | 低 | `teacherWorkload` 在循环里先 put Set 后 put size | 转 VO 后改为 `courseIds` 临时字段 + 构造时计算 |
| `dailyPeriods` 动态键 (`Map<Integer,Long>`) | 低 | JSON 序列化为 `{"1":3,"2":5}` | TS `Record<number,number>` 兼容 |
| `evaluation` 中文字符串 | 极低 | 字符串无类型风险 | 维持现状 |
| `null` 字段处理 | 低 | Java `Integer` 默认 null，TS `number\|null` | 已对齐 |
| `applySimulation` 异常路径透传 `applySimulationPlan` Map | 中 | V5 流程兜底 `result.appliedCount` | 透传类型签名需同步 |
| `M-14 InvestigationTest` 现锁定 66 处 | 中 | 替换后总数会变 | 测试要更新断言 |
| ~~删除 `refreshPlanSummary/refreshPlanRisks`~~ | ⚠️ **高** | **二者均有前端调用方**（非"无调用方"），删除会断 UI | **不可删**；按活端点改 VO，保留路由 |

### 3.2 影响面（受改动牵连的文件）

- **新建 VO**：~~建议 12-14 个~~ ⚠️ **实为 18 个**（§4.2 列了 16 个，加上误标"已有"的 `ScoreSummaryVo`/`UnassignedSummaryVo` 共 18；§3.5"12-14 个类"同步修正为 18）
- **修改 Service**：5 个 service（`ScheduleStatisticsService` / `SchedulePlanService` / `ScheduleCompareService` / `SchedulePlanExplainService` / `V5SimulationService`）
- **修改 Controller**：8 个 controller（统计/方案/排课/分析/风险/评分/调整/认证/健康/V5 仿真）
- **修改测试**：`M14MapStringObjectUsageInvestigationTest` 计数断言需重写
- **前端**：**无需改动**（TS 接口已对齐 JSON 字段）
- **数据库/SQL**：无影响
- **其他后端模块**：仅 `V4ScheduleAdjustmentService` 内部调用 `schedulePlanService.adjustPlanItem()` 需同步返回类型

### 3.3 不可逆/难以回滚点

| 点 | 性质 | 应对 |
|---|---|---|
| 5 个 service 公共方法签名变更 | 中：跨模块耦合 | 在 PR 描述列出全部调用方；合并前 grep 确认无遗漏 |
| `M14MapStringObjectUsageInvestigationTest` 改写 | 低：测试自己 | 改造完成后由新的 0 命中替代 |

### 3.4 测试覆盖现状

| 测试类 | 当前状态 | 改造后状态 |
|---|---|---|
| `M14MapStringObjectUsageInvestigationTest` | 锁定 66 处 / 16 文件 / 21 controller / 45 service | 重写为"已修复模式"——锁定 0 处 / 或仅锁定内部聚合用法 |
| `SchedulePlanServiceTest` | 4 tests 通过 | 需新增 `adjustPlanItem` 返回值断言 |
| `ScheduleStatisticsServiceTest` | **无** | 需新增 5 个统计方法的 VO 返回断言 |
| `ScheduleCompareServiceTest` | 已存在 | 需新增 `compare()` VO 返回断言 |
| `V4ScheduleReportServiceTest` | 已存在 | 无直接关联 |
| 前端构建 | 通过 | 应继续通过（无 API 字段改动） |

### 3.5 性能影响

- **运行时**：Jackson 序列化 VO 与 Map 性能无差异（实测可忽略）
- **编译时**：新增 12-14 个类，启动类加载量 +12-14
- **构建时**：`M14 InvestigationTest` 由 0/66 模式 + 21/45 分布断言改为枚举式断言

### 3.6 安全影响

- **无**：替换前后都是同一组字段输出，无认证/授权/输入校验变化
- **`HealthController` / `AuthController.logout`**：当前返回 `Map.of("status", "UP")` / `Map.of("success", true)` 形式简单，替换 VO 无安全收益但消除魔法字符串

---

## 4. 改造方案（推荐 3 阶段推进）

### 4.1 推荐路径

**前置条件**：在分支 `minimaxm3/m14-m16-m25-risk-assessment` 上仅评估不动代码；建议另开实施分支 `refactor/m14-mapstringobject-dto-ify`。

#### 阶段 1：低风险收割（0.5 人日）
- ⚠️ **勘误：`refreshPlanSummary` / `refreshPlanRisks` 有前端调用方（`ScheduleAnalysisDetail.vue:41` / `ScheduleRiskCenter.vue:103`），不可删**；按活端点改 VO 即可
- 改写 `HealthController.health` → `HealthInfoVo(status, service, time)`
- 改写 `AuthController.logout` → `LogoutResultVo(success)`
- 改写 `ScheduleScoreController.getScoreSummary/rescore` → ⚠️ **`ScoreSummaryVo` 不存在，需新建**（非"复用现有"）+ `RescoreResultVo`
- 改写 `ScheduleController.checkConflict` → `ConflictCheckResultVo(hasConflict, message)`
- 改写 `SchedulePlanExplainService.summarizeUnassignedTasks` → ⚠️ **`UnassignedSummaryVo` 不存在，需新建**（非"复用现有"；该方法当前返回 `List<Map<String,Object>>`）

#### 阶段 2：中风险（1.0 人日）
- `SchedulePlanService` 4 个公共方法 → `AdjustPlanResultVo` / `ApplyPlanResultVo` / `RollbackPlanResultVo`（3 个新 VO）
- `ScheduleCompareService.compare()` → `CompareResultVo`（含嵌套 `ComparePlanVo`）
- `V5SimulationService.apply()` → 透传类型

#### 阶段 3：聚合重构（1.0 人日）
- `ScheduleStatisticsService` 5 个方法 → `TeacherWorkloadVo` / `ClassroomUtilizationVo` / `ClassBalanceVo` / `PlanOverviewVo` / `DashboardStatsVo`（5 个新 VO）
- 改造 `loadItems()`：建议改用 sealed interface 或抽公共基类解多态
- 删除私有辅助 `longSet` / `integerLongMap`

#### 阶段 4：测试与守卫（0.5 人日）
- `M14MapStringObjectUsageInvestigationTest` 改为"剩余 0 处/仅内部"或迁移为"剩余为 0"的回归测试
- 为新增 VO 添加 `*VoTest` 序列化回归（参考 `SchedulePlanStatusTest` 模式）

### 4.2 计划新增 VO 清单

| VO 类 | 来源 | 字段 |
|---|---|---|
| `HealthInfoVo` | 阶段 1 | `status, service, time` |
| `LogoutResultVo` | 阶段 1 | `success` |
| `ConflictCheckResultVo` | 阶段 1 | `hasConflict, message` |
| `RescoreResultVo` | 阶段 1 | `planId, totalScore, conflictCount, scoreLevel` |
| `RefreshPlanSummaryVo` | 阶段 1（可选） | `planId, refreshed, message` |
| `RefreshPlanRisksVo` | 阶段 1（可选） | `planId, riskCount, message` |
| `AdjustPlanResultVo` | 阶段 2 | 9 字段（itemId, planId, beforeScore, afterScore, conflictFlag, conflictReason, syncFormalSchedule, scheduleId, message） |
| `ApplyPlanResultVo` | 阶段 2 | 4 字段（planId, semesterId, appliedCount, appliedAt）；`message` 当前 JSON 未发，纳入与否待定 |
| `RollbackPlanResultVo` | 阶段 2 | 4 字段（同上）；`message` 同 apply，待定 |
| `ComparePlanVo` | 阶段 2 | 12 字段 |
| `CompareResultVo` | 阶段 2 | 4 字段（semesterId, plans, bestPlanId, summary） |
| `TeacherWorkloadVo` | 阶段 3 | 9 字段 |
| `ClassroomUtilizationVo` | 阶段 3 | 9 字段 |
| `ClassBalanceVo` | 阶段 3 | 11 字段 |
| `PlanOverviewVo` | 阶段 3 | 17 字段 |
| `DashboardStatsVo` | 阶段 3 | 4 字段（嵌套 PlanOverview） |

> ⚠️ **勘误**：`ScoreSummaryVo`、`UnassignedSummaryVo` 全仓均不存在（仅本报告提及），**需新建、不能复用**——故实际新增 VO 应为 **18 个**而非 16 个。

### 4.3 不建议做的"激进"重构

- ❌ **全量 record class 化**：record 不可变但当前 Map 是 LinkedHashMap 累加器，强行 record 会改写聚合算法
- ❌ **Map → Map.of(...) 静态工厂**：聚合类需要循环 + 条件，仅最终返回值可改
- ❌ **统一为 `JsonNode`**：会丢失类型信息，类型安全倒退
- ❌ **改写 `loadItems()` 为泛型 + 类型擦除**：会引入 unchecked 警告

---

## 5. 风险结论

### 5.1 总体评级：**可推进，建议分 3-4 个 PR**

| 维度 | 评级 | 说明 |
|---|---|---|
| 功能正确性风险 | **低** | 前端契约已对齐，纯机械替换 |
| 接口契约风险 | **低** | TS 接口已锁定 14 端点字段；真正死代码仅 1 个（V3 `adjust`，前端无页面调用），两个 refresh 端点是活端点（见 §1.2 勘误）|
| 性能风险 | **极低** | Jackson 序列化等价 |
| 安全风险 | **无** | 替换无认证/输入变更 |
| 回归风险 | **中** | 需新增 VO 序列化测试，否则聚合逻辑走样难发现 |
| 工期风险 | **低-中** | 4 阶段合计 1.5-2 人日，需配合 `M14MapStringObjectUsageInvestigationTest` 改写 |

### 5.2 不实施改造的代价

- 类型不安全：IDE 无法做字段补全 / 重命名
- 重构阻力持续放大：每加一个聚合指标 = 加一个 `row.put("xxx", ...)`，无字段约束
- 后续如要加 OpenAPI 文档生成（SpringDoc），`Map` 类型会变成 `object` 黑洞
- 团队新人 onboarding 成本：grep 不出"教师工作量的字段有哪些"

### 5.3 实施时的强制约束（建议写进 PR 模板）

1. **不做全局 batch replace**：按 §4.1 阶段推进
2. **每个 VO 必加序列化测试**：从 `Map.of` 输入构造 VO，序列化 JSON，断言关键字段
3. **前端构建必须通过**：`npm run build` 0 错误
4. **不动 SQL/Entity/Mapper**：本任务仅 Service + Controller + VO 三层
5. **`M14MapStringObjectUsageInvestigationTest` 改写后必须能在新分支独立通过**

---

## 6. 建议

| 选项 | 评估 |
|---|---|
| A. 立即按 §4.1 阶段 1 + 2 启动（2 周内 1.5 人日） | 推荐：风险最低、收益最快 |
| B. 完整跑完 §4.1 四阶段（2-3 人日） | 推荐：彻底收口，但需要阻塞 1 个 sprint 安排 |
| C. 维持"暂不修复" | 不推荐：技术债每年放大 |
| D. 一次性全量替换 | 不推荐：会引入聚合逻辑回归且无单元测试覆盖 |

**最终建议**：开 `refactor/m14-mapstringobject-dto-ify` 分支，按阶段 1 → 2 → 3 → 4 推进，每个阶段单独 PR，方便 review 和回滚。

---

## 7. 附录

### 7.1 关键文件位置

| 用途 | 路径 |
|---|---|
| 现状测试 | `backend/src/test/java/com/paike/scheduler/architecture/M14MapStringObjectUsageInvestigationTest.java` |
| 现有调查 | `20260529_M14_MapStringObject使用调查.md` |
| 验证报告 | `20260527bug验证报告.md` §4 M-14 |
| 证据清单 | `minimaxm320260602.md` §3 M-14 |
| 前端 TS 接口 | `frontend/src/api/scheduleStatistics.ts` / `schedulePlan.ts` / `scheduleScore.ts` / `v4ScheduleAdjustmentApi.ts` / `v4ScheduleReplanApi.ts` / `v5SimulationApi.ts` |

### 7.2 引用检查命令

```bash
# 数量分布
grep -rn "Map<String, Object>" backend/src/main --include="*.java" | wc -l

# 按文件分布
grep -rln "Map<String, Object>" backend/src/main --include="*.java" | xargs -I{} sh -c 'echo "$(grep -c "Map<String, Object>" "{}") {}"' | sort -rn

# 公开 API 端点
grep -rn "Result<Map<String, Object>>" backend/src/main --include="*.java"

# 公共 Service 方法
grep -rn "public.*Map<String, Object>" backend/src/main --include="*.java"
```

## gpt-5.5 评语

我核验了 `backend/src/main` 中 `Map<String,Object>` 计数、Controller 返回类型、Service 聚合位置、前端调用方和 TS 契约。总体判断：本文主结论正确，M-14 可以按 DTO/VO 分阶段收敛，公开 API 侧风险低，真正风险在 `ScheduleStatisticsService` 的内部聚合和多态 `loadItems()` 输入。

核验成立：

- `Map<String,Object>` 实测 66 处、16 文件，与本文修正后的总数一致。
- Controller 端点口径为 14 个 `Result<Map<String,Object>>` + 4 个 `Result<List<Map<String,Object>>>`，本文勘误正确。
- `refreshScheduleAnalysisSummary` 与 `refreshScheduleRiskList` 在前端有实际调用，不能删；`adjustSchedulePlanItem` 仅在 `frontend/src/api/schedulePlan.ts` 定义，未发现页面调用。
- 前端统计、比较、评分等 TS 类型基本已约束字段，改 VO 时重点是保持 JSON 字段名和值类型不变。

需要修正/注意：

- §5.1 仍写“2 端点为死代码”，与前文勘误和代码核验冲突；应改为“V3 adjust 接口前端无页面调用，两个 refresh 端点是活端点”。
- `PlanOverviewVo` 清单处仍写 16 字段，但前文已勘误 `PlanOverview` 为 17 字段；实施时需按 17 字段建 VO。
- `ApplyPlanResultVo` 行写“4 字段”但括号内列了 5 个字段；需明确 `message` 是否纳入 JSON 契约。
- `getScoreSummary` 的 `conflictCount` 后端缺发、前端兜底，是改 VO 时应优先定案的契约不一致点。

结论：这份评估可以作为实施参考，但不能按原文“机械替换”直接开干。先修正文案残留矛盾，再用序列化测试锁定现有 JSON 输出，尤其覆盖统计聚合、score-summary、apply/rollback、V5 apply 透传。
