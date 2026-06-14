# 项目深度调研与后续开发方向（2026-06-14）

## 0. 背景与定位

本文档在 V8 退火引擎性能优化收口（commit `394f9f8`，benchmark 三档全绿）之后，对整个项目做一次**基于代码实证的深度调研**，目的是：

1. 摸清项目**实际完成度**（不依赖文档声明，而是读代码判断）。
2. 识别**确凿的半成品和缺口**（有代码证据，不是猜测）。
3. 给出**后续开发方向建议**（按价值/工作量比排序，供决策）。

调研方法：codegraph 符号级探索 + context-mode 批量检索 + 关键文件 Read，覆盖后端 316 Java 文件 / 前端 50 Vue 页面 / 88 后端测试 / 5 个 E2E spec / 全部 V6 治理模块。

> 注意：本文档的结论与 `V7_FINAL_项目最终说明.md`（2026-06-09，自评完成度 90-95%）和 `project-understanding-2026-06-07.md` 有出入。那两份文档写作时间较早，部分判断已过时（如"首页统计仍为占位页"）。本文档以 **2026-06-14 的代码现状**为准，过时之处在相应章节标注。

---

## 1. 项目实际完成度（代码实证）

### 1.1 代码规模

| 维度 | 数量 | 说明 |
|---|---|---|
| 后端 Java 文件 | 316 | `backend/src/main/java/com/paike/scheduler` |
| 后端 Controller | 42 | 覆盖 V1-V8 全部业务域 |
| 后端 Service | 52 | 含 V8 引擎包（独立于 Spring） |
| 前端 Vue 页面 | 50 | `frontend/src/views`，无占位页 |
| 前端路由 | 47 实际 + 1 死代码 placeholder | 菜单无入口指向 placeholder |
| 后端测试文件 | 88 | `backend/src/test` |
| E2E spec | 5 | stage6/7/9 + v6-governance + v8-solver |

### 1.2 已完成且扎实的部分（给项目正名）

以下内容在早期文档中被标为"未完成"或"占位"，但**代码实证已完成**：

| 项 | 早期文档说法 | 2026-06-14 代码现状 | 证据 |
|---|---|---|---|
| 首页统计 | `project-understanding` 称"首页统计仍为占位页" | ✅ **已接真实数据** | `DashboardView.vue` 调 `getDashboardStats`，展示教师/班级/教室/课程数 + V3 方案概览 + 治理摘要 |
| V8 前端接入 | 疑似"最小接入"（只多个下拉） | ✅ **参数化接入** | `ScheduleGenerateView.vue:74-76` 暴露 `solverSeed`/`solverTimeBudgetMs`/`solverOptimizeTimeBudgetMs` 三个参数 |
| 审计日志接入 | `project-understanding` 第 8 节列 6 项缺失 | ✅ **已补 5/6** | ScheduleService/V4Adjustment/V4Replan/V5Simulation/AutoSchedule 均已接 `recordSuccess/recordFailure` |
| V6 一致性检查 | 草案能力 | ✅ **完整实现** | 委托 `V5ConsistencyCheckService.check()`，12 条真实校验规则，能阻断 apply |
| V6 性能基线 | 草案能力 | ✅ **完整实现** | `PerformanceBaselineService` 有 record + summary + trends，已在 4 处生产代码埋点 |
| V6 迁移状态 | 草案能力 | ✅ **完整实现（轻量）** | 真实解析文件系统 + 配置，识别 CONFIGURED/MISSING/UNCONFIGURED 三态 + 风险等级 |

### 1.3 核心测试覆盖

核心 Service 基本都有对应测试（抽样核对）：

- 有测试：`ScheduleService`(Audit)、`ScheduleConflictService`、`V3ScheduleGenerateService`、`SchedulePlanService`、`AutoScheduleService`(Test+Audit)、`V5SimulationService`(Audit+Transaction)、`V5ConsistencyCheckService`、V8 引擎全套（ConflictDetector/Backtracking/Annealing/IncrementalPenalty）。
- 测试风格：以 `@SpringBootTest` 集成测试为主（装载真实 schema），辅以纯单元测试（Mockito，如引擎包）。
- E2E：5 个 spec 覆盖多方案管理(stage6)、手动排课冲突(stage7, 22 用例)、课表查询(stage9)、V6 治理、V8 求解。

