# V8 Final 最终验收记录

日期：2026-06-13

分支：`feature/v8-stage4-closeout`

## 阶段提交

1. 阶段 1：`e185df7` - `merge: V8 stage1 - engine model + conflict detector + pair test`；返工 `c3ec3ff` - `merge: V8 stage1 rework - all 7 review items fixed`
2. 阶段 2：`0905466` - `feat(v8): connect solver engine generation`（SOLVER_V8 接入 V3 生成流程 + 前端最小接入）
3. 阶段 3：`9c471f9` - `merge: V8 stage3 annealing optimizer`（回溯→退火串接 + 目标函数与 rescore 同源对拍）
4. 阶段 4：本提交 - `feat(v8): stage4 benchmark comparison + E2E + closeout`

> 阶段 2 当期以 feat 直提交入 main，未单独走 `--no-ff` merge；这是 main 上既有状态，本记录如实标注。阶段 1/3/4 均按 V8_06 分支规范走 feature 分支 + `--no-ff` 合并。

## 验收口径裁决（执行中发现、经用户确认）

V8_05 T6 原文"rescore 总分"字面线在执行中暴露两处与现实冲突，按 V8_06 §3 报用户裁决如下：

1. **总分线改为同权重(COMPREHENSIVE)口径**。根因：各策略 `ScheduleScoreService.rescore` 用各自 `strategyType` 的权重打分（`list(sem, strategyType)`），跨权重直接比 totalScore 对单一策略不公。SOLVER_V8 走 `initDefaultRules` default 分支，与 COMPREHENSIVE 共用权重，是唯一公平横向基准。裁决：五个方案统一在 COMPREHENSIVE SOFT 权重下打分后比较。V8 自身 rescore 总分即其 COMPREHENSIVE 权重分；旧策略另用只读方式复刻 COMPREHENSIVE 权重打分。
2. **耗时线区分引擎求解耗时与端到端耗时**。根因：`V3ScheduleGenerateService` 用 4 参 `SolverConfig` 构造器，退火预算硬编码 `DEFAULT_OPTIMIZE_TIME_BUDGET_MS=10_000`，请求参数 `solverTimeBudgetMs` 只喂回溯预算；端到端含 context 装载 + plan_item 逐条落库 + rescore，叠加后 ~17.5s。裁决：引擎求解耗时（回溯+退火，直接调 `EngineFacade.solve` 打点）≤ 15000ms 为硬线；端到端耗时单独记录。

未改引擎代码、未改旧策略权重、未改 V8_01 锁定决策。

## 验证命令与结果

### 1. 后端 V7 收口回归集

```powershell
cd D:\paike\backend
$env:DB_USERNAME="root"; $env:DB_PASSWORD="123456"
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"
mvn -q "-Dtest=DatabaseSchemaScriptTest,SchedulePlanServiceTest,ScheduleServiceAuditTest,TimetableServiceSemesterBoundaryTest,RequestBodySizeLimitFilterTest,AuthControllerClientIpTest" test
```

结果：Tests run: 35, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS。

### 2. 后端全量单测

```powershell
cd D:\paike\backend
$env:DB_USERNAME="root"; $env:DB_PASSWORD="123456"
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"
mvn test
```

结果：Tests run: 275, Failures: 0, Errors: 0, Skipped: 1（=V8BenchmarkComparisonTest，`@EnabledIfSystemProperty` 默认跳过），BUILD SUCCESS。

### 3. 前端类型检查

```powershell
cd D:\paike\frontend
npx vue-tsc -b --pretty false
```

结果：exit code 0。

### 4. 质量/性能对比基准（T6）

```powershell
cd D:\paike\backend
$env:DB_USERNAME="root"; $env:DB_PASSWORD="123456"
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"
mvn -q "-Dtest=V8BenchmarkComparisonTest" "-Dv8.benchmark=true" test
```

结果：Tests run: 1, Failures: 0, Errors: 0，BUILD SUCCESS，耗时 299.8s。原始输出见 `reports/v8-stage4-benchmark-raw.txt`。

配置：`DATA_SEED=20260613`、`SOLVER_SEED=42`、`SOLVER_TIME_BUDGET_MS=1000`（回溯预算），退火预算=引擎默认 10000ms。

### 5. E2E（T7）

```powershell
cd D:\paike
npm test
```

需手动启动后端 8090 / 前端 5173 后运行。结果：见文末"最终实机验收"段（待回填）。

## 对比表（T6，真实数据，可溯源至 benchmark 输出）

三档规模均使用默认 20 个 time_slot（周一至周五 × 4 大节）。T6 建议表给"大=25 时段"，本基准统一用 20，原因：`time_slot` 为全局共享表，追加行会污染依赖默认 20 槽位的回归基线；五策略在同一档内同槽位，横向对比仍为 apples-to-apples。

| 规模 | 策略 | 同权重分 | 未排 | 引擎ms | 端到端ms |
|---|---|---|---|---|---|
| 小(30任务) | **SOLVER_V8** | **94.97** | 0 | 10121 | 17315 |
| | TEACHER_PRIORITY | 93.84 | 0 | - | 1595 |
| | CLASS_BALANCE | 94.95 | 0 | - | 920 |
| | CLASSROOM_UTILIZATION | 76.77 | 0 | - | 893 |
| | COMPREHENSIVE | 93.84 | 0 | - | 654 |
| 中(120任务) | **SOLVER_V8** | **90.30** | 0 | 10942 | 15369 |
| | TEACHER_PRIORITY | 73.45 | 0 | - | 1390 |
| | CLASS_BALANCE | 79.31 | 0 | - | 1433 |
| | CLASSROOM_UTILIZATION | 59.16 | 0 | - | 886 |
| | COMPREHENSIVE | 70.03 | 0 | - | 1119 |
| 大(300任务) | **SOLVER_V8** | **88.21** | 0 | 11077 | 14916 |
| | TEACHER_PRIORITY | 79.34 | 0 | - | 3488 |
| | CLASS_BALANCE | 82.28 | 0 | - | 4401 |
| | CLASSROOM_UTILIZATION | 59.74 | 0 | - | 3162 |
| | COMPREHENSIVE | 75.04 | 0 | - | 3605 |

验收线判定：

| 规模 | 同权重总分 V8≥旧最高 | 未排 V8≤旧最少 | 引擎耗时 |
|---|---|---|---|
| 小 | 94.97 ≥ 94.95 PASS | 0 ≤ 0 PASS | - |
| 中 | 90.30 ≥ 79.31 PASS | 0 ≤ 0 PASS | - |
| 大 | 88.21 ≥ 82.28 PASS | 0 ≤ 0 PASS | 11077ms ≤ 15000ms PASS |

说明：

- 同权重口径下 SOLVER_V8 三档规模均胜过全部旧策略；中/大规模优势显著（+11 / +6 分），小规模险胜（+0.02）。
- 未排数全 0：T6 表规模合成数据集对四旧策略与 V8 均为可行解（资源充足），未排线退化为等值比较；区分新旧引擎质量的硬指标落在同权重总分。
- 引擎求解耗时三档约 10-11s（退火默认 10000ms 跑满为主因），均 < 15s。端到端含落库 + rescore，大规模 14916ms。

## 清理记录

- `V8BenchmarkComparisonTest` 每档规模独立学期，tearDown 物理清理本测试创建的全部 plan / plan_item / score_detail / score_report / unassigned_task / performance_record / rule_weight / teaching_task / classroom / course / class_info / teacher / semester，不残留。
- 阶段 1-3 的既有测试清理逻辑不变，本阶段未引入新的残留风险。

## 偏离文档之处

1. **总分线同权重口径**：V8_05 T6 原文"rescore 总分"按字面执行会跨权重比较（根因见上），经用户裁决改为同权重(COMPREHENSIVE)口径。V8 自身 rescore 即该口径；旧策略用只读方式复刻 COMPREHENSIVE 权重打分（`comprehensiveWeightedScore`，复用 `ScoringFunctions` 离线公式）。
2. **耗时线拆分**：V8_05 T6"大规模耗时 ≤ 15s（默认预算）"按字面取端到端会因退火 10s + 落库 ~7s 失败。经用户裁决拆为引擎求解耗时（≤15s 硬线）与端到端（记录用）。
3. **time_slot 统一 20**：T6 建议表给"大=25 时段"，本基准统一用默认 20，避免污染全局 `time_slot` 表。五策略同档同槽位，横向对比公平性不受影响。
4. **benchmark gated**：`V8BenchmarkComparisonTest` 用 `@EnabledIfSystemProperty(v8.benchmark=true)` 默认跳过，不进日常 `mvn test`；显式触发命令见上。原因：单跑分钟级，不应阻塞常规提交。

## 最终实机验收（E2E）

执行时间：2026-06-13 19:30-19:34

环境：

- 后端：`http://127.0.0.1:8090`
- 前端：`http://127.0.0.1:5173`
- 默认管理员：`APP_ADMIN_DEFAULT_PASSWORD=123456` 显式固定。

完整验收命令：

```powershell
cd D:\paike
npm test
```

结果：`57 passed (3.7m)`，exit code 0。含现有 52 个（V6 governance 2 + stage6 多方案管理 + stage7 手动排课冲突检测 22 + stage9 课表查询 8）+ 阶段 4 新增 `v8-solver.spec.ts` 5 个（登录、准备基础数据、智能求解生成方案、方案详情页可见、rescore 重算）。

说明：

- 首次完整 run 时 `stage7.spec.ts` 的「18. 前端登录页」因前端冷启动偶发 timeout（15s 内未跳转到 `/dashboard`）失败，单独复跑通过；第二次完整 run 全绿。该用例为 V1 时期既有前端 UI 登录测试，与 V8 改动无关（V8 阶段 4 仅改 README / 新增 benchmark / 新增 v8-solver / 新增 V8_FINAL 文档，未碰登录逻辑）。
- 首次 run 还暴露 `package.json` 的 `test`/`test:acceptance` 脚本硬编码了 4 个 spec 文件、未含 v8-solver，导致 `npm test` 只跑 52 个。已在本阶段把 `tests/v8-solver.spec.ts` 加入 `test` 与 `test:acceptance`，并新增 `test:v8` 单文件入口；README 的 E2E 说明同步更新（串行运行含 v8-solver）。

清理结果：

- `v8-solver.spec.ts` 的 `afterAll` 已清理本测试创建的 plan / schedule / teaching-task / course / classroom / class / teacher，不残留。
- 完整 run 结束后前后端进程由用户管理（CLAUDE.md §0.2：AI 不启动/不停止 Spring Boot）。