**结论：测试不是主要缺口。**

---

## 2. 确凿的半成品与缺口（有代码证据）

这部分是本文档的核心——识别出**真正需要后续工作**的地方。

### 2.1 V6 回归测试中心：不会"跑"测试 ⚠️（中等严重）

**现象**：菜单里挂着"回归测试"，点进去只能看历史记录，**没有执行回归测试的能力**。

**证据**：
- `V6RegressionTestService.java` 只有 `list(...)`（分页查询）和 `getById(Long)` 两个**只读**方法，无 `run`/`execute`/`trigger`。
- `V6RegressionTestController.java` 只暴露 `GET /api/v6/regression-tests` 和 `GET /{id}`，**无 POST 执行端点**。
- 前端 `RegressionTestView.vue` 只有"刷新/搜索/重置/详情"按钮，**无"执行回归测试"按钮**。
- 表 `schedule_regression_test` 的数据来源是 `V5Stage1DataService.recordRegressionTest`（V5 流程顺带写入），不是 V6 主动跑出来的。

**定性**：这是一个"回归测试**结果查看**中心"，不是"回归测试**执行**中心"。门面与实质不符。

### 2.2 错误码中心：内容稀薄 ⚠️（低严重）

**现象**：`SystemErrorCode.java` 只有 **10 个通用 HTTP 错误码**（AUTH 3 个 / VALIDATION 3 个 / BUSINESS 2 个 / CONFLICT 1 个 / SYSTEM 1 个），**零个排课领域错误码**。

**证据**：架构是正确的（枚举驱动 + 统一 VO + 前端真实 API），但内容撑不起"错误码中心"的门面。而 V5 一致性检查里其实已经存在大量领域 issue code（如 TEACHER_HARD_CONFLICT、CLASSROOM_CAPACITY_OVERFLOW 等），只是没注册进 `SystemErrorCode` 枚举。

### 2.3 V5RepairSuggestionService：唯一未接审计（低严重）

**现象**：审计接入清单中，文档原列 6 项缺失，现已补 5 项，**仅此服务完全未接入审计**。

**证据**：`V5RepairSuggestionService.java` grep `audit/Audit/recordSuccess/recordFailure` 零匹配，且 `SystemAuditLogService` 未为其预定义专门的 action 常量。

### 2.4 权限体系：粗粒度二元角色 ⚠️（中等严重，看项目定位）

**现状**：介于"无角色"和"完整 RBAC"之间，偏向"单一 admin 全权限"。

**证据**：
- **无 Spring Security 方法级安全**：`SecurityConfig.java` 只注册 `BCryptPasswordEncoder` Bean，无 `@EnableWebSecurity`、无 `SecurityFilterChain`、全后端无 `@PreAuthorize`/`@Secured`/`@Roles` 注解（grep 零命中）。
- **角色是裸 String，无 Role 枚举**：`SysUser.role` 是 `String`，`AdminUserInitializer` 写死 `admin.setRole("ADMIN")`。
- **授权只在拦截器，且粒度粗**（`AuthInterceptor.java:95-101`）：**只对写操作（POST/PUT/DELETE/PATCH）要求 ADMIN，所有 GET 请求任何登录用户均可访问**。即"非 admin 可读全部数据，只是不能写"。
- **前端无角色控制**：`router/index.ts` 的 meta 只有 `requiresAuth`，无 `roles`；`BaseLayout.vue` 菜单是静态写死的，无 `v-if` 按角色过滤，治理菜单对非 admin 也全量展示。

**定性**：对个人项目/演示够用；对"体现系统设计能力"偏弱。读权限全开放这一点在多角色场景下是隐患。

### 2.5 数据模型：无周次/单双周（非缺陷，是决策）

**现状**：`schema.sql` 及全部 `v*.sql` 迁移脚本 grep `week/单双周/odd_even/start_week/end_week` **零命中**。

**定性**：这是 V8_01 明确的非目标决策（"不做周次/单双周"），**不是 bug**。但它是**真实高校排课的最大功能缺口**——单双周上课、1-16 周排程是刚需。是否补取决于项目定位（见第 4 节方向 C1）。

### 2.6 死代码（低严重，可顺手清理）

- `PlaceholderView.vue` + `placeholder/:module` 路由：菜单无任何入口指向它，是早期遗留。
- 早期文档的过时描述（如"首页占位"）。

---

## 3. 后续开发方向（按价值/工作量比排序）

### 方向 A：补齐 V6 治理门面（让"看起来有的功能"真的能用）

**适用场景**：希望系统完整、可演示，不想动核心架构。**性价比最高，风险最低。**

| 子项 | 做什么 | 工作量 | 对应缺口 |
|---|---|---|---|
| A1 | 给回归测试中心加"执行"能力：`POST /api/v6/regression-tests/run`，内部调 `mvn test`（或选定测试子集）并写入记录；前端加"执行"按钮 + 实时状态 | 中（需处理进程执行/超时/输出捕获） | 2.1 |
| A2 | 扩充错误码中心：把 V5 一致性 issue code、排课业务异常码注册进 `SystemErrorCode`，从 10 个扩到 40-60 个 | 小（纯数据补充） | 2.2 |
| A3 | 补 `V5RepairSuggestionService` 审计：修复建议生成/状态变更加 `recordSuccess` | 小（抄邻近服务写法） | 2.3 |

### 方向 B：权限体系升级（让系统"像真实系统"）

**适用场景**：答辩/评审会问权限设计，或希望多角色（如教务/教师/管理员）区分。**⚠️ 触及面广，需充足回归。**

| 子项 | 做什么 | 工作量 |
|---|---|---|
| B1 | 引入 Role 枚举 + 权限点定义（如 `schedule:read`/`schedule:write`/`governance:read`） | 中 |
| B2 | 前端菜单/路由按角色控制：`router meta` 加 `roles`，菜单加 `v-if` | 小-中 |
| B3 | （可选）引入 Spring Security 方法级安全，`@PreAuthorize` 下沉到 Controller | 中 |

**风险提示**：V8_06 等执行约束明确写"不修改现有行为"。权限改造会触及几乎所有写接口，建议单独开版本（如 V9）并配完整回归，不在 main 上随手做。

### 方向 C：排课引擎能力扩展（最有技术含量，适合作为亮点）

**适用场景**：想在 V8 引擎基础上继续深化，作为"技术亮点"叙事。

| 子项 | 做什么 | 价值 | 工作量 | 备注 |
|---|---|---|---|---|
| C1 | **周次/单双周支持** | 真实排课最大刚需 | **大**（动数据模型 + 全链路） | V8 明确列为非目标；要做需正式立项 V9 |
| C2 | 教师/班级工作量均衡等软约束扩展 | 提升排课质量 | 中 | 审查 `ScoringFunctions` penalty 覆盖度，补缺失软约束，复用 V8 引擎 |
| C3 | 课表导出能力审查 | V2 文档列了"Excel 导出"，需核查完整度 | 小-中 | 核查 `TimetableService` 三种课表导出是否齐全 |

### 方向 D：工程化与交付（如果接近收尾）

**适用场景**：项目要交付/答辩/部署。

| 子项 | 做什么 | 工作量 |
|---|---|---|
| D1 | 部署手册（Docker/一键启动）、数据初始化说明、演示账号脚本 | 中 |
| D2 | 项目展示页（`ProjectShowcaseView` 已存在）打磨 | 小 |
| D3 | 清理死代码（placeholder 路由）、修正过时文档 | 小 |

---

## 4. 决策建议

### 要不要开 V9？

取决于方向选择，不是"应该开"或"不应该开"：

- **走方向 A + D**（补门面 + 交付）→ **不需要开 V9**，直接在 main 上小步提交即可。性价比最高。
- **走方向 C2 / C3**（引擎深化/导出）→ **不需要开 V9**，作为 V8 的延续，复用现有架构。
- **走方向 C1**（周次/单双周）→ **必须开 V9**，这是动数据模型的大改，要正式立项。
- **走方向 B**（权限）→ **建议开 V9**，触及面广，需要隔离 + 充足回归。

### 按项目目标的推荐组合

| 项目目标 | 推荐路径 | 理由 |
|---|---|---|
| 把现有系统做扎实/可演示 | **A + D** | 1-2 天见效，风险最低，补上门面缺口 |
| 练技术 / 答辩要有亮点 | **C2 或 C3** | 在 V8 基础上深化，有"我又做了 X"的叙事，不动数据模型 |
| 做真实能用的排课系统 | **C1**（开 V9） | 周次是唯一刚需缺口，但工作量大 |
| 体现系统设计能力 | **B**（开 V9） | RBAC 是评审常问点，但收益/风险比一般 |

### 最不推荐先做

**方向 B（权限）优先级最低**：触及面广、V8 执行约束禁止改现有行为、对"排课系统"核心价值贡献最低。除非有明确评审要求，否则放到最后。

---

## 5. 调研方法与可溯源证据

本节列出关键证据文件，供后续核验。

### 5.1 V6 治理模块核查证据

| 模块 | 结论 | 关键文件 |
|---|---|---|
| V6RegressionTestService | 半成品（只读） | `service/V6RegressionTestService.java`（仅 list/getById）、`controller/V6RegressionTestController.java`（无 POST）、`views/v6/RegressionTestView.vue`（无执行按钮） |
| ErrorCodeCatalogService | 半成品（内容稀薄） | `common/exception/SystemErrorCode.java`（10 个枚举） |
| V6ConsistencyCheckService | 完整 | `service/V6ConsistencyCheckService.java:60`（委托 V5）、`V5ConsistencyCheckService.java:79-158`（12 条校验） |
| PerformanceBaselineService | 完整 | `service/PerformanceBaselineService.java`（record/summary/trends），埋点：AutoSchedule/V3Generate/V4Replan/V5Simulation |
| DatabaseMigrationStatusService | 完整（轻量） | `service/DatabaseMigrationStatusService.java:36-136`（真实解析文件+配置+风险识别） |

### 5.2 审计接入核查证据（`recordSuccess`/`recordFailure` 调用点）

| 服务 | 状态 | 证据 |
|---|---|---|
| ScheduleService | ✅ 已接入 | `:57` 注入、`:125` CREATE 成功、`:163` DELETE 成功、`:354` 失败 |
| V4ScheduleAdjustmentService | ✅ 已接入 | `:51` 注入、`:103/:114`，action=ADJUST_SCHEDULE |
| V4ScheduleReplanService | ✅ 已接入 | `:47` 注入、`:71/:159`，action=CREATE_LOCAL_REPLAN_PLAN |
| V5SimulationService | ✅ 已接入 | `:92` 注入，GENERATE/LOCAL_REPLAN/APPLY 三类全覆盖 |
| AutoScheduleService | ✅ 已接入 | `:50` 注入、`:336/:92`，action=RUN_AUTO_SCHEDULE |
| SchedulePlanService | ✅ 已接入 | applyPlanWithAudit / recordApplyPlanFailure |
| V4ScheduleLockService | ✅ 已接入 | 锁/解锁 |
| **V5RepairSuggestionService** | ❌ **未接入** | grep 零匹配 |

### 5.3 权限体系核查证据

| 维度 | 现状 | 证据 |
|---|---|---|
| 安全框架 | 无 Spring Security 方法级安全 | `config/SecurityConfig.java`（仅 BCrypt Bean） |
| 角色模型 | 裸 String，无枚举 | `SysUser.java:27`、`AdminUserInitializer.java:56` |
| 授权逻辑 | 只校验写操作要 ADMIN，GET 全开放 | `auth/AuthInterceptor.java:95-101` |
| 前端控制 | 无角色过滤 | `router/index.ts`（meta 仅 requiresAuth）、`BaseLayout.vue`（静态菜单） |

### 5.4 数据模型核查证据

- `backend/src/main/resources/db/schema.sql` + 全部 `v*.sql`：grep `week/单双周/odd_even/start_week/end_week` 零命中 → 确认无周次字段。

---

## 6. 附：调研工具说明

本次调研使用了项目配置的 MCP 工具：
- **codegraph**（符号级代码图谱，542 文件 / 10955 符号 / 17141 边）：用于高效定位 Service/方法/调用关系，替代逐文件 grep。
- **context-mode**（上下文批量检索）：用于批量执行命令并索引输出，跨批次检索。

> 注：context-mode 的 shell 默认走 bash，Windows 上执行 PowerShell cmdlet（`Get-ChildItem` 等）需用 `pwsh -NoProfile -Command` 包裹，否则报 syntax error。
